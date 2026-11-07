package com.mrsep.musicrecognizer.feature.recognition.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mrsep.musicrecognizer.core.common.DeeplinkRouter
import com.mrsep.musicrecognizer.core.common.util.checkPermissionsGranted
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.AutoRecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.ui.isDarkUiMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.strings.R as StringsR

private const val TAG = "RecognitionControlActivity"

/**
 * Transparent activity that requests runtime permissions and a media projection token
 * when recognition is started from widgets, quick tiles, shortcuts, or notifications.
 * Also lets TileService start the foreground recognition service from the background:
 * https://issuetracker.google.com/issues/299506164
 */
@AndroidEntryPoint
class RecognitionControlActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var autoRecognitionInteractor: AutoRecognitionInteractor

    @Inject
    lateinit var deeplinkRouter: DeeplinkRouter

    private val mediaProjectionManager get() =
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val keyguardManager get() =
        getSystemService(KEYGUARD_SERVICE) as KeyguardManager

    private lateinit var requestedAudioCaptureMode: AudioCaptureMode
    private var useAltDeviceSoundSource = false
    private var intentHandled = false
    private var launchTransitionFinished = false
    private var pendingScreenCaptureConsent = false
    private var awaitingBatteryExemption = false
    // Set in onPause. The first onResume happens before the settings activity is shown.
    private var batteryExemptionPromptPaused = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result: Map<String, Boolean> ->
        if (result.isEmpty()) {
            Log.w(TAG, "Permissions request returned an empty result")
            finish()
            return@registerForActivityResult
        }
        val denied = result.mapNotNull { (permission, isGranted) ->
            permission.takeIf { !isGranted }
        }
        when {
            denied.isEmpty() -> onPermissionsGranted(intent?.action)

            denied.any { permission -> !shouldShowRequestPermissionRationale(permission) } -> {
                showPermissionsBlockedDialog()
            }

            else -> finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private val requestMediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            finish()
            return@registerForActivityResult
        }
        result.data?.let { mediaProjectionData ->
            val mode = requestedAudioCaptureMode.toServiceMode(mediaProjectionData)
            startRecognitionWithMode(mode)
        } ?: finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enforceTransparentEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.run {
            getString(KEY_REQUESTED_CAPTURE_MODE)
                ?.run(AudioCaptureMode::valueOf)
                ?.let { requestedAudioCaptureMode = it }
            useAltDeviceSoundSource = getBoolean(KEY_ALT_DEVICE_SOURCE, false)
            intentHandled = getBoolean(KEY_INTENT_HANDLED, false)
            pendingScreenCaptureConsent = getBoolean(KEY_PENDING_SCREEN_CAPTURE_CONSENT, false)
            awaitingBatteryExemption = getBoolean(KEY_AWAITING_BATTERY_EXEMPTION, false)
            batteryExemptionPromptPaused = getBoolean(KEY_BATTERY_EXEMPTION_PROMPT_PAUSED, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.run {
            if (::requestedAudioCaptureMode.isInitialized) {
                putString(KEY_REQUESTED_CAPTURE_MODE, requestedAudioCaptureMode.name)
            }
            putBoolean(KEY_ALT_DEVICE_SOURCE, useAltDeviceSoundSource)
            putBoolean(KEY_INTENT_HANDLED, intentHandled)
            putBoolean(KEY_PENDING_SCREEN_CAPTURE_CONSENT, pendingScreenCaptureConsent)
            putBoolean(KEY_AWAITING_BATTERY_EXEMPTION, awaitingBatteryExemption)
            putBoolean(KEY_BATTERY_EXEMPTION_PROMPT_PAUSED, batteryExemptionPromptPaused)
        }
    }

    override fun onStart() {
        super.onStart()
        if (intentHandled) return
        val action = intent?.action
        if (action == null) {
            Log.e(TAG, "Started with null intent action")
            finish()
            return
        }
        lifecycleScope.launch {
            if (action == TileService.ACTION_QS_TILE_PREFERENCES) {
                when (getClickedTileClassName(intent)) {
                    OneTimeRecognitionTileService::class.java.name -> Unit
                    AutoRecognitionTileService::class.java.name -> {
                        openAutoRecognitionLongPress()
                        return@launch
                    }
                    else -> {
                        Log.e(TAG, "Unknown quick settings tile")
                        finish()
                        return@launch
                    }
                }
            }
            if (action == ACTION_LAUNCH_RECOGNITION ||
                action == ACTION_RETRY_RECOGNITION ||
                action == TileService.ACTION_QS_TILE_PREFERENCES
            ) {
                loadCaptureModePreferences(action)
            }
            when (action) {
                ACTION_START_AUTO_RECOGNITION,
                ACTION_LAUNCH_RECOGNITION,
                ACTION_RETRY_RECOGNITION,
                TileService.ACTION_QS_TILE_PREFERENCES,
                ACTION_SHOW_FLOATING_BUTTON -> {
                    checkAndRequestPermissions(action)
                }
                else -> {
                    Log.e(TAG, "Unknown intent action: $action")
                    finish()
                }
            }
        }
        intentHandled = true
    }

    override fun onPause() {
        super.onPause()
        if (awaitingBatteryExemption) batteryExemptionPromptPaused = true
    }

    override fun onResume() {
        super.onResume()
        if (!awaitingBatteryExemption || !batteryExemptionPromptPaused || isFinishing) return
        awaitingBatteryExemption = false
        batteryExemptionPromptPaused = false
        if (isIgnoringBatteryOptimizations()) {
            AutoRecognitionService.start(applicationContext)
        }
        finish()
    }

    // API 35 QPR1+: MediaProjection consent no longer has FLAG_SHOW_WHEN_LOCKED
    // https://android.googlesource.com/platform/frameworks/base/+/496c34dadbb8 (b/351409536)
    // Starting it during our enter transition races with keyguard occlusion and
    // SystemUI cancels with RESULT_CANCELED, so wait until the transition finishes
    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        launchTransitionFinished = true
        if (pendingScreenCaptureConsent && !isFinishing) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestScreenCaptureConsent()
            } else {
                Log.w(TAG, "AudioPlaybackCapture API is available on Android 10+")
                finish()
            }
        }
    }

    private suspend fun loadCaptureModePreferences(action: String) {
        val preferences = preferencesRepository.userPreferencesFlow.first()
        useAltDeviceSoundSource = preferences.useAltDeviceSoundSource
        requestedAudioCaptureMode = when (action) {
            ACTION_LAUNCH_RECOGNITION -> {
                val intentMode = intent.getStringExtra(EXTRA_AUDIO_CAPTURE_MODE)?.let { modeName ->
                    runCatching { AudioCaptureMode.valueOf(modeName) }.getOrNull()
                }
                intentMode ?: preferences.defaultAudioCaptureMode
            }
            ACTION_RETRY_RECOGNITION -> {
                preferences.lastUsedAudioCaptureMode
            }
            TileService.ACTION_QS_TILE_PREFERENCES -> {
                val tileClassName = getClickedTileClassName(intent)
                check(tileClassName == OneTimeRecognitionTileService::class.java.name)
                preferences.mainButtonLongPressAudioCaptureMode
            }
            else -> {
                preferences.defaultAudioCaptureMode
            }
        }
    }

    private fun checkAndRequestPermissions(action: String) {
        val requiredPermissions = getRequiredPermissionsForRecognition()
        if (checkPermissionsGranted(requiredPermissions)) {
            onPermissionsGranted(action)
        } else {
            val shouldShowRationale = requiredPermissions
                .any { shouldShowRequestPermissionRationale(it) }
            if (shouldShowRationale) {
                showPermissionsRationaleDialog()
            } else {
                requestPermissionLauncher.launch(requiredPermissions)
            }
        }
    }

    private fun onPermissionsGranted(action: String?) = when (action) {
        ACTION_START_AUTO_RECOGNITION -> {
            startAutoRecognitionIfExempt()
        }
        TileService.ACTION_QS_TILE_PREFERENCES,
        ACTION_LAUNCH_RECOGNITION,
        ACTION_RETRY_RECOGNITION -> {
            onLaunchRecognition()
        }
        ACTION_SHOW_FLOATING_BUTTON -> {
            lifecycleScope.launch { onShowFloatingButton() }
        }
        else -> {
            Log.e(TAG, "Permissions granted for unknown action: $action")
            finish()
        }
    }

    private fun startAutoRecognitionIfExempt() {
        if (isIgnoringBatteryOptimizations()) {
            AutoRecognitionService.start(applicationContext)
            finish()
            return
        }
        requestBatteryExemption()
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val intents = listOf(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        awaitingBatteryExemption = true
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "Battery optimization intent not available: ${intent.action}")
            }
        }
        awaitingBatteryExemption = false
        Log.e(TAG, "Battery optimization settings are not available")
        finish()
    }

    private fun onLaunchRecognition() {
        when (requestedAudioCaptureMode) {
            AudioCaptureMode.Microphone -> {
                startRecognitionWithMode(requestedAudioCaptureMode.toServiceMode(null))
            }
            AudioCaptureMode.Device,
            AudioCaptureMode.Auto -> if (useAltDeviceSoundSource) {
                startRecognitionWithMode(requestedAudioCaptureMode.toServiceMode(null))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestScreenCaptureConsent()
            } else {
                Log.w(TAG, "AudioPlaybackCapture API is available on Android 10+")
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestScreenCaptureConsent() {
        // Pre-API 35 the consent dialog itself overlays the lock screen, so it can be launched immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            keyguardManager.isKeyguardLocked &&
            !launchTransitionFinished
        ) {
            pendingScreenCaptureConsent = true
            return
        }
        pendingScreenCaptureConsent = false
        requestMediaProjectionLauncher.launch(
            mediaProjectionManager.createScreenCaptureIntentForDisplay()
        )
    }

    private fun startRecognitionWithMode(audioCaptureServiceMode: AudioCaptureServiceMode) {
        RecognitionControlService.startRecognition(this.applicationContext, audioCaptureServiceMode)
        finish()
    }

    private suspend fun onShowFloatingButton() {
        val canDrawOverlay = Settings.canDrawOverlays(this@RecognitionControlActivity)
        if (!canDrawOverlay) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${packageName}".toUri(),
            )
            startActivity(intent)
            finish()
            return
        }
        with(preferencesRepository) {
            setNotificationServiceEnabled(true)
            setFloatingButtonEnabled(true)
        }
        RecognitionControlService.startHoldMode(this, false)
        finish()
    }

    private fun getPermissionDialogTheme(): Int {
        return if (isDarkUiMode()) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }

    private fun showPermissionsRationaleDialog() {
        var isDialogDismissListenerEnabled = true
        AlertDialog.Builder(this, getPermissionDialogTheme())
            .setTitle(StringsR.string.permissions)
            .setMessage(
                buildString {
                    append(getString(StringsR.string.permission_rationale_record_audio))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        append(" ")
                        append(getString(StringsR.string.permission_rationale_post_notifications))
                    }
                }
            )
            .setPositiveButton(getString(StringsR.string.request_permission)) { dialog, _ ->
                isDialogDismissListenerEnabled = false
                requestPermissionLauncher.launch(getRequiredPermissionsForRecognition())
                dialog.dismiss()
            }
            .setNegativeButton(getString(StringsR.string.not_now)) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                if (isDialogDismissListenerEnabled) finish()
            }
            .create()
            .show()
    }

    private fun showPermissionsBlockedDialog() {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).setFlags(FLAG_ACTIVITY_NEW_TASK)
        val dialogBuilder = AlertDialog.Builder(this, getPermissionDialogTheme())
            .setTitle(StringsR.string.permissions)
            .setMessage(
                buildString {
                    append(getString(StringsR.string.permission_rationale_record_audio))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        append(" ")
                        append(getString(StringsR.string.permission_rationale_post_notifications))
                    }
                    append("\n")
                    append(getString(StringsR.string.permissions_denied_message))
                }
            )
        if (appSettingsIntent.resolveActivity(packageManager) != null) {
            dialogBuilder
                .setPositiveButton(getString(StringsR.string.permissions_denied_open_settings)) { dialog, _ ->
                    startActivity(appSettingsIntent)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(StringsR.string.not_now)) { dialog, _ ->
                    dialog.dismiss()
                }
        } else {
            dialogBuilder
                .setPositiveButton(getString(StringsR.string.close)) { dialog, _ ->
                    dialog.dismiss()
                }
        }
        dialogBuilder
            .setOnDismissListener {
                finish()
            }
            .create()
            .show()
    }

    private fun openAutoRecognitionLongPress() {
        val trackId = (autoRecognitionInteractor.state.value as? AutoRecognitionState.Running)
            ?.sessionTrackIds
            ?.lastOrNull()
        if (trackId == null) {
            openApp()
            return
        }
        startActivity(deeplinkRouter.getDeepLinkIntentToTrack(trackId))
        finish()
    }

    private fun openApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Log.e(TAG, "Launcher activity not found")
            finish()
            return
        }
        startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    @Suppress("DEPRECATION")
    private fun enforceTransparentEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun getClickedTileClassName(intent: Intent): String? {
        val componentName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as? ComponentName
        }
        return componentName?.className
    }

    companion object {
        private const val ACTION_LAUNCH_RECOGNITION = "com.mrsep.musicrecognizer.control_activity.action.launch_recognition"
        private const val ACTION_RETRY_RECOGNITION = "com.mrsep.musicrecognizer.control_activity.action.retry_recognition"
        private const val ACTION_SHOW_FLOATING_BUTTON = "com.mrsep.musicrecognizer.control_activity.action.show_floating_button"
        private const val ACTION_START_AUTO_RECOGNITION = "com.mrsep.musicrecognizer.control_activity.action.start_auto_recognition"
        private const val KEY_INTENT_HANDLED = "key_intent_handled"
        private const val KEY_REQUESTED_CAPTURE_MODE = "key_requested_capture_mode"
        private const val KEY_ALT_DEVICE_SOURCE = "key_alt_device_source"
        private const val KEY_PENDING_SCREEN_CAPTURE_CONSENT = "key_pending_screen_capture_consent"
        private const val KEY_AWAITING_BATTERY_EXEMPTION = "key_awaiting_battery_exemption"
        private const val KEY_BATTERY_EXEMPTION_PROMPT_PAUSED = "key_battery_exemption_prompt_paused"
        private const val EXTRA_AUDIO_CAPTURE_MODE = "extra_audio_capture_mode"

        fun getRequiredPermissionsForRecognition(): Array<String> {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fun startRecognitionWithPermissionRequestIntent(
            context: Context,
            audioCaptureMode: AudioCaptureMode? = null
        ): Intent {
            return Intent(context, RecognitionControlActivity::class.java).apply {
                action = ACTION_LAUNCH_RECOGNITION
                addFlags(FLAG_ACTIVITY_NEW_TASK)
                if (audioCaptureMode != null) {
                    putExtra(EXTRA_AUDIO_CAPTURE_MODE, audioCaptureMode.name)
                }
            }
        }

        fun startRecognitionWithPermissionRequestPendingIntent(
            context: Context,
            audioCaptureMode: AudioCaptureMode? = null
        ): PendingIntent {
            val intent = startRecognitionWithPermissionRequestIntent(context, audioCaptureMode)
            val requestCode = audioCaptureMode?.ordinal ?: 0
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun retryRecognitionWithPermissionRequestIntent(context: Context): Intent {
            return Intent(context, RecognitionControlActivity::class.java).apply {
                action = ACTION_RETRY_RECOGNITION
                addFlags(FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun retryRecognitionWithPermissionRequestPendingIntent(context: Context): PendingIntent {
            val intent = retryRecognitionWithPermissionRequestIntent(context)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun showFloatingButtonIntent(context: Context): Intent {
            return Intent(context, RecognitionControlActivity::class.java)
                .setAction(ACTION_SHOW_FLOATING_BUTTON)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
        }

        fun showFloatingButtonPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                showFloatingButtonIntent(context),
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun startAutoRecognitionIntent(context: Context): Intent {
            return Intent(context, RecognitionControlActivity::class.java)
                .setAction(ACTION_START_AUTO_RECOGNITION)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
        }

        fun startAutoRecognitionPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                startAutoRecognitionIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

internal fun MediaProjectionManager.createScreenCaptureIntentForDisplay(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
        createScreenCaptureIntent()
    }
}

internal fun AudioCaptureMode.toServiceMode(mediaProjectionData: Intent?) = when (this) {
    AudioCaptureMode.Microphone -> AudioCaptureServiceMode.Microphone
    AudioCaptureMode.Device -> AudioCaptureServiceMode.Device(mediaProjectionData)
    AudioCaptureMode.Auto -> AudioCaptureServiceMode.Auto(mediaProjectionData)
}

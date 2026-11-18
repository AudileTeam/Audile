package com.mrsep.musicrecognizer.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mrsep.musicrecognizer.core.audio.audiorecord.AudioCaptureConfig
import com.mrsep.musicrecognizer.core.audio.audiorecord.AudioRecordingControllerFactory
import com.mrsep.musicrecognizer.core.common.util.checkPermissionsGranted
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.domain.preferences.ThemeMode
import com.mrsep.musicrecognizer.core.domain.recognition.AudioRecordingController
import com.mrsep.musicrecognizer.core.domain.recognition.RecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.core.ui.theme.MusicRecognizerTheme
import com.mrsep.musicrecognizer.feature.recognition.service.RecognitionControlActivity.Companion.getRequiredPermissionsForRecognition
import com.mrsep.musicrecognizer.feature.recognition.service.RecognitionControlService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()
    private var isServiceStartupHandled = false

    @Inject
    lateinit var audioRecordingControllerFactory: AudioRecordingControllerFactory

    @Inject
    lateinit var recognitionInteractor: RecognitionInteractor

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecognitionRequest(intent)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        if (savedInstanceState == null) {
            handleRecognitionRequest(intent)
        }
        startControlServiceOnDemand()
        controlPrerecording()
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = shouldUseDarkTheme(uiState)
            val pendingRecognitionRequest by viewModel.pendingRecognitionRequest.collectAsStateWithLifecycle()
            val unviewedTracksCount = viewModel.unviewedTracksCount.collectAsStateWithLifecycle()

            MusicRecognizerTheme(
                darkTheme = darkTheme,
                dynamicColor = shouldUseDynamicColors(uiState),
                pureBlack = shouldUsePureBlack(uiState)
            ) {
                // Update enableEdgeToEdge to match user theme mode
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        ) { darkTheme },
                        navigationBarStyle = if (darkTheme) {
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        }
                    )
                    onDispose {}
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        unviewedTracksCount = unviewedTracksCount,
                        pendingRecognitionRequest = pendingRecognitionRequest,
                        setPendingRecognitionRequest = viewModel::setPendingRecognitionRequest,
                        shouldShowNavRail = shouldShowNavRail(windowSizeClass),
                        isExpandedScreen = isExpandedScreen(windowSizeClass),
                        restoreState = savedInstanceState != null,
                        hideSplashScreen = { keepSplashScreen = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RecognitionControlService.isFloatingButtonAllowed.update { false }
    }

    override fun onPause() {
        super.onPause()
        RecognitionControlService.isFloatingButtonAllowed.update { true }
    }

    private fun handleRecognitionRequest(intent: Intent) {
        when (intent.action) {
            ACTION_RECOGNIZE -> viewModel.requestStartupRecognition(ignoreStartupUserPreference = true)
            ACTION_MAIN -> {
                if (intent.getBooleanExtra(KEY_RESTART_ON_BACKUP_RESTORE, false)) return
                viewModel.requestStartupRecognition(ignoreStartupUserPreference = false)
            }
        }
    }

    private val userInteractionSignal = Channel<Unit>(Channel.CONFLATED)

    override fun onUserInteraction() {
        super.onUserInteraction()
        userInteractionSignal.trySend(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun controlPrerecording() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var prerecordingController: AudioRecordingController? = null
                try {
                    viewModel.uiState
                        .filterIsInstance<MainActivityUiState.Success>()
                        .map { it.usePrerecording &&
                                (it.defaultAudioCaptureMode == AudioCaptureMode.Microphone ||
                                        it.mainButtonLongPressAudioCaptureMode == AudioCaptureMode.Microphone)
                        }
                        .distinctUntilChanged()
                        .mapLatest { shouldStartPrerecording ->
                            if (!shouldStartPrerecording) return@mapLatest false
                            if (!isRecordingPermissionGranted()) {
                                // Wait for the first recognition start, after that we should have recording permission
                                recognitionInteractor.status.first { it is RecognitionStatus.Recognizing }
                            }
                            isRecordingPermissionGranted()
                        }
                        // Disable prerecording when the user has been inactive with an idle timeout
                        .flatMapLatest { canStartPrerecording ->
                            if (!canStartPrerecording) {
                                flowOf(false)
                            } else {
                                userInteractionSignal
                                    .receiveAsFlow()
                                    .onStart { emit(Unit) }
                                    .transformLatest {
                                        emit(true)
                                        delay(PRERECORDING_IDLE_TIMEOUT)
                                        emit(false)
                                    }
                            }
                        }
                        .distinctUntilChanged()
                        // Immediate start for faster first recognition, delayed stop
                        .debounce { enabled -> if (enabled) Duration.ZERO else 1000.milliseconds }
                        .collect { enablePrerecording ->
                            if (enablePrerecording) {
                                Log.d(TAG, "Start prerecording")
                                prerecordingController = prerecordingController ?:
                                        audioRecordingControllerFactory.getAudioController(AudioCaptureConfig.Microphone)
                                prerecordingController?.startPrerecording(PRERECORDING_BUFFER_DURATION)
                            } else {
                                Log.d(TAG, "Stop prerecording")
                                prerecordingController?.stopPrerecording()
                                prerecordingController?.release()
                                prerecordingController = null
                            }
                        }
                } finally {
                    Log.d(TAG, "Stop prerecording in finally")
                    withContext(NonCancellable) {
                        prerecordingController?.stopPrerecording()
                        prerecordingController?.release()
                        prerecordingController = null
                    }
                }
            }
        }
    }

    private fun isRecordingPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Start previously started foreground service if the app was force killed for some reason
    private fun startControlServiceOnDemand() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (isServiceStartupHandled) return@repeatOnLifecycle
                val shouldTurnOnService = viewModel.uiState
                    .filterIsInstance<MainActivityUiState.Success>()
                    .first().notificationServiceEnabled
                val permissions = getRequiredPermissionsForRecognition()
                val permissionsGranted = checkPermissionsGranted(permissions)
                if (shouldTurnOnService) {
                    viewModel.setNotificationServiceEnabled(permissionsGranted)
                }
                isServiceStartupHandled = true
            }
        }
    }

    companion object {
        const val ACTION_RECOGNIZE = "com.mrsep.musicrecognizer.intent.action.RECOGNIZE"
        private const val KEY_RESTART_ON_BACKUP_RESTORE = "RESTART_ON_BACKUP_RESTORE"
        private val PRERECORDING_BUFFER_DURATION = 3.5.seconds
        private val PRERECORDING_IDLE_TIMEOUT = 10.minutes

        fun Context.restartApplicationOnRestore() {
            val componentName = ComponentName(this, MainActivity::class.java)
            // Set the package explicitly, required for API 34 and later
            // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            val intent = Intent.makeRestartActivityTask(componentName)
                .setPackage(packageName)
                .putExtra(KEY_RESTART_ON_BACKUP_RESTORE, true)
            startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}

@Stable
private fun shouldUseDynamicColors(uiState: MainActivityUiState): Boolean {
    return when (uiState) {
        MainActivityUiState.Loading -> false
        is MainActivityUiState.Success -> uiState.dynamicColorsEnabled
    }
}

@Stable
fun shouldShowBottomBar(windowSizeClass: WindowSizeClass) =
    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

@Stable
fun shouldShowNavRail(windowSizeClass: WindowSizeClass) = !shouldShowBottomBar(windowSizeClass)

@Stable
fun isExpandedScreen(windowSizeClass: WindowSizeClass) =
    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded ||
            windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

@Composable
private fun shouldUseDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.themeMode) {
        ThemeMode.FollowSystem -> isSystemInDarkTheme()
        ThemeMode.AlwaysLight -> false
        ThemeMode.AlwaysDark -> true
    }
}

@Stable
private fun shouldUsePureBlack(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.usePureBlackForDarkTheme
}

package com.mrsep.musicrecognizer

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.mrsep.musicrecognizer.core.audio.audiorecord.encoder.AudioRecordingDataSource
import com.mrsep.musicrecognizer.core.common.di.ApplicationScope
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionScheduler
import com.mrsep.musicrecognizer.feature.backup.AutoBackupScheduler
import com.mrsep.musicrecognizer.feature.backup.BackupNotificationHelper
import com.mrsep.musicrecognizer.feature.backup.RestoreStaging
import com.mrsep.musicrecognizer.feature.recognition.service.AutoServiceNotificationHelper
import com.mrsep.musicrecognizer.feature.recognition.service.AutoRecognitionTileService
import com.mrsep.musicrecognizer.feature.recognition.service.RecognitionControlActivity
import com.mrsep.musicrecognizer.feature.recognition.service.ResultNotificationHelper
import com.mrsep.musicrecognizer.feature.recognition.service.ServiceNotificationHelper
import com.mrsep.musicrecognizer.presentation.MainActivity
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.acra.ACRA
import org.acra.ACRAConstants
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR
import androidx.core.net.toUri
import com.mrsep.musicrecognizer.feature.recognition.service.OneTimeRecognitionTileService

@HiltAndroidApp
class MusicRecognizerApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var audioRecordingDataSource: AudioRecordingDataSource
    @Inject
    lateinit var autoBackupScheduler: AutoBackupScheduler
    @Inject
    lateinit var preferencesRepository: PreferencesRepository
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope
    @Inject
    lateinit var enqueuedRecognitionScheduler: EnqueuedRecognitionScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (ACRA.isACRASenderServiceProcess()) return
        RestoreStaging.recoverIfNeeded(this)
        if (!BuildConfig.DEBUG) setupAcra()
    }

    override fun onCreate() {
        super.onCreate()
        if (ACRA.isACRASenderServiceProcess()) return
        if (BuildConfig.DEBUG) enableStrictMode()
        cleanup()
        createNotificationChannels()
        AutoRecognitionTileService.requestListeningState(this)
        OneTimeRecognitionTileService.requestListeningState(this)
        createShortcuts()
        ensureAutoBackupSchedule()
        ensureRecognitionQueueSchedule()
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader.get()

    fun cleanup() {
        appScope.launch {
            audioRecordingDataSource.clear()
        }
    }

    private fun ensureRecognitionQueueSchedule() {
        appScope.launch {
            enqueuedRecognitionScheduler.scheduleIfNeededOnAppStart()
        }
    }

    private fun ensureAutoBackupSchedule() {
        appScope.launch {
            val autoBackup = preferencesRepository.userPreferencesFlow.first().autoBackup
            if (autoBackup.enabled) {
                autoBackupScheduler.ensureScheduled(
                    autoBackup.intervalDays,
                    autoBackup.treeUri.toUri(),
                )
            } else {
                autoBackupScheduler.cancel()
            }
        }
    }

    private fun createNotificationChannels() {
        getSystemService<NotificationManager>()?.createNotificationChannels(
            listOf(
                ServiceNotificationHelper.getStatusChannel(this),
                AutoServiceNotificationHelper.getStatusChannel(this),
                ResultNotificationHelper.getChannelForBackgroundRecognitionResult(this),
                ResultNotificationHelper.getChannelForForegroundRecognitionResult(this),
                ResultNotificationHelper.getChannelForScheduledRecognitionResult(this),
                ResultNotificationHelper.getChannelForAutoRecognitionMatch(this),
                BackupNotificationHelper.getChannelForBackup(this),
            )
        )
    }

    private fun createShortcuts() {
        ShortcutManagerCompat.setDynamicShortcuts(this, getShortcuts())
    }

    private fun getShortcuts() = listOf(
        recognitionShortcut(),
        backgroundRecognitionShortcut(),
        showFloatingButtonShortcut(),
    )
        .also { check(it.size <= 4) { "The guideline recommends publishing only 4 distinct shortcuts" } }
        .take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(this))

    private fun recognitionShortcut(): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(this, SHORTCUT_ID_OPEN_AND_RECOGNIZE)
            .setShortLabel(getString(StringsR.string.app_shortcut_open_and_recognize_short))
            .setLongLabel(getString(StringsR.string.app_shortcut_open_and_recognize_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_recognize))
            .setIntent(
                Intent(MainActivity.ACTION_RECOGNIZE, Uri.EMPTY, this, MainActivity::class.java)
            )
            .build()
    }

    private fun backgroundRecognitionShortcut(): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(this, SHORTCUT_ID_RECOGNIZE_IN_BACKGROUND)
            .setShortLabel(getString(StringsR.string.app_shortcut_recognize_short))
            .setLongLabel(getString(StringsR.string.app_shortcut_recognize_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_recognize))
            .setIntent(
                RecognitionControlActivity.startRecognitionWithPermissionRequestIntent(this)
            )
            .build()
    }

    private fun showFloatingButtonShortcut(): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(this, SHORTCUT_ID_SHOW_FLOATING_BUTTON)
            .setShortLabel(getString(StringsR.string.app_shortcut_show_floating_button_short))
            .setLongLabel(getString(StringsR.string.app_shortcut_show_floating_button_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_recognize))
            .setIntent(
                RecognitionControlActivity.showFloatingButtonIntent(this)
            )
            .build()
    }

    // Send reports only upon user approval (https://f-droid.org/en/docs/Anti-Features/#Tracking)
    private fun setupAcra() {
        ACRA.DEV_LOGGING = false
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            reportContent = ACRAConstants.DEFAULT_REPORT_FIELDS
            stopServicesOnCrash = true
            dialog {
                title = getString(StringsR.string.crash_dialog_title)
                resIcon = UiR.drawable.rounded_bug_report_fill1_24
                text = getString(StringsR.string.crash_dialog_message)
                commentPrompt = getString(StringsR.string.crash_dialog_comment_prompt)
                positiveButtonText = getString(StringsR.string.crash_dialog_button_send)
                negativeButtonText = getString(StringsR.string.crash_dialog_button_cancel)
                resTheme = R.style.Theme_MusicRecognizer_Dialog_Crash
            }
            mailSender {
                mailTo = "audile.crashes@gmail.com"
                reportAsFile = true
                reportFileName = "crash-report.json"
                subject = "Crash report"
            }
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .apply {
                    detectLeakedSqlLiteObjects()
                    detectActivityLeaks()
                    detectLeakedClosableObjects()
                    detectLeakedRegistrationObjects()
                    detectFileUriExposure()
                    detectCleartextNetwork()
                    detectContentUriWithoutPermission()
//                    detectUntaggedSockets()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        detectCredentialProtectedWhileLocked()
                        detectImplicitDirectBoot()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        detectIncorrectContextUse()
                        detectUnsafeIntentLaunch()
                    }
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        detectBlockedBackgroundActivityLaunch()
                    }

                }
                .penaltyLog()
                .build()
        )
    }

    companion object {
        private const val SHORTCUT_ID_OPEN_AND_RECOGNIZE = "RecognizeShortcutId"
        private const val SHORTCUT_ID_RECOGNIZE_IN_BACKGROUND = "RecognizeInBackgroundShortcutId"
        private const val SHORTCUT_ID_SHOW_FLOATING_BUTTON = "ShowFloatingButtonShortcutId"
    }
}

package com.mrsep.musicrecognizer.feature.recognition.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mrsep.musicrecognizer.core.common.di.ApplicationScope
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ServiceStartupReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            appScope.launch {
                val userPreferences = preferencesRepository.userPreferencesFlow.first()
                if (userPreferences.notificationServiceEnabled) {
                    RecognitionControlService.startHoldMode(context.applicationContext, true)
                }
                OneTimeRecognitionTileService.requestListeningState(context)
                AutoRecognitionTileService.requestListeningState(context)
            }.invokeOnCompletion {
                pendingResult.finish()
            }
        }
    }
}

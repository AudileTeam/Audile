package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.common.di.MainDispatcher
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.AutoRecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.NetworkMonitor
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.feature.recognition.di.MainScreenStatusHolder
import com.mrsep.musicrecognizer.feature.recognition.RecognitionStatusHolder
import com.mrsep.musicrecognizer.feature.recognition.platform.VibrationManager
import com.mrsep.musicrecognizer.feature.recognition.service.AudioCaptureServiceMode
import com.mrsep.musicrecognizer.feature.recognition.service.RecognitionControlService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecognitionViewModel @Inject constructor(
    @MainScreenStatusHolder private val statusHolder: RecognitionStatusHolder,
    private val recognitionController: ScreenRecognitionController,
    private val vibrationManager: VibrationManager,
    preferencesRepository: PreferencesRepository,
    networkMonitor: NetworkMonitor,
    autoRecognitionInteractor: AutoRecognitionInteractor,
) : ViewModel() {

    val recognitionState = statusHolder.status
    val autoRecognitionState = autoRecognitionInteractor.state

    val preferences = preferencesRepository.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        null
    )

    val isOffline = networkMonitor.isOffline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val soundLevelFlow = recognitionState.mapLatest { state ->
        state is RecognitionStatus.Recognizing
    }.distinctUntilChanged().flatMapLatest { isRecording ->
        if (isRecording) {
            recognitionController.soundLevelFlow
        } else {
            flowOf(0f)
        }
    }

    fun launchRecognition(audioCaptureServiceMode: AudioCaptureServiceMode) {
        recognitionController.launchRecognition(audioCaptureServiceMode)
    }

    fun cancelRecognition() {
        recognitionController.cancelRecognition()
    }

    fun resetRecognitionResult() {
        statusHolder.resetFinalStatus()
    }

    fun vibrateOnTap() {
        vibrationManager.vibrateOnTap()
    }

    fun vibrateSuccess() {
        vibrationManager.vibrateSuccess()
    }

    fun vibrateFailure() {
        vibrationManager.vibrateFailure()
    }
}

internal interface ScreenRecognitionController {
    val soundLevelFlow: Flow<Float>
    fun launchRecognition(audioCaptureServiceMode: AudioCaptureServiceMode)
    fun cancelRecognition()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScreenRecognitionControllerImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ScreenRecognitionController {

    private val serviceBinderFlow = callbackFlow {
        val serviceConnection = object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                trySend(service as RecognitionControlService.MainScreenBinder)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                trySend(null)
            }
        }
        appContext.bindService(
            RecognitionControlService.bindMainScreenIntent(appContext),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        awaitClose {
            appContext.unbindService(serviceConnection)
        }
    }
        .conflate()
        .buffer(0)
        .shareIn(
            scope = CoroutineScope(mainDispatcher + SupervisorJob()),
            started = SharingStarted.WhileSubscribed(0),
            replay = 1
        )

    override val soundLevelFlow: Flow<Float> = serviceBinderFlow
        .filterNotNull()
        .flatMapLatest { binder -> binder.soundLevel }

    override fun launchRecognition(audioCaptureServiceMode: AudioCaptureServiceMode) {
        RecognitionControlService.startRecognition(appContext, audioCaptureServiceMode)
    }

    override fun cancelRecognition() {
        RecognitionControlService.cancelRecognition(appContext)
    }
}

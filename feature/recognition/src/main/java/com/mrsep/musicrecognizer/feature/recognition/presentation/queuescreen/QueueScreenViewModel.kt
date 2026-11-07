package com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.audio.audioplayer.PlayerController
import com.mrsep.musicrecognizer.core.audio.audioplayer.PlayerStatus
import com.mrsep.musicrecognizer.core.common.di.DefaultDispatcher
import com.mrsep.musicrecognizer.core.common.util.AppDateTimeFormatter
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionScheduler
import com.mrsep.musicrecognizer.core.domain.recognition.NetworkMonitor
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionWorkStatus
import com.mrsep.musicrecognizer.feature.recognition.domain.DeleteEnqueuedRecognition
import com.mrsep.musicrecognizer.feature.recognition.platform.BatteryMonitor
import com.mrsep.musicrecognizer.feature.recognition.presentation.model.EnqueuedRecognitionUi
import com.mrsep.musicrecognizer.feature.recognition.presentation.model.PlayerStatusUi
import com.mrsep.musicrecognizer.feature.recognition.presentation.model.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class QueueScreenViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val enqueuedRecognitionRepository: EnqueuedRecognitionRepository,
    private val recognitionScheduler: EnqueuedRecognitionScheduler,
    private val deleteEnqueuedRecognitionUseCase: DeleteEnqueuedRecognition,
    private val playerController: PlayerController,
    private val dateFormatter: AppDateTimeFormatter,
    networkMonitor: NetworkMonitor,
    batteryMonitor: BatteryMonitor,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val screenUiStateFlow = combine(
        enqueuedRecognitionRepository.getAllRecognitionsFlow(),
        preferencesRepository.userPreferencesFlow,
        batteryMonitor.isBatteryLowFlow(),
        networkMonitor.isOffline,
    ) { enqueuedList, preferences, batteryLow, offline ->
        QueueScreenUiState.Success(
            recognitionList = enqueuedList
                .map { it.toUi(dateFormatter) }
                .toImmutableList(),
            useGridLayout = preferences.useGridForRecognitionQueue,
            showCreationDate = preferences.showCreationDateInQueue,
            queuePauseReason = resolvePauseReason(
                offline = offline,
                batteryLow = batteryLow,
                allowQueueOnLowBattery = preferences.allowQueueOnLowBattery,
                hasQueued = enqueuedList.any { it.workStatus == RecognitionWorkStatus.QUEUED },
            ),
        )
    }.flowOn(defaultDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QueueScreenUiState.Loading
    )

    val playerStatusFlow = playerController.statusFlow.map(PlayerStatus::toUi).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerStatusUi.Idle
    )

    fun renameRecognition(recognitionId: Int, newTitle: String) {
        viewModelScope.launch {
            enqueuedRecognitionRepository.updateTitle(recognitionId, newTitle)
        }
    }

    fun enqueueRecognition(recognitionId: Int) {
        viewModelScope.launch {
            recognitionScheduler.enqueue(listOf(recognitionId))
        }
    }

    fun cancelRecognition(recognitionId: Int) {
        cancelRecognitions(setOf(recognitionId))
    }

    fun cancelRecognitions(recognitionIds: Set<Int>) {
        viewModelScope.launch {
            recognitionScheduler.cancel(recognitionIds.toList())
        }
    }

    fun cancelAndDeleteRecognition(recognitionId: Int) {
        cancelAndDeleteRecognitions(setOf(recognitionId))
    }

    fun cancelAndDeleteRecognitions(recognitionIds: Set<Int>) {
        viewModelScope.launch {
            playerController.stop()
            deleteEnqueuedRecognitionUseCase(recognitionIds.toList())
        }
    }

    fun startAudioPlayer(recognitionId: Int) {
        viewModelScope.launch {
            enqueuedRecognitionRepository.getAudioSampleFile(recognitionId)?.let { sampleFile ->
                playerController.start(recognitionId, sampleFile)
            }
        }
    }

    fun stopAudioPlayer() {
        playerController.stop()
    }

    fun pauseAudioPlayer() {
        playerController.pause()
    }

    fun resumeAudioPlayer() {
        playerController.resume()
    }

    fun resetPlayerError() {
        playerController.resetError()
    }

    fun setUseGridLayout(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseGridForRecognitionQueue(value)
        }
    }

    fun setShowCreationDate(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowCreationDateInQueue(value)
        }
    }

    fun allowLowBatteryThisTime() {
        viewModelScope.launch {
            recognitionScheduler.allowLowBatteryThisTime()
        }
    }

    override fun onCleared() {
        stopAudioPlayer()
    }
}

@Immutable
internal sealed class QueueScreenUiState {

    data object Loading : QueueScreenUiState()

    data class Success(
        val recognitionList: ImmutableList<EnqueuedRecognitionUi>,
        val useGridLayout: Boolean,
        val showCreationDate: Boolean,
        val queuePauseReason: QueuePauseReason,
    ) : QueueScreenUiState()
}

internal enum class QueuePauseReason {
    None,
    Offline,
    LowBattery,
}

private fun resolvePauseReason(
    offline: Boolean,
    batteryLow: Boolean,
    allowQueueOnLowBattery: Boolean,
    hasQueued: Boolean,
): QueuePauseReason {
    if (!hasQueued) return QueuePauseReason.None
    if (offline) return QueuePauseReason.Offline
    if (!batteryLow) return QueuePauseReason.None
    return if (allowQueueOnLowBattery) QueuePauseReason.None else QueuePauseReason.LowBattery
}

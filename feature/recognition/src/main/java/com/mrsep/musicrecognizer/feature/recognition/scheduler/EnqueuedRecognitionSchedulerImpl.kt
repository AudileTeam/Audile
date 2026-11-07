package com.mrsep.musicrecognizer.feature.recognition.scheduler

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.mrsep.musicrecognizer.core.common.di.ApplicationScope
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionScheduler
import com.mrsep.musicrecognizer.feature.recognition.platform.BatteryMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EnqueuedRecognitionSchedulerImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: EnqueuedRecognitionRepository,
    private val runRegistry: EnqueuedRecognitionRunRegistry,
    private val preferencesRepository: PreferencesRepository,
    private val batteryMonitor: BatteryMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
) : EnqueuedRecognitionScheduler {

    private val workManager get() = WorkManager.getInstance(appContext)

    private val persistentCoroutineContext = appScope.coroutineContext

    private val scheduleMutex = Mutex()

    override suspend fun enqueue(recognitionIds: List<Int>) {
        if (recognitionIds.isEmpty()) return
        withContext(persistentCoroutineContext) {
            scheduleMutex.withLock {
                repository.markQueued(recognitionIds, Instant.now())
                scheduleLocked()
            }
        }
    }

    override suspend fun cancel(recognitionIds: List<Int>) {
        if (recognitionIds.isEmpty()) return
        withContext(persistentCoroutineContext) {
            scheduleMutex.withLock {
                repository.cancelWork(recognitionIds)
                runRegistry.cancelIfProcessing(recognitionIds)
                // Dropped rows may have been the earliest backoff
                scheduleLocked()
            }
        }
    }

    override suspend fun cancelAll() {
        withContext(persistentCoroutineContext) {
            scheduleMutex.withLock {
                repository.cancelAllWork()
                runRegistry.cancelAllProcessing()
                workManager.cancelUniqueWork(RecognitionQueueWorker.UNIQUE_NAME)
                workManager.cancelUniqueWork(RecognitionTimerWorker.UNIQUE_NAME)
                // Low-battery reset stays
            }
        }
    }

    override suspend fun scheduleIfNeededOnAppStart() {
        withContext(persistentCoroutineContext) {
            repository.resetStaleRunningOnAppStart()
            scheduleMutex.withLock {
                val isQueueAllowedOnLowBattery = isQueueAllowedOnLowBattery()
                if (isQueueAllowedOnLowBattery && !batteryMonitor.isBatteryLow()) {
                    preferencesRepository.setAllowQueueOnLowBattery(false)
                    workManager.cancelUniqueWork(LowBatteryResetWorker.UNIQUE_NAME)
                } else if (isQueueAllowedOnLowBattery) {
                    scheduleResetWorker()
                }
                updateConstraintsAndScheduleLocked()
            }
        }
    }

    override suspend fun allowLowBatteryThisTime() {
        withContext(persistentCoroutineContext) {
            scheduleMutex.withLock {
                if (!batteryMonitor.isBatteryLow()) return@withLock
                preferencesRepository.setAllowQueueOnLowBattery(true)
                updateConstraintsAndScheduleLocked()
                scheduleResetWorker()
            }
        }
    }

    override suspend fun disallowQueueOnLowBattery() {
        withContext(persistentCoroutineContext) {
            scheduleMutex.withLock {
                if (!isQueueAllowedOnLowBattery()) return@withLock
                preferencesRepository.setAllowQueueOnLowBattery(false)
                workManager.cancelUniqueWork(LowBatteryResetWorker.UNIQUE_NAME)
                updateConstraintsAndScheduleLocked()
            }
        }
    }

    suspend fun onLowBatteryRecovered() {
        withContext(persistentCoroutineContext) {
            scheduleMutex.withLock {
                preferencesRepository.setAllowQueueOnLowBattery(false)
                updateConstraintsAndScheduleLocked()
            }
        }
    }

    /**
     * Schedules the queue worker with no delay, or the backoff timer.
     * Backoff is a separate unique work.
     */
    suspend fun scheduleIfNeeded() {
        scheduleMutex.withLock { scheduleLocked() }
    }

    suspend fun onTimerFired() {
        scheduleMutex.withLock { scheduleLocked(fromTimer = true) }
    }

    private suspend fun scheduleLocked(fromTimer: Boolean = false) {
        wakeup(fromTimer = fromTimer, updateConstraints = false)
    }

    private suspend fun updateConstraintsAndScheduleLocked() {
        wakeup(fromTimer = false, updateConstraints = true)
    }

    private suspend fun wakeup(fromTimer: Boolean, updateConstraints: Boolean) {
        val requiresBatteryNotLow = !isQueueAllowedOnLowBattery()
        val constraintsUpdated = updateConstraints && updateUnfinishedQueueWork(requiresBatteryNotLow)
        val delayMs = queuedDelayMs()
        when {
            delayMs == null -> if (!fromTimer) cancelTimer()
            delayMs > 0L -> scheduleTimer(delayMs, requiresBatteryNotLow)
            else -> {
                if (!constraintsUpdated) scheduleQueueWorker(requiresBatteryNotLow)
                if (!fromTimer) cancelTimer()
            }
        }
    }

    private suspend fun queuedDelayMs(): Long? {
        val nextAttemptAt = repository.queuedNextAttempt() ?: return null
        return nextAttemptAt.toEpochMilli() - Instant.now().toEpochMilli()
    }

    // Returns true when an unfinished queue request has this battery constraint
    private suspend fun updateUnfinishedQueueWork(requiresBatteryNotLow: Boolean): Boolean {
        val unfinished = workManager
            .getWorkInfosForUniqueWork(RecognitionQueueWorker.UNIQUE_NAME)
            .await()
            .filter { !it.state.isFinished }
        if (unfinished.isEmpty()) return false
        var applied = false
        for (info in unfinished) {
            if (info.constraints.requiresBatteryNotLow() == requiresBatteryNotLow) {
                applied = true
                continue
            }
            val request = RecognitionQueueWorker.buildRequest(
                requiresBatteryNotLow = requiresBatteryNotLow,
                id = info.id,
            )
            val result = try {
                workManager.updateWork(request).await()
            } catch (_: IllegalArgumentException) {
                WorkManager.UpdateResult.NOT_APPLIED
            }
            if (result != WorkManager.UpdateResult.NOT_APPLIED) applied = true
        }
        return applied
    }

    private fun scheduleQueueWorker(requiresBatteryNotLow: Boolean) {
        workManager.enqueueUniqueWork(
            RecognitionQueueWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            RecognitionQueueWorker.buildRequest(requiresBatteryNotLow = requiresBatteryNotLow),
        )
    }

    private fun scheduleTimer(delayMs: Long, requiresBatteryNotLow: Boolean) {
        workManager.enqueueUniqueWork(
            RecognitionTimerWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            RecognitionTimerWorker.buildRequest(
                initialDelayMs = delayMs,
                requiresBatteryNotLow = requiresBatteryNotLow,
            ),
        )
    }

    private fun scheduleResetWorker() {
        workManager.enqueueUniqueWork(
            LowBatteryResetWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            LowBatteryResetWorker.buildRequest(),
        )
    }

    private fun cancelTimer() {
        workManager.cancelUniqueWork(RecognitionTimerWorker.UNIQUE_NAME)
    }

    private suspend fun isQueueAllowedOnLowBattery(): Boolean {
        return preferencesRepository.userPreferencesFlow.first().allowQueueOnLowBattery
    }
}

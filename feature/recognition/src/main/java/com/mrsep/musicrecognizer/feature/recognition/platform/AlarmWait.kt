package com.mrsep.musicrecognizer.feature.recognition.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration

internal interface AlarmWait {
    suspend fun await(duration: Duration)
}

@Singleton
internal class AlarmWaitImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val attemptWakeLock: AttemptWakeLock,
) : AlarmWait {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var waiter: CancellableContinuation<Unit>? = null

    private val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(ACTION)
            .setPackage(appContext.packageName)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_IMMUTABLE,
    )

    override suspend fun await(duration: Duration) {
        checkMainThread()
        if (duration <= Duration.ZERO) return
        // Don't hold the CPU across the wait
        attemptWakeLock.release()
        suspendCancellableCoroutine { continuation ->
            scheduleAlarm(continuation, duration)
        }
        attemptWakeLock.acquire()
    }

    fun onAlarm() {
        checkMainThread()
        val continuation = waiter
        if (continuation == null) {
            // Stale alarm
            alarmManager.cancel(pendingIntent)
            return
        }
        waiter = null
        attemptWakeLock.acquire()
        continuation.resume(Unit)
    }

    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun scheduleAlarm(continuation: CancellableContinuation<Unit>, duration: Duration) {
        val replaced = waiter
        waiter = continuation
        // A second wait replaces the first
        if (replaced != null) alarmManager.cancel(pendingIntent)
        continuation.invokeOnCancellation {
            if (waiter === continuation) {
                waiter = null
                alarmManager.cancel(pendingIntent)
            }
        }
        replaced?.cancel()
        if (!continuation.isActive) return
        if (!powerManager.isIgnoringBatteryOptimizations(appContext.packageName)) {
            onBatteryExemptionLost(continuation)
            return
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + duration.inWholeMilliseconds,
                pendingIntent,
            )
        } catch (_: SecurityException) {
            onBatteryExemptionLost(continuation)
        }
    }

    private fun onBatteryExemptionLost(continuation: CancellableContinuation<Unit>) {
        if (waiter === continuation) waiter = null
        alarmManager.cancel(pendingIntent)
        continuation.cancel(BatteryExemptionLost())
    }

    private fun checkMainThread() {
        check(Looper.getMainLooper().isCurrentThread) { "AlarmWait is main-thread only" }
    }

    companion object {
        const val ACTION = "com.mrsep.musicrecognizer.auto_recognition.action.wakeup"
    }
}

internal class BatteryExemptionLost : CancellationException(
    "Auto recognition lost its battery-optimization exemption",
)

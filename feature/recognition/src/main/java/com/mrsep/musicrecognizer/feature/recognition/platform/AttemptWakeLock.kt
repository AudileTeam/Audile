package com.mrsep.musicrecognizer.feature.recognition.platform

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Not reference-counted. Another acquire refreshes the timeout. */
@Singleton
internal class AttemptWakeLock @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val lock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${context.packageName}:auto_recognition"
            )
            .apply { setReferenceCounted(false) }

    fun acquire() {
        lock.acquire(TIMEOUT_MS)
    }

    fun release() {
        if (lock.isHeld) lock.release()
    }

    private companion object {
        private const val TIMEOUT_MS = 120_000L
    }
}

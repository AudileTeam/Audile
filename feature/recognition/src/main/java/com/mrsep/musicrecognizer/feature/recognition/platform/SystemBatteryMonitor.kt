package com.mrsep.musicrecognizer.feature.recognition.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

internal class SystemBatteryMonitor @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : BatteryMonitor {

    override fun isBatteryLow(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val sticky = appContext.queryStickyBatteryChangedIntent() ?: return false
        return sticky.isBatteryLowExtra()
    }

    override fun isBatteryLowFlow(): Flow<Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return flowOf(false)
        return callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(intent.isBatteryLowExtra())
                }
            }
            val sticky = ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            trySend(sticky?.isBatteryLowExtra() == true)
            awaitClose { appContext.unregisterReceiver(receiver) }
        }.distinctUntilChanged()
    }

    private fun Context.queryStickyBatteryChangedIntent(): Intent? {
        return ContextCompat.registerReceiver(
            this,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun Intent.isBatteryLowExtra(): Boolean {
        return getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
    }
}

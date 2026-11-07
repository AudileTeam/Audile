package com.mrsep.musicrecognizer.feature.recognition.platform

import kotlinx.coroutines.flow.Flow

internal interface BatteryMonitor {

    fun isBatteryLow(): Boolean

    fun isBatteryLowFlow(): Flow<Boolean>
}

package com.mrsep.musicrecognizer.feature.recognition.di

import com.mrsep.musicrecognizer.feature.recognition.platform.AlarmWait
import com.mrsep.musicrecognizer.feature.recognition.platform.AlarmWaitImpl
import com.mrsep.musicrecognizer.feature.recognition.platform.AudioModeCallMonitor
import com.mrsep.musicrecognizer.feature.recognition.platform.BatteryMonitor
import com.mrsep.musicrecognizer.feature.recognition.platform.CallMonitor
import com.mrsep.musicrecognizer.feature.recognition.platform.SystemBatteryMonitor
import com.mrsep.musicrecognizer.feature.recognition.platform.VibrationManager
import com.mrsep.musicrecognizer.feature.recognition.platform.VibrationManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
internal interface PlatformModule {

    @Binds
    @Singleton
    fun bindVibrationManager(implementation: VibrationManagerImpl): VibrationManager

    @Binds
    @Singleton
    fun bindBatteryMonitor(implementation: SystemBatteryMonitor): BatteryMonitor

    @Binds
    @Singleton
    fun bindCallMonitor(implementation: AudioModeCallMonitor): CallMonitor

    @Binds
    @Singleton
    fun bindAlarmWait(implementation: AlarmWaitImpl): AlarmWait
}

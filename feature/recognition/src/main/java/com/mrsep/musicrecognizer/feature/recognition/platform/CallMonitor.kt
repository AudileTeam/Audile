package com.mrsep.musicrecognizer.feature.recognition.platform

import android.os.Build
import androidx.annotation.RequiresApi

interface CallMonitor {

    fun inCall(): Boolean

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun awaitCallEnd()
}

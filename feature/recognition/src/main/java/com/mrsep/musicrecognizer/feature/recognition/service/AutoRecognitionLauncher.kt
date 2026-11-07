package com.mrsep.musicrecognizer.feature.recognition.service

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.util.Log
import com.mrsep.musicrecognizer.core.common.util.checkPermissionsGranted

object AutoRecognitionLauncher {

    private const val TAG = "AutoRecognitionLauncher"

    fun start(context: Context) {
        if (hasGrantedPermissionsAndBatteryExemption(context) && startServiceOrBlocked(context)) return
        context.startActivity(
            RecognitionControlActivity.startAutoRecognitionIntent(context),
        )
    }

    fun stop(context: Context) {
        AutoRecognitionService.stop(context)
    }

    // API 34 cannot start a microphone foreground service from a tile.
    // https://issuetracker.google.com/issues/299506164
    fun startFromTile(tile: TileService) {
        val blockedOnThisApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (!blockedOnThisApi && hasGrantedPermissionsAndBatteryExemption(tile) &&
            startServiceOrBlocked(tile)) {
            return
        }
        collapseToTrampoline(tile)
    }

    private fun startServiceOrBlocked(context: Context): Boolean {
        return try {
            AutoRecognitionService.start(context)
            true
        } catch (e: Exception) {
            if (!isStartBlocked(e)) throw e
            Log.w(TAG, "Direct start was blocked, using the permission activity", e)
            false
        }
    }

    private fun isStartBlocked(error: Exception): Boolean {
        return error is SecurityException || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun collapseToTrampoline(tile: TileService) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tile.startActivityAndCollapse(
                RecognitionControlActivity.startAutoRecognitionPendingIntent(tile),
            )
        } else {
            @Suppress("DEPRECATION")
            tile.startActivityAndCollapse(
                RecognitionControlActivity.startAutoRecognitionIntent(tile),
            )
        }
    }

    private fun hasGrantedPermissionsAndBatteryExemption(context: Context): Boolean {
        val permissions = RecognitionControlActivity.getRequiredPermissionsForRecognition()
        if (!context.checkPermissionsGranted(permissions)) {
            return false
        }
        if (!context.isIgnoringBatteryOptimizations()) return false
        return true
    }
}

internal fun Context.isIgnoringBatteryOptimizations(): Boolean {
    return (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(packageName)
}

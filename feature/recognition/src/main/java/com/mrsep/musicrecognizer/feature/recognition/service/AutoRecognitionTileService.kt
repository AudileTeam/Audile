package com.mrsep.musicrecognizer.feature.recognition.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.mrsep.musicrecognizer.core.domain.recognition.AutoRecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.strings.R as StringsR

private const val TAG = "AutoRecognitionTileService"

@AndroidEntryPoint
class AutoRecognitionTileService : TileService() {

    private var coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tileUpdateJob: Job? = null

    @Inject
    lateinit var interactor: AutoRecognitionInteractor

    @Inject
    lateinit var trackRepository: TrackRepository

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        requestListeningState(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        tileUpdateJob?.cancel()
        tileUpdateJob = coroutineScope.launch {
            interactor.state
                .map { it.tileContent() }
                .distinctUntilChanged()
                .collect { updateTile(it) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        tileUpdateJob?.cancel()
        tileUpdateJob = null
    }

    override fun onClick() {
        super.onClick()
        if (interactor.state.value.running) {
            AutoRecognitionLauncher.stop(this)
        } else {
            AutoRecognitionLauncher.startFromTile(this)
        }
    }

    private suspend fun updateTile(content: AutoRecognitionTileContent) {
        val tile = qsTile ?: run {
            Log.e(TAG, "qsTile is null, is update called in valid state?")
            return
        }
        val trackLine = content.nowPlayingTrackId?.let { nowPlayingSubtitle(it) }
        val showTrack = content.blocker == null && trackLine != null
        tile.label = getString(
            if (showTrack) {
                StringsR.string.quick_tile_auto_recognition_now_playing
            } else {
                StringsR.string.quick_tile_auto_recognition
            },
        )
        tile.state = if (content.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = content.blocker?.let(::blockerSubtitle) ?: trackLine
        }
        tile.updateTile()
    }

    private fun blockerSubtitle(phase: AutoRecognitionState.Phase): String? {
        val resId = when (phase) {
            AutoRecognitionState.Phase.WaitingForNetwork ->
                StringsR.string.notification_auto_recognition_status_waiting_network
            AutoRecognitionState.Phase.PausedLowBattery ->
                StringsR.string.notification_auto_recognition_status_low_battery
            AutoRecognitionState.Phase.PausedDuringCall ->
                StringsR.string.notification_auto_recognition_status_during_call
            else -> return null
        }
        return getString(resId)
    }

    private suspend fun nowPlayingSubtitle(trackId: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val track = trackRepository.getTrackFlow(trackId).first() ?: return null
        return "${track.title} - ${track.artist}"
    }

    companion object {
        fun requestListeningState(context: Context) {
            try {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(
                        context.applicationContext,
                        AutoRecognitionTileService::class.java,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request qsTile listening state", e)
            }
        }
    }
}

package com.mrsep.musicrecognizer.feature.recognition.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.material3.ColorProviders
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.core.ui.theme.darkColorScheme
import com.mrsep.musicrecognizer.core.ui.theme.lightColorScheme
import com.mrsep.musicrecognizer.feature.recognition.scheduler.TrackArtworkPrefetchWorker
import com.mrsep.musicrecognizer.feature.recognition.service.RecognitionControlService
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.CircleLayoutContent
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RowLayoutContent
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.TileLayoutContent
import com.mrsep.musicrecognizer.feature.recognition.widget.util.ImageUtils.getWidgetArtworkOrNull
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

class RecognitionWidget : GlanceAppWidget() {

    /* Unlike responsive mode, it makes building flex layouts easier
     * and does not keep bitmaps in memory for each size */
    override val sizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val workManager = WorkManager.getInstance(context)
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context,
            RecognitionWidgetEntryPoint::class.java
        )
        val router = hiltEntryPoint.serviceRouter()
        val widgetStatusHolder = hiltEntryPoint.widgetStatusHolder()
        val glanceCoroutineContext = currentCoroutineContext() + Dispatchers.Default

        val statusFlow = widgetStatusHolder.status.onEach { status ->
            when (status) {
                RecognitionStatus.Ready -> Unit
                is RecognitionStatus.Recognizing -> ResetWidgetStatusWorker.cancel(context)
                is RecognitionStatus.Done -> ResetWidgetStatusWorker.enqueue(context)
            }
        }.stateIn(scope = CoroutineScope(glanceCoroutineContext))

        /* Glance can't round image corners prior to API 31
         * so we must do it on-demand based on the required image size */
        fun widgetUiFlow(widgetLayout: RecognitionWidgetLayout) = statusFlow.flatMapLatest { status ->
            when (status) {
                is RecognitionStatus.Done -> when (val result = status.result) {
                    is RecognitionResult.Success -> flow {
                        val track = result.track
                        if (!widgetLayout.showArtwork ||
                            (track.artworkThumbUrl == null && track.artworkUrl == null)
                        ) {
                            emit(WidgetUiState(status, null))
                            return@flow
                        }

                        val longestArtworkSidePx = maxOf(
                            widgetLayout.artworkWidthPx,
                            widgetLayout.artworkHeightPx,
                        )
                        val minRequiredArtworkSizePx = (longestArtworkSidePx / 1.25f).toInt()
                        val isHiResRequired = minRequiredArtworkSizePx > 400 // Typical thumb is about 400x400

                        val initialArtwork = context.resolveWidgetArtworkOrNull(
                            artworkThumbUrl = track.artworkThumbUrl,
                            artworkUrl = track.artworkUrl,
                            widgetLayout = widgetLayout,
                            preferHiRes = isHiResRequired
                        )
                        emit(WidgetUiState(status = status, artwork = initialArtwork))

                        if (!isHiResRequired && initialArtwork != null) return@flow

                        val isSuccess = workManager.awaitArtworkPrefetchWorkerResult(track.id)
                        if (isSuccess) {
                            val refreshedArtwork = context.resolveWidgetArtworkOrNull(
                                artworkThumbUrl = track.artworkThumbUrl,
                                artworkUrl = track.artworkUrl,
                                widgetLayout = widgetLayout,
                                preferHiRes = true
                            )
                            emit(WidgetUiState(status = status, artwork = refreshedArtwork))
                        }
                    }

                    else -> flowOf(WidgetUiState(status = status, artwork = null))
                }

                else -> flowOf(WidgetUiState(status = status, artwork = null))
            }
        }.flowOn(Dispatchers.Default)

        val onLaunchRecognition = actionRunCallback<LaunchRecognition>()
        val onCancelRecognition = actionRunCallback<CancelRecognition>()
        val onRetryRecognition = actionRunCallback<RetryRecognition>()

        provideContent {
            val widgetLayout = RecognitionWidgetLayout.fromLocalSize()
            val widgetUiState by remember(widgetLayout) {
                widgetUiFlow(widgetLayout)
            }.collectAsState(
                initial = WidgetUiState(
                    status = RecognitionStatus.Ready,
                    artwork = null
                )
            )
            val onWidgetClick = when (val state = widgetUiState.status) {
                RecognitionStatus.Ready -> onLaunchRecognition

                is RecognitionStatus.Recognizing -> onCancelRecognition

                is RecognitionStatus.Done -> when (val result = state.result) {
                    is RecognitionResult.Success -> actionStartActivity(
                        intent = router.getDeepLinkIntentToTrack(result.track.id)
                    )

                    is RecognitionResult.NoMatches,
                    RecognitionResult.NoSoundDetected -> onRetryRecognition

                    is RecognitionResult.ScheduledOffline,
                    is RecognitionResult.Error -> actionRunCallback<ResetWidgetFinalState>()
                }
            }
            GlanceTheme(
                // Enable dynamic colors if available
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    ColorProviders(light = lightColorScheme, dark = darkColorScheme)
                }
            ) {
                when (widgetLayout) {
                    is RecognitionWidgetLayout.Circle -> CircleLayoutContent(
                        layout = widgetLayout,
                        uiState = widgetUiState,
                        onLaunchRecognition = onLaunchRecognition,
                        onCancelRecognition = onCancelRecognition,
                        onWidgetClick = onWidgetClick
                    )
                    is RecognitionWidgetLayout.Tile -> TileLayoutContent(
                        layout = widgetLayout,
                        uiState = widgetUiState,
                        onLaunchRecognition = onLaunchRecognition,
                        onCancelRecognition = onCancelRecognition,
                        onRetryRecognition = onRetryRecognition,
                        onWidgetClick = onWidgetClick
                    )
                    is RecognitionWidgetLayout.Row -> RowLayoutContent(
                        layout = widgetLayout,
                        uiState = widgetUiState,
                        onLaunchRecognition = onLaunchRecognition,
                        onCancelRecognition = onCancelRecognition,
                        onRetryRecognition = onRetryRecognition,
                        onWidgetClick = onWidgetClick
                    )
                }
            }
        }
    }

    private suspend fun Context.resolveWidgetArtworkOrNull(
        artworkThumbUrl: String?,
        artworkUrl: String?,
        widgetLayout: RecognitionWidgetLayout,
        preferHiRes: Boolean,
    ): Bitmap? {
        val urlsInPriorityOrder = if (preferHiRes) {
            listOfNotNull(artworkUrl, artworkThumbUrl)
        } else {
            listOfNotNull(artworkThumbUrl, artworkUrl)
        }
        for (url in urlsInPriorityOrder) {
            val artwork = getWidgetArtworkOrNull(
                url = url,
                widthPx = widgetLayout.artworkWidthPx,
                heightPx = widgetLayout.artworkHeightPx,
                artworkStyle = widgetLayout.artworkStyle
            )
            if (artwork != null) return artwork
        }
        return null
    }

    private suspend fun WorkManager.awaitArtworkPrefetchWorkerResult(trackId: String): Boolean {
        val uniqueWorkName = TrackArtworkPrefetchWorker.buildUniqueWorkerName(trackId)
        return getWorkInfosForUniqueWorkFlow(uniqueWorkName)
            .transform { infos ->
                when (infos.lastOrNull()?.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING -> Unit
                    WorkInfo.State.SUCCEEDED -> emit(true)
                    WorkInfo.State.FAILED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.CANCELLED,
                    null -> emit(false)
                }
            }
            .first()
    }
}

internal class LaunchRecognition : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context,
            RecognitionWidgetEntryPoint::class.java
        )
        val preferences = hiltEntryPoint.preferencesRepository().userPreferencesFlow.first()
        RecognitionControlService.startRecognitionWithPermissionFlow(
            context = context,
            audioCaptureMode = preferences.defaultAudioCaptureMode,
            useAltDeviceSoundSource = preferences.useAltDeviceSoundSource,
        )
    }
}

internal class RetryRecognition : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context,
            RecognitionWidgetEntryPoint::class.java
        )
        val preferences = hiltEntryPoint.preferencesRepository().userPreferencesFlow.first()
        RecognitionControlService.startRecognitionWithPermissionFlow(
            context = context,
            audioCaptureMode = preferences.lastUsedAudioCaptureMode,
            useAltDeviceSoundSource = preferences.useAltDeviceSoundSource,
        )
    }
}

internal class CancelRecognition : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RecognitionControlService.cancelRecognition(context)
    }
}

internal class ResetWidgetFinalState : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context,
            RecognitionWidgetEntryPoint::class.java
        )
        val widgetStatusHolder = hiltEntryPoint.widgetStatusHolder()
        widgetStatusHolder.resetFinalStatus()
        RecognitionWidget().updateAll(context)
    }
}

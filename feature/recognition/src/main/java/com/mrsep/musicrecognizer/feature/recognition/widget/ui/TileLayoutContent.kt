package com.mrsep.musicrecognizer.feature.recognition.widget.ui

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.feature.recognition.R
import com.mrsep.musicrecognizer.feature.recognition.widget.WidgetUiState
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.buttonScaleFactor
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.tileOverlayButtonSize
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.tileWidgetBorderWidth
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTopPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.titleTextSize
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun TileLayoutContent(
    layout: RecognitionWidgetLayout.Tile,
    uiState: WidgetUiState,
    onLaunchRecognition: Action,
    onCancelRecognition: Action,
    onRetryRecognition: Action,
    onWidgetClick: Action,
) {
    // Clickable modifier affects the shape of widget during smooth transition,
    // it's better when it is not placed on root view, so wrap it in extra box
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .roundedWidgetBackground(
                transparent = tileWidgetBorderWidth == 0.dp && uiState.artwork != null
            ),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(rippleOverride = -1, onClick = onWidgetClick)
                .padding(tileWidgetBorderWidth),
            contentAlignment = Alignment.Center,
        ) {
            when (val status = uiState.status) {
                RecognitionStatus.Ready,
                is RecognitionStatus.Recognizing -> ButtonWithStatus(
                    uiState = uiState,
                    onLaunchRecognition = onLaunchRecognition,
                    onCancelRecognition = onCancelRecognition,
                    layout = layout,
                )

                is RecognitionStatus.Done -> {
                    when (val result = status.result) {
                        is RecognitionResult.Success -> Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            if (uiState.artwork != null) {
                                Image(
                                    provider = ImageProvider(uiState.artwork),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier.fillMaxSize(),
                                )
                            } else {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(UiR.drawable.outline_album_fill1_24),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = GlanceModifier.fillMaxSize(),
                                        colorFilter = ColorFilter.tint(GlanceTheme.colors.outline)
                                    )
                                }
                            }
                            if (layout.artworkStyle.bottomFading) {
                                Image(
                                    provider = ImageProvider(R.drawable.widget_artwork_fade_gradient),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = GlanceModifier.fillMaxSize(),
                                )
                            }
                            TrackInfoWithButton(
                                title = result.track.title,
                                artist = result.track.artist,
                                onLaunchRecognition = onLaunchRecognition,
                                buttonSize = tileOverlayButtonSize,
                            )
                        }

                        is RecognitionResult.NoMatches,
                        RecognitionResult.NoSoundDetected -> ButtonWithStatus(
                            uiState = uiState,
                            onLaunchRecognition = onRetryRecognition,
                            onCancelRecognition = onCancelRecognition,
                            layout = layout,
                        )

                        is RecognitionResult.ScheduledOffline,
                        is RecognitionResult.Error -> IconWithStatus(
                            uiState = uiState,
                            iconResId = UiR.drawable.rounded_priority_high_48,
                            layout = layout,
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TrackInfoWithButton(
    title: String,
    artist: String,
    onLaunchRecognition: Action,
    buttonSize: Dp,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight()
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = titleTextSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
            Spacer(GlanceModifier.height(subtitleTopPadding - 2.dp))
            Text(
                text = artist,
                style = TextStyle(
                    color = ColorProvider(Color(0.9f, 0.9f, 0.9f)),
                    fontSize = subtitleTextSize,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
            TextCenteringPadding(artist)
        }
        Spacer(GlanceModifier.width(12.dp))
        StaticRecognitionButton(
            isRecognizing = false,
            onClick = onLaunchRecognition,
            onClickLabel = context.getString(StringsR.string.action_recognize),
            filledStyle = true,
            buttonSize = buttonSize,
        )
    }
}

@Composable
private fun ButtonWithStatus(
    uiState: WidgetUiState,
    onLaunchRecognition: Action,
    onCancelRecognition: Action,
    layout: RecognitionWidgetLayout.Tile,
) {
    val context = LocalContext.current
    val buttonSize = layout.recognitionButtonMaxSize
    Column(
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val isRecognizing = uiState.status is RecognitionStatus.Recognizing
        Spacer(GlanceModifier.height(layout.statusButtonTopPadding))
        AnimatedRecognitionButton(
            isRecognizing = isRecognizing,
            onClick = if (isRecognizing) onCancelRecognition else onLaunchRecognition,
            onClickLabel = context.getString(
                if (isRecognizing) StringsR.string.action_cancel_recognition
                else StringsR.string.action_recognize
            ),
            filledStyle = true,
            scaledButtonSize = buttonSize,
            iconPadding = (buttonSize / buttonScaleFactor() * 0.16f).coerceAtLeast(8.dp),
        )
        Spacer(GlanceModifier.height(layout.statusLabelTopPadding))
        Text(
            text = context.getWidgetTitleForStatus(uiState.status),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = titleTextSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun IconWithStatus(
    uiState: WidgetUiState,
    @DrawableRes iconResId: Int,
    layout: RecognitionWidgetLayout.Tile,
) {
    val context = LocalContext.current
    val iconSize = layout.recognitionButtonMaxSize
    Column(
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(GlanceModifier.height(layout.statusButtonTopPadding))
        Image(
            provider = ImageProvider(iconResId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier
                .size(iconSize)
                .padding((iconSize * 0.16f).coerceAtLeast(8.dp))
        )
        Spacer(GlanceModifier.height(layout.statusLabelTopPadding))
        Text(
            text = context.getWidgetTitleForStatus(uiState.status),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = titleTextSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

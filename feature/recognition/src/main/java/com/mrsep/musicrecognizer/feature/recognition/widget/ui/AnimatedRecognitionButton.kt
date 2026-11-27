package com.mrsep.musicrecognizer.feature.recognition.widget.ui

import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.mrsep.musicrecognizer.feature.recognition.R
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.buttonScaleFactor
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AnimatedRecognitionButton(
    isRecognizing: Boolean,
    onClick: Action,
    onClickLabel: String,
    filledStyle: Boolean = true,
    scaledButtonSize: Dp,
    iconPadding: Dp? = null,
) {
    val context = LocalContext.current
    val buttonScaleFactor = buttonScaleFactor()
    Box(
        modifier = GlanceModifier
            .size(scaledButtonSize)
            .semantics { this.contentDescription = onClickLabel }
            .clickable(rippleOverride = -1, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRecognizing) {
            // Using a hack to make animated button in Glance. Open for refactoring.
            AndroidRemoteViews(
                remoteViews = RemoteViews(
                    context.packageName,
                    R.layout.widget_flipper_container
                ),
                containerViewId = R.id.widget_flipper_container,
                modifier = GlanceModifier.fillMaxSize()
            ) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    RecognitionButtonContent(
                        isRecognizing = true,
                        contentSize = scaledButtonSize / buttonScaleFactor,
                        filledStyle = filledStyle,
                        iconPadding = iconPadding,
                    )
                }
                Box {} // Used to change flipper states
            }
        } else {
            RecognitionButtonContent(
                isRecognizing = false,
                contentSize = scaledButtonSize / buttonScaleFactor,
                filledStyle = filledStyle,
                iconPadding = iconPadding,
            )
        }
    }
}

@Composable
internal fun StaticRecognitionButton(
    isRecognizing: Boolean,
    onClick: Action,
    onClickLabel: String,
    filledStyle: Boolean = true,
    buttonSize: Dp,
) {
    Box(
        modifier = GlanceModifier
            .semantics { this.contentDescription = onClickLabel }
            .clickable(rippleOverride = -1, onClick = onClick)
    ) {
        RecognitionButtonContent(
            isRecognizing = isRecognizing,
            contentSize = buttonSize,
            filledStyle = filledStyle,
        )
    }
}

@Composable
private fun RecognitionButtonContent(
    isRecognizing: Boolean,
    contentSize: Dp,
    filledStyle: Boolean = true,
    iconPadding: Dp? = null,
) {
    val padding = iconPadding ?: if (contentSize >= 40.dp) 8.dp else 6.dp
    Box(
        modifier = GlanceModifier
            .size(contentSize)
            .then(
                if (filledStyle) {
                    GlanceModifier.background(ImageProvider(R.drawable.widget_recognition_button_shape))
                } else {
                    GlanceModifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(UiR.drawable.outline_lines_shift1_48),
            contentDescription = null,
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(padding),
            colorFilter = if (filledStyle) {
                ColorFilter.tint(GlanceTheme.colors.onPrimary)
            } else {
                if (isRecognizing) {
                    ColorFilter.tint(GlanceTheme.colors.primary)
                } else {
                    ColorFilter.tint(GlanceTheme.colors.onSurface)
                }
            }
        )
    }
}

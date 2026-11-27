package com.mrsep.musicrecognizer.feature.recognition.widget.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.artworkToTextPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.contentPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTopPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.titleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.util.rowLineCounts

// Assume that the bitmap is loaded with appropriate (pre-calculated) size
@Composable
internal fun RowScope.TrackInfo(
    track: Track,
    artwork: Bitmap?,
    layout: RecognitionWidgetLayout.Row,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val reservedArtworkWidth = if (layout.showArtwork) {
        layout.artworkSize + artworkToTextPadding
    } else {
        0.dp
    }
    val lines = rowLineCounts(
        context = context,
        widgetWidth = size.width,
        widgetHeight = size.height,
        buttonSize = layout.recognitionButtonMaxSize,
        title = track.title,
        subtitle = track.artist,
        subtitleMaxLines = 1,
        reservedArtworkWidth = reservedArtworkWidth,
    )
    val artist = track.artist.takeIf { lines.subtitleLines > 0 }
    Row(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (layout.showArtwork) {
            if (artwork != null) {
                Image(
                    provider = ImageProvider(artwork),
                    contentDescription = null,
                )
            } else {
                ArtworkRoundedPlaceholder(layout.artworkSize)
            }
            Spacer(GlanceModifier.width(artworkToTextPadding))
        }
        Column(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
        ) {
            Text(
                text = track.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = titleTextSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                ),
                maxLines = lines.titleLines,
                modifier = GlanceModifier.fillMaxWidth()
            )
            if (artist != null) {
                Spacer(GlanceModifier.height(subtitleTopPadding))
                Text(
                    text = artist,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = subtitleTextSize,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start,
                    ),
                    maxLines = lines.subtitleLines,
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
            TextCenteringPadding(artist)
        }
    }
}

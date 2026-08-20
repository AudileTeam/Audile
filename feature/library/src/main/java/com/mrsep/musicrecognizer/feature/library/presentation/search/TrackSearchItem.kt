package com.mrsep.musicrecognizer.feature.library.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import com.mrsep.musicrecognizer.core.ui.util.forwardingPainter
import com.mrsep.musicrecognizer.feature.library.presentation.model.TrackUi
import kotlinx.collections.immutable.ImmutableSet
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun TrackSearchItem(
    track: TrackUi,
    query: String,
    searchScope: ImmutableSet<TrackDataField>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    showRecognitionDate: Boolean = true,
    contentPadding: PaddingValues
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(contentPadding)
    ) {
        val placeholder = forwardingPainter(
            painter = painterResource(UiR.drawable.outline_album_fill1_24),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            alpha = 0.3f
        )
        AsyncImage(
            model = track.artworkThumbUrl,
            fallback = placeholder,
            error = placeholder,
            contentDescription = stringResource(StringsR.string.artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .shadow(
                    elevation = 1.dp,
                    shape = shape
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = shape
                )
                .clip(shape)
                .heightIn(max = 100.dp)
                .aspectRatio(1f, true)
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (searchScope.contains(TrackDataField.Title)) {
                    highlightQueryPart(track.title, query)
                } else {
                    AnnotatedString(track.title)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (searchScope.contains(TrackDataField.Artist)) {
                    highlightQueryPart(track.artist, query)
                } else {
                    AnnotatedString(track.artist)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (track.album != null && searchScope.contains(TrackDataField.Album)) {
                Text(
                    text = highlightQueryPart(track.album, query),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showRecognitionDate) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = track.recognitionDate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .alpha(0.9f)
                        .align(Alignment.End)
                )
            }
        }
    }
}

@Composable
@Stable
private fun highlightQueryPart(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val spanStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    return buildAnnotatedString {
        var start = 0
        while (true) {
            val first = text.indexOf(query, start, ignoreCase = true).takeIf { it != -1 } ?: break
            val end = first + query.length
            append(text.substring(start, first))
            withStyle(spanStyle) {
                append(text.substring(first, end))
            }
            start = end
        }
        append(text.substring(start, text.length))
    }
}

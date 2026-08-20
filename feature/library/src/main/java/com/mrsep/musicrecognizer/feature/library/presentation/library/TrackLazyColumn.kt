package com.mrsep.musicrecognizer.feature.library.presentation.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.mrsep.musicrecognizer.core.ui.components.MultiSelectionState
import com.mrsep.musicrecognizer.core.ui.util.forwardingPainter
import com.mrsep.musicrecognizer.feature.library.presentation.model.TrackUi
import kotlinx.collections.immutable.ImmutableList
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun TrackLazyColumn(
    trackList: ImmutableList<TrackUi>,
    onTrackClick: (trackId: String) -> Unit,
    lazyListState: LazyListState,
    multiSelectionState: MultiSelectionState<String>,
    showRecognitionDate: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
    ) {
        itemsIndexed(
            items = trackList,
            key = { _, track -> track.id }
        ) { index, track ->
            Column(modifier = Modifier.animateItem()) {
                TrackLazyColumnItem(
                    track = track,
                    selected = multiSelectionState.isSelected(track.id),
                    onClick = {
                        if (multiSelectionState.hasSelected) {
                            multiSelectionState.toggleSelection(track.id)
                        } else {
                            onTrackClick(track.id)
                        }
                    },
                    onLongClick = { multiSelectionState.toggleSelection(track.id) },
                    showRecognitionDate = showRecognitionDate,
                    contentPadding = PaddingValues(vertical = 5.dp, horizontal = 12.dp)
                )
                if (index != trackList.lastIndex) {
                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                }
            }
        }
    }
}

@Composable
private fun TrackLazyColumnItem(
    track: TrackUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRecognitionDate: Boolean,
    contentPadding: PaddingValues,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(color = containerColor) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
            }
            .combinedClickable(
                interactionSource = null,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(contentPadding)
    ) {
        val placeholder = forwardingPainter(
            painter = painterResource(UiR.drawable.outline_album_fill1_24),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            alpha = 0.3f
        )
        Box(
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
        ) {
            AsyncImage(
                model = track.artworkThumbUrl,
                fallback = placeholder,
                error = placeholder,
                contentDescription = stringResource(StringsR.string.artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!track.isViewed) {
                UnviewedTrackBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(0.95f)
            )
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.95f)
            )
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

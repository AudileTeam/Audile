package com.mrsep.musicrecognizer.feature.preferences.presentation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.ui.resources.titleId
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun RequiredServicesDialog(
    modifier: Modifier = Modifier,
    requiredServices: List<MusicService>,
    onRequiredServicesChanged: (List<MusicService>) -> Unit,
    onDismissClick: () -> Unit,
) {
    var enabledServices by rememberSaveable(stateSaver = MusicServiceListSaver) {
        mutableStateOf(requiredServices)
    }
    val initialServices = remember { requiredServices }
    val dismissDialog = {
        if (enabledServices != initialServices) onRequiredServicesChanged(enabledServices)
        onDismissClick()
    }
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    // Scroll sticks to the first visible item's key. Asking for the same index forgets that
    // key for one measure, so a moved anchor item doesn't drag the viewport with it.
    // The index and offset are unchanged, so the placement animation is kept.
    fun pinScrollIndex() {
        lazyListState.requestScrollToItem(
            lazyListState.firstVisibleItemIndex,
            lazyListState.firstVisibleItemScrollOffset,
        )
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // disabled rows are not reorder targets
        val current = enabledServices
        if (from.index in current.indices && to.index in current.indices) {
            enabledServices = current.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val enabledSet = enabledServices.toSet()
    val services = enabledServices +
        MusicService.entriesByPopularity.filter { it !in enabledSet }

    var raised by remember { mutableStateOf<MusicService?>(null) }

    AlertDialog(
        modifier = modifier,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(text = stringResource(StringsR.string.pref_title_music_services_links))
        },
        confirmButton = {
            TextButton(onClick = dismissDialog) {
                Text(text = stringResource(StringsR.string.close))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                pinScrollIndex()
                raised = null
                enabledServices = emptyList()
            }) {
                Text(text = stringResource(StringsR.string.reset))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxHeight()) {
                Text(
                    text = stringResource(StringsR.string.music_services_links_dialog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(StringsR.string.music_services_links_extra_lookup),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(services, key = { it }) { service ->
                        val enabled = service in enabledSet
                        ReorderableItem(
                            reorderableState,
                            key = service,
                            enabled = enabled,
                            animateItemModifier = Modifier
                                .zIndex(if (service == raised) 1f else 0f)
                                .animateItem(),
                        ) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                            MusicServiceRow(
                                service = service,
                                enabled = enabled,
                                elevation = elevation,
                                onToggle = {
                                    if (services.getOrNull(lazyListState.firstVisibleItemIndex) == service) {
                                        pinScrollIndex()
                                    }
                                    raised = service
                                    enabledServices = if (enabled) enabledServices - service
                                    else enabledServices + service
                                },
                                dragHandleModifier = if (enabled) {
                                    Modifier.draggableHandle(
                                        onDragStarted = {
                                            raised = null
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate
                                            )
                                        },
                                        onDragStopped = {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.GestureEnd
                                            )
                                        },
                                    )
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        },
        onDismissRequest = dismissDialog,
    )
}

@Composable
private fun MusicServiceRow(
    service: MusicService,
    enabled: Boolean,
    elevation: Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier?,
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = enabled,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = enabled,
            onCheckedChange = null,
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
        Text(
            text = stringResource(service.titleId()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (dragHandleModifier != null) {
            IconButton(
                onClick = {},
                modifier = dragHandleModifier,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_drag_handle_24),
                    contentDescription = stringResource(StringsR.string.reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Avoid listSaver: it encodes an empty list as null, which rememberSaveable treats as missing state
private val MusicServiceListSaver = Saver<List<MusicService>, List<String>>(
    save = { services -> services.map { it.name } },
    restore = { saved ->
        saved.mapNotNull { runCatching { MusicService.valueOf(it) }.getOrNull() }.distinct()
    }
)

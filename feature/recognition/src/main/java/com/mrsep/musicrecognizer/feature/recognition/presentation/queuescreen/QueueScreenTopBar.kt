package com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

private enum class TopBarMode { EmptyQueue, Default, MultiSelection }

@Composable
internal fun QueueScreenTopBar(
    isQueueEmpty: Boolean,
    isMultiselectEnabled: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancelSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    useGridLayout: Boolean,
    onChangeUseGridLayout: (Boolean) -> Unit,
    showCreationDate: Boolean,
    onChangeShowCreationDate: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val topBarMode = when {
        isQueueEmpty -> TopBarMode.EmptyQueue
        isMultiselectEnabled -> TopBarMode.MultiSelection
        else -> TopBarMode.Default
    }
    val transition = updateTransition(targetState = topBarMode, label = "topBarMode")
    TopAppBar(
        modifier = modifier,
        title = {
            Crossfade(
                targetState = (selectedCount != 0),
                label = "Title"
            ) { selectionTitle ->
                val selectedDesc = pluralStringResource(StringsR.plurals.items_selected, count = selectedCount, selectedCount)
                Text(
                    text = if (selectionTitle) {
                        if (selectedCount == 0) "" else "$selectedCount / $totalCount"
                    } else {
                        stringResource(StringsR.string.queue).toUpperCase(LocalLocale.current)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (selectedCount == 0) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics {
                            contentDescription = selectedDesc
                            liveRegion = LiveRegionMode.Assertive
                        }
                    }
                )
            }
        },
        navigationIcon = {
            transition.AnimatedContent(
                contentAlignment = Alignment.CenterStart
            ) { mode ->
                when (mode) {
                    TopBarMode.EmptyQueue,
                    TopBarMode.Default -> {}

                    TopBarMode.MultiSelection -> IconButton(onClick = onDeselectAll) {
                        Icon(
                            painter = painterResource(UiR.drawable.outline_close_24),
                            contentDescription = stringResource(StringsR.string.disable_multi_selection_mode)
                        )
                    }
                }
            }
        },
        actions = {
            transition.AnimatedContent(
                contentAlignment = Alignment.CenterEnd
            ) { mode ->
                when (mode) {
                    TopBarMode.EmptyQueue -> {}

                    TopBarMode.Default -> Row(horizontalArrangement = Arrangement.End) {
                        QueueDropdownMenu(
                            useGridLayout = useGridLayout,
                            onChangeUseGridLayout = onChangeUseGridLayout,
                            showCreationDate = showCreationDate,
                            onChangeShowCreationDate = onChangeShowCreationDate,
                        )
                    }

                    TopBarMode.MultiSelection -> Row(
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_select_all_24),
                                contentDescription = stringResource(StringsR.string.select_all)
                            )
                        }
                        IconButton(onClick = onCancelSelected) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_cancel_schedule_send_24),
                                contentDescription = stringResource(StringsR.string.cancel_selected)
                            )
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_delete_24),
                                contentDescription = stringResource(StringsR.string.delete_selected)
                            )
                        }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun QueueDropdownMenu(
    modifier: Modifier = Modifier,
    useGridLayout: Boolean,
    onChangeUseGridLayout: (Boolean) -> Unit,
    showCreationDate: Boolean,
    onChangeShowCreationDate: (Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { menuExpanded = !menuExpanded }) {
            Icon(
                painter = painterResource(UiR.drawable.outline_more_vert_24),
                contentDescription = stringResource(StringsR.string.show_more)
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = MaterialTheme.shapes.small,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.pref_title_use_grid_layout)) },
                onClick = { onChangeUseGridLayout(!useGridLayout) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_check_24),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (useGridLayout) 1f else 0f
                        }
                    )
                },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    selected = useGridLayout
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.pref_title_show_creation_date)) },
                onClick = { onChangeShowCreationDate(!showCreationDate) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_check_24),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (showCreationDate) 1f else 0f
                        }
                    )
                },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    selected = showCreationDate
                },
            )
        }
    }
}

@Composable
internal fun QueueScreenLoadingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(StringsR.string.queue).toUpperCase(LocalLocale.current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

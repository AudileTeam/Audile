package com.mrsep.musicrecognizer.feature.library.presentation.library

import androidx.compose.animation.*
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

private enum class TopBarMode { EmptyLibrary, Default, MultiSelection }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreenTopBar(
    isLibraryEmpty: Boolean,
    isFilterApplied: Boolean,
    isMultiselectEnabled: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    useGridLayout: Boolean,
    showRecognitionDate: Boolean,
    onChangeUseGridLayout: (Boolean) -> Unit,
    onChangeShowRecognitionDate: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val topBarMode = when {
        isLibraryEmpty -> TopBarMode.EmptyLibrary
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
                Text(
                    text = if (selectionTitle) {
                        if (selectedCount == 0) "" else "$selectedCount / $totalCount"
                    } else {
                        stringResource(StringsR.string.library).toUpperCase(Locale.current)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            transition.AnimatedContent(
                contentAlignment = Alignment.CenterStart
            ) { mode ->
                when (mode) {
                    TopBarMode.EmptyLibrary,
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
                    TopBarMode.EmptyLibrary -> {}

                    TopBarMode.Default -> Row(horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_search_24),
                                contentDescription = stringResource(StringsR.string.library_search)
                            )
                        }
                        IconButton(onClick = onFilterClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_filter_list_24),
                                tint = animateColorAsState(
                                    if (isFilterApplied) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    },
                                    label = "FilterIconColor"
                                ).value,
                                contentDescription = stringResource(StringsR.string.filters)
                            )
                        }
                        LibraryDropdownMenu(
                            useGridLayout = useGridLayout,
                            onChangeUseGridLayout = onChangeUseGridLayout,
                            showRecognitionDate = showRecognitionDate,
                            onChangeShowRecognitionDate = onChangeShowRecognitionDate,
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
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_delete_24),
                                contentDescription = stringResource(StringsR.string.delete_selected)
                            )
                        }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LibraryDropdownMenu(
    modifier: Modifier = Modifier,
    useGridLayout: Boolean,
    onChangeUseGridLayout: (Boolean) -> Unit,
    showRecognitionDate: Boolean,
    onChangeShowRecognitionDate: (Boolean) -> Unit,
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
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    selected = useGridLayout
                },
                onClick = { onChangeUseGridLayout(!useGridLayout) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_check_24),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (useGridLayout) 1f else 0f
                        }
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.pref_title_show_recognition_date)) },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    selected = showRecognitionDate
                },
                onClick = { onChangeShowRecognitionDate(!showRecognitionDate) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_check_24),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (showRecognitionDate) 1f else 0f
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreenLoadingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(StringsR.string.library).toUpperCase(Locale.current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

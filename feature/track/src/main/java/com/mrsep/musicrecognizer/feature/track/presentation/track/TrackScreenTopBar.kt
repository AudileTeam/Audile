package com.mrsep.musicrecognizer.feature.track.presentation.track

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackScreenTopBar(
    onBackPressed: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShowDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        modifier = modifier,
        title = {},
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_arrow_back_24),
                    contentDescription = stringResource(StringsR.string.nav_back)
                )
            }
        },
        actions = {
            Row {
                IconButton(onClick = onShareClick) {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_share_24),
                        contentDescription = stringResource(StringsR.string.share)
                    )
                }
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
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
                            text = { Text(text = stringResource(StringsR.string.show_more)) },
                            onClick = {
                                menuExpanded = false
                                onShowDetailsClick()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(UiR.drawable.outline_info_24),
                                    contentDescription = null
                                )
                            }
                        )
                        HorizontalDivider(Modifier.alpha(0.5f))
                        DropdownMenuItem(
                            text = { Text(text = stringResource(StringsR.string.delete)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(UiR.drawable.outline_delete_24),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackScreenLoadingTopBar(
    onBackPressed: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {},
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_arrow_back_24),
                    contentDescription = stringResource(StringsR.string.nav_back)
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

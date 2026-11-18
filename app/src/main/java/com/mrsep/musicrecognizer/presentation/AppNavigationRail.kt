package com.mrsep.musicrecognizer.presentation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey

@Composable
fun AppNavigationRail(
    unviewedTracksCount: State<Int>,
    selectedKey: NavKey,
    onSelect: (NavKey) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        TopLevelDestination.entries.forEach { destinationEntry ->
            val selected = destinationEntry.key == selectedKey
            NavigationRailItem(
                selected = selected,
                enabled = enabled,
                icon = {
                    when (destinationEntry) {
                        TopLevelDestination.Library -> LibraryNavigationIcon(
                            selected = selected,
                            unviewedTracksCount = unviewedTracksCount
                        )

                        else -> Icon(
                            painter = painterResource(
                                if (selected) destinationEntry.selectedIconResId
                                else destinationEntry.unselectedIconResId
                            ),
                            contentDescription = stringResource(destinationEntry.titleResId)
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(destinationEntry.titleResId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = false,
                onClick = { onSelect(destinationEntry.key) },
            )
        }
    }
}

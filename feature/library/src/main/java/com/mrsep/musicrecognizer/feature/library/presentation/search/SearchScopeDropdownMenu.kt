package com.mrsep.musicrecognizer.feature.library.presentation.search

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import kotlinx.collections.immutable.ImmutableSet
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun SearchScopeDropdownMenu(
    onSearchScopeChanged: (Set<TrackDataField>) -> Unit,
    searchScope: ImmutableSet<TrackDataField>,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { menuExpanded = !menuExpanded }) {
            Icon(
                painter = painterResource(UiR.drawable.outline_tune_24),
                contentDescription = stringResource(StringsR.string.search_in)
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = MaterialTheme.shapes.small,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.search_in)) },
                enabled = false,
                onClick = {},
                colors = MenuDefaults.itemColors(
                    disabledTextColor = MaterialTheme.colorScheme.tertiary
                ),
            )
            HorizontalDivider(modifier = Modifier.alpha(0.5f))
            TrackDataField.entries.forEach { field ->
                val selected = searchScope.contains(field)
                DropdownMenuItem(
                    text = { Text(text = field.getTitle()) },
                    modifier = Modifier.semantics {
                        role = Role.Checkbox
                        selected = selected
                    },
                    onClick = {
                        if (searchScope.size != 1 || !selected) {
                            val newSearchScope =
                                if (selected) searchScope - field else searchScope + field
                            onSearchScopeChanged(newSearchScope)
                        }
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(UiR.drawable.outline_check_24),
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer {
                                alpha = if (selected) 1f else 0f
                            }
                        )
                    }
                )
            }
        }
    }
}

@Stable
@Composable
internal fun TrackDataField.getTitle() = when (this) {
    TrackDataField.Title -> stringResource(StringsR.string.title)
    TrackDataField.Artist -> stringResource(StringsR.string.artist)
    TrackDataField.Album -> stringResource(StringsR.string.album)
}

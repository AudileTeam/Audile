package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.FontSize
import com.mrsep.musicrecognizer.feature.track.presentation.track.AlbumArtwork
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun LyricsScreenTopBar(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    artworkThumbUrl: String?,
    artworkUrl: String?,
    isScrolled: Boolean,
    selectedCount: Int,
    onDeselectAll: () -> Unit,
    onSelectAll: () -> Unit,
    onBackPressed: () -> Unit,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    onLyricsStyleClick: () -> Unit,
    lyricsFontSize: FontSize,
    onChangeLyricsFontSize: (newSize: FontSize) -> Unit,
    createSeedColor: Boolean,
    onSeedColorCreated: (Int) -> Unit,
) {
    val defaultColors = TopAppBarDefaults.topAppBarColors()
    val containerColor by animateColorAsState(
        targetValue = if (isScrolled)
            defaultColors.scrolledContainerColor
        else
            defaultColors.containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )
    val selectionTransition = updateTransition(
        targetState = selectedCount != 0,
        label = "selectionTransition"
    )
    val topBarHeight = TopAppBarDefaults.TopAppBarExpandedHeight
    TopAppBar(
        expandedHeight = topBarHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier
            .drawBehind {
                drawRect(color = containerColor)
            }
            .fontSizeHorizontalSwitcher(
                enabled = selectedCount == 0,
                fontSize = lyricsFontSize,
                onChangeFontSize = onChangeLyricsFontSize
            ),
        title = {
            selectionTransition.AnimatedContent(
                contentAlignment = Alignment.CenterStart
            ) { selectionMode ->
                if (!selectionMode) {
                    TrackInfoRow(
                        artworkThumbUrl = artworkThumbUrl,
                        artworkUrl = artworkUrl,
                        title = title,
                        artist = artist,
                        createSeedColor = createSeedColor,
                        onSeedColorCreated = onSeedColorCreated,
                        modifier = Modifier
                            .height(topBarHeight)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = onBackPressed,
                            )
                        ,
                    )
                } else {
                    Text(
                        text = if (selectedCount == 0) " " else "$selectedCount",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

        },
        navigationIcon = {
            selectionTransition.AnimatedContent(
                contentAlignment = Alignment.CenterStart
            ) { selectionMode ->
                if (selectionMode) {
                    IconButton(onClick = onDeselectAll) {
                        Icon(
                            painter = painterResource(UiR.drawable.outline_close_24),
                            contentDescription = stringResource(StringsR.string.disable_multi_selection_mode)
                        )
                    }
                } else {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            painter = painterResource(UiR.drawable.outline_arrow_back_24),
                            contentDescription = stringResource(StringsR.string.nav_back)
                        )
                    }
                }
            }
        },
        actions = {
            selectionTransition.AnimatedContent(
                contentAlignment = Alignment.CenterEnd
            ) { selectionMode ->
                if (selectionMode) {
                    Row(horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_select_all_24),
                                contentDescription = stringResource(StringsR.string.select_all)
                            )
                        }
                        IconButton(onClick = onCopyClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_content_copy_24),
                                contentDescription = stringResource(StringsR.string.copy)
                            )
                        }
                        IconButton(onClick = onShareClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_share_24),
                                contentDescription = stringResource(StringsR.string.share)
                            )
                        }

                    }
                } else {
                    Row(horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onShareClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_share_24),
                                contentDescription = stringResource(StringsR.string.share)
                            )
                        }
                        IconButton(onClick = onLyricsStyleClick) {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_format_size_24),
                                contentDescription = stringResource(StringsR.string.text_style)
                            )
                        }

                    }
                }
            }
        },
    )
}

@Composable
internal fun LyricsScreenLoadingTopBar(
    onBackPressed: () -> Unit,
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
    )
}

@Composable
private fun TrackInfoRow(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    artworkThumbUrl: String?,
    artworkUrl: String?,
    createSeedColor: Boolean,
    onSeedColorCreated: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (artworkThumbUrl != null || artworkUrl != null) {
            AlbumArtwork(
                artworkThumbUrl = artworkThumbUrl,
                artworkUrl = artworkUrl,
                elevation = 1.dp,
                shape = MaterialTheme.shapes.extraSmall,
                createSeedColor = createSeedColor,
                onSeedColorCreated = onSeedColorCreated,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                        mode = LineHeightStyle.Mode.Minimum
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                        mode = LineHeightStyle.Mode.Minimum
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import coil3.compose.AsyncImage
import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.domain.track.model.TrackPreview
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AutoRecognitionBar(
    state: AutoRecognitionState,
    track: TrackPreview?,
    onToggle: () -> Unit,
    onOpenTrack: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state is AutoRecognitionState.Running
    val closed by animateFloatAsState(
        targetValue = if (running) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = BarSpringStiffness),
        label = "autoRecognitionBar",
    )
    val liveContent = barContent(state, track)
    val presentation = rememberBarPresentation(running = running, live = liveContent)
    val content = if (running) liveContent else presentation.frozenContent
    val spinning = state is AutoRecognitionState.Running &&
        (state.phase == AutoRecognitionState.Phase.Recording ||
            state.phase == AutoRecognitionState.Phase.Recognizing)

    val collapsedContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerp(
        MaterialTheme.colorScheme.secondaryContainer,
        collapsedContainer,
        closed,
    )
    val toggleColor = lerp(
        MaterialTheme.colorScheme.secondary,
        collapsedContainer,
        closed
    )
    val iconColor = lerp(
        MaterialTheme.colorScheme.onSecondary,
        MaterialTheme.colorScheme.onSurfaceVariant,
        closed,
    )
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val density = LocalDensity.current
    val bubblePx = with(density) { BarHeight.roundToPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight),
    ) {
        val availablePx = constraints.maxWidth
        val bubble = bubblePx.coerceAtMost(availablePx)
        val openWidthPx = availablePx
            .coerceAtMost(with(density) { BarMaxWidth.roundToPx() })
            .coerceAtLeast(bubble)
        val widthPx = lerpFloat(openWidthPx.toFloat(), bubble.toFloat(), closed)
            .roundToInt()
        // Open: centered. Closed: the bubble's trailing edge is the row's end.
        // offset {} is layout-direction aware, so this LTR x is mirrored in RTL.
        val x = lerpFloat(
            (availablePx - openWidthPx) / 2f,
            (availablePx - bubble).toFloat(),
            closed,
        ).roundToInt()
        Surface(
            modifier = Modifier
                .offset { IntOffset(x, 0) }
                .width(with(density) { widthPx.toDp() })
                .fillMaxHeight(),
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            ) {
                BarContents(
                    openWidthPx = openWidthPx,
                    content = content,
                    running = running,
                    spinning = spinning,
                    suppressStatusSlide = presentation.suppressStatusSlide,
                    detailAlpha = 1f - closed,
                    toggleColor = toggleColor,
                    iconColor = iconColor,
                    contentColor = contentColor,
                    onToggle = onToggle,
                    onOpenTrack = onOpenTrack,
                    onOpenLibrary = onOpenLibrary,
                )
            }
        }
    }
}

/**
 * Holds the open label so it fades with the pill, instead of vanishing when turned off.
 * Also skips the status slide for whatever is already showing when the feature turns on.
 */
@Composable
private fun rememberBarPresentation(
    running: Boolean,
    live: BarContent,
): BarPresentation {
    val presentation = remember { BarPresentation(live, running) }
    if (running) presentation.frozenContent = live
    if (!running) presentation.suppressStatusSlide = true
    SideEffect {
        if (running) presentation.suppressStatusSlide = false
    }
    return presentation
}

@Stable
private class BarPresentation(
    content: BarContent,
    running: Boolean,
) {
    var frozenContent by mutableStateOf(content)
    var suppressStatusSlide by mutableStateOf(!running)
}

@Composable
private fun BarContents(
    openWidthPx: Int,
    content: BarContent,
    running: Boolean,
    spinning: Boolean,
    suppressStatusSlide: Boolean,
    detailAlpha: Float,
    toggleColor: Color,
    iconColor: Color,
    contentColor: Color,
    onToggle: () -> Unit,
    onOpenTrack: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val hasAction = running && content.hasAction
    Box(modifier = Modifier.fillMaxSize()) {
        // Full pill, behind the toggle. Its ripple is the pill, not the text row.
        // The toggle is drawn later and keeps the presses on its own slot.
        // Semantics of this click live on the text, so they do not cover the switch.
        if (hasAction) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {},
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { handleBarClick(content, onOpenTrack, onOpenLibrary) },
                )
            }
        }
        Row(
            modifier = Modifier
                // Open width, pinned to the end: the toggle stays on the
                // surface's trailing edge and the surface clips the rest.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(openWidthPx, constraints.maxHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(constraints.maxWidth - openWidthPx, 0)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = detailAlpha }
                    .textSemantics(
                        content = content,
                        hasAction = hasAction,
                        running = running,
                        onOpenTrack = onOpenTrack,
                        onOpenLibrary = onOpenLibrary,
                    )
                    .padding(start = BarStartPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = content,
                    contentKey = { it.contentKey },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                    transitionSpec = {
                        if (suppressStatusSlide) {
                            EnterTransition.None togetherWith ExitTransition.None using null
                        } else {
                            val enter = fadeIn(tween(StatusFadeInMillis)) +
                                    slideInVertically(tween(StatusFadeInMillis)) { it / 3 }
                            val exit = fadeOut(tween(StatusFadeOutMillis)) +
                                    slideOutVertically(tween(StatusFadeOutMillis)) { -it / 3 }
                            enter togetherWith exit using null
                        }
                    },
                    label = "autoRecognitionStatus",
                ) { current ->
                    BarStatus(content = current, contentColor = contentColor)
                }
            }
            BarToggle(
                running = running,
                spinning = spinning,
                containerColor = toggleColor,
                iconColor = iconColor,
                onClick = onToggle,
            )
        }
    }
}

private fun Modifier.textSemantics(
    content: BarContent,
    hasAction: Boolean,
    running: Boolean,
    onOpenTrack: (String) -> Unit,
    onOpenLibrary: () -> Unit,
): Modifier = when {
    hasAction -> clearAndSetSemantics {
        role = Role.Button
        contentDescription = content.spokenDescription()
        onClick {
            handleBarClick(content, onOpenTrack, onOpenLibrary)
            true
        }
    }
    // Plain status is merged inside BarStatus. Merging on this row would also
    // join the outgoing and incoming titles for the whole crossfade.
    running -> this
    else -> clearAndSetSemantics {}
}

private fun handleBarClick(
    content: BarContent,
    onOpenTrack: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    when (content) {
        is BarContent.Track -> onOpenTrack(content.id)
        is BarContent.Status -> if (content.openLibrary) onOpenLibrary()
        BarContent.Empty -> Unit
    }
}

@Composable
private fun BarStatus(
    content: BarContent,
    contentColor: Color,
) {
    val title: String
    val subtitle: String
    val artworkUrl: String?
    when (content) {
        BarContent.Empty -> return
        is BarContent.Status -> {
            title = content.title
            subtitle = content.subtitle
            artworkUrl = null
        }
        is BarContent.Track -> {
            title = content.title
            subtitle = content.artist
            artworkUrl = content.artworkUrl
        }
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content is BarContent.Track) {
            BarArtwork(url = artworkUrl, tint = contentColor)
            Spacer(Modifier.width(ArtworkToTextGap))
        } else {
            Spacer(Modifier.width(TextStatusExtraStartPadding))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(TitleToSubtitlePadding))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = SubtitleAlpha),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BarArtwork(
    url: String?,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(ArtworkSize)
            .clip(CircleShape)
            .background(tint.copy(alpha = ArtworkPlaceholderAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.rounded_music_note_48),
            contentDescription = null,
            tint = tint.copy(alpha = ArtworkPlaceholderIconAlpha),
            modifier = Modifier.size(24.dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun rememberIconRotation(spinning: Boolean): Animatable<Float, AnimationVector1D> {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) {
            rotation.settleUpright()
            return@LaunchedEffect
        }
        while (isActive) {
            if (rotation.value >= FullTurnDegrees) {
                rotation.snapTo(rotation.value % FullTurnDegrees)
            }
            rotation.animateTo(
                targetValue = rotation.value + FullTurnDegrees,
                animationSpec = tween(durationMillis = SpinCycleMillis, easing = LinearEasing),
            )
        }
    }
    return rotation
}

private suspend fun Animatable<Float, AnimationVector1D>.settleUpright() {
    // The two arrows are the same picture every half turn.
    val pastUpright = value % IconUprightDegrees
    if (pastUpright <= 0.5f) return
    val left = IconUprightDegrees - pastUpright
    animateTo(
        targetValue = value + left,
        animationSpec = tween(
            durationMillis = (left / IconUprightDegrees * SpinSettleMillis)
                .roundToInt()
                .coerceAtLeast(1),
            easing = EaseOut,
        ),
    )
}

@Composable
private fun BarToggle(
    running: Boolean,
    spinning: Boolean,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    val rotation = rememberIconRotation(spinning)
    val label = stringResource(StringsR.string.quick_tile_auto_recognition)
    val clickLabel = stringResource(
        if (running) {
            StringsR.string.notification_auto_recognition_action_turn_off
        } else {
            StringsR.string.notification_auto_recognition_action_turn_on
        },
    )
    // The slot is the whole bubble. Closed: the pill and this control are the same circle.
    // Open: the filled disc sits in the middle of the slot.
    Box(
        modifier = Modifier
            .width(BarHeight)
            .fillMaxHeight()
            .clearAndSetSemantics {
                role = Role.Switch
                contentDescription = label
                toggleableState = if (running) ToggleableState.On else ToggleableState.Off
                onClick(label = clickLabel) {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ToggleFilledContainerSize)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.rounded_autorenew_rotated_24),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotation.value },
                )
            }
        }
    }
}

@Composable
private fun barContent(
    state: AutoRecognitionState,
    track: TrackPreview?,
): BarContent {
    val running = state as? AutoRecognitionState.Running ?: return BarContent.Empty
    val onTitle = stringResource(StringsR.string.notification_auto_recognition_status_title_on)
    val defaultSubtitle = stringResource(StringsR.string.notification_auto_recognition_status_subtitle_on)
    val nowPlayingTitle = stringResource(StringsR.string.quick_tile_auto_recognition_now_playing)
    val songsFound = running.sessionTrackIds.size
    val songsSubtitle = pluralStringResource(
        StringsR.plurals.notification_auto_recognition_songs_found,
        songsFound,
        songsFound,
    )
    val blocker = when (running.phase) {
        AutoRecognitionState.Phase.WaitingForNetwork -> stringResource(StringsR.string.notification_auto_recognition_status_waiting_network)
        AutoRecognitionState.Phase.PausedLowBattery -> stringResource(StringsR.string.notification_auto_recognition_status_low_battery)
        AutoRecognitionState.Phase.PausedDuringCall -> stringResource(StringsR.string.notification_auto_recognition_status_during_call)
        AutoRecognitionState.Phase.Recording,
        AutoRecognitionState.Phase.Recognizing,
        AutoRecognitionState.Phase.BetweenAttempts,
        -> null
    }
    return when {
        blocker != null -> BarContent.Status(title = onTitle, subtitle = blocker)
        track != null -> BarContent.Track(
            id = track.id,
            artworkUrl = track.artworkThumbUrl ?: track.artworkUrl,
            title = track.title.takeIf { it.isNotBlank() } ?: nowPlayingTitle,
            artist = track.artist,
        )
        songsFound > 0 -> BarContent.Status(
            title = onTitle,
            subtitle = songsSubtitle,
            openLibrary = true,
        )
        else -> BarContent.Status(title = onTitle, subtitle = defaultSubtitle)
    }
}

@Immutable
private sealed interface BarContent {
    data object Empty : BarContent

    data class Status(
        val title: String,
        val subtitle: String,
        val openLibrary: Boolean = false,
    ) : BarContent

    data class Track(
        val id: String,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
    ) : BarContent

    val hasAction: Boolean
        get() = when (this) {
            is Track -> true
            is Status -> openLibrary
            Empty -> false
        }

    fun spokenDescription(): String {
        val parts = when (this) {
            is Track -> listOf(title, artist)
            is Status -> listOf(title, subtitle)
            Empty -> emptyList()
        }
        return parts.filter { it.isNotEmpty() }.joinToString(separator = ", ")
    }

    /** What the status crossfade keys on. */
    val contentKey: Any
        get() = when (this) {
            Empty -> this
            is Status -> title to subtitle
            is Track -> id
        }
}

private val BarHeight = 68.dp
private val BarMaxWidth = 560.dp

private val BarStartPadding = 8.dp
private val TextStatusExtraStartPadding = 16.dp
private val TitleToSubtitlePadding = 2.dp

private val ToggleFilledContainerSize = 52.dp

private val ArtworkSize = 52.dp
private val ArtworkToTextGap = 12.dp

private const val SubtitleAlpha = 0.72f
private const val ArtworkPlaceholderAlpha = 0.12f
private const val ArtworkPlaceholderIconAlpha = 0.8f

private const val BarSpringStiffness = 380f

private const val StatusFadeInMillis = 220
private const val StatusFadeOutMillis = 140

private const val SpinCycleMillis = 1500
private const val IconUprightDegrees = 180f
private const val FullTurnDegrees = 360f
private const val SpinSettleMillis = 300

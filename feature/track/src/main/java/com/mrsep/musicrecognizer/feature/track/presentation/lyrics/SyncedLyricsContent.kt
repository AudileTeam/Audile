package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.compose.LifecycleStartEffect
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsStyle
import com.mrsep.musicrecognizer.core.domain.track.model.SyncedLyrics
import com.mrsep.musicrecognizer.core.ui.components.LyricsPlayer
import com.mrsep.musicrecognizer.core.ui.components.rememberMultiSelectionState
import com.mrsep.musicrecognizer.core.ui.util.copyTextToClipboard
import com.mrsep.musicrecognizer.core.ui.util.shareText
import com.mrsep.musicrecognizer.core.ui.components.LyricsPlayer.Companion.rememberLyricsPlayer
import com.mrsep.musicrecognizer.feature.track.presentation.track.verticalFadingEdges
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import com.mrsep.musicrecognizer.core.ui.R as UiR
import com.mrsep.musicrecognizer.core.strings.R as StringsR

internal const val SelectionAnimationStiffness = Spring.StiffnessMedium
private const val InactiveTextAlpha = 0.5f
private const val ScrollAnimationDurationMs = 350
private val ScrollAnimationEasing = FastOutSlowInEasing

@Composable
internal fun SyncedLyricsContent(
    lyrics: SyncedLyrics,
    title: String,
    artist: String,
    artworkThumbUrl: String?,
    artworkUrl: String?,
    trackDuration: Duration?,
    recognizedAt: Duration?,
    recognitionDate: Instant,
    lyricsStyle: LyricsStyle,
    lyricsTextStyle: TextStyle,
    onBackPressed: () -> Unit,
    onShowLyricsStyleSettings: () -> Unit,
    onChangeLyricsStyle: (newStyle: LyricsStyle) -> Unit,
    createSeedColor: Boolean,
    onSeedColorCreated: (Int) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // It's expected that the lyrics should start from zero
    val lyrics = remember(lyrics) {
        val originalLines = lyrics.lines
        val firstLine = originalLines.firstOrNull()
        when {
            firstLine == null || firstLine.timestamp == Duration.ZERO -> lyrics
            else -> SyncedLyrics(
                buildList(capacity = originalLines.size + 1) {
                    add(SyncedLyrics.Line(Duration.ZERO, ""))
                    addAll(originalLines)
                }
            )
        }
    }
    val lines = lyrics.lines

    val trackDuration = remember(trackDuration) {
        val minLyricsDuration = lines.last().timestamp + 2.seconds
        trackDuration?.coerceAtLeast(minLyricsDuration) ?: minLyricsDuration
    }

    val listState = rememberLazyListState()
    val lyricsPlayer = rememberLyricsPlayer()
    val multiSelectionState = rememberMultiSelectionState(
        lines, lyricsPlayer.isPlaying,
        selectedIds = emptyList<Int>()
    )

    BackHandler(
        enabled = multiSelectionState.hasSelected,
        onBack = multiSelectionState::deselectAll
    )

    DisposableEffect(lyricsPlayer.isPlaying) {
        view.keepScreenOn = lyricsPlayer.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    var syncPoint: SyncPoint? by rememberSaveable(stateSaver = SyncPoint.Saver) {
        mutableStateOf(
            recognizedAt?.let { SyncPoint(time = recognitionDate, playbackPosition = recognizedAt) }
        )
    }
    // Stop the player when leaving the screen and restart it when on return
    LifecycleStartEffect(Unit) effect@{
        val disposeAction = onStopOrDispose { lyricsPlayer.stop() }
        val (syncTime, syncPosition) = syncPoint ?: return@effect disposeAction
        if (multiSelectionState.hasSelected || lyricsPlayer.isPlaying) return@effect disposeAction
        val currentPlaybackOffset = syncPosition + durationBetween(syncTime, Instant.now())
        if (currentPlaybackOffset !in Duration.ZERO.rangeUntil(trackDuration)) return@effect disposeAction
        val currentLineIndex = lyrics.currentLineIndex(currentPlaybackOffset) ?: return@effect disposeAction

        if (lyrics.hasMeaningfulRemainingDuration(currentLineIndex, trackDuration)) {
            lyricsPlayer.start(currentPlaybackOffset, trackDuration)
            coroutineScope.launch {
                listState.scrollToItem(
                    index = currentLineIndex,
                    scrollOffset = -listState.layoutInfo.viewportSize.height / 2
                )
            }
        }
        disposeAction
    }

    val currentLineIndex = rememberSaveable { mutableIntStateOf(0) }
    // Scroll to the current line if it is in the viewport area
    // Warn: we can't click on a line to seek lyrics while scrolling animation is playing
    LaunchedEffect(lyricsPlayer.isPlaying, lyricsTextStyle) {
        if (!lyricsPlayer.isPlaying) return@LaunchedEffect
        if (!listState.canScrollForward && !listState.canScrollBackward) return@LaunchedEffect
        snapshotFlow { currentLineIndex.intValue }.collectLatest { index ->
            val currentLine = listState.layoutInfo
                .visibleItemsInfo
                .fastFirstOrNull { it.index == index }
            if (currentLine != null && !listState.isScrollInProgress) {
                val itemHeight = currentLine.size
                val center = listState.layoutInfo.viewportSize.height / 2
                val topPadding = listState.layoutInfo.beforeContentPadding
                val distance = currentLine.offset - center + topPadding + (itemHeight / 2)
                if (distance < 0 && !listState.canScrollBackward) return@collectLatest
                if (distance > 0 && !listState.canScrollForward) return@collectLatest
                listState.animateScrollBy(
                    value = distance * 1f,
                    animationSpec = tween(
                        durationMillis = ScrollAnimationDurationMs,
                        easing = ScrollAnimationEasing
                    )
                )
            }
        }
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            val updatedStyle by rememberUpdatedState(lyricsStyle)
            LyricsScreenTopBar(
                title = title,
                artist = artist,
                artworkThumbUrl = artworkThumbUrl,
                artworkUrl = artworkUrl,
                isScrolled = listState.canScrollBackward,
                selectedCount = multiSelectionState.selectedCount,
                onSelectAll = { multiSelectionState.select(List(lines.size) { it }) },
                onDeselectAll = multiSelectionState::deselectAll,
                onBackPressed = onBackPressed,
                onCopyClick = {
                    context.copyTextToClipboard(lines.joinLines(multiSelectionState.getSelected()))
                    multiSelectionState.deselectAll()
                },
                onShareClick = {
                    if (multiSelectionState.hasSelected) {
                        context.shareText(
                            subject = "",
                            body = lines.joinLines(multiSelectionState.getSelected())
                        )
                    } else {
                        val trackInfo = "$title - $artist"
                        context.shareText(
                            subject = trackInfo,
                            body = "$trackInfo\n\n${lyrics.plain}"
                        )
                    }
                    multiSelectionState.deselectAll()
                },
                onLyricsStyleClick = onShowLyricsStyleSettings,
                lyricsFontSize = lyricsStyle.fontSize,
                onChangeLyricsFontSize = { newSize ->
                    onChangeLyricsStyle(updatedStyle.copy(fontSize = newSize))
                },
                createSeedColor = createSeedColor,
                onSeedColorCreated = onSeedColorCreated,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalFadingEdges(
                        topFadeLength = 56.dp,
                        topFadeInitialColor = MaterialTheme.colorScheme.surface
                    ),
                state = listState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = if (lines.first().content.isBlank()) 32.dp else 48.dp,
                    bottom = if (lines.last().content.isBlank()) 32.dp else 48.dp
                ),
                verticalArrangement = remember(lyricsTextStyle.fontSize) {
                    with(density) {
                        Arrangement.spacedBy((lyricsTextStyle.fontSize.toDp() - 16.dp).coerceAtLeast(4.dp))
                    }
                },
                horizontalAlignment = when (lyricsTextStyle.textAlign) {
                    TextAlign.Center -> Alignment.CenterHorizontally
                    TextAlign.Start -> Alignment.Start
                    TextAlign.End -> Alignment.End
                    else -> error("Not implemented")
                }
            ) {
                itemsIndexed(items = lines) { index, (timestamp, line) ->
                    val nextTimestamp = remember(index, timestamp) {
                        lines.getOrNull(index + 1)?.timestamp ?: trackDuration
                    }

                    // Shift active line to compensate for scroll and highlight animation duration
                    // Capped to half of the previous line's duration to prevent overlaps
                    val activeStart = remember(index, timestamp) {
                        if (index == 0) return@remember timestamp
                        val prevDuration = timestamp - lines[index - 1].timestamp
                        timestamp - minOf(ScrollAnimationDurationMs.milliseconds, prevDuration / 2)
                    }
                    val activeEnd = remember(index, timestamp, nextTimestamp) {
                        val currentDuration = nextTimestamp - timestamp
                        nextTimestamp - minOf(ScrollAnimationDurationMs.milliseconds, currentDuration / 2)
                    }

                    if (line.isNotBlank()) {
                        LyricLine(
                            line = line,
                            time = timestamp,
                            nextTime = nextTimestamp,
                            activeStart = activeStart,
                            activeEnd = activeEnd,
                            style = lyricsTextStyle,
                            lyricsPlayer = lyricsPlayer,
                            onBecomeCurrent = { currentLineIndex.intValue = index },
                            isSelected = multiSelectionState.isSelected(index),
                            onClick = {
                                if (multiSelectionState.hasSelected) {
                                    multiSelectionState.toggleSelection(index)
                                } else {
                                    lyricsPlayer.stop()
                                    lyricsPlayer.start(timestamp, trackDuration)
                                    syncPoint = SyncPoint(Instant.now(), timestamp)
                                }
                            },
                            onLongPress = {
                                if (!lyricsPlayer.isPlaying) {
                                    multiSelectionState.toggleSelection(index)
                                }
                            },
                        )
                    } else {
                        BubblesLine(
                            time = timestamp,
                            nextTime = nextTimestamp,
                            lastLine = index == lines.lastIndex,
                            activeStart = activeStart,
                            activeEnd = activeEnd,
                            textStyle = lyricsTextStyle,
                            lyricsPlayer = lyricsPlayer,
                            onBecomeCurrent = { currentLineIndex.intValue = index },
                        )
                    }
                }
            }
        }

        val isLyricsDragged by listState.interactionSource.collectIsDraggedAsState()
        var floatingButtonVisible by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(
            lyricsPlayer.isPlaying,
            isLyricsDragged,
            listState.canScrollBackward,
            multiSelectionState.hasSelected
        ) {
            when {
                multiSelectionState.hasSelected -> {
                    floatingButtonVisible = false
                }

                !lyricsPlayer.isPlaying -> {
                    floatingButtonVisible = !listState.canScrollBackward
                }

                isLyricsDragged -> {
                    floatingButtonVisible = true
                }

                lyricsPlayer.isPlaying -> {
                    delay(3.seconds)
                    floatingButtonVisible = false
                }
            }
        }
        AnimatedVisibility(
            visible = floatingButtonVisible,
            enter = slideInVertically { fillHeight -> fillHeight },
            exit = slideOutVertically { fillHeight -> fillHeight },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = {
                    if (lyricsPlayer.isPlaying) {
                        lyricsPlayer.stop()
                        syncPoint = null
                    } else if (!multiSelectionState.hasSelected) {
                        coroutineScope.launch {
                            listState.scrollToItem(0)
                            lyricsPlayer.start(Duration.ZERO, trackDuration)
                            syncPoint = SyncPoint(Instant.now(), Duration.ZERO)
                        }
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .safeDrawingPadding(),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 3.dp,
                    focusedElevation = 3.dp,
                    hoveredElevation = 3.dp
                ),
            ) {
                if (lyricsPlayer.isPlaying) {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_stop_fill1_24),
                        contentDescription = stringResource(StringsR.string.lyrics_stop_autoscroll)
                    )
                } else {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_play_arrow_fill1_24),
                        contentDescription = stringResource(StringsR.string.lyrics_start_autoscroll)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricLine(
    modifier: Modifier = Modifier,
    line: String,
    time: Duration,
    nextTime: Duration,
    activeStart: Duration,
    activeEnd: Duration,
    style: TextStyle,
    lyricsPlayer: LyricsPlayer,
    onBecomeCurrent: () -> Unit,
    isSelected: Boolean,
    selectionContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isCurrentLine by remember {
        derivedStateOf { lyricsPlayer.currentPosition in activeStart.rangeUntil(activeEnd) }
    }
    LaunchedEffect(isCurrentLine) {
        if (isCurrentLine) onBecomeCurrent()
    }
//    val progressFraction by remember {
//        derivedStateOf {
//            ((lyricsPlayer.currentPosition - time) / (nextTime - time)).toFloat().coerceIn(0f, 1f)
//        }
//    }
    val textAlpha by animateFloatAsState(
        targetValue = when {
            isCurrentLine || !lyricsPlayer.isPlaying -> 1f
            else -> InactiveTextAlpha
        },
        animationSpec = tween(durationMillis = ScrollAnimationDurationMs, easing = ScrollAnimationEasing),
        label = "lyricsTextAlpha"
    )
    val selectionContainerAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = SelectionAnimationStiffness),
        label = "selectionContainerAlpha"
    )
    Text(
        text = line,
        style = style,
        modifier = modifier
            .graphicsLayer {
                alpha = textAlpha
            }
            .drawBehind {
                drawRoundRect(
                    color = selectionContainerColor,
                    alpha = selectionContainerAlpha,
                    cornerRadius = 8.dp.toPx().run(::CornerRadius),
                )
            }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(if (style.fontSize > 14.sp) 8.dp else 6.dp)
    )
}

@Composable
private fun BubblesLine(
    modifier: Modifier = Modifier,
    time: Duration,
    nextTime: Duration,
    activeStart: Duration,
    activeEnd: Duration,
    lastLine: Boolean,
    textStyle: TextStyle,
    lyricsPlayer: LyricsPlayer,
    onBecomeCurrent: () -> Unit,
) {
    val isCurrentLine by remember {
        derivedStateOf { lyricsPlayer.currentPosition in activeStart.rangeUntil(activeEnd) }
    }
    LaunchedEffect(isCurrentLine) {
        if (isCurrentLine) onBecomeCurrent()
    }

    val progressFraction by remember {
        derivedStateOf {
            ((lyricsPlayer.currentPosition - time) / (nextTime - time)).toFloat().coerceIn(0f, 1f)
        }
    }
    val firstBubbleProgress by remember {
        derivedStateOf { (progressFraction / 0.33f).coerceIn(0f, 1f) }
    }
    val secondBubbleProgress by remember {
        derivedStateOf { ((progressFraction - 0.33f) / 0.33f).coerceIn(0f, 1f) }
    }
    val thirdBubbleProgress by remember {
        derivedStateOf { ((progressFraction - 0.33f * 2) / 0.33f).coerceIn(0f, 1f) }
    }

    val density = LocalDensity.current
    val bubbleMaxSize = remember(density, textStyle) {
        val weightFactor = when (textStyle.fontWeight?.weight) {
            in 500..1000 -> 1.2f
            else -> 1f
        }
        with(density) { (textStyle.fontSize.toDp() * weightFactor * 0.35f).coerceAtLeast(4.dp) }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "bubblesTransition")
    val maxTranslationX = with(LocalDensity.current) { bubbleMaxSize.toPx() / 12 }
    val firstBubbleTranslationX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = maxTranslationX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "firstBubbleTranslationX"
    )
    val secondBubbleTranslationX by infiniteTransition.animateFloat(
        initialValue = -maxTranslationX * 0.65f,
        targetValue = maxTranslationX * 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 500,
                offsetType = StartOffsetType.FastForward
            )
        ),
        label = "secondBubbleTranslationX"
    )
    val thirdBubbleTranslationX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -maxTranslationX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 1000,
                offsetType = StartOffsetType.FastForward
            )
        ),
        label = "thirdBubbleTranslationX"
    )

    val scale by animateFloatAsState(
        targetValue = when {
            progressFraction < 0.97f -> 1f
            lastLine -> 0.8f
            else -> 1.2f
        },
        label = "bubblesFinalScale"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(bubbleMaxSize),
    ) {
        AnimatedVisibility(
            visible = isCurrentLine && lyricsPlayer.isPlaying,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(bubbleMaxSize * 0.75f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Bubble(
                    modifier = Modifier.size(bubbleMaxSize),
                    animationProgress = firstBubbleProgress,
                    translationX = firstBubbleTranslationX,
                    translationOffset = secondBubbleTranslationX
                )
                Bubble(
                    modifier = Modifier.size(bubbleMaxSize),
                    animationProgress = secondBubbleProgress,
                    translationX = secondBubbleTranslationX,
                    translationOffset = thirdBubbleTranslationX
                )
                Bubble(
                    modifier = Modifier.size(bubbleMaxSize),
                    animationProgress = thirdBubbleProgress,
                    translationX = thirdBubbleTranslationX,
                    translationOffset = firstBubbleTranslationX
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    modifier: Modifier = Modifier,
    animationProgress: Float,
    translationX: Float,
    translationOffset: Float,
    bounceFactor: Float = 0.2f,
    color: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        withTransform(
            transformBlock = {
                translate(
                    left = translationX,
                    top = -size.height * bounceFactor *
                            (sin((animationProgress - 0.25f) * 2 * PI.toFloat()) / 2 + 0.5f) +
                            translationX * translationOffset / 2
                )
                val scale = 0.5f + animationProgress / 2
                scale(scaleX = scale, scaleY = scale)
            }
        ) {
            drawCircle(
                color = color,
                alpha = InactiveTextAlpha + animationProgress / 2
            )
        }
    }
}

@Immutable
private data class SyncPoint(
    val time: Instant,
    val playbackPosition: Duration,
) {
    companion object {
        // Avoid listSaver: it encodes an empty list as null, which rememberSaveable treats as missing state
        val Saver = Saver<SyncPoint?, List<String>>(
            save = { syncPoint ->
                if (syncPoint != null) {
                    listOf(
                        syncPoint.time.toString(),
                        syncPoint.playbackPosition.toIsoString()
                    )
                } else {
                    emptyList()
                }
            },
            restore = { saved ->
                if (saved.size == 2) {
                    SyncPoint(
                        time = Instant.parse(saved[0]),
                        playbackPosition = Duration.parseIsoString(saved[1])
                    )
                } else {
                    null
                }
            }
        )
    }
}

private fun durationBetween(startInclusive: Instant, endExclusive: Instant): Duration {
    return java.time.Duration.between(startInclusive, endExclusive).toKotlinDuration()
}

private fun List<SyncedLyrics.Line>.joinLines(indexes: Set<Int>): String {
    return joinLines(indexes) { it.content }
}

internal fun <T> List<T>.joinLines(indexes: Set<Int>, transform: ((T) -> CharSequence)? = null): String {
    return indexes.sorted()
        .mapNotNull { getOrNull(it) }
        .joinToString("\n", transform = transform)
}

package com.mrsep.musicrecognizer.feature.track.presentation.track

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.mrsep.musicrecognizer.core.ui.findActivity
import com.mrsep.musicrecognizer.core.ui.util.shareImage
import com.mrsep.musicrecognizer.core.ui.util.shareText
import com.mrsep.musicrecognizer.feature.track.presentation.utils.ImageShareUtils.getImageFileForSharing
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AlbumArtworkShield(
    artworkUrl: String,
    title: String,
    artist: String,
    album: String?,
    year: String?,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackPressed)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val bottomBarBehaviour = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    val zoomState = rememberZoomState()
    val imageZoomed = remember {
        derivedStateOf { zoomState.scale > 1.1f }
    }
    val forceHideControlPanels = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(imageZoomed) {
        snapshotFlow { imageZoomed.value }.collect { isZoomed ->
            if (isZoomed) forceHideControlPanels.value = false
        }
    }
    val showControlPanels = remember {
        derivedStateOf { !forceHideControlPanels.value && !imageZoomed.value }
    }

    val controlsAlpha = animateFloatAsState(
        targetValue = if (showControlPanels.value) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "controlsAlpha"
    )

    if (!showControlPanels.value) {
        HideSystemBars()
    }
    // Collapse bars manually
    LaunchedEffect(Unit) {
        snapshotFlow { showControlPanels.value }
            .collect { showControls ->
                if (showControls) {
                    launch { topBarScrollBehavior.state.expand() }
                    launch { bottomBarBehaviour.state.expand() }
                } else {
                    launch { topBarScrollBehavior.state.collapse() }
                    launch { bottomBarBehaviour.state.collapse() }
                }
            }
    }

    var sharingJob by remember { mutableStateOf<Job?>(null) }
    fun shareImage() {
        if (sharingJob?.isActive == true) return
        sharingJob = scope.launch {
            val fileNameUnprocessed = album ?: "$title - $artist"
            val imageUri = getImageFileForSharing(
                imageUrl = artworkUrl,
                fileName = fileNameUnprocessed,
                fileNameFallback = resources.getString(StringsR.string.artwork),
                context = context
            )
            imageUri?.let {
                context.shareImage(
                    subject = "",
                    body = fileNameUnprocessed,
                    imageUri = imageUri
                )
            }
        }
    }

    fun shareLink() {
        context.shareText(subject = "", body = artworkUrl)
    }

    val tintColor = MaterialTheme.colorScheme.surface.copy(0.7f)
    Surface(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(Size.ORIGINAL)
                    .crossfade(false)
                    .build(),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painter,
                contentDescription = stringResource(StringsR.string.artwork),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(
                        zoomState = zoomState,
                        onTap = {
                            if (!imageZoomed.value) {
                                forceHideControlPanels.value = !forceHideControlPanels.value
                            }
                        }
                    )
            )

            TopAppBar(
                modifier = Modifier.graphicsLayer { alpha = controlsAlpha.value },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tintColor,
                    scrolledContainerColor = tintColor,
                ),
                windowInsets = WindowInsets.systemBarsIgnoringVisibility
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                scrollBehavior = topBarScrollBehavior,
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            painter = painterResource(UiR.drawable.outline_arrow_back_24),
                            contentDescription = stringResource(StringsR.string.nav_back),
                        )
                    }
                },
                actions = {
                    ArtworkShareDropdownMenu(
                        onShareLink = ::shareLink,
                        onShareImage = ::shareImage
                    )
                },
            )
            BottomAppBar(
                modifier = Modifier
                    .graphicsLayer { alpha = controlsAlpha.value }
                    .align(Alignment.BottomStart),
                containerColor = tintColor,
                windowInsets = WindowInsets.systemBarsIgnoringVisibility
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                scrollBehavior = bottomBarBehaviour,
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    album?.let {
                        Text(
                            text = album,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                    year?.let {
                        Text(
                            text = year,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HideSystemBars() {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        val window = context.findActivity().window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            hide(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
        }

        onStopOrDispose {
            insetsController.apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }
}

private suspend fun TopAppBarState.expand(
    animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium),
) {
    animate(
        initialValue = heightOffset,
        targetValue = 0f,
        animationSpec = animationSpec
    ) { value, _ ->
        heightOffset = value
    }
    contentOffset = 0f
}

private suspend fun BottomAppBarState.expand(
    animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium),
) {
    animate(
        initialValue = heightOffset,
        targetValue = 0f,
        animationSpec = animationSpec
    ) { value, _ ->
        heightOffset = value
    }
    contentOffset = 0f
}

private suspend fun TopAppBarState.collapse(
    animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium),
) {
    animate(
        initialValue = heightOffset,
        targetValue = heightOffsetLimit,
        animationSpec = animationSpec
    ) { value, _ ->
        heightOffset = value
    }
    contentOffset = heightOffsetLimit
}

private suspend fun BottomAppBarState.collapse(
    animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium),
) {
    animate(
        initialValue = heightOffset,
        targetValue = heightOffsetLimit,
        animationSpec = animationSpec
    ) { value, _ ->
        heightOffset = value
    }
    contentOffset = heightOffsetLimit
}

@Composable
private fun ArtworkShareDropdownMenu(
    onShareLink: () -> Unit,
    onShareImage: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = !menuExpanded }) {
            Icon(
                painter = painterResource(UiR.drawable.outline_share_24),
                contentDescription = stringResource(StringsR.string.share),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = MaterialTheme.shapes.small,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.artwork_share_menu_link)) },
                onClick = {
                    menuExpanded = false
                    onShareLink()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_link_24),
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.artwork_share_menu_image)) },
                onClick = {
                    menuExpanded = false
                    onShareImage()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_image_24),
                        contentDescription = null
                    )
                }
            )
        }
    }
}

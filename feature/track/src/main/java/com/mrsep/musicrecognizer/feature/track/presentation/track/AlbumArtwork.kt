package com.mrsep.musicrecognizer.feature.track.presentation.track

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.mrsep.musicrecognizer.core.ui.util.forwardingPainter
import com.mrsep.musicrecognizer.core.ui.util.generateThemeSeedColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AlbumArtwork(
    modifier: Modifier = Modifier,
    artworkThumbUrl: String?,
    artworkUrl: String?,
    elevation: Dp,
    shape: Shape,
    onLoadedArtworkClick: (() -> Unit)? = null,
    createSeedColor: Boolean,
    onSeedColorCreated: (Int) -> Unit,
) {
    val placeholder = forwardingPainter(
        painter = painterResource(UiR.drawable.outline_album_fill1_24),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        alpha = 0.3f
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumbPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(artworkThumbUrl)
            .allowHardware(!createSeedColor)
            .crossfade(200)
            .build(),
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            if (createSeedColor && !state.result.request.allowHardware) {
                scope.launch(Dispatchers.Default) {
                    val seedColor = state.result.image.toBitmap().generateThemeSeedColor() ?: return@launch
                    withContext(Dispatchers.Main) { onSeedColorCreated(seedColor) }
                }
            }
        }
    )
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(artworkUrl)
            .placeholderMemoryCacheKey(artworkThumbUrl)
            .allowHardware(!createSeedColor)
            .crossfade(200)
            .build(),
        error = thumbPainter,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            if (createSeedColor && !state.result.request.allowHardware) {
                scope.launch(Dispatchers.Default) {
                    val seedColor = state.result.image.toBitmap().generateThemeSeedColor() ?: return@launch
                    withContext(Dispatchers.Main) { onSeedColorCreated(seedColor) }
                }
            }
        }
    )
    val painterState by painter.state.collectAsStateWithLifecycle()
    Image(
        painter = painter,
        contentDescription = stringResource(StringsR.string.artwork),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape
            )
            .then(
                if (onLoadedArtworkClick != null) {
                    Modifier.clickable(
                        enabled = painterState is AsyncImagePainter.State.Success,
                        role = Role.Button,
                        onClickLabel = stringResource(StringsR.string.artwork),
                        onClick = onLoadedArtworkClick
                    )
                } else {
                    Modifier
                }
            )
    )
}

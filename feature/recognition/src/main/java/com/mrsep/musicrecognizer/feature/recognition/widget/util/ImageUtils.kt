package com.mrsep.musicrecognizer.feature.recognition.widget.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.annotation.Px
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Scale
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.WidgetArtworkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal object ImageUtils {

    private const val BYTES_PER_PIXEL = 4

    /**
     * Returns maximum bytes allowed per the appwidget limits for bitmaps in the remote views.
     */
    fun Context.getMaxWidgetMemoryAllowedSizeInBytes(): Int {
        val size = resources.displayMetrics.run { Size(widthPixels, heightPixels) }
        // Cap memory usage at 1.5 times the size of the display
        // 1.5 * 4 bytes/pixel * w * h ==> 6 * w * h
        // See https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java;l=274-281;drc=389cb6f54a5a5bb8dea540f57a3a8ac3c3c1c758
        return 6 * size.width * size.height
    }

    /**
     * Largest bitmap of [aspectRatio] (width / height) that stays inside the widget memory budget.
     */
    fun getMaxPossibleImageSize(aspectRatio: Double, memoryLimitBytes: Int, maxImages: Int): Size {
        // for each orientation (landscape, portrait, +2 for fold).
        val maxPixels = ((memoryLimitBytes / 4) / maxImages) / BYTES_PER_PIXEL
        // Truncate both axes. Rounding both up can push w * h over the budget.
        val width = sqrt(maxPixels.toDouble() * aspectRatio).toInt().coerceAtLeast(1)
        val height = sqrt(maxPixels.toDouble() / aspectRatio).toInt().coerceAtLeast(1)
        return Size(width, height)
    }

    // It's expected that the image has been previously loaded into the image disk cache
    suspend fun Context.getWidgetArtworkOrNull(
        url: String,
        @Px widthPx: Int,
        @Px heightPx: Int,
        artworkStyle: WidgetArtworkStyle,
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(this@getWidgetArtworkOrNull)
                .data(url)
                .size(widthPx, heightPx)
                .scale(Scale.FILL)
                .networkCachePolicy(CachePolicy.DISABLED)
                .transformations(
                    when (artworkStyle) {
                        WidgetArtworkStyle.CircleCrop -> {
                            CircleCropTransformation()
                        }
                        is WidgetArtworkStyle.RoundedCorners -> {
                            RoundedCornersTransformation(artworkStyle.artworkCornerRadiusPx.toFloat())
                        }
                    }
                )
                .build()
            imageLoader.execute(request).image?.toBitmap()
        }
    }
}

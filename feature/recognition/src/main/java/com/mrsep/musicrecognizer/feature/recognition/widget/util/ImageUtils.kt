package com.mrsep.musicrecognizer.feature.recognition.widget.util

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.util.SizeF
import android.view.Display
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
     *
     * The system cap is `6 * width * height`, taken from [Display.getRealSize] once at boot.
     * It stays on that mode if the resolution changes later.
     * [android.content.res.Resources.getDisplayMetrics] can report a larger mode, so the smallest
     * of the real size and every supported mode stays under the cap.
     */
    fun Context.getMaxWidgetMemoryAllowedSizeInBytes(): Int {
        val area = displayAreaCandidates().minOrNull()
            ?: resources.displayMetrics.let { metrics ->
                metrics.widthPixels.toLong() * metrics.heightPixels
            }
        // 1.5 * 4 bytes/pixel * w * h ==> 6 * w * h
        return (6L * area).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun Context.displayAreaCandidates(): List<Long> {
        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return emptyList()
        val areas = ArrayList<Long>(8)
        val realSize = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(realSize)
        areas += realSize.x.toLong() * realSize.y
        // Lower-resolution modes. The cap may have been frozen to one of these at boot.
        for (mode in display.supportedModes) {
            areas += mode.physicalWidth.toLong() * mode.physicalHeight
        }
        return areas.filter { it > 0L }
    }

    /**
     * Mirrors Glance: [AppWidgetManager.OPTION_APPWIDGET_SIZES] on API 31+, otherwise the
     * portrait and landscape sizes estimated from min/max width and height.
     */
    fun Bundle.sizeBucketCount(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                return sizes.distinct().size
            }
        }
        // Glance Exact fallback
        // https://github.com/androidx/androidx/blob/e6d33dd5d0a60001a5784d84123b05308d35f410/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/SizeBox.kt#L93-L112
        // https://github.com/androidx/androidx/blob/e6d33dd5d0a60001a5784d84123b05308d35f410/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/AppWidgetUtils.kt#L82-L117
        val minWidth = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxWidth = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minHeight = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxHeight = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        // Portrait is minWidth x maxHeight, landscape is maxWidth x minHeight.
        val buckets = ArrayList<Pair<Int, Int>>(2)
        if (minWidth > 0 && maxHeight > 0) buckets += minWidth to maxHeight
        if (maxWidth > 0 && minHeight > 0) buckets += maxWidth to minHeight
        return buckets.distinct().size.coerceAtLeast(1)
    }

    /**
     * Largest bitmap of [aspectRatio] (width / height) that stays inside the widget memory budget.
     *
     * [memoryLimitBytes] is shared by every size in one update. [sizeBuckets] is how many distinct
     * sizes Glance embeds, and [maxImages] is how many distinct bitmaps one size contributes.
     */
    fun getMaxPossibleImageSize(
        aspectRatio: Double,
        memoryLimitBytes: Int,
        maxImages: Int,
        sizeBuckets: Int,
    ): Size {
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val images = maxImages.coerceAtLeast(1)
        val buckets = sizeBuckets.coerceAtLeast(1)
        val maxPixels = memoryLimitBytes.toLong() / buckets / images / BYTES_PER_PIXEL
        // Truncate both axes. Rounding both up can push w * h over the budget.
        val width = sqrt(maxPixels.toDouble() * safeAspect).toInt().coerceAtLeast(1)
        val height = sqrt(maxPixels.toDouble() / safeAspect).toInt().coerceAtLeast(1)
        return Size(width, height)
    }

    // It's expected that the image has been previously loaded into the image disk cache.
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

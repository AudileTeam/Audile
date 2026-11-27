package com.mrsep.musicrecognizer.feature.recognition.widget.ui

import android.os.Build
import android.util.TypedValue
import androidx.annotation.Px
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import com.mrsep.musicrecognizer.core.ui.util.dpToPx
import com.mrsep.musicrecognizer.core.ui.util.pxToDp
import com.mrsep.musicrecognizer.feature.recognition.R
import com.mrsep.musicrecognizer.feature.recognition.widget.util.ImageUtils
import com.mrsep.musicrecognizer.feature.recognition.widget.util.ImageUtils.getMaxWidgetMemoryAllowedSizeInBytes

/**
 * Calculate sizes manually due to the lack of flexible size and weight modifiers in Glance
 * and need in rounded corner artwork on API less than 31 (runtime bitmap transformation).
 * Open for refactoring.
 */
internal sealed class RecognitionWidgetLayout {

    abstract val artworkWidth: Dp
    abstract val artworkHeight: Dp

    @get:Px
    abstract val artworkWidthPx: Int

    @get:Px
    abstract val artworkHeightPx: Int

    abstract val artworkStyle: WidgetArtworkStyle

    val showArtwork: Boolean
        get() = artworkWidthPx > 0 && artworkHeightPx > 0

    data class Circle(
        override val artworkWidth: Dp,
        override val artworkHeight: Dp,
        @Px override val artworkWidthPx: Int,
        @Px override val artworkHeightPx: Int,
        val widgetSize: Dp,
        val recognitionButtonMaxSize: Dp,
    ) : RecognitionWidgetLayout() {
        override val artworkStyle = WidgetArtworkStyle.CircleCrop

        val artworkSize: Dp
            get() = artworkWidth
    }

    data class Tile(
        override val artworkWidth: Dp,
        override val artworkHeight: Dp,
        @Px override val artworkWidthPx: Int,
        @Px override val artworkHeightPx: Int,
        override val artworkStyle: WidgetArtworkStyle.RoundedCorners,
        val recognitionButtonMaxSize: Dp,
        val statusButtonTopPadding: Dp,
        val statusLabelTopPadding: Dp,
    ) : RecognitionWidgetLayout()

    data class Row(
        override val artworkWidth: Dp,
        override val artworkHeight: Dp,
        @Px override val artworkWidthPx: Int,
        @Px override val artworkHeightPx: Int,
        override val artworkStyle: WidgetArtworkStyle.RoundedCorners,
        val recognitionButtonMaxSize: Dp,
    ) : RecognitionWidgetLayout() {
        val artworkSize: Dp
            get() = artworkWidth
    }

    companion object {

        val circleWidgetBorderWidth = 3.dp
        val tileWidgetBorderWidth = 0.dp

        val widgetPadding = 8.dp
        val contentPadding = 8.dp
        val statusExtraStartPadding = 4.dp
        val artworkToTextPadding = 12.dp

        val titleTextSize = 16.sp
        val subtitleTextSize = 14.sp

        // From API 33, includeFontPadding becomes redundant for TextView
        // https://medium.com/androiddevelopers/fixing-font-padding-in-compose-text-768cd232425b
        val shouldIncludeFontPadding = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        val subtitleTopPadding = if (shouldIncludeFontPadding) 4.dp else 6.dp

        val dividerWidth = 1.dp
        val dividerHorizontalPadding = 8.dp
        val dividerVerticalPadding = 4.dp

        val tileOverlayButtonSize = 48.dp

        private val maxRowArtworkSize = 64.dp
        private val minArtworkSize = 40.dp

        fun buttonHorizontalPadding(buttonSize: Dp) = if (buttonSize <= 36.dp) 4.dp else 8.dp

        @Composable
        fun widgetInnerRadius(): Dp = with(LocalContext.current) {
            pxToDp(resources.getDimension(R.dimen.widget_inner_radius)).dp
        }

        @Composable
        fun widgetOuterRadius(): Dp = with(LocalContext.current) {
            pxToDp(resources.getDimension(R.dimen.widget_background_radius)).dp
        }

        @Composable
        fun buttonScaleFactor(): Float = with(LocalContext.current) {
            val outValue = TypedValue()
            resources.getValue(R.dimen.widget_button_scale_factor, outValue, true)
            return outValue.float
        }

        @Composable
        fun fromLocalSize(): RecognitionWidgetLayout = with(LocalSize.current) {
            when (chooseForm(width, height)) {
                WidgetForm.Circle -> circleLayout(width, height)
                WidgetForm.Row -> rowLayout(width, height)
                WidgetForm.Tile -> tileLayout(width, height)
            }
        }

        @Composable
        private fun circleLayout(width: Dp, height: Dp): Circle {
            val minSide = width.coerceAtMost(height).coerceAtLeast(0.dp)
            val artworkSize = (minSide - circleWidgetBorderWidth * 2).coerceAtLeast(0.dp)
            val (artworkWidthPx, artworkHeightPx) = artworkPxOrZero(artworkSize, artworkSize)
            return Circle(
                artworkWidth = artworkSize,
                artworkHeight = artworkSize,
                artworkWidthPx = artworkWidthPx,
                artworkHeightPx = artworkHeightPx,
                widgetSize = minSide,
                recognitionButtonMaxSize = circleButtonSize(minSide),
            )
        }

        @Composable
        private fun tileLayout(width: Dp, height: Dp): Tile {
            val artworkWidth = (width - tileWidgetBorderWidth * 2).coerceAtLeast(0.dp)
            val artworkHeight = (height - tileWidgetBorderWidth * 2).coerceAtLeast(0.dp)
            val (artworkWidthPx, artworkHeightPx) = artworkPxOrZero(artworkWidth, artworkHeight)
            val minSide = width.coerceAtMost(height)
            val recognitionButtonMaxSize = (minSide.value * 0.4f).dp.coerceIn(56.dp, 240.dp)
            // Very tall tiles stop the status block at 3.25× width. The cell below stays empty.
            val statusBlockHeight = height.coerceAtMost(width * 3.25f)
            val labelTop = statusBlockHeight * 0.70f
            val buttonTopPadding: Dp
            val labelTopPadding: Dp
            if (recognitionButtonMaxSize > labelTop) {
                buttonTopPadding = 0.dp
                labelTopPadding = 0.dp
            } else {
                buttonTopPadding = (labelTop - recognitionButtonMaxSize) / 2
                labelTopPadding = labelTop - buttonTopPadding - recognitionButtonMaxSize
            }
            return Tile(
                artworkWidth = artworkWidth,
                artworkHeight = artworkHeight,
                artworkWidthPx = artworkWidthPx,
                artworkHeightPx = artworkHeightPx,
                artworkStyle = roundedCornersStyle(
                    artworkWidth = artworkWidth,
                    artworkWidthPx = artworkWidthPx,
                    artworkHeightPx = artworkHeightPx,
                    cornerRadius = widgetOuterRadius() - tileWidgetBorderWidth,
                    bottomFading = true,
                ),
                recognitionButtonMaxSize = recognitionButtonMaxSize,
                statusButtonTopPadding = buttonTopPadding,
                statusLabelTopPadding = labelTopPadding,
            )
        }

        @Composable
        private fun rowLayout(width: Dp, height: Dp): Row {
            val contentHeight = (height - widgetPadding * 2 - contentPadding * 2).coerceAtLeast(0.dp)
            val buttonInnerHeight = (height - widgetPadding * 2).coerceAtLeast(0.dp)
            val tier = when {
                width < 220.dp || buttonInnerHeight < 48.dp -> 36.dp
                width < 280.dp || buttonInnerHeight < 64.dp -> 40.dp
                else -> 56.dp
            }
            val buttonSize = if (buttonInnerHeight < 36.dp) {
                buttonInnerHeight
            } else {
                tier.coerceAtMost(buttonInnerHeight)
            }
            val artworkSize = rowArtworkSize(width, contentHeight, buttonSize)
            val (artworkWidthPx, artworkHeightPx) = artworkPxOrZero(artworkSize, artworkSize)
            return Row(
                artworkWidth = artworkSize,
                artworkHeight = artworkSize,
                artworkWidthPx = artworkWidthPx,
                artworkHeightPx = artworkHeightPx,
                artworkStyle = roundedCornersStyle(
                    artworkWidth = artworkSize,
                    artworkWidthPx = artworkWidthPx,
                    artworkHeightPx = artworkHeightPx,
                    cornerRadius = widgetInnerRadius(),
                ),
                recognitionButtonMaxSize = buttonSize,
            )
        }

        private enum class WidgetForm {
            Circle,
            Row,
            Tile,
        }

        private val rowMinWidth = 160.dp
        private val rowMinHeight = 56.dp
        private val rowMaxHeight = 160.dp
        private val rowMinAspect = 1.5f

        private val tileMinWidth = 140.dp
        private val tileMinHeight = 140.dp

        // Circle when the slot is too small, too short, or too narrow for the other two.
        // Row is a short wide strip. Tile fills a cell that is large or tall enough.
        private fun chooseForm(width: Dp, height: Dp): WidgetForm = when {
            width <= 0.dp || height <= 0.dp -> WidgetForm.Circle
            width >= rowMinWidth &&
                height >= rowMinHeight &&
                height <= rowMaxHeight &&
                width / height >= rowMinAspect -> WidgetForm.Row
            width >= tileMinWidth && height >= tileMinHeight -> WidgetForm.Tile
            else -> WidgetForm.Circle
        }

        private fun circleButtonSize(minSide: Dp): Dp {
            val inner = (minSide - circleWidgetBorderWidth * 2).coerceAtLeast(0.dp)
            if (inner <= 0.dp) return 0.dp
            val preferred = (minSide.value * 0.75f).toInt().dp.coerceAtLeast(56.dp)
            return preferred.coerceAtMost(inner)
        }

        private fun rowArtworkSize(
            width: Dp,
            contentHeight: Dp,
            buttonSize: Dp,
        ): Dp {
            val textMin = 100.dp
            val buttonWithPadding = buttonSize + buttonHorizontalPadding(buttonSize) * 2
            val dividerWithPadding = dividerHorizontalPadding * 2 + dividerWidth
            val budget = width - widgetPadding * 2 - contentPadding * 2 - textMin -
                dividerWithPadding - buttonWithPadding - artworkToTextPadding
            val fitted = contentHeight
                .coerceAtMost(budget)
                .coerceAtMost(maxRowArtworkSize)
                .coerceAtLeast(0.dp)
            return if (fitted >= minArtworkSize) fitted else 0.dp
        }

        @Composable
        private fun artworkPxOrZero(width: Dp, height: Dp): Pair<Int, Int> {
            if (width < minArtworkSize || height < minArtworkSize) return 0 to 0
            return cappedArtworkSizePx(width, height)
        }

        @Composable
        private fun cappedArtworkSizePx(width: Dp, height: Dp): Pair<Int, Int> {
            val context = LocalContext.current
            val widthPx = context.dpToPx(width.value).toInt().coerceAtLeast(0)
            val heightPx = context.dpToPx(height.value).toInt().coerceAtLeast(0)
            if (widthPx == 0 || heightPx == 0) return 0 to 0
            val limit = ImageUtils.getMaxPossibleImageSize(
                aspectRatio = widthPx.toDouble() / heightPx.toDouble(),
                memoryLimitBytes = context.getMaxWidgetMemoryAllowedSizeInBytes(),
                maxImages = 1,
            )
            val scale = minOf(
                1f,
                limit.width.toFloat() / widthPx,
                limit.height.toFloat() / heightPx,
            )
            if (scale == 1f) return widthPx to heightPx
            return (widthPx * scale).toInt().coerceAtLeast(1) to
                (heightPx * scale).toInt().coerceAtLeast(1)
        }

        @Composable
        private fun artworkCornerRadiusPx(
            imageSize: Dp,
            imageSizePx: Int,
            cornerRadius: Dp,
        ): Int {
            val imageDensity = imageSizePx / imageSize.value
            return (imageDensity * cornerRadius.value).toInt()
        }

        @Composable
        private fun roundedCornersStyle(
            artworkWidth: Dp,
            artworkWidthPx: Int,
            artworkHeightPx: Int,
            cornerRadius: Dp,
            bottomFading: Boolean = false,
        ): WidgetArtworkStyle.RoundedCorners {
            val radiusPx = if (artworkWidthPx > 0 && artworkHeightPx > 0) {
                artworkCornerRadiusPx(
                    imageSize = artworkWidth,
                    imageSizePx = artworkWidthPx,
                    cornerRadius = cornerRadius,
                )
            } else {
                0
            }
            return WidgetArtworkStyle.RoundedCorners(
                artworkCornerRadiusPx = radiusPx,
                bottomFading = bottomFading,
            )
        }
    }
}

internal sealed interface WidgetArtworkStyle {

    data object CircleCrop : WidgetArtworkStyle

    data class RoundedCorners(
        @Px val artworkCornerRadiusPx: Int,
        val bottomFading: Boolean = false,
    ) : WidgetArtworkStyle
}

package com.mrsep.musicrecognizer.feature.recognition.widget.util

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.ui.util.dpToPx
import com.mrsep.musicrecognizer.core.ui.util.pxToDp
import com.mrsep.musicrecognizer.core.ui.util.spToPx
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.buttonHorizontalPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.contentPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.dividerHorizontalPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.dividerWidth
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.shouldIncludeFontPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTopPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.titleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.widgetPadding

internal data class LineCounts(
    val titleLines: Int,
    val subtitleLines: Int,
)

internal fun rowLineCounts(
    context: Context,
    widgetWidth: Dp,
    widgetHeight: Dp,
    buttonSize: Dp,
    title: String,
    subtitle: String?,
    subtitleMaxLines: Int,
    reservedArtworkWidth: Dp = 0.dp,
    extraStartPadding: Dp = 0.dp,
): LineCounts {
    val subtitleText = subtitle?.takeIf { it.isNotEmpty() }
    val textWidth = (
        widgetWidth -
            (widgetPadding + contentPadding) * 2 -
            (dividerHorizontalPadding * 2 + dividerWidth) -
            (buttonSize + buttonHorizontalPadding(buttonSize) * 2) -
            reservedArtworkWidth -
            extraStartPadding
        ).coerceAtLeast(0.dp)
    val textHeight = (
        widgetHeight -
            (widgetPadding + contentPadding) * 2 -
            textCenteringPadding(context, subtitleText != null)
        ).coerceAtLeast(0.dp)
    return FontUtils.measureLineCounts(
        context = context,
        title = title,
        subtitle = subtitleText,
        titleSize = titleTextSize,
        subtitleSize = subtitleTextSize,
        width = textWidth,
        availableHeight = textHeight,
        gap = subtitleTopPadding,
        subtitleMaxLines = subtitleMaxLines,
        includeFontPadding = shouldIncludeFontPadding,
    )
}

internal fun textCenteringPadding(context: Context, withSubtitle: Boolean): Dp {
    if (!shouldIncludeFontPadding) return 0.dp
    val titlePadding = FontUtils.measureTextExtraPaddings(context, titleTextSize)
    val bottom = if (withSubtitle) {
        FontUtils.measureTextExtraPaddings(context, subtitleTextSize).second
    } else {
        titlePadding.second
    }
    val padding = titlePadding.first - bottom
    return if (padding > 0.dp) padding else 0.dp
}

internal object FontUtils {

    fun measureLineCounts(
        context: Context,
        title: String,
        subtitle: String?,
        titleSize: TextUnit,
        subtitleSize: TextUnit,
        width: Dp,
        availableHeight: Dp,
        gap: Dp,
        subtitleMaxLines: Int,
        includeFontPadding: Boolean,
    ): LineCounts {
        if (width <= 0.dp) return LineCounts(1, 0)
        val widthPx = context.dpToPx(width.value).toInt().coerceAtLeast(1)
        fun height(text: String, size: TextUnit, lines: Int): Dp = context.pxToDp(
            measureTextHeight(context, text, size, widthPx, lines, includeFontPadding).toFloat()
        ).dp
        val subtitleText = subtitle?.takeIf { it.isNotEmpty() }
        return resolveLineCounts(
            availableHeight = availableHeight,
            titleOneLine = height(title, titleSize, 1),
            titleTwoLines = height(title, titleSize, 2),
            subtitleOneLine = subtitleText?.let { height(it, subtitleSize, 1) },
            subtitleTwoLines = subtitleText
                ?.takeIf { subtitleMaxLines >= 2 }
                ?.let { height(it, subtitleSize, 2) },
            gap = gap,
        )
    }

    fun measureTextExtraPaddings(
        context: Context,
        fontSize: TextUnit,
    ): Pair<Dp, Dp> = with(context) {
        require(fontSize.isSp) { "Font size must be sp" }
        return Paint().run {
            this.textSize = spToPx(fontSize.value)
            val fontMetrics = this.fontMetrics
            val extraPaddingTop = pxToDp(fontMetrics.ascent - fontMetrics.top).dp
            val extraPaddingBottom = pxToDp(fontMetrics.bottom - fontMetrics.descent).dp
            extraPaddingTop to extraPaddingBottom
        }
    }

    private fun measureTextHeight(
        context: Context,
        text: String,
        fontSize: TextUnit,
        widthPx: Int,
        maxLines: Int,
        includeFontPadding: Boolean,
    ): Int {
        require(fontSize.isSp) { "Font size must be sp" }
        val textView = TextView(context).apply {
            this.text = text
            this.includeFontPadding = includeFontPadding
            this.maxLines = maxLines
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
        }
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return textView.measuredHeight
    }

    // Prefer the fullest text that fits, in this order:
    // max title lines + 2 subtitle lines, max title lines + 1 subtitle line,
    // 1 title line + 1 subtitle line, max title lines alone, otherwise a single title line.
    private fun resolveLineCounts(
        availableHeight: Dp,
        titleOneLine: Dp,
        titleTwoLines: Dp,
        subtitleOneLine: Dp?,
        subtitleTwoLines: Dp?,
        gap: Dp,
    ): LineCounts {
        fun height(titleLines: Int, subtitleLines: Int): Dp {
            val title = if (titleLines == 2) titleTwoLines else titleOneLine
            if (subtitleLines == 0 || subtitleOneLine == null) return title
            val subtitle = if (subtitleLines == 2 && subtitleTwoLines != null) {
                subtitleTwoLines
            } else {
                subtitleOneLine
            }
            return title + gap + subtitle
        }

        val maxTitleLines = if (titleTwoLines > titleOneLine) 2 else 1
        val maxSubtitleLines = when {
            subtitleOneLine == null -> 0
            subtitleTwoLines != null && subtitleTwoLines > subtitleOneLine -> 2
            else -> 1
        }
        fun fits(titleLines: Int, subtitleLines: Int) =
            height(titleLines, subtitleLines) <= availableHeight

        return when {
            maxSubtitleLines == 2 && fits(maxTitleLines, 2) -> LineCounts(maxTitleLines, 2)
            maxSubtitleLines >= 1 && fits(maxTitleLines, 1) -> LineCounts(maxTitleLines, 1)
            maxSubtitleLines >= 1 && maxTitleLines == 2 && fits(1, 1) -> LineCounts(1, 1)
            fits(maxTitleLines, 0) -> LineCounts(maxTitleLines, 0)
            else -> LineCounts(1, 0)
        }
    }
}

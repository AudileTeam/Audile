package com.mrsep.musicrecognizer.core.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.material3.dynamicColorScheme
import com.materialkolor.material3.ktx.animateColorScheme

@Composable
fun SwitchingMusicRecognizerTheme(
    seedColor: Color?,
    artworkBasedThemeEnabled: Boolean = true,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    usePureBlackForDarkTheme: Boolean = false,
    highContrastMode: Boolean = false,
    style: PaletteStyle = PaletteStyle.Vibrant,
    contrastLevel: Double = 0.0,
    animationSpec: FiniteAnimationSpec<Color> = tween(durationMillis = 800),
    content: @Composable () -> Unit,
) {
    val defaultColorScheme = MaterialTheme.colorScheme
    val colorScheme: ColorScheme by remember(
        artworkBasedThemeEnabled,
        seedColor,
        useDarkTheme,
        usePureBlackForDarkTheme,
        highContrastMode,
        style,
        contrastLevel,
    ) {
        derivedStateOf {
            val scheme = if (!artworkBasedThemeEnabled || seedColor == null) {
                defaultColorScheme
            } else {
                dynamicColorScheme(
                    seedColor = seedColor,
                    isAmoled = false,
                    isDark = useDarkTheme,
                    style = style,
                    contrastLevel = contrastLevel,
                    specVersion = ColorSpec.SpecVersion.SPEC_2021,
                )
            }
            val newBackgroundColor = when {
                highContrastMode -> if (useDarkTheme) Color.Black else Color.White
                usePureBlackForDarkTheme && useDarkTheme -> Color.Black
                else -> return@derivedStateOf scheme
            }
            scheme.copy(
                background = newBackgroundColor,
                surface = newBackgroundColor
            )
        }
    }
    MaterialTheme(
        colorScheme = animateColorScheme(colorScheme, animationSpec = { animationSpec }),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        content()
    }
}

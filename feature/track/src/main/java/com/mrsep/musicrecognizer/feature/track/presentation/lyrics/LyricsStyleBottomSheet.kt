package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.FontSize
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsStyle
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsStyleBottomSheet(
    sheetState: SheetState,
    style: LyricsStyle,
    onStyleChanged: (LyricsStyle) -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissClick,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Text(
            text = stringResource(StringsR.string.text_style),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .weight(1f, false)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = stringResource(StringsR.string.font_size))
                Spacer(Modifier.weight(1f))
                Text(text = style.fontSize.title())
            }
            Spacer(Modifier.height(8.dp))
            val fontSizeDesc = stringResource(StringsR.string.font_size)
            val fontSizeState = style.fontSize.title()
            Slider(
                value = style.fontSize.ordinal.toFloat(),
                onValueChange = { sliderValue ->
                    val fontSize = FontSize.entries[sliderValue.toInt()]
                    onStyleChanged(style.copy(fontSize = fontSize))
                },
                valueRange = 0f..3f,
                steps = 2,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics {
                        contentDescription = fontSizeDesc
                        stateDescription = fontSizeState
                    }
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BottomBarSwitch(
                    title = stringResource(StringsR.string.align_to_start),
                    checked = style.alignToStart,
                    onClick = {
                        onStyleChanged(style.copy(alignToStart = !style.alignToStart))
                    }
                )
                BottomBarSwitch(
                    title = stringResource(StringsR.string.bold_text),
                    checked = style.isBold,
                    onClick = {
                        onStyleChanged(style.copy(isBold = !style.isBold))
                    }
                )
                BottomBarSwitch(
                    title = stringResource(StringsR.string.high_contrast_text),
                    checked = style.isHighContrast,
                    onClick = {
                        onStyleChanged(style.copy(isHighContrast = !style.isHighContrast))
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BottomBarSwitch(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onClick() }
            )
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
private fun FontSize.title() = when (this) {
    FontSize.Small -> stringResource(StringsR.string.font_size_small)
    FontSize.Normal -> stringResource(StringsR.string.font_size_normal)
    FontSize.Large -> stringResource(StringsR.string.font_size_large)
    FontSize.Huge -> stringResource(StringsR.string.font_size_huge)
}

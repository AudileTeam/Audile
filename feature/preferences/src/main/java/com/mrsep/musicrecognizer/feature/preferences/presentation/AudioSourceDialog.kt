package com.mrsep.musicrecognizer.feature.preferences.presentation

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.ui.R
import com.mrsep.musicrecognizer.core.ui.components.DialogSwitch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun AudioSourceDialog(
    defaultAudioCaptureMode: AudioCaptureMode,
    mainButtonLongPressAudioCaptureMode: AudioCaptureMode,
    useAltDeviceSoundSource: Boolean,
    onChangeDefaultAudioCaptureMode: (AudioCaptureMode) -> Unit,
    onChangeMainButtonLongPressAudioCaptureMode: (AudioCaptureMode) -> Unit,
    onChangeUseAltDeviceSoundSource: (Boolean) -> Unit,
    onDismissClick: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(StringsR.string.audio_source_dialog_title))
        },
        confirmButton = {
            TextButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.close))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(StringsR.string.audio_source_dialog_default_mode_message))
                Spacer(Modifier.height(16.dp))
                AudioCaptureModeDropdownMenu(
                    modes = allOptions,
                    label = stringResource(StringsR.string.audio_source_dialog_default_mode_title),
                    selectedMode = defaultAudioCaptureMode,
                    onSelectMode = onChangeDefaultAudioCaptureMode,
                )
                Spacer(Modifier.height(16.dp))
                Text(text = stringResource(StringsR.string.audio_source_dialog_button_long_press_mode_message))
                Spacer(Modifier.height(16.dp))
                AudioCaptureModeDropdownMenu(
                    modes = allOptions,
                    label = stringResource(StringsR.string.audio_source_dialog_button_long_press_mode_title),
                    selectedMode = mainButtonLongPressAudioCaptureMode,
                    onSelectMode = onChangeMainButtonLongPressAudioCaptureMode,
                )
                Spacer(Modifier.height(16.dp))
                var showAltDeviceSourceDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // To capture device audio the app uses AudioPlaybackCapture API (Android 10+) by default
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        IconButton(
                            onClick = { showAltDeviceSourceDialog = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_help_24),
                                contentDescription = stringResource(StringsR.string.help),
                            )
                        }
                        DialogSwitch(
                            modifier = Modifier.weight(1f),
                            title = stringResource(StringsR.string.audio_source_dialog_use_alt_device_source),
                            checked = useAltDeviceSoundSource,
                            onClick = { onChangeUseAltDeviceSoundSource(!useAltDeviceSoundSource) },
                        )
                    } else {
                        Text(
                            text = stringResource(StringsR.string.audio_source_dialog_device_audio_limited),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { showAltDeviceSourceDialog = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_help_24),
                                contentDescription = stringResource(StringsR.string.help),
                            )
                        }
                    }
                }
                if (showAltDeviceSourceDialog) {
                    AltDeviceSourceHelpDialog { showAltDeviceSourceDialog = false }
                }
            }
        },
        onDismissRequest = onDismissClick
    )
}

@Composable
private fun AudioCaptureModeDropdownMenu(
    modifier: Modifier = Modifier,
    label: String,
    modes: ImmutableList<AudioCaptureMode>,
    selectedMode: AudioCaptureMode,
    onSelectMode: (AudioCaptureMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = selectedMode.getTitle()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            state = remember(title) { TextFieldState(title) },
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = MaterialTheme.shapes.small,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            modes.forEach { selectedMode ->
                DropdownMenuItem(
                    text = { Text(selectedMode.getTitle()) },
                    onClick = {
                        onSelectMode(selectedMode)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private val allOptions = AudioCaptureMode.entries.toImmutableList()

@Composable
private fun AudioCaptureMode.getTitle(): String {
    return when (this) {
        AudioCaptureMode.Microphone -> stringResource(StringsR.string.audio_capture_mode_microphone)
        AudioCaptureMode.Device -> stringResource(StringsR.string.audio_capture_mode_device)
        AudioCaptureMode.Auto -> stringResource(StringsR.string.audio_capture_mode_auto)
    }
}

@Composable
internal fun AltDeviceSourceHelpDialog(
    modifier: Modifier = Modifier,
    onDismissClick: () -> Unit
) {
    AlertDialog(
        modifier = modifier,
        title = {
            Text(text = stringResource(StringsR.string.audio_capture_mode_device))
        },
        confirmButton = {
            TextButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.close))
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(StringsR.string.audio_source_dialog_methods),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(StringsR.string.audio_source_dialog_screen_casting_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "• ${stringResource(StringsR.string.audio_source_dialog_screen_casting_bullet_1)}")
                    Text(text = "• ${stringResource(StringsR.string.audio_source_dialog_screen_casting_bullet_2)}")
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(StringsR.string.audio_source_dialog_alt_method_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "• ${stringResource(StringsR.string.audio_source_dialog_alt_method_bullet_1)}")
                    Text(text = "• ${stringResource(StringsR.string.audio_source_dialog_alt_method_bullet_2)}")
                    Text(text = "• ${stringResource(StringsR.string.audio_source_dialog_alt_method_bullet_3)}")
                }
            }
        },
        onDismissRequest = onDismissClick
    )
}

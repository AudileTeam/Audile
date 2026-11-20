package com.mrsep.musicrecognizer.feature.preferences.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.FallbackAction
import com.mrsep.musicrecognizer.core.domain.preferences.FallbackPolicy
import com.mrsep.musicrecognizer.core.ui.components.DialogSwitch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun FallbackPolicyDialog(
    fallbackPolicy: FallbackPolicy,
    onFallbackPolicyChanged: (FallbackPolicy) -> Unit,
    autoDeleteSavedRecordingOnMatch: Boolean,
    onAutoDeleteSavedRecordingOnMatchChanged: (Boolean) -> Unit,
    onDismissClick: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(StringsR.string.fallback_policy_dialog_title))
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
                Text(text = stringResource(StringsR.string.fallback_policy_dialog_message))
                FallbackActionsDropdownMenu(
                    options = allOptions,
                    label = stringResource(StringsR.string.fallback_trigger_bad_connection),
                    selectedOption = fallbackPolicy.badConnection,
                    onSelectOption = { option ->
                        onFallbackPolicyChanged(fallbackPolicy.copy(badConnection = option))
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
                FallbackActionsDropdownMenu(
                    options = ignoreOrSaveOptions,
                    label = stringResource(StringsR.string.fallback_trigger_no_matches_found),
                    selectedOption = fallbackPolicy.noMatches,
                    onSelectOption = { option ->
                        onFallbackPolicyChanged(fallbackPolicy.copy(noMatches = option))
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
                FallbackActionsDropdownMenu(
                    options = ignoreOrSaveOptions,
                    label = stringResource(StringsR.string.fallback_trigger_other_failures),
                    selectedOption = fallbackPolicy.anotherFailure,
                    onSelectOption = { option ->
                        onFallbackPolicyChanged(fallbackPolicy.copy(anotherFailure = option))
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
                DialogSwitch(
                    title = stringResource(StringsR.string.pref_title_auto_delete_sample_on_match),
                    checked = autoDeleteSavedRecordingOnMatch,
                    onClick = {
                        onAutoDeleteSavedRecordingOnMatchChanged(!autoDeleteSavedRecordingOnMatch)
                    },
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        },
        onDismissRequest = onDismissClick
    )
}

@Composable
private fun FallbackActionsDropdownMenu(
    modifier: Modifier = Modifier,
    label: String,
    options: ImmutableList<FallbackAction>,
    selectedOption: FallbackAction,
    onSelectOption: (FallbackAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = selectedOption.getTitle()
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
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption.getTitle()) },
                    onClick = {
                        onSelectOption(selectionOption)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private val allOptions = FallbackAction.entries.toImmutableList()
private val ignoreOrSaveOptions =
    listOf(FallbackAction.Ignore, FallbackAction.Save).toImmutableList()

@Composable
private fun FallbackAction.getTitle(): String {
    return when (this) {
        FallbackAction.Ignore -> stringResource(StringsR.string.fallback_action_ignore)
        FallbackAction.Save -> stringResource(StringsR.string.fallback_action_save_recording)
        FallbackAction.SaveAndLaunch -> stringResource(StringsR.string.fallback_action_save_recording_and_retry)
    }
}

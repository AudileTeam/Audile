package com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AuddServiceDialog(
    modifier: Modifier = Modifier,
    state: AuddPreferencesState,
    currentProvider: RecognitionProvider,
    onProviderChanged: (RecognitionProvider) -> Unit,
    onSaveClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    RecognitionServiceDialogBase(
        modifier = modifier,
        currentProvider = currentProvider,
        onProviderChanged = onProviderChanged,
        onSaveClick = onSaveClick,
        onDismissClick = onDismissClick,
    ) {
        AuddPreferences(state = state)
    }
}

@Composable
internal fun AcrCloudServiceDialog(
    modifier: Modifier = Modifier,
    state: AcrCloudPreferencesState,
    currentProvider: RecognitionProvider,
    onProviderChanged: (RecognitionProvider) -> Unit,
    onSaveClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    RecognitionServiceDialogBase(
        modifier = modifier,
        currentProvider = currentProvider,
        onProviderChanged = onProviderChanged,
        onSaveClick = onSaveClick,
        onDismissClick = onDismissClick,
    ) {
        AcrCloudPreferences(state = state)
    }
}

@Composable
internal fun ShazamServiceDialog(
    modifier: Modifier = Modifier,
    currentProvider: RecognitionProvider,
    onProviderChanged: (RecognitionProvider) -> Unit,
    onSaveClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    RecognitionServiceDialogBase(
        modifier = modifier,
        currentProvider = currentProvider,
        onProviderChanged = onProviderChanged,
        onSaveClick = onSaveClick,
        onDismissClick = onDismissClick,
    ) { }
}

@Composable
private fun RecognitionServiceDialogBase(
    modifier: Modifier = Modifier,
    currentProvider: RecognitionProvider,
    onProviderChanged: (RecognitionProvider) -> Unit,
    onSaveClick: () -> Unit,
    onDismissClick: () -> Unit,
    serviceConfiguration: @Composable () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        title = {
            Text(text = stringResource(StringsR.string.recognition_provider_dialog_title))
        },
        confirmButton = {
            TextButton(onClick = onSaveClick) {
                Text(text = stringResource(StringsR.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.close))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(StringsR.string.recognition_provider_dialog_message)
                )
                RecognitionProviderDropdownMenu(
                    options = RecognitionProvider.entries.toImmutableList(),
                    label = stringResource(StringsR.string.recognition_provider_dialog_title),
                    selectedOption = currentProvider,
                    onSelectOption = onProviderChanged,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                serviceConfiguration()
            }
        },
        onDismissRequest = onDismissClick
    )
}

@Composable
private fun RecognitionProviderDropdownMenu(
    modifier: Modifier = Modifier,
    label: String,
    options: ImmutableList<RecognitionProvider>,
    selectedOption: RecognitionProvider,
    onSelectOption: (RecognitionProvider) -> Unit,
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
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
                        expanded = false
                        onSelectOption(selectionOption)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
internal fun AuthenticationRow(
    modifier: Modifier = Modifier,
    serviceName: String,
    onHelpClick: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(StringsR.string.recognition_provider_dialog_format_auth, serviceName))
        IconButton(onClick = onHelpClick) {
            Icon(
                painter = painterResource(UiR.drawable.outline_help_24),
                contentDescription = stringResource(StringsR.string.help),
            )
        }
    }
}

@Stable
@Composable
internal fun RecognitionProvider.getTitle() = when (this) {
    RecognitionProvider.Audd -> stringResource(StringsR.string.audd)
    RecognitionProvider.AcrCloud -> stringResource(StringsR.string.acr_cloud)
    RecognitionProvider.Shazam -> stringResource(StringsR.string.shazam)
}

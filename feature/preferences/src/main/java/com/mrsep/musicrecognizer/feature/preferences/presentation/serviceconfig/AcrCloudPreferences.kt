package com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.ui.components.PasswordInputField
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun AcrCloudPreferences(
    state: AcrCloudPreferencesState,
    modifier: Modifier = Modifier,
) {
    var helpDialogVisible by rememberSaveable { mutableStateOf(false) }
    if (helpDialogVisible) {
        AcrCloudHelpDialog { helpDialogVisible = false }
    }
    Column(modifier = modifier) {
        AuthenticationRow(
            modifier = Modifier.fillMaxWidth(),
            serviceName = stringResource(StringsR.string.acr_cloud),
            onHelpClick = { helpDialogVisible = true }
        )
        val showHostError = state.isEmptyHost && state.shouldShowErrors.value
        AcrCloudHostDropdownMenu(
            label = stringResource(StringsR.string.host),
            options = AcrCloudRegion.entries.toImmutableList(),
            host = state.host.value,
            onHostChanged = { newHost -> state.host.value = newHost },
            error = stringResource(StringsR.string.text_field_must_not_be_empty)
                .takeIf { showHostError },
        )
        Spacer(modifier = Modifier.height(12.dp))
        val showAccessKeyError = state.isEmptyAccessKey && state.shouldShowErrors.value
        PasswordInputField(
            state = state.accessKey,
            label = stringResource(StringsR.string.access_key),
            error = stringResource(StringsR.string.text_field_must_not_be_empty)
                .takeIf { showAccessKeyError },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        val showAccessSecretError = state.isEmptyAccessSecret && state.shouldShowErrors.value
        PasswordInputField(
            state = state.accessSecret,
            label = stringResource(StringsR.string.access_secret),
            error = stringResource(StringsR.string.text_field_must_not_be_empty)
                .takeIf { showAccessSecretError },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AcrCloudHostDropdownMenu(
    modifier: Modifier = Modifier,
    label: String,
    options: ImmutableList<AcrCloudRegion>,
    host: String,
    onHostChanged: (String) -> Unit,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            state = remember(host) { TextFieldState(host) },
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = error?.let { { Text(error) } },
            isError = (error != null),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
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
                    text = { Text(selectionOption.title) },
                    onClick = {
                        onHostChanged(selectionOption.host)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

internal enum class AcrCloudRegion(
    val host: String,
    val title: String,
) {
    EUROPE(
        "identify-eu-west-1.acrcloud.com",
        "Europe"
    ),
    US_WEST(
        "identify-us-west-2.acrcloud.com",
        "US West"
    ),
    ASIA_PACIFIC(
        "identify-ap-southeast-1.acrcloud.com",
        "Asia Pacific"
    )
}

@Stable
internal class AcrCloudPreferencesState(config: AcrCloudConfig) {
    val host = mutableStateOf(config.host)
    val accessKey = TextFieldState(config.accessKey)
    val accessSecret = TextFieldState(config.accessSecret)

    var shouldShowErrors = mutableStateOf(false)

    val currentConfig
        get() = AcrCloudConfig(
            host = host.value,
            accessKey = accessKey.text.toString(),
            accessSecret = accessSecret.text.toString(),
        )

    val isConfigValid
        get() = host.value.isNotBlank() &&
                accessKey.text.isNotBlank() &&
                accessSecret.text.isNotBlank()

    val isEmptyHost
        get() = host.value.isBlank()

    val isEmptyAccessKey
        get() = accessKey.text.isBlank()

    val isEmptyAccessSecret
        get() = accessSecret.text.isBlank()

    fun showErrors() {
        shouldShowErrors.value = true
    }

    companion object {
        val Saver: Saver<AcrCloudPreferencesState, *> = listSaver(
            save = { state ->
                listOf(
                    state.host.value,
                    state.accessKey.text.toString(),
                    state.accessSecret.text.toString(),
                )
            },
            restore = { saved ->
                AcrCloudPreferencesState(
                    config = AcrCloudConfig(
                        host = saved[0],
                        accessKey = saved[1],
                        accessSecret = saved[2],
                    )
                )
            }
        )
    }
}

@Composable
internal fun rememberAcrCloudPreferencesState(config: AcrCloudConfig): AcrCloudPreferencesState {
    return rememberSaveable(
        inputs = arrayOf(config),
        saver = AcrCloudPreferencesState.Saver
    ) {
        AcrCloudPreferencesState(config)
    }
}

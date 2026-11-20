package com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mrsep.musicrecognizer.core.domain.preferences.AuddConfig
import com.mrsep.musicrecognizer.core.strings.R
import com.mrsep.musicrecognizer.core.ui.components.PasswordInputField

@Composable
internal fun AuddPreferences(
    state: AuddPreferencesState,
    modifier: Modifier = Modifier,
) {
    var helpDialogVisible by rememberSaveable { mutableStateOf(false) }
    if (helpDialogVisible) {
        AuddHelpDialog { helpDialogVisible = false }
    }
    Column(modifier = modifier) {
        AuthenticationRow(
            modifier = Modifier.fillMaxWidth(),
            serviceName = stringResource(R.string.audd),
            onHelpClick = { helpDialogVisible = true }
        )
        PasswordInputField(
            state = state.apiToken,
            label = stringResource(R.string.api_token),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Stable
internal class AuddPreferencesState(config: AuddConfig) {
    val apiToken = TextFieldState(config.apiToken)

    val currentConfig get() = AuddConfig(apiToken = apiToken.text.toString())

    companion object {
        val Saver: Saver<AuddPreferencesState, *> = listSaver(
            save = { state ->
                listOf(state.apiToken.text.toString())
            },
            restore = { saved ->
                AuddPreferencesState(
                    config = AuddConfig(apiToken = saved[0])
                )
            }
        )
    }
}

@Composable
internal fun rememberAuddPreferencesState(config: AuddConfig): AuddPreferencesState {
    return rememberSaveable(
        inputs = arrayOf(config),
        saver = AuddPreferencesState.Saver
    ) {
        AuddPreferencesState(config)
    }
}

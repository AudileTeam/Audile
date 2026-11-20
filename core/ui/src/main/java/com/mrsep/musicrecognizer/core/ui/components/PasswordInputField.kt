package com.mrsep.musicrecognizer.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
fun PasswordInputField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        label = { Text(text = label) },
        trailingIcon = {
            val iconPainter = painterResource(
                if (passwordVisible) {
                    UiR.drawable.outline_visibility_off_24
                } else {
                    UiR.drawable.outline_visibility_24
                }
            )
            val iconDesc = stringResource(
                if (passwordVisible) StringsR.string.hide else StringsR.string.show
            )
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    painter = iconPainter,
                    contentDescription = iconDesc
                )
            }
        },
        supportingText = error?.let { { Text(error) } },
        isError = (error != null),
        textObfuscationMode = if (passwordVisible) {
            TextObfuscationMode.Visible
        } else {
            TextObfuscationMode.Hidden
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        shape = MaterialTheme.shapes.small
    )
}

package com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun RenameRecognitionDialog(
    initialName: String,
    modifier: Modifier = Modifier,
    onConfirmClick: (String) -> Unit,
    onDismissClick: () -> Unit
) {
    val newName = rememberTextFieldState(initialName)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissClick,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmClick(newName.text.toString())
                },
                enabled = newName.text.isNotBlank()
            ) {
                Text(text = stringResource(StringsR.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.cancel))
            }
        },
        title = {
            Text(
                text = stringResource(StringsR.string.recognition_action_rename),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        },
        text = {
            OutlinedTextField(
                state = newName,
                trailingIcon = {
                    Crossfade(
                        targetState = newName.text.isNotEmpty(),
                        label = ""
                    ) { visible ->
                        if (visible) {
                            IconButton(onClick = { newName.clearText() }) {
                                Icon(
                                    painter = painterResource(UiR.drawable.outline_close_24),
                                    contentDescription = stringResource(StringsR.string.clear_text_field)
                                )
                            }
                        }
                    }
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = MaterialTheme.shapes.small
            )
        }
    )
}

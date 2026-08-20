package com.mrsep.musicrecognizer.feature.backup.presentation

import android.content.Context
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mrsep.musicrecognizer.core.ui.util.shareFile
import com.mrsep.musicrecognizer.feature.backup.BackupEntry
import com.mrsep.musicrecognizer.feature.backup.BackupResult
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun BackupDialog(
    backupState: BackupUiState,
    onChangeSelectedBackupEntry: (entry: BackupEntry, selected: Boolean) -> Unit,
    onBackupClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val dismissOnClickOutside = backupState !is BackupUiState.InProgress
    AlertDialog(
        title = {
            Text(text = stringResource(StringsR.string.backup_dialog_title))
        },
        confirmButton = {
            when (backupState) {
                is BackupUiState.EstimatingEntries,
                is BackupUiState.InProgress -> {}

                is BackupUiState.Ready -> TextButton(
                    enabled = backupState.selectedEntries.isNotEmpty(),
                    onClick = onBackupClick
                ) {
                    Text(text = stringResource(StringsR.string.backup_dialog_button_backup))
                }

                is BackupUiState.Result -> when (backupState.result) {
                    BackupResult.Success -> TextButton(
                        enabled = true,
                        onClick = {
                            val subject = resources.getString(StringsR.string.app_name)
                            context.shareFile(subject, "", backupState.uri)
                        }
                    ) {
                        Text(text = stringResource(StringsR.string.share))
                    }

                    BackupResult.FileNotFound,
                    BackupResult.UnhandledError -> {}
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissClick) {
                Text(text = backupState.dismissButtonText())
            }
        },
        onDismissRequest = onDismissClick,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnClickOutside,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (backupState) {
                    is BackupUiState.EstimatingEntries -> DialogProgressRow(
                        title = stringResource(StringsR.string.backup_estimating_size)
                    )

                    is BackupUiState.Ready -> BackupEntryPicker(
                        title = stringResource(StringsR.string.backup_entry_picker_title),
                        availableEntriesWithSizes = backupState.entriesUncompressedSize,
                        selectedBackupEntries = backupState.selectedEntries,
                        onChangeSelectedBackupEntry = onChangeSelectedBackupEntry,
                    )

                    is BackupUiState.InProgress -> DialogProgressRow(
                        title = stringResource(StringsR.string.backup_in_progress)
                    )

                    is BackupUiState.Result -> Text(backupState.result.getMessage())
                }
            }
        },
    )
}

@Composable
internal fun BackupEntryPicker(
    title: String,
    availableEntriesWithSizes: Map<BackupEntry, Long>,
    selectedBackupEntries: Set<BackupEntry>,
    onChangeSelectedBackupEntry: (entry: BackupEntry, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Text(text = title)
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            availableEntriesWithSizes.forEach { (backupEntry, entrySize) ->
                val size = remember(entrySize) {
                    context.funFormatByteSize(entrySize)
                }
                val checked = selectedBackupEntries.contains(backupEntry)
                BackupEntryCheckBox(
                    title = backupEntry.getTitle(),
                    subtitle = size,
                    checked = checked,
                    onClick = { onChangeSelectedBackupEntry(backupEntry, !checked) }
                )
            }
        }
    }
}

@Composable
private fun BackupResult.getMessage() = when (this) {
    BackupResult.Success -> stringResource(StringsR.string.backup_result_success)
    BackupResult.FileNotFound -> stringResource(StringsR.string.backup_restore_result_file_not_found)
    BackupResult.UnhandledError -> stringResource(StringsR.string.backup_restore_unhandled_error) +
            "\n" + stringResource(StringsR.string.backup_restore_unhandled_error_message)
}

@Composable
private fun BackupUiState.dismissButtonText() = when (this) {
    is BackupUiState.EstimatingEntries,
    is BackupUiState.InProgress,
    -> stringResource(StringsR.string.cancel)

    is BackupUiState.Ready,
    is BackupUiState.Result,
    -> stringResource(StringsR.string.close)
}

@Composable
internal fun DialogProgressRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title)
            subtitle?.let { Text(text = subtitle) }
        }
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun BackupEntryCheckBox(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onClick() }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(0.85f)
            )
        }
    }
}

internal fun Context.funFormatByteSize(bytes: Long): String {
    return android.text.format.Formatter.formatShortFileSize(this, bytes)
}

@Composable
internal fun BackupEntry.getTitle() = when (this) {
    BackupEntry.Data -> stringResource(StringsR.string.backup_entry_data)
    BackupEntry.Preferences -> stringResource(StringsR.string.backup_entry_preferences)
}

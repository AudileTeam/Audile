package com.mrsep.musicrecognizer.feature.backup.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mrsep.musicrecognizer.core.common.util.AppDateTimeFormatter
import com.mrsep.musicrecognizer.core.domain.preferences.AutoBackupResult
import com.mrsep.musicrecognizer.core.ui.components.LoadingStub
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceClickableItem
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceGroup
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceSwitchItem
import com.mrsep.musicrecognizer.feature.backup.BackupNotificationHelper
import com.mrsep.musicrecognizer.feature.backup.RestoreResult
import com.mrsep.musicrecognizer.feature.backup.data.BackupFileNames
import java.time.ZoneId
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun BackupRestoreScreen(
    onBackPressed: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val backupUiState by viewModel.backupState.collectAsStateWithLifecycle()
    val restoreUiState by viewModel.restoreUiState.collectAsStateWithLifecycle()
    val csvExportUiState by viewModel.csvExportUiState.collectAsStateWithLifecycle()
    val autoBackupUiState by viewModel.autoBackupUiState.collectAsStateWithLifecycle()
    val topBarBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    var enableAutoAfterTreePick by rememberSaveable { mutableStateOf(false) }
    var showFrequencyDialog by rememberSaveable { mutableStateOf(false) }
    var showRetentionDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BackupRestoreEvent.PersistableUriPermissionDenied -> {
                    Toast.makeText(
                        context,
                        resources.getString(StringsR.string.auto_backup_error_access_denied),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    val backupUriLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupFileNames.MIME_TYPE)
    ) { resultUri ->
        if (resultUri == null) {
            val message = resources.getString(StringsR.string.toast_no_file_selected)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.backup(resultUri)
    }

    val restoreUriLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { resultUri ->
        if (resultUri == null) {
            val message = resources.getString(StringsR.string.toast_no_file_selected)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.validateBackup(resultUri)
    }

    val csvExportUriLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { resultUri ->
        if (resultUri == null) {
            val message = resources.getString(StringsR.string.toast_no_file_selected)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.exportToCsv(resultUri)
    }

    val autoBackupTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { resultUri ->
        val shouldEnable = enableAutoAfterTreePick
        enableAutoAfterTreePick = false
        if (resultUri == null) {
            if (shouldEnable) {
                val message = resources.getString(StringsR.string.toast_no_file_selected)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }
        viewModel.onBackupTreePicked(resultUri, enable = shouldEnable)
    }

    fun launchAutoBackupTreePicker(enableAfter: Boolean, currentTreeUri: String) {
        enableAutoAfterTreePick = enableAfter
        val initialUri = currentTreeUri
            .takeIf { it.isNotEmpty() }
            ?.toUri()
        try {
            autoBackupTreeLauncher.launch(initialUri)
        } catch (_: ActivityNotFoundException) {
            enableAutoAfterTreePick = false
            Toast.makeText(
                context,
                resources.getString(StringsR.string.file_manager_not_found_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun enableAutoBackup(state: AutoBackupUiState.Ready) {
        if (state.needsTreePickerToEnable()) {
            launchAutoBackupTreePicker(
                enableAfter = true,
                currentTreeUri = state.preferences.treeUri,
            )
        } else {
            viewModel.enableAutoBackup()
        }
    }

    val readyAutoBackupState = autoBackupUiState as? AutoBackupUiState.Ready
    val currentEnableAutoBackup by rememberUpdatedState(
        newValue = { readyAutoBackupState?.let(::enableAutoBackup) }
    )
    @SuppressLint("InlinedApi")
    val notificationPermissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    ) { _ ->
        currentEnableAutoBackup()
    }

    backupUiState?.let { backupState ->
        BackupDialog(
            backupState = backupState,
            onChangeSelectedBackupEntry = viewModel::onChangeSelectedBackupEntry,
            onBackupClick = {
                try {
                    backupUriLauncher.launch(BackupFileNames.newBackupDisplayName())
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        resources.getString(StringsR.string.file_manager_not_found_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismissClick = viewModel::cancelBackupScopeJobs,
        )
    }
    restoreUiState?.let { restoreState ->
        RestoreDialog(
            restoreState = restoreState,
            onChangeSelectedBackupEntry = viewModel::onChangeSelectedRestoreEntry,
            onAppRestartRequest = viewModel::restartApplicationOnRestore,
            onRestoreClick = viewModel::restore,
            onDismissRequest = when (restoreState) {
                is RestoreUiState.ValidatingBackup,
                is RestoreUiState.Ready,
                -> viewModel::cancelRestoreScopeJobs

                is RestoreUiState.InProgress -> null /* main restore task is not cancelable */
                is RestoreUiState.Result -> when (val result = restoreState.result) {
                    is RestoreResult.Success -> if (!result.appRestartRequired) {
                        viewModel::cancelRestoreScopeJobs
                    } else {
                        null /* await app restart */
                    }

                    is RestoreResult.UnhandledError -> if (!result.appRestartRequired) {
                        viewModel::cancelRestoreScopeJobs
                    } else {
                        null /* await app restart */
                    }

                    RestoreResult.FileNotFound,
                    RestoreResult.MalformedBackup,
                    RestoreResult.NewerVersionBackup,
                    RestoreResult.NotBackupFile,
                    -> viewModel::cancelRestoreScopeJobs
                }
            }
        )
    }

    csvExportUiState?.let { exportState ->
        CsvExportFullScreenDialog(
            modifier = Modifier.fillMaxSize(),
            exportState = exportState,
            onExportClick = {
                try {
                    csvExportUriLauncher.launch(BackupFileNames.newCsvDisplayName())
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        resources.getString(StringsR.string.file_manager_not_found_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onChangeExportState = viewModel::onChangeExportState,
            onDismissClick = viewModel::cancelCsvExportScopeJobs,
        )
    }

    if (readyAutoBackupState != null) {
        if (showFrequencyDialog) {
            AutoBackupFrequencyDialog(
                selectedDays = readyAutoBackupState.preferences.intervalDays,
                onSelectDays = viewModel::setAutoBackupIntervalDays,
                onDismiss = { showFrequencyDialog = false },
            )
        }
        if (showRetentionDialog) {
            AutoBackupRetentionDialog(
                selectedKeepCount = readyAutoBackupState.preferences.keepCount,
                onSelectKeepCount = viewModel::setAutoBackupKeepCount,
                onDismiss = { showRetentionDialog = false },
            )
        }
    }

    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(StringsR.string.pref_title_backup_and_restore).toUpperCase(LocalLocale.current),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_arrow_back_24),
                        contentDescription = stringResource(StringsR.string.nav_back)
                    )
                }
            },
            scrollBehavior = topBarBehaviour,
        )
        when (val autoBackupState = autoBackupUiState) {
            AutoBackupUiState.Loading -> {
                LoadingStub(modifier = Modifier.fillMaxSize())
            }

            is AutoBackupUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(topBarBehaviour.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                ) {
                    PreferenceGroup(title = stringResource(StringsR.string.pref_group_auto_backup)) {
                        val autoBackup = autoBackupState.preferences
                        PreferenceSwitchItem(
                            title = stringResource(StringsR.string.pref_title_auto_backup),
                            subtitle = stringResource(StringsR.string.pref_subtitle_auto_backup),
                            checked = autoBackup.enabled,
                            onClick = {
                                if (autoBackup.enabled) {
                                    viewModel.disableAutoBackup()
                                } else {
                                    val needsNotificationPermission =
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                !notificationPermissionState.status.isGranted
                                    if (needsNotificationPermission) {
                                        notificationPermissionState.launchPermissionRequest()
                                    } else {
                                        enableAutoBackup(autoBackupState)
                                    }
                                }
                            }
                        )
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_backup_location),
                            subtitle = stringResource(
                                StringsR.string.pref_subtitle_backup_location,
                                autoBackupState.locationName ?: "…",
                            ),
                            onItemClick = {
                                launchAutoBackupTreePicker(
                                    enableAfter = false,
                                    currentTreeUri = autoBackup.treeUri,
                                )
                            }
                        )
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_backup_frequency),
                            subtitle = frequencyOptionLabel(autoBackup.intervalDays),
                            onItemClick = { showFrequencyDialog = true }
                        )
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_backup_retention),
                            subtitle = retentionOptionLabel(autoBackup.keepCount),
                            onItemClick = { showRetentionDialog = true }
                        )
                        AnimatedVisibility(
                            visible = autoBackupState.shouldShowStatus(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            val shouldShowError = autoBackupState.shouldShowError()
                            Column(modifier = Modifier.animateContentSize()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = autoBackupStatusText(
                                            state = autoBackupState,
                                            dateFormatter = viewModel.dateFormatter,
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (shouldShowError) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (shouldShowError) {
                                        IconButton(onClick = viewModel::resetLastAutoBackupResult) {
                                            Icon(
                                                painter = painterResource(UiR.drawable.outline_close_24),
                                                contentDescription = stringResource(StringsR.string.cancel),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                    Spacer(Modifier.height(16.dp))
                    PreferenceGroup(title = stringResource(StringsR.string.pref_title_backup_and_restore)) {
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_backup),
                            subtitle = stringResource(StringsR.string.pref_subtitle_backup),
                            onItemClick = viewModel::estimateEntriesToBackup
                        )
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_restore),
                            subtitle = stringResource(StringsR.string.pref_subtitle_restore),
                            onItemClick = {
                                try {
                                    restoreUriLauncher.launch(arrayOf("*/*"))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        resources.getString(StringsR.string.file_manager_not_found_toast),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                    Spacer(Modifier.height(16.dp))
                    PreferenceGroup(title = stringResource(StringsR.string.pref_group_misc)) {
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_export_to_csv),
                            subtitle = stringResource(StringsR.string.pref_subtitle_export_to_csv),
                            onItemClick = viewModel::prepareForCsvExport
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun autoBackupStatusText(
    state: AutoBackupUiState.Ready,
    dateFormatter: AppDateTimeFormatter,
): String {
    if (state.isRunning) {
        return stringResource(StringsR.string.backup_in_progress)
    }
    return when (val lastResult = state.lastResult) {
        null -> if (state.preferences.enabled) {
            stringResource(StringsR.string.auto_backup_status_scheduled)
        } else {
            ""
        }

        is AutoBackupResult.Success -> {
            val relativeDate = dateFormatter.formatRelativeToToday(
                lastResult.timestamp.atZone(ZoneId.systemDefault())
            )
            stringResource(StringsR.string.auto_backup_status_last_success, relativeDate)
        }

        is AutoBackupResult.Failure -> {
            val relativeDate = dateFormatter.formatRelativeToToday(
                lastResult.timestamp.atZone(ZoneId.systemDefault())
            )
            val reason = BackupNotificationHelper.localizedError(
                LocalContext.current,
                lastResult,
            )
            stringResource(StringsR.string.auto_backup_status_last_failure, relativeDate) + "\n$reason"
        }
    }
}

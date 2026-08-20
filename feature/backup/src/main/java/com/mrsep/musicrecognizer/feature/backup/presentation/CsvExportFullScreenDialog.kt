package com.mrsep.musicrecognizer.feature.backup.presentation

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mrsep.musicrecognizer.core.ui.components.DialogSwitch
import com.mrsep.musicrecognizer.core.ui.resources.titleId
import com.mrsep.musicrecognizer.core.ui.util.shareFile
import com.mrsep.musicrecognizer.feature.backup.CsvField
import com.mrsep.musicrecognizer.feature.backup.ExportResult
import com.mrsep.musicrecognizer.feature.backup.TrackField
import com.mrsep.musicrecognizer.feature.backup.TrackLinkField
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.collections.map
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsvExportFullScreenDialog(
    modifier: Modifier = Modifier,
    exportState: CsvExportUiState,
    onDismissClick: () -> Unit,
    onExportClick: () -> Unit,
    onChangeExportState: (CsvExportUiState.Ready) -> Unit,
) {
    val dismissOnClickOutside = exportState !is CsvExportUiState.InProgress
    BasicAlertDialog(
        modifier = modifier,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissOnClickOutside,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        content = {
            Surface {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    TopAppBar(
                        title = { Text(text = stringResource(StringsR.string.pref_title_export_to_csv)) },
                        navigationIcon = {
                            IconButton(onClick = onDismissClick) {
                                Icon(
                                    painter = painterResource(UiR.drawable.outline_arrow_back_24),
                                    contentDescription = stringResource(StringsR.string.nav_back)
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    when (exportState) {
                        is CsvExportUiState.Ready -> CsvExportReadyContent(
                            modifier = Modifier.fillMaxSize(),
                            exportState = exportState,
                            onDismissClick = onDismissClick,
                            onExportClick = onExportClick,
                            onChangeExportState = onChangeExportState,
                        )
                        is CsvExportUiState.InProgress -> CsvExportInProgressContent(
                            modifier = Modifier.fillMaxSize(),
//                            exportState = exportState,
                            onDismissClick = onDismissClick,
                        )
                        is CsvExportUiState.Result -> CsvExportResultContent(
                            modifier = Modifier.fillMaxSize(),
                            exportState = exportState,
                            onDismissClick = onDismissClick,
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissClick
    )
}

@Composable
private fun CsvExportInProgressContent(
//    exportState: CsvExportUiState.InProgress,
    modifier: Modifier = Modifier,
    onDismissClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(StringsR.string.export_in_progress),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onDismissClick) {
            Text(text = stringResource(StringsR.string.cancel))
        }
    }
}

@Composable
private fun CsvExportResultContent(
    exportState: CsvExportUiState.Result,
    modifier: Modifier = Modifier,
    onDismissClick: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.outline_check_24),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = exportState.getMessage(),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(onClick = {
                val subject = resources.getString(StringsR.string.app_name)
                context.shareFile(subject, "", exportState.uri)
            }) {
                Text(text = stringResource(StringsR.string.share))
            }
            OutlinedButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.close))
            }
        }
    }
}

@Composable
private fun CsvExportReadyContent(
    modifier: Modifier = Modifier,
    exportState: CsvExportUiState.Ready,
    onDismissClick: () -> Unit,
    onExportClick: () -> Unit,
    onChangeExportState: (CsvExportUiState.Ready) -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val writeHeader = exportState.writeHeader
    val onlyFavorites = exportState.exportOnlyFavorites
    val exportFields = exportState.exportFields
    val allSelected = remember(exportFields) { exportFields.all { it.selected } }
    val allDeselected = remember(exportFields) { exportFields.all { !it.selected } }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        DialogSwitch(
            title = stringResource(StringsR.string.export_only_favorites_tracks),
            checked = onlyFavorites,
            onClick = {
                onChangeExportState(exportState.copy(exportOnlyFavorites = !onlyFavorites))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        DialogSwitch(
            title = stringResource(StringsR.string.export_write_csv_header),
            checked = writeHeader,
            onClick = {
                onChangeExportState(exportState.copy(writeHeader = !writeHeader))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(StringsR.string.export_select_fields_to_export),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val newList = exportFields.map { it.copy(selected = !allSelected) }
                    onChangeExportState(exportState.copy(exportFields = newList))
                }
            ) {
                if (allSelected) {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_deselect_24),
                        contentDescription = stringResource(StringsR.string.deselect_all)
                    )
                } else {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_select_all_24),
                        contentDescription = stringResource(StringsR.string.select_all)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val lazyListState = rememberLazyListState()
        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val newFields = exportFields.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            onChangeExportState(exportState.copy(exportFields = newFields))
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(exportFields, key = { _, selectable -> selectable.key() }) { index, selectable ->
                ReorderableItem(reorderableLazyListState, key = selectable.key() ) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                    fun onItemSelect() {
                        val newList = exportFields.toMutableList().apply {
                            add(index, removeAt(index).copy(selected = !selectable.selected))
                        }
                        onChangeExportState(exportState.copy(exportFields = newList))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = elevation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        onClick = ::onItemSelect
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectable.selected,
                                onCheckedChange = null
                            )
                            if (selectable.field is TrackLinkField || selectable.field == TrackField.LINK_ARTWORK) {
                                Icon(
                                    painter = painterResource(UiR.drawable.outline_link_2_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = selectable.field.header(context),
                                modifier = Modifier.padding(horizontal = 8.dp).weight(1f)
                            )
                            IconButton(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                ),
                                onClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(UiR.drawable.outline_drag_handle_24),
                                    contentDescription = stringResource(StringsR.string.reorder)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onDismissClick) {
                Text(text = stringResource(StringsR.string.close))
            }
            Button(onClick = onExportClick, enabled = !allDeselected) {
                Text(text = stringResource(StringsR.string.export_export))
            }
        }
    }
}

internal fun CsvField.header(context: Context) = when (this) {
    TrackField.TITLE -> context.getString(StringsR.string.export_track_field_title)
    TrackField.ARTIST -> context.getString(StringsR.string.export_track_field_artist)
    TrackField.ALBUM -> context.getString(StringsR.string.export_track_field_album)
    TrackField.RELEASE_DATE -> context.getString(StringsR.string.export_track_field_release_date)
    TrackField.ISRC -> context.getString(StringsR.string.isrc)
    TrackField.DURATION -> context.getString(StringsR.string.export_track_field_duration)
    TrackField.PLAYBACK_OFFSET -> context.getString(StringsR.string.export_track_field_playback_offset)
    TrackField.RECOGNITION_DATE -> context.getString(StringsR.string.export_track_field_recognition_date)
    TrackField.RECOGNITION_PROVIDER -> context.getString(StringsR.string.export_track_field_provider)
    TrackField.LYRICS -> context.getString(StringsR.string.export_track_field_lyrics)
    TrackField.LINK_ARTWORK -> context.getString(StringsR.string.export_track_field_artwork_link)
    TrackField.IS_FAVORITE -> context.getString(StringsR.string.export_track_field_favorite)
    is TrackLinkField -> context.getString(service.titleId())
}

internal fun SelectableCsvField.key() = when (field) {
    is TrackField -> field.name
    is TrackLinkField -> field.service.name
}

@Composable
private fun CsvExportUiState.Result.getMessage() = when (this.result) {
    is ExportResult.Success -> stringResource(StringsR.string.export_csv_result_success)
    ExportResult.FileNotFound -> stringResource(StringsR.string.backup_restore_result_file_not_found)
    is ExportResult.UnhandledError -> stringResource(StringsR.string.backup_restore_unhandled_error) +
            "\n" + stringResource(StringsR.string.backup_restore_unhandled_error_message)
}

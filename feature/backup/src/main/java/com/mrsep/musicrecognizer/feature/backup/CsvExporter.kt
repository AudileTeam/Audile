package com.mrsep.musicrecognizer.feature.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.jsoizo.kotlincsv.csvWriter
import com.jsoizo.kotlincsv.writer.writeNullable
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.preferences.FavoritesMode
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.feature.backup.CsvField.Companion.extractFrom
import com.mrsep.musicrecognizer.feature.backup.data.openOutputStreamPreferTruncate
import com.mrsep.musicrecognizer.feature.backup.presentation.header
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

internal interface CsvExporter {

    suspend fun export(destination: Uri, params: CsvExportParams): ExportResult
}

internal data class CsvExportParams(
    val exportFields: List<CsvField>,
    val writeHeader: Boolean,
    val favoritesMode: FavoritesMode,
)

private const val TAG = "CsvExporterImpl"

internal class CsvExporterImpl @Inject constructor(
    private val trackRepository: TrackRepository,
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CsvExporter {

    private val csvWriter = csvWriter()

    override suspend fun export(destination: Uri, params: CsvExportParams) = withContext(ioDispatcher) {
        try {
            val outputStream = try {
                appContext.contentResolver.openOutputStreamPreferTruncate(destination)
            } catch (_: Exception) {
                deleteUnfinishedExportFile(destination)
                return@withContext ExportResult.FileNotFound
            }

            var trackCount = 0
            val trackRows = trackRepository.getTracksFlow(params.favoritesMode).first()
                .asSequence()
                .mapNotNull { track ->
                    coroutineContext.ensureActive()
                    params.exportFields.extractFrom(track)
                        .takeIf { values -> values.any { value -> !value.isNullOrBlank() } }
                        ?.also { trackCount++ }
                }

            outputStream.use { stream ->
                val rows = sequence {
                    if (params.writeHeader) {
                        yield(params.exportFields.map { it.header(appContext) })
                    }
                    yieldAll(trackRows)
                }
                csvWriter.writeNullable(rows, stream)
            }
            ExportResult.Success(trackCount)
        } catch (e: CancellationException) {
            deleteUnfinishedExportFile(destination)
            throw e
        } catch (e: Exception) {
            val baseMsg = "Fatal error while creating CSV file"
            Log.e(TAG, baseMsg, e)
            deleteUnfinishedExportFile(destination)
            ExportResult.UnhandledError(e.message ?: baseMsg)
        }
    }

    private fun deleteUnfinishedExportFile(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(appContext.contentResolver, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete unfinished CSV file", e)
        }
    }
}

internal sealed class ExportResult {
    data class Success(val trackCount: Int) : ExportResult()
    data object FileNotFound : ExportResult()
    data class UnhandledError(val message: String) : ExportResult()
}

package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.domain.history.HistoryReport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the History report as a PDF the patient can hand to a doctor or a
 * pharmacist (T7). The text comes from [HistoryPdfContent] and the layout from
 * [PdfDocumentWriter]; the file lives in `cacheDir/exports` and leaves the app
 * only when the patient picks an app in the share sheet.
 */
@Singleton
class HistoryPdfExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val content = HistoryPdfContent(context)

        /** Writes the report and returns the shareable content Uri as a string. */
        fun export(
            report: HistoryReport,
            generatedAt: Instant,
        ): String {
            val file = outputFile(generatedAt)
            FileOutputStream(file).use { output -> write(report, generatedAt, output) }
            return exportUri(context, file)
        }

        /** The file the export writes; `cacheDir/exports/history-<millis>.pdf`. */
        internal fun outputFile(generatedAt: Instant): File = exportFile(context, FILE_PREFIX, EXTENSION, generatedAt)

        /** Renders the document; separate from [export] so tests can use a stream. */
        fun write(
            report: HistoryReport,
            generatedAt: Instant,
            output: OutputStream,
        ) {
            PdfDocumentWriter(context.isRtlLayout()).write(content.blocks(report, generatedAt), output)
        }

        private companion object {
            const val FILE_PREFIX = "history-"
            const val EXTENSION = "pdf"
        }
    }

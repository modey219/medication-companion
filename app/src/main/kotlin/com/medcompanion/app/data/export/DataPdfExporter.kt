package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.domain.export.FullData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Export my data" as a PDF (T8a): the same content as the JSON export,
 * laid out by [PdfDocumentWriter] for a doctor or a pharmacist to read.
 */
@Singleton
class DataPdfExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val collector: FullDataCollector,
    ) {
        private val content = DataPdfContent(context)

        /** Collects the data and writes it; returns the shareable content Uri. */
        suspend fun export(): String {
            val data = collector.collect()
            val file = outputFile(data.generatedAt)
            file.outputStream().use { write(data, it) }
            return exportUri(context, file)
        }

        /** The file the export writes; `cacheDir/exports/data-<millis>.pdf`. */
        internal fun outputFile(generatedAt: Instant): File = exportFile(context, FILE_PREFIX, EXTENSION, generatedAt)

        /** Writes the document; the caller owns [output]. */
        fun write(
            data: FullData,
            output: OutputStream,
        ) {
            PdfDocumentWriter(context.isRtlLayout()).write(content.blocks(data), output)
        }

        private companion object {
            const val FILE_PREFIX = "data-"
            const val EXTENSION = "pdf"
        }
    }

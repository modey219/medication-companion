package com.medcompanion.app.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import androidx.core.content.FileProvider
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
 * pharmacist (AGENTS.md > Part C, T7).
 *
 * Uses the platform [PdfDocument]: no extra dependency, no network, no key.
 * The text comes from [HistoryPdfContent]; this class only lays it out (A4,
 * `StaticLayout` so Arabic is shaped and wrapped, new pages as needed) and
 * writes the file into `cacheDir/exports`. The file leaves the app only when
 * the patient picks an app in the share sheet, which gets a content Uri.
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
            return FileProvider
                .getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
                .toString()
        }

        /** The file the export writes; `cacheDir/exports/history-<millis>.pdf`. */
        internal fun outputFile(generatedAt: Instant): File {
            val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
            return File(directory, "$FILE_PREFIX${generatedAt.toEpochMilli()}.pdf")
        }

        /** Renders the document; separate from [export] so tests can use a stream. */
        fun write(
            report: HistoryReport,
            generatedAt: Instant,
            output: OutputStream,
        ) {
            val document = PdfDocument()
            val renderer = Renderer(document)
            content.blocks(report, generatedAt).forEach { block ->
                if (block is PdfBlock.Heading) renderer.space()
                renderer.draw(paintFor(block), block.text)
            }
            renderer.close()
            document.writeTo(output)
            document.close()
        }

        private fun paintFor(block: PdfBlock): TextPaint =
            when (block) {
                is PdfBlock.Title -> titlePaint
                is PdfBlock.Heading -> headingPaint
                is PdfBlock.Body -> bodyPaint
                is PdfBlock.Footer -> footerPaint
            }

        /** Draws paragraphs and starts a new page when the next block does not fit. */
        private inner class Renderer(
            private val document: PdfDocument,
        ) {
            private var pageNumber = FIRST_PAGE
            private var page = startPage()
            private var y = MARGIN

            fun draw(
                paint: TextPaint,
                text: String,
            ) {
                val layout = layout(paint, text)
                if (y + layout.height > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = startPage()
                    y = MARGIN
                }
                page.canvas.save()
                page.canvas.translate(MARGIN, y)
                layout.draw(page.canvas)
                page.canvas.restore()
                y += layout.height + BLOCK_SPACING
            }

            fun space() {
                y += SECTION_SPACING
            }

            fun close() {
                document.finishPage(page)
            }

            private fun startPage(): PdfDocument.Page =
                document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
                )

            private fun layout(
                paint: TextPaint,
                text: String,
            ): StaticLayout =
                StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, PAGE_WIDTH - (MARGIN * 2).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(
                        if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR,
                    ).setLineSpacing(LINE_SPACING_EXTRA, LINE_SPACING_MULTIPLIER)
                    .build()
        }

        private val titlePaint: TextPaint
            get() =
                TextPaint().apply {
                    textSize = TITLE_SIZE
                    isFakeBoldText = true
                    color = Color.BLACK
                }

        private val headingPaint: TextPaint
            get() =
                TextPaint().apply {
                    textSize = HEADING_SIZE
                    isFakeBoldText = true
                    color = Color.BLACK
                }

        private val bodyPaint: TextPaint
            get() =
                TextPaint().apply {
                    textSize = BODY_SIZE
                    color = Color.BLACK
                }

        private val footerPaint: TextPaint
            get() =
                TextPaint().apply {
                    textSize = FOOTER_SIZE
                    color = Color.DKGRAY
                }

        /** Arabic is written right to left; the document follows the device locale. */
        private val isRtl: Boolean
            get() = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

        private companion object {
            const val EXPORT_DIRECTORY = "exports"
            const val FILE_PREFIX = "history-"
            const val FILE_PROVIDER_SUFFIX = ".fileprovider"

            const val PAGE_WIDTH = 595
            const val PAGE_HEIGHT = 842
            const val MARGIN = 24f
            const val FIRST_PAGE = 1

            const val TITLE_SIZE = 16f
            const val HEADING_SIZE = 13f
            const val BODY_SIZE = 11f
            const val FOOTER_SIZE = 9f
            const val LINE_SPACING_EXTRA = 2f
            const val LINE_SPACING_MULTIPLIER = 1f
            const val BLOCK_SPACING = 3f
            const val SECTION_SPACING = 10f
        }
    }

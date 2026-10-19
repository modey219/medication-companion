package com.medcompanion.app.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import java.io.OutputStream

/**
 * Lays [PdfBlock]s out on A4 pages and writes a PDF, using the platform
 * [PdfDocument]: no extra dependency, no network, no key. `StaticLayout`
 * shapes and wraps the text (Arabic included) and the writing direction
 * follows the app locale.
 */
internal class PdfDocumentWriter(
    private val isRtl: Boolean,
) {
    fun write(
        blocks: List<PdfBlock>,
        output: OutputStream,
    ) {
        val document = PdfDocument()
        try {
            val renderer = Renderer(document)
            blocks.forEach { block ->
                if (block is PdfBlock.Heading) renderer.space()
                renderer.draw(paintFor(block), block.text)
            }
            renderer.close()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun paintFor(block: PdfBlock): TextPaint =
        when (block) {
            is PdfBlock.Title -> paint(TITLE_SIZE, bold = true)
            is PdfBlock.Heading -> paint(HEADING_SIZE, bold = true)
            is PdfBlock.Body -> paint(BODY_SIZE, bold = false)
            is PdfBlock.Footer -> paint(FOOTER_SIZE, bold = false, color = Color.DKGRAY)
        }

    private fun paint(
        size: Float,
        bold: Boolean,
        color: Int = Color.BLACK,
    ) = TextPaint().apply {
        textSize = size
        isFakeBoldText = bold
        this.color = color
    }

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

    private companion object {
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

/** The document follows the app locale: Arabic is written right to left. */
internal fun Context.isRtlLayout(): Boolean = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

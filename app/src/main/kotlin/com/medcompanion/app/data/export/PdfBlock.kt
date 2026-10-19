package com.medcompanion.app.data.export

/**
 * One paragraph of an exported document. The text is built without touching
 * the PDF library so the wording can be reviewed and tested on its own; the
 * writer turns blocks into pages.
 */
sealed interface PdfBlock {
    val text: String

    data class Title(
        override val text: String,
    ) : PdfBlock

    data class Heading(
        override val text: String,
    ) : PdfBlock

    data class Body(
        override val text: String,
    ) : PdfBlock

    data class Footer(
        override val text: String,
    ) : PdfBlock
}

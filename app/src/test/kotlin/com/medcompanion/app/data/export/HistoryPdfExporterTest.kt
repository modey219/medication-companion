package com.medcompanion.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The History export file (T7).
 *
 * The drawing itself needs the real `android.graphics.pdf.PdfDocument`, which
 * Robolectric does not implement, so the rendered PDF is checked on a device
 * (see the verification steps), while this test pins the file the export
 * writes and the share path that is exposed for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class HistoryPdfExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val exporter = HistoryPdfExporter(context)

    private val generatedAt = Instant.parse("2026-10-17T12:00:00Z")

    @Test
    fun `the export file is a named pdf inside the cache exports folder`() {
        val file = exporter.outputFile(generatedAt)

        assertEquals("history-${generatedAt.toEpochMilli()}.pdf", file.name)
        assertEquals("exports", file.parentFile?.name)
        assertEquals(context.cacheDir, file.parentFile?.parentFile)
        assertTrue(file.parentFile?.isDirectory == true)
    }
}

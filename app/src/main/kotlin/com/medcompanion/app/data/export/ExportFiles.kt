package com.medcompanion.app.data.export

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant

/** Folder inside the app cache the exports are written to (see file_paths.xml). */
internal const val EXPORT_DIRECTORY: String = "exports"

/** Suffix of the authority declared in the manifest provider entry. */
internal const val FILE_PROVIDER_SUFFIX: String = ".fileprovider"

/**
 * A new file in the export folder, named after the time of the export. The
 * cache directory keeps exports out of the database and lets the system clean
 * them up; the file only leaves the app through the share sheet.
 */
internal fun exportFile(
    context: Context,
    prefix: String,
    extension: String,
    generatedAt: Instant,
): File {
    val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
    return File(directory, "$prefix${generatedAt.toEpochMilli()}.$extension")
}

/** Content Uri for one exported file, granted to the single app the patient picks. */
internal fun exportUri(
    context: Context,
    file: File,
): String =
    FileProvider
        .getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        .toString()

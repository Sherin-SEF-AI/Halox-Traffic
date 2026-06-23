package com.haloxtraffic.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

/** Share an exported file via the app's FileProvider (authority `${applicationId}.fileprovider`). */
object ExportShare {
    fun share(context: Context, file: File, mime: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share evidence").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.e(it, "Share failed for ${file.name}") }
    }

    fun mimeFor(format: ExportFormat): String = when (format) {
        ExportFormat.PDF_CASE_FILE -> "application/pdf"
        ExportFormat.ECHALLAN_BUNDLE -> "application/zip"
        ExportFormat.CSV_INDEX -> "text/csv"
    }
}

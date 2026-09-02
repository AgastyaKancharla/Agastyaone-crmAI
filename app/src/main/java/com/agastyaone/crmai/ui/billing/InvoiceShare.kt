package com.agastyaone.crmai.ui.billing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * The standard Android share-sheet intent (Phase 4a spec: WhatsApp/email/Drive all show
 * up here naturally - no WhatsApp-specific code). The chooser, not a fixed target, is the
 * point: staff pick whichever app they want to send the PDF through.
 */
fun shareInvoicePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share invoice"))
}

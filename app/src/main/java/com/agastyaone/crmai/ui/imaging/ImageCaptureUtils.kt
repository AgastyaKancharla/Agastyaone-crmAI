package com.agastyaone.crmai.ui.imaging

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A fresh cache file under `cacheDir/imaging/` (matching `file_paths.xml`'s
 * `imaging_captures` entry) and its `content://` Uri, suitable as the destination for
 * `ActivityResultContracts.TakePicture()` - the system camera app writes the full-res
 * photo there, and [com.agastyaone.crmai.data.storage.ImageUploader] reads it back out.
 */
fun createCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "imaging").apply { mkdirs() }
    val file = File(directory, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

package com.ordia.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the update APK to internal cache and launches the system installer.
 * Emits download progress as a 0–100 percentage.
 */
class UpdateInstaller(private val context: Context) {

    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading = _downloading.asStateFlow()

    suspend fun downloadAndInstall(apkUrl: String) = withContext(Dispatchers.IO) {
        if (_downloading.value) return@withContext
        _downloading.value = true
        _progress.value = 0
        try {
            val apkFile = File(context.cacheDir, "ordia-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        _progress.value = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    }
                }
            }
            _progress.value = 100
            launchInstaller(apkFile)
        } finally {
            _downloading.value = false
        }
    }

    private fun launchInstaller(apkFile: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

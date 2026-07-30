package com.ordia.app.updates

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** User-visible gate between Ordia's validation and Android's package installer. */
class UpdateInstallActivity : ComponentActivity() {
    private var downloadId: Long = -1L
    private var waitingForUnknownSourcesPermission = false
    private var validatedUri: Uri? = null
    private var validationStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) {
            finish()
            return
        }
        validateAndContinue()
    }

    override fun onResume() {
        super.onResume()
        if (!waitingForUnknownSourcesPermission) return
        waitingForUnknownSourcesPermission = false
        if (packageManager.canRequestPackageInstalls()) {
            launchInstaller()
        } else {
            Toast.makeText(this, "No se autorizó la instalación de actualizaciones de Ordia.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun validateAndContinue() {
        if (validationStarted) return
        validationStarted = true
        lifecycleScope.launch {
            when (val result = OrdiaUpdateManager.validateDownloadedPackage(applicationContext, downloadId)) {
                is OrdiaUpdateManager.ValidationResult.Invalid -> {
                    OrdiaUpdateManager.discardDownload(applicationContext, downloadId)
                    Toast.makeText(this@UpdateInstallActivity, result.reason, Toast.LENGTH_LONG).show()
                    finish()
                }
                is OrdiaUpdateManager.ValidationResult.Valid -> {
                    validatedUri = result.uri
                    if (packageManager.canRequestPackageInstalls()) launchInstaller()
                    else requestUnknownSourcesPermission()
                }
            }
        }
    }

    private fun requestUnknownSourcesPermission() {
        waitingForUnknownSourcesPermission = true
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            waitingForUnknownSourcesPermission = false
            Toast.makeText(this, "Android no pudo abrir el permiso de instalación.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun launchInstaller() {
        val uri = validatedUri ?: run {
            finish()
            return
        }
        runCatching {
            startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_RETURN_RESULT, false)
                }
            )
            // Keep metadata and the private APK until a successful app update is observed.
            // This permits a safe retry if the user cancels Android's confirmation dialog.
        }.onFailure {
            Toast.makeText(this, "Android no pudo abrir el instalador de Ordia.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}

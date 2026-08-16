package com.ordia.app.updates

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ordia.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Puerta de usuario entre la validación de Ordía y el instalador de Android.
 *
 * Usa PackageInstaller.Session (mecanismo nativo de Android): escribe la APK ya
 * verificada en una sesión y hace commit con un PendingIntent. Android muestra su
 * confirmación final (STATUS_PENDING_USER_ACTION) y entrega el resultado a
 * [UpdateInstallResultReceiver]. Nunca se instala una APK sin haber pasado la
 * validación completa (SHA-256, tamaño, applicationId, versionCode y firma).
 */
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
            Toast.makeText(this, getString(R.string.update_install_not_authorized), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun validateAndContinue() {
        if (validationStarted) return
        validationStarted = true
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = OrdiaUpdateManager.validateDownloadedPackage(applicationContext, downloadId)) {
                is OrdiaUpdateManager.ValidationResult.Invalid -> {
                    OrdiaUpdateManager.discardDownload(applicationContext, downloadId)
                    runOnUiThread {
                        Toast.makeText(this@UpdateInstallActivity, result.reason, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                is OrdiaUpdateManager.ValidationResult.Valid -> {
                    validatedUri = result.uri
                    runOnUiThread {
                        if (packageManager.canRequestPackageInstalls()) launchInstaller()
                        else requestUnknownSourcesPermission()
                    }
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
            Toast.makeText(this, getString(R.string.update_install_permission_error), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun launchInstaller() {
        val uri = validatedUri ?: run {
            finish()
            return
        }
        runCatching {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                (applicationInfo.loadIcon(packageManager) as? BitmapDrawable)?.bitmap?.let { setAppIcon(it) }
                setAppLabel(applicationInfo.loadLabel(packageManager).toString())
            }
            val sessionId = packageManager.packageInstaller.createSession(params)
            val session = packageManager.packageInstaller.openSession(sessionId)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    session.openWrite("ordia-update.apk", 0, -1)?.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            output.write(buffer, 0, read)
                        }
                        session.fsync(output)
                    }
                } ?: error("No se pudo leer la APK verificada.")
                // Android mostrará su confirmación final y notificará a
                // UpdateInstallResultReceiver (STATUS_PENDING_USER_ACTION → resultado).
                session.commit(installResultPendingIntent().intentSender)
            } finally {
                session.close()
            }
            Toast.makeText(this, getString(R.string.update_install_started), Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, getString(R.string.update_install_launch_error), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun installResultPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            REQUEST_INSTALL_RESULT,
            Intent(this, UpdateInstallResultReceiver::class.java)
                .setPackage(packageName)
                .setAction(UpdateInstallResultReceiver.ACTION_PACKAGE_INSTALL_RESULT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val REQUEST_INSTALL_RESULT = 3401
    }
}

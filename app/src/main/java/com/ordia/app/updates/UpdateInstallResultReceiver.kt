package com.ordia.app.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.ordia.app.R

/**
 * Recibe el resultado de la sesión PackageInstaller lanzada por [UpdateInstallActivity].
 *
 * - STATUS_PENDING_USER_ACTION: Android está mostrando su confirmación final; se espera.
 * - STATUS_SUCCESS: limpieza de artefactos + estado actualizado en la UI.
 * - Cualquier otro estado: se descarta la descarga y se informa del error con claridad.
 *   Nunca se deja una APK a medio instalar ni se tocan los datos de Ordía.
 */
class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PACKAGE_INSTALL_RESULT) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                OrdiaUpdateManager.cleanupObsolete(context)
                OrdiaUpdateController.onInstallResult(success = true)
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit // Confirmación final de Android en curso.
            else -> {
                OrdiaUpdateManager.discardCurrent(context)
                OrdiaUpdateController.onInstallResult(success = false)
                runCatching {
                    OrdiaUpdateManager.showFailure(context, context.getString(R.string.update_install_rejected))
                }
            }
        }
    }

    companion object {
        const val ACTION_PACKAGE_INSTALL_RESULT = "com.ordia.app.action.PACKAGE_INSTALL_RESULT"
    }
}

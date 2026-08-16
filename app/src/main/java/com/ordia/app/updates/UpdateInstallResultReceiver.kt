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
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                OrdiaUpdateManager.cleanupObsolete(context)
                OrdiaUpdateController.onInstallResult(success = true)
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit // Confirmación final de Android en curso.
            else -> {
                OrdiaUpdateManager.discardCurrent(context)
                val reason = describeFailure(context, status, intent)
                OrdiaUpdateController.onInstallResult(success = false, reason = reason)
                runCatching {
                    OrdiaUpdateManager.showFailure(context, reason)
                }
            }
        }
    }

    /**
     * Traduce el estado de PackageInstaller a un mensaje claro para el usuario.
     * Distingue "el usuario canceló el diálogo de Android" de fallos reales
     * (firma distinta, conflicto de paquete, incompatible, almacenamiento, etc.).
     * Sin esto, todo se reporta como "rechazada" y es imposible diagnosticar.
     */
    private fun describeFailure(context: Context, status: Int, intent: Intent): String {
        val extra = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
        val base = context.getString(R.string.update_install_rejected)
        val detail = when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                context.getString(R.string.update_fail_blocked)
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                context.getString(R.string.update_fail_aborted)
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                context.getString(R.string.update_fail_conflict)
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                context.getString(R.string.update_fail_storage)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                context.getString(R.string.update_fail_incompatible)
            else -> context.getString(R.string.update_fail_generic)
        }
        return buildString {
            append(base).append(' ').append(detail)
            if (!extra.isNullOrBlank()) append(" (").append(extra).append(')')
        }
    }

    companion object {
        const val ACTION_PACKAGE_INSTALL_RESULT = "com.ordia.app.action.PACKAGE_INSTALL_RESULT"
    }
}

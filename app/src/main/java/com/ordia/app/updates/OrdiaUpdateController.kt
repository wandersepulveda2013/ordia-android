package com.ordia.app.updates

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.ordia.app.R
import com.ordia.app.updates.OrdiaUpdateManager.Release
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Máquina de estados de la actualización in-app de Ordía.
 *
 * Orquesta el ciclo completo (comprobar → avisar → descargar con progreso real →
 * verificar → instalar con PackageInstaller) sin bloquear el arranque ni la UI:
 * todo el trabajo de red/IO vive en [scope] (Dispatchers.IO) y la UI solo observa
 * [state]. El origen del feed (update-manifest.json) queda desacoplado del agente
 * que lo publica: cambiar de agente no cambia el mecanismo.
 */
object OrdiaUpdateController {
    sealed interface UpdateState {
        /** No se ha comprobado todavía en esta sesión. */
        data object Idle : UpdateState

        /** Comprobación de feed en curso (no bloquea nada). */
        data object Checking : UpdateState

        /** Se comprobó y la versión instalada es la más reciente. */
        data object UpToDate : UpdateState

        /** Existe una versión nueva; [mandatory] obliga a actualizar. */
        data class Available(val release: Release, val mandatory: Boolean) : UpdateState

        /** Descarga en curso con progreso real (bytes/total; total puede ser -1 desconocido). */
        data class Downloading(val release: Release, val bytes: Long, val total: Long) : UpdateState

        /** APK descargada y verificada (SHA-256 + tamaño + paquete + versión + firma). */
        data class Ready(val release: Release, val downloadId: Long) : UpdateState

        data object Installing : UpdateState

        data object Installed : UpdateState

        data class Failed(val reason: String, val release: Release?) : UpdateState
    }

    private const val PREFS = "ordia_updates"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val POLL_INTERVAL_MILLIS = 400L

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var lastRelease: Release? = null

    fun lastCheckAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK_AT, 0L)

    /**
     * Comprobación no bloqueante. Nunca lanza excepciones a la UI: sin red, con el
     * servidor caído o con manifiesto inválido la app abre igual (estado [UpdateState.Failed]).
     */
    fun checkNow(context: Context) {
        val current = _state.value
        if (current is UpdateState.Checking) return
        if (current is UpdateState.Downloading || current is UpdateState.Ready || current is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        scope.launch {
            val appContext = context.applicationContext
            val result = OrdiaUpdateManager.checkDetailed(appContext)
            prefs(appContext).edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
            _state.value = when (result) {
                is OrdiaUpdateManager.CheckResult.Available -> {
                    lastRelease = result.release
                    UpdateState.Available(result.release, result.release.mandatory)
                }
                OrdiaUpdateManager.CheckResult.UpToDate -> UpdateState.UpToDate
                is OrdiaUpdateManager.CheckResult.Failed -> UpdateState.Failed(result.reason, null)
            }
        }
    }

    /** Inicia (o reanuda) la descarga con progreso real, cancelable y reintentable. */
    fun download(context: Context, release: Release) {
        downloadJob?.cancel()
        lastRelease = release
        _state.value = UpdateState.Downloading(release, 0L, release.apkBytes)
        downloadJob = scope.launch {
            val appContext = context.applicationContext
            val id = OrdiaUpdateManager.download(appContext, release, allowMetered = true, userInitiated = false)
            if (id == null) {
                _state.value = UpdateState.Failed(
                    appContext.getString(R.string.update_download_failed),
                    release
                )
                return@launch
            }
            while (true) {
                val progress = OrdiaUpdateManager.downloadProgress(appContext, id)
                if (progress == null) {
                    OrdiaUpdateManager.discardDownload(appContext, id)
                    _state.value = UpdateState.Failed(
                        appContext.getString(R.string.update_download_failed),
                        release
                    )
                    return@launch
                }
                when (progress.status) {
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED -> {
                        // STATUS_PAUSED cubre la recuperación ante pérdida de conexión:
                        // DownloadManager reanuda solo cuando vuelve la red.
                        _state.value = UpdateState.Downloading(release, progress.bytes, progress.total)
                        delay(POLL_INTERVAL_MILLIS)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _state.value = UpdateState.Downloading(release, progress.bytes, progress.total)
                        when (val validation = OrdiaUpdateManager.validateDownloadedPackage(appContext, id)) {
                            is OrdiaUpdateManager.ValidationResult.Valid ->
                                _state.value = UpdateState.Ready(release, id)
                            is OrdiaUpdateManager.ValidationResult.Invalid -> {
                                OrdiaUpdateManager.discardDownload(appContext, id)
                                _state.value = UpdateState.Failed(validation.reason, release)
                            }
                        }
                        return@launch
                    }
                    else -> {
                        // STATUS_FAILED permanente: se descarta y se ofrece reintentar.
                        OrdiaUpdateManager.discardDownload(appContext, id)
                        _state.value = UpdateState.Failed(
                            appContext.getString(R.string.update_download_failed),
                            release
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun cancel(context: Context) {
        downloadJob?.cancel()
        downloadJob = null
        val current = _state.value
        if (current is UpdateState.Downloading) {
            OrdiaUpdateManager.discardCurrent(context.applicationContext)
            _state.value = UpdateState.Available(current.release, current.release.mandatory)
        }
    }

    /** Abre el flujo de instalación verificado (PackageInstaller en UpdateInstallActivity). */
    fun install(context: Context) {
        val current = _state.value
        if (current !is UpdateState.Ready) return
        lastRelease = current.release
        _state.value = UpdateState.Installing
        runCatching {
            context.startActivity(
                Intent(context, UpdateInstallActivity::class.java)
                    .putExtra(UpdateInstallActivity.EXTRA_DOWNLOAD_ID, current.downloadId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            _state.value = UpdateState.Failed(
                it.message ?: context.getString(R.string.update_install_launch_error),
                current.release
            )
        }
    }

    /** "Más tarde": la actualización disponible sigue visible (badge) pero se descarta el diálogo. */
    fun dismissAvailable() {
        if (_state.value is UpdateState.Available) _state.value = UpdateState.Idle
    }

    fun retry(context: Context) {
        val current = _state.value
        when (current) {
            is UpdateState.Failed -> if (current.release != null) download(context, current.release) else checkNow(context)
            is UpdateState.Available -> download(context, current.release)
            else -> checkNow(context)
        }
    }

    /** Lo notifica UpdateInstallResultReceiver al terminar la confirmación de Android. */
    fun onInstallResult(success: Boolean, reason: String? = null) {
        _state.value = if (success) UpdateState.Installed else {
            UpdateState.Failed(reason ?: "Instalación cancelada o rechazada.", lastRelease)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

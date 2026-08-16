package com.ordia.app.updates

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ordia.app.BuildConfig
import com.ordia.app.MainActivity
import com.ordia.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Hardened local-first updater for APKs distributed through the official GitHub Releases page. */
object OrdiaUpdateManager {
    private const val RELEASES_PAGE = "https://github.com/wandersepulveda2013/ordia-android/releases"

    /** Página pública de releases para descarga manual cuando la self-update no es viable (firma distinta). */
    val releasePageUrl: String get() = RELEASES_PAGE
    private const val WORK_NAME = "ordia-auto-update"
    private const val PREFS = "ordia_updates"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_DOWNLOAD_CODE = "download_code"
    private const val KEY_EXPECTED_SHA256 = "expected_sha256"
    private const val KEY_EXPECTED_BYTES = "expected_bytes"
    private const val KEY_DOWNLOAD_STARTED_AT = "download_started_at"
    private const val CHANNEL = "ordia_updates"
    private const val NOTIFICATION_ID = 3001
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private const val MAX_VERIFIED_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val MAX_HTTP_REDIRECTS = 5
    private const val UPDATE_DIRECTORY = "verified-updates"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val HTTP_READ_TIMEOUT_MILLIS = 30_000
    private const val HTTP_CONNECT_TIMEOUT_MILLIS = 15_000
    /** Sentinel almacenado en KEY_DOWNLOAD_ID cuando la descarga se hizo por canal HTTP
     *  directo (sin DownloadManager). [isManagedDownload] lo acepta como descarga válida. */
    private const val HTTP_DOWNLOAD_SENTINEL = -2L

    private val validationMutex = Mutex()
    private val downloadLock = Any()

    data class Release(
        val tag: String,
        val code: Int,
        val pageUrl: String,
        val apkUrl: String,
        val sha256: String,
        val apkBytes: Long,
        val changelog: String = "",
        val mandatory: Boolean = false,
        val minSupportedVersion: Int = 1,
        val releaseDate: String? = null
    )

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class Available(val release: Release) : CheckResult
        data class Failed(val reason: String) : CheckResult
    }

    sealed interface ValidationResult {
        data class Valid(val uri: Uri) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    fun schedule(context: Context) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) {
            cancelSchedule(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<OrdiaUpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelSchedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Consulta el canal estable y decide si existe una versión nueva.
     *
     * El feed es un `update-manifest.json` firmado de forma indirecta por la propia
     * GitHub Release (HTTPS + allow-list de [UpdateSecurityRules]). Nunca se considera
     * nueva una versión cuyo versionCode no sea ESTRICTAMENTE superior al instalado.
     */
    suspend fun checkDetailed(context: Context): CheckResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) {
            return@withContext CheckResult.Failed(context.getString(R.string.update_fail_store_channel))
        }
        runCatching {
            val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL
            require(UpdateSecurityRules.isTrustedLatestDownloadUrl(manifestUrl)) {
                "El origen de actualización no es confiable."
            }
            val manifest = UpdateManifestParser.parse(
                requestText(manifestUrl, MAX_MANIFEST_BYTES),
                MAX_APK_BYTES
            )
            require(
                UpdateSecurityRules.isTrustedApkUrl(
                    manifest.apkUrl,
                    UpdateSecurityRules.expectedApkName(BuildConfig.UPDATE_FLAVOR)
                )
            ) {
                "La APK publicada no proviene del canal oficial."
            }
            require(manifest.channel.isBlank() || manifest.channel.equals("stable", ignoreCase = true)) {
                context.getString(R.string.update_fail_channel, manifest.channel)
            }
            if (!UpdateSecurityRules.isNewerCode(manifest.versionCode, BuildConfig.VERSION_CODE)) {
                return@runCatching CheckResult.UpToDate
            }
            CheckResult.Available(
                Release(
                    tag = manifest.versionName,
                    code = manifest.versionCode,
                    pageUrl = RELEASES_PAGE,
                    apkUrl = manifest.apkUrl,
                    sha256 = manifest.sha256.lowercase(),
                    apkBytes = manifest.size,
                    changelog = manifest.changelog,
                    mandatory = UpdateSecurityRules.isMandatoryUpdate(
                        mandatory = manifest.mandatory,
                        installedCode = BuildConfig.VERSION_CODE,
                        minSupportedVersion = manifest.minSupportedVersion
                    ),
                    minSupportedVersion = manifest.minSupportedVersion,
                    releaseDate = manifest.releaseDate
                )
            )
        }.getOrElse { error ->
            // Código interno seguro (no se muestra tal cual al usuario): registra la
            // causa sin exponer detalles sensibles y muestra un mensaje genérico cuando
            // el rechazo es por validación de seguridad (origen/APK no confiables, etc.).
            val message = error.message.orEmpty()
            val securityFailure = message.contains("confiable", ignoreCase = true) ||
                message.contains("canal oficial", ignoreCase = true) ||
                message.contains("formato", ignoreCase = true) ||
                message.contains("no es", ignoreCase = true)
            val reason = if (securityFailure) {
                context.getString(R.string.update_fail_security)
            } else {
                message.take(180).ifBlank { context.getString(R.string.update_fail_github) }
            }
            CheckResult.Failed(reason)
        }
    }

    /**
     * Starts or resumes one managed download. A successful existing download is reopened only
     * after an explicit user action; background workers never launch UI.
     */
    fun download(
        context: Context,
        release: Release,
        allowMetered: Boolean = true,
        userInitiated: Boolean = false
    ): Long? = synchronized(downloadLock) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return@synchronized null
        activeDownload(context, release.code, release.sha256)?.let { active ->
            if (userInitiated && active.status == DownloadManager.STATUS_SUCCESSFUL) {
                openInstallFlow(context, active.id)
            }
            return@synchronized active.id
        }
        if (!UpdateSecurityRules.isTrustedApkUrl(release.apkUrl, UpdateSecurityRules.expectedApkName(BuildConfig.UPDATE_FLAVOR)) ||
            !UpdateSecurityRules.isValidSha256(release.sha256) ||
            !UpdateSecurityRules.isReportedSizeAcceptable(release.apkBytes, MAX_APK_BYTES)
        ) {
            return@synchronized null
        }
        discardCurrentManagedDownload(context)
        val destinationName = "Ordia-${release.code}.download.apk"
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { directory ->
            File(directory, destinationName).delete()
        }

        val id = runCatching {
            val request = DownloadManager.Request(Uri.parse(release.apkUrl))
                .setTitle(context.getString(R.string.update_download_title))
                .setDescription(context.getString(R.string.update_download_description, release.tag))
                .setMimeType(APK_MIME)
                // Never expose a completed, unverified APK through DownloadManager's notification.
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(allowMetered)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    destinationName
                )
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }.getOrNull() ?: return@synchronized null
        val metadataSaved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putInt(KEY_DOWNLOAD_CODE, release.code)
            .putString(KEY_EXPECTED_SHA256, release.sha256.lowercase())
            .putLong(KEY_EXPECTED_BYTES, release.apkBytes)
            .putLong(KEY_DOWNLOAD_STARTED_AT, System.currentTimeMillis())
            .commit()
        if (!metadataSaved) {
            removeDownload(context, id)
            return@synchronized null
        }
        id
    }

    fun isManagedDownload(context: Context, id: Long): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DOWNLOAD_ID, -1L) == id

    /** Estado de descarga en vivo para la UI in-app (bytes, total y estado DownloadManager). */
    data class DownloadProgress(val bytes: Long, val total: Long, val status: Int)

    fun downloadProgress(context: Context, id: Long): DownloadProgress? = runCatching {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (bytesIndex < 0 || totalIndex < 0 || statusIndex < 0) null
            else DownloadProgress(cursor.getLong(bytesIndex), cursor.getLong(totalIndex), cursor.getInt(statusIndex))
        }
    }.getOrNull()

    fun currentDownloadCode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DOWNLOAD_CODE, -1)

    /** Descarta la descarga gestionada actual sin conocer su id (usado al fallar la instalación). */
    fun discardCurrent(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (storedId > 0L) removeDownload(context, storedId)
        clearDownloadMetadata(context)
        deleteVerifiedPackages(context)
    }

    fun discardDownload(context: Context, id: Long) {
        if (!isManagedDownload(context, id)) return
        removeDownload(context, id)
        clearDownloadMetadata(context)
        deleteVerifiedPackages(context)
    }

    /** Removes stale private APKs and metadata after successful updates or abandoned attempts. */
    fun cleanupObsolete(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getInt(KEY_DOWNLOAD_CODE, -1)
        val startedAt = prefs.getLong(KEY_DOWNLOAD_STARTED_AT, 0L)
        val stale = startedAt > 0L && System.currentTimeMillis() - startedAt > MAX_VERIFIED_AGE_MILLIS
        if (code in 1..BuildConfig.VERSION_CODE || stale) {
            prefs.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it > 0L }?.let { removeDownload(context, it) }
            clearDownloadMetadata(context)
        }
        val cutoff = System.currentTimeMillis() - MAX_VERIFIED_AGE_MILLIS
        verifiedDirectory(context).listFiles().orEmpty().forEach { file ->
            val fileCode = Regex("Ordia-(\\d+)\\.apk").matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()
            if (fileCode == null || fileCode <= BuildConfig.VERSION_CODE || file.lastModified() < cutoff) file.delete()
        }
    }

    suspend fun validateDownloadedPackage(context: Context, id: Long): ValidationResult =
        validationMutex.withLock {
            withContext(Dispatchers.IO) {
                validateDownloadedPackageLocked(context, id)
            }
        }

    /**
     * Valida un archivo APK ya descargado al directorio verificado, identificado por su
     * versionCode. Canal independiente de DownloadManager: usado por la descarga HTTP
     * directa y por la re-validación en [UpdateInstallActivity]. Reutiliza exactamente
     * la misma validación (SHA-256, tamaño, applicationId, versionCode y firma) que el
     * canal DownloadManager, de modo que la seguridad es idéntica cualquiera que sea el
     * origen de los bytes.
     */
    suspend fun validateVerifiedFile(context: Context, code: Int): ValidationResult =
        validationMutex.withLock {
            withContext(Dispatchers.IO) {
                validateVerifiedFileLocked(context, code)
            }
        }

    private fun validateVerifiedFileLocked(context: Context, code: Int): ValidationResult {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expectedHash = prefs.getString(KEY_EXPECTED_SHA256, null)
            ?.takeIf(UpdateSecurityRules::isValidSha256)
            ?: return ValidationResult.Invalid(context.getString(R.string.update_invalid_no_checksum))
        val expectedCode = prefs.getInt(KEY_DOWNLOAD_CODE, -1)
        val expectedBytes = prefs.getLong(KEY_EXPECTED_BYTES, -1L)
        if (expectedCode != code) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_not_managed))
        }
        if (!UpdateSecurityRules.isReportedSizeAcceptable(expectedBytes, MAX_APK_BYTES)) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_no_size))
        }
        if (expectedCode <= BuildConfig.VERSION_CODE) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_old_version))
        }
        val verified = File(verifiedDirectory(context), "Ordia-$expectedCode.apk")
        if (!verified.exists() || verified.length() <= 0L) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_cannot_open))
        }
        return try {
            val actualBytes = verified.length()
            require(actualBytes == expectedBytes) {
                context.getString(R.string.update_invalid_size_detail, actualBytes, expectedBytes)
            }
            require(sha256(verified).equals(expectedHash, ignoreCase = true)) {
                context.getString(R.string.update_invalid_sha)
            }
            verifyArchive(context, verified, expectedCode)
            verified.setLastModified(System.currentTimeMillis())
            val privateUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.update-files",
                verified
            )
            ValidationResult.Valid(privateUri)
        } catch (error: Exception) {
            verified.delete()
            val rawMessage = error.message.orEmpty()
            val reason = when {
                rawMessage == "SIGNATURE_MISMATCH" ->
                    context.getString(R.string.update_invalid_signature_mismatch)
                else -> rawMessage.take(180).ifBlank { context.getString(R.string.update_invalid_cannot_validate) }
            }
            ValidationResult.Invalid(reason)
        }
    }

    /**
     * Descarga la APK de una release por canal HTTP directo (HttpURLConnection siguiendo
     * redirecciones de forma segura, el mismo mecanismo que el fetch del manifiesto) y la
     * valida integramente. Este canal es más fiable que DownloadManager en dispositivos/OEM
     * donde este último no sigue bien las redirecciones firmadas de GitHub a
     * objects.githubusercontent.com y la descarga muere silenciosamente antes de pedir
     * permiso de instalación.
     *
     * Escribe a un archivo temporal, calcula SHA-256 y tamaño en flujo, y solo promueve a
     * archivo verificado si todo coincide. [onProgress] recibe (bytes, total) para la UI.
     * Devuelve Valid(uri) si la APK quedó verificada, o Invalid(reason) si falló.
     */
    suspend fun downloadFileHttp(
        context: Context,
        release: Release,
        onProgress: (Long, Long) -> Unit
    ): ValidationResult = validationMutex.withLock {
        withContext(Dispatchers.IO) {
            downloadFileHttpLocked(context, release, onProgress)
        }
    }

    private fun downloadFileHttpLocked(
        context: Context,
        release: Release,
        onProgress: (Long, Long) -> Unit
    ): ValidationResult {
        if (!BuildConfig.SELF_UPDATE_ENABLED) {
            return ValidationResult.Invalid(context.getString(R.string.update_fail_security))
        }
        if (!UpdateSecurityRules.isTrustedApkUrl(release.apkUrl, UpdateSecurityRules.expectedApkName(BuildConfig.UPDATE_FLAVOR)) ||
            !UpdateSecurityRules.isValidSha256(release.sha256) ||
            !UpdateSecurityRules.isReportedSizeAcceptable(release.apkBytes, MAX_APK_BYTES)
        ) {
            return ValidationResult.Invalid(context.getString(R.string.update_fail_security))
        }
        discardCurrentManagedDownload(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_DOWNLOAD_CODE, release.code)
            .putString(KEY_EXPECTED_SHA256, release.sha256.lowercase())
            .putLong(KEY_EXPECTED_BYTES, release.apkBytes)
            .putLong(KEY_DOWNLOAD_STARTED_AT, System.currentTimeMillis())
            .putLong(KEY_DOWNLOAD_ID, HTTP_DOWNLOAD_SENTINEL)
            .commit()

        val directory = verifiedDirectory(context)
        val temporary = File(directory, "Ordia-${release.code}.apk.part")
        val verified = File(directory, "Ordia-${release.code}.apk")
        temporary.delete()
        verified.delete()

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val total = release.apkBytes
            streamTrustedUrlToFile(release.apkUrl, temporary, digest) { bytes ->
                onProgress(bytes, total)
            }
            val actualBytes = temporary.length()
            require(actualBytes == release.apkBytes) {
                context.getString(R.string.update_invalid_size_detail, actualBytes, release.apkBytes)
            }
            require(digest.digest().toHex().equals(release.sha256, ignoreCase = true)) {
                context.getString(R.string.update_invalid_sha)
            }
            if (!temporary.renameTo(verified)) {
                temporary.copyTo(verified, overwrite = true)
                temporary.delete()
            }
            verifyArchive(context, verified, release.code)
            verified.setLastModified(System.currentTimeMillis())
            val privateUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.update-files",
                verified
            )
            ValidationResult.Valid(privateUri)
        } catch (error: Exception) {
            temporary.delete()
            verified.delete()
            ValidationResult.Invalid(httpFailureReason(context, error))
        }
    }

    /**
     * Clasifica un error del canal HTTP directo en un motivo legible. Los fallos de red
     * (timeout, status HTTP, host) se reportan con [R.string.update_download_failed]
     * para que el controller pueda reintentar con DownloadManager; los de validación
     * (SHA/firma/tamaño) se reportan con su motivo concreto para no reintentar en vano.
     * Pública para testeo unitario.
     */
    fun httpFailureReason(context: Context, error: Throwable): String {
        return when (val cls = classifyHttpFailure(error)) {
            HttpFailureClass.SIGNATURE -> context.getString(R.string.update_invalid_signature_mismatch)
            HttpFailureClass.NETWORK -> context.getString(R.string.update_download_failed)
            HttpFailureClass.VALIDATION -> (error.message.orEmpty().take(180))
                .ifBlank { context.getString(R.string.update_invalid_cannot_validate) }
        }
    }

    /** Clasificación pura (sin Context) del fallo del canal HTTP, para testeo unitario. */
    enum class HttpFailureClass { SIGNATURE, NETWORK, VALIDATION }

    fun classifyHttpFailure(error: Throwable): HttpFailureClass {
        val rawMessage = error.message.orEmpty()
        return when {
            rawMessage == "SIGNATURE_MISMATCH" -> HttpFailureClass.SIGNATURE
            error is java.io.IOException || rawMessage.contains("GitHub respondió") ||
                rawMessage.contains("redirecciones", ignoreCase = true) ||
                rawMessage.contains("confiable", ignoreCase = true) -> HttpFailureClass.NETWORK
            else -> HttpFailureClass.VALIDATION
        }
    }

    /**
     * Sigue redirecciones (hasta [MAX_HTTP_REDIRECTS]) validando cada host con
     * [UpdateSecurityRules.isTrustedNetworkUrl], y copia el cuerpo a [destination]
     * actualizando [digest] y notificando [onProgress] con los bytes acumulados.
     */
    private fun streamTrustedUrlToFile(
        url: String,
        destination: File,
        digest: MessageDigest,
        onProgress: (Long) -> Unit
    ) {
        var currentUrl = url
        var bytesCopied = 0L
        repeat(MAX_HTTP_REDIRECTS + 1) { redirectCount ->
            require(UpdateSecurityRules.isTrustedNetworkUrl(currentUrl)) { "URL de actualización no confiable." }
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_CONNECT_TIMEOUT_MILLIS
                readTimeout = HTTP_READ_TIMEOUT_MILLIS
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", APK_MIME)
                setRequestProperty("User-Agent", "Ordia/${BuildConfig.VERSION_NAME}")
            }
            try {
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    require(redirectCount < MAX_HTTP_REDIRECTS) { "GitHub devolvió demasiadas redirecciones." }
                    val location = connection.getHeaderField("Location")
                        ?.let { URL(URL(currentUrl), it).toString() }
                        ?: error("GitHub devolvió una redirección sin destino.")
                    require(UpdateSecurityRules.isTrustedNetworkUrl(location)) {
                        "GitHub redirigió a un host no confiable."
                    }
                    currentUrl = location
                    return@repeat
                }
                if (status !in 200..299) error("GitHub respondió $status.")
                connection.inputStream.use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            bytesCopied += read
                            require(bytesCopied <= MAX_APK_BYTES) { "La APK supera el tamaño máximo permitido." }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(bytesCopied)
                        }
                    }
                }
                require(bytesCopied > 0L) { "La APK descargada está vacía." }
                return
            } finally {
                connection.disconnect()
            }
        }
        error("No se pudo resolver la URL de actualización.")
    }

    private fun validateDownloadedPackageLocked(context: Context, id: Long): ValidationResult {
        if (!isManagedDownload(context, id)) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_not_managed))
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expectedHash = prefs.getString(KEY_EXPECTED_SHA256, null)
            ?.takeIf(UpdateSecurityRules::isValidSha256)
            ?: return ValidationResult.Invalid(context.getString(R.string.update_invalid_no_checksum))
        val expectedCode = prefs.getInt(KEY_DOWNLOAD_CODE, -1)
        val expectedBytes = prefs.getLong(KEY_EXPECTED_BYTES, -1L)
        if (!UpdateSecurityRules.isReportedSizeAcceptable(expectedBytes, MAX_APK_BYTES)) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_no_size))
        }
        if (expectedCode <= BuildConfig.VERSION_CODE) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_old_version))
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        if (queryStatus(manager, id) != DownloadManager.STATUS_SUCCESSFUL) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_incomplete))
        }
        val reportedBytes = queryTotalBytes(manager, id)
        if (!UpdateSecurityRules.isReportedSizeAcceptable(reportedBytes, MAX_APK_BYTES)) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_bad_size))
        }
        if (reportedBytes != null && reportedBytes >= 0L && reportedBytes != expectedBytes) {
            return ValidationResult.Invalid(context.getString(R.string.update_invalid_size_mismatch))
        }
        val sourceUri = manager.getUriForDownloadedFile(id)
            ?: return ValidationResult.Invalid(context.getString(R.string.update_invalid_cannot_open))
        val directory = verifiedDirectory(context)
        val temporary = File(directory, "Ordia-$expectedCode.apk.part")
        val verified = File(directory, "Ordia-$expectedCode.apk")
        var preserveVerified = false

        return try {
            temporary.delete()
            verified.delete()
            copyAndHash(context, sourceUri, temporary).also { copied ->
                require(copied.bytes == expectedBytes) {
                    context.getString(R.string.update_invalid_size_detail, copied.bytes, expectedBytes)
                }
                require(copied.sha256.equals(expectedHash, ignoreCase = true)) {
                    context.getString(R.string.update_invalid_sha)
                }
            }
            verifyArchive(context, temporary, expectedCode)

            if (!temporary.renameTo(verified)) {
                temporary.copyTo(verified, overwrite = true)
                temporary.delete()
            }
            // Verify the exact private bytes Android will receive, not only the external source.
            require(sha256(verified).equals(expectedHash, ignoreCase = true)) {
                context.getString(R.string.update_invalid_changed)
            }
            verifyArchive(context, verified, expectedCode)
            verified.setLastModified(System.currentTimeMillis())
            val privateUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.update-files",
                verified
            )
            preserveVerified = true
            ValidationResult.Valid(privateUri)
        } catch (error: Exception) {
            val rawMessage = error.message.orEmpty()
            val reason = when {
                rawMessage == "SIGNATURE_MISMATCH" ->
                    context.getString(R.string.update_invalid_signature_mismatch)
                else -> rawMessage.take(180).ifBlank { context.getString(R.string.update_invalid_cannot_validate) }
            }
            ValidationResult.Invalid(reason)
        } finally {
            temporary.delete()
            if (!preserveVerified) verified.delete()
        }
    }

    fun showAvailable(context: Context, release: Release) {
        createChannel(context)
        val pending = PendingIntent.getActivity(
            context,
            31,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.OPEN_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_ordia)
                .setContentTitle(context.getString(R.string.update_available_title))
                .setContentText(context.getString(R.string.update_available_text, release.tag))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    fun showInstall(context: Context, downloadId: Long) {
        createChannel(context)
        val pending = PendingIntent.getActivity(
            context,
            32,
            Intent(context, UpdateInstallActivity::class.java)
                .putExtra(UpdateInstallActivity.EXTRA_DOWNLOAD_ID, downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_ordia)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(context.getString(R.string.update_ready_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    fun showFailure(context: Context, reason: String) {
        createChannel(context)
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID + 2,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_ordia)
                .setContentTitle(context.getString(R.string.update_discarded_title))
                .setContentText(reason.take(140))
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    private data class ManagedDownload(val id: Long, val status: Int)

    private fun activeDownload(context: Context, releaseCode: Int, expectedHash: String): ManagedDownload? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DOWNLOAD_CODE, -1) != releaseCode) return null
        if (!prefs.getString(KEY_EXPECTED_SHA256, "").equals(expectedHash, ignoreCase = true)) return null
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) return null
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val status = queryStatus(manager, id) ?: return null
        return if (status in setOf(
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_SUCCESSFUL
            )) ManagedDownload(id, status) else null
    }

    private fun openInstallFlow(context: Context, id: Long) {
        runCatching {
            context.startActivity(
                Intent(context, UpdateInstallActivity::class.java)
                    .putExtra(UpdateInstallActivity.EXTRA_DOWNLOAD_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun discardCurrentManagedDownload(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it > 0L }?.let { removeDownload(context, it) }
        clearDownloadMetadata(context)
        deleteVerifiedPackages(context)
    }

    private fun removeDownload(context: Context, id: Long) {
        runCatching { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) }
    }

    private fun clearDownloadMetadata(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_CODE)
            .remove(KEY_EXPECTED_SHA256)
            .remove(KEY_EXPECTED_BYTES)
            .remove(KEY_DOWNLOAD_STARTED_AT)
            .apply()
    }

    private fun verifiedDirectory(context: Context): File =
        File(context.filesDir, UPDATE_DIRECTORY).apply { mkdirs() }

    private fun deleteVerifiedPackages(context: Context) {
        verifiedDirectory(context).listFiles().orEmpty().forEach { it.delete() }
    }

    private fun queryStatus(manager: DownloadManager, id: Long): Int? = runCatching {
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (index < 0) null else cursor.getInt(index)
        }
    }.getOrNull()

    private fun queryTotalBytes(manager: DownloadManager, id: Long): Long? = runCatching {
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            if (index < 0) null else cursor.getLong(index)
        }
    }.getOrNull()

    private data class CopyResult(val sha256: String, val bytes: Long)

    private fun copyAndHash(context: Context, sourceUri: Uri, destination: File): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= MAX_APK_BYTES) { "La APK supera el tamaño máximo permitido." }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                require(copied > 0L) { "La APK descargada está vacía." }
            }
        } ?: error("No se pudo leer la APK descargada.")
        return CopyResult(digest.digest().toHex(), destination.length())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun verifyArchive(context: Context, file: File, expectedCode: Int) {
        val archiveInfo = packageInfoForArchive(context, file) ?: error("El archivo descargado no es una APK válida.")
        require(archiveInfo.packageName == context.packageName) {
            "La APK pertenece a otro paquete (${archiveInfo.packageName})."
        }
        val archiveCode = packageVersionCode(archiveInfo)
        require(archiveCode == expectedCode.toLong()) {
            "El versionCode de la APK ($archiveCode) no coincide con la versión publicada ($expectedCode)."
        }
        require(signaturesAreCompatible(packageInfoForInstalledApp(context), archiveInfo)) {
            "SIGNATURE_MISMATCH"
        }
    }

    private fun requestText(url: String, maxBytes: Int): String {
        var currentUrl = url
        repeat(MAX_HTTP_REDIRECTS + 1) { redirectCount ->
            require(UpdateSecurityRules.isTrustedNetworkUrl(currentUrl)) { "URL de actualización no confiable." }
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Ordia/${BuildConfig.VERSION_NAME}")
            }
            try {
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    require(redirectCount < MAX_HTTP_REDIRECTS) { "GitHub devolvió demasiadas redirecciones." }
                    val location = connection.getHeaderField("Location")
                        ?.let { URL(URL(currentUrl), it).toString() }
                        ?: error("GitHub devolvió una redirección sin destino.")
                    require(UpdateSecurityRules.isTrustedNetworkUrl(location)) {
                        "GitHub redirigió a un host no confiable."
                    }
                    currentUrl = location
                    return@repeat
                }
                if (status !in 200..299) error("GitHub respondió $status.")
                connection.contentLengthLong.takeIf { it >= 0L }?.let {
                    require(it <= maxBytes) { "La respuesta de actualización es demasiado grande." }
                }
                val output = java.io.ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        require(output.size() + read <= maxBytes) { "La respuesta de actualización es demasiado grande." }
                        output.write(buffer, 0, read)
                    }
                }
                return UpdateSecurityRules.decodeUtf8Strict(output.toByteArray())
                    ?: error("GitHub devolvió texto UTF-8 inválido.")
            } finally {
                connection.disconnect()
            }
        }
        error("No se pudo resolver la URL de actualización.")
    }

    @Suppress("DEPRECATION")
    private fun packageInfoForArchive(context: Context, file: File): PackageInfo? = when {
        Build.VERSION.SDK_INT >= 33 -> context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )
        Build.VERSION.SDK_INT >= 28 -> context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        else -> context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
    }

    @Suppress("DEPRECATION")
    private fun packageInfoForInstalledApp(context: Context): PackageInfo = when {
        Build.VERSION.SDK_INT >= 33 -> context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )
        Build.VERSION.SDK_INT >= 28 -> context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        else -> context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private data class SigningSnapshot(
        val current: Set<String>,
        val history: Set<String>,
        val multiple: Boolean
    )

    @Suppress("DEPRECATION")
    private fun signingSnapshot(info: PackageInfo): SigningSnapshot {
        if (Build.VERSION.SDK_INT < 28) {
            val digests = info.signatures.orEmpty().mapTo(mutableSetOf()) { digest(it.toByteArray()) }
            return SigningSnapshot(digests, digests, digests.size > 1)
        }
        val signingInfo = info.signingInfo ?: return SigningSnapshot(emptySet(), emptySet(), false)
        val current = signingInfo.apkContentsSigners.orEmpty().mapTo(mutableSetOf()) { digest(it.toByteArray()) }
        val history = if (signingInfo.hasMultipleSigners()) current else {
            signingInfo.signingCertificateHistory.orEmpty().mapTo(mutableSetOf()) { digest(it.toByteArray()) }
        }
        return SigningSnapshot(current, history, signingInfo.hasMultipleSigners())
    }

    private fun signaturesAreCompatible(installed: PackageInfo, candidate: PackageInfo): Boolean {
        val existing = signingSnapshot(installed)
        val update = signingSnapshot(candidate)
        if (existing.current.isEmpty() || update.current.isEmpty()) return false
        return if (existing.multiple || update.multiple) {
            existing.multiple && update.multiple && existing.current == update.current
        } else {
            existing.current.all { it in update.history }
        }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.update_channel_description)
            }
        )
    }
}

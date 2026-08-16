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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Hardened local-first updater for APKs distributed through the official GitHub Releases page. */
object OrdiaUpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/wandersepulveda2013/ordia-android/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/wandersepulveda2013/ordia-android/releases"
    private const val WORK_NAME = "ordia-auto-update"
    private const val PREFS = "ordia_updates"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_DOWNLOAD_CODE = "download_code"
    private const val KEY_EXPECTED_SHA256 = "expected_sha256"
    private const val KEY_EXPECTED_BYTES = "expected_bytes"
    private const val KEY_DOWNLOAD_STARTED_AT = "download_started_at"
    private const val CHANNEL = "ordia_updates"
    private const val NOTIFICATION_ID = 3001
    private const val MAX_RELEASE_JSON_BYTES = 1_000_000
    private const val MAX_CHECKSUM_BYTES = 8_192
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private const val MAX_VERIFIED_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val MAX_HTTP_REDIRECTS = 5
    private const val UPDATE_DIRECTORY = "verified-updates"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val validationMutex = Mutex()
    private val downloadLock = Any()

    data class Release(
        val tag: String,
        val code: Int,
        val pageUrl: String,
        val apkUrl: String,
        val sha256: String,
        val apkBytes: Long
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

    suspend fun checkDetailed(context: Context): CheckResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) {
            return@withContext CheckResult.Failed(context.getString(R.string.update_fail_store_channel))
        }
        runCatching {
            val releaseJson = JSONObject(requestText(RELEASE_API, MAX_RELEASE_JSON_BYTES))
            if (releaseJson.optBoolean("draft") || releaseJson.optBoolean("prerelease")) {
                return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_not_stable))
            }
            val tag = releaseJson.optString("tag_name").trim().takeIf { it.isNotBlank() }
                ?: return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_no_tag))
            val remoteCode = UpdateSecurityRules.parseVersionCodeFromTag(tag)
                ?: return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_bad_tag, tag))
            if (remoteCode <= BuildConfig.VERSION_CODE) return@runCatching CheckResult.UpToDate

            val page = releaseJson.optString("html_url").takeIf(UpdateSecurityRules::isTrustedReleasePageUrl) ?: RELEASES_PAGE
            val assets = releaseJson.optJSONArray("assets")
                ?: return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_no_assets))
            data class Asset(val name: String, val url: String, val bytes: Long)
            val trustedAssets = buildList {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val bytes = asset.optLong("size", -1L)
                    if (name.isNotBlank() && UpdateSecurityRules.isTrustedReleaseAssetUrl(url, name)) {
                        add(Asset(name, url, bytes))
                    }
                }
            }
            val apkName = UpdateSecurityRules.selectExpectedApk(trustedAssets.map { it.name }, remoteCode)
                ?: return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_no_apk, remoteCode))
            val apkMatches = trustedAssets.filter { it.name == apkName }
            if (apkMatches.size != 1 || apkMatches.single().bytes !in 1..MAX_APK_BYTES) {
                return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_ambiguous_apk))
            }
            val checksumName = "$apkName.sha256"
            val checksumMatches = trustedAssets.filter { it.name == checksumName }
            if (checksumMatches.size != 1) {
                return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_no_checksum, apkName))
            }
            val checksum = UpdateSecurityRules.parseChecksum(
                requestText(checksumMatches.single().url, MAX_CHECKSUM_BYTES),
                apkName
            ) ?: return@runCatching CheckResult.Failed(context.getString(R.string.update_fail_bad_checksum))

            CheckResult.Available(
                Release(tag, remoteCode, page, apkMatches.single().url, checksum, apkMatches.single().bytes)
            )
        }.getOrElse { error ->
            CheckResult.Failed(error.message?.take(180) ?: context.getString(R.string.update_fail_github))
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
        if (!UpdateSecurityRules.isTrustedReleaseAssetUrl(release.apkUrl, UpdateSecurityRules.expectedApkName(release.code)) ||
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
            ValidationResult.Invalid(error.message?.take(180) ?: context.getString(R.string.update_invalid_cannot_validate))
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
            "La firma de la APK no coincide con la instalación actual."
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

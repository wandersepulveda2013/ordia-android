package com.ordia.app.update

import com.ordia.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of checking for updates against the latest GitHub Release.
 */
sealed interface UpdateResult {
    data object Checking : UpdateResult
    data object UpToDate : UpdateResult
    data class Available(
        val versionCode: Int,
        val versionName: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val htmlUrl: String,
    ) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/**
 * Checks GitHub Releases for a newer APK build.
 *
 * The release tag must follow the pattern `v{versionName}-{versionCode}`
 * (e.g. `v3.0.0-11`). The trailing integer is compared against
 * [BuildConfig.VERSION_CODE] to decide whether an update is available.
 */
class UpdateChecker(private val repoOwner: String, private val repoName: String) {

    private val apiURL get() = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(apiURL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Ordia-Android/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            conn.inputStream.bufferedReader().use { it.readText() }
                .let { json -> parseRelease(JSONObject(json)) }
        }.getOrElse { e -> UpdateResult.Error(e.message ?: "Error desconocido") }
    }

    private fun parseRelease(json: JSONObject): UpdateResult {
        val tagName = json.optString("tag_name", "")
        val remoteVersionCode = extractVersionCode(tagName)
            ?: return UpdateResult.Error("Tag de release inválido: $tagName")

        if (remoteVersionCode <= BuildConfig.VERSION_CODE) return UpdateResult.UpToDate

        val apkUrl = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).map { assets.optJSONObject(it) }
                .firstOrNull { it?.optString("name", "")?.endsWith(".apk", ignoreCase = true) == true }
                ?.optString("browser_download_url", "")
        } ?: ""

        if (apkUrl.isBlank()) return UpdateResult.Error("No se encontró APK en la release")

        return UpdateResult.Available(
            versionCode = remoteVersionCode,
            versionName = extractVersionName(tagName),
            releaseNotes = json.optString("body", "").ifBlank { "Nueva versión disponible." },
            apkDownloadUrl = apkUrl,
            htmlUrl = json.optString("html_url", ""),
        )
    }

    /** Extracts the trailing integer from a tag like `v3.0.0-11` → `11`. */
    private fun extractVersionCode(tag: String): Int? =
        tag.substringAfterLast('-').removeSuffix(")").toIntOrNull()

    /** Extracts the version name from a tag like `v3.0.0-11` → `3.0.0`. */
    private fun extractVersionName(tag: String): String {
        val withoutV = tag.removePrefix("v")
        val dashIndex = withoutV.lastIndexOf('-')
        return if (dashIndex > 0) withoutV.substring(0, dashIndex) else withoutV
    }
}

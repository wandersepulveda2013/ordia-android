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
 * The release tag is expected to contain a versionCode integer.
 * Supported tag formats (the last integer is used as versionCode):
 *   - `v3.0.0-11`          → versionCode=11, versionName=3.0.0
 *   - `v3.0.0-build.31662` → versionCode=31662, versionName=3.0.0
 *   - `v1.0.0-10`           → versionCode=10, versionName=1.0.0
 * The extracted versionCode is compared against [BuildConfig.VERSION_CODE].
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
                instanceFollowRedirects = true
            }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                return@runCatching UpdateResult.Error(
                    "GitHub API respondió $responseCode. " +
                        if (responseCode == 403) "Límite de peticiones alcanzado. Intenta más tarde."
                        else "No se pudo conectar con el servidor de actualizaciones."
                )
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseRelease(JSONObject(body))
        }.getOrElse { e ->
            UpdateResult.Error(e.message ?: "Error de conexión. Verifica tu internet.")
        }
    }

    internal fun parseRelease(json: JSONObject): UpdateResult {
        val tagName = json.optString("tag_name", "")
        if (tagName.isBlank()) {
            val apiMessage = json.optString("message", "")
            return UpdateResult.Error(
                if (apiMessage.isNotBlank()) "GitHub: $apiMessage"
                else "No se encontró información de versión en la release."
            )
        }

        val remoteVersionCode = extractVersionCode(tagName)
        if (remoteVersionCode == null) {
            return UpdateResult.Error(
                "La etiqueta '$tagName' no tiene un código de versión válido. " +
                    "Formato esperado: v3.0.0-12 (número al final)."
            )
        }

        val remoteVersionName = extractVersionName(tagName)

        if (remoteVersionCode <= BuildConfig.VERSION_CODE) {
            return UpdateResult.UpToDate
        }

        val apkUrl = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).mapNotNull { i -> assets.optJSONObject(i) }
                .firstOrNull { it.optString("name", "").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url", "")
        } ?: ""

        if (apkUrl.isBlank()) {
            return UpdateResult.Error("La release $remoteVersionName existe pero no contiene un APK descargable.")
        }

        return UpdateResult.Available(
            versionCode = remoteVersionCode,
            versionName = remoteVersionName,
            releaseNotes = json.optString("body", "").ifBlank { "Nueva versión $remoteVersionName disponible." },
            apkDownloadUrl = apkUrl,
            htmlUrl = json.optString("html_url", ""),
        )
    }

    /**
     * Extracts the last integer from a tag string as the versionCode.
     *   - `v3.0.0-11`           → `11`
     *   - `v3.0.0-build.31662`  → `31662`
     *   - `v1.0.0-10`            → `10`
     *   - `v3.0.0`              → `0`
     */
    private fun extractVersionCode(tag: String): Int? {
        val matches = Regex("\\d+").findAll(tag).toList()
        return if (matches.isEmpty()) null else matches.last().value.toIntOrNull()
    }

    /**
     * Extracts the version name (everything after 'v', before the last `-`).
     *   - `v3.0.0-11`          → `3.0.0`
     *   - `v3.0.0-build.12345` → `3.0.0`
     *   - `v1.0.0`             → `1.0.0`
     */
    private fun extractVersionName(tag: String): String {
        val withoutV = tag.removePrefix("v")
        val dashIndex = withoutV.lastIndexOf('-')
        return if (dashIndex > 0) withoutV.substring(0, dashIndex) else withoutV
    }
}

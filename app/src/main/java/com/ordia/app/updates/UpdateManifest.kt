package com.ordia.app.updates

import org.json.JSONObject

/**
 * Contrato de publicación estable del canal de actualizaciones de Ordía.
 *
 * El CI publica un `update-manifest.json` por variante (update-manifest-safe,
 * update-manifest-full, update-manifest-advanced) como asset de la GitHub Release.
 * El cliente descarga SOLO su propia variante desde una URL estable y decide si
 * existe una versión nueva comparando el versionCode (estrictamente mayor).
 *
 * Fuera de estos campos el manifiesto no se usa: la APK se verifica por su
 * SHA-256, tamaño, applicationId, versionCode y firma (compatible con la
 * instalada) antes de instalarse. La URL de la APK debe provenir de la release
 * oficial del repositorio (allow-list en [UpdateSecurityRules]).
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val releaseDate: String?,
    val changelog: String,
    val mandatory: Boolean,
    val minSupportedVersion: Int,
    val channel: String
)

/** Parser estricto y puro del manifiesto de actualización (unit-testable). */
object UpdateManifestParser {
    private const val MAX_MANIFEST_JSON_BYTES = 64 * 1024
    private const val MAX_VERSION_NAME = 64
    private const val MAX_CHANGELOG = 2_000
    private const val MAX_CHANNEL = 24

    fun parse(text: String, maxApkBytes: Long): UpdateManifest {
        if (text.isBlank() || text.length > MAX_MANIFEST_JSON_BYTES) {
            error("El manifiesto de actualización está vacío o es demasiado grande.")
        }
        val root = runCatching { JSONObject(text) }
            .getOrElse { error("El manifiesto de actualización no es JSON válido.") }

        val versionCode = root.optInt("versionCode", -1).takeIf { it > 0 }
            ?: error("El manifiesto no tiene un versionCode válido.")

        val versionName = root.optString("versionName").trim()
            .takeIf { it.isNotBlank() && it.length <= MAX_VERSION_NAME }
            ?: error("El manifiesto no tiene un versionName válido.")

        val apkUrl = root.optString("apkUrl").trim()
            .takeIf { it.isNotBlank() }
            ?: error("El manifiesto no define la URL de la APK.")

        val sha256 = root.optString("sha256").trim()
            .takeIf(UpdateSecurityRules::isValidSha256)
            ?: error("El manifiesto no tiene un SHA-256 válido.")

        // El tamaño es obligatorio: la verificación pre-instalación lo exige exacto.
        val size = root.optLong("size", -1L)
        if (size !in 1..maxApkBytes) error("El manifiesto anuncia un tamaño inválido.")

        val releaseDate = root.optString("releaseDate").trim().takeIf { it.isNotBlank() }

        val changelog = root.optString("changelog").takeIf { it.length <= MAX_CHANGELOG }
            ?: error("El changelog del manifiesto es demasiado grande.")

        val mandatory = root.optBoolean("mandatory", false)

        val minSupportedVersion = root.optInt("minSupportedVersion", 1).takeIf { it > 0 }
            ?: error("El manifiesto exige un minSupportedVersion positivo.")

        val channel = root.optString("channel").trim()
            .takeIf { it.isBlank() || it.length <= MAX_CHANNEL }
            ?: error("El canal del manifiesto es demasiado largo.")

        return UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            size = size,
            releaseDate = releaseDate,
            changelog = changelog,
            mandatory = mandatory,
            minSupportedVersion = minSupportedVersion,
            channel = channel.ifBlank { "stable" }
        )
    }
}

package com.ordia.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas puras del manifiesto de actualización. Cubren los escenarios que
 * pueden decidirse sin dispositivo: manifiesto válido/nuevo/igual/viejo,
 * manifiesto inválido, SHA incorrecto, tamaño inválido y reglas de canal.
 */
class UpdateManifestParserTest {

    private val validSha = "a".repeat(64)
    private val maxApkBytes = 250L * 1024L * 1024L

    private fun json(
        versionCode: Int = 1_300_000_101,
        versionName: String = "3.0.17-preview-safe.1",
        apkUrl: String = "https://github.com/wandersepulveda2013/ordia-android/releases/download/v3.0.0-build.1/Ordia-3.0-safe-signed.apk",
        sha256: String = validSha,
        size: Long = 35_000_000L,
        releaseDate: String? = "2026-08-15",
        changelog: String = "Mejoras de estabilidad.",
        mandatory: Boolean = false,
        minSupportedVersion: Int = 1,
        channel: String = "stable"
    ): String {
        val fields = StringBuilder()
        fields.append("\"versionCode\": $versionCode")
        fields.append(", \"versionName\": \"$versionName\"")
        fields.append(", \"apkUrl\": \"$apkUrl\"")
        fields.append(", \"sha256\": \"$sha256\"")
        fields.append(", \"size\": $size")
        if (releaseDate != null) fields.append(", \"releaseDate\": \"$releaseDate\"")
        fields.append(", \"changelog\": \"$changelog\"")
        fields.append(", \"mandatory\": $mandatory")
        fields.append(", \"minSupportedVersion\": $minSupportedVersion")
        fields.append(", \"channel\": \"$channel\"")
        return "{ $fields }"
    }

    @Test fun validManifest_isParsedCompletely() {
        val manifest = UpdateManifestParser.parse(json(), maxApkBytes)
        assertEquals(1_300_000_101, manifest.versionCode)
        assertEquals("3.0.17-preview-safe.1", manifest.versionName)
        assertEquals("Ordia-3.0-safe-signed.apk", manifest.apkUrl.substringAfterLast('/'))
        assertEquals(validSha, manifest.sha256)
        assertEquals(35_000_000L, manifest.size)
        assertEquals("2026-08-15", manifest.releaseDate)
        assertEquals("Mejoras de estabilidad.", manifest.changelog)
        assertFalse(manifest.mandatory)
        assertEquals(1, manifest.minSupportedVersion)
        assertEquals("stable", manifest.channel)
    }

    @Test fun validManifest_withOptionalFieldsDefaults() {
        val manifest = UpdateManifestParser.parse(json(releaseDate = null, changelog = "", channel = ""), maxApkBytes)
        assertNull(manifest.releaseDate)
        assertEquals("", manifest.changelog)
        assertEquals("stable", manifest.channel)
    }

    @Test fun invalidJson_isRejected() {
        runCatching { UpdateManifestParser.parse("not json {", maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun missingOrInvalidVersionCode_isRejected() {
        runCatching { UpdateManifestParser.parse(json(versionCode = 0), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(versionCode = -5), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun blankOrHugeVersionName_isRejected() {
        runCatching { UpdateManifestParser.parse(json(versionName = "   "), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(versionName = "x".repeat(65)), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun invalidSha256_isRejected() {
        runCatching { UpdateManifestParser.parse(json(sha256 = "xyz"), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(sha256 = "a".repeat(63)), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun invalidSize_isRejected() {
        runCatching { UpdateManifestParser.parse(json(size = 0L), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(size = -1L), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(size = maxApkBytes + 1), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun blankApkUrl_orOversizedChangelog_orBadMinVersion_orHugeChannel_areRejected() {
        runCatching { UpdateManifestParser.parse(json(apkUrl = "  "), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(changelog = "x".repeat(2001)), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(minSupportedVersion = 0), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(json(channel = "c".repeat(25)), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun emptyOrHugeDocument_isRejected() {
        runCatching { UpdateManifestParser.parse("", maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
        runCatching { UpdateManifestParser.parse(" ".repeat(70_000), maxApkBytes) }
            .onSuccess { error("se esperaba rechazo") }
    }

    @Test fun parsedManifest_obeysStrictVersionComparison() {
        val installed = 1_300_000_100
        val newer = UpdateManifestParser.parse(json(versionCode = 1_300_000_101), maxApkBytes)
        val same = UpdateManifestParser.parse(json(versionCode = 1_300_000_100), maxApkBytes)
        val older = UpdateManifestParser.parse(json(versionCode = 1_300_000_099), maxApkBytes)
        assertTrue(UpdateSecurityRules.isNewerCode(newer.versionCode, installed))
        assertFalse(UpdateSecurityRules.isNewerCode(same.versionCode, installed))
        assertFalse(UpdateSecurityRules.isNewerCode(older.versionCode, installed))
    }

    @Test fun parsedManifest_supportsMandatoryChannels() {
        val forced = UpdateManifestParser.parse(json(mandatory = true), maxApkBytes)
        val minVersion = UpdateManifestParser.parse(json(mandatory = false, minSupportedVersion = 1_300_000_200), maxApkBytes)
        val installed = 1_300_000_100
        assertTrue(UpdateSecurityRules.isMandatoryUpdate(forced.mandatory, installed, forced.minSupportedVersion))
        assertTrue(UpdateSecurityRules.isMandatoryUpdate(minVersion.mandatory, installed, minVersion.minSupportedVersion))
        assertFalse(UpdateSecurityRules.isMandatoryUpdate(false, installed, 1))
    }
}

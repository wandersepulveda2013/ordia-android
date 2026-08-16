package com.ordia.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityRulesTest {
    @Test fun strictReleaseTag_requiresOrdia3Format() {
        assertEquals(1_000_000_101, UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-1000000101"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("release-code-1000000101"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v4.0.0-code-1000000101"))
    }

    /**
     * Contrato CI ↔ app: el tag que genera android-ci.yml DEBE ser aceptado por
     * parseVersionCodeFromTag y devolver EXACTAMENTE el versionCode embebido en
     * el APK y el manifiesto. Formato canónico: v3.0.<build>-code-<versionCode>.
     */
    @Test fun canonicalCiTag_isAcceptedAndReturnsItsVersionCode() {
        // Tag real producido por el CI (build 449 → versionCode 1300044901).
        assertEquals(1_300_044_901, UpdateSecurityRules.parseVersionCodeFromTag("v3.0.449-code-1300044901"))
        assertEquals(1_300_050_000, UpdateSecurityRules.parseVersionCodeFromTag("v3.0.240-code-1300050000"))
        // El versionCode del tag debe poder usarse para seleccionar la APK legacy.
        val vc = 1_300_044_901
        assertEquals("Ordia-3.0-code-$vc.apk", UpdateSecurityRules.expectedApkName(vc))
        assertEquals(
            "Ordia-3.0-code-$vc.apk",
            UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0-code-$vc.apk"), vc)
        )
    }

    /** El tag inválido que PUBLICÓ el CI antes de la corrección debe ser RECHAZADO. */
    @Test fun invalidBuildTag_isRejected() {
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.0-build.31948697776"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.0-build.42"))
    }

    @Test fun malformedTags_areRejected() {
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag(""))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-abc"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0-code-100"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-100-extra"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("prefix-v3.0.1-code-100"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-100/evil"))
    }

    @Test fun negativeZeroAndOverflowVersionCodes_areRejected() {
        // versionCode debe ser estrictamente positivo.
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-0"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code--5"))
        // Overflow de Int (2_147_483_648 > Int.MAX_VALUE) → toIntOrNull da null.
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-2147483648"))
    }

    @Test fun tagCaseAndUnicode_areRejectedUnlessCanonical() {
        // Sensible a mayúsculas: el patrón canónico es minúsculas.
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("V3.0.1-code-100"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-CODE-100"))
        // Espacios alrededor se toleran (trim), pero internos no.
        assertEquals(100, UpdateSecurityRules.parseVersionCodeFromTag("  v3.0.1-code-100  "))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1 -code-100"))
        // Caracteres confusables Unicode (cirílico 'а') no coinciden con 'a' ASCII.
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.1-code-100\u0430"))
    }

    /**
     * Test de contrato CI ↔ app: garantiza que una release publicada por el CI
     * (tag + versionCode + nombre de APK + nombre de manifiesto + URL) satisface
     * TODAS las reglas de seguridad de UpdateSecurityRules a la vez. Si este test
     * pasa, es imposible que el CI publique algo que Ordía rechace por formato.
     */
    @Test fun releaseContract_isFullyAcceptedBySecurityRules() {
        val flavor = "advanced"
        val versionCode = 1_300_050_000
        val buildNumber = 240
        val tag = "v3.0.$buildNumber-code-$versionCode"

        // 1) Tag aceptado y devuelve el versionCode exacto.
        val parsedCode = UpdateSecurityRules.parseVersionCodeFromTag(tag)
        assertEquals(versionCode, parsedCode)

        // 2) versionCode del tag > versionCode instalado (actualización real).
        assertTrue(UpdateSecurityRules.isNewerCode(parsedCode!!, 1_300_044_901))

        // 3) Nombre canónico de APK por flavor (flujo manifiesto, app nueva).
        val apkName = UpdateSecurityRules.expectedApkName(flavor)
        assertEquals("Ordia-3.0-$flavor-signed.apk", apkName)

        // 4) Nombre canónico de manifiesto.
        val manifestName = UpdateSecurityRules.expectedManifestName(flavor)
        assertEquals("update-manifest-$flavor.json", manifestName)

        // 5) URL de la APK (estable latest) es confiable y su filename coincide.
        val apkUrl = "https://github.com/wandersepulveda2013/ordia-android/releases/latest/download/$apkName"
        assertTrue(UpdateSecurityRules.isTrustedApkUrl(apkUrl, apkName))

        // 6) Asset legacy por versionCode (bootstrap de la app ya instalada).
        val legacyApk = UpdateSecurityRules.expectedApkName(versionCode)
        assertEquals("Ordia-3.0-code-$versionCode.apk", legacyApk)
        assertEquals(
            legacyApk,
            UpdateSecurityRules.selectExpectedApk(listOf(legacyApk), versionCode)
        )

        // 7) URL inmutable de la release también es confiable.
        val immutableUrl = "https://github.com/wandersepulveda2013/ordia-android/releases/download/$tag/$apkName"
        assertTrue(UpdateSecurityRules.isTrustedReleaseAssetUrl(immutableUrl, apkName))
    }

    @Test fun assetSelection_requiresCodeSpecificExactName() {
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0.apk"), 20001))
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0-code-20001.apk", "ordia-3.0-code-20001.APK"), 20001))
        assertEquals("Ordia-3.0-code-20001.apk", UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0-code-20001.apk"), 20001))
    }

    @Test fun checksum_requiresOneCanonicalLineAndExactName() {
        val hash = "a".repeat(64)
        assertEquals(hash, UpdateSecurityRules.parseChecksum("$hash  Ordia-3.0-code-20.apk", "Ordia-3.0-code-20.apk"))
        assertNull(UpdateSecurityRules.parseChecksum("$hash *Ordia-3.0-code-20.apk", "Ordia-3.0-code-20.apk"))
        assertNull(UpdateSecurityRules.parseChecksum("$hash  Ordia-3.0-code-20.apk\n", "other.apk"))
        assertNull(UpdateSecurityRules.parseChecksum("$hash  Ordia-3.0-code-20.apk\n$hash  Ordia-3.0-code-20.apk", "Ordia-3.0-code-20.apk"))
    }

    @Test fun urls_areBoundToOfficialRepository() {
        assertTrue(UpdateSecurityRules.isTrustedReleaseAssetUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/download/v3.0.1-code-20/Ordia-3.0-code-20.apk",
            "Ordia-3.0-code-20.apk"
        ))
        assertTrue(UpdateSecurityRules.isTrustedReleasePageUrl("https://github.com/wandersepulveda2013/ordia-android/releases"))
        assertFalse(UpdateSecurityRules.isTrustedReleaseAssetUrl("https://github.com/owner/repo/releases/download/v1/app.apk"))
        assertTrue(UpdateSecurityRules.isTrustedNetworkUrl("https://release-assets.githubusercontent.com/path/app.apk?sig=abc"))
        assertFalse(UpdateSecurityRules.isTrustedNetworkUrl("https://github.com.evil.example/app.apk"))
        assertFalse(UpdateSecurityRules.isTrustedNetworkUrl("https://user@github.com/app.apk"))
    }

    @Test fun manifestFeed_isBoundToOfficialRepositoryAndFlavor() {
        assertEquals("update-manifest-safe.json", UpdateSecurityRules.expectedManifestName("safe"))
        assertEquals("update-manifest-advanced.json", UpdateSecurityRules.expectedManifestName("advanced"))
        assertEquals("Ordia-3.0-safe-signed.apk", UpdateSecurityRules.expectedApkName("safe"))
        assertEquals("Ordia-3.0-full-signed.apk", UpdateSecurityRules.expectedApkName("full"))

        val stable = "https://github.com/wandersepulveda2013/ordia-android/releases/latest/download/update-manifest-safe.json"
        assertTrue(UpdateSecurityRules.isTrustedLatestDownloadUrl(stable))
        assertTrue(UpdateSecurityRules.isTrustedNetworkUrl(stable))
        assertFalse(UpdateSecurityRules.isTrustedLatestDownloadUrl("https://github.com/owner/repo/releases/latest/download/update-manifest-safe.json"))
        assertFalse(UpdateSecurityRules.isTrustedLatestDownloadUrl("$stable?x=1"))
        assertFalse(UpdateSecurityRules.isTrustedLatestDownloadUrl("https://github.com/wandersepulveda2013/ordia-android/releases/latest/download/../secret"))
        // La redirección a un asset concreto de la release sigue siendo confiable.
        assertTrue(UpdateSecurityRules.isTrustedReleaseAssetUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/download/v3.0.0-build.42/update-manifest-safe.json"
        ))
        // La APK firmada publicada por variante se acepta con su nombre exacto.
        assertTrue(UpdateSecurityRules.isTrustedReleaseAssetUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/download/v3.0.0-build.42/Ordia-3.0-safe-signed.apk",
            "Ordia-3.0-safe-signed.apk"
        ))
        assertFalse(UpdateSecurityRules.isTrustedReleaseAssetUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/download/v3.0.0-build.42/Ordia-3.0-safe-signed.apk",
            "Ordia-3.0-advanced-signed.apk"
        ))
        // La APK del manifiesto puede venir del enlace estable "latest" con su nombre exacto.
        assertTrue(UpdateSecurityRules.isTrustedApkUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/latest/download/Ordia-3.0-safe-signed.apk",
            "Ordia-3.0-safe-signed.apk"
        ))
        assertFalse(UpdateSecurityRules.isTrustedApkUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/latest/download/Ordia-3.0-safe-signed.apk",
            "Ordia-3.0-full-signed.apk"
        ))
    }

    @Test fun versionComparison_isStrictlyGreater() {
        assertTrue(UpdateSecurityRules.isNewerCode(101, 100))
        assertFalse(UpdateSecurityRules.isNewerCode(100, 100))
        assertFalse(UpdateSecurityRules.isNewerCode(99, 100))
    }

    @Test fun mandatoryUpdate_combinesFlagAndMinSupportedVersion() {
        assertTrue(UpdateSecurityRules.isMandatoryUpdate(mandatory = true, installedCode = 200, minSupportedVersion = 1))
        assertTrue(UpdateSecurityRules.isMandatoryUpdate(mandatory = false, installedCode = 50, minSupportedVersion = 100))
        assertFalse(UpdateSecurityRules.isMandatoryUpdate(mandatory = false, installedCode = 100, minSupportedVersion = 100))
        assertFalse(UpdateSecurityRules.isMandatoryUpdate(mandatory = false, installedCode = 101, minSupportedVersion = 100))
    }

    @Test fun unknownDownloadSize_isAllowedButZeroAndOversizeAreNot() {
        assertTrue(UpdateSecurityRules.isReportedSizeAcceptable(-1L, 100L))
        assertTrue(UpdateSecurityRules.isReportedSizeAcceptable(null, 100L))
        assertFalse(UpdateSecurityRules.isReportedSizeAcceptable(0L, 100L))
        assertFalse(UpdateSecurityRules.isReportedSizeAcceptable(101L, 100L))
    }
    @Test fun malformedUtf8AndCaseConfusableApksAreRejected() {
        assertNull(UpdateSecurityRules.decodeUtf8Strict(byteArrayOf(0xC3.toByte(), 0x28)))
        val code = 1_000_000_101
        val expected = UpdateSecurityRules.expectedApkName(code)
        assertEquals("Ordia-3.0-code-1000000101.apk", expected)
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf(expected.lowercase()), code))
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf(expected, expected.lowercase()), code))
        assertEquals(expected, UpdateSecurityRules.selectExpectedApk(listOf(expected), code))
    }

}

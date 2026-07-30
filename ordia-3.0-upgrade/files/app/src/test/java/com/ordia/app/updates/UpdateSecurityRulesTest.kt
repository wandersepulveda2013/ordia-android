package com.ordia.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityRulesTest {
    @Test fun strictReleaseTag_requiresOrdia2Format() {
        assertEquals(1_000_000_101, UpdateSecurityRules.parseVersionCodeFromTag("v2.0.1-code-1000000101"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("release-code-1000000101"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.0-code-1000000101"))
    }

    @Test fun assetSelection_requiresCodeSpecificExactName() {
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-2.0.apk"), 20001))
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-2.0-code-20001.apk", "ordia-2.0-code-20001.APK"), 20001))
        assertEquals("Ordia-2.0-code-20001.apk", UpdateSecurityRules.selectExpectedApk(listOf("Ordia-2.0-code-20001.apk"), 20001))
    }

    @Test fun checksum_requiresOneCanonicalLineAndExactName() {
        val hash = "a".repeat(64)
        assertEquals(hash, UpdateSecurityRules.parseChecksum("$hash  Ordia-2.0-code-20.apk", "Ordia-2.0-code-20.apk"))
        assertNull(UpdateSecurityRules.parseChecksum("$hash *Ordia-2.0-code-20.apk", "Ordia-2.0-code-20.apk"))
        assertNull(UpdateSecurityRules.parseChecksum("$hash  Ordia-2.0-code-20.apk\n", "other.apk"))
        assertNull(UpdateSecurityRules.parseChecksum("$hash  Ordia-2.0-code-20.apk\n$hash  Ordia-2.0-code-20.apk", "Ordia-2.0-code-20.apk"))
    }

    @Test fun urls_areBoundToOfficialRepository() {
        assertTrue(UpdateSecurityRules.isTrustedReleaseAssetUrl(
            "https://github.com/wandersepulveda2013/ordia-android/releases/download/v2.0.1-code-20/Ordia-2.0-code-20.apk",
            "Ordia-2.0-code-20.apk"
        ))
        assertTrue(UpdateSecurityRules.isTrustedReleasePageUrl("https://github.com/wandersepulveda2013/ordia-android/releases"))
        assertFalse(UpdateSecurityRules.isTrustedReleaseAssetUrl("https://github.com/owner/repo/releases/download/v1/app.apk"))
        assertTrue(UpdateSecurityRules.isTrustedNetworkUrl("https://release-assets.githubusercontent.com/path/app.apk?sig=abc"))
        assertFalse(UpdateSecurityRules.isTrustedNetworkUrl("https://github.com.evil.example/app.apk"))
        assertFalse(UpdateSecurityRules.isTrustedNetworkUrl("https://user@github.com/app.apk"))
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
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf(expected.lowercase()), code))
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf(expected, expected.lowercase()), code))
        assertEquals(expected, UpdateSecurityRules.selectExpectedApk(listOf(expected), code))
    }

}

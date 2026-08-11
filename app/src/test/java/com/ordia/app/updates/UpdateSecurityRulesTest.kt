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

    /**
     * Contract test: the names produced by .github/workflows/openhands-delivery.yml MUST be
     * accepted by UpdateSecurityRules. This prevents the regression where CI published
     * v3.0.0-build.N / Ordia-3.0-signed.apk and the updater silently rejected everything.
     *
     * Workflow formulas (openhands-delivery.yml):
     *   VERSION_CODE = 1_300_000_000 + (RUN * 100) + ATTEMPT   (matches build.gradle.kts)
     *   TAG = "v3.0.${RUN}-code-${VERSION_CODE}"
     *   APK = "Ordia-3.0-code-${VERSION_CODE}.apk"
     *   SHA = "${APK}.sha256"
     */
    @Test fun ciWorkflowNaming_isAcceptedByUpdater() {
        for (run in listOf(1, 42, 428)) {
            val attempt = 1
            val versionCode = 1_300_000_000 + (run * 100) + attempt
            val tag = "v3.0.$run-code-$versionCode"
            val apk = "Ordia-3.0-code-$versionCode.apk"
            val sha = "$apk.sha256"

            // Updater parses the versionCode from the tag and accepts it.
            assertEquals(versionCode, UpdateSecurityRules.parseVersionCodeFromTag(tag))
            // Updater selects exactly this APK name.
            assertEquals(apk, UpdateSecurityRules.expectedApkName(versionCode))
            assertEquals(apk, UpdateSecurityRules.selectExpectedApk(listOf(apk), versionCode))
            // Checksum asset name is the APK name + ".sha256".
            assertEquals("$apk.sha256", sha)
            // A canonical checksum line for this APK parses correctly.
            val hash = "b".repeat(64)
            assertEquals(hash, UpdateSecurityRules.parseChecksum("$hash  $apk", apk))
            // The release download URL with this tag + name is trusted.
            assertTrue(
                UpdateSecurityRules.isTrustedReleaseAssetUrl(
                    "https://github.com/wandersepulveda2013/ordia-android/releases/download/$tag/$apk",
                    apk
                )
            )
        }
    }

    @Test fun ciWorkflowNaming_rejectsOldBrokenFormats() {
        // The OLD android-ci format that the updater must STILL reject (regression guard).
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.0-build.5"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.0-signed"))
        assertNull(UpdateSecurityRules.parseVersionCodeFromTag("latest"))
        // Old asset names are not selected.
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0-signed.apk"), 1_300_000_101))
        assertNull(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0.apk"), 1_300_000_101))
    }

}

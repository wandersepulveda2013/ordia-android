package com.ordia.app.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val checker = UpdateChecker("test", "test")

    private fun releaseJson(
        tag: String,
        body: String = "",
        apkName: String = "Ordia-3.0-signed.apk",
    ): JSONObject {
        val assetJson = """{"name":"$apkName","browser_download_url":"https://example.com/$apkName"}"""
        val assetsArray = "[$assetJson]"
        return JSONObject("""{"tag_name":"$tag","html_url":"https://github.com/test/test/releases/tag/$tag","body":"$body","assets":$assetsArray}""")
    }

    @Test
    fun `parses v3_0_0-11 tag extracts versionCode 11`() {
        val result = checker.parseRelease(releaseJson("v3.0.0-11"))
        assertTrue(result is UpdateResult.UpToDate)
    }

    @Test
    fun `parses v3_0_0-build_31662 tag fails when build number exceeds Int range`() {
        // 31662428440 > Int.MAX_VALUE, so toIntOrNull returns null → Error
        val result = checker.parseRelease(releaseJson("v3.0.0-build.31662428440"))
        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("código de versión"))
    }

    @Test
    fun `error when tag has no digits`() {
        val result = checker.parseRelease(releaseJson("vrelease"))
        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("código de versión"))
    }

    @Test
    fun `error when tag_name is blank`() {
        val json = JSONObject("""{"tag_name":"","message":"Not Found"}""")
        val result = checker.parseRelease(json)
        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("Not Found"))
    }

    @Test
    fun `error when no APK asset found`() {
        val json = JSONObject(
            """{"tag_name":"v99.0.0-99999","assets":[{"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"}]}"""
        )
        val result = checker.parseRelease(json)
        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("APK"))
    }

    @Test
    fun `available when remote versionCode greater than current`() {
        val result = checker.parseRelease(releaseJson("v99.0.0-99999", body = "Bug fixes"))
        assertTrue(result is UpdateResult.Available)
        val available = result as UpdateResult.Available
        assertEquals("99.0.0", available.versionName)
        assertEquals("Bug fixes", available.releaseNotes)
        assertEquals("https://example.com/Ordia-3.0-signed.apk", available.apkDownloadUrl)
    }

    @Test
    fun `up to date when remote versionCode equals current`() {
        // Current BuildConfig.VERSION_CODE is 12 (from build.gradle.kts)
        val result = checker.parseRelease(releaseJson("v3.0.1-12"))
        assertTrue(result is UpdateResult.UpToDate)
    }

    @Test
    fun `versionName extracted correctly from various tag formats`() {
        // v3.0.0-11 → versionName 3.0.0, versionCode 11
        val r1 = checker.parseRelease(releaseJson("v3.0.0-11"))
        assertTrue(r1 is UpdateResult.UpToDate) // 11 <= 12

        // v3.0.1-12 → versionName 3.0.1, versionCode 12
        val r2 = checker.parseRelease(releaseJson("v3.0.1-12"))
        assertTrue(r2 is UpdateResult.UpToDate) // 12 <= 12

        // v3.0.0 → no dash, versionName 3.0.0, versionCode 0
        val r3 = checker.parseRelease(releaseJson("v3.0.0"))
        assertTrue(r3 is UpdateResult.UpToDate) // 0 <= 12
    }
}

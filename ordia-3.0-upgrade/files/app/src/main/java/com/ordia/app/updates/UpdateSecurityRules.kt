package com.ordia.app.updates

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Pure, unit-testable allow-list rules used before any update network or install operation. */
object UpdateSecurityRules {
    private const val OWNER = "wandersepulveda2013"
    private const val REPOSITORY = "ordia-android"
    private val sha256Pattern = Regex("(?i)^[0-9a-f]{64}$")
    private val checksumLinePattern = Regex("^([0-9a-fA-F]{64})  ([^/\\\\]+)$")
    private val releaseTagPattern = Regex("^v3\\.0\\.\\d+-code-(\\d+)$")
    private val redirectHosts = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun isValidSha256(value: String): Boolean = sha256Pattern.matches(value)

    fun parseVersionCodeFromTag(tag: String): Int? =
        releaseTagPattern.matchEntire(tag.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }

    fun expectedApkName(versionCode: Int): String = "Ordia-3.0-code-$versionCode.apk"

    /** Requires one exact canonical filename and rejects case-confusable duplicates. */
    fun selectExpectedApk(assetNames: Collection<String>, versionCode: Int): String? {
        val expected = expectedApkName(versionCode)
        val confusable = assetNames.filter { it.equals(expected, ignoreCase = true) }
        return expected.takeIf { confusable.size == 1 && confusable.single() == expected }
    }

    fun decodeUtf8Strict(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    /** Exactly one nonblank line, canonical two-space format, and the exact APK filename. */
    fun parseChecksum(text: String, expectedFileName: String): String? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size != 1) return null
        val match = checksumLinePattern.matchEntire(lines.single()) ?: return null
        if (match.groupValues[2] != expectedFileName) return null
        return match.groupValues[1].lowercase().takeIf(::isValidSha256)
    }

    fun isReportedSizeAcceptable(bytes: Long?, maxBytes: Long): Boolean = when {
        bytes == null || bytes < 0L -> true
        bytes == 0L -> false
        else -> bytes <= maxBytes
    }

    fun isTrustedReleasePageUrl(value: String): Boolean {
        val uri = secureUri(value) ?: return false
        if (uri.host?.lowercase() !in setOf("github.com", "www.github.com")) return false
        val base = "/$OWNER/$REPOSITORY/releases"
        return uri.path == base || uri.path.startsWith("$base/tag/")
    }

    fun isTrustedReleaseAssetUrl(value: String, expectedFileName: String? = null): Boolean {
        val uri = secureUri(value) ?: return false
        if (uri.host?.lowercase() !in setOf("github.com", "www.github.com")) return false
        val prefix = "/$OWNER/$REPOSITORY/releases/download/"
        if (!uri.path.startsWith(prefix)) return false
        return expectedFileName == null || uri.path.substringAfterLast('/') == expectedFileName
    }

    fun isTrustedNetworkUrl(value: String): Boolean {
        val uri = secureUri(value, allowQuery = true) ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host in redirectHosts) return true // GitHub uses signed query parameters on its asset CDN.
        if (uri.query != null) return false
        if (host == "api.github.com") return uri.path == "/repos/$OWNER/$REPOSITORY/releases/latest"
        return isTrustedReleaseAssetUrl(value)
    }

    private fun secureUri(value: String, allowQuery: Boolean = false): URI? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return null
        if (uri.port !in setOf(-1, 443) || uri.fragment != null || (!allowQuery && uri.query != null)) return null
        if (!uri.path.orEmpty().startsWith('/')) return null
        return uri
    }
}

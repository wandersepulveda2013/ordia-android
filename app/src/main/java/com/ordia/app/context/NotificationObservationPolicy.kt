package com.ordia.app.context

import com.ordia.app.conversations.ConversationPrivacyPolicy
import java.security.MessageDigest

enum class ObservationRejection {
    DISABLED,
    PAUSED,
    SOURCE_NOT_AUTHORIZED,
    SYSTEM_NOTIFICATION,
    SENSITIVE_PACKAGE,
    SENSITIVE_CONTENT,
    EMPTY,
    DUPLICATE
}

data class NotificationObservationDecision(
    val accepted: Boolean,
    val normalizedText: String = "",
    val fingerprint: String = "",
    val rejection: ObservationRejection? = null
)

/** Decisión pura y auditable previa a cualquier procesamiento o persistencia. */
object NotificationObservationPolicy {
    private val sensitivePackageTokens = setOf(
        "bank", "banco", "wallet", "finance", "authenticator", "password", "vault",
        "health", "medical", "clinic", "hospital", "insurance"
    )

    fun evaluate(
        globalEnabled: Boolean,
        notificationAccessEnabled: Boolean,
        pausedUntil: Long,
        sourceEnabled: Boolean,
        packageName: String,
        notificationKey: String,
        title: String,
        text: String,
        isOngoing: Boolean,
        isGroupSummary: Boolean,
        alreadySeen: Boolean,
        now: Long = System.currentTimeMillis()
    ): NotificationObservationDecision {
        fun reject(reason: ObservationRejection) = NotificationObservationDecision(false, rejection = reason)
        if (!globalEnabled || !notificationAccessEnabled) return reject(ObservationRejection.DISABLED)
        if (pausedUntil > now) return reject(ObservationRejection.PAUSED)
        if (!sourceEnabled) return reject(ObservationRejection.SOURCE_NOT_AUTHORIZED)
        if (isOngoing || isGroupSummary) return reject(ObservationRejection.SYSTEM_NOTIFICATION)
        val lowerPackage = packageName.lowercase()
        if (sensitivePackageTokens.any(lowerPackage::contains)) return reject(ObservationRejection.SENSITIVE_PACKAGE)
        val normalized = text.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT_LENGTH)
        if (normalized.length < MIN_TEXT_LENGTH) return reject(ObservationRejection.EMPTY)
        if (ConversationPrivacyPolicy.containsSensitiveContent("$title $normalized")) {
            return reject(ObservationRejection.SENSITIVE_CONTENT)
        }
        if (alreadySeen) return reject(ObservationRejection.DUPLICATE)
        val fingerprint = sha256("$packageName|${notificationKey.take(500)}|$normalized")
        return NotificationObservationDecision(true, normalized, fingerprint)
    }

    fun fingerprint(packageName: String, notificationKey: String, text: String): String =
        sha256("$packageName|${notificationKey.take(500)}|${text.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT_LENGTH)}")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    const val MAX_TEXT_LENGTH = 4_000
    private const val MIN_TEXT_LENGTH = 4
}

package com.ordia.app.conversations

import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.domain.NaturalTaskParser
import java.security.MessageDigest
import java.util.Locale

data class CommitmentDraft(
    val kind: CommitmentKind,
    val owner: CommitmentOwner,
    val actor: String,
    val action: String,
    val location: String,
    val dueAt: Long?,
    val confidence: Float,
    val suggestedReminderAt: Long?,
    val fingerprint: String
)

/** Filtro determinista previo: el contenido bloqueado no llega al extractor. */
object ConversationPrivacyPolicy {
    private val sensitivePatterns = listOf(
        Regex("""(?i)\b(?:contrase(?:ña|na)|password|passwd|pin|cvv|cvc|token\s+bancario)\b"""),
        Regex("""(?i)\b(?:c[oó]digo|clave)\s+(?:de\s+)?(?:verificaci[oó]n|seguridad|acceso)\b"""),
        Regex("""(?i)\b(?:otp|2fa|autenticaci[oó]n\s+de\s+dos\s+pasos)\b"""),
        Regex("""\b(?:\d[ -]?){13,19}\b""")
    )
    private val otpCode = Regex("""(?i)\b(?:c[oó]digo|otp|verificaci[oó]n)\D{0,20}\d{4,8}\b""")

    fun containsSensitiveContent(text: String): Boolean =
        sensitivePatterns.any { it.containsMatchIn(text) } || otpCode.containsMatchIn(text)
}

/** Extrae compromisos localmente sin guardar ni ejecutar acciones. */
object CommitmentEngine {
    private val requestSignal = Regex(
        """(?i)\b(?:env[ií]ame|m[aá]ndame|no\s+olvides|recuerda|recu[eé]rdame|por\s+favor|puedes|podr[ií]as|necesito\s+que)\b"""
    )
    private val meetingSignal = Regex(
        """(?i)\b(?:nos\s+vemos|reuni[oó]n|cita|quedamos|encuentro|ser[aá]\s+a\s+las)\b"""
    )
    private val purchaseSignal = Regex("""(?i)\b(?:comprar|compra|traer|conseguir|mercado|supermercado)\b""")
    private val reminderSignal = Regex("""(?i)\b(?:recu[eé]rdame|recordatorio|av[ií]same|no\s+dejes\s+que\s+olvide)\b""")
    private val commitmentSignal = Regex(
        """(?i)\b(?:yo\s+me\s+encargo|me\s+comprometo|te\s+llamo|te\s+env[ií]o|te\s+respondo|despu[eé]s\s+te\s+respondo|voy\s+a|debo|tengo\s+que|terminar[eé]|har[eé]|lo\s+hago)\b"""
    )
    private val locationSignal = Regex(
        """(?i)\b(?:lugar\s*:\s*|(?:nos\s+vemos|reuni[oó]n|cita)[^.!?\n]{0,80}?\ben\s+)([\p{L}\d][\p{L}\d .,'-]{2,50})"""
    )

    fun extract(
        messages: List<ChatMessage>,
        selfParticipant: String? = null,
        scopeHash: String
    ): List<CommitmentDraft> {
        val self = selfParticipant?.trim()?.lowercase(Locale.ROOT)
        return messages.asSequence()
            .take(ChatImportParser.MAX_MESSAGES)
            .filterNot { ConversationPrivacyPolicy.containsSensitiveContent(it.text) }
            .mapNotNull { message -> detect(message, self, scopeHash) }
            .distinctBy { it.fingerprint }
            .take(MAX_COMMITMENTS)
            .toList()
    }

    private fun detect(message: ChatMessage, self: String?, scopeHash: String): CommitmentDraft? {
        val text = message.text.trim().replace(Regex("\\s+"), " ").take(MAX_ACTION_CHARS)
        if (text.length < 4) return null
        val isRequest = requestSignal.containsMatchIn(text)
        val isMeeting = meetingSignal.containsMatchIn(text)
        val isPurchase = purchaseSignal.containsMatchIn(text)
        val isReminder = reminderSignal.containsMatchIn(text)
        val isCommitment = commitmentSignal.containsMatchIn(text)
        if (!isRequest && !isMeeting && !isPurchase && !isReminder && !isCommitment) return null

        val sender = message.sender.orEmpty().trim().take(80)
        val owner = when {
            isRequest -> CommitmentOwner.SELF
            sender.isNotBlank() && self != null && sender.lowercase(Locale.ROOT) == self -> CommitmentOwner.SELF
            sender.isNotBlank() && self != null -> CommitmentOwner.OTHER
            sender.isNotBlank() -> CommitmentOwner.UNKNOWN
            isCommitment -> CommitmentOwner.SELF
            else -> CommitmentOwner.UNKNOWN
        }
        val kind = when {
            isRequest -> CommitmentKind.REQUEST
            isMeeting -> CommitmentKind.MEETING
            isPurchase -> CommitmentKind.PURCHASE
            isReminder -> CommitmentKind.REMINDER
            owner == CommitmentOwner.OTHER -> CommitmentKind.OTHER_COMMITMENT
            else -> CommitmentKind.SELF_COMMITMENT
        }
        val parsed = NaturalTaskParser.parse(text)
        val dueAt = parsed.dueAt
        val confidence = (
            0.67f +
                (if (isCommitment || isRequest) 0.12f else 0f) +
                (if (dueAt != null) 0.11f else 0f) +
                (if (sender.isNotBlank()) 0.05f else 0f)
            ).coerceAtMost(0.97f)
        val reminderAt = dueAt?.minus(DEFAULT_REMINDER_OFFSET_MS)?.takeIf { it > System.currentTimeMillis() }
        val location = locationSignal.find(text)?.groupValues?.getOrNull(1)
            ?.trim()?.trimEnd('.', ',', ';')?.take(80).orEmpty()
        val fingerprint = sha256(
            listOf(scopeHash.take(24), kind.name, owner.name, text.lowercase(Locale.ROOT), dueAt ?: 0L).joinToString("|")
        )
        return CommitmentDraft(
            kind = kind,
            owner = owner,
            actor = sender,
            action = text,
            location = location,
            dueAt = dueAt,
            confidence = confidence,
            suggestedReminderAt = reminderAt,
            fingerprint = fingerprint
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private const val MAX_COMMITMENTS = 500
    private const val MAX_ACTION_CHARS = 500
    private const val DEFAULT_REMINDER_OFFSET_MS = 30 * 60_000L
}

object ConversationSummaryEngine {
    fun summarize(preview: ConversationPreview, commitments: List<CommitmentDraft>): String {
        val people = preview.participants.take(4).joinToString(", ")
        val participantText = if (people.isBlank()) "sin participantes identificados" else "entre $people"
        val base = "${preview.messages.size} mensajes $participantText."
        if (commitments.isEmpty()) return "$base No se detectaron compromisos claros."
        val dated = commitments.count { it.dueAt != null }
        val requests = commitments.count { it.kind == CommitmentKind.REQUEST }
        return buildString {
            append(base)
            append(" Se detectaron ${commitments.size} compromisos")
            if (dated > 0) append(", $dated con fecha")
            if (requests > 0) append(" y $requests solicitudes")
            append(". Revisa cada propuesta antes de convertirla en tarea.")
        }
    }
}

package com.ordia.app.conversations

import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.ReminderRules
import com.ordia.app.domain.SensitiveSecretPatterns
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
    // Cobertura alineada con ContextPrivacyFilter para las categorías que pueden
    // llegar en una notificación de SMS/mensajería (paquete NO bancario, que pasa el
    // filtro de paquete de NotificationObservationPolicy). Sin esto, un SMS con
    // "tu saldo disponible", "estado de cuenta" o una "frase semilla" escapaba al
    // gate de notificaciones y se persistía. (c.286)
    //
    // c.290: también claves privadas cripto (hex 64 con/sin prefijo 0x) y bloques
    // PEM -----BEGIN ... PRIVATE KEY-----. ContextPrivacyFilter (gate de contexto/IME)
    // ya las bloqueaba, pero este gate (el que decide si una notificación se persiste
    // en la BD de conversaciones) NO → una clave privada recibida por SMS quedaba en
    // texto plano. Misma clase de fuga que c.287 cerró para seed phrases.
    //
    // c.292: credenciales cortas (PIN/NIP/contraseña) que el gate de lectura bloquea
    // pero este NO. Un SMS "tu clave temporal es 4821" o "tu pwd de acceso es ab12cd"
    // pasaba el gate de persistencia: "clave temporal" no es "clave de
    // acceso/seguridad/verificación" (patrón 2) y el otpCode sólo miraba
    // código/otp/verificación. La pareja "clave" + secreto numérico cercano, "pwd" en
    // peludo y "nip" (PIN en español de México, sinónimo exacto de "pin" que ya
    // bloqueábamos) se guardaban en texto plano en la BD de conversaciones. Cierre
    // simétrico con ContextPrivacyFilter, en la dirección que protege la persistencia.
    //
    // c.293: IBAN alfanumérico PELADO (sin la palabra "iban") y "two factor"/
    // "two-factor" en inglés. El gate de lectura bloquea un IBAN estructural
    // (2 mayúsculas + 2 dígitos + 11-30 alfanuméricos) aunque no diga "iban", y
    // bloquea "two factor" aunque no diga "2fa"; este gate sólo miraba la palabra
    // "iban" y "2fa" → "Transfiere a GB82WEST1234..." se persistía en texto plano.
    private val sensitivePatterns = listOf(
        Regex("""(?i)\b(?:contrase(?:ña|na)|password|passwd|pwd|pin|nip|cvv|cvc|token\s+bancario)\b"""),
        Regex("""(?i)\b(?:c[oó]digo|clave)\s+(?:de\s+)?(?:verificaci[oó]n|seguridad|acceso)\b"""),
        Regex("""(?i)\b(?:otp|2fa|two.?factor|autenticaci[oó]n\s+de\s+dos\s+pasos)\b"""),
        Regex("""(?i)\b(?:n[uú]mero\s+de\s+cuenta|account\s+number|clabe|iban|swift|c[eé]dula)\b"""),
        Regex("""(?i)\b(?:seed\s+phrase|recovery\s+phrase|frase\s+semilla|frase\s+de\s+recuperaci[oó]n|palabras\s+de\s+recuperaci[oó]n|mnemonic)\b"""),
        Regex("""(?i)\b(?:transferencia|dep[oó]sito|retiro|saldo|estado\s+de\s+cuenta)\b"""),
        // c.299: credenciales/secretos de infraestructura y nube (claves PEM,
        // hex 64, IBAN estructural, SSH, API keys, AWS, JWT, Google, Slack,
        // GitHub/GitLab PATs, cadenas de conexion) movidos a la fuente unica
        // `domain.SensitiveSecretPatterns` (compartida con ContextPrivacyFilter)
        // para que persistencia y lectura no puedan desincronizarse (causa raiz
        // de las 7 fugas c.287-c.298). Se consumen en `containsSensitiveContent`.
    )
    // "clave temporal/bancaria 4821" no entra en el patrón 2 (no es "de acceso/") pero
    // sí es un PIN: lo capturamos como otpCode. Añadir "clave" en peludo al patrón 1
    // bloquearía "la clave del éxito" (falso positivo → pérdida de chat legítimo), así
    // que aquí sólo la casamos cuando un secreto numérico corto la acompaña. Igual que
    // con código/otp/verificación: la palabra + hasta 20 no-dígitos + 4-8 dígitos.
    private val otpCode = Regex("""(?i)\b(?:c[oó]digo|otp|verificaci[oó]n|clave)\D{0,20}\d{4,8}\b""")

    fun containsSensitiveContent(text: String): Boolean =
        sensitivePatterns.any { it.containsMatchIn(text) } ||
            SensitiveSecretPatterns.patterns.any { it.containsMatchIn(text) } ||
            SensitiveSecretPatterns.containsNumericSensitive(text) ||
            otpCode.containsMatchIn(text)
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
        // "me encargo"/"me ocupo" son las formas más naturales en español de
        // asumir un compromiso y se dicen SIN pronombre "yo" ("¿Quién llama?"
        // → "me encargo"). Exigir "yo me encargo" dejaba estos compromisos sin
        // detectar (falso negativo: olvido). El pronombre es opcional. (c.278)
        """(?i)\b(?:(?:yo\s+)?me\s+(?:encargo|ocupo)|me\s+comprometo|te\s+llamo|te\s+env[ií]o|te\s+respondo|despu[eé]s\s+te\s+respondo|voy\s+a|debo|tengo\s+que|terminar[eé]|har[eé]|lo\s+hago)\b"""
    )
    private val locationSignal = Regex(
        """(?i)\b(?:lugar\s*:\s*|(?:nos\s+vemos|reuni[oó]n|cita)[^.!?\n]{0,80}?\ben\s+)([\p{L}\d][\p{L}\d .,'-]{2,50})"""
    )
    // "no te llamo"/"no me encargo"/"no lo hago" son NEGATIVAS (rechazos), no
    // compromisos. Hay compromiso solo si alguna frase de compromiso aparece SIN
    // "no " inmediatamente antes. Así "no tengo tiempo, lo hago manana" sigue
    // siendo compromiso (la 2ª frase no está negada). NO se aplica a request/reminder,
    // donde la negación es idiomática y POSITIVA ("no olvides" = recuérdame). (c.279)
    private val precedingNegation = Regex("""(?i)\bno\s+""")

    private fun hasUnnegatedCommitment(text: String): Boolean =
        commitmentSignal.findAll(text).any { m ->
            val start = m.range.first
            val prefix = text.substring(maxOf(0, start - 3), start)
            !precedingNegation.containsMatchIn(prefix)
        }

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
        // "no te llamo"/"no me encargo"/"no lo hago" son NEGATIVAS (rechazos), no
        // compromisos: excluir las frases de compromiso directamente negadas. Ojo:
        // NO se aplica a request/reminder, donde la negacion es idiomatica y POSITIVA
        // ("no olvides" = recuérdame, "no dejes que olvide" = recuérdame). (c.279)
        val isCommitment = hasUnnegatedCommitment(text)
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
        val now = System.currentTimeMillis()
        val reminderAt = dueAt?.let { ReminderRules.defaultReminderAt(it, now) }
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

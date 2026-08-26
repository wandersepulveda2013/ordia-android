import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.1187 (persistida): lateral ABIERTA de MI cierre c.1182
 * («responder el mail» FIXED) — hermana del piso «contestar…» c.873/
 * c.1177: «contestar el mail de <persona>» (el mail sin contestar,
 * mismo olvido cotidiano que «responder el mail» pero con el verbo
 * más usado para mensajería: «contestar»).
 *
 * Misma metodología que las sondas persistidas anteriores: PRE medido
 * sobre HEAD 27e300b (mi c.1182 integrada; «mail» YA es keyword
 * ERRAND, así la frase LLEGA al análisis pero el piso «contestar…»
 * exige objeto whatsapp|llamada|mensaje — NULL esperado por asimetría
 * de objeto). Controles: negación, pasado, estado recibido, sustantivo
 * aislado. Regresiones: piso contestar c.1177 (whatsapp), piso
 * responder c.1182 (mail), piso contestar c.873 (correo).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }

    // --- CANDIDATAS (contestar + mail) ---
    show("C1", "contestar el mail de Marta esta noche")
    show("C2", "contestar el mail de trabajo mañana")
    show("C3", "contestar los mails del cliente")
    show("C4", "vale, contestar el mail del jefe")
    show("C5", "esta noche contestar el mail")

    // --- CONTROLES (deben seguir NULL) ---
    show("C6", "no contestar el mail del banco")
    show("C7", "contesté el mail de Marta ayer")
    show("C8", "me contestó el mail ayer")
    show("C9", "el mail del cliente")

    // --- REGRESIONES (deben seguir HIT) ---
    show("C10", "contestar el WhatsApp de Marta")
    show("C11", "responder el mail de trabajo esta noche")
    show("C12", "contestar el correo del cole esta tarde")
}

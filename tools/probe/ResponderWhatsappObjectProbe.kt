import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.1192 (persistida): hermana ABIERTA del piso «responder el
 * correo» c.860/c.867/c.869/c.1182 (documentada en KDoc de ContextIntentEngine
 * línea ~2890 y ~2896): objeto «whatsapp» del piso responder. La forma
 * hablada dominante es «responder el WhatsApp (de <persona>) (mañana)»
 * — medida NULL por asimetría de objeto: el piso responder exige
 * objeto correos?|emails?|mails?|mensajes? y la keyword-OBJETO
 * «whatsapp» YA existe desde c.1177 (la frase LLEGA al análisis).
 *
 * Misma metodología que las sondas persistidas anteriores: PRE medido
 * sobre HEAD ab4e253 (mi c.1187 integrada). Controles NULL correctos:
 * negación, pasado, 3ª persona recibida, sustantivo aislado, rama
 * bivalente «responder a la pregunta». Regresiones: pisos responder
 * c.860/c.867/c.1182 (correo/email/mail/mensaje) y contestar c.1177/
 * c.1187 (whatsapp/mail).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        println("$label: " + if (i == null) "NULL" else "${i.kind} '${i.title}' dueAt=${i.dueAt != null}")
    }
    // Candidatas (PRE: NULL esperadas todas)
    show("C1", "responder el whatsapp de Marta mañana")
    show("C2", "responder el whatsapp del grupo esta tarde")
    show("C3", "responder los whatsapps pendientes")
    show("C4", "vale, responder el whatsapp del jefe")
    show("C5", "esta noche responder el whatsapp")
    // Controles (NULL correctos siempre)
    show("G1", "no responder el whatsapp del banco")
    show("G2", "respondí el whatsapp ayer")
    show("G3", "me respondió el whatsapp ayer")
    show("G4", "el whatsapp del cliente")
    show("G5", "responder a la pregunta del examen")
    // Regresiones (HIT esperadas)
    show("R1", "responder el correo del trabajo mañana")
    show("R2", "responder el mail de trabajo esta noche")
    show("R3", "contestar el WhatsApp de Marta")
    show("R4", "contestar el mail de Marta esta noche")
}

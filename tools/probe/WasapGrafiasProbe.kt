import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1197 (DISJUNTO del marcador del hermano c.1194 —
 * parser/sanitización temporal): hermana ABIERTA registrada en c.1192 —
 * grafías coloquiales «wasap/wassap(s)» del piso responder/contestar
 * (whatsapp cubierto c.1192). PRE medido NULL 4/4 en sonda efímera
 * (/tmp/WasapGrafiasProbe.kt); POST 4/4 HIT tras lockstep keyword +
 * pisos + plantillas (sonda re-ejecutada sobre el HEAD del fix).
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

    // CANDIDATAS (grafías coloquiales wasap/wassap — esperado NULL)
    show("C1", "responder el wasap de Marta mañana")
    show("C2", "responder los wassaps del grupo esta noche")
    show("C3", "contestar el wasap de Ana ahora")
    show("C4", "contestar los wassaps del trabajo")

    // GUARDS (deben quedar NULL)
    show("G1", "no voy a responder el wasap")
    show("G2", "respondí el wasap ayer")
    show("G3", "quizá responda el wasap")
    show("G4", "responder el wasap")     // enunciado nominal negativo? (CONTEXT: ¿guard?)

    // REGRESIONES heredadas (deben seguir HIT)
    show("R1", "responder el whatsapp de Marta")     // c.1192
    show("R2", "contestar el correo de Marta")       // c.873
    show("R3", "responder el mail de Marta")         // c.1187
}

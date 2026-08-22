import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.869 (PERSISTENTE, evidencia complementaria del fix eb340db — dos runs
 * paralelos implementaron el mismo lateral; ésta es la sonda del run cuyo duplicado se
 * descartó por colisión, verificada GREEN sobre el fix del hermano): piso «responder el
 * mensaje» — lateral medida NULL
 * en la sonda c.860 (OCTAVA clase explotada; las formas sinónimas del objeto se
 * registran como candidatas propias, una por ciclo). RED (c.869, medida PRE sobre
 * HEAD 3503ac3 (PRE) / 7341168 (POST, fix del hermano) con esta misma sonda efímera `/tmp/probe869/PreProbe.kt`): 7/7
 * candidatas NULL, 6/6 controles NULL, 5/5 regresiones HIT (la envolvente
 * «recuérdame responder el mensaje…» ya ruteaba TASK 0.45 por el candado c.613).
 * GREEN esperado/verificado: las 7 candidatas capturan TASK con título
 * «Responder el mensaje…» (grafía preservada, doctrina c.653); los 6 controles
 * siguen NULL (negación/duda/pasado/verbo aislado/bivalentes examen); las 5
 * regresiones intactas.
 */
fun main() {
    val cases = listOf(
        // candidatas (lateral c.860 registrada: "responder el mensaje de Juan mañana")
        "responder el mensaje de Juan mañana",
        "responder el mensaje de Ana hoy",
        "responder los mensajes esta tarde",
        "responder mi mensaje hoy",
        "vale, responder el mensaje de Juan mañana",
        "mañana responder el mensaje",
        "responder el mensaje",
        // controles (NO deben capturar)
        "no responder el mensaje hoy",
        "quizá responder el mensaje mañana",
        "respondí el mensaje ayer",
        "responder",
        "responder a la pregunta del examen",
        "responder en el examen",
        // regresiones (ya capturan)
        "responder el correo de Ana hoy",
        "responder el email de Ana hoy",
        "contestar a Juan esta tarde",
        "llamar a mamá mañana",
        "recuérdame responder el mensaje de Juan mañana"
    )
    for (c in cases) {
        val i = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, c, 1000))
        if (i == null) println("NULL  | $c")
        else println("HIT ${i.kind} dueAt=${i.dueAt != null} | «${i.title}» | $c")
    }
}

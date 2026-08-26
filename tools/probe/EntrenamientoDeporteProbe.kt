import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1235 (lateral (f) «entrenamiento de (fútbol|deporte)»
 * de la auditoría c.1227 cl.XXX deporte). PRE medido con este mismo archivo;
 * gate (NULL/FALSO GAP/ok) documentado en BACKLOG/RUN_LOG. Determinista.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} <- $t")
    }
    // T1–T4: capturas nominales «entrenamiento de <objeto>» (un entrenamiento
    // recreativo tiene frecuencia — se plan y no se olvidan)
    show("T1", "entrenamiento de fútbol el domingo")
    show("T2", "entrenamiento de fútbol por la tarde")
    show("T3", "el entrenamiento de fútbol el domingo")
    show("T4", "el entrenamiento de deporte esta tarde")
    // Guards NULL pineados (pretérito/negación)
    show("G1", "el entrenamiento de fútbol fue ayer")
    show("G2", "no ir al entrenamiento el domingo")
    show("G3", "no entrenamiento de fútbol el domingo")
    // Regresiones HIT intactas (entrenar, hacer ejercicio)
    show("R1", "entrenar a las 6")
    show("R2", "hacer ejercicio por la mañana")
}

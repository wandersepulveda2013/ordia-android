import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.904 (persistida): lateral NOVENA-b «dar gracias a <persona> (por
 * <objeto>)» SIN artículo «las», registrada a medir en c.901 (hermana de la
 * enclítica resuelta en c.903). Decisión de bivalencia MEDIDA: la forma sin
 * artículo es la habitual de las expresiones figuradas/religiosas («dar
 * gracias a Dios», «dar gracias a la vida») — la candidata solo merece
 * captura si los figurados quedan guardados (doctrina conservadora).
 * NO es un test; su salida PRE documenta el NULL medido y POST el HIT tras
 * el lockstep, convención c.857.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label  $s")
    }
    // Candidatas (gratitud interpersonal pendiente, sin artículo; PRE: NULL)
    show("CAND-A", "dar gracias a Ana por el regalo")
    show("CAND-B", "dar gracias a Marta mañana")
    show("CAND-C", "mañana dar gracias a Irene")
    show("CAND-D", "vale, dar gracias a los vecinos hoy")
    // Lateral a medir (enclítico SIN artículo; NO de este ciclo)
    show("LAT-1", "darle gracias a Ana por el regalo")
    // Guards no-imperativas / negación (esperado: NULL correctos)
    show("GUARD-1", "no dar gracias a Ana")
    show("GUARD-2", "quizá dé gracias a Ana")
    show("GUARD-3", "di gracias a Ana ayer")
    show("GUARD-4", "dar gracias")
    // Guards figuradas (bivalencia de la forma sin artículo; esperado: NULL)
    show("GUARD-5", "dar gracias a Dios")
    show("GUARD-6", "dar gracias a la vida por todo")
    // Regresiones (esperado: HIT)
    show("REG-1", "dar las gracias a Ana por el regalo")
    show("REG-2", "darle las gracias a Ana por el regalo")
    show("REG-3", "dar las gracias al jefe por el ascenso hoy")
    show("REG-4", "recuérdame dar gracias a Ana mañana")
    show("REG-5", "avisar a mamá de la cita mañana")
    show("REG-6", "llamar a Ana mañana")
}

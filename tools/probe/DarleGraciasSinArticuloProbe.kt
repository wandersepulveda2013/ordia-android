import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.905 (persistida): lateral NOVENA-b FINAL «darle gracias a
 * <persona> (por <objeto>)» — enclítico SIN artículo «las», registrada
 * a medir en c.901 y medida NULL en las sondas hermanas c.903 (LAT) y
 * c.904 (LAT-1). ÚLTIMA lateral de la familia «dar (las) gracias»
 * (c.901 articulada, c.902 «al», c.903 enclítica articulada, c.904 sin
 * artículo). Misma bivalencia MEDIDA de c.904: la forma sin artículo
 * (con o sin enclítico) es la habitual de las figuradas/religiosas
 * («darle gracias a Dios», «darle gracias a la vida») — el guard
 * anti-figurado de la rama 2 debe seguir protegiéndolas.
 * NO es un test; su salida PRE documenta el NULL medido y POST el HIT
 * tras el lockstep, convención c.857.
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
    // Candidatas (gratitud interpersonal pendiente, enclítico sin artículo; PRE: NULL)
    show("CAND-A", "darle gracias a Ana por el regalo")
    show("CAND-B", "darle gracias a Marta mañana")
    show("CAND-C", "mañana darle gracias a Irene")
    show("CAND-D", "vale, darle gracias a los vecinos hoy")
    show("CAND-E", "darle gracias al jefe por el ascenso")
    // Guards no-imperativas / negación (esperado: NULL correctos)
    show("GUARD-1", "no darle gracias a Ana")
    show("GUARD-2", "quizá déle gracias a Ana")
    show("GUARD-3", "le di gracias a Ana ayer")
    show("GUARD-4", "darle gracias")
    // Guards figuradas (bivalencia de la forma sin artículo; esperado: NULL)
    show("GUARD-5", "darle gracias a Dios")
    show("GUARD-6", "darle gracias a la vida por todo")
    show("GUARD-7", "darle gracias al cielo")
    // Regresiones (esperado: HIT)
    show("REG-1", "dar las gracias a Ana por el regalo")
    show("REG-2", "darle las gracias a Ana por el regalo")
    show("REG-3", "dar gracias a Ana por el regalo")
    show("REG-4", "dar las gracias al jefe por el ascenso hoy")
    show("REG-5", "recuérdame darle gracias a Ana mañana")
    show("REG-6", "avisar a mamá de la cita mañana")
}

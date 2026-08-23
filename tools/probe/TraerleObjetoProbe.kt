import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.907 (persistida): lateral enclítica del piso c.900 — «traerle
 * <objeto> a <persona/lugar>» (dativo pegado al infinitivo), registrada
 * "a medir" en c.900 (UNA forma por ciclo, doctrina anti-overreach
 * c.615). Misma metodología que [TraerObjetoProbe] (c.900) y
 * [DarleLasGraciasProbe] (c.903): candidatas declarativas cotidianas +
 * guards + regresiones. NO es un test; su salida PRE documenta el NULL
 * medido y POST el HIT tras el lockstep, convención c.857.
 *
 * Criterio de lectura: NULL sobre una candidata DECLARATIVA es GAP de
 * captura (olvido silencioso P1 — la keyword «traer» llega por subcadena
 * de «traerle» pero suma solo ~0.22 < umbral sin piso); NULL sobre guards
 * es CORRECTO (intencionado — los figurados «traerle suerte/alegría/
 * consecuencias» quedan fuera por lookahead anti-figurado heredado del
 * piso c.900).
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
    // Candidatas (esperado PRE: NULL)
    show("CAND-A", "traerle el cargador a Ana mañana")
    show("CAND-B", "traerle el libro a Marta el viernes")
    show("CAND-C", "mañana traerle el cuaderno a Irene")
    show("CAND-D", "vale, traerle las llaves a papá hoy")
    show("CAND-E", "traerles las fotos a los abuelos el sábado")
    // Guards (esperado: NULL correctos)
    show("GUARD-1", "no traerle el cargador a Ana")
    show("GUARD-2", "quizá le traiga el cargador a Ana")
    show("GUARD-3", "le traje el cargador a Ana ayer")
    show("GUARD-4", "traerle suerte a la casa")
    show("GUARD-5", "eso puede traerle consecuencias a la empresa")
    show("GUARD-6", "traerle alegría a la familia")
    show("GUARD-7", "traerle el cargador mañana")
    // Regresiones (esperado: HIT)
    show("REG-1", "traer el cargador a Ana mañana")
    show("REG-2", "llevarle su cuaderno a Ana")
    show("REG-3", "devolver el libro a la biblioteca")
    show("REG-4", "recuérdame traerle el cargador a Ana mañana")
    show("REG-5", "llamar a Ana mañana")
    show("REG-6", "darle gracias a Ana por el regalo")
}

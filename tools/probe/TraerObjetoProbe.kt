import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.900 (persistida): SEGUNDA forma NULL de la clase NOVENA-b
 * (coordinación y préstamos con personas, sonda c.890b) — «traer <objeto>
 * a <persona/lugar>», candidata (a) del BACKLOG. Misma metodología que
 * [NinthClassCoordinationProbe]: candidatas declarativas cotidianas +
 * regresiones + guards. NO es un test; su salida PRE documenta el NULL
 * medido y POST el HIT tras el lockstep (piso acotado `ERRAND_BRING_FLOOR`
 * + cláusula de negación + plantilla de título), convención c.857.
 *
 * PRE (verificado contra c.899, HEAD 35b6d27): 5/5 candidatas NULL;
 * 7/7 guards NULL (correcto); 6/6 regresiones HIT.
 *
 * Criterio de lectura: NULL sobre una candidata DECLARATIVA es GAP de
 * captura (olvido silencioso P1 — «traer» es keyword TASK suelta, 0.12,
 * bajo umbral); NULL sobre guards es CORRECTO (intencionado — los
 * figurados «traer suerte/consecuencias» quedan fuera por lookahead
 * anti-figurado; «traer a colación» no casa la ancla dativa).
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
    show("CAND-A", "traer el cargador a Ana mañana")
    show("CAND-B", "traer el libro a Marta el viernes")
    show("CAND-C", "mañana traer el cuaderno a Irene")
    show("CAND-D", "vale, traer las llaves a papá hoy")
    show("CAND-E", "traer el informe a la oficina mañana")
    // Guards (esperado: NULL correctos)
    show("GUARD-1", "no traer el cargador a Ana")
    show("GUARD-2", "quizá traiga el cargador a Ana")
    show("GUARD-3", "traje el cargador a Ana ayer")
    show("GUARD-4", "traer suerte a la casa")
    show("GUARD-5", "eso puede traer consecuencias a largo plazo")
    show("GUARD-6", "traer a colación el tema")
    show("GUARD-7", "la traída del cargador")
    // Regresiones (esperado: HIT)
    show("REG-1", "llevarle su cuaderno a Ana")
    show("REG-2", "recoger el paquete en Correos")
    show("REG-3", "devolver el libro a la biblioteca")
    show("REG-4", "recuérdame traer el cargador a Ana mañana")
    show("REG-5", "comprar pan mañana")
    show("REG-6", "llamar a Ana mañana")
}

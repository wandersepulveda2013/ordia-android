import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.903 (persistida): lateral enclítica de la candidata (b) de la
 * clase NOVENA-b — «darle las gracias a <persona> (por <objeto>)»,
 * medida NULL en la sonda hermana `DarLasGraciasProbe.kt` c.901 (2/2
 * laterales NULL) y registrada en BACKLOG (UNA forma por ciclo,
 * doctrina anti-overreach). Misma metodología que [DarLasGraciasProbe]:
 * candidatas declarativas cotidianas + guards + regresiones. NO es un
 * test; su salida PRE documenta el NULL medido y POST el HIT tras el
 * lockstep, convención c.857.
 *
 * Criterio de lectura: NULL sobre una candidata DECLARATIVA es GAP de
 * captura (olvido silencioso P1 — el piso c.901 exige la forma exacta
 * «dar las gracias» y el enclítico «darle» rompe la cadena: ni el piso
 * ni la keyword-frase «dar las gracias» casan); NULL sobre guards es
 * CORRECTO (intencionado — la negación, el pasado, el subjuntivo y la
 * forma sin destino no son compromiso; la lateral sin artículo «dar
 * gracias a…» queda a medir, UNA forma por ciclo).
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
    show("CAND-A", "darle las gracias a Ana por el regalo")
    show("CAND-B", "darle las gracias a Marta mañana")
    show("CAND-C", "mañana darle las gracias a Irene")
    show("CAND-D", "vale, darle las gracias a papá por el favor hoy")
    show("CAND-E", "darle las gracias a los vecinos el viernes")
    // Guards (esperado: NULL correctos)
    show("GUARD-1", "no darle las gracias a Ana")
    show("GUARD-2", "quizá le dé las gracias a Ana")
    show("GUARD-3", "le di las gracias a Ana ayer")
    show("GUARD-4", "darle las gracias")
    // Lateral a medir (esperado PRE: NULL; NO de este ciclo)
    show("LAT-1", "dar gracias a Ana por el regalo")
    // Regresiones (esperado: HIT)
    show("REG-1", "dar las gracias a Ana por el regalo")
    show("REG-2", "recuérdame darle las gracias a Ana mañana")
    show("REG-3", "avisar a mamá de la cita mañana")
    show("REG-4", "llamar a Ana mañana")
    show("REG-5", "dar de baja el gimnasio")
    show("REG-6", "comprar pan mañana")
    show("REG-7", "llevarle su cuaderno a Ana")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.901 (persistida): candidata (b) de la clase NOVENA-b
 * (coordinación y préstamos con personas, sonda `NinthClassCoordinationProbe.kt`
 * c.890b) — «dar las gracias a <persona> (por <objeto>)», ÚLTIMA forma
 * NULL medida de la clase. Misma metodología que [TraerObjetoProbe]:
 * candidatas declarativas cotidianas + guards + regresiones. NO es un
 * test; su salida PRE documenta el NULL medido y POST el HIT tras el
 * lockstep, convención c.857.
 *
 * Criterio de lectura: NULL sobre una candidata DECLARATIVA es GAP de
 * captura (olvido silencioso P1 — ni «dar» ni «gracias» son keyword ni
 * activan piso alguno); NULL sobre guards es CORRECTO (intencionado —
 * la negación, el pasado, el sustantivo y el subjuntivo no son
 * compromiso; las laterales enclítica y sin artículo quedan a medir,
 * doctrina anti-overreach UNA forma por ciclo).
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
    show("CAND-A", "dar las gracias a Ana por el regalo")
    show("CAND-B", "dar las gracias a Marta mañana")
    show("CAND-C", "mañana dar las gracias a Irene")
    show("CAND-D", "vale, dar las gracias a papá por el favor hoy")
    show("CAND-E", "dar las gracias a los vecinos el viernes")
    // c.902 (delta STALE_RUN): contracción «al» («a + el») — el piso c.901
    // exigía «a» literal + \s + \w, así «dar las gracias al jefe» quedaba
    // NULL (medido PRE sobre b956cc5: la keyword 0.12 sola inerte < umbral).
    // Esperado PRE: NULL; POST: HIT TASK 0.45.
    show("CAND-F", "dar las gracias al jefe por el ascenso hoy")
    show("CAND-G", "mañana dar las gracias al médico")
    // Guards (esperado: NULL correctos)
    show("GUARD-1", "no dar las gracias a Ana")
    show("GUARD-2", "quizá dé las gracias a Ana")
    show("GUARD-3", "di las gracias a Ana ayer")
    show("GUARD-4", "las gracias de Ana")
    show("GUARD-5", "dar las gracias")
    // Laterales a medir (esperado PRE: NULL; NO de este ciclo)
    show("LAT-1", "darle las gracias a Ana por el regalo")
    show("LAT-2", "dar gracias a Ana por el regalo")
    // Regresiones (esperado: HIT)
    show("REG-1", "avisar a mamá de la cita mañana")
    show("REG-2", "llamar a Ana mañana")
    show("REG-3", "recuérdame dar las gracias a Ana mañana")
    show("REG-4", "dar de baja el gimnasio")
    show("REG-5", "comprar pan mañana")
    show("REG-6", "llevarle su cuaderno a Ana")
}

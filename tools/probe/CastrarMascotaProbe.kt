import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1202 (candidata (a) DESCUBIERTA c.1195, clase
 * VIGESIMOSEGUNDA mascotas): «castrar al perro/gato» es transitiva
 * veterinaria monosemántica, hermana estructural de «desparasitar»
 * (c.1017) y «vacunar» (c.757). Lockstep 2 puntos (lección c.616/c.751,
 * sin keyword-verb como c.1017): piso acotado [HOUSEHOLD_NEUTER_FLOOR] +
 * plantilla matchCastrarMascota en extractTitle. PRE medido: 4/4 targets
 * NULL y 5/5 guards NULL y 3/3 regresiones HIT; envolventes TASK 0.45.
 * POST: directas HOUSEHOLD 0.45 con título "Castrar…"; envolventes TASK
 * byte-idénticas (policy envolvente gobierna kind).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} <- $t")
    }
    show("T1", "castrar al gato")
    show("T2", "castrar al perro")
    show("T3", "castrar a mi gato")
    show("T4", "castrar al gato mañana")
    show("G1", "no castrar al gato")
    show("G2", "castré al gato")
    show("G3", "que castre al gato")
    show("G4", "castrar al niño")
    show("R1", "vacunar al perro")
    show("R2", "desparasitar al gato")
    show("R3", "bañar al perro")
    show("E1", "recuérdame castrar al gato")
    show("E2", "tengo que castrar al gato")
}

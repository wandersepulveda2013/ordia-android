import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1205 (lateral ABIERTA de MI auditoría c.1195, clase
 * VIGESIMOSEGUNDA mascotas): «esterilizar al perro/gato» es sinónimo
 * terminante de «castrar» (transitiva veterinaria monosemántica).
 * Lockstep 2 puntos sin keyword-verb (gate c.751, precedente c.1017):
 * MISMO piso [HOUSEHOLD_NEUTER_FLOOR] alternando (?:castrar|esterilizar)
 * + MISMA alternativa en plantilla matchCastrarMascota. PRE medido: 3/3
 * targets NULL, 5/5 guards NULL (incluye nominalización G5), 3/3 regresiones
 * HIT y envolventes TASK 0.45; T4 es canario de «castrar» (c.1202).
 * POST: directas HOUSEHOLD 0.45, T3 dueAt correcto, guards NULL intactos;
 * envolventes byte-idénticas (policy envolvente gobierna kind).
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
    show("T1", "esterilizar al perro")
    show("T2", "esterilizar a mi gata")
    show("T3", "esterilizar a los perros mañana")
    show("T4", "castrar a la gata")
    show("G1", "no esterilizar al gato")
    show("G2", "ya esterilicé al perro")
    show("G3", "quizá esterilice al gato")
    show("G4", "esterilizar a ella")
    show("R1", "castrar al perro")
    show("R2", "vacunar al perro")
    show("R3", "desparasitar a mi gata")
    show("G5", "la esterilización del perro")
    show("E1", "recuérdame esterilizar al perro")
    show("E2", "tengo que esterilizar al gato")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1204 (lateral ABIERTA documentada de c.1174/c.1185/c.1192):
 * destino-posesivo «a mi entrevista» del mismo piso ERRAND_INTERVIEW_RUN_FLOOR.
 * Lockstep 2 puntos (lección c.616; CERO keywords nuevas, gate c.751 satisfecho
 * por «llevar» histórica): destino `(la) → (la|mi)` en el piso + MISMA alternativa
 * en la plantilla [matchInterviewRun] (grafía preservada c.653).
 * PRE (efímera /tmp/probe1204.kt sobre base 8d52939b y re-medida sobre e4fc31c7):
 * 5/5 candidatas NULL, 4/4 guards NULL, regresiones HIT byte-idénticas.
 * POST: directas ERRAND 0.45 con título limpio; envolventes TASK vía candado
 * WRAPPABLE (kind gobernado por policy envolvente — precedente c.1035/c.1139);
 * guard nuevo «(la|mi)» re-pin documentado en
 * [ContextIntentEngineLlevarDestinoPosesivoEntrevistaFloorTest].
 * DISJUNTO de marcadores activos hermanos (c.1202 mascota, c.1205 esterilizar,
 * c.1206 suscripciones). Primer-marcador-gana (c.1077). Nunca force, nunca main.
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
    // POST: las cinco candidatas deben capturar (antes NULL)
    show("D1", "llevar el currículum a mi entrevista mañana")
    show("D2", "llevarme el currículum a mi entrevista")
    show("D3", "llevar el CV a mi entrevista el jueves")
    show("D4", "mañana llevar el informe a mi entrevista")
    show("D5", "llevo el portfolio a mi entrevista")
    // Guards: se espera NULL (destino posesivo sin objeto exigido,
    // negación, duda, pasado)
    show("G1", "llevarme a mi entrevista")
    show("G2", "no llevo el currículum a mi entrevista")
    show("G3", "quizá lleve el currículum a mi entrevista")
    show("G4", "llevé el currículum a mi entrevista")
    // Regresiones: «a la entrevista» original (byte-idénticas)
    show("R1", "llevar el currículum a la entrevista mañana")
    show("R2", "llevarme el curriculum a la entrevista")
    show("R3", "llevar el CV a la entrevista")
    show("R4", "llévame el currículum")
}

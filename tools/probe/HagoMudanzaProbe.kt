import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda persistida c.1171: presente 1ª persona «hago la mudanza» (lateral (d-bis) de c.1169).
// PRE (HEAD ec8c5c21): C1-C5 [NULL], G1-G6 [NULL], R1-R6 [HIT].
// POST (lockstep (hacer|hago) aplicado): C1-C5 [HIT] TASK 0.45, G1-G6 [NULL],
// R1-R6 byte-idénticos. Test: ContextIntentEngineHagoMudanzaFloorTest (17).
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} <- $t"
        println("$label $s")
    }

    // --- CANDIDATAS (presente 1ª persona, compromiso plausible) ---
    show("C1", "hago la mudanza el sábado")
    show("C2", "hago la mudanza del piso nuevo el fin de semana")
    show("C3", "el lunes hago la mudanza")
    show("C4", "hago la mudanza")
    show("C5", "vale, hago la mudanza del piso")
    // --- GUARDS ---
    show("G1", "no hago la mudanza")
    show("G2", "hice la mudanza ayer")
    show("G3", "quizá haga la mudanza el sábado")
    show("G4", "haga la mudanza")
    show("G5", "él hace la mudanza el sábado")
    show("G6", "la mudanza del piso nuevo")
    // --- REGRESIONES / PINES ---
    show("R1", "hacer la mudanza el sábado")
    show("R2", "hacer la maleta mañana")
    show("R3", "hacer el curso de prevención antes del día 30")
    show("R4", "comprar leche mañana")
    show("R5", "tengo que hacer la mudanza del piso")
    show("R6", "hacer los deberes con los niños")
}

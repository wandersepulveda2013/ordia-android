import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda efímera PRE c.1175: futuro 1ª persona «haré la mudanza» (lateral (d-ter) de c.1169).
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

    // --- CANDIDATAS (futuro 1ª persona, compromiso diferido) ---
    show("C1", "haré la mudanza el sábado")
    show("C2", "haré la mudanza del piso nuevo en octubre")
    show("C3", "el lunes haré la mudanza")
    show("C4", "haré la mudanza")
    show("C5", "vale, haré la mudanza mañana")

    // --- GUARDS (deben seguir NULL) ---
    show("G1", "no haré la mudanza esta semana")
    show("G2", "hice la mudanza ayer")
    show("G3", "quizá haga la mudanza en verano")
    show("G4", "él hará la mudanza mañana")
    show("G5", "haría la mudanza si tuviera furgoneta")
    show("G6", "la mudanza del piso nuevo será en octubre")

    // --- REGRESIONES (pines byte-idénticos) ---
    show("R1", "hacer la mudanza el sábado")
    show("R2", "hago la mudanza el sábado")
    show("R3", "hacer la maleta esta noche")
    show("R4", "hacer el curso de prevención antes del día 30")
    show("R5", "recuérdame hacer la mudanza mañana")
    show("R6", "hacer la compra mañana")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.897 — sonda de las laterales salariales de la familia cobros (3/8) que
 * el hermano c.895b (commit `698c8ba`, máster «cobrar la nómina/reembolso»
 * TASK) dejó sin cubrir: «cobrar la pensión/el sueldo/el salario»
 * (objetos-hermanos de la nómina). Metodología de [CobrarNominaProbe] (c.895b),
 * [SacarDineroProbe]/[IngresarDineroProbe]/[DepositChequeProbe] (c.893…c.895):
 * NO es un test; PRE documenta el NULL medido y POST el HIT tras la
 * ampliación aditiva del piso hermano (objetos `pensi[oó]n(es)?|sueldo|
 * salario` + keywords-OBJETO «pensión/pension/sueldo/salario» lockstep),
 * convención c.857. Guard anti-overreach: bivalentes sin ancla («cobrar la
 * compra/el alquiler/la deuda») NULL deliberados (doctrina del hermano).
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (laterales salariales del hermano c.895b; PRE NULL objetivo)
    val candidates = listOf(
        "cobrar la pensión mañana",
        "cobrar el sueldo esta tarde",
        "cobrar el salario el viernes"
    )

    // Guards anti-overreach (objetivo: NULL siempre)
    val guards = listOf(
        "no cobrar la pensión mañana",
        "no cobrar el sueldo mañana",
        "cobrar la compra mañana",
        "cobrar el alquiler mañana",
        "cobrar la deuda mañana",
        "quizá cobrar la pensión mañana"
    )

    // Regresiones conocidas (objetivo: HIT inalterado)
    val regressions = listOf(
        "cobrar la nómina mañana",
        "cobrar el reembolso mañana",
        "ingresar el reembolso mañana",
        "pagar la tarjeta mañana",
        "revisar el extracto del banco mañana"
    )

    println("=== CANDIDATAS (laterales salariales; PRE se espera NULL) ===")
    for (c in candidates) {
        val r = probe(c)
        if (r == null) println("[NULL] " + "«" + c + "»")
        else println("[HIT]  " + r.kind + " " + r.confidence + " | \"" + r.title +
            "\" | dueAt=" + (r.dueAt != null) + " ← «" + c + "»")
    }
    println("=== GUARDS anti-overreach (objetivo: NULL) ===")
    for (c in guards) {
        val r = probe(c)
        if (r == null) println("[OK] NULL ¬ «" + c + "»")
        else println("[OVERREACH] " + r.kind + " | \"" + r.title + "\" ← «" + c + "»")
    }
    println("=== REGRESIONES (objetivo: HIT inalterado) ===")
    for (c in regressions) {
        val r = probe(c)
        if (r != null) println("[HIT " + r.kind + " " + r.confidence + "] \"" +
            r.title + "\" ← «" + c + "»")
        else println("[LOST] «" + c + "»")
    }
}

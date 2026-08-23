import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.895 — sonda de las laterales que el hermano c.894 (commit `cec1e29`,
 * máster «ingresar dinero/reembolso») dejó medidas como PENDIENTES en su
 * propio comentario del piso [ERRAND_DEPOSIT_FLOOR]: «depositar el cheque»
 * (verbo hermano) y «hacer el ingreso» (forma sustantiva). Familia (2/8) de
 * la clase NOVENA (sonda c.892, BACKLOG P1). Misma metodología que
 * [NinthClassMoneyProbe]/[SacarDineroProbe]/[IngresarDineroProbe] (c.892/
 * c.893/c.894): NO es un test; su salida PRE documenta el NULL medido y
 * POST el HIT tras el lockstep (piso+keywords+plantilla/cinturón),
 * convención c.857. Guard anti-overreach: objetos distintos («la basura»,
 * «la contraseña») NO; laterales bivalentes sin ancla («ir a ingresar»/
 * «pasar a depositar» — hospital/club/universidad) NULL deliberados.
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (laterales del hermano: PRE NULL → objetivo: captura ERRAND)
    val candidates = listOf(
        "depositar el cheque mañana",
        "depositar el reembolso mañana",
        "hacer el ingreso mañana"
    )

    // Guards anti-overreach / negación (objetivo: NULL siempre)
    val guards = listOf(
        "no depositar el cheque mañana",
        "no hacer el ingreso mañana",
        "depositar la basura mañana",
        "hacer la limpieza mañana",
        "quizá depositar el cheque mañana"
    )

    // Laterales bivalentes sin ancla (objetivo: NULL deliberado)
    val laterales = listOf(
        "ir a ingresar mañana",
        "pasar a depositar esta tarde"
    )

    // Regresiones conocidas (objetivo: HIT inalterado)
    val regressions = listOf(
        "ingresar dinero en el banco mañana",
        "ingresar el reembolso mañana",
        "sacar dinero mañana",
        "ir al banco mañana",
        "pagar la tarjeta mañana",
        "revisar el extracto del banco mañana"
    )

    println("=== CANDIDATAS (laterales c.894; PRE se espera NULL) ===")
    for (c in candidates) {
        val r = probe(c)
        if (r == null) println("[NULL] " + "«" + c + "»")
        else println("[HIT]  " + r.kind + " " + r.confidence + " | \"" + r.title +
            "\" | dueAt=" + (r.dueAt != null) + " ← «" + c + "»")
    }
    println("=== GUARDS (objetivo: NULL) ===")
    for (c in guards) {
        val r = probe(c)
        if (r == null) println("[OK] NULL ¬ «" + c + "»")
        else println("[OVERREACH] " + r.kind + " | \"" + r.title + "\" ← «" + c + "»")
    }
    println("=== LATERALES (objetivo: NULL deliberado, sin ancla) ===")
    for (c in laterales) {
        val r = probe(c)
        if (r == null) println("[OK] NULL ¬ «" + c + "»")
        else println("[HIT] " + r.kind + " | \"" + r.title + "\" ← «" + c + "»")
    }
    println("=== REGRESIONES (objetivo: HIT inalterado) ===")
    for (c in regressions) {
        val r = probe(c)
        if (r != null) println("[HIT " + r.kind + " " + r.confidence + "] \"" +
            r.title + "\" ← «" + c + "»")
        else println("[LOST] «" + c + "»")
    }
}

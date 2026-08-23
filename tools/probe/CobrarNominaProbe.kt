import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.895b — sonda de la familia (3/8) de la clase NOVENA dinero/banca
 * (sonda c.892, BACKLOG P1): «cobrar la nómina/el reembolso». Decisión de
 * dominio TASK (gestión financiera SIN desplazamiento — el dinero entra
 * por cuenta/transferencia; hermana de «revisar el extracto» TASK, no de
 * la doctrina «la diligencia gobierna» ERRAND c.842/c.862 que gobierna
 * solo cuando hay desplazamiento físico al banco/ATM). Misma metodología
 * que [DepositChequeProbe] (c.895): NO es un test; su salida PRE sobre
 * HEAD 3b3766c documenta el NULL medido y POST el HIT tras el lockstep
 * (piso TASK acotado + keywords-OBJETO + plantilla de título).
 * Guard anti-overreach: objetos fuera de ancla («la compra», «el alquiler»,
 * «la deuda») NULL deliberados; negación/duda/pasado/sustantivo «el cobro…»
 * NULL siempre.
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (objetivo POST: captura TASK)
    val candidates = listOf(
        "cobrar la nómina mañana",
        "cobrar el reembolso mañana",
        "cobrar la nómina el viernes",
        "cobrar el reembolso esta tarde"
    )

    // Guards (objetivo: NULL siempre)
    val guards = listOf(
        "no cobrar la nómina mañana",
        "quizá cobrar el reembolso mañana",
        "ayer cobré la nómina",
        "el cobro de la nómina llegó",
        "cobrar la compra mañana"
    )

    // Laterales bivalentes sin ancla (objetivo: NULL deliberado)
    val laterales = listOf(
        "cobrar el alquiler mañana",
        "cobrar la deuda mañana"
    )

    // Regresiones conocidas (objetivo: HIT inalterado)
    val regressions = listOf(
        "ingresar el reembolso mañana",
        "revisar el extracto del banco mañana",
        "pagar la tarjeta mañana",
        "recuérdame cobrar la nómina mañana"
    )

    println("=== CANDIDATAS (familia 3/8 cobros; PRE se espera NULL) ===")
    for (c in candidates) {
        val r = probe(c)
        if (r == null) println("[NULL] «" + c + "»")
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

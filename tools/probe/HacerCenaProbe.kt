import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.898 — sonda de la familia (5/8) de la clase NOVENA (comida/deberes): los
 * objetivos de comida/deberes de la clase («hacer la cena esta noche»,
 * «preparar el almuerzo mañana», «descongelar la carne por la tarde»,
 * «hacer los deberes mañana»). Metodología de [NinthClassMoneyProbe] (c.885),
 * [CobrarNominaProbe] (c.895b), [CobrarPensionProbe] (c.897): PRE documenta
 * un NULL medido y POST un HIT tras la ampliación del piso «hacer/preparar
 * la <comida/deberes>». Guards anti-overreach (bivalentes/target conkeyword
 * «hacer la lista/el plan» y «cocinar» y negados; sins «hacer la lista mañana»
 * NULL siempre). Convención c.857 (UNA familia por ciclo).
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    val candidates = listOf(
        "hacer la cena esta noche",
        "preparar el almuerzo mañana",
        "descongelar la carne por la tarde",
        "hacer los deberes mañana"
    )

    val guards = listOf(
        "no hacer la cena esta noche",
        "no preparar el almuerzo mañana",
        "hacer la lista mañana",
        "hacer el plan mañana",
        "quizá hacer la cena esta noche"
    )

    // «cocinar» ya tiene piso libre en HOUSEHOLD: el guard aquí es la
    // EXPECTATIVA de cobertura hermana (HIT antes del c.898), no el
    // objetivo-NULL anti-overreach. Se lista aparte para que el PRE no
    // marque falsos FAILs ([FAIL-sóguard] c.898a ajustada).
    val coveredSiblings = listOf(
        "cocinar la cena esta noche"
    )

    val regressions = listOf(
        "hacer la compra mañana",
        "hacerse una foto mañana",
        "ir al cajero mañana",
        "revisar el extracto del banco mañana"
    )

    println("=== CANDIDATAS (objetivos comida/deberes; PRE se espera NULL) ===")
    var gap = 0
    for (c in candidates) {
        val r = probe(c)
        if (r == null) { gap++; println("[NULL] ← {${c}}") }
        else println("[HIT] ${r.kind} ${r.confidence} | \"${r.title}\" | dueAt=${r.dueAt != null} ← «$c»")
    }
    println("=== GUARDS anti-overreach (objetivo: NULL siempre) ===")
    for (c in guards) {
        val r = probe(c)
        if (r == null) println("[OK] NULL ¬ «$c»")
        else println("[FAIL-guard] ${r.kind} ${r.confidence} ← «$c»")
    }
    println("=== HERMANAS ya cubiertas antes del cambio (objetivo: HIT) ===")
    for (c in coveredSiblings) {
        val r = probe(c)
        if (r != null) println("[OK] HIT ¬ «$c» (${r.kind})")
        else println("[FAIL] NULL ¬ «$c»")
    }
    println("=== REGRESIONES (objetivo: HIT) ===")
    for (c in regressions) {
        val r = probe(c)
        if (r != null) println("[HIT ${r.kind} ${r.confidence}] \"${r.title}\" ← «$c»")
        else println("[NULL] ← «$c»")
    }
    if (gap == 0) { println("SIN GAPs: familia (5) alcanzó su piso"); return }
    println("GAP=$gap/4 (NULLEO medio de la familia (5/8) NOVENA)")
}

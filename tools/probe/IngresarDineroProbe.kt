import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.894 (persistida): SEGUNDA familia NULL de la clase NOVENA
 * (gestiones de dinero y banca cotidiana, c.892) — INGRESOS / DEPÓSITO.
 * Misma metodología que [NinthClassMoneyProbe] (c.892) y [SacarDineroProbe]
 * (c.893): frases declarativas cotidianas (compromiso plausible) +
 * regresiones + controles. NO es un test; su salida PRE documenta el NULL
 * medido y POST el HIT tras el lockstep (piso+keyword+plantilla/cinturón),
 * convención c.857.
 *
 * Criterio de lectura: NULL sobre una candidata DECLARATIVA es GAP de
 * captura (olvido silencioso P1); NULL sobre controles es CORRECTO
 * (intencionado — bivalence «ingresar en el club» queda fuera de la
 * ancla-objeto `dinero|reembolso` deliberada; la débil «hacer el ingreso»
 * queda como lateral medida fuera del alcance de esta sonda, una forma por
 * ciclo).
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // --- CANDIDATAS: la familia «ingresar (el/el dinero/el reembolso)» ---
        "ingresar el dinero mañana",
        "ingresar dinero en el banco mañana",
        "ingresar el reembolso el lunes",
        "ingresar el dinero",
        "vale, ingresar el dinero mañana",
        "mañana ingresar el dinero",
        // --- REGRESIONES: deben seguir HIT ---
        "sacar el dinero del cajero mañana", // ERRAND piso acotado c.893
        "pasar por el banco mañana", // ERRAND piso acotado c.718
        "cambiar dólares en el banco mañana", // TASK piso abierto c.710
        "recuérdame ingresar el dinero mañana", // TASK envolvente c.613
        // --- CONTROLES: deben permanecer NULL ---
        "no ingresar el dinero mañana", // negación (lookbehind `(?<!no )`)
        "quizá ingresar el dinero mañana", // duda (HEDGE_PENALTY c.649)
        "ingresé el dinero ayer", // narrativa pasado (no-infinitivo)
        "ingresamos el dinero ayer", // narrativa pasado 1ª pl.
        "ingresar", // verbo aislado (ancla-objeto exigida)
        "ingresar en el club mañana", // bivalente (club ≠ ancla-objeto)
        "el reembolso tardó dos semanas", // declarativo sin imperativo
        "no ingresar el reembolso el lunes" // negación (cinturón y tirantes)
    )
    var nulls = 0
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        if (intent == null) {
            nulls++
            println("[NULL] $c")
        } else {
            println(
                "[HIT] ${intent.kind} ${"%.2f".format(intent.confidence)}" +
                    " | ${intent.title} | dueAt=${intent.dueAt != null} ← $c"
            )
        }
    }
    println("=== RESUMEN: $nulls NULLs de ${cases.size} ===")
}

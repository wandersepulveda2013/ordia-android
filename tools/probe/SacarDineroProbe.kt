import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.893 (persistida): PRIMERA familia NULL de la clase NOVENA
 * (gestiones de dinero y banca cotidiana, c.892) — EFECTIVO / CAJERO. Misma
 * metodología que [NinthClassMoneyProbe] (c.892): frases declarativas
 * cotidianas (compromiso plausible) + regresiones + controles. NO es un
 * test; su salida PRE documenta el NULL medido y POST el HIT tras el
 * lockstep (piso+keyword+plantilla/cinturón), convención c.857.
 *
 * Criterio de lectura: NULL sobre una candidata DECLARATIVA es GAP de
 * captura (olvido silencioso P1); NULL sobre controles es CORRECTO
 * (intencionado — bidence «sacar la tarjeta»/«sacar a bailar» queda fuera
 * de la ancla-objeto `dinero|efectivo` deliberada).
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // --- CANDIDATAS: la familia «sacar dinero/efectivo» + «ir al cajero» ---
        "sacar dinero mañana",
        "sacar el dinero del cajero mañana",
        "sacar efectivo del cajero el viernes",
        "sacar efectivo del atm antes del viaje",
        "ir al cajero mañana",
        "vale, sacar dinero",
        // --- REGRESIONES: deben seguir HIT ---
        "sacar la basura mañana", // HOUSEHOLD piso acotado c.717
        "sacar al perro esta tarde", // HOUSEHOLD piso acotado c.740
        "ir al banco mañana", // ERRAND keyword «banco» c.639
        "pasar por el banco mañana", // ERRAND piso acotado c.718
        "revisar el extracto del banco mañana", // TASK piso revisar c.691
        "pagar la tarjeta el viernes", // PAYMENT keyword «pagar»
        "recuérdame sacar dinero mañana", // TASK envolvente c.613 (piso protegido)
        // --- CONTROLES: deben permanecer NULL ---
        "no sacar dinero mañana", // negación (lookbehind `(?<!no )`)
        "quizá sacar dinero mañana", // duda (HEDGE_PENALTY c.649)
        "saqué dinero ayer", // narrativa pasado (forma no-infinitivo)
        "sacar", // verbo aislado (ancla-objeto exigida)
        "sacar la tarjeta mañana", // bivalente (tarjeta ≠ ancla-objeto)
        "sacar a bailar mañana", // bivalente (a bailar ≠ dinero)
        "el dinero está en la mesa", // declarativo sin imperativo
        "no ir al cajero mañana" // negación de destino (cinturón y tirantes)
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

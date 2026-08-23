import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.899: los objetos de «descongelar» son bivalentes — el piso libre
 * `descongelar\s+\w` del hermano c.898 roba «el banco»/«la cuenta»/«el
 * congelador» como HOUSEHOLD (overreach). Candidatas = formas que NO son
 * comida; medida PRE "HIT indebido" → post-fix NULL.
 */
fun main() {
    val candidates = listOf(
        "descongelar el congelador mañana",
        "descongelar el banco mañana",
        "descongelar la cuenta mañana"
    )
    val regressions = listOf(
        "descongelar la carne por la noche",
        "descongelar el pollo mañana",
        "descongelar el pescado esta tarde"
    )
    println("=== CANDIDATAS overreach (PRE: HIT indebido; POST: NULL) ===")
    for (c in candidates) {
        val intent = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, c, 1000))
        if (intent == null) println("[OK] NULL ← «$c»")
        else println("[OVERREACH] ${intent.kind} ${intent.confidence} | \"${intent.title}\" ← «$c»")
    }
    println("=== REGRESIONES comida (objetivo: HIT inalterado) ===")
    for (c in regressions) {
        val intent = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, c, 1000))
        if (intent != null) println("[HIT] ${intent.kind} | \"${intent.title}\" ← «$c»")
        else println("[NULL-FAIL] ← «$c»")
    }
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun nullOrNot(id: String, t: String) {
        val r = a(t)
        println(if (r == null) "["+id+"] NULL  <- "+t else "["+id+"] HIT "+r.kind+" "+r.confidence+" |"+r.title+"  <- "+t)
    }
    listOf(
        "T1" to "podar el rosal",
        "T2" to "podar los setos",
        "T3" to "podar los rosales",
        "T4" to "podar los setos mañana",
        "G1" to "no podar los setos",
        "G2" to "ya podé el rosal",
        "G3" to "quizás pode los setos",
        "G4" to "la poda del rosal",
        "G5" to "podar el árbol",
        "R1" to "podar el jardín",
        "R2" to "regar las plantas",
        "E1" to "recuérdame podar el rosal"
    ).forEach { (id, t) -> nullOrNot(id, t) }
}

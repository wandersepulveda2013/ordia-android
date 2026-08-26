import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun probe(id: String, t: String) {
        val r = a(t)
        println(if (r == null) id+" [NULL] <- "+t else id+" [HIT] "+r.kind+" "+r.confidence+" | "+r.title+" | dueAt="+(r.dueAt != null)+" <- "+t)
    }
    probe("T1", "doblar la ropa")
    probe("T2", "doblar mi ropa")
    probe("T3", "doblar la ropa hoy")
    probe("T4", "doblar los trapos hoy")
    probe("G1", "no doblar la ropa")
    probe("G2", "ya doblé la ropa")
    probe("G3", "quizá doble la ropa")
    probe("G4", "doblar y planchar hecha")
    probe("G5", "el doblado de ropa")
    probe("R1", "lavar la ropa")
    probe("R2", "colgar la ropa")
    probe("R3", "planchar la camisa")
    probe("E1", "recuérdame doblar la ropa")
    probe("E2", "tengo que doblar la ropa")
}

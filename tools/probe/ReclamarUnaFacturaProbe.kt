import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextCaptureSource

fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t" else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }
    show("C1", "reclamar una factura mañana")
    show("C2", "reclamar una factura esta tarde")
    show("C3", "reclamar una factura al banco mañana")
    show("G1", "reclamar un premio mañana")
    show("G2", "reclamar un turno mañana")
    show("G3", "no reclamar una factura mañana")
    show("G4", "quizá reclamar una factura mañana")
    show("G5", "reclamé una factura ayer")
    show("R1", "reclamar la factura del banco mañana")
    show("R2", "reclamar las facturas mañana")
    show("R3", "reclamar mi factura mañana")
}

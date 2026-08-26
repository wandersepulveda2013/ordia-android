import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda POST c.1191 (persistida): lateral (b) de c.1190 — objeto
// «el informe|portfolio» del piso entrevista. PRE (c.1191) medía 4/4
// targets NULL; POST medida en vivo sobre el motor sin stash: targets
// HIT, guards NULL (negación/pasado/duda), regresiones «currículum»/«CV»
// HIT. Evidencia efímera convertida en persistida (protocolo).
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L))
    fun show(label: String, t: String) {
        val i = a(t)
        println(label + " " + (if (i == null) "[NULL] " + t else "[HIT] " + i.kind + " " + i.confidence + " | " + i.title + " | dueAt=" + (i.dueAt != null) + " <- " + t))
    }
    show("T1", "llevar el informe a la entrevista")
    show("T2", "llevarme el informe a la entrevista mañana")
    show("T3", "llevar el portfolio a la entrevista")
    show("T4", "llevar mi informe a la entrevista")
    show("G1", "no voy a llevar el informe a la entrevista")
    show("G2", "llevé el informe a la entrevista ayer")
    show("G3", "quizá lleve el portfolio a la entrevista")
    show("R1", "llevar el currículum a la entrevista")
    show("R2", "llevar el CV a la entrevista")
    show("R3", "llevarme el currículum a la entrevista")
}

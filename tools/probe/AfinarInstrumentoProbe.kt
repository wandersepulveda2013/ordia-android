import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda c.1251 (lateral (b) FUERTE de MI auditoría c.1248, clase XXXV
// música/instrumentos): «afinar <instrumento acotado>». PRE: se espera
// NULL en T1–T6 (gap silencioso P1). POST: HIT TASK con título limpio.
// Guards: negación, pretérito, duda, declarativa, verbo solo, sustantivo,
// figurado fuera del objeto acotado → NULL. Envolventes → TASK.
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    tag("T1", "afinar la guitarra")
    tag("T2", "afinar el piano")
    tag("T3", "afinar el violín por la tarde")
    tag("T4", "afinar mi ukelele")
    tag("T5", "afinar el saxofón")
    tag("T6", "afinar la trompeta")
    tag("G1", "no afinar la guitarra")
    tag("G2", "afinó la guitarra ayer")
    tag("G3", "quizá afinar el violín")
    tag("G4", "la guitarra está afinada")
    tag("G5", "afinar")
    tag("G6", "la afinación del piano")
    tag("G7", "afinar la puntería")
    tag("G8", "afinar los detalles del proyecto")
    tag("E1", "recuérdame afinar la guitarra")
    tag("E2", "tengo que afinar el piano")
    tag("R1", "llamar a mamá")
    tag("R2", "taladrar la pared")
    tag("R3", "sincronizar los archivos")
    tag("R4", "apagar el ordenador")
}

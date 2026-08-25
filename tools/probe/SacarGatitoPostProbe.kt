import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda POST c.1054 (persistida): «sacar al gatito» diminutivo — re-medida
// tras el fix. PRE (medido con /tmp/probe1053/Probe.kt): 6/6 candidatas
// puras NULL (gap confirmado). POST: 6/6 HIT HOUSEHOLD, guards intactos,
// regresiones intactas, pins FUERA siguen NULL.
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    var failed = 0
    fun expectNull(label: String, t: String) {
        val i = a(t)
        if (i == null) println("OK  [NULL] $label ← $t")
        else { failed++; println("FAIL [HIT] $label ${i.kind} ${i.confidence} | ${i.title} ← $t") }
    }
    fun expectHit(label: String, t: String, due: Boolean = false) {
        val i = a(t)
        if (i == null) { failed++; println("FAIL [NULL] $label ← $t"); return }
        val ok = !due || i.dueAt != null
        if (ok) println("OK  [HIT] $label ${i.kind} | ${i.title} | dueAt=${i.dueAt != null} ← $t")
        else { failed++; println("FAIL [HIT sin dueAt] $label ← $t") }
    }
    expectHit("C1", "sacar al gatito")
    expectHit("C2", "sacar a la gatita")
    expectHit("C3", "sacar a mi gatito")
    expectHit("C4", "sacar el gatito")
    expectHit("C5 dueAt", "sacar al gatito a las 8", due = true)
    expectHit("C6 envolvente TASK", "tengo que sacar al gatito")
    expectNull("G1 negación inmediata", "no sacar al gatito hoy")
    expectNull("G2 envolvente c.1009", "no voy a sacar al gatito")
    expectNull("G3 pasado", "saqué al gatito ayer")
    expectNull("G4 hedge subjuntivo", "quizá saque al gatito mañana")
    expectHit("REG1 regresión gato c.1052", "sacar al gato a las 8", due = true)
    expectHit("REG2 regresión perro c.740", "sacar al perro a las 8", due = true)
    expectNull("FUERA1 diminutivo perro", "sacar al perrito mañana")
    expectNull("FUERA2 vía pasear gatito", "pasear al gatito mañana")
    println(if (failed == 0) "POST OK: 6/6 HIT + 4/4 guards + 2/2 REG + 2/2 FUERA" else "POST FAILS=$failed")
}

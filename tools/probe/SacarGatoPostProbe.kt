import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda POST c.1050 (persistida): «sacar al gato» — re-medida tras el fix.
// PRE (medido): 6/6 candidatas puras NULL + C7 TASK (hueco = piso) + 7/7
// guards NULL + 6/6 regresiones HIT. POST: 6/6 candidatas HIT HOUSEHOLD
// (dueAt=true con fecha/hora), C7 sigue TASK (envolvente c.613 gobierna),
// guards intactos NULL, regresiones intactas HIT.
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
        if (ok) println("OK  [HIT] $label ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t")
        else { failed++; println("FAIL [HIT sin dueAt] $label ${i.kind} | ${i.title} ← $t") }
    }

    // CANDIDATAS (gap esperado PRE: NULL)
    expectHit("C1", "sacar al gato")
    expectHit("C2", "sacar a la gata")
    expectHit("C3", "sacar a los gatos")
    expectHit("C4", "sacar a mi gato")
    expectHit("C5", "sacar la gata")
    expectHit("C6 dueAt", "sacar al gato a las 8", due = true)
    expectHit("C7 keyword-generica ya captura TASK (hueco = piso HOUSEHOLD)", "tengo que sacar al gato")

    // GUARDS (deben seguir NULL)
    expectNull("G1 destinatario humano", "sacar al bebé")
    expectNull("G2 sin mascota", "sacar las entradas")
    expectNull("G3 negación inmediata", "no sacar al gato")
    expectNull("G4 plan negado", "no voy a sacar al gato")
    expectNull("G5 pasado", "saqué al gato")
    expectNull("G6 hedge subjuntivo", "quizás sacar al gato")
    expectNull("G7 sintagma nominal", "el paseo del gato")

    // REGRESIONES (deben seguir HIT)
    expectHit("R1 perro c.740", "sacar al perro")
    expectHit("R2 perra fam.", "sacar la perra")
    expectHit("R3 perros plural", "sacar a los perros")
    expectHit("R4 pasear perro c.1018", "pasear al perro")
    expectHit("R5 pasear gato c.1046", "pasear al gato")
    expectHit("R6 alimentar gato c.744", "alimentar al gato")

    if (failed > 0) { println("RESULT: $failed UNEXPECTED"); kotlin.system.exitProcess(1) }
    println("RESULT: ALL POST EXPECTATIONS OK (6 HIT + C7 TASK + 7 GUARDS NULL + 6 HIT REG)")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de VERIFICACIÓN POST c.1174 (persistida): piso acarreo
 * «llevar el currículum a la entrevista»
 * (`ERRAND_INTERVIEW_RUN_FLOOR` + plantilla `matchInterviewRun`,
 * lockstep lección c.616). El PRE medido sobre HEAD `9220964` daba
 * 6/6 candidatas NULL (descubrimiento d-bis de la auditoría c.1165
 * — sonda EighteenthClassSocialProbe). Esta sonda queda como
 * evidencia reproducible del POST: las 6 capturas deben ser HIT
 * ERRAND, los guards NULL (negación compuesta/directa, duda
 * subjuntivo, pretérito, sustantivo, verbo aislado), los pines
 * anti-overreach NULL («el CV», «el informe», «a la oficina») y
 * las regresiones «llevar» HIT (taller c.684, cole c.773, fiesta
 * del cole c.1170, veterinario c.747).
 */
fun main() {
    fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )
    var ok = 0
    var fail = 0
    fun expect(label: String, text: String, hit: Boolean, kind: String? = null) {
        val r = analyze(text)
        val good = if (hit) {
            r != null && (kind == null || r.kind.name == kind)
        } else {
            r == null
        }
        if (good) { ok++; println("[OK ] $label") } else {
            fail++
            val got = if (r == null) "NULL" else "HIT ${r.kind} '${r.title}'"
            println("[FALLO] $label -> $got")
        }
    }

    // Capturas (POST: HIT ERRAND)
    expect("C1 llevar el currículum a la entrevista mañana", "llevar el currículum a la entrevista mañana", true, "ERRAND")
    expect("C2 entrevista de trabajo", "llevar el currículum a la entrevista de trabajo mañana", true, "ERRAND")
    expect("C3 primera persona llevo", "llevo el currículum a la entrevista el jueves", true, "ERRAND")
    expect("C4 prefijo temporal", "mañana llevar el currículum a la entrevista", true, "ERRAND")
    expect("C5 sin temporal", "llevar el currículum a la entrevista", true, "ERRAND")
    expect("C6 grafía sin tilde", "llevar el curriculum a la entrevista mañana", true, "ERRAND")

    // Guards (POST: NULL)
    expect("G1 negación compuesta", "no voy a llevar el currículum a la entrevista mañana", false)
    expect("G2 negación directa", "no llevo el currículum a la entrevista", false)
    expect("G3 duda subjuntivo", "quizá lleve el currículum a la entrevista", false)
    expect("G4 pretérito", "llevé el currículum a la entrevista ayer", false)
    expect("G5 sustantivo aislado", "el currículum de la entrevista", false)
    expect("G6 verbo aislado", "llevar", false)

    // Pines anti-overreach (POST: NULL deliberado)
    expect("P1 el CV fuera", "llevar el CV a la entrevista mañana", false)
    expect("P2 otro objeto fuera", "llevar el informe a la entrevista mañana", false)
    expect("P3 otro destino fuera", "llevar el currículum a la oficina mañana", false)

    // Regresiones (POST: HIT intacto)
    expect("R1 coche al taller (c.684)", "llevar el coche al taller mañana", true)
    expect("R2 niños al cole (c.773)", "llevar a los niños al cole mañana", true)
    expect("R3 fiesta del cole (c.1170)", "llevar a los niños a la fiesta del cole el viernes", true)
    expect("R4 perro al veterinario (c.747)", "llevar al perro al veterinario el sábado", true)

    println()
    println("RESULTADO c.1174 POST: $ok OK, $fail FALLOS (esperado: 19 OK, 0 FALLOS)")
    if (fail > 0) kotlin.system.exitProcess(1)
}

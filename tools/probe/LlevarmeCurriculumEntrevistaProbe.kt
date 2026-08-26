import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de VERIFICACIÓN POST c.1180 (persistida): forma reflexiva
 * (dativo ético) del piso acarreo «llevarme (el|mi|tu|su)
 * curr[ií]culum a la entrevista» — extensión aditiva del verbo en
 * `ERRAND_INTERVIEW_RUN_FLOOR` + plantilla `matchInterviewRun`
 * (lockstep lección c.616; CERO keywords nuevas, gate c.751).
 * El PRE medido sobre HEAD `ed11660` (sonda efímera
 * `/tmp/probe1180/Probe.kt`) daba 6/6 candidatas NULL (lateral
 * ABIERTA del cierre c.1174). Esta sonda queda como evidencia
 * reproducible del POST: las 6 capturas deben ser HIT ERRAND con
 * el título conservando «Llevarme» (grafía preservada c.653), los
 * guards NULL (negación compuesta, duda subjuntivo, pretérito),
 * los pines anti-overreach NULL (imperativo enclítico «llévame»,
 * otro objeto «el informe», otro destino «la oficina») y las
 * regresiones «llevar» HIT (c.1174 no reflexiva, taller c.684,
 * cole c.773, portátil c.1157).
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
    expect("C1 llevarme el currículum mañana", "llevarme el currículum a la entrevista mañana", true, "ERRAND")
    expect("C2 posesivo mi currículum", "llevarme mi currículum a la entrevista mañana", true, "ERRAND")
    expect("C3 grafía sin tilde", "llevarme el curriculum a la entrevista mañana", true, "ERRAND")
    expect("C4 prefijo temporal", "mañana llevarme el currículum a la entrevista", true, "ERRAND")
    expect("C5 entrevista de trabajo", "llevarme el currículum a la entrevista de trabajo mañana", true, "ERRAND")
    expect("C6 sin temporal", "llevarme el currículum a la entrevista", true, "ERRAND")

    // Guards (POST: NULL)
    expect("G1 negación compuesta", "no voy a llevarme el currículum a la entrevista mañana", false)
    expect("G2 duda subjuntivo", "quizá me lleve el currículum a la entrevista", false)
    expect("G3 pretérito", "me llevé el currículum a la entrevista ayer", false)

    // Pines anti-overreach (POST: NULL deliberado)
    expect("P1 imperativo llévame fuera", "llévame el currículum a la entrevista mañana", false)
    expect("P2 reflexivo otro objeto fuera", "llevarme el informe a la entrevista mañana", false)
    expect("P3 reflexivo otro destino fuera", "llevarme el currículum a la oficina mañana", false)

    // Regresiones (POST: HIT intacto)
    expect("R1 no reflexiva c.1174", "llevar el currículum a la entrevista mañana", true, "ERRAND")
    expect("R2 taller c.684", "llevar el coche al taller mañana", true, "ERRAND")
    expect("R3 cole c.773", "llevar a los niños al cole mañana", true, "ERRAND")
    expect("R4 portátil c.1157", "llevar el portátil al trabajo mañana", true, "ERRAND")

    println("RESULTADO: $ok OK, $fail FALLOS")
    if (fail > 0) kotlin.system.exitProcess(1)
}

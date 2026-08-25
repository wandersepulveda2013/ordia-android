import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1076 (persistida): duda PLURAL «no sabemos si +
// infinitivo» → NULL (duda ≠ compromiso). Lateral ABIERTA c.1069/
// c.1070 (ÚLTIMA variante registrada de la familia de la duda)
// RESUELTA. Esperado: C1–C6 NULL (6 capturas medidas PRE sobre HEAD
// 48a0767), residual R1 sobrevive con confianza reducida (0.85−0.3=
// 0.55 ≥ umbral, doctrina de la familia), regresiones HIT (compromiso
// directo plural/singular, coma-cierra, envolventes fieles), guards
// NULL estables (3ª persona, pasado, presente, sin «si», sin acción,
// futuro plural FUERA), pins de la familia singular NULL.
var failures = 0
fun check(label: String, ok: Boolean, detail: String) {
    if (ok) println("OK   $label $detail") else { failures++; println("FAIL $label $detail") }
}
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun hit(l: String, t: String, k: ContextIntentKind) {
        val i = a(t)
        check(l, i != null && i.kind == k, "${i?.kind} ${i?.confidence} | ${i?.title} ← $t")
    }
    fun nul(l: String, t: String) {
        val i = a(t)
        check(l, i == null, "${i?.kind} ← $t")
    }
    nul("C1", "no sabemos si llamar a mamá")
    nul("C2", "no sabemos si ir al médico")
    nul("C3", "no sabemos si sacar al perro")
    nul("C4", "no sabemos si llamar a mamá mañana")
    nul("C5", "no sabemos muy bien si llamar a mamá")
    nul("C6", "No sabemos si llamar a mamá")
    run {
        val i = a("no sabemos si ir al médico mañana a las 9")
        check("R1", i != null && i.kind == ContextIntentKind.APPOINTMENT && i.confidence >= 0.45f,
            "${i?.kind} ${i?.confidence} ← residual 0.85-0.3=0.55")
    }
    hit("REG1", "llamar a mamá", ContextIntentKind.CALL)
    hit("REG2", "sabemos que llamar a mamá", ContextIntentKind.CALL)
    hit("REG3", "no sabemos si es buena idea, llamar a mamá", ContextIntentKind.CALL)
    hit("REG4", "tengo que llamar a mamá", ContextIntentKind.TASK)
    hit("REG5", "recuérdame llamar a mamá", ContextIntentKind.TASK)
    nul("G1", "no sabemos si ella llamará a mamá")
    nul("G2", "no sabemos si llamó ayer")
    nul("G3", "no sabemos si sabemos la respuesta")
    nul("G4", "no sabemos nada de mamá")
    nul("G5", "no sabemos si es buena idea")
    nul("G6", "no sabemos si llamaremos a mamá")
    nul("PIN1", "no sé si llamar a mamá")
    nul("PIN2", "no sé si debería llamar a mamá")
    nul("PIN3", "no sé muy bien si llamar a mamá")
    nul("PIN4", "no sé si llamaré a mamá")
    println(if (failures == 0) "POST OK (22 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

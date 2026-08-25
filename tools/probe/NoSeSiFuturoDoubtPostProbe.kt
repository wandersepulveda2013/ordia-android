import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1073 (persistida): duda «no sé si + FUTURO 1ª persona» →
// NULL (duda ≠ compromiso). Lateral ABIERTA c.1069/c.1070/c.1072
// RESUELTA. Esperado: C1–C9 NULL (9 capturas medidas PRE), regresiones
// HIT (compromiso directo con futuro, sin duda), coma-cierra HIT, guards
// NULL estables (3ª persona, pasado, presente «sé», plural).
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
    nul("C1", "no sé si llamaré a mamá")
    nul("C2", "no sé si podré ir al gimnasio")
    nul("C3", "no sé si llamaré a mamá mañana")
    nul("C4", "no sé si iré al médico mañana a las 9")
    nul("C5", "no sé muy bien si llamaré a mamá")
    nul("C6", "no sé si llamaré a mamá mañana a las 9")
    nul("C7", "no sé si llamaré a mamá a las 9")
    nul("C8", "No sé si llamaré a mamá")
    nul("C9", "no sé si tendré que llamar a mamá")
    hit("REG1", "llamaré a mamá", ContextIntentKind.CALL)
    hit("REG2", "sé que llamaré a mamá", ContextIntentKind.CALL)
    hit("REG3", "no sé si es buena idea, llamaré a mamá", ContextIntentKind.CALL)
    hit("REG4", "tengo que llamar a mamá", ContextIntentKind.TASK)
    hit("REG5", "recuérdame llamar a mamá", ContextIntentKind.TASK)
    nul("G1", "no sé si ella llamará a mamá")
    nul("G2", "no sé si llamará mamá")
    nul("G3", "no sé si él llamó ayer")
    nul("G4", "no sé si sé la respuesta")
    nul("G5", "no sabemos si llamaremos a mamá")
    nul("G6", "no sé si habré llamado a mamá")
    nul("PIN1", "no sé si llamar a mamá")
    nul("PIN2", "no sé si debería llamar a mamá")
    nul("PIN3", "no sé muy bien si llamar a mamá")
    println(if (failures == 0) "POST OK (24 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

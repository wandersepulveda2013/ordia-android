import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1063 (persistida): objeto «medicamento»/«medicina»
// del piso dativo de mascota (lateral ABIERTA (2) c.1012) RESUELTO.
// Esperado: C1–C6 HIT (HOUSEHOLD 0.45, C5 dueAt), C7 TASK envolvente
// (RED-pass medido PRE vía candado genérico), guards NULL (negación
// inmediata, pasado, hedge), negación envolvente TASK (comportamiento
// ESTABLE transversal — pin, lateral ABIERTA), regresiones HIT
// (c.1012/c.1059/c.1011), FUERA NULL (destinatario humano, sin ancla,
// sintagma nominal).
var failures = 0
fun check(label: String, ok: Boolean, detail: String) {
    if (ok) println("OK   $label $detail") else { failures++; println("FAIL $label $detail") }
}
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun hit(l: String, t: String, k: ContextIntentKind, due: Boolean? = null) {
        val i = a(t)
        check(l, i != null && i.kind == k && (due == null || (i.dueAt != null) == due),
            "${i?.kind} ${i?.confidence} | ${i?.title} | dueAt=${i?.dueAt != null} ← $t")
    }
    fun nul(l: String, t: String) {
        val i = a(t)
        check(l, i == null, "${i?.kind} ← $t")
    }
    hit("C1", "darle el medicamento al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("C2", "darle un medicamento al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C3", "darle la medicina al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("C4", "dale el medicamento al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("C5", "darle el medicamento al perro a las 9", ContextIntentKind.HOUSEHOLD, true)
    hit("C6", "vale, darle el medicamento al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C7", "tengo que darle el medicamento al perro", ContextIntentKind.TASK)
    nul("G1", "no darle el medicamento al perro")
    nul("G2", "le di el medicamento al perro ayer")
    nul("G3", "quizá darle el medicamento al perro")
    hit("G4", "tengo que no darle el medicamento al perro", ContextIntentKind.TASK)
    hit("REG1", "darle la pastilla al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("REG2", "dale una pastilla al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("REG3", "ponerle la vacuna al perro", ContextIntentKind.HOUSEHOLD, false)
    nul("FUERA1", "darle el medicamento al niño")
    nul("FUERA2", "darle el medicamento")
    nul("FUERA3", "el medicamento del perro")
    println(if (failures == 0) "POST OK (17 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

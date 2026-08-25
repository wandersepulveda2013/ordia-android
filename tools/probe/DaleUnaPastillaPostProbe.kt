import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1059 (persistida): indefinido dativo «dale una
// pastilla al perro» RESUELTO. Esperado: C1–C6 HIT (C1–C4 HOUSEHOLD,
// C5/C6 dueAt), C7 TASK envolvente, guards NULL (negación inmediata,
// pasado, hedge), negación envolvente TASK (comportamiento estable
// transversal — pin, lateral ABIERTA), regresiones HIT, FUERA NULL
// (humano indefinido + sintagma nominal).
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
    hit("C1", "dale una pastilla al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("C2", "darle una pastilla al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C3", "dale unas pastillas al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("C4", "darle una pastilla a mi gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C5", "dale una pastilla al perro a las 9", ContextIntentKind.HOUSEHOLD, true)
    hit("C6", "darle una pastilla al gato mañana", ContextIntentKind.HOUSEHOLD, true)
    hit("C7", "tengo que darle una pastilla al perro", ContextIntentKind.TASK)
    nul("G1", "no darle una pastilla al perro")
    nul("G2", "le di una pastilla al perro")
    nul("G3", "quizá dale una pastilla al perro")
    hit("G4", "tengo que no darle una pastilla al perro", ContextIntentKind.TASK)
    hit("REG1", "dale la pastilla al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("REG2", "darle las pastillas al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("REG3", "ponerle una vacuna al perro", ContextIntentKind.HOUSEHOLD, false)
    nul("FUERA1", "dale una pastilla al niño")
    nul("FUERA2", "la pastilla del perro")
    println(if (failures == 0) "POST OK (16 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

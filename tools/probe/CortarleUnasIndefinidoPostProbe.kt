import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1061 (persistida): indefinido dativo uñas «cortarle
// una/unas uña(s) al gato/perro» RESUELTO. Esperado: C1–C6 HIT
// (C1–C4/C6 HOUSEHOLD sin dueAt, C5 dueAt), C7 TASK envolvente
// (RED-pass medido PRE), guards NULL (negación inmediata, pasado,
// hedge), negación envolvente TASK (comportamiento ESTABLE
// transversal medido sobre el piso definido c.1015 — pin, lateral
// ABIERTA), regresiones HIT (c.1015/c.1024/c.1059), FUERA NULL
// (humano indefinido, sin ancla, sintagma nominal).
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
    hit("C1", "cortarle una uña al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C2", "cortarle unas uñas al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("C3", "cortarle unas uñas a mi gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C4", "cortar una uña del gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C5", "cortarle una uña al perro mañana", ContextIntentKind.HOUSEHOLD, true)
    hit("C6", "vale, cortarle unas uñas al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("C7", "tengo que cortarle una uña al perro", ContextIntentKind.TASK)
    nul("G1", "no cortarle una uña al gato")
    nul("G2", "le corté una uña al gato")
    nul("G3", "quizá cortarle unas uñas al gato")
    hit("G4", "tengo que no cortarle una uña al gato", ContextIntentKind.TASK)
    hit("REG1", "cortarle las uñas al gato", ContextIntentKind.HOUSEHOLD, false)
    hit("REG2", "cortar las uñas del gato", ContextIntentKind.HOUSEHOLD, false)
    hit("REG3", "darle una pastilla al perro", ContextIntentKind.HOUSEHOLD, false)
    nul("FUERA1", "cortarle una uña al niño")
    nul("FUERA2", "cortarle una uña")
    nul("FUERA3", "una uña del gato")
    println(if (failures == 0) "POST OK (17 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1062 (persistida): indefinido humano «tomar una/unas
// pastilla(s)», «tomar un medicamento» (piso c.859) RESUELTO.
// Esperado: C1–C6 HIT (TASK 0.45, C5 dueAt), C7 TASK envolvente
// (RED-pass medido PRE vía candado genérico), guards NULL (negación
// inmediata, pasado, hedge), negación envolvente TASK (comportamiento
// ESTABLE transversal medido sobre el piso definido c.859 — pin,
// lateral ABIERTA), regresiones HIT (c.765/c.770/c.859), FUERA NULL
// (sintagma nominal, objeto bivalente, interrogativa).
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
    hit("C1", "tomar una pastilla", ContextIntentKind.TASK, false)
    hit("C2", "tomarme una pastilla", ContextIntentKind.TASK, false)
    hit("C3", "tomar unas pastillas", ContextIntentKind.TASK, false)
    hit("C4", "tomar un medicamento", ContextIntentKind.TASK, false)
    hit("C5", "tomar una pastilla a las 9", ContextIntentKind.TASK, true)
    hit("C6", "vale, tomar una pastilla", ContextIntentKind.TASK, false)
    hit("C7", "tengo que tomar una pastilla", ContextIntentKind.TASK)
    nul("G1", "no tomar una pastilla")
    nul("G2", "me tomé una pastilla")
    nul("G3", "quizá tome una pastilla")
    hit("G4", "tengo que no tomar una pastilla", ContextIntentKind.TASK)
    hit("REG1", "tomar la pastilla", ContextIntentKind.TASK, false)
    hit("REG2", "tomarme la medicina", ContextIntentKind.TASK, false)
    hit("REG3", "tomar la medicación", ContextIntentKind.TASK, false)
    nul("FUERA1", "una pastilla para el dolor")
    nul("FUERA2", "tomar una copa")
    nul("FUERA3", "cómo tomar una pastilla")
    println(if (failures == 0) "POST OK (17 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

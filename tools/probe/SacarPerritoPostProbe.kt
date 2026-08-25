import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind

// Sonda POST c.1055 (persistida): diminutivo perro «sacar al perrito» RESUELTO.
// Esperado: C1–C5 HIT HOUSEHOLD (C5 dueAt), C6 TASK envolvente, guards NULL,
// regresiones HIT, FUERA NULL (vía pasear diminutivo + destinatario humano).
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
    hit("C1", "sacar al perrito", ContextIntentKind.HOUSEHOLD, false)
    hit("C2", "sacar a la perrita", ContextIntentKind.HOUSEHOLD, false)
    hit("C3", "sacar a mi perrito", ContextIntentKind.HOUSEHOLD, false)
    hit("C4", "sacar el perrito", ContextIntentKind.HOUSEHOLD, false)
    hit("C5", "sacar al perrito a las 8", ContextIntentKind.HOUSEHOLD, true)
    hit("C6", "tengo que sacar al perrito", ContextIntentKind.TASK)
    nul("G1", "no sacar al perrito hoy")
    nul("G2", "no voy a sacar al perrito")
    nul("G3", "saqué al perrito ayer")
    nul("G4", "quizá saque al perrito mañana")
    hit("REG1", "sacar al perro a las 8", ContextIntentKind.HOUSEHOLD, true)
    hit("REG2", "sacar al gatito a las 8", ContextIntentKind.HOUSEHOLD, true)
    nul("FUERA1", "pasear al perrito mañana")
    nul("FUERA2", "sacar al bebé mañana")
    println(if (failures == 0) "POST OK (14 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

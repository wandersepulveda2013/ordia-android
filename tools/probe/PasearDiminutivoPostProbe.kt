import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1057 (persistida): vía pasear diminutiva RESUELTA.
// Esperado: C1–C5b HIT HOUSEHOLD (C5/C5b dueAt), C6 TASK envolvente,
// guards NULL, regresiones HIT, FUERA NULL (bivalente + humano).
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
    hit("C1", "pasear al perrito", ContextIntentKind.HOUSEHOLD, false)
    hit("C2", "pasear a la perrita", ContextIntentKind.HOUSEHOLD, false)
    hit("C3", "pasear a mi gatito", ContextIntentKind.HOUSEHOLD, false)
    hit("C4", "pasear al gatito", ContextIntentKind.HOUSEHOLD, false)
    hit("C5", "pasear al perrito a las 8", ContextIntentKind.HOUSEHOLD, true)
    hit("C5b", "pasear al gatito a las 9", ContextIntentKind.HOUSEHOLD, true)
    hit("C6", "tengo que pasear al perrito", ContextIntentKind.TASK)
    nul("G1", "no pasear al perrito hoy")
    nul("G2", "no voy a pasear al perrito")
    nul("G3", "paseé al perrito ayer")
    nul("G4", "quizá pasee al perrito mañana")
    hit("REG1", "pasear al perro", ContextIntentKind.HOUSEHOLD, false)
    hit("REG2", "pasear a la gata", ContextIntentKind.HOUSEHOLD, false)
    hit("REG3", "pasear al perro a las 8", ContextIntentKind.HOUSEHOLD, true)
    nul("FUERA1", "salir a pasear mañana")
    nul("FUERA2", "pasear al bebé mañana")
    println(if (failures == 0) "POST OK (16 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1071 (persistida): TRANSVERSAL interrogativa «cómo +
// infinitivo» al inicio → NULL (pregunta how-to ≠ compromiso). Lateral
// ABIERTA c.1062/c.1066 RESUELTA. Esperado: Q1–Q15 NULL (15 capturas
// medidas PRE en toda la familia de pisos de posición libre), regresiones
// HIT (compromiso directo idéntico sin «cómo»), «cómo» subordinado HIT
// (compromiso legítimo intacto), guards NULL estables (chat, presente,
// pisos anclados ya NULL estructuralmente).
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
    nul("Q1", "cómo darle la pastilla al perro")
    nul("Q2", "cómo darle el medicamento al perro")
    nul("Q3", "cómo ponerle la vacuna al gato")
    nul("Q4", "cómo cortarle las uñas al gato")
    nul("Q5", "cómo sacar al perro")
    nul("Q6", "cómo darle una pastilla al gato")
    nul("Q7", "cómo llamar a mamá")
    nul("Q8", "cómo ir al médico")
    nul("Q9", "cómo hacer la compra")
    nul("Q10", "cómo ir al gimnasio")
    nul("Q11", "cómo cortarse el pelo")
    nul("Q12", "¿cómo darle la pastilla al perro?")
    nul("Q13", "cómo sacar al perro mañana")
    nul("Q14", "cómo bañar al perro")
    nul("Q15", "cómo darle la pastilla al perro a las 9")
    hit("REG1", "darle la pastilla al perro", ContextIntentKind.HOUSEHOLD)
    hit("REG2", "llamar a mamá", ContextIntentKind.CALL)
    hit("REG3", "sacar al perro", ContextIntentKind.HOUSEHOLD)
    hit("REG4", "comprar pan", ContextIntentKind.SHOPPING)
    hit("REG5", "ir al gimnasio", ContextIntentKind.EXERCISE)
    hit("SUB1", "recuérdame cómo hacer la compra", ContextIntentKind.TASK)
    hit("SUB2", "tengo que pensar cómo sacar al perro", ContextIntentKind.TASK)
    nul("G1", "cómo estás")
    nul("G2", "cómo va el proyecto")
    nul("G3", "cómo llego al trabajo")
    nul("G4", "cómo tomar una pastilla")
    nul("G5", "cómo comprar pan")
    nul("G6", "cómo pagar la luz")
    println(if (failures == 0) "POST OK (28 checks)" else "POST FAILURES: $failures")
    if (failures > 0) kotlin.system.exitProcess(1)
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind
// Sonda POST c.1078 (persistida): duda «no sabemos si + MODAL PLURAL
// + infinitivo» → NULL (duda ≠ compromiso). Lateral ABIERTA c.1076
// RESUELTA. Esperado: V1–V7 NULL (7 capturas medidas PRE sobre HEAD
// 01d9e0e con sonda efímera /tmp/probe1078), volteo aceptado W1 NULL
// («deberíamos haber llamado» — arrepentimiento pasado, consistente
// con el pin singular c.1070), residual R1 sobrevive con confianza
// reducida (0.85−0.3=0.55 ≥ umbral, doctrina de la familia),
// regresiones HIT (coma-cierra, sin «no», modal plural sin duda, 3ª
// persona plural FUERA), guard NULL estable (presente plural), pins
// de la familia NULL (c.1070 singular modal, c.1076 plural
// infinitivo), envolventes fieles y compromiso directo HIT.
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
    nul("V1", "no sabemos si deberíamos llamar a mamá")
    nul("V2", "no sabemos si deberíamos ir al médico")
    nul("V3", "no sabemos si podríamos sacar al perro")
    nul("V4", "no sabemos si tendríamos que pagar la luz")
    nul("V5", "no sabemos muy bien si deberíamos llamar a mamá")
    nul("V6", "No sabemos si deberíamos llamar a mamá")
    nul("V7", "no sabemos si habríamos que llamar a mamá")
    nul("W1", "no sabemos si deberíamos haber llamado a mamá")
    run {
        val i = a("no sabemos si deberíamos ir al médico mañana a las 9")
        check("R1", i != null && i.kind == ContextIntentKind.APPOINTMENT &&
            i.confidence >= 0.45f && i.confidence < 0.75f,
            "${i?.kind} ${i?.confidence} ← residual 0.85-0.3=0.55")
    }
    hit("G1", "no sabemos si deberíamos, llamar a mamá", ContextIntentKind.CALL)
    hit("G3", "sabemos que deberíamos llamar a mamá", ContextIntentKind.CALL)
    hit("G4", "deberíamos llamar a mamá", ContextIntentKind.CALL)
    hit("L1", "no sabemos si deberían llamar a mamá", ContextIntentKind.CALL)
    nul("L2", "no sabemos si podemos llamar a mamá")
    nul("P1", "no sé si debería llamar a mamá")
    nul("P2", "no sabemos si llamar a mamá")
    hit("E1", "tengo que llamar a mamá", ContextIntentKind.TASK)
    hit("E2", "recuérdame llamar a mamá", ContextIntentKind.TASK)
    hit("E3", "llamar a mamá", ContextIntentKind.CALL)
    if (failures == 0) println("POST OK (19 checks)") else println("POST FAILURES: $failures")
}

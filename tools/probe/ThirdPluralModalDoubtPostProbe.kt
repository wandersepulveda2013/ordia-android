import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind

// POST c.1080 (persistida): duda «no sabemos si + modal 3ª plural +
// infinitivo» descartada, sin colaterales. Complementa el test delta
// (que sólo pina supervivencia ≥0.45 estilo c.1076): aquí se verifica
// el valor EXACTO del residual (0.55), el volteo aceptado W1, la
// fidelidad de los guards/pins y la 2ª plural FUERA intacta. Ejecutar
// con: bash tools/run_probe.sh tools/probe/ThirdPluralModalDoubtPostProbe.kt
fun main() {
    var ok = 0
    var ko = 0
    fun check(label: String, cond: Boolean) {
        if (cond) { ok++; println("OK  $label") } else { ko++; println("KO  $label") }
    }
    fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )
    fun isNull(label: String, text: String) = check(label, analyze(text) == null)
    fun isKind(label: String, text: String, kind: ContextIntentKind) {
        val i = analyze(text)
        check(label, i != null && i.kind == kind)
    }
    isNull("V1 no sabemos si deberían llamar a mamá", "no sabemos si deberían llamar a mamá")
    isNull("V2 no sabemos si deberían ir al médico", "no sabemos si deberían ir al médico")
    isNull("V3 no sabemos si podrían sacar al perro", "no sabemos si podrían sacar al perro")
    isNull("V4 no sabemos si tendrían que pagar la luz", "no sabemos si tendrían que pagar la luz")
    isNull("V5 muy bien intercalado", "no sabemos muy bien si deberían llamar a mamá")
    isNull("V6 mayúscula", "No sabemos si deberían llamar a mamá")
    isNull("V7 habrían que", "no sabemos si habrían que llamar a mamá")
    isNull("W1 volteo aceptado deberían haber llamado", "no sabemos si deberían haber llamado a mamá")
    val r1 = analyze("no sabemos si deberían ir al médico mañana a las 9")
    check("R1 residual APPOINTMENT 0.55 exacto", r1 != null && r1.kind == ContextIntentKind.APPOINTMENT && r1.confidence == 0.55f)
    isKind("G1 coma cierra fiel", "no sabemos si deberían, llamar a mamá", ContextIntentKind.CALL)
    isKind("G3 sabemos que deberían fiel", "sabemos que deberían llamar a mamá", ContextIntentKind.CALL)
    isKind("G4 deberían sin duda fiel", "deberían llamar a mamá", ContextIntentKind.CALL)
    isKind("G5 2ª plural sabéis FUERA fiel", "no sabéis si deberíais llamar a mamá", ContextIntentKind.CALL)
    isNull("G6 pretérito plural NULL estable", "no sabemos si llamaron a mamá ayer")
    isNull("PIN c.1070 modal singular", "no sé si debería llamar a mamá")
    isNull("PIN c.1076 plural infinitivo", "no sabemos si llamar a mamá")
    isNull("PIN c.1078 1ª plural modal", "no sabemos si deberíamos llamar a mamá")
    isKind("envolvente tengo que", "tengo que llamar a mamá", ContextIntentKind.TASK)
    isKind("directo llamar a mamá", "llamar a mamá", ContextIntentKind.CALL)
    println("RESULT: $ok OK / $ko KO")
    if (ko > 0) throw AssertionError("$ko assertions fallaron")
}

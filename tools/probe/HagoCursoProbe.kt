// Sonda POST persistida c.1188 — «hago (el)? curso(s)» (lateral del
// piso c.1152, verbo aditivo «hago» 1ª persona presente; espejo de
// HagoMudanzaProbe.kt c.1171). Ejecutar:
// bash tools/run_probe.sh tools/probe/HagoCursoProbe.kt
import com.ordia.app.context.*

fun main() {
    var misses = 0
    fun check(id: String, frase: String, expectHit: Boolean, expectKind: ContextIntentKind? = null) {
        val i = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, frase, 1000))
        val hit = i != null
        val kindOk = expectKind == null || i?.kind == expectKind
        val ok = hit == expectHit && kindOk
        if (!ok) misses++
        println(
            (if (ok) "OK  " else "MISS") + " $id -> " +
                (if (i == null) "NULL" else "${i.kind} ${i.confidence} \"${i.title}\" dueAt=${i.dueAt}")
        )
    }

    // Capturas c.1188 (HIT TASK):
    check("C1", "hago el curso de prevención mañana", true, ContextIntentKind.TASK)
    check("C2", "hago mi curso de inglés el jueves", true, ContextIntentKind.TASK)
    check("C3", "hago un curso de cocina esta noche", true, ContextIntentKind.TASK)
    check("C4", "hago el curso", true, ContextIntentKind.TASK)
    check("C5", "el sábado hago el curso", true, ContextIntentKind.TASK)
    check("C6", "vale, hago el curso de prevención", true, ContextIntentKind.TASK)
    // Guards NULL (negación; pretérito; duda-subjuntivo; subjuntivo;
    // 3ª persona; condicional; nominal; futuro FUERA — lateral; hedge;
    // básico «hago»):
    check("G1", "no hago el curso mañana", false)
    check("G2", "hice el curso ayer", false)
    check("G3", "quizá haga el curso", false)
    check("G4", "haga el curso", false)
    check("G5", "él hace el curso mañana", false)
    check("G6", "haría el curso si tuviera tiempo", false)
    check("G7", "el curso de prevención", false)
    check("G8", "haré el curso de prevención la semana que viene", false)
    check("G9", "no sé si hago el curso", false)
    check("G10", "hago", false)
    // Regresiones (HIT; pin byte-idéntico):
    check("R1", "hacer el curso de prevención mañana", true)
    check("R2", "hacer la mudanza el sábado", true)
    check("R3", "hago la mudanza el sábado", true)
    check("R4", "hacer el check-in del vuelo mañana", true)
    check("R5", "estudiar el examen mañana", true)
    // Envolvente (canario):
    check("E1", "tengo que hacer el curso el lunes", true)

    if (misses > 0) {
        println("PROBE FAIL ($misses misses)")
        kotlin.system.exitProcess(1)
    }
    println("PROBE OK (0 misses) — «hago el curso» c.1188 FIXED; piso c.1152 + plantilla extensión aditiva")
}

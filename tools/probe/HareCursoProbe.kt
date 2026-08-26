// Sonda POST persistida c.1196 — «haré (el)? curso(s)» (futuro 1ª
// persona, plan comprometido; hermano del ciclo c.1188 «hago»; espejo
// de HareMudanzaProbe.kt c.1171). Ejecutar:
// bash tools/run_probe.sh tools/probe/HareCursoProbe.kt
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
    check("C1", "haré el curso de prevención mañana", true, ContextIntentKind.TASK)
    check("C2", "haré el curso de inglés el jueves", true, ContextIntentKind.TASK)
    check("C3", "haré un curso de cocina esta noche", true, ContextIntentKind.TASK)
    check("C4", "haré el curso", true, ContextIntentKind.TASK)
    check("C5", "el sábado haré el curso", true, ContextIntentKind.TASK)
    check("C6", "vale, haré el curso de prevención", true, ContextIntentKind.TASK)
    // Guards NULL (negación; pretérito; duda-subjuntivo; subjuntivo;
    // 3ª persona; condicional; nominal; futuro FUERA — lateral; hedge;
    // básico «hago»):
    check("G1", "no haré el curso mañana", false)
    check("G2", "hice el curso ayer", false)
    check("G3", "quizá haga el curso", false)
    check("G4", "haga el curso", false)
    check("G5", "él hará el curso mañana", false)
    check("G6", "haría el curso si tuviera tiempo", false)
    check("G7", "el curso de prevención", false)
    check("G8", "hago el curso de prevención la semana que viene", true, ContextIntentKind.TASK)
    check("G9", "no sé si haré el curso", false)
    check("G10", "haré", false)
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
    println("PROBE OK (0 misses) — «haré el curso» c.1196 FIXED; piso+plantilla «hacer|hago|haré» invertidos a captura")
}

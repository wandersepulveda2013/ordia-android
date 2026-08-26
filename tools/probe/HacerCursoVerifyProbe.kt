// Sonda de VERIFICACIÓN c.1183 — «hacer el curso (de prevención)»:
// la candidata (c) de la auditoría c.1173 estaba OBSOLETA — el piso
// «hacer (el)? curso(s)» + plantilla matchHacerCurso + keyword «curso»
// existen desde c.1152 (clase DECIMOSÉPTIMA vida-laboral). Esta sonda
// documenta el estado real medido (motor real vía tools/run_probe.sh)
// para cerrar la fila y evitar reintentos ciegos futuros (mismo patrón
// que la fila (c) c.1148 «YA cubierto — verificado»).
// Ejecutar: bash tools/run_probe.sh tools/probe/HacerCursoVerifyProbe.kt
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

    // Capturas heredadas c.1152 (HIT TASK):
    check("C1", "hacer el curso de prevención mañana", true, ContextIntentKind.TASK)
    check("C2", "hacer el curso online esta semana", true, ContextIntentKind.TASK)
    check("C3", "tengo que hacer el curso el lunes", true, ContextIntentKind.TASK)
    check("C4", "mañana hacer el curso", true, ContextIntentKind.TASK)
    // Guards NULL (lookbehind negación; pasado «hice»; subjuntivo «haga»; hedge c.1152 medido):
    check("G1", "no hacer el curso", false)
    check("G2", "hice el curso ayer", false)
    check("G3", "quizá haga el curso", false)
    check("G4", "no sé si hacer el curso", false)
    // Pines estables de ámbito (comportamiento medido, sin cambio previsto):
    check("N1", "el curso empieza en septiembre", false)
    check("N2", "este curso es difícil", false)
    // Regresiones hermanas (HIT):
    check("R1", "hacer el check-in del vuelo mañana", true)
    check("R2", "hacer la mudanza el sábado", true)
    check("R3", "estudiar para el examen de mates", true)
    // Envolvente (canario):
    check("E1", "recuérdame hacer el curso de prevención mañana", true)

    if (misses > 0) {
        println("PROBE FAIL ($misses misses)")
        kotlin.system.exitProcess(1)
    }
    println("PROBE OK (0 misses) — «hacer el curso» c.1152 VERIFICADO vigente; candidata (c) c.1173 OBSOLETA (duplicada)")
}

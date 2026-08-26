import com.ordia.app.context.*

private val misses = mutableListOf<String>()
private fun check(label: String, text: String, expectHit: Boolean, missesOut: MutableList<String> = misses) {
    val intent = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000))
    val hit = intent != null
    if (hit == expectHit) {
        if (expectHit) println("OK $label -> ${intent!!.kind} ${intent.confidence}")
        else println("OK $label -> NULL")
    } else {
        val got = if (hit) "${intent!!.kind} ${intent.confidence}" else "NULL"
        missesOut.add("$label esperado hit=$expectHit obtenido $got")
        println("MISS $label -> $got")
    }
}

fun main() {
    check("C1", "llevar a mi abuela al médico mañana", true)
    check("C2", "llevar a la abuela al médico", true)
    check("C3", "llevo a mi abuelo al médico el lunes", true)
    check("C4", "llevar a los abuelos al doctor mañana", true)
    check("G1", "no llevar a mi abuela al médico", false)
    check("G2", "llevé a mi abuelo al médico ayer", false)
    check("P1", "llevar a mi mujer al médico mañana", false)
    check("P2", "llevar a mi marido al médico mañana", false)
    check("R1", "llevar a mamá al médico mañana", true)
    check("R2", "llevar a mi hijo al médico mañana", true)
    if (misses.isEmpty()) println("PROBE OK (0 misses) — abuelos piso médico c.1184 VERIFICADOS")
    else println("PROBE FAIL (${misses.size} misses): $misses")
}

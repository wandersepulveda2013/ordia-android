// Sonda c.1172 (PRE/POST): lateral P2 del piso escolar c.1170/c.773 —
// objeto posesivo singular «a mi/tu/su hija/o». El alternador del piso
// escolar cubre `los|las|mis|tus|sus` pero NO el singular `mi|tu|su` (la
// plantilla médica matchMedicalRun SÍ lo admite desde c.776 — asimetría
// hermana), y el objeto es solo `niñ[oa]s?`, no `hij[oa]s?`. El hermano
// pinó P2 «llevar a mi hija a la fiesta del cole el viernes» como NULL
// deliberado anti-overreach al cerrar c.1170 (`d00b0acb`) — lateral
// genuina: un padre escribe «a mi hija» con la misma intención.
// Extensión ACOTADA propuesta: alternativa de objeto
// `a\s+(?:mi|tu|su)\s+hij[oa]s?` (NO abre «a la hija» sin posesivo) en
// `ERRAND_SCHOOL_RUN_FLOOR` + plantilla `matchSchoolRun` lockstep
// (lección c.616). CERO keywords nuevas.
// Uso: bash tools/run_probe.sh tools/probe/SchoolRunMiHijoProbe.kt
// Esperado PRE-fix: misses EXACTOS en C1-C4 (4 misses), el resto OK.
// Esperado POST-fix: «0 misses» (exit 0).

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextIntentKind

private fun check(label: String, text: String, expectHit: Boolean, misses: MutableList<String>) {
    val intent = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000))
    val hit = intent != null
    if (hit == expectHit) {
        if (expectHit) {
            val kind = intent!!.kind
            val errand = kind == ContextIntentKind.ERRAND
            println("OK   $label  -> $kind ${intent.confidence}  \"${intent.title}\"" + (if (errand) "" else "  [KIND!]"))
            if (!errand) misses.add("$label kind inesperado: $kind")
        } else {
            println("OK   $label  -> NULL")
        }
    } else {
        val got = if (hit) "${intent!!.kind} ${intent.confidence}" else "NULL"
        misses.add("$label esperado hit=$expectHit, obtenido $got")
        println((if (expectHit) "MISS " else "GUARD-HIT ") + "$label  -> $got")
    }
}

fun main() {
    val misses = mutableListOf<String>()

    // Capturas: PRE NULL (4 misses), POST HIT ERRAND.
    check("C1", "llevar a mi hija a la fiesta del cole el viernes", true, misses)
    check("C2", "llevar a mi hijo al colegio mañana", true, misses)
    check("C3", "llevo a mi hija a la guardería esta tarde", true, misses)
    check("C4", "llevar a mi hijo a la fiesta del colegio el sábado", true, misses)

    // Guards negación: NULL siempre.
    check("G1", "no llevar a mi hija a la fiesta del cole", false, misses)
    check("G2", "no llevo a mi hijo al colegio", false, misses)

    // Regresiones: HIT esperado (misma región de regex, ya cubiertas).
    check("R1", "llevar a los niños al colegio mañana", true, misses)
    check("R2", "llevar a los niños a la fiesta del cole el viernes", true, misses)
    check("R3", "llevar a la niña al médico mañana", true, misses)
    check("R4", "llevar el portátil al trabajo mañana", true, misses)
    check("R5", "llevar a mi niña al médico mañana", true, misses)

    // Captura adicional: «a la hija» sin posesivo casa vía el alternador
    // ` la\s+` + objeto hij[oa]s? — DELIBERADO, coherente con el piso
    // aeropuerto c.1158 (mismo sub-patrón). PRE NULL (miss #5), POST HIT.
    check("C5", "llevar a la hija al colegio mañana", true, misses)

    // Pines anti-overreach: NULL esperado (fuera del alcance acotado).
    check("P2", "llevar a mi hija a la fiesta de cumpleaños el sábado", false, misses)
    // RE-PIN legítimo c.1176: cerrada exactamente esa forma.
    check("R7", "llevar a mi hijo al médico mañana", true, misses)
    check("P6", "llevar a mi mujer al colegio mañana", false, misses)

    // Regresión PRE-existente: el piso aeropuerto c.1158 ya admite el
    // objeto de parentesco completo (mi/tu/su + hij[oa]s?) — verde en PRE.
    check("R6", "llevar a mi hijo al aeropuerto mañana", true, misses)

    // Envolvente: camino genérico «tengo que…» debe seguir capturando.
    val env = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que llevar a mi hija a la fiesta del cole el viernes", 1000)
    )
    if (env != null) {
        println("OK   P5  -> ${env.kind} ${env.confidence}  \"${env.title}\"")
    } else {
        misses.add("P5 envolvente: NULL")
        println("MISS P5  -> NULL")
    }

    println(if (misses.isEmpty()) "\nPROBE OK (0 misses) — piso escolar objeto «mi/tu/su hij[oa]s?» + plantilla matchSchoolRun VERIFICADOS"
            else "\nPROBE FAIL (${misses.size} misses)")
    if (misses.isNotEmpty()) kotlin.system.exitProcess(1)
}

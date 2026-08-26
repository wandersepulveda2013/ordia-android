// Sonda c.1176 (PRE/POST): lateral espejo P2/P3 del piso médico c.776,
// registrada al cerrar c.1172 — objeto hij[oa]s?. El piso
// ERRAND_MEDICAL_RUN_FLOOR ya admite el posesivo singular mi|tu|su en el
// alternador (desde c.776: «llevar a mi niña al médico») pero el objeto
// es SOLO niñ[oa]s? — asimetría con el piso escolar (cerrada en c.1172:
// niñ[oa]s?|hij[oa]s?) y con el aeropuerto c.1158 (parentesco completo).
// «llevar a mi hijo al médico mañana» caía a NULL (medido como pin P3 de
// la sonda SchoolRunMiHijoProbe POST c.1172).
// Fix propuesto lockstep 2 puntos (lección c.616; CERO keywords nuevas):
// objeto niñ[oa]s? → (?:niñ[oa]s?|hij[oa]s?) en el piso + MISMO objeto en
// matchMedicalRun. UNA forma por ciclo: «llevar a mamá al médico» (otro
// parentesco) FUERA pineado (deliberado c.776).
// Uso: bash tools/run_probe.sh tools/probe/MedicalRunMiHijoProbe.kt
// Esperado PRE-fix: misses EXACTOS en C1-C6 (6 misses), el resto OK.
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

    // Capturas: PRE NULL (6 misses), POST HIT ERRAND.
    check("C1", "llevar a mi hijo al médico mañana", true, misses)
    check("C2", "llevar a mi hija al médico el viernes", true, misses)
    check("C3", "llevo a mi hijo al pediatra esta tarde", true, misses)
    check("C4", "llevar a tu hija al dentista mañana", true, misses)
    check("C5", "llevar a su hijo al hospital el lunes", true, misses)
    check("C6", "llevar a los hijos al médico mañana", true, misses)

    // Guards: NULL siempre (negación, pasado, duda subjuntivo).
    check("G1", "no llevar a mi hijo al médico", false, misses)
    check("G2", "no llevo a mi hija al médico", false, misses)
    check("G3", "llevé a mi hijo al médico ayer", false, misses)
    check("G4", "quizá lleve a mi hijo al médico", false, misses)

    // Pines anti-overreach: NULL esperado (fuera del alcance acotado).
    // P1: otro parentesco — FUERA deliberado c.776 («llevar a mamá…»).
    // RE-PIN legítimo c.1178: cerrada exactamente esa forma.
    check("R7", "llevar a mamá al médico mañana", true, misses)
    // P2: destino no médico con objeto hijo (sin piso aplicable).
    check("P2", "llevar a mi hijo al banco mañana", false, misses)

    // Regresiones: HIT esperado (misma región de regex, ya cubiertas).
    check("R1", "llevar a la niña al médico mañana", true, misses)
    check("R2", "llevar a mi niña al médico mañana", true, misses)
    check("R3", "llevar a los niños al colegio mañana", true, misses)
    check("R4", "llevar a mi hija a la fiesta del cole el viernes", true, misses)
    check("R5", "llevar a mi hijo al aeropuerto mañana", true, misses)
    check("R6", "llevar el portátil al trabajo mañana", true, misses)

    // Envolvente: camino genérico «recuérdame…» debe seguir capturando.
    val env = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a mi hijo al médico mañana", 1000)
    )
    if (env != null) {
        println("OK   E1  -> ${env.kind} ${env.confidence}  \"${env.title}\"")
    } else {
        misses.add("E1 envolvente: NULL")
        println("MISS E1  -> NULL")
    }

    println(if (misses.isEmpty()) "\nPROBE OK (0 misses) — piso médico objeto hij[oa]s? + plantilla matchMedicalRun VERIFICADOS"
            else "\nPROBE FAIL (${misses.size} misses)")
    if (misses.isNotEmpty()) kotlin.system.exitProcess(1)
}

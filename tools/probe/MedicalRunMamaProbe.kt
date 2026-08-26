// Sonda c.1178 (PRE/POST): lateral de c.1176 — parentesco adulto
// directo del piso médico c.776: «llevar a mamá/papá/madre/padre al
// médico». El pin «llevar a mamá al médico» de c.776 fue deliberado
// SOLO por doctrina una-forma-por-ciclo (no por falsedad); P1 evitar
// olvidos: la cita médica del padre/madre es la diligencia del cuidador
// adulto. El piso (tras c.1176) admite objeto niñ[oa]s?|hij[oa]s? con
// alternador mi|tu|su; falta el parentesco adulto directo.
// Fix propuesto lockstep 2 puntos (lección c.616; CERO keywords
// nuevas): objeto + mam[áa]|pap[áa]|madre|padre en
// ERRAND_MEDICAL_RUN_FLOOR + matchMedicalRun. UNA forma por ciclo:
// abuelos/esposa FUERA pineados.
// Uso: bash tools/run_probe.sh tools/probe/MedicalRunMamaProbe.kt
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
    check("C1", "llevar a mamá al médico mañana", true, misses)
    check("C2", "llevar a papá al médico el viernes", true, misses)
    check("C3", "llevo a mi madre al médico esta tarde", true, misses)
    check("C4", "llevar a mi padre al hospital el lunes", true, misses)
    check("C5", "llevar a tu madre al dentista mañana", true, misses)
    check("C6", "llevar a la madre al médico mañana", true, misses)

    // Guards: NULL siempre (negación, pasado, duda subjuntivo).
    check("G1", "no llevar a mamá al médico", false, misses)
    check("G2", "llevé a papá al médico ayer", false, misses)
    check("G3", "quizá lleve a mi madre al médico", false, misses)

    // Pines anti-overreach: NULL esperado (fuera del alcance acotado).
    // P1: abuelos — FUERA este ciclo (UNA forma por ciclo).
    check("P1", "llevar a mi abuela al médico mañana", false, misses)
    // P2: esposa — FUERA este ciclo.
    check("P2", "llevar a mi mujer al médico mañana", false, misses)

    // Regresiones: HIT esperado (misma región de regex, ya cubiertas).
    check("R1", "llevar a mi hijo al médico mañana", true, misses)
    check("R2", "llevar a la niña al médico mañana", true, misses)
    check("R3", "llevar a mi mamá al aeropuerto mañana", true, misses)
    check("R4", "llevar a los niños al colegio mañana", true, misses)

    // Envolvente: camino genérico «recuérdame…» debe seguir capturando.
    val env = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a mamá al médico mañana", 1000)
    )
    if (env != null) {
        println("OK   E1  -> ${env.kind} ${env.confidence}  \"${env.title}\"")
    } else {
        misses.add("E1 envolvente: NULL")
        println("MISS E1  -> NULL")
    }

    println(if (misses.isEmpty()) "\nPROBE OK (0 misses) — piso médico parentesco adulto directo + plantilla matchMedicalRun VERIFICADOS"
            else "\nPROBE FAIL (${misses.size} misses)")
    if (misses.isNotEmpty()) kotlin.system.exitProcess(1)
}

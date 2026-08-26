// Sonda c.1170 (POST): candidata (b) FUERTE de la auditoría c.1165 (clase
// DECIMOCTAVA, vida social) — «llevar a los niños a la fiesta del cole el
// viernes». PRE medido con sonda efímera sobre el motor real (HEAD
// `62e34ba`): 6/6 capturas NULL, 4/4 guards NULL, 5/5 regresiones HIT,
// pines NULL, envolvente TASK 0.49. Esta sonda persistida verifica el
// POST-fix (piso escolar c.773 `ERRAND_SCHOOL_RUN_FLOOR` + plantilla
// `matchSchoolRun` extendidas lockstep con el destino «fiesta del
// cole/colegio», lección c.616): toda captura debe ser HIT ERRAND y todo
// guard/pin debe seguir NULL/estable.
// Uso: bash tools/run_probe.sh tools/probe/EighteenthClassFiestaColeProbe.kt
// Esperado POST-fix: «0 misses» (exit 0). Cada línea que imprima
// "MISS"/"GUARD-HIT" sería una regresión.

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

    // Capturas directas (el patrón faltaba: piso escolar c.773 destinos
    // CERRADOS sin «fiesta del cole»; keyword «llevar» sola bajo umbral).
    check("C1", "llevar a los niños a la fiesta del cole el viernes", true, misses)
    check("C2", "llevar a los niños a la fiesta del colegio mañana", true, misses)
    check("C3", "llevar a los niños a la fiesta del cole mañana por la tarde", true, misses)
    check("C4", "vale, llevar a los niños a la fiesta del cole el viernes", true, misses)
    check("C5", "llevo a los niños a la fiesta del cole mañana", true, misses)
    check("C6", "llevar a los niños a la fiesta del cole", true, misses)

    // Guards: NULL esperado.
    check("G1", "no llevo a los niños a la fiesta del cole", false, misses)
    check("G2", "llevé a los niños a la fiesta del cole ayer", false, misses)
    check("G3", "no sé si llevar a los niños a la fiesta del cole", false, misses)
    check("G4", "la fiesta del cole es el viernes", false, misses)

    // Regresiones: HIT esperado (misma región de regex).
    check("R1", "llevar a los niños al colegio mañana", true, misses)
    check("R2", "llevar a los niños al aeropuerto mañana", true, misses)
    check("R3", "llevar a la niña al médico mañana", true, misses)
    check("R4", "llevar el portátil al trabajo mañana", true, misses)
    check("R5", "llevar a los niños al parque esta tarde", true, misses)

    // Pines anti-overreach: NULL esperado.
    check("P1", "llevar a los niños a la fiesta de cumpleaños el sábado", false, misses)
    check("P2", "llevar a mi hija a la fiesta del cole el viernes", false, misses)
    check("P3", "llevar a los niños a la fiesta mañana", false, misses)

    // Envolvente: camino genérico «tengo que…» debe seguir TASK.
    val env = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que llevar a los niños a la fiesta del cole el viernes", 1000)
    )
    if (env != null && env.kind == ContextIntentKind.TASK) {
        println("OK   P4  -> TASK ${env.confidence}  \"${env.title}\"")
    } else {
        misses.add("P4 envolvente: ${env?.kind ?: "NULL"}")
        println("MISS P4  -> ${env?.kind ?: "NULL"}")
    }

    println(if (misses.isEmpty()) "\nPROBE OK (0 misses) — piso fiesta del cole + plantilla matchSchoolRun VERIFICADOS"
            else "\nPROBE FAIL (${misses.size} misses)")
    if (misses.isNotEmpty()) kotlin.system.exitProcess(1)
}

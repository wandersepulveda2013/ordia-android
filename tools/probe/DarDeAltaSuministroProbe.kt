import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.1139 — sonda de la candidata (b) de la clase DECIMOQUINTA
 * burocracia/administración (medida por el hermano c.1132 con la sonda
 * persistida `tools/probe/FifteenthClassAdminProbe.kt` C3/C4): «dar de
 * alta/baja <suministro>» (luz/agua/gas/internet). Decisión de dominio
 * TASK (gestión administrativa SIN desplazamiento físico; hermana EXACTA
 * de «dar de baja el gimnasio/la suscripción» TASK c.895c — la doctrina
 * ERRAND c.842/c.862 gobierna solo el desplazamiento).
 *
 * NO es un test: su salida PRE (base 67b7e7e, medida con sonda efímera
 * idéntica) documenta el NULL medido — 8/8 candidatas NULL, 7/7 guards
 * NULL, regresiones HIT — y POST el HIT tras el lockstep de TRES puntos:
 * keyword-frase «dar de alta» (ContextIntent.kt), piso «dar de alta
 * <suministro>» nuevo + extensión aditiva de objetos del piso «dar de
 * baja» c.895c (ContextIntentEngine.hasStrongTaskImperative), plantilla
 * matchDarDeAlta en extractTitle (lección c.616, doctrina c.653).
 *
 * Olvido silencioso P1: mudanza sin luz/agua/gas/internet (alta) o cargo
 * mensual fantasma del piso viejo (baja).
 *
 * Guards anti-overreach: negación, duda, pasado («di de alta/baja»),
 * bivalente médico «dar de alta a un paciente», sustantivo «el alta de la
 * luz», objeto deliberado NULL «la línea telefónica» (c.895c).
 *
 * Laterales ABIERTAS (UNA por ciclo): «seguro» (contrato, no suministro),
 * «empadronarme» (candidata c), «sellar el paro» (candidata d).
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (objetivo POST: captura TASK 0.45, título limpio)
    val candidates = listOf(
        "dar de alta la luz del piso nuevo mañana",
        "dar de baja el internet del piso viejo",
        "dar de alta el agua del apartamento la semana que viene",
        "dar de baja el gas mañana",
        "dar de alta la luz mañana",
        "vale, dar de baja el internet el viernes",
        "dar de alta el internet en el piso nuevo",
        "dar de baja la luz del piso viejo el lunes"
    )

    // Guards (objetivo: NULL siempre)
    val guards = listOf(
        "no dar de alta la luz mañana",
        "quizá dar de baja el internet mañana",
        "di de alta la luz ayer",
        "di de baja el internet ayer",
        "dar de alta a un paciente mañana",
        "el alta de la luz del piso",
        "dar de baja la línea telefónica"
    )

    // Regresiones (objetivo: HIT byte-idéntico salvo bono keyword c.1139)
    val regressions = listOf(
        "dar de baja el gimnasio mañana",
        "dar de baja la suscripción mañana",
        "dar las gracias a Ana por el regalo mañana",
        "pagar la luz mañana",
        "recuérdame dar de alta la luz mañana",
        "recuérdame dar de baja el internet mañana"
    )

    println("=== CANDIDATAS (objetivo POST: HIT TASK 0.45) ===")
    for (c in candidates) {
        val r = probe(c)
        if (r == null) println("[NULL] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence +
            " title=" + r.title + " dueAt=" + (r.dueAt != null))
    }
    println("=== GUARDS (objetivo: NULL) ===")
    for (c in guards) {
        val r = probe(c)
        if (r == null) println("[NULL-ok] «" + c + "»")
        else println("[HIT-inesperado] «" + c + "» → " + r.kind + " " + r.confidence)
    }
    println("=== REGRESIONES (objetivo: HIT) ===")
    for (c in regressions) {
        val r = probe(c)
        if (r == null) println("[NULL-inesperado] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence)
    }
}

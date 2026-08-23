import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.895c — sonda de la familia (4/8) de la clase NOVENA dinero/banca
 * (sonda c.892, BACKLOG P1): «dar de baja el gimnasio/la suscripción» —
 * membresías. Decisión de dominio TASK (gestión administrativa SIN
 * desplazamiento físico; hermana de «cobrar la nómina» TASK c.895b, no de
 * la doctrina «la diligencia gobierna» ERRAND c.842/c.862). Misma
 * metodología que [CobrarNominaProbe]: NO es un test; su salida PRE sobre
 * HEAD 698c8ba documenta el NULL medido y POST el HIT tras el lockstep
 * (piso TASK acotado + keywords-OBJETO + plantilla de título).
 * Guard anti-overreach: «dar de alta» (acción opuesta) NULL deliberado;
 * objetos fuera de ancla («la baja maternal», «la baja del coche»)
 * NULL deliberados; negación/duda/pasado NULL siempre.
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (objetivo POST: captura TASK)
    val candidates = listOf(
        "dar de baja el gimnasio mañana",
        "dar de baja la suscripción mañana",
        "dar de baja el gimnasio el viernes",
        "dar de baja la suscripción de netflix esta tarde"
    )

    // Guards (objetivo: NULL siempre)
    val guards = listOf(
        "no dar de baja el gimnasio mañana",
        "quizá dar de baja la suscripción mañana",
        "ayer di de baja el gimnasio",
        "la baja maternal me la dieron ayer",
        "dar de alta el gimnasio mañana"
    )

    // Laterales bivalentes sin ancla (objetivo: NULL deliberado)
    val laterales = listOf(
        "la baja del coche está lista",
        "dar de baja la línea telefónica mañana"
    )

    // Regresiones conocidas (objetivo: HIT inalterado)
    val regressions = listOf(
        "ir al gimnasio mañana",
        "cobrar la nómina mañana",
        "pagar la tarjeta mañana",
        "cancelar la suscripción de netflix mañana"
    )

    println("=== CANDIDATAS (familia 4/8 membresías; PRE se espera NULL) ===")
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
    println("=== LATERALES (objetivo: NULL deliberado) ===")
    for (c in laterales) {
        val r = probe(c)
        if (r == null) println("[NULL-ok] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence)
    }
    println("=== REGRESIONES (objetivo: HIT) ===")
    for (c in regressions) {
        val r = probe(c)
        if (r == null) println("[NULL-inesperado] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence)
    }
}

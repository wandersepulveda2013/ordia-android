import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.1208 — sonda persistida de la lateral ABIERTA del cierre
 * c.1201→c.1203 del hermano: plural PELADO «sacar billetes» (sin
 * artículo; medida NULL 5/5 sobre el parentesis heredado del
 * determinante-exigente). PRE efímera `/tmp/pre1208/PreProbe.kt`
 * (base remoto 7589c51f): 5/5 NULL candidatas peladas, guards 6/6
 * NULL, regresiones 6/6 HIT; «sacar la entrada» sigue NULL
 * (lateral ABIERTA documentada). POST es esta sonda.
 *
 * Fix hermano del c.1203 en TRES puntos (lección c.616):
 *  1. Keyword-frase «sacar billetes» (tercera hermana).
 *  2. Piso acotado — determinante «(el|los)» hecho OPCIONAL.
 *  3. Plantilla matchSacarBillete — MISMA opcionalidad.
 * Guard `(?<!no )` y ancla inicio/acuse/prefijo temporal intactos;
 * anti-overreach: «sacar el pasaporte» y «sacar la entrada» NULL.
 */
fun main() {
    val now = 1723939200000L
    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )
    val candidates = listOf(
        "sacar billetes mañana",
        "sacar billetes",
        "mañana sacar billetes",
        "vale, sacar billetes mañana",
        "sacar billetes para el tren mañana"
    )
    val guards = listOf(
        "no saques billetes todavía",
        "no sacar billetes todavía",
        "saqué billetes ayer",
        "el billete cuesta 50 euros",
        "sacar el pasaporte antes del vuelo",
        "sacar la entrada mañana"
    )
    val regressions = listOf(
        "sacar el billete de tren mañana",
        "sacar los billetes del tren mañana",
        "sacar la basura mañana",
        "sacar al perro mañana",
        "sacar dinero mañana",
        "sacar cita mañana",
        "sacar el visado antes del viaje"
    )
    println("=== CANDIDATAS PELADAS (objetivo POST: HIT TASK 0.45) ===")
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
        else println("[HIT-inesperado] «" + c + "» → " + r.kind + " " +
            r.confidence)
    }
    println("=== REGRESIONES (objetivo: HIT) ===")
    for (c in regressions) {
        val r = probe(c)
        if (r == null) println("[NULL-inesperado] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence)
    }
}

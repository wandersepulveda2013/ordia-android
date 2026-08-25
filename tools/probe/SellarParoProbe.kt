import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.1143 — sonda de la candidata (c) de la clase DECIMOQUINTA
 * burocracia/administración (medida por el hermano c.1132 con la sonda
 * persistida `tools/probe/FifteenthClassAdminProbe.kt` C1): «sellar el
 * paro [temporal]». Decisión de dominio TASK (obligación administrativa
 * periódica SIN desplazamiento explícito; hermana EXACTA de «dar de
 * alta/baja <suministro>» TASK c.1139/c.895c — la doctrina ERRAND
 * c.842/c.862 gobierna solo el desplazamiento).
 *
 * NO es un test: su salida PRE (base `8eba7fe`, medida con sonda
 * efímera idéntica `/tmp/SellarParoPreProbe.kt`) documenta el NULL
 * medido — 2/2 candidatas DESNUDAS NULL («sellar el paro el día 4»,
 * «sellar el paro mañana»), 2/2 envolventes HIT por camino genérico
 * («tengo que…» 0.45, «recuérdame…» 0.45), 5/5 guards NULL, 3/3
 * regresiones HIT — y POST el HIT tras el lockstep de TRES puntos:
 * keyword-frase «sellar el paro» (ContextIntent.kt), piso «sellar
 * (el)? paro» nuevo (ContextIntentEngine.hasStrongTaskImperative) y
 * plantilla matchSellarParo en extractTitle (lección c.616, doctrina
 * c.653).
 *
 * Olvido silencioso P1: sellar el paro es una obligación periódica —
 * olvidarla cuesta la prestación por desempleo (el olvido más caro de
 * la clase DECIMOQUINTA).
 *
 * Guards anti-overreach: negación («no selles el paro todavía»),
 * duda («no sé si sellar el paro…», «quizá sellar el paro mañana»),
 * pasado («sellé el paro ayer»), sustantivo («el sello del paro…»),
 * bivalente fronterizo («sellar el pasaporte en la frontera») y otro
 * objeto («sellar la carta») — el piso EXIGE el objeto «paro».
 *
 * Laterales ABIERTAS (UNA por ciclo): cola temporal «el día N»
 * residual en el título («Sellar el paro el día 4» — dueAt correcto,
 * familia de colas conocida hermana del residuo «antes» de c.1134),
 * «sellar el paro por internet», candidata (d) «empadronarme»/«hacer
 * la mudanza».
 *
 * POST medido (base del fix): candidatas desnudas 4/4 HIT TASK 0.45
 * (sonda efímera cubre C1/C2 + test de piso 5 capturas), envolvente
 * «recuérdame…» re-pin legítimo 0.45→0.54 por la keyword nueva
 * (precedente c.1035/c.1139), 5/5 guards NULL, 3/3 regresiones HIT
 * byte-idénticas.
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (objetivo POST: captura TASK 0.45, título limpio salvo
    // cola temporal «el día N» — lateral ABIERTA documentada arriba)
    val candidates = listOf(
        "sellar el paro el día 4",
        "sellar el paro mañana",
        "sellar el paro el lunes",
        "sellar el paro",
        "vale, sellar el paro mañana"
    )

    // Envolventes (objetivo: HIT TASK; «recuérdame…» re-pin 0.45→0.54)
    val envelopes = listOf(
        "tengo que sellar el paro el día 4",
        "recuérdame sellar el paro el lunes"
    )

    // Guards (objetivo: NULL siempre)
    val guards = listOf(
        "no selles el paro todavía",
        "no sé si sellar el paro mañana o pasado",
        "quizá sellar el paro mañana",
        "sellé el paro ayer",
        "el sello del paro me llega por correo",
        "sellar el pasaporte en la frontera",
        "sellar la carta mañana"
    )

    // Regresiones (objetivo: HIT byte-idéntico salvo bono keyword c.1143)
    val regressions = listOf(
        "dar de alta la luz del piso nuevo mañana",
        "dar de baja el gimnasio mañana",
        "presentar el recurso de la multa esta semana",
        "pagar la luz mañana"
    )

    println("=== CANDIDATAS (objetivo POST: HIT TASK 0.45) ===")
    for (c in candidates) {
        val r = probe(c)
        if (r == null) println("[NULL] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence +
            " title=" + r.title + " dueAt=" + (r.dueAt != null))
    }
    println("=== ENVOLVENTES (objetivo: HIT TASK) ===")
    for (c in envelopes) {
        val r = probe(c)
        if (r == null) println("[NULL-inesperado] «" + c + "»")
        else println("[HIT] «" + c + "» → " + r.kind + " " + r.confidence)
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

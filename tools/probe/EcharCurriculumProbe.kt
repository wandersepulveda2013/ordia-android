import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.1148 — sonda de la candidata (a) FUERTE de la clase DECIMOSÉPTIMA
 * vida laboral (medida por el hermano c.1147 con la sonda persistida
 * `tools/probe/SeventeenthClassWorkProbe.kt` C5): «echar el currículum
 * [en la oferta…] [temporal]». Decisión de dominio TASK (gestión
 * administrativa SIN desplazamiento explícito: echar el currículum se
 * hace online; hermana EXACTA de «sellar el paro» TASK c.1143 y de
 * «enviar» TASK c.692 — la doctrina ERRAND c.842/c.862 gobierna solo
 * el desplazamiento).
 *
 * NO es un test: su salida PRE (base `5a39f45`, medida con sonda
 * efímera idéntica `/tmp/EcharCurriculumPreProbe.kt`) documenta el
 * NULL medido — 7/7 candidatas NULL (desnuda, «mañana», «esta semana»,
 * oferta con plazo, acuse «vale,», grafía coloquial «curriculo», sin
 * artículo), 2/2 envolventes HIT por camino genérico («tengo que…»/
 * «recuérdame…» 0.45, título ya limpio), 8/8 guards NULL, 4/4
 * regresiones HIT — y POST el HIT tras el lockstep de TRES puntos:
 * keyword-OBJETO «currículum»/«curriculo» (ContextIntent.kt — objeto
 * y NO el verbo «echar», bivalente c.829, mismo motivo por el que
 * «gasolina» fue keyword-OBJETO), piso «echar (el)? curr[ií]cul[ou]m?»
 * nuevo (ContextIntentEngine.hasStrongTaskImperative, junto al piso
 * «sellar el paro» c.1143) y plantilla matchEcharCurriculum en
 * extractTitle (lección c.616, doctrina c.653).
 *
 * Olvido silencioso P1: la oferta de empleo tiene plazo — el olvido
 * cuesta la oportunidad entera (el olvido más caro de la candidata (a)
 * de la clase DECIMOSÉPTIMA).
 *
 * Guards anti-overreach: negación («no eches el currículum todavía»),
 * duda («no sé si echar el currículum…», «quizá echar el currículum
 * mañana»), pasado («eché el currículum ayer»), sustantivo («el
 * currículum está listo para enviar» — keyword-objeto 0.12 + keyword
 * «enviar» 0.12 = 0.24 < 0.45, pin anti-apilado) y bivalentes del
 * verbo «echar» c.829 («echar de menos…», «echar la carta al buzón»,
 * «echar agua a las plantas») — el piso EXIGE el objeto «currículum».
 *
 * Laterales ABIERTAS (UNA por ciclo): cola de período «esta semana»
 * residual en el título («Echar el currículum esta semana» — dueAt
 * null, familia de colas conocida hermana del residuo «el día N» de
 * c.1143), «echar un currículum» (indefinido), «mandar el currículum»
 * (sinónimo), candidata (b) «cubrir el turno del sábado» (C7 de la
 * sonda c.1147).
 *
 * POST medido (base del fix): 7/7 candidatas HIT TASK 0.45 (títulos
 * limpios salvo la cola de período lateral), envolventes re-pin
 * legítimo 0.45→0.49 («tengo que…») / 0.45→0.54 («recuérdame…») por
 * la keyword nueva (precedente c.1035/c.1139/c.1143; títulos
 * byte-idénticos), 8/8 guards NULL, 4/4 regresiones HIT byte-idénticas
 * («echar gasolina mañana» ERRAND c.829 intacto — la plantilla TASK no
 * lo alcanza; «enviar el currículum a la empresa mañana» TASK c.692
 * 0.45 título idéntico).
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (objetivo POST: captura TASK 0.45, título limpio salvo
    // cola de período «esta semana» — lateral ABIERTA documentada arriba)
    val candidates = listOf(
        "echar el currículum en la oferta de infojobs",
        "echar el currículum mañana",
        "echar el currículum esta semana",
        "echar el currículum",
        "vale, echar el currículum mañana",
        "echar el curriculo en infojobs",
        "echar currículum en la oferta del mercadona"
    )

    // Envolventes (objetivo: HIT TASK; re-pin legítimo por keyword nueva)
    val envelopes = listOf(
        "tengo que echar el currículum mañana",
        "recuérdame echar el currículum el lunes"
    )

    // Guards (objetivo: NULL siempre)
    val guards = listOf(
        "no eches el currículum todavía",
        "no sé si echar el currículum en esa oferta",
        "quizá echar el currículum mañana",
        "eché el currículum ayer",
        "el currículum está listo para enviar",
        "echar de menos a los compañeros del trabajo",
        "echar la carta al buzón",
        "echar agua a las plantas"
    )

    // Regresiones (objetivo: HIT byte-idéntico)
    val regressions = listOf(
        "echar gasolina mañana",
        "sellar el paro mañana",
        "enviar el currículum a la empresa mañana",
        "pagar la luz mañana"
    )

    fun show(tag: String, s: String) {
        val r = probe(s)
        if (r == null) println("$tag | NULL | $s")
        else println("$tag | ${r.kind} ${r.confidence} dueAt=${r.dueAt != null} | $s | title='${r.title}'")
    }

    candidates.forEachIndexed { i, s -> show("C${i + 1}", s) }
    envelopes.forEachIndexed { i, s -> show("E${i + 1}", s) }
    guards.forEachIndexed { i, s -> show("G${i + 1}", s) }
    regressions.forEachIndexed { i, s -> show("R${i + 1}", s) }
}

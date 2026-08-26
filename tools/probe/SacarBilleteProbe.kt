import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.1219 — sonda persistida de la candidata (a) de la clase
 * VIGESIMOQUINTA viajes (medida NULL exacta en c.1200: «sacar el
 * billete de tren mañana» se DESCARTABA silenciosamente; «sacar»
 * estaba acotado a basura c.717, mascota c.740, dinero c.893,
 * cita/turno/hora c.1117 y visado c.1151, y «billete» NO era
 * keyword, gate c.751 — la notificación ni llegaba al análisis).
 * Decisión de dominio TASK: reserva/compra del desplazamiento
 * (sin billete no hay viaje), hermana de «sacar el visado»
 * c.1151 — la doctrina ERRAND c.842/c.862 gobierna solo el
 * desplazamiento, aquí el acto es de reserva/gestión provisoria.
 *
 * PRE (base 3fa210f, medida en este run): candidatas 5/5 NULL
 * (desnuda, temporal sujeta, temporal prefija, prefijo de
 * acuse «vale,», plural «los billetes del tren»); envolventes
 * 2/2 HIT por camino genérico («tengo que…» y «recuérdame…» vía
 * candado c.613 — pineadas como posibles re-pins de confianza,
 * medidas POST); guards 6/6 NULL (las envolventes de duda/pasado
 * ya quedaban cubiertas); regresiones 6/6 HIT (siblings «sacar»
 * c.717/c.740/c.893/c.1117/c.1151 + facturar-vuelo c.1140).
 *
 * Olvido silencioso P1: sin billete no hay viaje — el olvido más
 * caro de la clase VIGESIMOQUINTA junto a salir-aeropuerto
 * c.1150 y facturar-vuelo c.1140; «sacar el billete» es LA forma
 * coloquial española de reservar/comprar el pasaje.
 *
 * Fix lockstep en TRES puntos (lección c.616, doctrina c.653,
 * gate c.751 CERO keywords sueltas):
 *  1. Keywords-frase «sacar el billete»/«sacar los billetes»
 *     en ContextIntent.kt TASK (monosemánticas; «sacar» solo NO
 *     se toca — bivalente consolidado en 5 pisos; «billete» solo
 *     tampoco — sustantivo declarativo).
 *  2. Piso acotado en [ContextIntentEngine.hasStrongTaskImperative]:
 *     ancla inicio/acuse/prefijo temporal, guard anti-negación
 *     `(?<!no )`(negative guard, mismo argumento c.895c/c.1140),
 *     objeto EXIGIDO «(el|los) billete(s)» (anti-overreach:
 *     «sacar el pasaporte» queda lateral NULL deliberado).
 *  3. Plantilla hermana matchSacarBillete en la rama TASK de
 *     [extractTitle]: captura objeto + calificador opcional
 *     «de|del <prod. monosemántico>» («de tren», «del bus»);
 *     match arranca en el verbo para despojar acuse/temporal
 *     (lección c.616).
 *
 * Laterales ABIERTAS (UNA por ciclo, medidas POST en guards):
 * «sacar el pasaporte» (bivalente), «sacar la entrada» (eventos;
 * el piso exige «billete(s)»), frase coloquial «echar el billete»
 * y plural pelado «sacar billetes» (sin artículo).
 *
 * POST medido (base del fix de este run): candidatas 5/5 HIT
 * TASK 0.45 con título «Sacar el billete de tren» / «Sacar los
 * billetes del tren» (la cola temporal va a dueAt, no al
 * título), envolvente «tengo que…» re-pin legítimo 0.45→0.49 y
 * «recuérdame…» re-pin 0.45→0.54 por las keywords-frase nuevas
 * (precedente c.1035/c.1139 — mismo bono), guards 6/6 NULL,
 * regresiones 6/6 HIT byte-idénticas.
 */
fun main() {
    val now = 1723939200000L

    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )

    // Candidatas (objetivo POST: HIT TASK 0.45, título limpio con la
    // cola temporal en dueAt; variantes: sujeta, desnuda, prefecta,
    // acuse y plural)
    val candidates = listOf(
        "sacar el billete de tren mañana",
        "sacar el billete de tren",
        "mañana sacar el billete de tren",
        "vale, sacar el billete de tren mañana",
        "sacar los billetes del tren mañana"
    )

    // Envolventes (objetivo: HIT TASK; posible re-pin 0.45→0.54 por
    // keyword nueva, medido POST)
    val envelopes = listOf(
        "tengo que sacar el billete de tren mañana",
        "recuérdame sacar el billete de tren el lunes"
    )

    // Guards (objetivo: NULL siempre)
    val guards = listOf(
        "no saques el billete de tren todavía",
        "no sacar el billete de tren todavía",
        "saqué el billete de tren ayer",
        "no sé si sacar el billete de tren mañana",
        "el billete de tren cuesta 50 euros",
        "sacar el pasaporte antes del vuelo"
    )

    // Regresiones (objetivo: HIT byte-idéntico — siblings «sacar»
    // c.717/c.740/c.893/c.1117/c.1151 y facturar-vuelo c.1140)
    val regressions = listOf(
        "sacar el visado antes del viaje",
        "sacar la basura mañana",
        "sacar al perro mañana",
        "sacar dinero mañana",
        "sacar cita mañana",
        "facturar el vuelo mañana"
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

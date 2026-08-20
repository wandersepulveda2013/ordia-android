import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.736: CUARTA clase de formas cotidianas — cuidado
 * de mascotas, obligaciones de pago/compra con PREFIJO TEMPORAL antes del
 * verbo (clase de defecto c.643/c.647/c.651: los pisos SHOPPING/PAYMENT de
 * c.626/c.630/c.651 anclan a INICIO/ACUSE pero NO a prefijo temporal) y
 * quehaceres domésticos aún sin piso ni keyword (pasar la aspiradora, pintar,
 * colgar la ropa, podar), más formas cívicas/de salud cotidianas.
 *
 * Misma metodología que CommonVerbDiscoveryProbe (c.692, clase 1, CERRADA 8/8),
 * ManagementVerbDiscoveryProbe (c.711, clase 2, CERRADA 14/14) y
 * ThirdClassVerbDiscoveryProbe (c.721, clase 3, CERRADA 19/19): frases de
 * captura real (objeto + fecha) y controles. NO es un test; su salida
 * alimenta el BACKLOG (un ítem/forma por ciclo, anti-overreach).
 */
@Suppress("DEPRECATION")
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Candidatos: pago/compra con prefijo temporal.
        // Nota mantenimiento c.746: las 3 formas de PAGO RESUELTAS (piso
        // `PAYMENT_FLOOR` c.746 centralizado con ancla TEMPORAL añadida a las
        // de c.630 inicio + c.651 acuse, compartido con el guard de envolvente
        // `WRAPPABLE_PATTERNS` — lección lockstep c.648/c.652) — las líneas
        // quedan como REGRESIÓN de la familia pago temporal. OPEN residual:
        // "el lunes que viene pagar el arriendo" (el ancla temporal exige el
        // temporal INMEDIATO al verbo; "que viene" no está cubierto por
        // TASK_FLOOR_TEMPORAL — ampliarlo tocaría TODOS los pisos).
        "mañana pagar la luz",
        "el lunes pagar el arriendo",
        "hoy pagar el recibo del internet",
        "mañana pagar el arriendo",
        "el viernes pagar la renta",
        // Regresión c.746 del guard de envolvente (lockstep): el envolvente
        // gobierna TASK, no PAYMENT subordinado.
        "recuérdame mañana pagar el arriendo",
        "mañana comprar el mercado",
        "el sábado comprar el pan",
        // Candidatos: cuidado de mascotas (cotidiano, sin piso ni keyword).
        "sacar al perro mañana",
        "bañar al perro el sábado",
        // Nota mantenimiento c.744: "alimentar al gato hoy" RESUELTA (piso
        // `HOUSEHOLD_FEED_CAT_FLOOR` c.744 acotado al objeto mascota
        // `gat[oa]s?`, keywords "gato"/"gata" lockstep) — la línea queda
        // como REGRESIÓN de la familia mascota.
        "alimentar al gato hoy",
        // Nota mantenimiento c.747: "llevar al perro al veterinario
        // mañana" RESUELTA (piso `HOUSEHOLD_VET_FLOOR` c.747 acotado a la
        // forma completa mascota `perr[oa]s?` + destino `veterinari[oa]s?`,
        // keywords "veterinario"/"veterinaria" lockstep) — la línea queda
        // como REGRESIÓN de la familia mascota.
        "llevar al perro al veterinario mañana",
        // Candidatos: hogar aún no cubierto por HOUSEHOLD_VERBS ni pisos acotados.
        // Nota mantenimiento c.742: "pasar la aspiradora mañana" RESUELTA
        // por la sonda paralela Chore (piso `HOUSEHOLD_VACUUM_CLEANER_FLOOR`
        // c.742, keyword "aspiradora" lockstep) — la línea queda como
        // REGRESIÓN de forma compartida entre ambas sondas.
        "pasar la aspiradora mañana",
        "pintar la casa este fin de semana",
        // Nota mantenimiento c.743: "colgar la ropa hoy" RESUELTA por la
        // sonda paralela Chore (piso `HOUSEHOLD_HANG_LAUNDRY_FLOOR` c.743,
        // keyword "ropa" lockstep) — la línea queda como REGRESIÓN de
        // forma compartida entre ambas sondas.
        "colgar la ropa hoy",
        // Nota mantenimiento c.748: "podar el jardín el sábado" RESUELTA
        // (piso `HOUSEHOLD_GARDEN_FLOOR` c.748, verbo "podar" keyword
        // lockstep — la keyword-objeto "jardín" ya existía) — la línea
        // queda como REGRESIÓN.
        "podar el jardín el sábado",
        // Candidatos: obligaciones cívicas/salud/de dispositivo cotidianas.
        "votar el domingo",
        // Nota mantenimiento c.750: "donar sangre el sábado" RESUELTA (piso
        // TASK acotado al objeto `sangre` c.750 — kind TASK en deliberación
        // contra APPOINTMENT/ERRAND/HOUSEHOLD: quehacer de vida, hermano de
        // "renovar el DNI" c.698 — keyword "donar" lockstep, plantilla
        // "(donar) sangre"→"Donar sangre") — la línea queda como REGRESIÓN.
        "donar sangre el sábado",
        // Nota mantenimiento c.751: "cargar el celular hoy" RESUELTA (piso
        // TASK acotado al objeto `celular/celulares` c.751 — kind TASK en
        // deliberación contra HOUSEHOLD: deber de mantenimiento del móvil,
        // no quehacer físico del hogar — keyword-OBJETO "celular" lockstep,
        // NO el verbo "cargar" [bivalente + subcadena de "descargar" c.725],
        // plantilla "(cargar) … celular…"→"Cargar el celular") — la línea
        // queda como REGRESIÓN.
        "cargar el celular hoy",
        "sacar la basura esta noche",
        // Controles: negación / duda / sustantivo / pasado / verbo suelto /
        // chat deben permanecer NULL (anti-overreach).
        "no pagar la luz mañana",
        "quizá pagar la luz mañana",
        "el pago de la luz fue ayer",
        "pagué la luz ayer",
        "pagar",
        "no sacar al perro mañana",
        "quizá sacar al perro mañana",
        "saqué al perro ayer",
        "sacar",
        "no pasar la aspiradora mañana",
        "pasé la aspiradora ayer",
        "no colgar la ropa hoy",
        "colgué la ropa ayer",
        // c.744: controles del piso `alimentar al gato` (negada / duda /
        // pasado / objeto no gato).
        "no alimentar al gato hoy",
        "quizá alimentar al gato mañana",
        "alimenté al gato ayer",
        "alimentar al bebé hoy",
        // c.747: controles del piso `llevar al perro al veterinario`
        // (negada / duda / pasado / objeto no mascota / destino no
        // veterinario). Nota: "llevar el coche al taller" captura como
        // ERRAND por vía PRE-EXISTENTE (piso de llevar-objeto de ERRAND,
        // verificado pre-cambio en la base) — el control exige que NO
        // robe HOUSEHOLD.
        "no llevar al perro al veterinario mañana",
        "quizá llevar al perro al veterinario mañana",
        "llevé al perro al veterinario ayer",
        "llevar el coche al taller mañana",
        "llevar al perro al parque mañana",
        // c.748: controles del piso `podar el jardín` (negada / duda /
        // pasado / objeto no jardín / diminutivo). La forma capturable
        // "podar el jardín el sábado" se movió arriba como regresión.
        "no podar el jardín mañana",
        "quizá podar el jardín mañana",
        "podé el jardín ayer",
        "podar las rosas mañana",
        "podar el jardincito mañana",
        "no pintar la casa mañana",
        "pinté la casa ayer",
        "no votar el domingo",
        "voté el domingo pasado",
        "no donar sangre el sábado",
        "doné sangre el sábado pasado",
        // c.750: controles del piso `donar sangre` (duda / objeto bivalente
        // / verbo suelto). La forma capturable "donar sangre el sábado" se
        // movió arriba como regresión.
        "quizá donar sangre el sábado",
        "donar dinero a la ONG el sábado",
        "donar",
        "no cargar el celular hoy",
        "cargué el celular anoche",
        "hola cómo estás",
        "jaja qué risa"
    )
    for (c in cases) {
        @Suppress("DEPRECATION")
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

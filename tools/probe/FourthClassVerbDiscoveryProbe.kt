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
        // Candidatos: pago/compra con prefijo temporal (ancla ausente en los
        // pisos de c.626/c.630/c.651 — misma clase que c.643/c.647).
        "mañana pagar la luz",
        "el lunes pagar el arriendo",
        "hoy pagar el recibo del internet",
        "mañana comprar el mercado",
        "el sábado comprar el pan",
        // Candidatos: cuidado de mascotas (cotidiano, sin piso ni keyword).
        "sacar al perro mañana",
        "bañar al perro el sábado",
        "alimentar al gato hoy",
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
        "podar el jardín el sábado",
        // Candidatos: obligaciones cívicas/salud/de dispositivo cotidianas.
        "votar el domingo",
        "donar sangre el sábado",
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
        "no pintar la casa mañana",
        "pinté la casa ayer",
        "no votar el domingo",
        "voté el domingo pasado",
        "no donar sangre el sábado",
        "doné sangre el sábado pasado",
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

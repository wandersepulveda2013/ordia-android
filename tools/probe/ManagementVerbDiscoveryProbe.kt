import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.711: segunda clase de verbos cotidianos de
 * gestión personal (avisar/pedir/solicitar/apuntar/buscar/coger/pasar por)
 * sin piso propio, herencia de la clase-verbos c.692…c.710 (CERRADA 8/8).
 * Metodología idéntica: frases de captura real (objeto + fecha) y controles
 * anti-overreach. NO es un test; su salida alimenta el BACKLOG (una forma o
 * cluster por ciclo, doctrina anti-overreach).
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Candidatos: verbos cotidianos de gestión con fecha
        "avisar a mamá de la cita mañana",
        "avisar al jefe de la entrega hoy",
        "pedir el taxi mañana",
        "pedir una cita hoy",
        "solicitar la cita el lunes",
        "apuntar la dirección del médico",
        "anotar el número del banco mañana",
        "buscar el seguro de la casa mañana",
        "coger la ropa mañana",
        "coger el bus mañana",
        "sacar la basura esta noche",
        "pasar por el banco mañana",
        "publicar las fotos mañana",
        "recordar a papá el almuerzo mañana",
        // Controles: negación / condicional / sustantivo / verbo suelto deben
        // permanecer NULL (anti-overreach), chat casual NULL.
        "no avisar a mamá mañana",
        "no pedir el taxi mañana",
        "quizá pedir el taxi mañana",
        "no solicitar la cita mañana",
        "quizá solicitar la cita mañana",
        "el aviso a mamá era ayer",
        "avisar",
        "el pedido llegó ayer",
        "pedir",
        "la solicitud de la cita llegó ayer",
        "solicitar",
        "buscar",
        "no coger el bus mañana",
        "quizá coger el bus mañana",
        "coger",
        "cogí el bus ayer",
        "no pasar por el banco mañana",
        "quizá pasar por el banco mañana",
        "pasé por el banco ayer",
        "pasar por el parque mañana",
        "recordadetodos los pendientes",
        "hola buenos días gracias luego",
        "nos vemos después jeje"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

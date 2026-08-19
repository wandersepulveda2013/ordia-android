import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.692: clase de verbos cotidianos de gestión
 * personal sin piso propio. Misma metodología que KindCheckProbe (c.690):
 * frases de captura real (objeto + fecha) y controles. NO es un test; su
 * salida alimenta el BACKLOG (un ítem/forma por ciclo, anti-overreach).
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Verbos candidatos de gestión cotidiana con fecha
        "enviar el informe mañana",
        "entregar la tarea el lunes",
        "firmar el contrato el jueves",
        "renovar el DNI la semana que viene",
        "devolver el libro mañana",
        "confirmar la reserva esta noche",
        "imprimir las entradas el viernes",
        "recoger el paquete mañana",
        "reservar el restaurante el sábado",
        "cambiar las sábanas el domingo",
        // Controles: negación, condicional, sustantivos, suelto
        "no enviar el informe",
        "quizá entregar la tarea mañana",
        "la entrega del paquete es mañana",
        "la firma del contrato fue ayer",
        "enviar",
        // Controles c.698 (piso "renovar"): negada, condicional,
        // sustantivo y verbo aislado deben permanecer NULL.
        "no renovar el seguro",
        "quizá renovar el seguro mañana",
        "la renovación del seguro es mañana",
        "renovar",
        // Controles c.700 (piso "confirmar"): misma doctrina.
        "no confirmar la reserva",
        "quizá confirmar la reserva mañana",
        "la confirmación de la reserva llegó ayer",
        "confirmar",
        // Controles c.708 (piso "imprimir"): misma doctrina.
        "no imprimir el informe",
        "quizá imprimir el informe mañana",
        "la impresión del informe fue ayer",
        "imprimir",
        // Controles c.709 (piso "reservar"): misma doctrina.
        "no reservar el hotel",
        "quizá reservar el restaurante mañana",
        "la reserva del restaurante es mañana",
        "reservar"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

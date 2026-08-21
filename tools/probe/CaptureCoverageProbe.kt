import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.822: cobertura del enrutado de captura
 * ([ContextIntentEngine.classify]/[extractTitle]) sobre formas cotidianas
 * reales que NINGUNA sonda anterior midió. Misma metodología que
 * [ManagementVerbDiscoveryProbe]/[CommonVerbDiscoveryProbe] (c.690–c.721):
 * frases de gestión personal (objeto + fecha) + controles (negación,
 * duda, narrativa pasado, verbo aislado). NO es un test; su salida
 * alimenta el BACKLOG (un ítem/forma por ciclo, anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es un
 * GAP de captura (olvido silencioso P1) si el enunciado es un compromiso
 * plausible del usuario. NULL sobre una narrativa/controles es CORRECTO.
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Trámites/gestión pública (clase nunca sondeada; "tramitar" 0 hits)
        // c.822: REGRESIÓN — las dos primeras formas ya tienen piso (TASK)
        // tras el fix c.822; deben reportar HIT.
        "tramitar el pasaporte mañana",
        "tramitar la visa la semana que viene",
        "renovar el pasaporte el lunes", // control: 'renovar' ya tiene piso (c.698)
        // Equipaje/viaje doméstico ("maleta" nunca sondeada)
        "hacer la maleta esta noche",
        "preparar la maleta mañana",
        "meter la maleta en el coche esta noche",
        // Encargos/comisiones ("mandar/encargar" nunca sondeados; "enviar" sí, c.692)
        // c.823: REGRESIÓN — las dos formas de "mandar" ya tienen piso
        // (TASK) tras el fix c.823; deben reportar HIT.
        "mandar el paquete el jueves",
        "mandar el fax mañana",
        "encargar el pastel mañana",
        "encargar las flores el viernes",
        "pedir el pastel mañana", // control: 'pedir' es keyword de TASK
        // Compra doméstica suelta ("nevera")
        "llenar la nevera mañana",
        "vaciar la nevera el domingo",
        // Vehículo/combustible ("gasolina" 1 hit: ERRAND_CARRY)
        "echar gasolina esta tarde",
        "ir a echar gasolina mañana",
        "gasolina: echar antes del viaje",
        // Agenda médica escrita distinto ("sacar cita", "pedir hora")
        "sacar cita con el dentista mañana",
        "pedir hora en el médico el lunes",
        "pedir cita con el dentista mañana",
        // Hogar: organización/armario
        "organizar el armario el sábado",
        "ordenar el armario mañana", // control: 'ordenar' ya es verbo de piso doméstico
        "vaciar el armario el domingo",
        // Frases de compra cotidiana
        "hacer la compra por la mañana",
        "hacer la compra del mes mañana",
        // Controles: deben permanecer NULL (negación, duda, narrativa,
        // verbo aislado). Las narrativas pasado NO son compromisos.
        "no tramitar el pasaporte",
        "quizá tramitar el pasaporte mañana",
        "tramitó el pasaporte ayer",
        "la maleta está hecha",
        "no mandar el paquete",
        "quizá mandar el paquete mañana",
        "mandó el paquete ayer",
        "el pastel ya está encargado",
        "no vaciar la nevera",
        "la nevera está llena",
        "tramitar",
        "mandar",
        "encargar"
    )
    var nulls = 0
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        if (intent == null) {
            nulls++
            println("[NULL] $c")
        } else {
            println(
                "[HIT] ${intent.kind} ${"%.2f".format(intent.confidence)}" +
                    " | ${intent.title} | dueAt=${intent.dueAt != null} ← $c"
            )
        }
    }
    println("=== RESUMEN: $nulls NULLs de ${cases.size} ===")
}

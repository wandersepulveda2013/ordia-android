import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.857 (persistida): OCTAVA clase de formas
 * cotidianas — GESTIONES DE LA VIDA ADULTA (vehículo, trámites,
 * finanzas del hogar, documentos, comunicaciones pendientes, reservas,
 * salud cotidiana). La clase SÉPTIMA (diligencias con tercero/persona y
 * planes sociales, c.845) quedó AGOTADA en c.856 («apuntarse a»); esta
 * clase explora la frontera siguiente del habla cotidiana española: la
 * burocracia y los recados de la vida adulta dichos como se hablan.
 *
 * Misma metodología que [SeventhClassErrandProbe] (c.845) y
 * [SixthClassEncliticProbe] (c.833): frases declarativas cotidianas
 * (compromiso plausible) + regresiones (formas que YA capturan) +
 * controles (negación, duda, narrativa pasado, verbo aislado, sentido
 * figurado). NO es un test; su salida alimenta el BACKLOG (un
 * ítem/forma por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es un
 * GAP de captura (olvido silencioso P1) si el enunciado es un compromiso
 * plausible del usuario. NULL sobre controles es CORRECTO (intencionado).
 *
 * Estado medido en c.857 (HEAD 26d96e0, run_probe.sh, motor real):
 *   CAPTURAS — 7 NULLs (gaps confirmados, abajo como CAPTURAS; al
 *   resolverse cada una se mueve a REGRESIONES con su ciclo, convención
 *   c.833/c.836/c.845):
 *     1) «tomar la medicación a las 8» — salud diaria (la más crítica:
 *        olvidar la medicación es el olvido con consecuencia real).
 *     2) «responder el correo de Ana hoy» — comunicación pendiente.
 *     3) «contestar a Juan esta tarde» — variante coloquial de la 2.
 *     4) «hacerme un análisis de sangre el lunes» — salud (reflexivo
 *        «hacerme», hermano del «cortarme el pelo» c.7xx).
 *     5) «hacer la declaración de la renta este mes» — trámite anual.
 *     6) «escanear el DNI esta tarde» — gestión documental.
 *     7) «reclamar la factura del banco mañana» — finanzas hogar.
 *   REGRESIONES — 24 HITs confirmados: 17 formas de la clase que YA
 *   capturan (ITV/aceite/ruedas/lavar coche, pasaporte, firmar
 *   contrato, cita previa, hipoteca/alquiler PAYMENT, revisar factura,
 *   devolver la llamada ERRAND, enviar informe, mandar currículum,
 *   reservar mesa, entradas ×2, pedir hora al dentista) + 7 regresiones
 *   conocidas (luz PAYMENT, mamá CALL, médico APPOINTMENT, DNI TASK,
 *   taller ERRAND, compra SHOPPING, pelo ERRAND).
 *   CONTROLES — 6 NULLs correctos (negación, duda, pasado ×2, aislado,
 *   figurado «pasar de todo»).
 *   Observaciones laterales (NO de esta clase): «este mes» no ancla
 *   fecha («cambiar las ruedas… este mes» / «renovar el pasaporte este
 *   mes» → HIT pero dueAt=false) y «el día N» tampoco («pagar la
 *   hipoteca el día 1» → PAYMENT dueAt=false) — área parser del
 *   hermano, ya registrado c.845/c.852; verificar antes de candidata.
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // --- CAPTURAS (gaps medidos NULL en c.857) ---
        "tomar la medicación a las 8",
        "responder el correo de Ana hoy",
        "contestar a Juan esta tarde",
        "hacerme un análisis de sangre el lunes",
        "hacer la declaración de la renta este mes",
        "escanear el DNI esta tarde",
        "reclamar la factura del banco mañana",
        // --- REGRESIONES de la clase (ya capturan en c.857) ---
        "pasar la ITV la semana que viene", // TASK
        "cambiar el aceite del coche el sábado", // TASK
        "cambiar las ruedas del coche este mes", // TASK (dueAt=false: «este mes»)
        "lavar el coche el domingo", // HOUSEHOLD
        "renovar el pasaporte este mes", // TASK (dueAt=false: «este mes»)
        "firmar el contrato mañana", // TASK
        "pedir cita previa en el ayuntamiento mañana", // TASK
        "pagar la hipoteca el día 1", // PAYMENT (dueAt=false: «día N»)
        "pagar el alquiler el día 5", // PAYMENT (dueAt=false: «día N»)
        "revisar la factura de la luz esta noche", // TASK
        "devolver la llamada a mi madre esta noche", // ERRAND
        "enviar el informe el viernes", // TASK
        "mandar el currículum mañana", // TASK
        "reservar mesa para el sábado", // TASK
        "comprar las entradas del concierto mañana", // SHOPPING
        "imprimir las entradas esta noche", // TASK
        "pedir hora al dentista mañana", // TASK
        // --- REGRESIONES conocidas: deben reportar HIT ---
        "pagar la luz mañana", // PAYMENT suministros
        "llamar a mamá mañana", // CALL
        "pedir cita con el médico mañana", // APPOINTMENT
        "renovar el DNI la semana que viene", // TASK gestión documental
        "llevar el coche al taller mañana", // ERRAND taller
        "hacer la compra el sábado", // SHOPPING
        "cortarme el pelo mañana", // ERRAND peluquería
        // --- CONTROLES: deben permanecer NULL ---
        "no pasar la ITV", // negación
        "quizá pagar la hipoteca", // duda (hedge c.649)
        "pagué la hipoteca ayer", // narrativa pasado
        "reservar", // verbo aislado
        "pasar de todo", // sentido figurado (indiferencia)
        "respondí el correo ayer" // narrativa pasado
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

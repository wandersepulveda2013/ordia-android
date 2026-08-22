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
 *        RESUELTA en c.859 (extensión lockstep del objeto del piso
 *        «tomar la medicina» c.765 con «medicaci[oó]n»: piso + plantilla
 *        de título + keyword-OBJETO; test
 *        ContextIntentEngineMedicacionFloorTest), movida a REGRESIONES.
 *     2) «responder el correo de Ana hoy» — comunicación pendiente.
 *        RESUELTA en c.860 (piso NUEVO acotado al objeto «correos?»
 *        «responder el correo»: piso + plantilla de título, CERO cambios
 *        en ContextIntent.kt — la keyword ERRAND «correo» ya lleva la
 *        frase al análisis; test ContextIntentEngineResponderCorreoFloorTest),
 *        movida a REGRESIONES.
 *     3) «contestar a Juan esta tarde» — variante coloquial de la 2.
 *        RESUELTA en c.861 (piso NUEVO acotado «contestar a <persona>»:
 *        tras «a» sólo nombre (propio o común — texto analizado en
     *        minúsculas, la captura rápida omite mayúsculas) o posesivo
     *        mi/tu/su — artículos
 *        FUERA por lookahead, «a la pregunta»/«a tiempo» son examen/
 *        adverbio — + plantilla de título + keyword-FRASE «contestar a»
 *        en ContextIntent.kt; test
 *        ContextIntentEngineContestarAPersonaFloorTest), movida a
 *        REGRESIONES.
 *     4) «hacerme un análisis de sangre el lunes» — salud (reflexivo
 *        «hacerme», hermano del «cortarme el pelo» c.842).
 *        RESUELTA en c.862 (piso NUEVO acotado al objeto «an[aá]lisis»
 *        con enclítico reflexivo EXIGIDO — la forma desnuda «hacer un
 *        análisis» es bivalente: análisis de datos/estudio — en
 *        [ERRAND_FLOORS] + plantilla de título; CERO cambios en
 *        ContextIntent.kt: la keyword TASK «hacer» es subcadena de
 *        «hacerme»; test ContextIntentEngineAnalisisSangreFloorTest),
 *        movida a REGRESIONES.
 *     5) «hacer la declaración de la renta este mes» — trámite anual.
 *        RESUELTA en c.863 (piso NUEVO acotado a la frase-objeto
 *        «declaraci[oó]n\s+de\s+la\s+renta» — el objeto desnudo
 *        «declaración» es bivalente: de amor/jurada, medidas NULL — en
 *        [hasStrongTaskImperative] + plantilla de título; CERO cambios
 *        en ContextIntent.kt: la keyword TASK «hacer» ya lleva la frase
 *        al análisis, hermana de c.860/c.862; test
 *        ContextIntentEngineDeclaracionRentaFloorTest), movida a
 *        REGRESIONES. Laterales medidas NULL (candidatas propias):
 *        «declarar la renta…», «presentar la declaración de la renta…»,
 *        «hacer la renta…» (elipsis) y «hacer la declaración este mes»
 *        (desnuda).
 *     6) «escanear el DNI esta tarde» — gestión documental.
 *        RESUELTA en c.864 (piso NUEVO acotado al objeto «dni» con el
 *        verbo monosemántico «escanear» en [hasStrongTaskImperative] +
 *        plantilla de título + lockstep keyword-VERBO «escanear» en
 *        ContextIntent.kt — lección c.751, precedente «votar» c.752 —;
 *        las compuestas «…y enviarlo al banco»/«…por las dos caras»
 *        capturan; guards NULL: negación/duda/pasado/suelto/
 *        «reescanear» (prefijo re-, fuera del ancla); test
 *        ContextIntentEngineEscanearDniFloorTest), movida a
 *        REGRESIONES. Laterales medidas NULL (candidatas propias):
 *        «escanear el contrato…», «escanear las notas…», «escanear el
 *        código QR…» (otros objetos), «fotocopiar el DNI…» (verbo
 *        distinto) y «reescanear el DNI…» (prefijo re-).
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
        "reclamar la factura del banco mañana",
        // --- REGRESIÓN c.864: candidata 6 resuelta (piso nuevo acotado
        // «escanear el DNI» + plantilla de título + lockstep
        // keyword-VERBO «escanear»); era NULL en c.857 ---
        "escanear el DNI esta tarde", // TASK
        // --- REGRESIÓN c.863: candidata 5 resuelta (piso nuevo acotado
        // «declaraci[oó]n de la renta» + plantilla de título); era NULL
        // en c.857 ---
        "hacer la declaración de la renta este mes", // TASK (dueAt=false: «este mes»)
        // --- REGRESIÓN c.859: candidata 1 resuelta (lockstep piso+título+
        // keyword «medicaci[oó]n»); era NULL en c.857 ---
        "tomar la medicación a las 8", // TASK
        // --- REGRESIÓN c.860: candidata 2 resuelta (piso nuevo acotado
        // «responder el correo»); era NULL en c.857 ---
        "responder el correo de Ana hoy", // TASK
        // --- REGRESIÓN c.861: candidata 3 resuelta (piso nuevo acotado
        // «contestar a <persona>» + keyword-FRASE «contestar a»); era
        // NULL en c.857 ---
        "contestar a Juan esta tarde", // TASK
        // --- REGRESIÓN c.862: candidata 4 resuelta (piso nuevo acotado
        // «hacer+enclítico+an[aá]lisis» en ERRAND_FLOORS); era NULL en
        // c.857 ---
        "hacerme un análisis de sangre el lunes", // ERRAND
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

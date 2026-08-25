import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1137 (persistida): DECIMOSEXTA clase de
 * formas cotidianas — VIAJES, RESERVAS Y OCIO FUERA DE CASA dichos
 * como se hablan (reservar mesa/hotel, facturar el vuelo, check-in,
 * billetes de tren/avión, maletas, visado, coche de alquiler, cancelar
 * reservas, pedir vacaciones). Las clases III–XV cerraron verbos/
 * chore/life/enclíticos/errand/admin/dinero/coordinación/mascotas/
 * digital/vehículo/salud/escuela/burocracia — pero el dominio de
 * viajes y reservas tiene frontera real NO sondeada: el olvido tiene
 * coste económico y logístico directo (vuelo perdido, reserva
 * caducada, check-in no hecho, mesa perdida un sábado noche).
 *
 * Misma metodología que [FifteenthClassAdminProbe] (c.1132),
 * [FourteenthClassSchoolProbe] (c.1127) y [ThirteenthClassHealthProbe]
 * (c.1102): frases declarativas cotidianas (compromiso plausible) +
 * regresiones (formas que YA capturan por pisos heredados) +
 * controles (negación, duda, pasado, sustantivo/verbo aislado,
 * declarativo sin compromiso). NO es un test; su salida alimenta el
 * BACKLOG (un ítem por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO
 * (intencionado). HIT sobre un control de PASADO es FALSO POSITIVO
 * (familia hermana del pretérito+MEETING medida c.1127-bis).
 *
 * Cobertura PREVIA relevante medida (keywords heredadas): pisos
 * abiertos «comprar <objeto>» (SHOPPING), «pagar <objeto>» (PAYMENT),
 * «recoger <objeto>» (c.857), «llamar a <alguien>» (CALL), «quedar
 * con» (MEETING c.847), «cancelar <objeto>»; guard de plan negado
 * c.1009/c.1136 («no voy a / no voy al»); guard de pretérito
 * por-familia-de-piso (c.1127-bis: gobierna PAYMENT, no MEETING).
 *
 * RESULTADOS medidos en c.1137 (HEAD base `67b7e7e`, run_probe.sh,
 * motor real; suite UNIÓN OK 8940; smokes 25/25 y 9/9):
 *   CAPTURAS — 15/20 HITs por cobertura HEREDADA de pisos abiertos
 *   (reservar mesa TASK 0.45 con dueAt; reservar hotel/coche TASK;
 *   comprar billetes SHOPPING; recoger billetes ERRAND; cancelar/
 *   confirmar reserva TASK; imprimir tarjetas TASK; preparar maleta
 *   TASK; pedir días de vacaciones TASK; cambiar billete TASK; pagar
 *   alojamiento PAYMENT; llevar maletas TASK 0.46; quedar MEETING) y
 *   5 gaps NULL en CUATRO familias:
 *     a) «check-in del vuelo» — 2/2 NULL hermanas del MISMO evento:
 *        C3 «facturar el vuelo mañana» («facturar» en sentido
 *        aeronáutico NO cubierto — el piso «factura» de c.865 es el
 *        doméstico de reclamación) y C4 «hacer el check-in del vuelo
 *        mañana por la mañana» (anglicismo sin keyword). CANDIDATA
 *        FUERTE: el check-in perdido tiene coste directo (recargo,
 *        asiento perdido) y ventana corta (24-48 h antes del vuelo).
 *        Piso NUEVO acotado «(facturar|hacer el check-in de(l)?) el
 *        vuelo» + keyword (lockstep, lección c.616).
 *     b) «salir para <aeropuerto/estación> a las N» — 1/1 NULL (C9):
 *        la logística previa al viaje (si no sales, lo pierdes todo).
 *        CANDIDATA: piso acotado «salir para (el|la) <lugar>».
 *     c) «sacar el visado antes del viaje» — 1/1 NULL (C11): «sacar»
 *        está acotado a mascota/coche/muela (c.857/c.1119) y «visado»
 *        no es keyword. CANDIDATA (sin visado no hay viaje). OJO
 *        región: el piso «sacar» acotado lo introdujo c.1119 — la
 *        extensión debe coordinarse con su estado (NO TOCAR si sigue
 *        activo; piso propio «sacar el visado» si está cerrado).
 *     d) NOMINAL «el vuelo sale el martes a las 6» — 1/1 NULL (C15):
 *        consistente con TODA la familia de sustantivos+fecha
 *        (documentada c.1136 «empaste de la muela», c.1102) — LATERAL
 *        de diseño, NO gap de esta clase.
 *   REGRESIONES — 7/8 HITs intactos (comprar billetes concierto
 *   SHOPPING, pagar hotel PAYMENT, recoger entradas ERRAND, llamar
 *   aerolínea CALL 0.67, quedar con Ana MEETING, pedir hora dentista
 *   TASK, reservar cita masaje TASK). HALLAZGO inesperado: R8
 *   «llevar a los niños al aeropuerto mañana» → NULL (esperaba HIT
 *   por el piso «llevar a» — C19 «llevar las maletas al coche» SÍ
 *   captura TASK 0.46): el piso «llevar a <personas>» no cubre el
 *   destino aeropuerto/estación. CANDIDATA lateral d) registrada.
 *   CONTROLES — 8/8 NULLs correctos: pasado (K1 reservé, K5 era),
 *   negación de plan (K2 «no voy a coger el avión» — guard c.1009/
 *   c.1136 gobernando), duda (K3), nominal (K4), declarativo sin
 *   compromiso (K6 precio, K8 color), verbo desnudo (K7).
 *   OBSERVACIONES laterales (NO gaps de esta clase): colas
 *   temporales «puente de diciembre»/«Semana Santa»/«esta semana»/
 *   «agosto»/«fin de semana» quedan en el TÍTULO con dueAt=false
 *   (C2/C5/C12/C13/C17) — familia de colas ya documentada
 *   (c.845/c.852/c.1079/c.1102/c.1127/c.1132), área parser.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }

    // --- CANDIDATAS (viajes/reservas cotidianos: compromisos plausibles) ---
    show("C1",  "reservar mesa para el sábado a las 9")
    show("C2",  "reservar el hotel para el puente de diciembre")
    show("C3",  "facturar el vuelo mañana")
    show("C4",  "hacer el check-in del vuelo mañana por la mañana")
    show("C5",  "comprar los billetes del tren de Semana Santa")
    show("C6",  "recoger los billetes en la estación el viernes")
    show("C7",  "cancelar la reserva del hotel antes del jueves")
    show("C8",  "imprimir las tarjetas de embarque esta noche")
    show("C9",  "salir para el aeropuerto a las 5 del lunes")
    show("C10", "preparar la maleta esta noche")
    show("C11", "sacar el visado antes del viaje")
    show("C12", "pedir los días de vacaciones esta semana")
    show("C13", "reservar el coche de alquiler para agosto")
    show("C14", "confirmar la reserva del restaurante mañana")
    show("C15", "el vuelo sale el martes a las 6")
    show("C16", "cambiar el billete de avión la semana que viene")
    show("C17", "pagar el alojamiento del fin de semana")
    show("C18", "apuntarnos a la excursión del sábado")
    show("C19", "llevar las maletas al coche esta noche")
    show("C20", "quedar con los primos en la estación el domingo")

    // --- REGRESIONES (formas que YA capturan — deben seguir HIT) ---
    show("R1", "comprar los billetes del concierto mañana")
    show("R2", "pagar el hotel mañana")
    show("R3", "recoger las entradas el viernes")
    show("R4", "llamar a la aerolínea mañana")
    show("R5", "quedar con Ana para cenar el sábado")
    show("R6", "pedir hora al dentista el lunes")
    show("R7", "reservar cita para el masaje el jueves")
    show("R8", "llevar a los niños al aeropuerto mañana")

    // --- CONTROLES (deben quedarse NULL) ---
    show("K1", "ya reservé el hotel la semana pasada")
    show("K2", "no voy a coger el avión")
    show("K3", "quizá reservemos mesa para el sábado")
    show("K4", "la reserva del hotel")
    show("K5", "el vuelo era de madrugada")
    show("K6", "los billetes del tren cuestan 40 euros")
    show("K7", "reservar")
    show("K8", "la maleta es azul")
}

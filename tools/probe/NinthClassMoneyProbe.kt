import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.892 (persistida): NOVENA clase de formas
 * cotidianas — GESTIONES DE DINERO Y BANCA COTIDIANA (efectivo/cajero,
 * ingresos, divisas, cobros, membresías) + GESTIÓN DE CITAS (pedir/confirmar/
 * cambiar) + vida cotidiana adyacente (cena, deberes). La clase OCTAVA
 * (gestiones de la vida adulta: vehículo, trámites, documentos, renta,
 * facturas, c.857) quedó AGOTADA en c.865 junto con sus laterales (c.878/
 * c.885/c.886/c.889); esta clase explora la frontera siguiente: el dinero
 * dicho como se habla en español cotidiano.
 *
 * Misma metodología que [EighthClassAdminProbe] (c.857) y
 * [SeventhClassErrandProbe] (c.845): frases declarativas cotidianas
 * (compromiso plausible) + regresiones (formas que YA capturan) +
 * controles (negación, duda, narrativa pasado, verbo aislado, sentido
 * figurado/bivalente). NO es un test; su salida alimenta el BACKLOG (un
 * ítem/forma por ciclo, doctrina anti-overreach; cero cambios de producto
 * en el ciclo de descubrimiento, convención c.834/c.845/c.857).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es un
 * GAP de captura (olvido silencioso P1) si el enunciado es un compromiso
 * plausible del usuario. NULL sobre controles es CORRECTO (intencionado).
 *
 * Estado medido en c.892 (HEAD d112367, run_probe.sh, motor real):
 *   CAPTURAS — 8 familias NULL (gaps confirmados; al resolverse cada
 *   una se mueve a REGRESIONES con su ciclo, convención c.857):
 *     1) «sacar dinero del cajero mañana/antes del viaje» + variante
 *        «sacar dinero del atm» + «ir al cajero mañana» — efectivo/
 *        cajero (el olvido clásico antes de un viaje).
 *     2) «ingresar el dinero/el reembolso» + «hacer el ingreso» —
 *        depósito en banco (hermanas verbo ingresar/elipsis ingreso).
 *     3) «depositar el cheque mañana» — depósito (dialectal).
 *     4) «cobrar la nómina/el reembolso» — cobros.
 *     5) «dar de baja el gimnasio el lunes» — baja de membresía.
 *     6) «hacer la cena esta noche» / «preparar el almuerzo mañana» —
 *        comida cotidiana.
 *     7) «descongelar la carne por la noche» — comida invariante.
 *     8) «hacer los deberes por la tarde» — estudio cotidiano.
 *   HITS de la clase (ya capturan — regresiones internas):
 *     «cambiar euros/dólares» TASK (piso abierto «cambiar <objeto>»
 *       c.710), «pasar por el banco»/«ir al banco» ERRAND (keyword),
 *       «revisar el extracto del banco» TASK, «pagar la tarjeta»
 *       PAYMENT, «pedir cita con el dentista» APPOINTMENT, «cambiar la
 *       cita del dentista» APPOINTMENT, «cocinar el almuerzo»
 *       HOUSEHOLD.
 *   REGRESIONES conocidas — 12 HITs (luz PAYMENT, cancelar cita +
 *   suscripción envolvente c.654, gasolina ERRAND, celular TASK,
 *   renta TASK c.863, factura TASK c.865, medicación TASK c.859,
 *   contestar TASK c.861, lavavajillas HOUSEHOLD, compra SHOPPING,
 *   quedar MEETING c.847).
 *   CONTROLES — 12 NULLs correctos (negación, duda, pasado ×4, aislado
 *   ×2, bivalence «ingresar en el club»/«sacar a bailar») + 2 HITs
 *   bivalentes medidos («cambiar de tema»/«cambiar de planes»: piso
 *   abierto «cambiar <objeto>» c.710, overreach deliberado documentado
 *   — OBSERVACIÓN, no candidata).
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // --- DESCUBRIMIENTO: candidatas de la NOVENA clase (medir NULL/HIT) ---
        // DINERO EN EFECTIVO Y CAJERO
        "sacar dinero del cajero mañana",
        "sacar dinero del cajero antes del viaje",
        "sacar dinero del atm el viernes",
        "ir al cajero mañana",
        // INGRESOS Y DIVISAS
        "ingresar el dinero mañana",
        "ingresar el reembolso el lunes",
        "hacer el ingreso mañana",
        "cambiar euros antes del viaje",
        "cambiar dólares en el banco mañana",
        "depositar el cheque mañana",
        // COBROS
        "cobrar la nómina mañana",
        "cobrar el reembolso el lunes",
        // BANCO COTIDIANO (probable HIT por keyword «banco»)
        "pasar por el banco mañana",
        "ir al banco mañana",
        "revisar el extracto del banco mañana",
        "pagar la tarjeta el viernes",
        // MEMBRESÍAS Y CITAS (gestión/baja)
        "dar de baja el gimnasio el lunes",
        "pedir cita con el dentista mañana",
        "cambiar la cita del dentista para el martes",
        // VIDA COTIDIANA ADYACENTE (comida/deberes)
        "hacer la cena esta noche",
        "preparar el almuerzo mañana",
        "descongelar la carne por la noche",
        "cocinar el almuerzo mañana",
        "hacer los deberes por la tarde",
        // --- REGRESIONES: deben seguir HIT ---
        "pagar la luz mañana", // PAYMENT suministros
        "cancelar la cita médica mañana", // TASK envolvente c.654
        "cancelar la suscripción el lunes", // TASK/PAYMENT envolvente c.654
        "echar gasolina mañana", // ERRAND c.829
        "cargar el celular esta noche", // TASK c.751
        "hacer la declaración de la renta este mes", // TASK c.863
        "reclamar la factura del banco mañana", // TASK c.865
        "tomar la medicación a las 8", // TASK c.859
        "contestar a Juan esta tarde", // TASK c.861
        "poner el lavavajillas esta noche", // HOUSEHOLD c.738
        "hacer la compra el sábado", // SHOPPING
        "quedar con Ana el viernes", // MEETING c.847
        // --- CONTROLES: deben permanecer NULL ---
        "no sacar dinero del cajero mañana", // negación
        "quizá ingresar el dinero mañana", // duda (hedge c.649)
        "saqué dinero ayer", // narrativa pasado
        "ingresó el reembolso ayer", // narrativa pasado
        "cobré la nómina ayer", // narrativa pasado
        "cambié euros ayer", // narrativa pasado
        "deberes", // sustantivo aislado
        "ingresar", // verbo aislado
        "ingresar en el club mañana", // bivalente (admitirse/comandar)
        "cambiar de tema mañana", // bivalente (de tema/ciudad/ropa)
        "cambiar de planes mañana", // bivalente (planes ≠ divisas)
        "sacar a bailar a María mañana" // bivalente (a bailar ≠ dinero)
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

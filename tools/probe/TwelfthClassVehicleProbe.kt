import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1079 (persistida): DUODÉCIMA clase de
 * formas cotidianas — VIDA CON VEHÍCULO (el mantenimiento del coche,
 * los trámites del conductor y el estacionamiento dichos como se
 * habla: lavar el coche, cambiar el aceite, la multa, el seguro,
 * el carnet, las ruedas, el parking). Las clases VI (enclíticos,
 * c.834), VII (diligencias sociales, c.845), VIII (vida adulta,
 * c.857), IX (dinero y banca cotidiana, c.890/c.892), X/DÉCIMA
 * (mascotas, c.1007) y XI/UNDÉCIMA (vida digital, c.1026) quedaron
 * AGOTADAS; la frontera siguiente es el vehículo — dominio
 * cotidiano con coste real de olvido (aceite sin cambiar, multa
 * que recarga, seguro/carnet vencidos, ruedas de temporada).
 *
 * Misma metodología que [EleventhClassDigitalProbe] (c.1026),
 * [TenthClassPetProbe] (c.1007), [NinthClassMoneyProbe] (c.892),
 * [EighthClassAdminProbe] (c.857) y [SeventhClassErrandProbe]
 * (c.845): frases declarativas cotidianas (compromiso plausible) +
 * regresiones (formas que YA capturan) + controles (negación
 * compuesta, duda, pasado, sustantivo aislado, figurado,
 * no-compromiso). NO es un test; su salida alimenta el BACKLOG
 * (un ítem por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana
 * es un GAP de captura (olvido silencioso P1) si el enunciado es
 * un compromiso plausible del usuario. NULL sobre controles es
 * CORRECTO (intencionado).
 *
 * Incluye como candidata medida la lateral documentada «cargar el
 * carro» (diagonal dialectal LatAm del piso c.853, acotada en
 * deliberación propia — «carro» no es keyword: gate c.751).
 *
 * Estado medido en c.1079 (HEAD `df67fcd8`, run_probe.sh, motor
 * real; suite OK (8209), smoke 25/25, automation 9/9):
 *   CAPTURAS — 11/14 HITs por cobertura HEREDADA de pisos
 *   abiertos y keywords de familia (lavar el coche HOUSEHOLD vía
 *   keyword-verbo de quehaceres; cambiar el aceite TASK vía piso
 *   abierto «cambiar <objeto>» c.710; renovar el seguro/carnet
 *   TASK vía piso abierto «renovar <objeto>» c.698; revisar los
 *   frenos TASK vía piso abierto «revisar <objeto>»; pagar la
 *   multa/parking PAYMENT; llevar a la revisión ERRAND; comprar
 *   neumáticos SHOPPING; lavar la moto HOUSEHOLD; aspiradora al
 *   coche HOUSEHOLD interop c.742) y 3 gaps NULL:
 *     a) «poner las ruedas de invierno en diciembre» — «poner»
 *        bivalente y «ruedas» sin keyword (gate c.751). CANDIDATA.
 *     b) «cargar el carro esta noche» — lateral documentada del
 *        piso c.853 (diagonal dialectal LatAm; «carro» ni keyword
 *        ni ancla). CANDIDATA.
 *     c) «inflar las ruedas de la bici hoy» — «inflar» sin piso
 *        ni keyword; «ruedas»/«bici» sin keyword. CANDIDATA.
 *   REGRESIONES — 8/8 HITs intactos (taller ERRAND c.684, ITV
 *   TASK c.768, gasolina ERRAND c.829, cargar el coche TASK
 *   c.853, llamar CALL 0.67, leche SHOPPING, luz PAYMENT, médico
 *   APPOINTMENT 0.85).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta «no voy a
 *   lavar el coche hoy» — el guard de plan/volición c.1009
 *   alcanza el dominio vehículo; duda subjuntivo; pasado ×2;
 *   sustantivo aislado «el seguro del coche»; verbo aislado;
 *   estado «mi coche es muy cómodo»; figurado «ir como un coche
 *   sin frenos» — la subcadena «coche» queda inerte < umbral,
 *   verificado en c.853).
 *   OBSERVACIONES laterales (parser/títulos, NO de esta clase):
 *   «este mes»/«en marzo»/«de tráfico esta semana» quedan en el
 *   TÍTULO (C3/C6/C7) — lateral ABIERTA de colas ya documentada
 *   de la familia de pisos; dueAt=false en «este mes»/«en marzo»/
 *   «esta semana» y dueAt=true en «mañana»/«la semana que viene»
 *   — anclaje temporal del context, consistente con lo registrado
 *   c.845/c.852 para «este mes».
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

    // --- CANDIDATAS (vehículo: compromisos cotidianos plausibles) ---
    show("C1", "lavar el coche esta tarde")
    show("C2", "cambiar el aceite del coche mañana")
    show("C3", "renovar el seguro del coche este mes")
    show("C4", "revisar los frenos del coche la semana que viene")
    show("C5", "poner las ruedas de invierno en diciembre")
    show("C6", "pagar la multa de tráfico esta semana")
    show("C7", "renovar el carnet de conducir en marzo")
    show("C8", "llevar el coche a la revisión mañana")
    show("C9", "cargar el carro esta noche")
    show("C10", "comprar neumáticos nuevos el sábado")
    show("C11", "lavar la moto el domingo")
    show("C12", "inflar las ruedas de la bici hoy")
    show("C13", "pagar el parking del mes mañana")
    show("C14", "pasar la aspiradora al coche esta tarde")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "llevar el coche al taller mañana")
    show("R2", "pasar la ITV este mes")
    show("R3", "echar gasolina esta tarde")
    show("R4", "cargar el coche esta noche")
    show("R5", "llamar a mamá esta noche")
    show("R6", "comprar leche esta tarde")
    show("R7", "pagar la luz el día 5")
    show("R8", "ir al médico el lunes a las 5")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a lavar el coche hoy")
    show("G2", "quizá lave el coche el sábado")
    show("G3", "lavé el coche ayer")
    show("G4", "el seguro del coche")
    show("G5", "lavar")
    show("G6", "mi coche es muy cómodo")
    show("G7", "ir como un coche sin frenos")
    show("G8", "pagué la multa ayer")
}

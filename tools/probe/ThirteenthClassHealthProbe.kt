import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1102 (persistida): DECIMOTERCERA clase de
 * formas cotidianas — SALUD Y AUTOCUIDADO (el cuerpo y su
 * mantenimiento dichos como se habla: el dentista, las analíticas en
 * ayunas, la farmacia, las gafas y la vista, la vacuna estacional,
 * la fisioterapia, el pediatra del niño, el psicólogo, la ecografía).
 * Las clases VI (enclíticos, c.834), VII (diligencias sociales,
 * c.845), VIII (vida adulta, c.857), IX (dinero y banca cotidiana,
 * c.890/c.892), X/DÉCIMA (mascotas, c.1007), XI/UNDÉCIMA (vida
 * digital, c.1026) y XII/DUODÉCIMA (vida con vehículo, c.1079)
 * quedaron agotadas o casi ((c) EN CURSO SU c.1097); la frontera
 * siguiente es la salud cotidiana — dominio con coste real de olvido
 * (analítica en ayunas perdida, revisión anual que pasa, vacuna
 * estacional, gafas que ya no enfocan).
 *
 * Misma metodología que [TwelfthClassVehicleProbe] (c.1079),
 * [EleventhClassDigitalProbe] (c.1026), [TenthClassPetProbe] (c.1007),
 * [NinthClassMoneyProbe] (c.892), [EighthClassAdminProbe] (c.857) y
 * [SeventhClassErrandProbe] (c.845): frases declarativas cotidianas
 * (compromiso plausible) + regresiones (formas que YA capturan) +
 * controles (negación compuesta, duda, pasado, sustantivo aislado,
 * verbo aislado, estado, dolor sin compromiso). NO es un test; su
 * salida alimenta el BACKLOG (un ítem por ciclo, doctrina
 * anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO
 * (intencionado).
 *
 * Cobertura PREVIA relevante medida (keywords heredadas):
 * APPOINTMENT tiene «cita con», «cita médica», «dentista», «doctor»,
 * «médico», «especialista», «consulta», «revisión», «chequeo»,
 * «terapia», «psicólogo», «nutricionista» (ContextIntent.kt l.300);
 * «vacuna» keyword-OBJETO c.1044; «tensión» keyword-OBJETO c.772;
 * «medicación» keyword-OBJETO c.859; «farmacia» keyword ERRAND.
 *
 * Estado medido en c.1102 (HEAD `c56291b` tras ff — región context
 * intacta desde SU c.1095 `5b666f6`; run_probe.sh, motor real; suite
 * UNIÓN OK (8512 = 8481 + 14 [SU c.1095 parser] + 17 [SU c.1096
 * assistant]) medida en ESTE run sobre la base final; smoke 25/25,
 * automation 9/9):
 *   CAPTURAS — 7/14 HITs por cobertura HEREDADA (pedir hora al
 *   dentista TASK [piso abierto «pedir <objeto>» + keyword]; renovar
 *   las gafas TASK [piso abierto «renovar <objeto>» c.698]; recoger
 *   los resultados del análisis ERRAND [piso «recoger <objeto>»];
 *   ponerme la vacuna de la gripe TASK [piso reflexivo c.1044];
 *   recoger la medicación en la farmacia ERRAND [keywords c.859];
 *   comprar los medicamentos SHOPPING; pedir turno para el ginecólogo
 *   TASK) y 7 gaps NULL:
 *     a) «hacerme las analíticas en ayunas la semana que viene» —
 *        «analíticas» sin keyword y «hacerme» reflexivo sin piso
 *        (gate c.751). CANDIDATA.
 *     b) «llevar al niño al pediatra el viernes» — «pediatra» NO es
 *        keyword de APPOINTMENT (a diferencia de «médico»/«dentista»).
 *        CANDIDATA.
 *     c) «sacar cita para el oftalmólogo mañana» — «sacar» sólo pisa
 *        acotado a mascota/coche; «oftalmólogo» sin keyword.
 *        CANDIDATA.
 *     d) «ir a fisioterapia el martes» — la subcadena «terapia»
 *        dentro de «fisioterapia» es INERTE (matching por frontera
 *        de palabra, consistente con c.853); «fisioterapia» sin
 *        keyword propia. CANDIDATA.
 *     e) «hacerme la revisión de la vista este mes» — keyword
 *        «revisión» existe pero el reflexivo «hacerme» no aporta
 *        señal de intención → bajo umbral (0.45). CANDIDATA.
 *     f) «ir a la limpieza dental la semana que viene» — «limpieza
 *        dental» sin keyword; «ir a <sustantivo>» no es piso.
 *        CANDIDATA.
 *     g) «hacerse la ecografía el miércoles» — «ecografía» sin
 *        keyword; «hacerse» reflexivo sin piso. CANDIDATA.
 *   REGRESIONES — 8/8 HITs intactos (médico APPOINTMENT 0.85,
 *   medir la tensión TASK c.772, llamar CALL 0.67, leche SHOPPING
 *   0.47, luz PAYMENT, taller ERRAND c.684, sacar al perro HOUSEHOLD,
 *   lavar el coche HOUSEHOLD).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta «no voy a ir
 *   al dentista mañana» — guard de plan/volición c.1009; duda
 *   subjuntivo «quizá vaya…»; pasado «fui al dentista ayer»;
 *   sustantivo aislado «los análisis»; verbo aislado «pedir»;
 *   estado «mi dentista es muy bueno» (keyword sola < umbral);
 *   dolor sin compromiso «me duele una muela»; pasado «hice las
 *   analíticas ayer»).
 *   OBSERVACIONES laterales (títulos, NO de esta clase): «este mes»
 *   queda en el TÍTULO (C3/C9) y dueAt=false — consistente con lo
 *   registrado c.845/c.852/c.1079 (lateral ABIERTA de colas ya
 *   documentada de la familia de pisos); C14 dialectal LatAm
 *   «pedir turno» YA captura (hermana de «pedir hora» C1).
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

    // --- CANDIDATAS (salud/autocuidado: compromisos cotidianos plausibles) ---
    show("C1",  "pedir hora al dentista mañana")
    show("C2",  "hacerme las analíticas en ayunas la semana que viene")
    show("C3",  "renovar las gafas este mes")
    show("C4",  "recoger los resultados del análisis el jueves")
    show("C5",  "llevar al niño al pediatra el viernes")
    show("C6",  "sacar cita para el oftalmólogo mañana")
    show("C7",  "ir a fisioterapia el martes")
    show("C8",  "ponerme la vacuna de la gripe en octubre")
    show("C9",  "hacerme la revisión de la vista este mes")
    show("C10", "recoger la medicación en la farmacia esta tarde")
    show("C11", "comprar los medicamentos mañana")
    show("C12", "ir a la limpieza dental la semana que viene")
    show("C13", "hacerse la ecografía el miércoles")
    show("C14", "pedir turno para el ginecólogo mañana")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "ir al médico el lunes a las 5")
    show("R2", "medir la tensión por la mañana")
    show("R3", "llamar a mamá esta noche")
    show("R4", "comprar leche esta tarde")
    show("R5", "pagar la luz el día 5")
    show("R6", "llevar el coche al taller mañana")
    show("R7", "sacar al perro esta tarde")
    show("R8", "lavar el coche esta tarde")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a ir al dentista mañana")
    show("G2", "quizá vaya al dentista el sábado")
    show("G3", "fui al dentista ayer")
    show("G4", "los análisis")
    show("G5", "pedir")
    show("G6", "mi dentista es muy bueno")
    show("G7", "me duele una muela")
    show("G8", "hice las analíticas ayer")
}

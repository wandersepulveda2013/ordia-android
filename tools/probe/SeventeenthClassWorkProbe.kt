import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1147 (persistida; renumerada c.1143→c.1147
 * por carrera de marcador: el hermano fijó c.1143 «sellar el paro»
 * primero en el remoto, primer-marcador-gana, lección c.1077):
 * DECIMOSÉPTIMA clase de formas cotidianas — VIDA LABORAL dicho como
 * se habla (turnos, currículum, entrevistas de trabajo, contratos,
 * cliente/jefe, guardias, formación obligatoria, horas extra, días
 * libres). Las clases III–XVI cerraron verbos/chore/life/enclíticos/
 * errand/admin/dinero/coordinación/mascotas/digital/vehículo/salud/
 * escuela/burocracia/viajes — pero el dominio laboral tiene frontera
 * real NO sondeada pese a ser de los de mayor frecuencia diaria: el
 * olvido tiene coste económico y profesional directo (entrevista
 * perdida, turno descubierto, formación obligatoria caducada, día
 * libre no pedido, plazo de currículum pasado). Sugerida como
 * «auditoría vida laboral» en el cierre de c.1132.
 *
 * Misma metodología que [SixteenthClassTravelProbe] (c.1137),
 * [FifteenthClassAdminProbe] (c.1132), [FourteenthClassSchoolProbe]
 * (c.1127) y [ThirteenthClassHealthProbe] (c.1102): frases
 * declarativas cotidianas (compromiso plausible) + regresiones (formas
 * que YA capturan por pisos heredados) + controles (negación, duda,
 * pasado, sustantivo/verbo aislado, declarativo sin compromiso,
 * tercera persona). NO es un test; su salida alimenta el BACKLOG (un
 * ítem por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO
 * (intencionado). HIT sobre un control de PASADO es FALSO POSITIVO
 * (familia hermana del pretérito+MEETING medida c.1127-bis).
 *
 * Cobertura PREVIA relevante medida (keywords/pisos heredados): pisos
 * «enviar <objeto>» c.692, «entregar <objeto>» c.693, «revisar» c.691;
 * keywords COMMITMENT_WORK («informe», «reporte», «presentación»,
 * «cliente», «proyecto de», «trabajo de», «asignación»); «cobrar la
 * nómina» c.895b (keyword-objeto + piso acotado); «entrevista» keyword
 * (familia clase/taller/curso/webinar); «llevarle <objeto> a
 * <persona>» enclítico c.854; pisos abiertos «quedar con» (MEETING
 * c.847), «llamar a» (CALL), «pedir»/«confirmar»/«imprimir»/«llevar»/
 * «firmar»/«cambiar» (TASK, medidos c.1127/c.1132/c.1137); guard de
 * plan negado c.1009/c.1136 («no voy a / no voy al»); guard de
 * pretérito por-familia-de-piso (c.1127-bis).
 *
 * RESULTADOS medidos en c.1147 (HEAD base `7de9143` post-integración
 * hermanos c.1139/c.1141, run_probe.sh, motor real; suite UNIÓN OK
 * 9074 medida en el remoto en c.1141 — este ciclo es CERO producto):
 *   CAPTURAS — 12/20 HITs por cobertura HEREDADA (enviar informe TASK
 *   c.692; entregar presentación TASK c.693; revisar contrato TASK
 *   c.691; cambiar turno TASK [piso «cambiar»]; pedir día libre/aumento
 *   TASK [piso «pedir»]; firmar contrato TASK; contestar al cliente
 *   TASK [«contestar»]; hablar con RRHH CALL 0.52; confirmar guardia
 *   TASK [«confirmar»]; mandar factura TASK [«mandar»]; quedar con
 *   cliente MEETING c.847) y 8 NULL en CINCO familias:
 *     a) «echar el currículum en la oferta de infojobs» — 1/1 NULL
 *        (C5): «echar» bivalente (c.829) y «currículum» no es keyword.
 *        CANDIDATA FUERTE: la oferta de empleo tiene plazo y el olvido
 *        cuesta la oportunidad entera. Piso NUEVO acotado «echar (el)?
 *        curr[ií]culum» + keyword (lockstep c.616).
 *     b) «cubrir el turno del sábado» — 1/1 NULL (C7): turno descubierto
 *        deja colgado al compañero/equipo, ventana corta. CANDIDATA:
 *        piso acotado «cubrir (el|la|mi|tu)? turnos?» + keyword.
 *     c) «hacer el curso de prevención antes del día 30» — 1/1 NULL
 *        (C16): formación obligatoria con plazo (sin ella no se puede
 *        trabajar / sanción). «curso» es keyword STUDY pero «hacer el
 *        curso» no casa ningún piso (hermana del «hacer el check-in»
 *        de c.1137). CANDIDATA: piso acotado «hacer (el|un)? curso de
 *        <objeto>» — evaluar frontera con STUDY.
 *     d) «preparar la entrevista de mañana» — 1/1 NULL (C4): «preparar»
 *        cubre objetos cerrados (cena/maleta…) pero no «entrevista»;
 *        «de mañana» no es cola temporal («mañana» sustantivo de
 *        parte del día). CANDIDATA: extender objeto del piso «preparar»
 *        (entrevista perdida = oportunidad perdida; coste hermano de
 *        C5).
 *     e) «llevar el portátil al trabajo mañana» — 1/1 NULL (C20):
 *        HALLAZGO hermano del de c.1137 («llevar las maletas al coche»
 *        SÍ HIT 0.46): el piso «llevar <objeto>» tiene alternancia de
 *        objetos cerrada sin «portátil/ordenador». Olvidar el portátil
 *        = no poder trabajar. CANDIDATA: extensión de objeto.
 *   OBSERVACIONES laterales (NO gaps de esta clase): C17 «turno de
 *   noche esta semana» NULL nominal — consistente con la familia
 *   sustantivo+fecha (documentada c.1102/c.1136/c.1137); C18 «empiezo
 *   el turno de noche el lunes» NULL — declarativo de calendario
 *   (hermano FRONTERIZO de la familia FP «empieza el campamento»
 *   c.1135/c.1141 [marcador activo del hermano, NO TOCAR]: aquí NULL
 *   es conservador-aceptable, capturarla exigiría resolver primero la
 *   frontera declarativo/compromiso); C13 «hacer horas extra el
 *   sábado» NULL — declarativa ambigua (compromiso débil), NO
 *   candidata fuerte.
 *   REGRESIONES — 8/8 HITs intactos (quedar jefe MEETING, llamar
 *   cliente CALL 0.67, cobrar nómina TASK c.895b, enviar/revisar
 *   correo TASK c.692/c.691, preparar cena HOUSEHOLD, imprimir informe
 *   TASK, llevarle informe al jefe ERRAND c.854).
 *   CONTROLES — 8/8 NULLs correctos: pasado (K1 envié, K8 «la
 *   entrevista fue ayer» — copulativa pretérito NO-MEETING también
 *   limpia), negación de plan (K2 gobernada por c.1009/c.1136), duda
 *   subjuntivo (K3), nominal (K4), declarativo sin compromiso
 *   (K5/K6), verbo desnudo (K7).
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

    // --- CANDIDATAS (vida laboral cotidiana: compromisos plausibles) ---
    show("C1",  "enviar el informe antes del viernes")
    show("C2",  "entregar la presentación mañana")
    show("C3",  "revisar el contrato antes de firmarlo")
    show("C4",  "preparar la entrevista de mañana")
    show("C5",  "echar el currículum en la oferta de infojobs")
    show("C6",  "cambiar el turno con marta esta semana")
    show("C7",  "cubrir el turno del sábado")
    show("C8",  "pedir el día libre para el puente")
    show("C9",  "firmar el contrato mañana")
    show("C10", "contestar al cliente esta tarde")
    show("C11", "hablar con recursos humanos esta semana")
    show("C12", "pedir el aumento al jefe el lunes")
    show("C13", "hacer horas extra el sábado")
    show("C14", "confirmar la guardia del fin de semana")
    show("C15", "mandar la factura al cliente hoy")
    show("C16", "hacer el curso de prevención antes del día 30")
    show("C17", "turno de noche esta semana")
    show("C18", "empiezo el turno de noche el lunes")
    show("C19", "quedar con el cliente para cerrar el trato")
    show("C20", "llevar el portátil al trabajo mañana")

    // --- REGRESIONES (formas que YA capturan — deben seguir HIT) ---
    show("R1", "quedar con el jefe mañana")
    show("R2", "llamar al cliente mañana")
    show("R3", "cobrar la nómina el día 30")
    show("R4", "enviar el correo mañana")
    show("R5", "revisar el correo esta noche")
    show("R6", "preparar la cena esta noche")
    show("R7", "imprimir el informe esta tarde")
    show("R8", "llevarle el informe al jefe mañana")

    // --- CONTROLES (deben quedarse NULL) ---
    show("K1", "envié el informe ayer")
    show("K2", "no voy a enviar el informe")
    show("K3", "quizá cambie de turno esta semana")
    show("K4", "el contrato de trabajo")
    show("K5", "mi jefe es muy exigente")
    show("K6", "el turno de noche es duro")
    show("K7", "enviar")
    show("K8", "la entrevista fue ayer")
}

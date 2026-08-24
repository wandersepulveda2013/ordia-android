import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1007 (persistida): DÉCIMA clase de
 * formas cotidianas — MASCOTAS (el cuidado de animales dicho como
 * se habla: veterinario, vacunas, pastillas, pienso, paseos,
 * higiene). Las clases VI (enclíticos, c.834), VII (diligencias
 * sociales, c.845), VIII (vida adulta, c.857) y IX (coordinación y
 * préstamos, c.890) quedaron AGOTADAS (últimas laterales: NOVENA
 * cerrada c.901 «dar las gracias»; OCTAVA cerrada c.865; SÉPTIMA
 * cerrada c.856); la frontera siguiente es el ámbito de las
 * mascotas — dominio cotidiano con coste real de olvido (vacunas,
 * medicación, citas veterinarias).
 *
 * Misma metodología que [NinthClassCoordinationProbe] (c.890),
 * [EighthClassAdminProbe] (c.857) y [SeventhClassErrandProbe]
 * (c.845): frases declarativas cotidianas (compromiso plausible) +
 * regresiones (formas que YA capturan) + controles (negación,
 * duda, pasado, sustantivo aislado, figurado, no-compromiso). NO
 * es un test; su salida alimenta el BACKLOG (un ítem por ciclo,
 * doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana
 * es un GAP de captura (olvido silencioso P1) si el enunciado es
 * un compromiso plausible del usuario. NULL sobre controles es
 * CORRECTO (intencionado).
 *
 * Estado medido en c.1007 (HEAD 1794a56 c.1006 propio;
 * run_probe.sh, motor real; suite OK (7160)):
 *   CAPTURAS — 9/14 HITs (llevar al veterinario HOUSEHOLD,
 *   sacar al perro HOUSEHOLD, comprar pienso SHOPPING, vacunar
 *   HOUSEHOLD, bañar HOUSEHOLD, recoger medicación ERRAND,
 *   pedir cita TASK, limpiar jaula HOUSEHOLD, cambiar agua
 *   pecera TASK) y 5 gaps NULL confirmados:
 *     1) «ponerle la vacuna al perro el mes que viene» — dativo
 *        enclítico «ponerle» + objeto «vacuna» (la transitiva
 *        «vacunar» ya captura). CANDIDATA P1.
 *     2) «darle la pastilla al perro a las 9» — dativo «darle
 *        la pastilla» (la hermana humana «tomar la medicación»
 *        ya captura TASK). CANDIDATA P1.
 *     3) «cortarle las uñas al gato mañana» — dativo «cortarle»
 *        con objeto «las uñas» (hermana del fix c.1006 «cortarle
 *        el pelo», dominio mascota). CANDIDATA P1.
 *     4) «desparasitar al perro este mes» — verbo monosemántico
 *        sin piso. CANDIDATA P1.
 *     5) «pasear al perro después de cenar» — sinónimo directo
 *        de «sacar al perro» (que SÍ captura HOUSEHOLD).
 *        CANDIDATA P1.
 *   REGRESIONES — 8/8 HITs intactos (médico APPOINTMENT 0.85,
 *   cortarme el pelo ERRAND, leche SHOPPING, luz PAYMENT, llamar
 *   CALL 0.67, medicación TASK, taller ERRAND, avisar TASK).
 *   CONTROLES — 7/8 NULLs correctos (duda, pasado ×2, sustantivo,
 *   aislado, figurado «trabajar como un perro», no-compromiso
 *   «mi perro es muy cariñoso») y 1 FALSO POSITIVO SISTÉMICO:
 *   G1 «no voy a sacar al perro hoy» → HIT HOUSEHOLD — la
 *   negación COMPUESTA «no voy a + infinitivo» NO está cubierta
 *   por `obligationWrapperIsNegated` (c.681/c.835, sólo
 *   obligación/condicional) ni por los lookbehind `(?<!no )` /
 *   cláusulas `imperativeIsNegated` (exigen «no» INMEDIATO al
 *   verbo). Micro-sonda efímera c.1007 (`/tmp/probe1007/Probe.kt`,
 *   19 casos): 10/12 pisos representativos capturan la forma
 *   «no voy a …» (perro, llamar, luz, leche, médico, pelo,
 *   lavadora, taller, medicación, vacunar — sólo «avisar» y
 *   «contestar» quedan NULL) + «no pienso ir al médico» HIT
 *   APPOINTMENT + «ya no voy a llamar a mamá» HIT CALL: la
 *   captura pasiva persiste EXACTAMENTE lo opuesto de lo dicho
 *   (misma clase P1 que c.681/c.835). CANDIDATA P1 SISTÉMICA
 *   TOP-prioridad (guard de envolvente de PLAN/VOLICIÓN negado:
 *   «no voy/vamos a», «no pienso/pensamos», «no quiero/queremos»,
 *   «no planeo/planeamos», «no cuento/contamos con» — 1ª persona;
 *   2ª persona «no vas a…» queda FUERA, lateral documentada).
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

    // --- CANDIDATAS (mascotas: compromisos cotidianos plausibles) ---
    show("C1", "llevar al perro al veterinario mañana")
    show("C2", "sacar al perro a las 8")
    show("C3", "comprar pienso para el gato esta tarde")
    show("C4", "vacunar al gato el lunes")
    show("C5", "ponerle la vacuna al perro el mes que viene")
    show("C6", "darle la pastilla al perro a las 9")
    show("C7", "bañar al perro el sábado")
    show("C8", "cortarle las uñas al gato mañana")
    show("C9", "recoger la medicación del perro en el veterinario")
    show("C10", "pedir cita en el veterinario mañana")
    show("C11", "limpiar la jaula del hámster el domingo")
    show("C12", "cambiar el agua de la pecera esta noche")
    show("C13", "desparasitar al perro este mes")
    show("C14", "pasear al perro después de cenar")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "ir al médico el lunes a las 5")
    show("R2", "cortarme el pelo mañana")
    show("R3", "comprar leche esta tarde")
    show("R4", "pagar la luz el día 5")
    show("R5", "llamar a mamá esta noche")
    show("R6", "tomar la medicación a las 8")
    show("R7", "llevar el coche al taller mañana")
    show("R8", "avisar a Ana de que llegamos tarde")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a sacar al perro hoy")
    show("G2", "quizá vacune al gato el lunes")
    show("G3", "llevé al perro al veterinario ayer")
    show("G4", "el pienso del gato")
    show("G5", "veterinario")
    show("G6", "trabajar como un perro toda la semana")
    show("G7", "mi perro es muy cariñoso")
    show("G8", "compré pienso ayer")
}

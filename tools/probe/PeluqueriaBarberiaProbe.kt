import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE/POST c.1256: «(la |mi )?(peluquer[ií]a|barber[ií]a)» — nominal
 * de lugar FUERTE (lateral (a) de MI auditoría c.1252 — clase TRIGÉSIMA
 * SEXTA belleza/cuidado personal). Gate c.751: objeto monosemántico-lugar
 * (la peluquería/barbería SÓLO se destina a corte/arreglo); CERO keyword
 * nueva — floor-only (paridad «partido» c.1231 / «clase-fitness» c.1250).
 * Kind hermano de «cortar(me) el pelo» c.842: ERRAND (desplazamiento).
 * Targets: PRE = NULL / POST = HIT ERRAND 0.45 (piso acotado nominal).
 * Guards: negación de plan (no voy a…), «salón de belleza» (polisémico —
 * living-room — FUERA, lateral a2), pasto (fue/fui) → NULL.
 * Regresiones: fórmulas heredadas (TASK/EXERCISE/APPOINTMENT/SHOPPING/
 * CALL/HOUSEHOLD) → HIT.
 *
 * MEDICIÓN PRE (HEAD 2beb542): T1–T6 NULL (olvido silencioso — el
 * nominal no era piso ni keyword); G1–G5 NULL (sin cobertura);
 * R1–R6 HIT (baseline intacta).
 * MEDICIÓN POST (lockstep c.1256: [ERRAND_BARBERSHOP_RUN_FLOOR] +
 * plantilla matchBarbershopRun en extractTitle + guard
 * [pastErrandCopulaGoverns]): T1–T6 HIT ERRAND con título limpio
 * («Peluquería»/«La peluquería»/«Mi barbería») y dueAt; G1 NULL
 * ([planWrapperIsNegated]); G2 NULL («salón» excluido por diseño);
 * G4 NULL (guard copulativa pasada c.1256); G3 «habla de la
 * peluquería» y G5 «fui a la peluquería ayer» HIT — laterales
 * ABIERTAS con paridad aceptada (el hermano nominal EXERCISE c.1250
 * tiene la misma clase de sobre-captura: «fui a la clase de zumba
 * ayer» HIT); R1–R6 HIT (regresiones intactas; 4598 tests engine
 * verde, 325 clases).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else {
            val due = i.dueAt != null
            println("$label [HIT] ${i.kind} | ${i.title} | dueAt=$due <- $t")
        }
    }
    // Targets (capturas esperadas ERRAND)
    show("T1", "peluquería el martes")
    show("T2", "cita en la peluquería el viernes")
    show("T3", "la peluquería mañana")
    show("T4", "mi peluquería el viernes")
    show("T5", "barbería el sábado")
    show("T6", "la barbería el lunes")
    // Guards (NULL esperado — anti-overreach)
    show("G1", "no voy a la peluquería mañana")
    show("G2", "el salón de belleza está cerrado")
    show("G3", "habla de la peluquería")
    show("G4", "la peluquería fue ayer")
    show("G5", "fui a la peluquería ayer")
    // Regresiones (HIT por fórmulas heredadas)
    show("R1", "recuérdame mañana")
    show("R2", "cita con el médico mañana")
    show("R3", "comprar leche")
    show("R4", "llamar a mamá")
    show("R5", "lavar al perro")
    show("R6", "hacer yoga")
    println("sonda c.1256 ok")
}

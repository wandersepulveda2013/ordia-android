import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE/POST c.1257: «(la |mi )?(manicura|pedicura)» — nominal de
 * servicio (lateral (b) MEDIA de MI auditoría c.1252 — clase TRIGÉSIMA
 * SEXTA belleza/cuidado personal). Gate c.751: objeto
 * monosemántico-servicio (la manicura/pedicura es sesión inequívoca de
 * cuidado); CERO keyword nueva — floor-only (paridad «partido» c.1231 /
 * «peluquería» c.1256). Kind hermano de c.1256: ERRAND (desplazamiento).
 * «Uñas»/«cejas» FUERA (partes corporales polisémicas — sonda G2).
 * Lockstep: extensión del piso [ERRAND_BEAUTY_RUN_FLOOR] (renombrado de
 * c.1256) + plantilla matchBeautyRun + guard [pastErrandCopulaGoverns]
 * (cubre los nominales nuevos por la misma constante).
 *
 * Uso:
 *   bash tools/run_probe.sh tools/probe/ManicuraPedicuraProbe.kt
 *
 * MEDICIÓN PRE (HEAD bd1e850, sonda efímera /tmp/probe1257): T1–T6 NULL
 * (olvido silencioso — el nominal no era piso ni keyword); G1–G4 NULL
 * (sin cobertura); R1–R7 HIT (baseline intacta, incl. R7 «peluquería el
 * martes» HIT de la hermana c.1256).
 * MEDICIÓN POST (con la extensión): T1–T6 HIT ERRAND con título limpio
 * («Manicura»/«La manicura»/«Mi pedicura») y dueAt; G1 «no voy a la
 * manicura mañana» NULL ([planWrapperIsNegated]); G2 «las uñas
 * pintadas» NULL (corporal polisémico, excluido por diseño); G3 «la
 * manicura fue ayer» NULL (guard copulativa c.1256 extendida por la
 * misma constante); G4 «fui a la manicura ayer» HIT — lateral ABIERTA
 * con paridad aceptada (hermano nominal c.1250/c.1256: «fui a la
 * peluquería ayer» HIT); R1–R7 HIT (regresiones intactas; suite engine
 * verde tras el renombre).
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
    show("T1", "la manicura el viernes")
    show("T2", "manicura el sábado")
    show("T3", "mi manicura mañana")
    show("T4", "la pedicura el lunes")
    show("T5", "pedicura mañana")
    show("T6", "cita en la pedicura el jueves")
    show("G1", "no voy a la manicura mañana")
    show("G2", "las uñas pintadas")
    show("G3", "la manicura fue ayer")
    show("G4", "fui a la manicura ayer")
    show("R1", "recuérdame mañana")
    show("R2", "cita con el médico mañana")
    show("R3", "comprar leche")
    show("R4", "llamar a mamá")
    show("R5", "lavar al perro")
    show("R6", "hacer yoga")
    show("R7", "peluquería el martes")
    println("sonda c.1257 ok")
}

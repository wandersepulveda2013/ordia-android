import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda PRE/POST c.1258: «(la |mi )?depilaci[oó]n» — nominal de servicio
 * (lateral (c) MEDIA de MI auditoría c.1252 — clase TRIGÉSIMA SEXTA
 * belleza/cuidado personal). Gate c.751: objeto monosemántico-servicio
 * (la depilación es sesión inequívoca de cuidado); CERO keyword nueva —
 * floor-only (paridad «partido» c.1231 / «peluquería» c.1256 /
 * «manicura» c.1257). Kind hermano de c.1256/c.1257: ERRAND
 * (desplazamiento). «Cera» FUERA (polisémica: vela/coche/oído — sonda
 * G2; «depilación con cera» queda cubierta por el nominal, T6).
 * Lockstep: extensión del piso [ERRAND_BEAUTY_RUN_FLOOR] + plantilla
 * matchBeautyRun + guard [pastErrandCopulaGoverns] (cubre el nominal
 * nuevo por la misma constante).
 *
 * Uso:
 *   bash tools/run_probe.sh tools/probe/DepilacionProbe.kt
 *
 * MEDICIÓN PRE (HEAD b04c885, sonda efímera /tmp/probe1258): T1–T6 NULL
 * (olvido silencioso — el nominal no era piso ni keyword); G1–G5 NULL
 * (sin cobertura); R1–R6 HIT (baseline intacta, incl. R5 «manicura» y
 * R6 «peluquería» de las hermanas c.1256/c.1257).
 * MEDICIÓN POST (con la extensión): T1–T6 HIT ERRAND con título limpio
 * («La depilación»/«Depilación»/«Mi depilación»/«Depilación con cera»)
 * y dueAt; G1 «no voy a la depilación mañana» NULL
 * ([planWrapperIsNegated]); G2 «la cera de las velas» NULL (polisémico
 * excluido por diseño); G3 «la depilación fue ayer» NULL (guard
 * copulativa c.1256 extendida por la misma constante); G4 «fui a la
 * depilación ayer» HIT — lateral ABIERTA con paridad aceptada
 * (hermanos nominales c.1256/c.1257: «fui a (la) <nominal> ayer» HIT);
 * G5 «háblame de la depilación láser» HIT — FP aceptada de paridad
 * (paridad de guard exacta ya documentada en c.1256: «habla de la
 * peluquería» no es guardable por piso sólo); R1–R6 HIT (regresiones
 * intactas).
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
    show("T1", "la depilación el viernes")
    show("T2", "depilación el sábado")
    show("T3", "mi depilación mañana")
    show("T4", "cita para la depilación el jueves")
    show("T5", "la depilacion el lunes")
    show("T6", "depilación con cera mañana")
    show("G1", "no voy a la depilación mañana")
    show("G2", "la cera de las velas")
    show("G3", "la depilación fue ayer")
    show("G4", "fui a la depilación ayer")
    show("G5", "háblame de la depilación láser")
    show("R1", "recuérdame mañana")
    show("R2", "cita con el médico mañana")
    show("R3", "comprar leche")
    show("R4", "llamar a mamá")
    show("R5", "la manicura el viernes")
    show("R6", "peluquería el martes")
    println("sonda c.1258 ok")
}

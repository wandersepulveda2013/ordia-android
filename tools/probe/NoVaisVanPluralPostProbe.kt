import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda POST persistida c.1091 — lateral ABIERTA «no vais/van a…»
 * 2ª/3ª persona plural de mi candidata S c.1007/c.1009 [guard de
 * plan negado; resuelta por este lado, DISJUNTO de SU «no vas» c.1044].
 * PRE (efímera): 7/7 candidatas HIT (falso compromiso); POST: todas
 * NULL mediante protección por [planWrapperIsNegated].
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} ← $t"
        println("$label $s")
    }
    // Candidatas plurales (hoy falso compromiso)
    show("P1", "no vais a sacar al perro esta tarde")
    show("P2", "no van a llamar a mamá esta noche")
    show("P3", "no piensan ir al médico el lunes")
    show("P4", "no quieren pagar la luz esta mañana")
    show("P5", "no van a comprar leche esta tarde")
    // Guards anti-overreach — NULL esperado hoy también (inversión «sin»)
    // (la guard no dispara: «pagar la luz» SÍ es compromiso → HIT medido)
    show("G1", "no vais a ir sin pagar la luz")
    // Regresiones singular ya negadas hoy → NULL byte-idénticas
    show("R1", "no voy a llamar a mamá esta noche")
    show("R2", "no vas a pagar la luz esta mañana")
    show("R3", "no pienso ir al médico el lunes")
    // Afirmativas control → HIT
    show("C1", "vamos a sacar al perro esta tarde")
    show("C2", "van a llamar a mamá esta noche")
}

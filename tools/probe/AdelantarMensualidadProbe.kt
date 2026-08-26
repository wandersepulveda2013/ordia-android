import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1201 (cierre de la unidad (c) de la auditoría
 * VIGESIMOTERCERA finanzas, c.1197): el infinitivo «adelantar» entró en
 * [PAYMENT_VERBS] en lockstep con la plantilla de título (lección
 * c.616/c.652); «mensualidad» ya era keyword-OBJETO PAYMENT (gate c.751,
 * CERO keywords nuevas). PRE medido: formas directas NULL; envolvente
 * caía a TASK. POST: directas PAYMENT 0.45 con título "Adelantar…";
 * envolvente TASK byte-idéntica a sus vecinos (pagar/recargar).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} <- $t")
    }
    show("C1", "adelantar la mensualidad del coche")
    show("C2", "adelantar la mensualidad el lunes")
    show("C3", "recuérdame adelantar la mensualidad")
    show("V1", "pagar la mensualidad")
    show("V2", "recarjar la tarjeta")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.1238: evaluación de las laterales (b)-(e) DÉBILES de MI
 * auditoría c.1236 (los 4 verbos ancla carecen de keyword: ninguno es
 * keyword de ContextIntent). PRE de gate — medir NULL/HÍT de TODAS y
 * elegir UNA. DISJUNTO del marcador del hermano (c.1235 deporte).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        if (i == null) println("$label [NULL] $t")
        else println("$label [HIT] ${i.kind} ${i.confidence} | ${i.title} <- $t")
    }
    show("b1", "conectar el wifi en la oficina")
    show("b2", "conectar el wifi esta noche")
    show("c1", "apagar el ordenador hoy")
    show("c2", "apagar la tablet")
    show("d1", "encender la tablet")
    show("d2", "encender el televisor")
    show("e1", "sincronizar el drive con el móvil")
    show("e2", "sincronizar los informes mañana")
    show("e3", "sincronizar fotos al drive")
    // guards
    show("g1", "no apagar el ordenador")
    show("g2", "la sincronización terminó")
}

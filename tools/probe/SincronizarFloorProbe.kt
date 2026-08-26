import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1238 (lateral (e) de MI auditoría c.1236):
 * «sincronizar + objeto acotado» en floor-only (SIN keyword nueva;
 * verbo monosemántico, gate c.751, precedente c.752). PRE: NUL; POST
 * HIT TASK 0.45. DISJUNTO del marcador del hermano (c.1235 deporte).
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
    show("T1", "sincronizar los archivos mañana")
    show("T2", "sincronizar los informes mañana")
    show("T3", "sincronizar los documentos")
    show("T4", "sincronizar fotos con el móvil")
    show("T5", "sincronizar el drive hoy")
    show("T6", "recuérdame sincronizar fotos")
    show("G1", "no sincronizar los archivos")
    show("G2", "quizás sincronizar los documentos")
    show("G3", "sincronicé los informes ayer")
    show("G4", "la sincronización terminó")
    show("G5", "sincroniza con tu pareja")
    show("R1", "escanear el informe mañana")
}

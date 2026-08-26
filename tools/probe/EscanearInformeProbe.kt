import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1237 (fix laterales (a) FUERTE de MI auditoría
 * c.1236, clase XXXI tecnología): «escanear + objeto documental
 * profesional (informe/documentos)» era NULL medido (piso c.864 con
 * alternancia de objetos acotada a dni|contratos?|notas?|código qr).
 * Keyword «escanear» preexistente (c.864) → gate c.751 satisfecho SIN
 * keyword nueva; el fix amplía solo la alternancia del piso. PRE NULL
 * medido; POST HIT TASK 0.45 con título «Escanear el informe…» en el
 * mismo piso. TDD RED→GREEN; DISJUNTO del marcador del hermano.
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
    // T1-T6: candidatas del fix (informe/documento(s))
    show("T1", "escanear el informe mañana")
    show("T2", "escanear el documento hoy")
    show("T3", "escanear los documentos esta semana")
    show("T4", "escanear los informes mañana")
    show("T5", "recuérdame escanear el documento")
    show("T6", "mañana escanear el informe")
    // G1-G4: guards NULL correctos (negación, duda, pasado, sustantivo)
    show("G1", "no escanear el informe mañana")
    show("G2", "quizá escanear el documento mañana")
    show("G3", "escaneé el informe ayer")
    show("G4", "el informe está completo")
    // R1-R2: regresiones hermandad del piso c.864
    show("R1", "escanear el DNI mañana")
    show("R2", "escanear el contrato mañana")
}

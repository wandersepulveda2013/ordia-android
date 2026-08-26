import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Auditoría clase TRIGÉSIMA CUARTA (XXXIV): BRICOLAJE / HERRAMIENTAS dichas
// como se hablan — dominio fresco (XXXI–XXXIII cubiertas; DISJUNTO del
// hermano). CERO producto (descubrimiento, convención c.1127/c.1165/
// c.1194/c.1225).
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    tag("D1", "taladrar la pared del salón")
    tag("D2", "atornillar la estantería")
    tag("D3", "montar el mueble de IKEA")
    tag("D4", "colgar el cuadro del pasillo")
    tag("D5", "pintar la habitación")
    tag("D6", "cambiar el enchufe")
    tag("D7", "arreglar el grifo que gotea")
    tag("D8", "sellar la junta de la ducha")
    tag("D9", "sustituir el foco del garaje")
    tag("D10", "pegardar la baldosa rota")
    tag("D11", "lijar el marco de la puerta")
    tag("D12", "puliendo el suelo mañana")
    tag("D13", "limpiar las herramientas")
    tag("D14", "comprar una caja de tornillos")
    tag("G1", "no taladrar la pared")
    tag("G2", "no sé si colgar el cuadro")
    tag("G3", "montaste el mueble")
    tag("G4", "la estantería montada")
    tag("G5", "no pintar el pasillo")
    tag("G6", "el grifo")
    tag("G7", "pinté la habitación")
    tag("G8", "los tornillos")
    tag("R1", "apagar la calefacción")
    tag("R2", "pellidoar a la vecina")
    tag("R3", "imprimir el contrato")
    tag("R4", "arreglar la luz que parpadea")
    tag("R5", "recoger la compra")
    tag("R6", "firmar el parte")
    tag("R7", "cambiar la bombilla")
    tag("R8", "comprar bombillas led")
}

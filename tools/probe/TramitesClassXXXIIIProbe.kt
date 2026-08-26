import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Auditoría clase TRIGÉSIMA TERCERA (XXXIII): OFICINA / TRÁMITES dichos
// como se hablan — dominio fresco (XXXI tecnología, XXXII luz ya cubiertas;
// DISJUNTO de marcadores del hermano). CERO producto (descubrimiento,
// convención c.1127/c.1165/c.1194/c.1225).
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    tag("D1", "imprimir el contrato mañana")
    tag("D2", "escanear los documentos")
    tag("D3", "firmar los papales de la hipoteca")
    tag("D4", "solicitar la cita previa")
    tag("D5", "presentar la documentación en el registro")
    tag("D6", "recoger el certificado")
    tag("D7", "sellar la nómina")
    tag("D8", "renovar el carnet de identidad")
    tag("D9", "pedir el justificante de empadronamiento")
    tag("D10", "ella firma la renuncia")
    tag("D11", "llevar las fotocopias al notario")
    tag("D12", "enviar el fax de cancelación")
    tag("D13", "redactar la renuncia")
    tag("D14", "descargar el resumen de la nómina")
    tag("G1", "no imprimir el contrato")
    tag("G2", "no sé si escanear")
    tag("G3", "firmaste los papales")
    tag("G4", "la cita previa")
    tag("G5", "no presentar la documentación")
    tag("G6", "el certificado sellado")
    tag("G7", "ella imprimió el contrato")
    tag("G8", "la nómina")
    tag("R1", "apagar el ordenador")
    tag("R2", "conectar el wifi")
    tag("R3", "encender la calefacción")
    tag("R4", "sincronizar los archivos")
    tag("R5", "enviar el paquete")
    tag("R6", "recoger el pedido")
    tag("R7", "llamar a la aseguradora")
    tag("R8", "pedir la cita")
}

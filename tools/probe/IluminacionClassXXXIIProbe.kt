import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Auditoría clase TRIGÉSIMA SEGUNDA (XXXII): LUZ / ILUMINACIÓN del hogar
// dominio fresco (yo hice XXXI tecnología). CERO producto (descubrimiento,
// convención c.1127/c.1165/c.1194/c.1225). El objeto «luz» se excluyó a
// propósito del piso «apagar/encender dispositivo» c.1241 (evitar FALP).
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    tag("D1", "cambiar la bombilla del pasillo")
    tag("D2", "encender la luz del salón")
    tag("D3", "apagar la luz de la habitación")
    tag("D4", "poner una lámpara en el comedor")
    tag("D5", "revisar los enchufes de casa")
    tag("D6", "comprar bombillas led")
    tag("D7", "instalar el ventilador de techo")
    tag("D8", "encender la calefacción")
    tag("D9", "apagar la calefacción por la noche")
    tag("D10", "encender el aire acondicionado")
    tag("D11", "apagar la chimenea")
    tag("D12", "sustituir el fluorescente del garaje")
    tag("D13", "colocar el foco del escritorio")
    tag("D14", "arreglar la luz que parpadea")
    tag("G1", "no cambiar la bombilla")
    tag("G2", "no sé si encender la luz")
    tag("G3", "apagaste la luz")
    tag("G4", "la lámpara está rota")
    tag("G5", "no apagar la luz")
    tag("G6", "la bombilla fundida")
    tag("G7", "el fluorescente viejo")
    tag("G8", "anoche encendí la calefacción")
    tag("R1", "apagar el ordenador")
    tag("R2", "conectar el wifi")
    tag("R3", "sincronizar los archivos")
    tag("R4", "lavar los platos")
    tag("R5", "sacar la basura")
    tag("R6", "llamar a mamá")
    tag("R7", "regar las plantas")
    tag("R8", "tender la ropa")
}

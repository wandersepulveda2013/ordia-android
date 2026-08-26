import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Auditoría clase TRIGÉSIMA QUINTA (XXXV): MÚSICA / INSTRUMENTOS / ENSAYOS
// dichas como se hablan — dominio fresco (XXXIV bricolaje cubierta por el
// hermano; DISJUNTO de marcadores activos). CERO producto (descubrimiento,
// convención c.1127/c.1165/c.1194/c.1225).
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    tag("D1", "practicar el piano por la tarde")
    tag("D2", "afinar la guitarra")
    tag("D3", "llevar el violín a la clase de música")
    tag("D4", "el ensayo del coro mañana a las 7")
    tag("D5", "cambiar las cuerdas de la guitarra")
    tag("D6", "comprar las baquetas")
    tag("D7", "la audición de piano del sábado")
    tag("D8", "apuntar a la niña al conservatorio")
    tag("D9", "recoger el saxofón del luthier")
    tag("D10", "estudiar la partitura del solo")
    tag("D11", "reservar la sala de ensayo")
    tag("D12", "devolver el teclado al conservatorio")
    tag("D13", "preparar el concierto del sábado")
    tag("D14", "limpiar la trompeta")
    tag("G1", "no practicar el piano")
    tag("G2", "practiqué el piano ayer")
    tag("G3", "no sé si afinar la guitarra")
    tag("G4", "la guitarra")
    tag("G5", "no cambiar las cuerdas")
    tag("G6", "el ensayo")
    tag("G7", "ella compró las baquetas")
    tag("G8", "no comprar las baquetas")
    tag("R1", "apagar el ordenador")
    tag("R2", "conectar el wifi")
    tag("R3", "encender la calefacción")
    tag("R4", "instalar la app") // c.1240 piso: artículo definido; «una» NULL documentado hallazgo marginal
    tag("R5", "taladrar la pared")
    tag("R6", "montar el mueble")
    tag("R7", "colgar el cuadro")
    tag("R8", "sincronizar los archivos")
}

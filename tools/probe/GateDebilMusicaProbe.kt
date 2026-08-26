import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Gate-evaluación PRE de laterales DÉBILES de MI auditoría c.1248 (clase
// XXXV música) — c.1255, DISJUNTO del hermano (c.1253 límpida mediante
// objeto FORZADO en «llevar»). Sonda efímera (convención c.1194/c.1227):
// nominales-bare + envolventes + guards + regresiones. CERO producto.
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    tag("D1", "el ensayo del coro mañana a las 7")
    tag("D2", "la audición de piano del sábado")
    tag("D3", "el ensayo general antes de la función")
    tag("D4", "la audición del conservatorio")
    tag("D5", "preparar el concierto del sábado")
    tag("D6", "preparar la audición de piano")
    tag("D7", "el concierto del sábado")
    tag("D8", "recuérdame el ensayo del coro mañana")
    tag("D9", "anota la audición de piano del sábado")
    tag("D10", "recuérdame preparar el concierto")
    tag("D11", "preparar el ensayo de coro")
    tag("D12", "la audición")
    tag("D13", "el ensayo")
    tag("D14", "el concierto")
    tag("G1", "no tengo ensayo mañana")
    tag("G2", "preparé el concierto el sábado")
    tag("G3", "el ensayo fue bien")
    tag("G4", "ella prepara la audición")
    tag("G5", "no preparar el concierto")
    tag("G6", "es que la audición es importante")
    tag("G7", "tengo dudas sobre mi papeleta del concierto")
    tag("G8", "no sé si ir al ensayo")
    tag("R1", "practicar el piano por la tarde")
    tag("R2", "afinar la guitarra")
    tag("R3", "preparar la oposición")
    tag("R4", "preparar la mochila del colegio")
    tag("R5", "recuérdame comprar las baquetas")
    tag("R6", "estudiar la partitura del solo")
    tag("R7", "reservar la sala de ensayo")
    tag("R8", "preparar la documentación antes de la entrevista")
}

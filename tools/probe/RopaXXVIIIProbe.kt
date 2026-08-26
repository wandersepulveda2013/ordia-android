import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun nullOrNot(id: String, t: String) {
        val r = a(t)
        println(if (r == null) "["+id+"] NULL  <- "+t else "["+id+"] HIT "+r.kind+" "+r.confidence+" |"+r.title+"  <- "+t)
    }
    listOf(
        "D1" to "colgar la ropa",
        "D2" to "lavar la ropa",
        "D3" to "planchar la camisa",
        "D4" to "doblar la ropa",
        "D5" to "coser el botón",
        "D6" to "comprar ropa nueva",
        "D7" to "comprar zapatos",
        "D8" to "llevar a la tintorería",
        "D9" to "recoger de la tintorería",
        "D10" to "poner la lavadora",
        "D11" to "quitar mancha de la camisa",
        "D12" to "guardar la ropa de invierno",
        "D13" to "cambiar de ropa",
        "D14" to "organizar el armario",
        "G1" to "mi armario tiene mucha ropa",
        "G2" to "ropa sucia en el suelo",
        "G3" to "el plan de lavar la ropa sin imperativo directo"
    ).forEach { (id, t) -> nullOrNot(id, t) }
}

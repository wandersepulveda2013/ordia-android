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
        "D1" to "regar las plantas",
        "D2" to "regar el huerto",
        "D3" to "regar el césped",
        "D4" to "podar el rosal",
        "D5" to "podar los setos",
        "D6" to "cortar el césped",
        "D7" to "plantar los tomates",
        "D8" to "quitar la hierba",
        "D9" to "quitar las malas hierbas",
        "D10" to "comprar tierra para las plantas",
        "D11" to "trasplantar la orquídea",
        "D12" to "echar el fertilizante",
        "D13" to "limpiar la piscina",
        "D14" to "cubrir las plantas del frío",
        "D15" to "sacar los muebles a la terraza",
        "G1" to "las plantas crecen bien",
        "G2" to "el huerto está seco",
        "G3" to "quizás riegue las plantas",
        "G4" to "regué las plantas ayer",
        "G5" to "plantar",
        "G6" to "las plantas del huerto"
    ).forEach { (id, t) -> nullOrNot(id, t) }
}

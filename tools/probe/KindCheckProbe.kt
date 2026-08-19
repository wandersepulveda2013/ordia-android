import com.ordia.app.context.*

fun main() {
    val now = 1723939200000L
    val cases = listOf(
        "revisar el informe pasado mañana",
        "tengo que entregar el informe pasado manana",
        "comprar entradas para el cine del sábado"
    )
    for (text in cases) {
        val i = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, now))
        val k = i?.kind?.name ?: "NULL"
        println("  $k | title='${i?.title ?: "-"}' | $text")
    }
}

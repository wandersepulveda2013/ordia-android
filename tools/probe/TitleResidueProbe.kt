import com.ordia.app.context.*

fun main() {
    val now = 1723939200000L
    val cases = listOf(
        "tengo que entregar el informe pasado mañana a las 10",
        "comprar entradas para el concierto del viernes",
        "pagar la renta pasado mañana",
        "llevar el informe pasado mañana",
        "el informe pasado quedó bien",
        "comprar leche mañana",
        "tengo que enviar el reporte del proyecto de investigación anual del departamento de ventas internacional mañana",
        "hacer ejercicio por la mañana",
        "comprar entradas del concierto"
    )
    for (text in cases) {
        val i = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, now))
        val k = i?.kind?.name ?: "NULL"
        val t = i?.title ?: "-"
        val d = i?.dueAt != null
        println("  $k dueAt=$d | title='$t' | $text")
    }
}

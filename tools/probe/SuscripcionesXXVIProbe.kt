import com.ordia.app.context.*

fun main() {
    val cases = listOf(
        "cancelar Netflix el viernes",
        "dar de baja el gimnasio mañana",
        "devolver el pedido de Amazon",
        "reclamar la factura",
        "renovar el seguro del coche",
        "pagar la suscripción",
        "descargar la factura",
        "pedir la factura",
        "cancelar la suscripción",
        "renovar la suscripción",
        "la suscripción vence esta semana",
        "renovar el pasaporte",
        "reclamar el retraso del vuelo",
        "devolver yenes"
    )
    for (c in cases) {
        val r: ContextIntent? = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, c, System.currentTimeMillis()))
        println("%-52s -> %-11s | conf=%s | %s".format(c, r?.kind, r?.confidence, r?.title))
    }
}

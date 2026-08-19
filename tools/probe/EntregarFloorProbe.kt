import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.693: piso TASK "entregar <objeto>" (clase de verbos cotidianos sin
 * piso, descubierta c.692 con `tools/probe/CommonVerbDiscoveryProbe.kt`; una
 * forma por ciclo, doctrina anti-overreach). Persistente para evidencia
 * PRE/POST del ciclo.
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Capturas esperadas: TASK con título "Entregar X"
        "entregar la tarea el lunes",
        "entregar el informe mañana",
        "entregar los documentos a las 5",
        "entregar la solicitud",
        "vale, entregar el informe mañana",
        // Regresión: la envolvente c.613 sigue gobernando
        "tengo que entregar el informe",
        // Controles anti-overreach (NULL)
        "no entregar el informe",
        "quizá entregar la tarea mañana",
        "la entrega del paquete es mañana",
        "entregar"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | conf=${intent?.confidence ?: "-"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda c.694 (PERSISTENTE): prefijo temporal duro ("hoy"/"mañana") + verbo de
 * piso TASK (revisar/enviar/entregar) se DESCARTABA — el ancla sólo admitía
 * inicio o prefijo de ACUSE (descubierto c.693, sonda ad-hoc 4/4 NULL).
 * PRE-fix: 5 capturas → NULL; POST-fix: TASK 0.45 con título limpio y dueAt.
 * Controles: negación/duda/sustantivo siguen NULL; regresiones sin prefijo y
 * de otros kinds intactas.
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Capturas esperadas: TASK con título "<Verbo> X" y dueAt
        "hoy entregar el informe",
        "mañana enviar el informe",
        "hoy revisar el informe",
        "mañana entregar la tarea",
        "pasado mañana enviar el informe",
        // Controles anti-overreach (NULL)
        "hoy no entregar el informe",
        "quizá hoy entregar el informe",
        "hoy la entrega del paquete",
        // Regresiones: sin prefijo y otros kinds intactos
        "entregar el informe hoy",
        "vale, entregar el informe mañana",
        "mañana regar las plantas",
        "hoy limpiar la cocina",
        // Control c.694: SHOPPING/PAYMENT con prefijo temporal capturan vía
        // score de keyword (sin piso) — no hay asimetría en esos kinds
        "hoy comprar pan",
        "mañana pagar la luz"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | conf=${intent?.confidence ?: "-"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

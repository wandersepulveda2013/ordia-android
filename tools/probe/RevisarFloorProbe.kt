// Sonda de descubrimiento/verificación (c.691): piso TASK para el verbo
// cotidiano "revisar" con objeto — "revisar el informe (pasado) mañana"
// se DESCARTABA (analyze → NULL): ningún piso cubre "revisar" y el bono
// temporal no alcanza el umbral (olvido silencioso P1 en captura pasiva).
// Uso: bash tools/run_probe.sh tools/probe/RevisarFloorProbe.kt
import com.ordia.app.context.*

fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Capturas esperadas (TASK, título limpio)
        "revisar el informe pasado mañana",
        "revisar el informe mañana",
        "revisar la presentación el viernes",
        "revisar el contrato a las 5",
        "revisar los apuntes",
        "vale, revisar el informe mañana",
        // Controles (NO deben capturar)
        "no revisar el informe",
        "quizá revisar el informe mañana",
        "la revisión del coche es mañana",
        "revisar",
        // Regresión (envolvente ya cubierta por c.613)
        "tengo que revisar el informe"
    )
    for (c in cases) {
        val i = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, c, now))
        val k = i?.kind?.name ?: "NULL"
        println("  $k | dueAt=${i?.dueAt != null} | title='${i?.title ?: "-"}' | $c")
    }
}

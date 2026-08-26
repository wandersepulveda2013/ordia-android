import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda POST persistida del fix c.1216 (lateral (c) ABIERTA de la
 * auditoría clase VIGESIMOCTAVA ROPA/VESTIMENTA c.1209): dirección
 * ENTREGA (drop-off) del dry-cleaner «llevar (…) a (la) tintorería».
 * NULL PRE medido sobre tip (sonda efímera 16 casos: 5/5 targets NULL,
 * 5/5 guards NULL; recogida «recoger la tintorería» HIT heredado;
 * «llevar el gato al veterinario» NULL base sin robar).
 * Fix: lockstep TRES puntos — piso `ERRAND_DRYCLEAN_FLOOR` +
 * keyword-OBJETO literal «tintorería» + plantilla `matchDryclean`.
 * Ejecuta con `bash tools/run_probe.sh tools/probe/LlevarTintoreriaProbe.kt`.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L))

    val hits = listOf(
        "llevar el traje a la tintorería",
        "llevar la camisa a la tintorería mañana",
        "llevar los vestidos a la tintorería",
        "llevar mi uniforme a la tintorería",
        "llevar a la tintorería",
        "llevar a la tintorería el viernes",
        "mañana llevar el traje a la tintorería",
        "recoger la tintorería",
        "recoger la camisa de la tintorería"
    )
    val guards = listOf(
        "no llevar a la tintorería",
        "ya lo llevé a la tintorería",
        "llevar",
        "la tintorería me llamó",
        "tener en tintorería",
        "llevar el gato al veterinario"
    )
    var fails = 0
    println("== HITS (esperado: capture) ==")
    hits.forEach { t ->
        val r = a(t)
        val ok = r != null
        if (!ok) fails++
        println(if (ok) "HIT  kind=${r!!.kind} title=${r.title} <- $t" else "MISS <- $t")
    }
    println("== GUARDS (esperado: NULL) ==")
    guards.forEach { t ->
        val r = a(t)
        val ok = r == null
        if (!ok) fails++
        println(if (ok) "NULL <- $t" else "FP   kind=${r!!.kind} title=${r.title} <- $t")
    }
    println(if (fails == 0) "PROBE OK" else "PROBE FAIL ($fails)")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
// Sonda POST persistida c.1217: lateral (b) «coser (el|los)? botón(es)»
// ABIERTA de la auditoría c.1209 clase VIGESIMOCTAVA ROPA/VESTIMENTA.
// PRE (sonda efímera): 5/5 targets NULL, 6/6 guards NULL. POST esperado:
// 5 HIT + 6 NULL + regresiones vecinas intactas (doblar/colgar c.1209).
fun main() {
    fun cap(text: String, expectHit: Boolean): Boolean {
        val r = ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))
        val hit = r != null
        val ok = hit == expectHit
        println(if (ok) "OK   " else "FAIL ") ; println((if (hit) "HIT " + r!!.kind + " «" + r.title + "»" else "NULL") + " <- " + text)
        return ok
    }
    var ok = true
    ok = cap("coser el botón", true) and ok
    ok = cap("coser los botones", true) and ok
    ok = cap("coser botones", true) and ok
    ok = cap("mañana coser el botón de la camisa", true) and ok
    ok = cap("vale, coser el botón", true) and ok
    ok = cap("no coser el botón", false) and ok
    ok = cap("coser", false) and ok
    ok = cap("ya lo cosí", false) and ok
    ok = cap("coser es un arte", false) and ok
    ok = cap("el botón de la chaqueta vino suelto", false) and ok
    ok = cap("cosió el botón ayer", false) and ok
    ok = cap("doblar la ropa", true) and ok // regresión vecina c.1209
    ok = cap("colgar la ropa", true) and ok // regresión vecina c.1209
    println(if (ok) "PROBE OK" else "PROBE FAIL")
    if (!ok) kotlin.system.exitProcess(1)
}

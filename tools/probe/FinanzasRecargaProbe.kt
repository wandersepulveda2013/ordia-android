import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda persistida c.1199 (DISJUNTA del marcador del hermano c.1196 —
 * parser «haré curso(s)»): laterales de la auditoría c.1197 (clase
 * VIGESIMOTERCERA finanzas domésticas). PRE medido en sonda efímera
 * (/tmp/FinanzasProbePRE.kt, HEAD d358e3c): 5/5 candidatas recarga NULL,
 * guards 6/6 NULL, (a) transferencia NULL por privacidad deliberada
 * (paso 1, precedente c.1029), (c) adelantar mensualidad NULL (ABIERTA).
 * POST: 4/4 capturas recarga→PAYMENT≥0.45 (piso acotado RECARGA_TARJETA_FLOOR
 * + WRAPPABLE_PATTERNS + plantilla extractTitle; lockstep TRES puntos);
 * guards intactos; regresiones intactas.
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }

    // CANDIDATAS (b) «recargar la tarjeta» — POST esperado PAYMENT
    show("C1", "recargar la tarjeta el lunes")
    show("C2", "recargar la tarjeta mañana")
    show("C3", "recargar la tarjeta de crédito esta semana")
    show("C4", "ok, recargar la tarjeta")

    // GUARDS (deben quedar NULL)
    show("G1", "no voy a recargar la tarjeta")
    show("G2", "recargué la tarjeta ayer")
    show("G3", "no sé si recargar la tarjeta mañana")
    show("G4", "la recarga de la tarjeta tardó dos días")
    show("G5", "recargar la página web")
    show("G6", "recargar el arma")

    // ENVOLVENTES (no roban a PAYMENT — registro WRAPPABLE_PATTERNS)
    show("W1", "avísame mañana recargar la tarjeta")
    show("W2", "recuérdame recargar la tarjeta mañana")

    // (a) PIN privacidad deliberada (cierre POR DISEÑO c.1198)
    show("P1", "hacer la transferencia al casero el lunes")
    show("P2", "pagar la transferencia al casero el lunes")

    // (c) ABIERTA (NULL por umbral, sin privacidad)
    show("A1", "adelantar la mensualidad del coche")
    show("A2", "adelantar la mensualidad el mes que viene")

    // REGRESIONES heredadas (deben seguir HIT)
    show("R1", "pagar el alquiler el lunes")
    show("R2", "pagar la luz mañana")
    show("R3", "cobrar la nómina el viernes")
    show("R4", "revisar el extracto esta noche")
    show("R5", "ingresar dinero en el cajero")
}

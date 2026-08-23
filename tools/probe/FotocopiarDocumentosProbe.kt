import com.ordia.app.context.ContextIntentEngine
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextCaptureSource

/**
 * Sonda PERSISTIDA c.891 — lateral «fotocopiar <documento no-DNI>»:
 * extensión de la clase de objetos del piso c.887 «fotocopiar el DNI»
 * (hermana de la extensión de objetos c.884 sobre el piso c.864
 * «escanear»). Candidatas: contrato / notas / código QR fotocopiados —
 * la gestión documental que el trámite duplica con la fotocopia.
 * Tras el fix se re-ejecuta y se anota REGRESIÓN (convención
 * c.749…c.888).
 *
 * Sonda PRE c.891 (HEAD 151d8f7) medida end-to-end con run_probe.sh:
 *   C.. — 6/6 candidatas NULL (contrato/notas/código QR, acuse, prefijo
 *      temporal, compuesta conservada «y guardarlo en la carpeta»).
 *   G.. — 5/5 guards NULL correctos.
 *   R.. — regresiones HIT (fotocopiar DNI c.887, escanear contrato
 *      c.884, reescanear DNI c.888, envolvente c.613).
 * POST c.891 (mismo runner, piso activo): medido — C.. 6/6 NULL→HIT
 * TASK 0.45, títulos limpios («Fotocopiar el contrato»/«…las notas»/
 * «…el código QR», compuesta conserva «y guardarlo en la carpeta»),
 * G.. 5/5 NULL intactas, R.. 4/4 HIT (envolvente 0.54 inalterada).
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)

    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t" else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }
    // Candidatas (capture-me)
    show("C1", "fotocopiar el contrato mañana")
    show("C2", "fotocopiar las notas esta tarde")
    show("C3", "fotocopiar el código QR mañana")
    show("C4", "vale, fotocopiar el contrato mañana")
    show("C5", "mañana fotocopiar las notas")
    show("C6", "fotocopiar el contrato y guardarlo en la carpeta mañana")
    // Guards (deben NULL)
    show("G1", "no fotocopiar el contrato mañana")
    show("G2", "quizá fotocopiar las notas mañana")
    show("G3", "fotocopié el contrato ayer")
    show("G4", "fotocopiar")
    show("G5", "la fotocopia del contrato salió borrosa")
    // Regresiones (deben seguir HIT)
    show("R1", "fotocopiar el DNI mañana")
    show("R2", "escanear el contrato mañana")
    show("R3", "reescanear el DNI mañana")
    show("R4", "recuérdame fotocopiar el contrato mañana")
}

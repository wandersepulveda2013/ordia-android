import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * c.1148 — sonda de la candidata (a) FUERTE de la clase DECIMOSÉPTIMA
 * vida laboral (medida por el hermano en la auditoría c.1147 con la
 * sonda persistida `tools/probe/SeventeenthClassWorkProbe.kt` C5):
 * «echar el currículum en la oferta de infojobs». Decisión de dominio
 * TASK (gestión laboral SIN desplazamiento explícito; hermana EXACTA de
 * «sellar el paro» TASK c.1143 — la doctrina ERRAND c.842/c.862 gobierna
 * solo el desplazamiento).
 *
 * NO es un test: su salida PRE (base `5a39f45`, medida con sonda
 * efímera idéntica `/tmp/EcharCurriculumPreProbe.kt`) documenta el NULL
 * de las 4 candidatas desnudas (C1/C2/C3/C5), el HIT heredado de la
 * envolvente (C4 TASK 0.45 por camino genérico), 6/6 guards NULL y
 * 4/4 regresiones HIT. Su salida POST (con el piso c.1148 aplicado)
 * verifica: 5/5 candidatas HIT TASK 0.45, envolvente re-pin 0.45→0.54
 * (precedente c.1035), guards NULL byte-idénticos, regresiones
 * byte-idénticas.
 *
 * Ejecutar: bash tools/run_probe.sh tools/probe/EcharCurriculumProbe.kt
 */
fun main() {
    val now = 1723939200000L
    fun probe(c: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
    )
    fun show(tag: String, t: String) {
        val i = probe(t)
        println("$tag " + (if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"))
    }
    // Candidatas (objetivo POST: captura TASK 0.45; C3 con lateral de
    // título «esta semana» residual heredada del motor — misma familia
    // que la cola «el día N» documentada en c.1143)
    show("C1", "echar el currículum en la oferta de infojobs")
    show("C2", "echar el curriculum mañana")
    show("C3", "echar currículums en varias webs esta semana")
    show("C4", "recuérdame echar el currículum el lunes")
    show("C5", "echar el currículum hoy")
    // Guards (NULL deliberado PRE y POST — pin del test c.1148)
    show("G1", "no eches el currículum todavía")
    show("G2", "no sé si echar el currículum en esa oferta")
    show("G3", "eché el currículum ayer")
    show("G4", "echar de menos a la familia")
    show("G5", "echar la carta al buzón")
    show("G6", "el currículum ya está enviado")
    // Regresiones (byte-idénticas PRE/POST)
    show("R1", "enviar el informe antes del viernes")
    show("R2", "quedar con el jefe mañana")
    show("R3", "imprimir el informe esta tarde")
    show("R4", "llevarle el informe al jefe")
}

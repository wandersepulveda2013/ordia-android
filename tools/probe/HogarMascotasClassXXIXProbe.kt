import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/** Sonda auditoría c.1225: clase VIGESIMONOVENA hogar+mascotas —
 * candidatas imperativas para descubrir gaps reales y descartar
 * FALSOS GAPS (lección lavar-coche: el piso genérico HOUSEHOLD_FLOOR
 * ya captura verbos alineados; sonda PRE siempre antes de backlog).
 */
fun main() {
    fun event(t: String) = ContextEvent(
        source = ContextCaptureSource.SHARED_TEXT,
        rawText = t,
        timestampMs = 0L
    )
    fun show(label: String, t: String) {
        val intent = ContextIntentEngine.analyze(event(t))
        println("$label | ${intent?.kind} | ${intent?.confidence} | \"${intent?.title}\" <- $t")
    }
    // regresiones conocidas: pisos vecinos deben seguir HIT byte-idénticos
    show("R1", "sacar la basura")
    show("R2", "poner la lavadora")
    show("R3", "vaciar el lavavajillas")
    show("R4", "quitar el polvo")
    show("R5", "limpiar la mesa")
    // candidatas mascotas
    show("M1", "limpiar la jaula")
    show("M2", "cambiar el agua al gato")
    show("M3", "llenar el comedero del perro")
    show("M4", "cepillar al gato")
    show("M5", "sacar al conejo de la jaula")
    // candidatas hogar no alineadas
    show("H1", "regar el césped")
    show("H2", "limpiar los cristales")
    show("H3", "barrer la terraza")
    show("H4", "ordenar el garaje")
    show("H5", "regar las plantas del balcón")
}

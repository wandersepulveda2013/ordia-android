import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantEngine
import java.time.ZoneId
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (c.1087): lateral ABIERTA (2) de la
// auditoría de routing c.1085 — familia recordatorio «no (se) (te)
// olvide(s) <x>». PRE con sonda efímera /tmp/probe1085b/Probe.kt: las
// variantes caían al MENÚ genérico (mentira por omisión) mientras
// «recuérdame …» ya capturaba desde c.987. Guards («no me olvides»
// despedida, pretérito «no olvidaste», contenido negativo «no …») medidos
// — cero capturas indebidas. Cierre: noOlvidesCapture en AssistantEngine
// (clítico opcional SOLO «se te»/«te») → CREATE_TASK; pelada → guía
// honesta SIN acción. Guards deben seguir SIN CREATE_TASK; si alguna frase
// cerrada vuelve a GAP o un guard empieza a capturar, la sonda falla
// (exit 1).
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    val captures = listOf(
        "no se te olvide llamar a mamá",
        "no olvides las pastillas",
        "no te olvides de comprar leche",
        "no olvides que la cita es a las 3",
        "No olvides las llaves",
    )
    val guards = listOf(
        "no olvides", // pelada → guía honesta, NUNCA tarea basura
        "no me olvides", // despedida → menú
        "no olvidaste llamar a mamá", // pretérito → menú
        "no olvides no ir al banco", // contenido negativo → menú
        "recuérdame llamar a mamá", // regresión hermana (c.987) — sigue CREATE_TASK
    )
    var unexpected = 0
    for (p in captures) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != AssistantAction.CREATE_TASK) {
            unexpected++
            println("[GAP inesperado] $p -> ${ans.action}")
        } else println("[ok] $p -> CREATE_TASK")
    }
    for ((i, p) in guards.withIndex()) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val expected = when (i) {
            4 -> AssistantAction.CREATE_TASK
            else -> AssistantAction.NONE
        }
        if (ans.action != expected) {
            unexpected++
            println("[guard VIOLADO] $p -> ${ans.action} (esperado $expected)")
        } else println("[guard ok] $p -> ${ans.action}")
    }
    println("=== RESUMEN: ${captures.size} capturas + ${guards.size} guards; inesperados: $unexpected ===")
    if (unexpected > 0) exitProcess(1)
}

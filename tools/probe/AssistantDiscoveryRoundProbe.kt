import com.ordia.app.assistant.AssistantEngine
import java.time.ZoneId
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (c.803-b): ronda sugerida por c.803 —
// recordatorios / rutinas / relaciones + dos panoramas semanales. Fixture
// vacío: el routing correcto no depende del dato (GAP = cae al menú
// genérico, la mentira por omisión). c.803-b cerró los 2 GAPs de panorama
// semanal («cómo va mi semana» / «resumen de la semana» → agenda de la
// semana, hermana de «qué viene esta semana» c.802). c.807 cerró los 2 de
// contenido cualificado interrogativo y c.808 los 3 de recordatorios. Queda
// 1 GAP ABIERTO documentado y se tolera: si se cierra, la sonda lo reporta;
// si aparece un GAP NUEVO fuera de esa lista, falla.
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    // Frases cerradas (regresión: deben seguir sin caer al menú).
    val phrases = listOf(
        // Listados interrogativos básicos (ya ruteaban)
        "que notas tengo",
        "que tareas tengo",
        // Pendiente de mañana (paridad ayer/mañana solo en mañana por ahora)
        "tengo algo pendiente de manana",
        // "acordarás" (ya rutea a búsqueda)
        "cuando me acordaras",
        // Familia S (c.803-b): panorama semanal — cerrada
        "como va mi semana",
        "resumen de la semana",
        // Familia de contenido cualificado interrogativo (c.807): la
        // afirmativa «notas de casa» ya buscaba (c.794); la interrogativa
        // caía al menú. Ahora rutea a OPEN_SEARCH con payload afirmativo
        // equivalente; el alcance temporal («de hoy») queda excluido.
        "que notas tengo de trabajo",
        "que tareas tengo del proyecto casa",
        // Familia de recordatorios (c.808): NO hizo falta wiring estructural —
        // TaskEntity.reminderAt ya llega al asistente; solo faltaba routing.
        // Lista avisos próximos ordenados por disparo; vacío honesto sin menú.
        "que recordatorios tengo",
        "mis recordatorios",
        "que me vas a recordar"
    )
    // ABIERTOS (documentados c.803-b, tolerados mientras no se implementen):
    // · «qué tengo pendiente de ayer»: solapa parcialmente con vencidas
    //   (respuesta honesta existente); decidir si rutea a vencidas es
    //   decisión de producto abierta.
    val openGaps = listOf(
        "que tengo pendiente de ayer"
    )
    var gaps = 0
    var unexpected = 0
    for (p in phrases) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val fallback = ans.text.startsWith("Puedo organizar tu día") || ans.text.startsWith("Puedo organizar tu dia")
        if (fallback) { gaps++; unexpected++; println("[GAP inesperado] $p") }
        else println("[ok] $p -> ${ans.action}")
    }
    for (p in openGaps) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        val fallback = ans.text.startsWith("Puedo organizar tu día") || ans.text.startsWith("Puedo organizar tu dia")
        gaps++
        println(if (fallback) "[abierto tolerado] $p" else "[abierto CERRADO — retirar de openGaps] $p -> ${ans.action}")
    }
    println("=== RESUMEN: $gaps GAPs de ${phrases.size + openGaps.size} (abiertos tolerados: ${openGaps.size}) ===")
    if (unexpected > 0) exitProcess(1)
}

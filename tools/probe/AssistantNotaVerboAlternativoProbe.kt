import com.ordia.app.assistant.AssistantEngine
import java.time.ZoneId
import kotlin.system.exitProcess

// Sonda de descubrimiento PERSISTENTE (c.1085): auditoría de routing del
// asistente. Candidatas = captura de notas «una nota» con verbos
// alternativos («guarda[r]/toma[r]/haceme (voseo)/d[eé]jame»), hermanas de
// «escribe/crea/haz/hazme una nota» (c.974/c.988). PRE con sonda efímera
// /tmp/probe1085/Probe.kt: las variantes caían al MENÚ genérico (mentira
// por omisión); guards («guarda el recuerdo», «toma la pastilla», «déjame
// el paquete», «haceme favor») también medidos — cero capturas indebidas.
// Cierre: conjunto de verbos extendido en TAKE_NOTE_PREFIX +
// WRITE_NOTE_WITH_CONTENT (2 puntos, SAME regexes); la palabra «nota»
// sigue obligatoria. Guards deben seguir SIN CREATE_NOTE; si alguna
// frase cerrada vuelve a GAP o un guard empieza a capturar, la sonda
// falla (exit 1).
fun main() {
    val now = 1753495200000L
    val zone = ZoneId.of("America/Bogota")
    val captures = listOf(
        "guarda una nota: la wifi es 1234",
        "guardar una nota: el codigo es 4321",
        "toma una nota: el cumple es el lunes",
        "tomar una nota: llamar al banco",
        "haceme una nota: la cita es el martes",
        "déjame una nota: la llave está en la maceta",
        "dejame una nota: la llave esta en la maceta",
        "Guarda una nota: el codigo es 4321",
        "toma una nota de averiguar precios",
        // Regresiones de la familia preexistente
        "escribe una nota: el codigo es 1234",
        "toma nota: llamar al banco",
        "hazme una nota: llamar al banco"
    )
    val guards = listOf(
        "guarda el recuerdo de la infancia",
        "toma la pastilla",
        "déjame el paquete en la puerta",
        "haceme favor",
        "guarda una nota" // pelada → guía honesta, NUNCA nota basura
    )
    var unexpected = 0
    for (p in captures) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action != com.ordia.app.assistant.AssistantAction.CREATE_NOTE) {
            unexpected++
            println("[GAP inesperado] $p -> ${ans.action}")
        } else println("[ok] $p -> CREATE_NOTE")
    }
    for (p in guards) {
        val ans = AssistantEngine.answer(p, emptyList(), emptyList(), emptyList(), now, zone)
        if (ans.action == com.ordia.app.assistant.AssistantAction.CREATE_NOTE) {
            unexpected++
            println("[guard VIOLADO — captura indebida] $p -> CREATE_NOTE")
        } else println("[guard ok] $p -> ${ans.action}")
    }
    println("=== RESUMEN: ${captures.size} capturas + ${guards.size} guards; inesperados: $unexpected ===")
    if (unexpected > 0) exitProcess(1)
}

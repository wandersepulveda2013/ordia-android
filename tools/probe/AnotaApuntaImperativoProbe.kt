import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda PRE/POST c.1260 (descubrimiento documentado c.1255, marcador EN CURSO
// propio): imperativos conjugados de captura «anota|apunta <objeto>».
// El piso [NOTE_FLOOR] (c.714/c.856) solo cubre el infinitivo «apuntar|anotar»
// y el reflexivo «apuntarse a»; la forma conjugada «anota» recibe score por la
// keyword «nota» (subcadena) pero sin piso queda < MINIMUM_CONFIDENCE → NULL,
// y «apunta» ni siquiera recibe keyword → olvido silencioso P1 de la orden de
// captura más natural en dictado («apunta este número»).
// Gate c.751: «anota»/«apunta» + objeto en posición ^/ACUSE es la orden de
// anotación monosemántica; CERO keywords nuevas (floor-only, paridad
// c.1231/c.1256). Ancla SOLO ^|ACUSE: el prefijo temporal queda FUERA porque
// «el lunes anota todo» es 3ª-persona habitual ambigua (no imperativo).
// D = directas (NULL en PRE, HIT NOTE en POST); G = guards (NULL siempre);
// F = FP-paridad documentada (HIT aceptado, paridad infinitivo c.714);
// R = regresiones (HIT siempre, fórmulas heredadas).
fun main() {
    fun tag(label: String, text: String) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )
        if (r == null) println("$label [NULL] $text")
        else println("$label [HIT] ${r.kind} ${String.format("%.2f", r.confidence)} | ${r.title!!}${if (r.dueAt != null) " | dueAt=true" else " | dueAt=false"} <- $text")
    }
    // Directas bare (imperativo conjugado al inicio)
    tag("D1", "anota la dirección del médico")
    tag("D2", "anota el número del banco")
    tag("D3", "apunta la matrícula del coche")
    tag("D4", "apunta el código de la puerta")
    tag("D5", "anota el horario del vuelo")
    tag("D6", "apunta este teléfono")
    // Directas con acuse
    tag("D7", "vale, anota la dirección")
    tag("D8", "ok apunta el horario")
    // Directa con temporal sufijo (dueAt bonus, ancla sigue siendo ^)
    tag("D9", "anota la cita del dentista mañana")
    // Guards (NULL esperado PRE y POST)
    tag("G1", "no anotes la dirección")
    tag("G2", "anotó la dirección ayer")
    tag("G3", "ya anoté todo en el cuaderno")
    tag("G4", "no apunta el número")
    tag("G5", "ella apunta todo en su cuaderno")
    tag("G6", "el lunes anota todo lo del trabajo")
    tag("G7", "apunta")
    tag("G8", "anota")
    // FP-paridad documentada (el infinitivo hermano c.714 también casa
    // «apuntar al blanco»; el sentido «apuntar = dirigir» es heredado).
    tag("F1", "apunta al blanco")
    // Regresiones (HIT esperado por fórmulas heredadas)
    tag("R1", "apuntar la dirección del médico")
    tag("R2", "anotar el número del banco mañana")
    tag("R3", "apuntarse a la lista")
    tag("R4", "recuérdame apuntar la dirección mañana")
    tag("R5", "apuntar a los niños al fútbol")
    tag("R6", "comprar leche")
    tag("R7", "pagar la luz el día 4")
    tag("R8", "cita con el médico mañana")
    println("sonda c.1260 ok")
}

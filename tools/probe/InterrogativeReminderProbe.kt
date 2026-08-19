import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

// Sonda de descubrimiento (c.687): forma interrogativa de recordatorio
// "¿te acuerdas de <infinitivo>?" y forma EXERCISE con ancla de franja
// horaria ("hacer ejercicio por la mañana"). PRE-fix espera NULL (olvido
// silencioso P1); POST-fix debe capturar la intención correcta.
fun main() {
    fun probe(text: String) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1723939200000L)
        )
        if (intent == null) {
            println("  NULL        | $text")
        } else {
            println("  ${intent.kind} dueAt=${intent.dueAt != null} | title='${intent.title}' | $text")
        }
    }
    println("=== FORMA A: interrogativo recordatorio 'te acuerdas de <infinitivo>?' ===")
    listOf(
        "te acuerdas de pagar la renta?",
        "te acuerdas de llamar al banco?",
        "¿te acuerdas de recoger el paquete?",
        "te acuerdas de renovar el seguro mañana?",
        "¿te acuerdas de comprar leche?"
    ).forEach(::probe)
    println("=== FORMA A: controles (NO deben capturar / deben seguir igual) ===")
    listOf(
        // afirmación, no pregunta: "te acuerdas de X" declarativo es evocación
        "te acuerdas de cuando íbamos al parque",
        // interrogativo sobre el PASADO (pregunta conversacional, no recordatorio)
        "te acuerdas de la película que vimos?",
        "¿te acuerdas de mi cumpleaños?",
        // negación: "¿no te acuerdas de...?" es conversación
        "no te acuerdas de nada",
        // control afirmativo existente (debe seguir TASK)
        "recuerda comprar leche",
        "acuérdate de sacar al perro"
    ).forEach(::probe)
    println("=== FORMA B: EXERCISE con franja horaria ('hacer ejercicio por la mañana') ===")
    listOf(
        "hacer ejercicio por la mañana",
        "hacer ejercicio por las mañanas",
        "hacer ejercicio mañana por la mañana"
    ).forEach(::probe)
    println("=== FORMA B: controles ===")
    listOf(
        "no hacer ejercicio por la mañana",
        "ejercicio de matemáticas por la mañana"
    ).forEach(::probe)

    // c.689: forma imperativa reflexiva "acuérdate de <infinitivo>" →
    // REMINDER (antes NULL). Controles: evocación/sustantivo/negación NULL.
    println("=== FORMA C: imperativo recordatorio 'acuérdate de <infinitivo>' ===")
    listOf(
        "acuérdate de sacar al perro",
        "acuerdate de comprar leche",
        "acuérdate de llamar al banco mañana"
    ).forEach(::probe)
    println("=== FORMA C: controles ===")
    listOf(
        "acuérdate de cuando íbamos al parque",
        "acuérdate de las llaves",
        "no acuérdate de pagar"
    ).forEach(::probe)
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.833 (persistida): SEXTA clase de formas
 * cotidianas — VERBOS ENCLÍTICOS (pronombre pegado al infinitivo:
 * «echarle», «medirme», «hacerme», «cortarme»). La taquigrafía hablada
 * española pega el pronombre al infinitivo con una frecuencia enorme
 * («echarle gasolina al coche», «tomarme la pastilla»); un piso escrito
 * sólo con el infinitivo desnudo pierde esa forma entera.
 * Misma metodología que [FifthClassLifeProbe] (c.765)/[CaptureCoverageProbe]
 * (c.822): frases declarativas cotidianas (compromiso plausible del
 * usuario) + regresiones (formas que YA deben capturar) + controles
 * (negación, duda, narrativa pasado, verbo aislado, objeto bivalente).
 * NO es un test; su salida alimenta el BACKLOG (un ítem/forma por ciclo,
 * doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es un
 * GAP de captura (olvido silencioso P1) si el enunciado es un compromiso
 * plausible del usuario. NULL sobre controles/regresiones es un FALLO.
 *
 * Candidata documentada desde c.829: «echarle gasolina» (enclítico del
 * piso [ERRAND_FUEL_FLOOR] c.829/c.832) — RESUELTA en c.833.
 * «hacerme la maleta» (enclítico del piso de equipaje c.827) — RESUELTA
 * en c.836 por el hermano remoto (`(?:me|te|se|nos)?`), movida a REGRESIONES.
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // --- Combustible enclítico (candidata documentada c.829) ---
        "echarle gasolina al coche mañana",
        "echarle gasoil esta tarde",
        "echarle diésel antes del viaje",
        // --- Salud/autocuidado enclítico (hermanas de c.765…c.775) ---
        "medirme la tensión mañana",
        // --- Cuidado personal (peluquería) — nunca sondeado ---
        "cortarme el pelo el sábado",
        "cortar el pelo el sábado",
        // --- REGRESIONES: deben reportar HIT ---
        "tomarme la pastilla esta noche", // c.770 (tomar|tomarme) + objeto pastillas
        "hacerme la maleta esta noche", // c.836 equipaje enclítico (hermano de c.827)
        "echarles gasolina a las dos motos mañana", // c.833 plural «echarles» (hermano remoto)
        "recoger la ropa de la tintorería mañana", // ERRAND recoger (posición libre)
        "reservar el hotel para el sábado", // TASK reservar
        // --- CONTROLES: deben permanecer NULL ---
        "no echarle gasolina al coche", // negación
        "quizá echarle gasolina mañana", // duda (hedge c.649)
        "le eché gasolina ayer", // narrativa pasado
        "el coche gasta mucha gasolina", // declarativo
        "echarle agua al radiador mañana", // verbo bivalente, objeto NO combustible
        "echarle", // verbo aislado
        "me corté el pelo ayer" // narrativa pasado
    )
    var nulls = 0
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        if (intent == null) {
            nulls++
            println("[NULL] $c")
        } else {
            println(
                "[HIT] ${intent.kind} ${"%.2f".format(intent.confidence)}" +
                    " | ${intent.title} | dueAt=${intent.dueAt != null} ← $c"
            )
        }
    }
    println("=== RESUMEN: $nulls NULLs de ${cases.size} ===")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.826: cobertura de las formas de necesidad
 * CONDICIONAL («habría que …», «debería …», «tendría que …», «debía/debí …»)
 * sobre el enrutado de captura ([ContextIntentEngine.analyze]). Misma
 * metodología que [CaptureCoverageProbe]/[ManagementVerbDiscoveryProbe]:
 * frases cotidianas reales + controles (pasado simple, imperativo positivo,
 * envoltura explícita). NO es un test; su salida alimenta el BACKLOG.
 *
 * Criterio de lectura:
 * - NULL sobre «habría que <gestión>» es un GAP de captura (olvido
 *   silencioso) si la familia hermana «debería/tendría que» SÍ enruta.
 * - Un piso de baja confianza (acknowledged-need, doctrina c.649) es
 *   ACEPTABLE; captura de alta confianza sobre condicional sería
 *   OVERREACH (futuro firme inventado).
 * - «debía/debí …» (pasado) debe ser NULL (anti-overreach c.824).
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Familia «habría que» (condicional de necesidad, nunca sondeada)
        "habría que llamar al fontanero",
        "habría que ir al médico",
        "habría que comprar leche",
        "habría que hacer copias de seguridad",
        "habría que terminar el informe",
        "habría que recoger el paquete",
        "habría que pagar la factura de la luz",
        "habría que pedirle cita al dentista",
        "habría que estudiar para el examen",
        // Familias hermanas ya cubiertas (controles positivos de referencia)
        "debería llamar al banco",
        "tendría que estudiar para el examen",
        "tendría que comprar leche",
        // Envoltura explícita (debe enrutar fuerte: intención de captura real)
        "recuérdame que debería llamar al banco",
        // Pasado de deber (anti-overreach: NO debe capturarse)
        "debía llamar al banco",
        "debí llamar al banco",
        // Control positivo puro
        "tengo que llamar al fontanero"
    )
    var captured = 0
    for (src in cases) {
        val r = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, src, now)
        )
        if (r == null) println("NULL        <- $src")
        else {
            captured++
            println(String.format("%-9s %.2f  [%s] <- %s", r.kind, r.confidence, r.title, src))
        }
    }
    println("\nCapturadas: $captured/${cases.size}")
}

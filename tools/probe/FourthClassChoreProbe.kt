import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.733: CUARTA clase de formas cotidianas —
 * quehaceres domésticos de OBJETO ACOTADO sobre verbos bivalentes genéricos
 * (cuya acepción doméstica solo existe ligada al objeto).
 *
 * Misma metodología que CommonVerbDiscoveryProbe (c.692, clase 1, CERRADA 8/8),
 * ManagementVerbDiscoveryProbe (c.711, clase 2, CERRADA 10/14) y
 * ThirdClassVerbDiscoveryProbe (c.721, clase 3, CERRADA 19/19 en c.732):
 * frases de captura real (objeto + fecha) y controles. NO es un test; su
 * salida alimenta el BACKLOG (un ítem/forma por ciclo, anti-overreach).
 *
 * PRECEDENTES de los pisos de objeto acotado: `HOUSEHOLD_TRASH_FLOOR` (c.717,
 * "sacar la basura"), `HOUSEHOLD_BED_FLOOR` (c.728, "hacer la cama"),
 * lavadora (c.729), césped (c.731), polvo (c.732). Aquí se mide el vacío
 * que QUEDA: verbos genéricos ("poner", "sacar", "hacer", "echar", "colgar",
 * "dar") cuya forma doméstica canónica sigue sin piso.
 */
@Suppress("DEPRECATION")
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // Candidatos: objeto acotado sobre verbos genéricos bivalentes
        "poner la mesa hoy",                       // poner + mesa (≠ lavadora c.729)
        "poner el lavavajillas esta noche",        // poner + lavavajillas (≠ lavadora c.729)
        "sacar al perro mañana",                   // sacar + perro (≠ basura c.717)
        "hacer la compra mañana",                  // hacer + compra (≠ cama c.728)
        "pasar la aspiradora mañana",              // pasar + aspiradora (≠ aspirar c.730)
        "colgar la ropa hoy",                      // colgar + ropa
        "hacer la colada mañana",                  // hacer + colada (~ lavadora)

        // Cobertura existente (guards — deben SEGUIR capturando vía keyword
        // c.639 de HOUSEHOLD)
        "fregar los platos esta noche",
        "regar las plantas mañana",

        // Regresiones de las clases cerradas (deben SEGUIR capturando)
        "poner la lavadora esta tarde",            // c.729
        "quitar el polvo hoy",                     // c.732

        // Controles anti-overreach (deben permanecer NULL)
        "no poner la mesa hoy",
        "quizá poner la mesa hoy",
        "puse la mesa ayer",                       // pasado
        "poner",                                   // suelto
        "echar de menos a mi hermana",             // sentimento, sin fecha → NULL
        "tirar la toalla",                         // rendirse (idiomático)
    )

    for (raw in cases) {
        val result = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, now))
        if (result == null) {
            println("  NULL | dueAt=false | title='-' | $raw")
        } else {
            println("${result.kind} | dueAt=${result.dueAt != null} | title='${result.title}' | $raw")
        }
    }
}

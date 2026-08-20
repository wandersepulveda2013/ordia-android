import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.734: CUARTA clase de formas cotidianas —
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
        // (3 OPEN: "poner la mesa" c.736, "poner el lavavajillas" c.738,
        // "sacar al perro" c.740 — resuelta por la sonda paralela de
        // mascotas `FourthClassVerbDiscoveryProbe.kt` — y "pasar la
        // aspiradora" c.742 resueltas — abajo, sección regresiones)
        "hacer la compra mañana",                  // hacer + compra (≠ cama c.728)
        "colgar la ropa hoy",                      // colgar + ropa
        "hacer la colada mañana",                  // hacer + colada (~ lavadora)

        // Cobertura existente (guards — deben SEGUIR capturando vía keyword
        // c.639 de HOUSEHOLD)
        "fregar los platos esta noche",
        "regar las plantas mañana",

        // Regresiones de las clases cerradas (deben SEGUIR capturando)
        "poner la lavadora esta tarde",            // c.729
        "quitar el polvo hoy",                     // c.732
        "poner la mesa hoy",                       // c.736 (forma 1/7 clase 4)
        "poner el lavavajillas esta noche",        // c.738 (forma 2/7 clase 4)
        "sacar al perro mañana",                   // c.740 (forma 3/7 clase 4, vía mascota)
        "pasar la aspiradora mañana",              // c.742 (forma 5/7 clase 4)

        // Controles anti-overreach (deben permanecer NULL)
        "no poner la mesa hoy",
        "quizá poner la mesa hoy",
        "puse la mesa ayer",                       // pasado
        "poner",                                   // suelto
        "echar de menos a mi hermana",             // sentimento, sin fecha → NULL
        "tirar la toalla",                         // rendirse (idiomático)

        // Controles c.738 (forma "poner el lavavajillas"): negada/duda/
        // pasado/objeto no acotado deben permanecer NULL.
        "no poner el lavavajillas esta noche",
        "quizá poner el lavavajillas esta noche",
        "puse el lavavajillas ayer",               // pasado
        "poner la película a las 2",               // objeto no acotado (cine ≠ quehacer)

        // Controles c.742 (forma "pasar la aspiradora"): negada/duda/
        // pasado/objeto no acotado deben permanecer NULL.
        "no pasar la aspiradora mañana",
        "quizá pasar la aspiradora mañana",
        "pasé la aspiradora ayer",                 // pasado
        "pasar la tarde en casa",                  // objeto no acotado (el tardeo ≠ quehacer)
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

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de descubrimiento de la QUINTA clase de formas cotidianas (c.765):
 * salud/autocuidado, familia/niños, hogar-tecnología y vehículo — frases de
 * vida real que una persona captura por notificación/compartir y que el motor
 * debería retener. Hermanas de `ManagementVerbDiscoveryProbe.kt` (SEGUNDA),
 * `ThirdClassVerbDiscoveryProbe.kt` (TERCERA) y
 * `FourthClassVerbDiscoveryProbe.kt` (CUARTA).
 *
 * Doctrina: el patrón se usa TAL CUAL aparece en la sonda (ancla/guard
 * idénticos, sin derivaciones). La sonda corre PRE y POST; los candidatos
 * aún NULL se documentan en el RUN_LOG como pool OPEN y los controles de
 * anti-overreach (negación/duda/pasado/sustantivo/suelto/chat) NO deben
 * capturar nunca.
 *
 * Candidatos aceptados hasta ahora (UNO por ciclo):
 * - c.765: "tomar la medicina (a las 8)" → TASK acotado al objeto
 *   `medicinas?|medicamentos?|pastillas?` (salud/autocuidado: el olvido de
 *   mayor coste; "tomar" es bivalente — café/autobús/vuelo fuera).
 * - c.766: "ponerse la insulina mañana" → TASK acotado al objeto `insulina`
 *   ("ponerse" es bivalente — chaqueta/enfermo/contento fuera; dispersión
 *   epoch-day 20685 % 9 = 3).
 * - c.768: "pasar la ITV este mes" → TASK acotado al objeto `itv` (vehículo;
 *   "pasar" es bivalente — la tarde/el rato/la película fuera; dispersión
 *   epoch-day 20685 % 9 = 3).
 * - c.770: "tomarme la medicina esta noche" → TASK por ALTERNANCIA del
 *   piso c.765 al enclítico reflexivo `tomar|tomarme` (objeto acotado
 *   intacto; keyword "medicina" ya existía — lockstep coste-cero;
 *   dispersión epoch-day 20685 % 7 = 0; NULL PRE verificado sobre
 *   HEAD b025444).
 * - c.771: "reiniciar el router esta noche" → TASK acotado al objeto
 *   `routers?` ("reiniciar" es bivalente — ordenador/app/móvil fuera;
 *   dispersión epoch-day 20685 % 6 = 3 sobre el pool residual de 6).
 * - c.772: "medir la tensión hoy" → TASK acotado al objeto `tensi[oó]n`
 *   ("medir" es bivalente — la mesa/el espacio/el rendimiento fuera; sin
 *   plural: "las tensiones del equipo" son fricciones interpersonales;
 *   [oó] admite la grafía sin tilde; dispersión epoch-day 20685 % 5 = 0
 *   sobre el pool residual de 5; NULL PRE verificado sobre HEAD 0990f7b).
 *
 * Pool OPEN (NULL medido en PRE c.765, HEAD 9815ee2):
 * - "medirme la presión mañana" (reflexivo; objeto `presión`)
 * - "hacer copia de seguridad hoy" (hogar-tecnología; protege datos)
 * - "llevar a los niños al colegio mañana" / "llevar a la niña al médico
 *   el lunes" (familia; "recoger a los niños" ya captura vía ERRAND)
 */
@Suppress("DEPRECATION")
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // ---- Salud / autocuidado (aceptado c.765 + pool OPEN) ----
        "tomar la medicina a las 8",
        "tomar la medicina mañana",
        "tomar las pastillas hoy",
        "tomar el medicamento esta noche",
        "vale, tomar la medicina mañana",
        // c.772 OPEN residual: el reflexivo con objeto `presión` sigue OPEN
        "medirme la presión mañana",
        // ---- Hogar-tecnología (pool OPEN) ----
        "hacer copia de seguridad hoy",
        // ---- Familia / niños (parcial: pool OPEN) ----
        "llevar a los niños al colegio mañana",
        "llevar a la niña al médico el lunes",
        // ---- Regresiones (cobertura previa, no debe romperse) ----
        "sacar al perro mañana",
        "pagar el arriendo el lunes",
        "recuérdame mañana pagar el arriendo",
        "llenar el tanque mañana",
        "recoger a los niños a las 5",
        "donar sangre el sábado",
        "votar el domingo",
        "cargar el celular hoy",
        "ponerse la insulina mañana",
        "pasar la ITV este mes",
        "pasar la ITV mañana",
        "vale, pasar la ITV el viernes",
        // c.770: capturas del enclítico reflexivo (regresión)
        "tomarme la medicina esta noche",
        "tomarme las pastillas hoy",
        "tengo que tomarme la medicina esta noche",
        // c.771: capturas del piso router (regresión)
        "reiniciar el router esta noche",
        "reiniciar el router mañana",
        "vale, reiniciar el router hoy",
        "mañana reiniciar el router",
        "reiniciar routers mañana",
        // c.772: capturas del piso tensión (regresión)
        "medir la tensión hoy",
        "medir la tensión mañana",
        "vale, medir la tensión hoy",
        "mañana medir la tensión",
        "medir la tension hoy",
        // ---- Controles anti-overreach (deben quedar NULL) ----
        "no tomar la medicina mañana",
        "quizá tomar la medicina mañana",
        "tomé la medicina ayer",
        "tomar el café mañana",
        "tomar",
        "la medicina está en la mesa",
        // c.770: controles del enclítico (NULL)
        "no tomarme la medicina mañana",
        "quizá tomarme la medicina mañana",
        "me tomé la medicina ayer",
        "tomarme el café mañana",
        "tomarme",
        "no ponerse la insulina mañana",
        "quizá ponerse la insulina mañana",
        "me puse la insulina ayer",
        "ponerse la chaqueta mañana",
        "la insulina está en la nevera",
        "no pasar la ITV mañana",
        "quizá pasar la ITV mañana",
        "pasé la ITV ayer",
        "pasar la tarde",
        "pasar la película mañana",
        "la ITV del coche está cara",
        "no llenar el tanque mañana",
        "llené el tanque ayer",
        "no recoger a los niños mañana",
        "recogí a los niños ayer",
        // c.771: controles del piso router (NULL)
        "no reiniciar el router mañana",
        "quizá reiniciar el router mañana",
        "reinicié el router ayer",
        "reiniciar el ordenador mañana",
        "reiniciar",
        "el router está apagado",
        // c.772: controles del piso tensión (NULL)
        "no medir la tensión mañana",
        "quizá medir la tensión mañana",
        "medí la tensión ayer",
        "medir la mesa mañana",
        "medir",
        "la tensión está alta",
        "medir las tensiones del equipo mañana",
        "hola cómo estás"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

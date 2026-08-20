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
 *
 * Pool OPEN (NULL medido en PRE c.765, HEAD 9815ee2):
 * - "tomarme la medicina esta noche" (reflexivo 1ª persona)
 * - "medir la tensión hoy" / "medirme la presión mañana"
 * - "hacer copia de seguridad hoy" (hogar-tecnología; protege datos)
 * - "reiniciar el router esta noche"
 * - "pasar la ITV este mes" (vehículo)
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
        "tomarme la medicina esta noche",
        "medir la tensión hoy",
        "medirme la presión mañana",
        // ---- Hogar-tecnología (pool OPEN) ----
        "hacer copia de seguridad hoy",
        "reiniciar el router esta noche",
        // ---- Vehículo (parcial: pool OPEN) ----
        "pasar la ITV este mes",
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
        // ---- Controles anti-overreach (deben quedar NULL) ----
        "no tomar la medicina mañana",
        "quizá tomar la medicina mañana",
        "tomé la medicina ayer",
        "tomar el café mañana",
        "tomar",
        "la medicina está en la mesa",
        "no ponerse la insulina mañana",
        "quizá ponerse la insulina mañana",
        "me puse la insulina ayer",
        "ponerse la chaqueta mañana",
        "la insulina está en la nevera",
        "no llenar el tanque mañana",
        "llené el tanque ayer",
        "no recoger a los niños mañana",
        "recogí a los niños ayer",
        "hola cómo estás"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

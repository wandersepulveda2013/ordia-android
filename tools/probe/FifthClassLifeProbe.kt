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
 * - c.773: "llevar a los niños al colegio mañana" → ERRAND acotado a la
 *   forma completa objeto+destino `niñ[oa]s?` + `colegio|escuela|
 *   guarder[ií]a` ("llevar" es bivalente — el coche al taller c.684, al
 *   perro al veterinario c.747, a María al cine fuera; hermano SIMÉTRICO
 *   de "recoger a los niños" vía ERRAND; dispersión epoch-day 20686 % 4 = 2
 *   sobre el pool residual de 4; NULL PRE verificado sobre HEAD dda9251).
 * - c.774: "hacer copia de seguridad hoy" → TASK acotado al objeto
 *   `copias? de seguridad` con alternancia `backups?` ("hacer" es muy
 *   bivalente — la compra SHOPPING c.758, la cama HOUSEHOLD c.728,
 *   "la copia de la llave" fuera; hermano de "reiniciar el router" c.771;
 *   lockstep keyword-OBJETO "backup"; dispersión epoch-day 20686 % 3 = 1
 *   sobre el pool residual de 3; NULL PRE verificado sobre HEAD 027826b).
 * - c.775: "medirme la presión mañana" → TASK acotado a la pareja
 *   reflexivo enclítico `medirme` + objeto `presi[oó]n` (la misma
 *   medición de tensión arterial en su forma real más cotidiana —
 *   hermana de c.772 y de la alternancia enclítica c.770; la NO
 *   reflexiva "medir la presión" y "medirme la tensión" quedan FUERA —
 *   una forma por ciclo; lockstep keyword-OBJETO "presión" — colisiones
 *   "depresión"/"compresión" inertes; dispersión epoch-day 20686 % 2 = 0
 *   sobre el pool residual de 2; NULL PRE verificado sobre HEAD c5031be).
 * - c.776: "llevar a la niña al médico el lunes" → ERRAND acotado al
 *   MISMO objeto `niñ[oa]s?` del piso escolar c.773 + destino médico
 *   inequívoco `médico|doctor|dentista|hospital|consulta` ("llevar"
 *   sigue bivalente — a María al médico FUERA; hermano del piso escolar;
 *   kind ERRAND en deliberación contra APPOINTMENT: la cita es de la
 *   niña, para el usuario es desplazamiento familiar; lockstep keyword
 *   "niños" PREEXISTENTE c.773 → coste-cero; dispersión epoch-day
 *   20685 % 2 = 1 sobre el pool residual de 2; NULL PRE verificado
 *   sobre HEAD c5031be). Ítem 2/2: pool OPEN AGOTADO (c.765–c.776
 *   resolvieron las 12 formas medidas NULL en PRE c.765).
 *
 * Pool OPEN: VACÍO (los 2 ítems residuales se resolvieron en c.775
 * "medirme la presión" y c.776 "llevar a la niña al médico").
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
        // c.773: capturas del piso escolar (regresión)
        "llevar a los niños al colegio mañana",
        "llevar a los niños a la escuela hoy",
        "vale, llevar a los niños al colegio mañana",
        "mañana llevar a los niños al colegio",
        "llevo a mis niños a la guardería mañana",
        // c.774: capturas del piso copia de seguridad (regresión)
        "hacer copia de seguridad hoy",
        "hacer la copia de seguridad mañana",
        "vale, hacer copia de seguridad hoy",
        "mañana hacer copia de seguridad",
        "hacer backup hoy",
        // c.775: capturas del piso presión reflexiva (regresión)
        "medirme la presión mañana",
        "medirme la presión hoy",
        "vale, medirme la presión hoy",
        "hoy medirme la presión",
        "medirme la presion mañana",
        // c.776: capturas del piso médico familiar (regresión)
        "llevar a la niña al médico el lunes",
        "llevar a los niños al doctor mañana",
        "vale, llevar a la niña al médico mañana",
        "mañana llevar a la niña al médico",
        "llevo a mi niña al dentista mañana",
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
        // c.773: controles del piso escolar (NULL)
        "no llevar a los niños al colegio mañana",
        "quizá llevar a los niños al colegio mañana",
        "llevé a los niños al colegio ayer",
        "llevar a María al cine mañana",
        "llevar a los niños mañana",
        "los niños van al colegio mañana",
        // c.776: controles del piso médico familiar (NULL)
        "no llevar a la niña al médico mañana",
        "quizá llevar a la niña al médico mañana",
        "llevé a la niña al médico ayer",
        "llevar a María al médico mañana",
        "llevar a la niña mañana",
        "la niña va al médico mañana",
        // c.774: controles del piso copia de seguridad (NULL)
        "no hacer copia de seguridad hoy",
        "quizá hacer copia de seguridad hoy",
        "hice la copia de seguridad ayer",
        "hacer la copia de la llave hoy",
        "hacer copia",
        "la copia de seguridad falló",
        "el backup está corrupto",
        // c.775: controles del piso presión reflexiva (NULL)
        "no medirme la presión mañana",
        "quizá medirme la presión hoy",
        "me medí la presión ayer",
        "medirme la estatura mañana",
        "medir la presión mañana",
        "medirme",
        "la presión está alta",
        "hola cómo estás"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        println("  ${intent?.kind ?: "NULL"} | dueAt=${intent?.dueAt != null} | title='${intent?.title ?: "-"}' | $c")
    }
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.845 (persistida): SÉPTIMA clase de formas
 * cotidianas — DILIGENCIAS CON TERCERO/PERSONA Y PLANES SOCIALES. El pool
 * de la sexta clase (enclíticos, c.833) quedó AGOTADO en c.842; esta clase
 * explora la frontera siguiente del habla cotidiana española:
 *   - «quedar con/para» (el verbo social por excelencia: «quedar con Ana»,
 *     «quedar para cenar», «quedamos con Ana») — NO existe piso: toda la
 *     familia es NULL.
 *   - «llevar a + PERSONA» (el trayecto escolar/familiar diario: «llevar
 *     a los niños al cole») — el piso «llevar» actual (coche al taller,
 *     coche a revisión) está acotado a objetos inanimados.
 *   - Dativo enclítico de «llevar/devolver» («llevarle el almuerzo a papá»,
 *     «devolverle el dinero a Juan») — hermana de la clase sexta.
 *   - Alternancia de artículo en electrodomésticos: «poner UNA lavadora»
 *     (NULL) frente a «poner LA lavadora» (HIT HOUSEHOLD).
 *   - «cargar» dispositivos («cargar el móvil», «cargar el coche») — sin
 *     piso; el usuario lo dice a diario.
 *   - «apuntarse a» reflexivo («apuntarse al gimnasio») — la transitiva
 *     «apuntar a los niños al fútbol» SÍ captura (NOTE).
 * Misma metodología que [SixthClassEncliticProbe] (c.833): frases
 * declarativas cotidianas (compromiso plausible) + regresiones (formas que
 * YA capturan) + controles (negación, duda, narrativa pasado, verbo
 * aislado, sentido figurado). NO es un test; su salida alimenta el BACKLOG
 * (un ítem/forma por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es un
 * GAP de captura (olvido silencioso P1) si el enunciado es un compromiso
 * plausible del usuario. NULL sobre controles es CORRECTO (intencionado).
 *
 * Estado medido en c.845 (HEAD 94f9404, run_probe.sh, motor real):
 *   CAPTURAS — 12 NULLs: las 7 formas «quedar/quedamos», las 2 «llevar a
 *   los niños», «llevarle…», «devolverle…», «poner una lavadora»,
 *   «apuntarse al gimnasio», «cargar el móvil», «cargar el coche».
 *   (listadas abajo como CAPTURAS; al resolverse cada una se mueve a
 *   REGRESIONES con su ciclo, convención c.833/c.836).
 *   REGRESIONES — 12 HITs confirmados (taller, revisión, cole/paquete,
 *   biblioteca/llaves, lavadora/lavavajillas, perro, luz, DNI, médico).
 *   CONTROLES — 10 NULLs correctos (negación, duda, pasado, aislado,
 *   figurado «quedar bien»/«quedar pendiente», imperativo «quédate»).
 * REGRESIÓN c.847: la familia «quedar con/para» ya captura (piso nuevo
 * c.847 → MEETING 0.45/0.54, título con el verbo del usuario, dueAt) —
 * los 4 casos quedan abajo como casos de regresión del piso c.847.
 * REGRESIÓN c.848: «poner una lavadora» ya captura (diagonal «una» del
 * piso c.729 → HOUSEHOLD 0.45, «Poner una lavadora», dueAt) — el caso
 * queda abajo como regresión de la diagonal c.848.
 * REGRESIÓN c.850: «llevar a los niños al cole» ya captura (diagonal
 * coloquial «cole» del piso c.773 → ERRAND 0.45, «Llevar a los niños al
 * cole», dueAt) — el caso queda abajo como regresión de la diagonal
 * c.850; «al parque» (ocio, NO educativo) sigue NULL como candidata
 * propia (una forma por ciclo).
 * Observación lateral (NO de esta clase): «pagar el alquiler el día 1»
 * captura PAYMENT pero dueAt=false — «el día 1» no ancla fecha; verificar
 * si el parser compacto soporta «día N» antes de registrar candidata.
 */
fun main() {
    val now = 1723939200000L
    val cases = listOf(
        // --- REGRESIONES c.847: «quedar con/para» (eran NULL en c.845;
        // piso c.847 → HIT MEETING 0.45/0.54 con título y dueAt) ---
        "quedar con Ana el viernes",
        "quedar con el dentista el lunes",
        "quedar para cenar el sábado",
        "quedamos con Ana el viernes",
        // --- REGRESIÓN c.850: la diagonal coloquial «cole» del trayecto
        // escolar ya captura (extensión del piso c.773 → HIT ERRAND 0.45
        // con título «Llevar a los niños al cole» y dueAt) ---
        "llevar a los niños al cole mañana",
        // --- CAPTURAS: «llevar a + persona» (trayecto familiar — destino
        // de ocio NO educativo; candidata propia, una forma por ciclo) ---
        "llevar a los niños al parque mañana",
        // --- CAPTURAS: dativo enclítico llevar/devolver (clase sexta, dativo) ---
        "llevarle el almuerzo a papá mañana",
        "devolverle el dinero a Juan mañana",
        // --- REGRESIÓN c.848: artículo indeterminado en electrodomésticos
        // (era NULL en c.845; diagonal «una» del piso c.729 → HIT HOUSEHOLD
        // 0.45, título «Poner una lavadora», dueAt) ---
        "poner una lavadora esta tarde",
        // --- CAPTURAS: reflexivo «apuntarse» ---
        "apuntarse al gimnasio mañana",
        // --- CAPTURAS: «cargar» dispositivos ---
        "cargar el móvil esta noche",
        "cargar el coche antes del viaje",
        // --- REGRESIONES: deben reportar HIT ---
        "llevar el coche al taller mañana", // ERRAND taller (piso inanimado)
        "llevar el coche a revisión el lunes", // ERRAND revisión
        "recoger a los niños del cole a las 5", // ERRAND recoger + persona
        "recoger el paquete de correos mañana", // ERRAND recoger + objeto
        "devolver el libro a la biblioteca el viernes", // ERRAND devolver
        "devolver las llaves a Marta mañana", // ERRAND devolver + persona
        "poner la lavadora esta tarde", // HOUSEHOLD electrodomésticos
        "poner el lavavajillas esta noche", // HOUSEHOLD
        "sacar al perro esta noche", // HOUSEHOLD mascotas
        "pagar la luz mañana", // PAYMENT suministros
        "renovar el DNI la semana que viene", // TASK gestión documental
        "pedir cita con el médico mañana", // APPOINTMENT
        // --- CONTROLES: deben permanecer NULL ---
        "no quedar con Ana el viernes", // negación
        "quizá quedar con Ana", // duda (hedge c.649)
        "quedé con Ana ayer", // narrativa pasado
        "quedar", // verbo aislado
        "quedar bien en la reunión", // sentido figurado (causar buena impresión)
        "quedar pendiente de la respuesta", // sentido figurado (estar por resolver)
        "quédate con el cambio", // imperativo + dativo
        "llevar", // verbo aislado
        "me llevo a los niños ayer", // narrativa pasado
        "cargué el móvil anoche" // narrativa pasado
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

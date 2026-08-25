import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1127 (persistida): DECIMODUARTA clase de
 * formas cotidianas — VIDA ESCOLAR DE LOS HIJOS (reuniones de padres,
 * deberes, uniforme, autorizaciones de excursión, extraescolares,
 * meriendas, libros de texto, mensualidad — dichas como se hablan).
 * Las clases VI (enclíticos, c.834), VII (diligencias sociales,
 * c.845), VIII (vida adulta, c.857), IX (dinero y banca cotidiana,
 * c.890/c.892), X (mascotas, c.1007), XI (vida digital, c.1026),
 * XII (vida con vehículo, c.1079) y XIII (salud y autocuidado,
 * c.1102) quedaron agotadas; la frontera siguiente es la vida
 * escolar de los hijos — dominio cotidiano con coste real de olvido
 * (reunión de padres perdida, autorización de excursión sin firmar,
 * inscripción que cierra plazo). La cobertura previa es parcial y
 * acotada: c.773 («llevar a los niños al colegio», keyword-OBJETO
 * «niños») y c.898 (piso «hacer/entregar (los) deberes»).
 *
 * Misma metodología que [ThirteenthClassHealthProbe] (c.1102),
 * [TwelfthClassVehicleProbe] (c.1079), [EleventhClassDigitalProbe]
 * (c.1026) y [TenthClassPetProbe] (c.1007): frases declarativas
 * cotidianas (compromiso plausible) + regresiones (formas que YA
 * capturan) + controles (negación compuesta, duda, pasado,
 * sustantivo aislado, verbo aislado, declarativo sin compromiso).
 * NO es un test; su salida alimenta el BACKLOG (un ítem por ciclo,
 * doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es CORRECTO
 * (intencionado).
 *
 * Cobertura PREVIA relevante medida (keywords heredadas):
 * keyword-OBJETO «niños» (ContextIntent.kt l.343, c.773 — 0.12
 * inerte sola); piso `ERRAND_SCHOOL_RUN_FLOOR` «llevar a los niños
 * al colegio» (c.773); piso «hacer/entregar (los) deberes» (c.898);
 * pisos abiertos «recoger <objeto>» (c.857), «renovar <objeto>»
 * (c.698), «comprar <objeto>» SHOPPING, «pagar <objeto>» PAYMENT;
 * «apuntarse a <actividad>» (c.856, reflexivo EXIGIDO).
 *
 * Estado medido en c.1127 (HEAD remoto `6b37f23` + integraciones;
 * run_probe.sh, motor real; suite OK (8852 = 8831 [post-c.1124] + 21 [hermano c.1123 ecografía]) medida en ESTE run;
 * smoke 25/25, automation 9/9):
 *   CAPTURAS — 14/19 HITs por cobertura HEREDADA (recoger a los
 *   niños del colegio ERRAND [piso «recoger <objeto>» + keyword
 *   «niños» c.773]; llevar a los niños al colegio ERRAND [piso
 *   c.773]; reunión de padres MEETING; hacer los deberes con la
 *   niña STUDY [piso c.898]; comprar el uniforme SHOPPING; firmar
 *   la autorización de la excursión TASK; inscribir al niño en
 *   natación EXERCISE [keyword «natación»]; pagar la mensualidad
 *   del colegio PAYMENT; comprar los libros de texto SHOPPING;
 *   preparar la merienda HOUSEHOLD; recoger las notas ERRAND;
 *   reunión con la profesora MEETING 0.5; apuntar a la niña a
 *   natación EXERCISE; inscribir… natación dueAt=false por cola
 *   «en septiembre») y 5 gaps NULL en DOS familias:
 *     a) «llevar <objeto> al colegio» — 5/5 NULL (merienda C11,
 *        dinero de la excursión C15, ropa de recambio C16,
 *        proyecto de ciencias C17, almuerzo C18): el piso
 *        `ERRAND_SCHOOL_RUN_FLOOR` c.773 sólo admite «niños» como
 *        objeto. CANDIDATA (olvido real: la merienda/el dinero
 *        de la excursión olvidados en casa).
 *     b) «inscribir <niño> en <actividad sin keyword>» — C19
 *        «inscribir al niño en el campamento en julio» NULL (las
 *        hermanas C7/C14 capturan SOLO por la keyword «natación»;
 *        «campamento»/«extraescolares» sin keyword propia).
 *        CANDIDATA (plazo de inscripción perdido).
 *   REGRESIONES — 8/8 HITs intactos (llamar CALL 0.67, leche
 *   SHOPPING 0.47, luz PAYMENT, médico APPOINTMENT 0.85, perro
 *   HOUSEHOLD, coche HOUSEHOLD, dentista TASK, taller ERRAND).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta «no voy
 *   a recoger…»; duda subjuntivo «quizá lleve…»; pasado «recogí
 *   a los niños ayer»; sustantivo aislado «los deberes»; verbo
 *   aislado «inscribir»; declarativo «los niños van al colegio»
 *   — keyword sola < umbral, pin c.773; pasado 3ª persona «el
 *   niño hizo los deberes ayer»; declarativo «la excursión del
 *   colegio es en octubre»).
 *   OBSERVACIONES laterales (títulos, NO gaps de esta clase):
 *   cola «en septiembre» queda en el TÍTULO y dueAt=false (C7)
 *   — familia de colas ya documentada c.845/c.852/c.1079/c.1102;
 *   cola «el día 1» queda en el título de PAYMENT con dueAt=true
 *   (C8) — mismo patrón heredado que la regresión R3 «el día 5»,
 *   consistente.
 *
 * ACTUALIZACIÓN c.1130 (este lado): familia (c) «ayudar a <hijo>
 * con los deberes» — C26-C29 persistidos aquí tras medirse con
 * sonda efímera (motor real, PRE sobre `d20f6ae`: 4/4 NULL;
 * keyword «deberes» c.898 existía pero el piso
 * `STUDY_HOMEWORK_FLOOR` sólo admitía el verbo «hacer» → 0.22 <
 * 0.45). POST c.1130 (alternativa «ayudar a… niñ[oa]s? con
 * (los)? deberes» en el piso + plantilla hermana, lockstep
 * c.616): 4/4 NULL→HIT STUDY 0.45, títulos limpios y dueAt
 * anclado. Las etiquetas C20-C25 quedan libres para el gap (b)
 * «inscribir en campamento» y sus laterales. Olvido silencioso
 * P1: la sesión de deberes CON los hijos es el compromiso
 * escolar cotidiano por excelencia y se decía «ayudar», no
 * «hacer».
 */
fun main() {
    fun a(t: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1_700_000_000_000L)
    )
    fun show(label: String, t: String) {
        val i = a(t)
        val s = if (i == null) "[NULL] $t"
            else "[HIT] ${i.kind} ${i.confidence} | ${i.title} | dueAt=${i.dueAt != null} ← $t"
        println("$label $s")
    }

    // --- CANDIDATAS (vida escolar: compromisos cotidianos plausibles) ---
    show("C1",  "recoger a los niños del colegio a las 5")
    show("C2",  "llevar a los niños al colegio mañana")
    show("C3",  "reunión de padres en el colegio el jueves")
    show("C4",  "hacer los deberes con la niña esta tarde")
    show("C5",  "comprar el uniforme del colegio el sábado")
    show("C6",  "firmar la autorización de la excursión mañana")
    show("C7",  "inscribir al niño en natación en septiembre")
    show("C8",  "pagar la mensualidad del colegio el día 1")
    show("C9",  "comprar los libros de texto la semana que viene")
    show("C10", "preparar la merienda de los niños mañana")
    show("C11", "llevar la merienda al colegio mañana")
    show("C12", "recoger las notas del niño el viernes")
    show("C13", "reunión con la profesora el martes a las 6")
    show("C14", "apuntar a la niña a natación el lunes")

    // --- FAMILIA del gap C11 «llevar <objeto> al colegio» (dimensionado) ---
    show("C15", "llevar el dinero de la excursión al colegio mañana")
    show("C16", "llevar la ropa de recambio al colegio mañana")
    show("C17", "llevar el proyecto de ciencias al colegio el viernes")
    show("C18", "llevar el almuerzo al colegio mañana")
    // --- Familia de la observación C7 (cola «en <mes>») ---
    show("C19", "inscribir al niño en el campamento en julio")

    // --- FAMILIA (c) «ayudar a <hijo> con los deberes» (c.1130: NULL→HIT) ---
    show("C26", "ayudar a los niños con los deberes esta tarde")
    show("C27", "ayudar al niño con los deberes mañana")
    show("C28", "ayudar a la niña con los deberes esta tarde")
    show("C29", "ayudar a los niños con los deberes de matemáticas mañana")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "llamar a mamá esta noche")
    show("R2", "comprar leche esta tarde")
    show("R3", "pagar la luz el día 5")
    show("R4", "ir al médico el lunes a las 5")
    show("R5", "sacar al perro esta tarde")
    show("R6", "lavar el coche esta tarde")
    show("R7", "pedir hora al dentista mañana")
    show("R8", "llevar el coche al taller mañana")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a recoger a los niños mañana")
    show("G2", "quizá lleve a los niños al colegio mañana")
    show("G3", "recogí a los niños ayer")
    show("G4", "los deberes")
    show("G5", "inscribir")
    show("G6", "los niños van al colegio")
    show("G7", "el niño hizo los deberes ayer")
    show("G8", "la excursión del colegio es en octubre")

    // --- Pines de cierre C19 (c.1135, VERDE): el piso EXERCISE
    // «campamento» (EXERCISE_VERBS, fuente única, lockstep c.616/c.751 con
    // la keyword en ContextIntent.EXERCISE) cerró el gap medido arriba.
    // exitProcess(1) si la realidad difiere del pin (canario de regresión,
    // mismo patrón que los cierres hermanos de esta sonda).
    run {
        fun pin(desc: String, t: String, wantHit: Boolean) {
            val i = ContextIntentEngine.analyze(
                ContextEvent(ContextCaptureSource.NOTIFICATION, t, 1000)
            )
            val got = i != null
            val estado = if (got) "HIT ${i!!.kind}" else "NULL"
            println("  $desc → $estado")
            if (got != wantHit) {
                val esperado = if (wantHit) "HIT" else "NULL"
                println("  INESPERADO: se esperaba $esperado")
                kotlin.system.exitProcess(1)
            }
        }
        pin("C19a", "inscribir al niño en el campamento en julio", true)
        pin("C19b", "inscribir a los niños en el campamento la semana que viene", true)
        pin("C19c", "inscribir a la niña en el campamento en agosto", true)
        pin("C19d", "inscribir a mi niño en el campamento mañana", true)
        pin("C19e", "no inscribir al niño en el campamento", false)
        pin("C19f", "quizá inscriba al niño en el campamento en julio", false)
        // Desnuda sin cola temporal: NULL (mismo perfil que la hermana
        // «inscribir al niño en natación») — NADA roto en recorte.
        pin("C19g", "inscribir al niño en el campamento", false)
    }
}

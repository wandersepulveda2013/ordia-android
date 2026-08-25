import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda COMPLEMENTARIA de la DECIMOTERCERA clase (c.1102-complemento):
 * SALUD Y AUTOCUIDADO — matriz DIFERENTE de 24 candidatas que midió
 * este run en paralelo con la sonda primaria del hermano
 * [ThirteenthClassHealthProbe] (14 candidatas, 7 gaps a–g). COLISIÓN
 * de runs paralelos (mismo lado, misma unidad) resuelta
 * NO-destructiva: el hermano pusheadó primero (primer-push-gana);
 * esta matriz SE CONSERVA porque midió 6 gaps NUEVOS que la primaria
 * no cubrió + solapamientos que REFUERZAN candidatas ya registradas.
 * CERO cambios de producción (sonda pura, read-only por construcción;
 * DISJUNTA del marcador activo c.1103 parser — NO TOCAR).
 *
 * Misma metodología que la primaria (heredada de
 * [TwelfthClassVehicleProbe] c.1079 y anteriores): frases declarativas
 * cotidianas (compromiso plausible) + regresiones (formas que YA
 * capturan) + controles (NULLs correctos). NO es un test; su salida
 * alimenta el BACKLOG (un ítem por ciclo, doctrina anti-overreach).
 *
 * Estado medido (run_probe.sh sobre la UNIÓN HEAD `dece72de`
 * [post-hermano c.1102 + c.1098 parser + c.1099 assistant +
 * c.1097 context «inflar ruedas» + c.1101 domain ?iu +
 * c.1103 parser ?iu FIXED + bis c.1102];
 * re-ejecuciones sobre `22741a2`/`cff687a`/`95c95538`/`7b74fa46`
 * BYTE-IDÉNTICAS — las regiones parser/assistant/context son
 * DISJUNTAS de estas frases de salud;
 * suite UNIÓN OK (8601 = 8535 + 22 [c.1098 parser] + 28 [c.1097 context + c.1101 domain] + 16 [c.1103 parser]), smokes 25/25 y 9/9):
 *   CONFIRMADAS (solapamiento con la primaria, misma candidata):
 *   «llevar al niño al pediatra el martes» NULL (su (b)); formas
 *   NOMINALES de sus (a)/(e)/(f)/(g) — «analítica en ayunas mañana
 *   a las 8» NULL (hermana nominal de «hacerme las analíticas»),
 *   «revisión de la vista en octubre» NULL (nominal de «hacerme la
 *   revisión»), «limpieza de boca en diciembre» NULL (nominal de
 *   «ir a la limpieza dental»), «ecografía del bebé el jueves» NULL
 *   (nominal de «hacerse la ecografía») — la forma NOMINAL es un
 *   gap separado de la reflexiva; un piso reflexivo acotado NO las
 *   cubrirá.
 *   GAPS NUEVOS (no cubiertos por la primaria; registrados i–n en
 *   BACKLOG):
 *     i) «ir al dermatólogo el viernes» NULL — «ir al médico» tiene
 *        keyword APPOINTMENT pero las ESPECIALIDADES no (asimetría
 *        medida: médico 0.85 HIT vs dermatólogo NULL).
 *     j) «sacar la muela del juicio la semana que viene» NULL —
 *        «sacar» pisa sólo acotado a mascota/coche (primaria (c));
 *        «muela» sin keyword.
 *     k) «empaste en la muela mañana» NULL — forma nominal dental.
 *     l) «revisión ginecológica en abril» NULL — nominal (hermana
 *        de su «pedir turno para el ginecólogo» HIT — asimetría).
 *     m) «operar la rodilla en enero» NULL — verbo sin piso.
 *     n) «empezar la dieta el lunes» NULL — verbo abierto sin piso.
 *   HITs HEREDADOS medidos aquí y NO en la primaria (enriquecen la
 *   evidencia de cobertura): «cita con el dentista» APPOINTMENT
 *   0.87, «cita con el psicólogo» APPOINTMENT 0.87, «pedir cita con
 *   el fisio» APPOINTMENT 0.47, «comprar las lentillas» SHOPPING,
 *   «recoger las gafas nuevas» ERRAND, «recoger la medicación»
 *   ERRAND, «renovar la receta» TASK (piso abierto c.698),
 *   «hacerme un análisis de sangre» ERRAND c.862, «medir la
 *   tensión» TASK c.772, «tomar la medicina» TASK c.765,
 *   «ponerme la vacuna de la gripe» TASK c.1044, «donar sangre»
 *   TASK 0.45, «pedir hora para el dentista» TASK 0.45.
 *   REGRESIONES — 8/8 HITs intactas (médico APPOINTMENT 0.85,
 *   medicina c.765, vacuna c.1044, análisis ERRAND c.862, tensión
 *   c.772, farmacia SHOPPING, llamar CALL 0.67, leche SHOPPING).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta «no voy a
 *   ir al dentista»; duda subjuntivo «quizá pida hora»; pasado «fui
 *   al dentista ayer»; sustantivo aislado «el dentista»; estado
 *   «las gafas están rotas», «mi hijo tiene fiebre»; «la receta de
 *   la abuela» desambiguado — keyword «receta» sola < umbral;
 *   «vista aérea» figurado).
 *   OBSERVACIONES laterales (parser/títulos, NO de esta clase):
 *   month-hints «en octubre/…» dueAt=false y colas relativas en el
 *   título («este fin de semana», «esta semana», «en noviembre») —
 *   consistente con lo registrado c.845/c.852/c.1079 y con la
 *   primaria.
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

    // --- CANDIDATAS (salud/autocuidado: compromisos cotidianos plausibles) ---
    show("C1", "pedir hora para el dentista mañana")
    show("C2", "cita con el dentista el lunes a las 5")
    show("C3", "recoger las gafas nuevas el jueves")
    show("C4", "comprar las lentillas este fin de semana")
    show("C5", "revisión de la vista en octubre")
    show("C6", "pedir cita con el fisio mañana")
    show("C7", "llevar al niño al pediatra el martes")
    show("C8", "cita con el psicólogo el miércoles a las 6")
    show("C9", "renovar la receta del médico esta semana")
    show("C10", "recoger la medicación en la farmacia esta tarde")
    show("C11", "analítica en ayunas mañana a las 8")
    show("C12", "hacerme un análisis de sangre el lunes")
    show("C13", "ponerme la vacuna de la gripe en noviembre")
    show("C14", "medir la tensión esta noche")
    show("C15", "tomar la medicina a las 9")
    show("C16", "ir al dermatólogo el viernes")
    show("C17", "limpieza de boca en diciembre")
    show("C18", "sacar la muela del juicio la semana que viene")
    show("C19", "empaste en la muela mañana")
    show("C20", "empezar la dieta el lunes")
    show("C21", "donar sangre el sábado por la mañana")
    show("C22", "revisión ginecológica en abril")
    show("C23", "ecografía del bebé el jueves")
    show("C24", "operar la rodilla en enero")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "ir al médico el lunes a las 5")
    show("R2", "tomar la medicina mañana")
    show("R3", "ponerme la vacuna mañana")
    show("R4", "hacerme un análisis de sangre el lunes")
    show("R5", "medir la tensión esta noche")
    show("R6", "ir a la farmacia esta tarde")
    show("R7", "llamar a mamá esta noche")
    show("R8", "comprar leche esta tarde")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a ir al dentista mañana")
    show("G2", "quizá pida hora para el dentista el lunes")
    show("G3", "fui al dentista ayer")
    show("G4", "el dentista")
    show("G5", "las gafas están rotas")
    show("G6", "mi hijo tiene fiebre")
    show("G7", "la receta de la abuela está buenísima")
    show("G8", "vista aérea de la ciudad")
}

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.890b (persistida): NOVENA clase de formas
 * cotidianas — COORDINACIÓN Y PRÉSTAMOS CON PERSONAS (avisar a
 * alguien, devolver objetos prestados, pedir prestado, traer/llevar
 * cosas a alguien). La clase OCTAVA (gestiones de la vida adulta,
 * c.857) quedó AGOTADA en c.865 («reclamar factura», últimas
 * laterales resueltas hasta c.889); las clases VI («recuérdame»,
 * c.821/c.822), VII (diligencias con persona, c.845) y VIII (them
 * c.857) dejaron una frontera: la coordinación interpersonal en
 * primera persona dicha como se habla.
 *
 * Misma metodología que [EighthClassAdminProbe] (c.857) y
 * [SeventhClassErrandProbe] (c.845): frases declarativas cotidianas
 * (compromiso plausible) + regresiones (formas que YA capturan) +
 * controles (negación, duda, pasado, verbo aislado, sustantivo,
 * figurado). NO es un test; su salida alimenta el BACKLOG (un ítem
 * por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana es
 * un GAP de captura (olvido silencioso P1) si el enunciado es un
 * compromiso plausible del usuario. NULL sobre controles es
 * CORRECTO (intencionado).
 *
 * Estado medido en c.890b (HEAD 94aadb4 tras el STALE_RUN-c.889b;
 * run_probe.sh, motor real; suite OK (5863)):
 *   CAPTURAS (gaps NULL confirmados — la clase está CASI CUBIERTA):
 *     1) «traer el cargador a Ana mañana» — coordinación/recado con
 *        persona (verbo «traer» sin keyword ni piso; los hermanos
 *        «llevarle su cuaderno a Ana» y «recoger el paquete…» ya
 *        capturan ERRAND). CANDIDATA P1 para su propio ciclo.
 *     2) «dar las gracias a Ana por el regalo» — comunicación de
 *        gratitud pendiente (hermana de «avisar a…» TASK; ni «dar»
 *        ni «gracias» activan piso alguno). CANDIDATA P1 para su
 *        propio ciclo.
 *   REGRESIONES — 8 HITs (llamar CALL 0.67, contestar TASK,
 *   devolver la llamada ERRAND, prueba de sonido ERRAND, reclamar
 *   factura TASK, hipoteca PAYMENT, médico APPOINTMENT 0.85, pan
 *   SHOPPING 0.47) + 12/14 de la clase YA capturan (avisar a… TASK
 *   0.45 ×4, devolver el libro/cargador/dinero ERRAND ×4, pedir
 *   prestado TASK ×2, llevarle ERRAND, recoger el paquete ERRAND).
 *   CONTROLES — 5 NULLs correctos (negación, duda, pasado,
 *   sustantivo «préstamo», figurado «preparar el terreno») y 3
 *   relajaciones medidas (verbo suelto HIT: «devolver el libro» /
 *   «avisar a» / «pedir prestado» — invariante anti-overreach del
 *   verbo aislado relajada en los pisos hermanos existentes;
 *   observación lateral del área, NO de esta clase en sí;
 *   verificar patrón antes de abrir candidata).
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

    // --- CANDIDATAS (coordinación y préstamos con personas) ---
    show("C1", "avisar a Ana de que llegamos tarde")
    show("C2", "avisar a mi jefe de que llegamos a las ocho")
    show("C3", "avisar al grupo de que hay huelga mañana")
    show("C4", "devolver el libro a María mañana")
    show("C5", "devolverle el libro a María mañana")
    show("C6", "devolver el cargador a Juan el lunes")
    show("C7", "pedir prestado el cargador a Juan esta noche")
    show("C8", "pedir prestado el paraguas a Irene esta tarde")
    show("C9", "traer el cargador a Ana mañana")
    show("C10", "llevarle su cuaderno a Ana esta tarde")
    show("C11", "recoger el paquete en casa de Irene mañana")
    show("C12", "avisar a María de que se cancela la reunión")
    show("C13", "devolver el dinero a Ana el viernes")
    show("C14", "dar las gracias a Ana por el regalo")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "llamar a mamá esta noche")
    show("R2", "contestar a Juan mañana")
    show("R3", "devolver la llamada a mi madre esta noche")
    show("R4", "hacerme la prueba de sonido mañana")
    show("R5", "reclamar una factura mañana")
    show("R6", "pagar la hipoteca el día 1")
    show("R7", "ir al médico el lunes a las 5")
    show("R8", "comprar pan esta tarde")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no avisar a Ana de que llegamos tarde")
    show("G2", "quizá devolver el libro a María mañana")
    show("G3", "avisé a Ana ayer de que llegábamos tarde")
    show("G4", "devolver el libro")
    show("G5", "avisar a")
    show("G6", "el préstamo de dinero")
    show("G7", "preparar el terreno antes de negociar")
    show("G8", "pedir prestado")
}

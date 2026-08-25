import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine

/**
 * Sonda de DESCUBRIMIENTO c.1165 (persistida): DECIMOCTAVA clase de
 * formas cotidianas — VIDA SOCIAL Y EVENTOS (los compromisos con
 * otras personas dichos como se habla: el regalo de cumpleaños,
 * reservar mesa, felicitar, confirmar asistencia, la fiesta del
 * cole, apuntar a los niños, devolver lo prestado). Las clases VI
 * (enclíticos, c.834), VII (diligencias sociales, c.845), VIII
 * (vida adulta, c.857), IX (dinero, c.890/c.892), X (mascotas,
 * c.1007), XI (vida digital, c.1026), XII (vehículo, c.1079),
 * XIII (salud), XIV (escuela), XV (burocracia, c.1132), XVI
 * (viajes, c.1137) y XVII (vida laboral, c.1147) están abiertas o
 * agotadas; la frontera siguiente es lo social — dominio cotidiano
 * con coste real de olvido (el regalo sin comprar, la mesa sin
 * reservar, la felicitación olvidada, la boda sin confirmar).
 *
 * Misma metodología que [TwelfthClassVehicleProbe] (c.1079) y
 * anteriores: frases declarativas cotidianas (compromiso
 * plausible) + regresiones (formas que YA capturan) + controles
 * (negación compuesta, duda, pasado, sustantivo aislado, figurado,
 * no-compromiso). NO es un test; su salida alimenta el BACKLOG
 * (un ítem por ciclo, doctrina anti-overreach).
 *
 * Criterio de lectura: NULL sobre una forma DECLARATIVA cotidiana
 * es un GAP de captura (olvido silencioso P1) si el enunciado es
 * un compromiso plausible del usuario. NULL sobre controles es
 * CORRECTO (intencionado).
 *
 * Estado medido en c.1165 (run_probe.sh, motor real, base
 * a1d8a643 post-c.1155 del hermano + c.1162 propio; suite OK
 * (9297), smoke 25/25, automation 9/9):
 *   CAPTURAS — 10/14 HITs por cobertura HEREDADA (regalo/entradas
 *   SHOPPING vía keyword comprar; reservar mesa TASK; confirmar
 *   asistencia TASK; enviar invitación TASK; preparar la cena
 *   HOUSEHOLD; organizar la fiesta TASK; pedir la tarta TASK;
 *   devolver el libro ERRAND; quedar con los primos MEETING) y
 *   3 gaps NULL:
 *     a) «felicitar a Laura mañana» — «felicitar» sin piso ni
 *        keyword (la felicitación de cumpleaños olvidada es el
 *        caso social canónico de coste). CANDIDATA FUERTE.
 *     b) «llevar a los niños a la fiesta del cole el viernes» —
 *        los pisos «llevar» están acotados (taller/escuela/vet/
 *        aeropuerto); fiesta del cole queda fuera. CANDIDATA.
 *     c) «colgar las fotos de la boda» — «colgar» bivalente y
 *        sin keyword; caso más débil (frecuencia baja). CANDIDATA
 *        DÉBIL (evaluar necesidad antes de implementar).
 *   REGRESIONES — 8/8 HITs intactos (llamar CALL 0.67, leche
 *   SHOPPING, luz PAYMENT, médico APPOINTMENT 0.85, taller
 *   ERRAND, perro HOUSEHOLD, «dar de alta el seguro» TASK c.1162
 *   y «preparar la entrevista» TASK c.1155 del hermano — UNIÓN
 *   de ambos lados verificada en vivo).
 *   CONTROLES — 8/8 NULLs correctos (negación compuesta, duda
 *   subjuntivo, pasado ×2, sustantivo aislado ×2, verbo aislado,
 *   estado pasado «la fiesta fue…»).
 *   OBSERVACIONES laterales (NO de esta clase):
 *   - C9 «apuntar a los niños al campamento de verano» captura
 *     con kind EXERCISE (piso gimnasio «apuntar» c.11xx): el
 *     compromiso SÍ se captura y el título es correcto, pero el
 *     kind es semánticamente erróneo → lateral ABIERTA de guard
 *     (acotar el piso EXERCISE de «apuntar» al ámbito deportivo
 *     o re-categorizar con objeto no deportivo). Registrar en
 *     BACKLOG como P2 honestidad-de-kind.
 *   - C1/C4/C7/C8 dueAt=false: «esta semana»/sin-temporal no
 *     ancla fecha en el context (consistente con c.845/c.852).
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

    // --- CANDIDATAS (vida social: compromisos cotidianos plausibles) ---
    show("C1", "comprar un regalo para el cumpleaños de Ana")
    show("C2", "reservar mesa para cuatro el sábado")
    show("C3", "felicitar a Laura mañana")
    show("C4", "confirmar asistencia a la boda esta semana")
    show("C5", "enviar la invitación del cumpleaños esta tarde")
    show("C6", "llevar a los niños a la fiesta del cole el viernes")
    show("C7", "preparar la cena de Nochebuena")
    show("C8", "organizar la fiesta sorpresa de mamá")
    show("C9", "apuntar a los niños al campamento de verano")
    show("C10", "pedir la tarta para el cumpleaños del jueves")
    show("C11", "colgar las fotos de la boda")
    show("C12", "comprar las entradas del concierto mañana")
    show("C13", "devolver el libro a Marta el lunes")
    show("C14", "quedar con los primos para cenar el sábado")

    // --- REGRESIONES (formas que YA capturan, canario) ---
    show("R1", "llamar a mamá esta noche")
    show("R2", "comprar leche esta tarde")
    show("R3", "pagar la luz el día 5")
    show("R4", "ir al médico el lunes a las 5")
    show("R5", "llevar el coche al taller mañana")
    show("R6", "sacar al perro mañana")
    show("R7", "dar de alta el seguro mañana")
    show("R8", "preparar la entrevista el viernes")

    // --- CONTROLES (NULLs correctos esperados) ---
    show("G1", "no voy a felicitar a Laura mañana")
    show("G2", "quizá felicite a Laura")
    show("G3", "felicité a Laura ayer")
    show("G4", "el regalo de cumpleaños")
    show("G5", "felicitar")
    show("G6", "la fiesta fue muy divertida")
    show("G7", "las campanas de la boda")
    show("G8", "devolví el libro ayer")
}

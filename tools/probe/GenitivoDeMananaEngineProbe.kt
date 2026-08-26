import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEvent
import com.ordia.app.context.ContextIntentEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sonda c.1192: genitivo-temporal «de mañana» en ContextIntentEngine
 * (descubrimiento c.1187 [este lado, renumerado c.1175→c.1194→c.1193 por primer-marcador-gana — el hermano fijó c.1175, c.1180, c.1181, c.1182, c.1183, c.1184, c.1185, c.1186 y c.1187 primero en el remoto, lección c.1077], clase DISJUNTA de TODOS los
 * marcadores activos (todos del hermano): c.1180 reflexivo «llevarme»
 * (FIXED), c.1181 «tirar la basura», c.1182 «responder el mail», c.1183
 * «hacer el curso» (cedida-verificada), c.1184 abuelos-médico (EN CURSO),
 * c.1185 «el CV» (EN CURSO), c.1186 «check-in del hotel» (EN CURSO), c.1187
 * «contestar el mail» (EN CURSO) — esta clase es extractDateTime + sanitizeTitle, regiones disjuntas).
 *
 * PRE (c.1192, verificado con esta sonda): «de mañana» era excluido de la
 * regla «mañana»=día siguiente por `mananaSuffix` (tratado como sufijo de
 * meridiano sin artículo), así que TODA la familia nacía SIN dueAt
 * (olvido silencioso P1: sin recordatorio ni What Now) y, en lockstep,
 * el guard genitivo c.690 de `sanitizeTitle` conservaba «de mañana» en el
 * título visible ('Reunión: de mañana', 'Estudio: de mañana'). Además el
 * caso con hora explícita («la reunión de mañana a las 5») perdía la fecha
 * y caía a HOY 05:00 = pasado. Paridad con NaturalTaskParser, que SÍ
 * resuelve el genitivo («examen de mañana» → mañana 09:00, título limpio).
 *
 * POST (verificado c.1192 tras el fix): toda la familia resuelve a mañana
 * (08-27 12:00 por defecto; «de mañana a las 5» → 08-27 05:00, ya no HOY
 * pasado) y el título queda limpio ('Reunión', 'Estudio', 'Cita con el
 * médico'). Controles intactos: «de la mañana» (meridiano) NO se fecha
 * para mañana (hoy 09:00), «pasado mañana» → +2 días, «mañana por la
 * mañana» → mañana 09:00.
 *
 * Controles: «mañana» desnudo sigue resolviendo mañana; «de la mañana»
 * (meridiano con artículo) NO se fecha para mañana; «pasado mañana» sigue
 * +2 días; «mañana por la mañana» = mañana 09:00.
 *
 * Laterales ABIERTAS (fuera de alcance, una forma por ciclo): residuo de
 * título «para mañana» (dueAt correcto, título sucio), residuo «de hoy»/
 * «de ayer» (mismo guard c.690), captura NULL de «cena de esta noche» y
 * de «inscribir a los niños en el campamento» (nota: el hallazgo c.1153
 * «apuntar al campamento → EXERCISE» es OBSOLETO: hoy da NOTE honesto).
 */
fun main() {
    // Miercoles 2026-08-26 12:00 UTC (fijo; el motor usa la zona del sistema).
    val now = 1787745600000L
    val dt = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    val cases = listOf(
        // Familia genitivo-temporal «de mañana» (PRE: dueAt=false)
        "la reunión de mañana",
        "examen de mañana",
        "la cita con el médico de mañana",
        "llevar el portátil al trabajo de mañana",
        "reunión con el equipo de mañana",
        "la reunión de mañana a las 5",
        // Controles de regresión (no deben cambiar)
        "reunión con el equipo mañana",
        "examen mañana",
        "cita con el médico a las 9 de la mañana",
        "entregar el informe pasado mañana",
        "cita con el médico mañana por la mañana"
    )
    for (c in cases) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, c, now)
        )
        val due = intent?.dueAt?.let { dt.format(Instant.ofEpochMilli(it)) } ?: "-"
        println("  ${intent?.kind ?: "NULL"} | conf=${intent?.confidence ?: "-"} | dueAt=$due | title='${intent?.title ?: "-"}' | $c")
    }
}

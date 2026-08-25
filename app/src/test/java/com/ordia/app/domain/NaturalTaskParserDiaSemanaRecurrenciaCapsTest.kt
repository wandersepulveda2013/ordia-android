package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * c.1106: listas de días con acento en MAYÚSCULAS — lateral ABIERTA
 * registrada en el run c.1103. Misma clase que c.1096/c.1098/c.1101/c.1103:
 * `(?i)` Java/Kotlin es ASCII-only, así que los patrones de lista de días de
 * [NaturalTaskParser.parseRecurrence] (dayListPattern, weekdayRangePattern,
 * weekdayCountPattern y su dayNameRegex) dejan de casar el día ACENTUADO
 * (mi[eé]rcoles / s[aá]bado) cuando viene en mayúscula (MIÉRCOLES, SÁBADOS).
 *
 * Medida PRE con sonda efímera /tmp/probe1106/Probe.kt (motor real vía
 * tools/run_probe.sh, now=domingo 2026-08-23 12:00 America/Santo_Domingo):
 *  - «cena con los abuelos los lunes y los MIÉRCOLES» → WEEKLY days=1 (solo
 *    lunes) + residuo «y los MIÉRCOLES» en el título: el miércoles se pierde
 *    EN SILENCIO (rutina mutilada: la cena del miércoles nunca se recuerda)
 *    y el título queda corrupto.
 *  - «reunión LUNES MIÉRCOLES Y VIERNES» → recurrence=NONE + «MIÉRCOLES Y»
 *    residual + due=2026-08-24 (el lunes cayó a fecha única): la rutina de
 *    3 días se guarda como tarea ÚNICA — se dispara una vez y nunca más.
 *  - «gym TODOS LOS SÁBADOS» → recurrence=NONE + título íntegro sucio
 *    + due=null: la rutina se pierde por completo.
 *  - «yoga CADA DOS MIÉRCOLES» → recurrence=MONTHLY día 2 + residuo
 *    «MIÉRCOLES» (caída al «cada N» a-secas ya documentada en c.343):
 *    frecuencia equivocada (mensual en vez de quincenal los miércoles).
 *  - «gimnasio DE LUNES A MIÉRCOLES» → recurrence=NONE + residuo
 *    «A MIÉRCOLES» + el lunes como fecha única.
 *  - «clases ENTRE SÁBADO Y DOMINGO» → recurrence=NONE + residuo
 *    «ENTRE SÁBADO Y» + domingo como fecha única.
 *  - ASCII pineadas desde el PRE: «cena LOS LUNES Y JUEVES», «fútbol CADA
 *    DOMINGO», «clases EL MARTES Y EL JUEVES» intactas.
 *  - Anti-overreach: «MAÑANA VOY AL MÉDICO» (fecha única, reparada c.1098)
 *    NO debe volverse recurrencia.
 *
 * Fix: (?i)→(?iu) en los 4 patrones de la familia lista-de-días dentro de
 * parseRecurrence (weekdayRangePattern, dayNameRegex, weekdayCountPattern,
 * dayListPattern). Paridad byte-idéntica de ancla/recurrencia/título con la
 * hermana minúscula pineada.
 */
class NaturalTaskParserDiaSemanaRecurrenciaCapsTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertWeekly(
        input: String,
        days: String,
        interval: Int = 1,
        expectedDue: LocalDateTime,
        expectedTitle: String
    ) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertEquals("«$input» debe ser WEEKLY como su hermana minúscula",
            RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("«$input» intervalo", interval, result.recurrenceInterval)
        assertEquals("«$input» días", days, result.recurrenceDays)
        assertEquals("«$input» debe anclar como su hermana minúscula",
            expectedDue,
            result.dueAt?.let {
                DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
            })
        assertEquals("«$input» debe limpiar el título como su hermana minúscula",
            expectedTitle, result.title)
    }

    // ---- RED: caps con acento mutilan/pierden la lista de días ----

    @Test
    fun `caps lista articulada con miercoles en mayusculas recupera ambos dias`() {
        assertWeekly("cena con los abuelos los lunes y los MIÉRCOLES",
            "1,3", expectedDue = LocalDateTime.of(2026, 8, 24, 9, 0),
            expectedTitle = "cena con los abuelos")
    }

    @Test
    fun `caps lista toda en mayusculas con miercoles recupera ambos dias`() {
        assertWeekly("cena con los abuelos LOS LUNES Y LOS MIÉRCOLES",
            "1,3", expectedDue = LocalDateTime.of(2026, 8, 24, 9, 0),
            expectedTitle = "cena con los abuelos")
    }

    @Test
    fun `caps lista desnuda de tres dias con miercoles recupera la rutina`() {
        assertWeekly("reunión LUNES MIÉRCOLES Y VIERNES",
            "1,3,5", expectedDue = LocalDateTime.of(2026, 8, 24, 9, 0),
            expectedTitle = "reunión")
    }

    @Test
    fun `caps sabados y domingos recupera fin de semana completo`() {
        assertWeekly("gym SÁBADOS Y DOMINGOS",
            "6,7", expectedDue = LocalDateTime.of(2026, 8, 29, 9, 0),
            expectedTitle = "gym")
    }

    @Test
    fun `caps todos los sabados recupera rutina semanal`() {
        assertWeekly("gym TODOS LOS SÁBADOS",
            "6", expectedDue = LocalDateTime.of(2026, 8, 29, 9, 0),
            expectedTitle = "gym")
    }

    @Test
    fun `caps entre sabado y domingo recupera fin de semana completo`() {
        assertWeekly("clases ENTRE SÁBADO Y DOMINGO",
            "6,7", expectedDue = LocalDateTime.of(2026, 8, 29, 9, 0),
            expectedTitle = "clases")
    }

    @Test
    fun `caps rango de lunes a miercoles recupera rango completo`() {
        assertWeekly("gimnasio DE LUNES A MIÉRCOLES",
            "1,2,3", expectedDue = LocalDateTime.of(2026, 8, 24, 9, 0),
            expectedTitle = "gimnasio")
    }

    @Test
    fun `caps cada dos miercoles es quincenal y no mensual`() {
        assertWeekly("yoga CADA DOS MIÉRCOLES",
            "3", interval = 2, expectedDue = LocalDateTime.of(2026, 8, 26, 9, 0),
            expectedTitle = "yoga")
    }

    // ---- Paridad con la hermana minúscula (mismo resultado exacto) ----

    private fun assertParity(input: String, lowercaseSister: String) {
        val a = NaturalTaskParser.parse(input, now, zone)
        val b = NaturalTaskParser.parse(lowercaseSister, now, zone)
        assertEquals("«$input» recurrencia", b.recurrence, a.recurrence)
        assertEquals("«$input» intervalo", b.recurrenceInterval, a.recurrenceInterval)
        assertEquals("«$input» días", b.recurrenceDays, a.recurrenceDays)
        assertEquals("«$input» ancla", b.dueAt, a.dueAt)
        assertEquals("«$input» título", b.title, a.title)
    }

    @Test
    fun `caps lista articulada miercoles en paridad con minuscula`() {
        assertParity("cena con los abuelos los lunes y los MIÉRCOLES",
            "cena con los abuelos los lunes y los miércoles")
    }

    @Test
    fun `caps lista desnuda miercoles en paridad con minuscula`() {
        assertParity("reunión LUNES MIÉRCOLES Y VIERNES",
            "reunión lunes miércoles y viernes")
    }

    @Test
    fun `caps todos los sabados en paridad con minuscula`() {
        assertParity("gym TODOS LOS SÁBADOS", "gym todos los sábados")
    }

    @Test
    fun `caps entre sabado y domingo en paridad con minuscula`() {
        assertParity("clases ENTRE SÁBADO Y DOMINGO", "clases entre sábado y domingo")
    }

    @Test
    fun `caps rango a miercoles en paridad con minuscula`() {
        assertParity("gimnasio DE LUNES A MIÉRCOLES", "gimnasio de lunes a miércoles")
    }

    @Test
    fun `caps cada dos miercoles en paridad con minuscula`() {
        assertParity("yoga CADA DOS MIÉRCOLES", "yoga cada dos miércoles")
    }

    // ---- Pins ASCII caps: intactas desde el PRE ----

    @Test
    fun `pin caps ascii lista lunes y jueves intacta`() {
        assertWeekly("cena LOS LUNES Y JUEVES",
            "1,4", expectedDue = LocalDateTime.of(2026, 8, 24, 9, 0),
            expectedTitle = "cena")
    }

    @Test
    fun `pin caps ascii cada domingo intacta`() {
        assertWeekly("fútbol CADA DOMINGO",
            "7", expectedDue = LocalDateTime.of(2026, 8, 30, 9, 0),
            expectedTitle = "fútbol")
    }

    @Test
    fun `pin caps ascii el martes y el jueves intacta`() {
        assertWeekly("clases EL MARTES Y EL JUEVES",
            "2,4", expectedDue = LocalDateTime.of(2026, 8, 25, 9, 0),
            expectedTitle = "clases")
    }

    // ---- Anti-overreach: fecha única no se convierte en recurrencia ----

    @Test
    fun `pin manana voy al medico sigue siendo fecha unica`() {
        val r = NaturalTaskParser.parse("MAÑANA VOY AL MÉDICO", now, zone)
        assertEquals(RecurrenceFrequency.NONE, r.recurrence)
        assertEquals("VOY AL MÉDICO", r.title)
    }
}

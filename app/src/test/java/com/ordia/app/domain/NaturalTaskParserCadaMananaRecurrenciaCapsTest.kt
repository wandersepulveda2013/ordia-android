package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1103: recurrencias «cada mañana» / «todas las mañanas» (y hermanas con
 * parte del día acentuada) en MAYÚSCULAS — lateral ABIERTA registrada en la
 * auditoría c.1098. Misma clase que c.1098: `(?i)` Java/Kotlin es ASCII-only,
 * así que [partOfDayDailyPattern] deja de casar cuando la letra acentuada
 * (ma[nñ]ana) viene en mayúscula.
 *
 * Medida PRE con sondas efímeras /tmp/probe1103/Probe.kt, Probe2.kt y
 * Probe3.kt (motor real vía tools/run_probe.sh, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo):
 *  - «organizar inbox CADA MAÑANA» → recurrence=NONE + residuo «CADA» en el
 *    título + due=2026-08-24T09:00 (la «MAÑANA» cayó a fecha del día
 *    siguiente vía c.1098): la rutina diaria se guarda como tarea ÚNICA
 *    mañana — el recordatorio dispara una vez y nunca más (P1 evitar
 *    olvidos), con título corrupto.
 *  - «tomar pastillas TODAS LAS MAÑANAS» → recurrence=NONE + título íntegro
 *    sucio + due=null (la recurrencia se pierde en silencio).
 *  - Hermanas ASCII («CADA TARDE», «CADA NOCHE», «TODAS LAS TARDES»…)
 *    intactas desde el PRE — pins.
 *  - Anti-overreach: «MAÑANA VOY AL MÉDICO» (fecha única, ya reparada en
 *    c.1098) NO debe volverse recurrencia — pin.
 *
 * Fix: UN punto, (?i)→(?iu) en partOfDayDailyPattern. Paridad byte-idéntica
 * de ancla, recurrencia y título con la hermana minúscula pineada.
 */
class NaturalTaskParserCadaMananaRecurrenciaCapsTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertDaily(input: String, expectedDue: LocalDateTime, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertEquals("«$input» debe ser DAILY como su hermana minúscula",
            RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals("«$input» intervalo 1", 1, result.recurrenceInterval)
        assertEquals("«$input» debe anclar como su hermana minúscula",
            expectedDue,
            result.dueAt?.let {
                DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
            })
        assertEquals("«$input» debe limpiar el título como su hermana minúscula",
            expectedTitle, result.title)
    }

    // ---- RED: caps con acento pierden la recurrencia ----

    @Test
    fun `caps cada manana recupera rutina diaria`() {
        assertDaily("organizar inbox CADA MAÑANA",
            LocalDateTime.of(2026, 8, 23, 9, 0), "organizar inbox")
    }

    @Test
    fun `caps cada manana con hora explicita recupera rutina`() {
        assertDaily("reunión CADA MAÑANA A LAS 9",
            LocalDateTime.of(2026, 8, 23, 9, 0), "reunión")
    }

    @Test
    fun `caps regar las plantas cada manana recupera rutina`() {
        assertDaily("regar las plantas CADA MAÑANA",
            LocalDateTime.of(2026, 8, 23, 9, 0), "regar las plantas")
    }

    @Test
    fun `caps todas las mananas recupera rutina diaria`() {
        assertDaily("tomar pastillas TODAS LAS MAÑANAS",
            LocalDateTime.of(2026, 8, 23, 9, 0), "tomar pastillas")
    }

    @Test
    fun `caps todas las mananas con hora explicita recupera rutina`() {
        assertDaily("meditación TODAS LAS MAÑANAS A LAS 6",
            LocalDateTime.of(2026, 8, 23, 6, 0), "meditación")
    }

    // ---- Pins: hermanas ASCII ya casaban desde el PRE ----

    @Test
    fun `pin caps cada madrugada ya era diaria`() {
        assertDaily("meditar CADA MADRUGADA",
            LocalDateTime.of(2026, 8, 23, 4, 0), "meditar")
    }

    @Test
    fun `pin caps cada tarde con hora pm ya era diaria`() {
        assertDaily("gimnasio CADA TARDE A LAS 5",
            LocalDateTime.of(2026, 8, 23, 17, 0), "gimnasio")
    }

    @Test
    fun `pin caps cada noche ya era diaria`() {
        assertDaily("caminar CADA NOCHE",
            LocalDateTime.of(2026, 8, 23, 21, 0), "caminar")
    }

    @Test
    fun `pin caps todas las tardes ya era diaria`() {
        assertDaily("pasear al perro TODAS LAS TARDES",
            LocalDateTime.of(2026, 8, 23, 15, 0), "pasear al perro")
    }

    @Test
    fun `pin caps todas las noches ya era diaria`() {
        assertDaily("dormir TODAS LAS NOCHES temprano",
            LocalDateTime.of(2026, 8, 23, 21, 0), "dormir")
    }

    @Test
    fun `pin caps todas las madrugadas ya era diaria`() {
        assertDaily("estirar TODAS LAS MADRUGADAS",
            LocalDateTime.of(2026, 8, 23, 4, 0), "estirar")
    }

    // ---- Pin: rareza heredada de la hermana minúscula («cada por la
    // mañana» deja residuo «cada» y NO es recurrencia en minúscula; la caps
    // debe comportarse igual, ni mejor ni peor). Lateral registrada. ----

    @Test
    fun `pin caps cada por la manana imita la rareza minuscula`() {
        val result = NaturalTaskParser.parse("leer CADA POR LA MAÑANA", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("leer CADA", result.title)
        assertEquals(LocalDateTime.of(2026, 8, 23, 9, 0),
            result.dueAt?.let {
                DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
            })
    }

    // ---- Anti-overreach: fecha única «mañana» NO es recurrencia ----

    @Test
    fun `anti-overreach caps manana fecha unica no es rutina`() {
        val result = NaturalTaskParser.parse("MAÑANA VOY AL MÉDICO", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("VOY AL MÉDICO", result.title)
        assertEquals(LocalDateTime.of(2026, 8, 24, 9, 0),
            result.dueAt?.let {
                DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
            })
    }

    // ---- Pins de hermanas minúsculas (regresión) ----

    @Test
    fun `pin minuscula cada manana sigue diaria`() {
        assertDaily("organizar inbox cada mañana",
            LocalDateTime.of(2026, 8, 23, 9, 0), "organizar inbox")
    }

    @Test
    fun `pin minuscula todas las mananas sigue diaria`() {
        assertDaily("tomar pastillas todas las mañanas",
            LocalDateTime.of(2026, 8, 23, 9, 0), "tomar pastillas")
    }

    @Test
    fun `anti-overreach minuscula manana fecha unica no es rutina`() {
        val result = NaturalTaskParser.parse("mañana voy al médico", now, zone)
        assertEquals(RecurrenceFrequency.NONE, result.recurrence)
        assertEquals("voy al médico", result.title)
        assertEquals(LocalDateTime.of(2026, 8, 24, 9, 0),
            result.dueAt?.let {
                DateRules.toLocalDate(it, zone).atTime(DateRules.toLocalTime(it, zone))
            })
    }
}

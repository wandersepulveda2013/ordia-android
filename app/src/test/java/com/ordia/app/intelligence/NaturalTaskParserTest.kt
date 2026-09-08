package com.ordia.app.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NaturalTaskParserTest {

    @Test
    fun parse_hoy_extractsDateAndRemovesKeyword() {
        val now = getFixedNow()
        val result = NaturalTaskParser.parse("Comprar leche hoy", now)

        assertTrue(result.isTask)
        assertEquals("Comprar leche", result.title)

        val expectedDate = getExpectedDate(now, 0)
        assertEquals(expectedDate, result.dueDate)
    }

    @Test
    fun parse_manana_extractsDateAndRemovesKeyword() {
        val now = getFixedNow()
        val result = NaturalTaskParser.parse("Mañana pagar internet", now)

        assertTrue(result.isTask)
        assertEquals("pagar internet", result.title)

        val expectedDate = getExpectedDate(now, 1)
        assertEquals(expectedDate, result.dueDate)
    }

    @Test
    fun parse_pasadoManana_extractsDateAndRemovesKeyword() {
        val now = getFixedNow()
        val result = NaturalTaskParser.parse("Llamar a Lucas pasado mañana", now)

        assertTrue(result.isTask)
        assertEquals("Llamar a Lucas", result.title)

        val expectedDate = getExpectedDate(now, 2)
        assertEquals(expectedDate, result.dueDate)
    }

    @Test
    fun parse_intentKeyword_noDate_detectsTask() {
        val result = NaturalTaskParser.parse("terminar presentación")

        assertTrue(result.isTask)
        assertEquals("terminar presentación", result.title)
        assertNull(result.dueDate)
    }

    @Test
    fun parse_randomText_noIntent_noDate_detectsNote() {
        val result = NaturalTaskParser.parse("una idea interesante sobre el diseño")

        assertFalse(result.isTask)
        assertEquals("una idea interesante sobre el diseño", result.title)
        assertNull(result.dueDate)
    }

    private fun getFixedNow(): Long {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.AUGUST, 16, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getExpectedDate(now: Long, offsetDays: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}

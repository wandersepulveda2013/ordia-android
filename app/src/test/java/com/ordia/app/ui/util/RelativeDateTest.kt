package com.ordia.app.ui.util

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeDateTest {

    /** 2026-08-31 13:00 hora local: referencia estable para el test. */
    private val now: Date = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 31, 13, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    private fun daysAgo(days: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now.time
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun today_timestamp_isLabeledHoy() {
        assertEquals("Hoy", relativeLabel(daysAgo(0), now = now))
    }

    @Test
    fun yesterday_timestamp_isLabeledAyer() {
        assertEquals("Ayer", relativeLabel(daysAgo(1, 8), now))
    }

    @Test
    fun olderTimestamp_fallsBackToMediumDate() {
        val old = daysAgo(10)
        val expected = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(old))
        assertEquals(expected, relativeLabel(old, now))
    }

    @Test
    fun boundary_exactlyYesterdayMidnight_isAyer() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        assertEquals("Ayer", relativeLabel(cal.timeInMillis, now = now))
    }

    @Test
    fun boundary_exactlyTodayMidnight_isHoy() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("Hoy", relativeLabel(cal.timeInMillis, now = now))
    }
}

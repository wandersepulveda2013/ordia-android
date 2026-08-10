package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusClockTest {

    @Test
    fun format_negativeValue_returnsZero() {
        assertEquals("00:00", FocusClock.format(-5))
        assertEquals("00:00", FocusClock.format(-100))
    }

    @Test
    fun format_zeroValue_returnsZero() {
        assertEquals("00:00", FocusClock.format(0))
    }

    @Test
    fun format_underOneMinute_formatsCorrectly() {
        assertEquals("00:09", FocusClock.format(9))
        assertEquals("00:59", FocusClock.format(59))
    }

    @Test
    fun format_exactMinutes_formatsCorrectly() {
        assertEquals("01:00", FocusClock.format(60))
        assertEquals("10:00", FocusClock.format(600))
    }

    @Test
    fun format_mixedMinutesAndSeconds_formatsCorrectly() {
        assertEquals("01:05", FocusClock.format(65))
        assertEquals("25:30", FocusClock.format(1530))
    }

    @Test
    fun format_longDurations_formatsCorrectly() {
        assertEquals("60:00", FocusClock.format(3600))
        assertEquals("99:59", FocusClock.format(5999))
        assertEquals("100:00", FocusClock.format(6000))
    }
}

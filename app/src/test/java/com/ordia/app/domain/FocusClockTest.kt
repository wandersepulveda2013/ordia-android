package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusClockTest {

    @Test
    fun format_zeroSeconds_returns0000() {
        assertEquals("00:00", FocusClock.format(0))
    }

    @Test
    fun format_lessThanOneMinute_formatsCorrectly() {
        assertEquals("00:05", FocusClock.format(5))
        assertEquals("00:59", FocusClock.format(59))
    }

    @Test
    fun format_moreThanOneMinute_formatsCorrectly() {
        assertEquals("01:00", FocusClock.format(60))
        assertEquals("01:05", FocusClock.format(65))
        assertEquals("15:30", FocusClock.format(930))
    }

    @Test
    fun format_moreThanOneHour_formatsCorrectly() {
        assertEquals("60:00", FocusClock.format(3600))
        assertEquals("65:05", FocusClock.format(3905))
        assertEquals("100:00", FocusClock.format(6000)) // 6000 seconds = 100 mins
    }

    @Test
    fun format_negativeSeconds_coercesToZero() {
        assertEquals("00:00", FocusClock.format(-10))
        assertEquals("00:00", FocusClock.format(-1))
    }
}

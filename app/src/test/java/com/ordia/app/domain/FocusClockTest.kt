package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusClockTest {
    @Test
    fun secondsUnderOneMinutePadded() {
        assertEquals("00:05", FocusClock.format(5))
    }

    @Test
    fun minutesPaddedToTwoDigits() {
        assertEquals("25:00", FocusClock.format(25 * 60))
    }

    @Test
    fun exactlyOneHourRendersAsHours() {
        assertEquals("1:00:00", FocusClock.format(60 * 60))
    }

    @Test
    fun ninetyMinutesRendersAsHoursNotOverflow() {
        // 90 min = 5400 s. Antes: "90:00" (ambiguo: ¿90 min?).
        assertEquals("1:30:00", FocusClock.format(5400))
    }

    @Test
    fun threeHoursMaxPresetRendersAsHoursNotOverflow() {
        // defaultFocusMinutes se clamp a 180; antes "180:00".
        assertEquals("3:00:00", FocusClock.format(180 * 60))
    }

    @Test
    fun negativeNeverRendersNegative() {
        assertEquals("00:00", FocusClock.format(-5))
    }
}

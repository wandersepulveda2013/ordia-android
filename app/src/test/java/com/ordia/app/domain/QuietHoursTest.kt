package com.ordia.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    @Test
    fun overnightWindow_isHandled() {
        assertTrue(QuietHours.contains(23 * 60, 22 * 60, 7 * 60))
        assertTrue(QuietHours.contains(6 * 60 + 30, 22 * 60, 7 * 60))
        assertFalse(QuietHours.contains(12 * 60, 22 * 60, 7 * 60))
    }

    @Test
    fun daytimeWindowAndDisabledWindow_areHandled() {
        assertTrue(QuietHours.contains(13 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(15 * 60, 12 * 60, 14 * 60))
        assertFalse(QuietHours.contains(12 * 60, 12 * 60, 12 * 60))
    }
}

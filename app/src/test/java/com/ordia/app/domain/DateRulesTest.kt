package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateRulesTest {
    @Test fun epochRoundTrip_preservesLocalDateAndTime() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val date = LocalDate.of(2026, 12, 23)
        val time = LocalTime.of(7, 30)
        val epoch = DateRules.toEpochMillis(date, time, zone)
        assertEquals(date, DateRules.toLocalDate(epoch, zone))
        assertEquals(time, DateRules.toLocalTime(epoch, zone))
    }

    @Test fun absoluteMinuteOffset_canContinueOnNextDay() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val date = LocalDate.of(2026, 7, 29)
        val epoch = DateRules.toEpochMillis(date, 24 * 60 + 30, zone)
        assertEquals(date.plusDays(1), DateRules.toLocalDate(epoch, zone))
        assertEquals(LocalTime.of(0, 30), DateRules.toLocalTime(epoch, zone))
    }

    @Test fun minutesToClock_formatsQuietHours() {
        assertEquals("22:00", DateRules.minutesToClock(1320))
        assertEquals("07:05", DateRules.minutesToClock(425))
    }
}

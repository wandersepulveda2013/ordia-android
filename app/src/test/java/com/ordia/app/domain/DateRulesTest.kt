package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Fuente única de verdad de los modificadores de "pasado" que comparten
    // SearchEngine (LAST_WEEK/MONTH_TOKENS) y AssistantEngine (recap + agenda).
    // Este test cristaliza el contrato anti-drift: si alguien cambia una copia
    // sin la otra, la aserción de igualdad lo expone aquí.
    @Test fun pastPeriodModifiers_areCanonicalAndComplete() {
        assertEquals(
            setOf("pasada", "pasadas", "pasado", "pasados", "ultima", "ultimas"),
            DateRules.LAST_WEEK_MODIFIERS,
        )
        assertEquals(
            setOf("pasada", "pasadas", "pasado", "pasados", "ultima", "ultimas", "ultimo", "ultimos"),
            DateRules.LAST_MONTH_MODIFIERS,
        )
    }

    @Test fun lastMonthModifiers_areSupersetOfWeekModifiers() {
        // "mes pasado" admite el masculino singular/plural ("último mes") además
        // de las formas de "semana pasada"; la semana reusa el mismo núcleo.
        assertTrue(DateRules.LAST_WEEK_MODIFIERS.all { it in DateRules.LAST_MONTH_MODIFIERS })
        assertTrue(DateRules.LAST_MONTH_MODIFIERS.size > DateRules.LAST_WEEK_MODIFIERS.size)
    }
}

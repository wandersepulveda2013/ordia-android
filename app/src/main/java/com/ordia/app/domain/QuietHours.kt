package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object QuietHours {
    /** Returns false when start and end are equal, treating that as disabled. */
    fun contains(currentMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val current = currentMinutes.coerceIn(0, 1439)
        val start = startMinutes.coerceIn(0, 1439)
        val end = endMinutes.coerceIn(0, 1439)
        if (start == end) return false
        return if (start < end) current in start until end else current >= start || current < end
    }

    fun nextEndMillis(
        nowMillis: Long,
        startMinutes: Int,
        endMinutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val end = endMinutes.coerceIn(0, 1439)
        val endTime = LocalTime.of(end / 60, end % 60)
        val currentMinutes = now.hour * 60 + now.minute
        val endDate: LocalDate = when {
            startMinutes.coerceIn(0, 1439) < end && currentMinutes < end -> now.toLocalDate()
            startMinutes.coerceIn(0, 1439) > end && currentMinutes < end -> now.toLocalDate()
            else -> now.toLocalDate().plusDays(1)
        }
        return LocalDateTime.of(endDate, endTime).atZone(zoneId).toInstant().toEpochMilli()
    }
}

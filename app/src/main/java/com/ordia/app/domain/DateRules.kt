package com.ordia.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateRules {
    fun toEpochMillis(date: LocalDate, time: LocalTime = LocalTime.of(9, 0), zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()


    /** Converts a date plus an absolute minute offset into epoch milliseconds.
     * Offsets at or beyond 24 hours continue on the following day.
     */
    fun toEpochMillis(date: LocalDate, absoluteMinute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val safeMinute = absoluteMinute.coerceAtLeast(0)
        val dayOffset = safeMinute / (24 * 60)
        val minuteOfDay = safeMinute % (24 * 60)
        return toEpochMillis(
            date.plusDays(dayOffset.toLong()),
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
            zone
        )
    }

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    fun formatDate(epochMillis: Long?, locale: Locale = Locale.getDefault()): String =
        epochMillis?.let {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        } ?: "Sin fecha"

    fun formatTime(epochMillis: Long?, locale: Locale = Locale.getDefault()): String =
        epochMillis?.let {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        } ?: ""

    fun minutesToClock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}

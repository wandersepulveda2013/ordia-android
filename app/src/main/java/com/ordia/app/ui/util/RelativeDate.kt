package com.ordia.app.ui.util

import java.util.Calendar
import java.util.Date

/**
 * Convierte una marca de tiempo [timestampMs] en una etiqueta relativa:
 * "Hoy", "Ayer" o una fecha corta (p. ej. "13 ago 2025") para notas
 * de días anteriores. El límite de "ayer" se calcula contra el día natural local,
 * no contra una ventana de 24 h.
 */
fun relativeLabel(timestampMs: Long, now: Date = Date()): String {
    val cal = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = cal.timeInMillis

    return when {
        timestampMs >= todayStart ->"Hoy"
        timestampMs >= yesterdayStart ->"Ayer"
        else -> {
            val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
            fmt.format(Date(timestampMs))
        }
    }
}
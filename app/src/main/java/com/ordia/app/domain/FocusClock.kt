package com.ordia.app.domain

object FocusClock {
    fun format(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        // Sesiones de enfoque admiten hasta 180 min (PreferencesRepository
        // clamp de defaultFocusMinutes a [5, 180]); con MM:SS, 90 min renderizaba
        // "90:00" y 180 "180:00" (ambiguo, parece roto). A partir de 1 h se usa
        // H:MM:SS para mostrar la duración real sin desbordar el campo de minutos.
        return if (safe >= 3600) {
            val h = safe / 3600
            val m = (safe % 3600) / 60
            val s = safe % 60
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(safe / 60, safe % 60)
        }
    }
}

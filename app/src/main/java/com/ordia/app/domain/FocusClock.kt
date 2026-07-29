package com.ordia.app.domain

object FocusClock {
    fun format(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }
}

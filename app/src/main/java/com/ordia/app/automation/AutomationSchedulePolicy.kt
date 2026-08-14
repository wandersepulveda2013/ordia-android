package com.ordia.app.automation

import com.ordia.app.data.local.AutomationTrigger

/**
 * Política pura que decide, según la hora del día, qué disparador diario conviene
 * activar. Ventana de mañana (5-11) y noche (17-23); el resto del día no activa nada
 * para no molestar.
 */
object AutomationSchedulePolicy {
    fun triggerForHour(hour: Int): AutomationTrigger? = when (hour) {
        in 5..11 -> AutomationTrigger.DAILY_MORNING
        in 17..23 -> AutomationTrigger.DAILY_EVENING
        else -> null
    }
}

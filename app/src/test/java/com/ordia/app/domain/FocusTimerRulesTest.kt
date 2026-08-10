package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusTimerRulesTest {
    @Test
    fun usesAbsoluteClockAfterDelayedTick() {
        assertEquals(7, FocusTimerRules.remainingSeconds(deadlineAt = 10_000L, now = 3_200L))
    }

    @Test
    fun expiredTimerNeverBecomesNegative() {
        assertEquals(0, FocusTimerRules.remainingSeconds(deadlineAt = 1_000L, now = 2_000L))
    }
}

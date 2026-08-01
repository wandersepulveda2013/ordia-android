package com.ordia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun officialVisibleName_hasAccent() {
        assertEquals("Ordía", composeRule.activity.getString(R.string.app_name))
    }

    @Test
    fun firstLaunchOrTodayScreen_isVisible() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Tu mundo, en orden").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("SIGUIENTE PASO").fetchSemanticsNodes().isNotEmpty()
        }
        val onboardingVisible = composeRule.onAllNodesWithText("Tu mundo, en orden").fetchSemanticsNodes().isNotEmpty()
        if (onboardingVisible) {
            composeRule.onNodeWithText("Tu mundo, en orden").assertIsDisplayed()
            composeRule.onNodeWithText("Continuar").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("SIGUIENTE PASO").assertIsDisplayed()
        }
    }
}

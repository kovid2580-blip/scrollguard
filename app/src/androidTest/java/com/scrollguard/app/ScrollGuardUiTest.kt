package com.scrollguard.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.scrollguard.app.blocking.BlockDecision
import com.scrollguard.app.blocking.BlockingOverlayScreen
import com.scrollguard.app.blocking.BlockingReason
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.presentation.theme.ScrollGuardTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScrollGuardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blockingOverlayScreen_displaysStopMessageAndHandlesReturnAction() {
        var returned = false
        val decision = BlockDecision(
            shouldBlock = true,
            packageName = "com.instagram.android",
            appName = "Instagram",
            contentCategory = ContentCategory.INSTAGRAM_REELS,
            reason = BlockingReason.SELECTIVE_CONTENT_RULE,
            confidence = 0.92f,
            messageTitle = "Instagram Reels is blocked.",
            messageBody = "You came here to use Instagram, not to lose another hour scrolling. Take back your time."
        )

        composeRule.setContent {
            ScrollGuardTheme {
                BlockingOverlayScreen(
                    decision = decision,
                    dailyBlockedCount = 4,
                    canTempUnlock = false,
                    remainingUnlocks = 0,
                    onReturnClick = { returned = true },
                    onTempUnlockClick = {}
                )
            }
        }

        composeRule.onNodeWithText("You've been stopped!").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram Reels is blocked.").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked attempts today: 4").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Safe Instagram").performClick()

        assertTrue(returned)
    }
}

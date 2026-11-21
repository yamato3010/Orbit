package com.yamato3010.vrchattools

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

internal fun ComposeTestRule.completeProvisioningIfNeeded() {
    val demoButton = onAllNodesWithText("デモで始める")
    if (demoButton.fetchSemanticsNodes().isNotEmpty()) {
        demoButton[0].performScrollTo().performClick()
    }
}

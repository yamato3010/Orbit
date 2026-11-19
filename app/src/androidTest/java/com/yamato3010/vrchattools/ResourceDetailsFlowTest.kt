package com.yamato3010.vrchattools

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceDetailsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun expand() = compose.onNodeWithText("詳しい情報").performScrollTo().performClick()
    @Test fun friendDetailsStartCollapsedAndCanBeClosedAgain() {
        compose.onNodeWithText("フレンドを見る").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("こはる")
        compose.onNode(hasText("こはる") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("登録日").assertDoesNotExist()
        expand()
        compose.onNodeWithText("登録日").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2022-04-01").assertExists()
        expand()
        compose.onNodeWithText("登録日").assertDoesNotExist()
    }
    @Test fun worldDetailsDisplayAdditionalApiMetrics() {
        compose.onNodeWithText("フレンドのいる場所").performScrollTo()
        compose.onNodeWithText("#789").performScrollTo().performClick()
        compose.onNodeWithText("ワールドの詳細").performScrollTo().performClick()
        compose.onNodeWithText("訪問数").assertDoesNotExist()
        expand()
        compose.onNodeWithText("訪問数").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12840").assertExists()
    }
    @Test fun instanceDetailsDisplayPlatformBreakdown() {
        compose.onNodeWithText("フレンドのいる場所").performScrollTo()
        compose.onNodeWithText("#789").performScrollTo().performClick()
        expand()
        compose.onNodeWithText("プラットフォーム別人数").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PC：7 人\nAndroid：0 人").assertExists()
    }
}

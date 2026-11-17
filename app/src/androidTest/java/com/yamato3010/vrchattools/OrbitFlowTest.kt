package com.yamato3010.vrchattools

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbitFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoNavigationSearchAndDetailFlow() {
        compose.onNodeWithText("さあ、会いにいこう。").assertIsDisplayed()
        compose.onNodeWithText("フレンドを見る").performClick()
        compose.onNodeWithText("8 人のフレンド").assertIsDisplayed()
        compose.onNodeWithText("参加歓迎").performClick()
        compose.onNodeWithText("2 人のフレンド").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("こはる")
        compose.onNodeWithText("1 人のフレンド").assertIsDisplayed()
        compose.onNode(hasText("こはる") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("まったりお話ししよ〜").assertIsDisplayed()
        compose.onNodeWithContentDescription("ワールド詳細").performClick()
        compose.onNodeWithText("デモ用の架空のワールドとイラストです。").assertExists()
    }
}

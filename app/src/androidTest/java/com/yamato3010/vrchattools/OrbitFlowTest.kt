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

    @Test fun homeInstanceOpensDetailsWithAvailableMetadata() {
        compose.onNodeWithText("フレンドのいる場所").performScrollTo()
        compose.onNodeWithText("#789").performScrollTo().performClick()
        compose.onNodeWithText("サンプルのインスタンスです。人数はデモデータです。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ワールドの詳細").assertExists()
        compose.onNodeWithText("再試行").assertDoesNotExist()
    }

    @Test fun instanceViewShowsSharedRoomAndOpensItsDetails() {
        compose.onNodeWithText("フレンドを見る").performClick()
        compose.onNodeWithText("インスタンス別").performClick()
        compose.onNodeWithText("3 インスタンス").assertIsDisplayed()
        compose.onNodeWithText("フレンド 2 人").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("#789").performScrollTo().performClick()
        compose.onNodeWithText("サンプルのインスタンスです。人数はデモデータです。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ワールドの詳細").performScrollTo().performClick()
        compose.onNodeWithText("デモ用の架空のワールドとイラストです。").assertExists()
    }

    @Test fun demoNavigationSearchAndDetailFlow() {
        compose.onNodeWithText("ダッシュボード").assertIsDisplayed()
        compose.onNodeWithText("フレンドを見る").performClick()
        compose.onNodeWithText("8 人のフレンド").assertIsDisplayed()
        compose.onNodeWithText("参加歓迎").performClick()
        compose.onNodeWithText("2 人のフレンド").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("こはる")
        compose.onNodeWithText("1 人のフレンド").assertIsDisplayed()
        compose.onNodeWithText("Trusted").assertIsDisplayed()
        compose.onNodeWithText("she/her").assertIsDisplayed()
        compose.onNode(hasText("こはる") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("まったりお話ししよ〜").assertIsDisplayed()
        compose.onNodeWithContentDescription("ワールド詳細").performClick()
        compose.onNodeWithText("対応プラットフォーム").assertIsDisplayed()
        listOf("Windows", "Android", "iOS").forEach { platform ->
            compose.onNodeWithText(platform).assertIsDisplayed()
        }
        compose.onNodeWithText("デモ用の架空のワールドとイラストです。").assertExists()
    }
}

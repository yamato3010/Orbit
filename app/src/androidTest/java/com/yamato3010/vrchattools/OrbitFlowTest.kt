package com.yamato3010.vrchattools

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbitFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun predictiveBackPreviewsPreviousTabAndCanBeCancelled() {
        compose.onNodeWithText("フレンドを見る").performClick()
        compose.onNodeWithContentDescription("設定").performClick()

        val dispatcher = compose.activity.onBackPressedDispatcher
        val start = BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)
        val halfway = BackEventCompat(60f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)

        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(start)
            dispatcher.dispatchOnBackProgressed(halfway)
        }
        compose.onNodeWithText("8 人のフレンド").assertExists()
        compose.onNodeWithContentDescription("設定を閉じる").assertExists()

        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithText("8 人のフレンド").assertDoesNotExist()
        compose.onNodeWithContentDescription("設定を閉じる").assertExists()

        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(start)
            dispatcher.dispatchOnBackProgressed(halfway)
        }
        compose.onNodeWithText("8 人のフレンド").assertExists()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("8 人のフレンド").assertIsDisplayed()
        compose.onNodeWithContentDescription("設定").assertExists()

        compose.onNodeWithContentDescription("通知").performClick()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("8 人のフレンド").assertIsDisplayed()

        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("ダッシュボード").assertIsDisplayed()
        compose.runOnIdle { check(!dispatcher.hasEnabledCallbacks()) }
    }

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

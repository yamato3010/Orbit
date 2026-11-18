package com.yamato3010.vrchattools

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun demoNotificationsAllowReadingAndConfirmedFriendApproval() {
        compose.onNodeWithContentDescription("通知").performClick()
        compose.onNodeWithText("あなたへのお知らせ").assertIsDisplayed()
        compose.onAllNodesWithText("既読にする")[0].performScrollTo().performClick()
        compose.onNodeWithText("未読 2").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("フレンド申請を承認"))
        compose.onNodeWithText("フレンド申請を承認").performClick()
        compose.onNodeWithText("承認する").assertIsDisplayed().performClick()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText("未読 1").assertExists()
    }
    @Test fun statusPresetNeedsExplicitApply() {
        compose.onNodeWithContentDescription("自分のステータス").performClick()
        compose.onNodeWithText("集中したい").performScrollTo().performClick()
        compose.onNodeWithText("VRChatに反映する（デモ）").performScrollTo().performClick()
        compose.onNodeWithText("旅するあなた · 現在：取り込み中").assertExists()
    }
    @Test fun invitePreviewCanBeCancelledWithoutSending() {
        compose.onNodeWithContentDescription("通知").performClick()
        compose.onNodeWithText("現在地へ招待する").performScrollTo().performClick()
        compose.onNodeWithText("宛先：しろ").assertIsDisplayed()
        compose.onNodeWithText("招待先：demo-world-3:789").assertIsDisplayed()
        compose.onNodeWithText("キャンセル").performClick()
        compose.onNodeWithText("あなたへのお知らせ").assertExists()
        compose.onNodeWithText("未読 3").assertExists()
    }
}

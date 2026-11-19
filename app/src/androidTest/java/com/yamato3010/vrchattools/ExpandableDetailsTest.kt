package com.yamato3010.vrchattools

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.DetailField
import com.yamato3010.vrchattools.ui.ExpandableDetails
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

@RunWith(AndroidJUnit4::class)
class ExpandableDetailsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fetchesOnlyOnExpansionRetriesFailureAndRetainsSuccessfulResult() {
        var calls = 0
        compose.setContent { MaterialTheme {
            ExpandableDetails("test", listOf(DetailField("ワールドID", "wrld_test")), false) {
                calls++
                if (calls == 1) throw IOException("test failure")
                listOf(DetailField("訪問数", "123"))
            }
        } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("詳しい情報").performClick()
        compose.onNodeWithText("追加情報を再試行").assertIsDisplayed().performClick()
        compose.onNodeWithText("123").assertIsDisplayed()
        compose.onNodeWithText("詳しい情報").performClick()
        compose.onNodeWithText("訪問数").assertDoesNotExist()
        compose.onNodeWithText("詳しい情報").performClick()
        compose.onNodeWithText("123").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, calls) }
    }
    @Test fun closingDuringFetchDoesNotStartAnotherRequestOnReopening() {
        var calls = 0
        val result = CompletableDeferred<List<DetailField>>()
        compose.setContent { MaterialTheme {
            ExpandableDetails("pending", emptyList(), false) { calls++; result.await() }
        } }
        compose.onNodeWithText("詳しい情報").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("詳しい情報").performClick()
        compose.onNodeWithText("詳しい情報").performClick()
        compose.runOnIdle { assertEquals(1, calls); result.complete(listOf(DetailField("訪問数", "456"))) }
        compose.onNodeWithText("456").assertIsDisplayed()
    }

}

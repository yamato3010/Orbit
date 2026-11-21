package com.yamato3010.vrchattools

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.OrbitViewModel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun openDemo() = compose.completeProvisioningIfNeeded()

    @Test fun dynamicColorsCanBeToggledAndRestoredAcrossActivityRecreation() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val model = ViewModelProvider(compose.activity)[OrbitViewModel::class.java]
        val initialDynamic = model.dynamicColor
        val initialTheme = model.themeMode
        try {
            compose.onNodeWithContentDescription("設定").performClick()
            val toggle = compose.onNodeWithTag("dynamic_color_switch")
            toggle.performScrollTo().performClick()
            if (initialDynamic) compose.onNodeWithTag("dynamic_color_switch").assertIsOff()
            else compose.onNodeWithTag("dynamic_color_switch").assertIsOn()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("dynamic_color_switch").performScrollTo().let {
                if (initialDynamic) it.assertIsOff() else it.assertIsOn()
            }
            compose.onNodeWithText("ダーク").performScrollTo().performClick()
            compose.onNodeWithText("ダーク").assertIsSelected()
            compose.onNodeWithTag("dynamic_color_switch").performScrollTo().performClick()
            if (initialDynamic) compose.onNodeWithTag("dynamic_color_switch").assertIsOn()
            else compose.onNodeWithTag("dynamic_color_switch").assertIsOff()
            compose.onNodeWithText("現在の配色").performScrollTo().assertIsDisplayed()
        } finally {
            compose.runOnIdle { model.setTheme(initialTheme); model.setDynamic(initialDynamic) }
        }
    }
}

package com.yamato3010.vrchattools

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamato3010.vrchattools.data.OrbitViewModel
import com.yamato3010.vrchattools.ui.OrbitApp
import com.yamato3010.vrchattools.ui.theme.VrchattoolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: OrbitViewModel = viewModel()
            val dark = when (model.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            VrchattoolsTheme(darkTheme = dark, dynamicColor = model.dynamicColor) {
                OrbitApp(model)
            }
        }
    }
}

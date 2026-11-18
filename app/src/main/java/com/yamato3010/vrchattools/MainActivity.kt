package com.yamato3010.vrchattools

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.ViewModelProvider
import com.yamato3010.vrchattools.data.OrbitViewModel
import com.yamato3010.vrchattools.ui.OrbitApp
import com.yamato3010.vrchattools.ui.LocalOrbitImageLoader
import com.yamato3010.vrchattools.ui.rememberOrbitAppState
import com.yamato3010.vrchattools.ui.theme.VrchattoolsTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: OrbitViewModel
    override fun onStart() { super.onStart(); model.setForeground(true) }
    override fun onStop() { model.setForeground(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[OrbitViewModel::class.java]
        setContent {
            val appState = rememberOrbitAppState()
            val dark = when (model.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            VrchattoolsTheme(darkTheme = dark, dynamicColor = model.dynamicColor) {
                CompositionLocalProvider(LocalOrbitImageLoader provides model.imageLoader) {
                    OrbitApp(model, appState)
                }
            }
        }
    }
}

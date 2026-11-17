package com.yamato3010.vrchattools.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF65548F), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DEFF), onPrimaryContainer = Color(0xFF30204F),
    secondary = Color(0xFF526344), secondaryContainer = Color(0xFFDDEFBE), onSecondaryContainer = Color(0xFF263717),
    tertiary = Color(0xFF87536A), tertiaryContainer = Color(0xFFFFD9E5), onTertiaryContainer = Color(0xFF3B1729),
    background = Color(0xFFFCF9FF), surface = Color(0xFFFCF9FF), onBackground = Color(0xFF22202A), onSurface = Color(0xFF22202A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F0FA),
    surfaceContainer = Color(0xFFF0EBF5), surfaceContainerHigh = Color(0xFFEAE5F0),
    surfaceContainerHighest = Color(0xFFE4DFE9), onSurfaceVariant = Color(0xFF777080), outlineVariant = Color(0xFFDFD8E7)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFE), onPrimary = Color(0xFF352357),
    primaryContainer = Color(0xFF4C3C6A), onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFBED39F), secondaryContainer = Color(0xFF39482B), onSecondaryContainer = Color(0xFFDDEFBE),
    tertiary = Color(0xFFF0B4CD), tertiaryContainer = Color(0xFF633C50),
    background = Color(0xFF15131A), surface = Color(0xFF15131A), onBackground = Color(0xFFE8E1ED), onSurface = Color(0xFFE8E1ED),
    surfaceContainerLowest = Color(0xFF100E15), surfaceContainerLow = Color(0xFF1F1C25),
    surfaceContainer = Color(0xFF27232D), surfaceContainerHigh = Color(0xFF302C36),
    surfaceContainerHighest = Color(0xFF3B3642), onSurfaceVariant = Color(0xFFC9BFCE), outlineVariant = Color(0xFF49424F)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VrchattoolsTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(colorScheme = colors, typography = Typography, content = content)
}

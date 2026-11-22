package com.example.lr_1.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = Sage80,
    tertiary = Amber80,
    background = AppDarkBackground,
    surface = AppDarkSurface,
    surfaceVariant = AppDarkSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = Sage40,
    tertiary = Amber40,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceVariant,
)

@Composable
fun LR_1Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

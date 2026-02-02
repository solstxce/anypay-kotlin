package com.idk.anypay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material3 Expressive Dark Color Scheme
 * Enhanced with expressive colors and refined tones
 */
private val DarkColorScheme = darkColorScheme(
    primary = Blue600,
    onPrimary = White,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue50,
    secondary = Blue500,
    onSecondary = White,
    secondaryContainer = Blue600,
    onSecondaryContainer = Blue50,
    tertiary = BalanceBlue,
    onTertiary = White,
    tertiaryContainer = Blue700,
    onTertiaryContainer = Blue50,
    background = Gray900,
    onBackground = White,
    surface = Gray800,
    onSurface = White,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray200,
    surfaceContainerHighest = Gray700,
    surfaceContainerHigh = Gray750,
    surfaceContainer = Gray800,
    surfaceContainerLow = Gray850,
    surfaceContainerLowest = Gray900,
    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRed.copy(alpha = 0.2f),
    onErrorContainer = ErrorRed
)

/**
 * Material3 Expressive Light Color Scheme
 * Enhanced with expressive colors and refined tones
 */
private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue700,
    secondary = Blue600,
    onSecondary = White,
    secondaryContainer = Blue50,
    onSecondaryContainer = Blue700,
    tertiary = BalanceBlue,
    onTertiary = White,
    tertiaryContainer = Blue50,
    onTertiaryContainer = Blue700,
    background = Gray50,
    onBackground = Gray900,
    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    surfaceContainerHighest = Gray200,
    surfaceContainerHigh = Gray100,
    surfaceContainer = Gray50,
    surfaceContainerLow = White,
    surfaceContainerLowest = White,
    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRed.copy(alpha = 0.1f),
    onErrorContainer = ErrorRed
)

/**
 * Material3 Expressive Theme with Dynamic Color support
 * 
 * @param darkTheme Whether to use dark theme colors
 * @param dynamicColor Whether to use dynamic color from Android 12+ (Material You)
 * @param content The composable content
 */
@Composable
fun AnyPayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enable dynamic color by default for Material You
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Use dynamic color on Android 12+ if enabled
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Use custom dark/light schemes
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
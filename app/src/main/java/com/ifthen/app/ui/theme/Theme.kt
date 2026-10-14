package com.ifthen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Pine,
    onPrimary = OnPine,
    primaryContainer = PineContainer,
    onPrimaryContainer = OnPineContainer,
    secondary = Amber,
    onSecondary = OnPine,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = Rust,
    onTertiary = OnPine,
    tertiaryContainer = RustContainer,
    onTertiaryContainer = OnRustContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = PineSurface,
    onSurfaceVariant = VariantLight,
    outline = OutlineLight
)

private val DarkScheme = darkColorScheme(
    primary = PineDark,
    onPrimary = Color(0xFF000000),
    primaryContainer = PineContainerDark,
    onPrimaryContainer = PineDark,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiaryContainer = RustContainer,
    onTertiaryContainer = OnRustContainer,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF1E2A25),
    onSurfaceVariant = VariantDark,
    outline = OutlineDark
)

@Composable
fun IfThenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}

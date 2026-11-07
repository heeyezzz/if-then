package com.ifthen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Klein,
    onPrimary = OnKlein,
    primaryContainer = KleinContainer,
    onPrimaryContainer = OnKleinContainer,
    secondary = Gold,
    onSecondary = OnKlein,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = Rust,
    onTertiary = OnKlein,
    tertiaryContainer = RustContainer,
    onTertiaryContainer = OnRustContainer,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = VariantLight,
    outline = OutlineLight
)

private val DarkScheme = darkColorScheme(
    primary = KleinDark,
    onPrimary = Color(0xFF071764),
    primaryContainer = KleinContainerDark,
    onPrimaryContainer = KleinContainer,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiaryContainer = RustContainer,
    onTertiaryContainer = OnRustContainer,
    background = DarkBg,
    onBackground = OnDark,
    surface = DarkBg,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariantDark,
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

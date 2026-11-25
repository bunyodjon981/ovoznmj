package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CosmicDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF003B47),
    onPrimaryContainer = Color(0xFFB3F7FF),
    secondary = SapphireCore,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A2859),
    onSecondaryContainer = Color(0xFFD8E0FF),
    tertiary = NeuralViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF331D66),
    onTertiaryContainer = Color(0xFFE7DAFF),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianSurfaceBorder,
    outlineVariant = Color(0xFF1B253B),
    error = CoralError,
    onError = Color.White
)

private val CosmicLightColorScheme = lightColorScheme(
    primary = Color(0xFF006D7E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5F4FF),
    onPrimaryContainer = Color(0xFF00242B),
    secondary = SapphireCore,
    onSecondary = Color.White,
    tertiary = NeuralViolet,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = CoralError,
    onError = Color.White
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun TelefonimTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CosmicDarkColorScheme else CosmicLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

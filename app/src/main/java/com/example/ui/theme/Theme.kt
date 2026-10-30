package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MeshDropColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = NeonCyan,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B1D7C),
    onSecondaryContainer = Color(0xFFE2D6FF),
    tertiary = MintEmerald,
    onTertiary = Color.Black,
    background = DeepVoid,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = TextSecondary,
    outline = OutlineColor,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MeshDropTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MeshDropColorScheme,
        typography = Typography,
        content = content
    )
}

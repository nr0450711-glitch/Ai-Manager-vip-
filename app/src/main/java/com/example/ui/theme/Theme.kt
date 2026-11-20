package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = VoidNavy,
    primaryContainer = DeepAzure,
    onPrimaryContainer = PureIce,
    secondary = ArcBlue,
    onSecondary = PureIce,
    secondaryContainer = ElevatedNavy,
    onSecondaryContainer = PureIce,
    tertiary = NeonTeal,
    onTertiary = VoidNavy,
    background = VoidNavy,
    onBackground = PureIce,
    surface = DeepNavy,
    onSurface = PureIce,
    surfaceVariant = PanelNavy,
    onSurfaceVariant = MutedSteel,
    surfaceContainerLow = DeepNavy,
    surfaceContainer = PanelNavy,
    surfaceContainerHigh = ElevatedNavy,
    outline = GlassBorder,
    outlineVariant = Color(0x2900D2FF),
    error = StatusCrimson,
    onError = VoidNavy
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisVipTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}

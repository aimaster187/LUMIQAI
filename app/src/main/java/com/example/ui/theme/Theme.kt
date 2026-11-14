package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PearlLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = CyanSoft,
    onPrimaryContainer = ElectricBlueDeep,
    secondary = NeuralViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7ECFF),
    onSecondaryContainer = InkPrimary,
    tertiary = CyanGlow,
    onTertiary = InkPrimary,
    background = PearlWhiteBg,
    onBackground = InkPrimary,
    surface = GlassWhite,
    onSurface = InkPrimary,
    surfaceVariant = PearlSurfaceBottom,
    onSurfaceVariant = InkSecondary,
    outline = ChromeSilverBorder,
    outlineVariant = Color(0xFFE2EBF7),
    error = CrimsonError,
    onError = Color.White
)

private val ObsidianDarkColorScheme = darkColorScheme(
    primary = CyanGlow,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF103057),
    onPrimaryContainer = Color(0xFFCDE9FF),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = ObsidianSurfaceElevated,
    onSecondaryContainer = ObsidianTextPrimary,
    tertiary = NeuralViolet,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1D2E4B),
    error = CrimsonError,
    onError = Color.White
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun LumiqTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ObsidianDarkColorScheme else PearlLightColorScheme
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumiqTypography,
            content = content
        )
    }
}

@Composable
fun LumiqBackgroundCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val gradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF070D18), Color(0xFF0D192E), Color(0xFF091222))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(PearlSurfaceTop, PearlWhiteBg, PearlSurfaceBottom)
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient),
        content = content
    )
}

@Composable
fun LumiqGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val surfaceColor = if (isDark) ObsidianSurface.copy(alpha = 0.90f) else GlassWhite
    val borderBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF2D4872), Color(0xFF192B47)))
    } else {
        Brush.linearGradient(listOf(ChromeHighlight, ChromeSilverBorder))
    }
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.55f) else SoftShadowBlue.copy(alpha = 0.45f)

    val baseModifier = modifier
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = shadowColor,
            spotColor = shadowColor
        )
        .clip(shape)
        .background(surfaceColor)
        .border(BorderStroke(1.2.dp, borderBrush), shape)
        .let { mod ->
            if (onClick != null) mod.clickable(role = Role.Button, onClick = onClick) else mod
        }

    Box(
        modifier = baseModifier.padding(contentPadding),
        content = content
    )
}

@Composable
fun LumiqCircleIconSurface(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val bgColor = if (isDark) ObsidianSurfaceElevated else GlassWhite
    val borderColor = if (isDark) ObsidianBorder else ChromeSilverBorder.copy(alpha = 0.7f)
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.5f) else SoftShadowBlue.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(CircleShape)
            .let { mod ->
                if (onClick != null) mod.clickable(role = Role.Button, onClick = onClick) else mod
            },
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

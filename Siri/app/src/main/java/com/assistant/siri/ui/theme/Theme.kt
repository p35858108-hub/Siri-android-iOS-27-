package com.assistant.siri.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colores de la cápsula (los mismos en modo claro y oscuro). */
object CapsuleColors {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0B0B0D)
    val Reflection = Color(0x664A6CF7)

    /** Reflejos de color muy tenues del borde superior (azul, violeta y cálido), como en la referencia. */
    val GlintBlue = Color(0xFF5B7CFF)
    val GlintViolet = Color(0xFFB06CFF)
    val GlintWarm = Color(0xFFFF8A5C)
    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFFA6A6B0)
    val Accent = Color(0xFF7B93FF)
    val Error = Color(0xFFFF6369)

    /** Capa sobre el contenido desenfocado. */
    val Scrim = Color(0x59000000)

    /** Capa más opaca para versiones sin desenfoque (antes de Android 12). */
    val ScrimFallback = Color(0xB3000000)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6CF7),
    onPrimary = Color.White,
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF111114),
    surface = Color.White,
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFECECF1),
    onSurfaceVariant = Color(0xFF5E5E68),
    outline = Color(0xFFD5D5DD),
    error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7B93FF),
    onPrimary = Color(0xFF0B0B0D),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF141417),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1D1D22),
    onSurfaceVariant = Color(0xFFA6A6B0),
    outline = Color(0xFF2E2E35),
    error = Color(0xFFFF6369)
)

/** Tipografía: clara, con jerarquía marcada y interlineado generoso para leer bien. */
val AssistantTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun AssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AssistantTypography,
        content = content
    )
}

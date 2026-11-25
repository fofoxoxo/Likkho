package com.likkho.diary.nativeapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wikipedia Editorial Design Tokens for Likkho Native UI.
 *
 * Parity with `src/index.css`:
 * - Light: `#FFFFFF` bg, `#F8F9FA` surface, `#A2A9B1` border, `#EAECF0` hairline, `#202122` text, `#54595D` muted.
 * - Dark: `#101418` bg, `#1A1F24` surface, `#3A4047` border, `#272D34` hairline, `#EAECF0` text, `#9AA0A6` muted.
 * - Accents: `#3366CC` Wikipedia Blue, `#B32424` Crimson, `#14866D` Emerald.
 * - Crisp square rectangular borders (`RoundedCornerShape(0.dp)`).
 */
@Immutable
data class LikkhoColors(
    val bg: Color,
    val surface: Color,
    val border: Color,
    val hairline: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentHover: Color,
    val link: Color,
    val crimson: Color,
    val crimsonHover: Color,
    val emerald: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightLikkhoColors = LikkhoColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF8F9FA),
    border = Color(0xFFA2A9B1),
    hairline = Color(0xFFEAECF0),
    text = Color(0xFF202122),
    muted = Color(0xFF54595D),
    accent = Color(0xFF3366CC),
    accentHover = Color(0xFF2A56B0),
    link = Color(0xFF3366CC),
    crimson = Color(0xFFB32424),
    crimsonHover = Color(0xFF941D1D),
    emerald = Color(0xFF14866D),
    scrim = Color(0xB3000000),
    isDark = false
)

val DarkLikkhoColors = LikkhoColors(
    bg = Color(0xFF101418),
    surface = Color(0xFF1A1F24),
    border = Color(0xFF3A4047),
    hairline = Color(0xFF272D34),
    text = Color(0xFFEAECF0),
    muted = Color(0xFF9AA0A6),
    accent = Color(0xFF3366CC),
    accentHover = Color(0xFF2A56B0),
    link = Color(0xFF6699FF),
    crimson = Color(0xFFB32424),
    crimsonHover = Color(0xFF941D1D),
    emerald = Color(0xFF14866D),
    scrim = Color(0xCC000000),
    isDark = true
)

val LocalLikkhoColors = staticCompositionLocalOf { LightLikkhoColors }

object LikkhoFontFamilies {
    val Serif: FontFamily = FontFamily.Serif
    val Prose: FontFamily = FontFamily.Serif
    val Sans: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

val LikkhoShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

val LikkhoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = LikkhoFontFamilies.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = LikkhoFontFamilies.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = LikkhoFontFamilies.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = LikkhoFontFamilies.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = LikkhoFontFamilies.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = LikkhoFontFamilies.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = LikkhoFontFamilies.Prose,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = LikkhoFontFamilies.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = LikkhoFontFamilies.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LikkhoFontFamilies.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = LikkhoFontFamilies.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = LikkhoFontFamilies.Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum"
    )
)

object LikkhoTheme {
    val colors: LikkhoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLikkhoColors.current
}

@Composable
fun LikkhoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkLikkhoColors else LightLikkhoColors
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.link,
            onSecondary = Color.White,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.hairline,
            error = palette.crimson,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.link,
            onSecondary = Color.White,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.hairline,
            error = palette.crimson,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalLikkhoColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            shapes = LikkhoShapes,
            typography = LikkhoTypography,
            content = content
        )
    }
}

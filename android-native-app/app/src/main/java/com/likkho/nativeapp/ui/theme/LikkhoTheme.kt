package com.likkho.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Wikipedia Clean Color Palette
val WikiBgLight = Color(0xFFFFFFFF)
val WikiSurfaceLight = Color(0xFFF8F9FA)
val WikiBorderLight = Color(0xFFA2A9B1)
val WikiTextLight = Color(0xFF202122)
val WikiMutedLight = Color(0xFF72777D)
val WikiHairlineLight = Color(0xFFEAECF0)

val WikiBgDark = Color(0xFF101418)
val WikiSurfaceDark = Color(0xFF1A1F24)
val WikiBorderDark = Color(0xFF54595D)
val WikiTextDark = Color(0xFFEAECF0)
val WikiMutedDark = Color(0xFFA2A9B1)
val WikiHairlineDark = Color(0xFF2E343A)

val WikiBlue = Color(0xFF3366CC)
val WikiBlueHover = Color(0xFF2A56B0)
val WikiRed = Color(0xFFB32424)
val WikiGreen = Color(0xFF14866D)
val WikiAmber = Color(0xFFAC6600)

// Aliases matching all screen references
val WikipediaBlue = WikiBlue
val WikipediaRed = WikiRed
val WikipediaGreen = WikiGreen
val WikipediaAmber = WikiAmber

// Wikipedia Serif Typography
val WikiSerif = FontFamily.Serif
val WikiMono = FontFamily.Monospace

val WikiTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = WikiSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = WikiSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WikiSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WikiSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = WikiSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WikiMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val LightColorScheme = lightColorScheme(
    primary = WikiBlue,
    onPrimary = Color.White,
    background = WikiBgLight,
    onBackground = WikiTextLight,
    surface = WikiSurfaceLight,
    onSurface = WikiTextLight,
    outline = WikiBorderLight,
    outlineVariant = WikiHairlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = WikiBlue,
    onPrimary = Color.White,
    background = WikiBgDark,
    onBackground = WikiTextDark,
    surface = WikiSurfaceDark,
    onSurface = WikiTextDark,
    outline = WikiBorderDark,
    outlineVariant = WikiHairlineDark
)

@Composable
fun LikkhoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = WikiTypography,
        content = content
    )
}

@Composable
fun LikkhoNativeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    LikkhoTheme(darkTheme = darkTheme, content = content)
}

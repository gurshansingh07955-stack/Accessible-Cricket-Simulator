package com.cricketsim.ui.theme

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

/**
 * The app's look: quiet, warm and high-contrast.
 *
 * PALETTE. A warm off-white "paper" background and a deep pitch green for
 * the primary colour, with a muted cricket-ball red kept for one small
 * decorative touch. There is a full dark scheme that follows the system
 * setting. Every text pairing was chosen to clear a comfortable margin
 * above WCAG AA rather than just scrape it, because a large part of this
 * app's audience has low vision rather than no vision:
 *   light: body text on paper ~ 15:1, secondary text ~ 7:1, the green on
 *          paper ~ 7:1, white on the green ~ 8:1, outlines ~ 3.6:1
 *          (the 3:1 that borders and controls need).
 *   dark:  the same relationships, inverted.
 * Colour is never the only carrier of meaning anywhere in the app.
 *
 * TYPE. Headings use the platform's serif (no font files to ship) for a
 * calmer, more editorial feel; body text is the system sans at a slightly
 * larger size and looser line height than Material's default, because long
 * passages (the About page, the scorecard) are read a lot. All sizes are in
 * `sp`, so they scale with the user's system font size.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5C3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBDD),
    onPrimaryContainer = Color(0xFF0E2E1B),
    secondary = Color(0xFF4A6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EBE4),
    onSecondaryContainer = Color(0xFF16261D),
    tertiary = Color(0xFF9E2B25),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF1B1F1C),
    surface = Color(0xFFFAF8F3),
    onSurface = Color(0xFF1B1F1C),
    surfaceVariant = Color(0xFFE9EEE8),
    onSurfaceVariant = Color(0xFF4A524C),
    outline = Color(0xFF7A857D),
    outlineVariant = Color(0xFFCBD2CA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6AB),
    onPrimary = Color(0xFF0B2517),
    primaryContainer = Color(0xFF1E4A31),
    onPrimaryContainer = Color(0xFFD5EFDD),
    secondary = Color(0xFFB2CCBA),
    onSecondary = Color(0xFF1D3326),
    secondaryContainer = Color(0xFF2E4A39),
    onSecondaryContainer = Color(0xFFD5EFDD),
    tertiary = Color(0xFFE8837C),
    onTertiary = Color(0xFF4A0F0B),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE6EEE8),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFE6EEE8),
    surfaceVariant = Color(0xFF232E27),
    onSurfaceVariant = Color(0xFFB9C5BD),
    outline = Color(0xFF7F8C84),
    outlineVariant = Color(0xFF3A4A40),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val CricketTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

@Composable
fun CricketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = CricketTypography,
        content = content
    )
}

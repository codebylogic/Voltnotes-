package com.shivam.vaultnotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class VaultEditorialPalette(
    val backgroundCanvas: Color,
    val cardSurface: Color,
    val softTintedSurface: Color,
    val primaryBrandAccent: Color,
    val onPrimaryBrandAccent: Color,
    val activeToolIndigo: Color,
    val folderBadgeBg: Color,
    val folderBadgeIcon: Color,
    val noteBadgeBg: Color,
    val noteBadgeIcon: Color,
    val dangerRed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val hairlineBorder: Color,
    val isDark: Boolean
)

val LightVaultPalette = VaultEditorialPalette(
    backgroundCanvas = Color(0xFFFBFBF9),
    cardSurface = Color(0xFFFFFFFF),
    softTintedSurface = Color(0xFFF3F2EE),
    primaryBrandAccent = Color(0xFF1E293B),
    onPrimaryBrandAccent = Color(0xFFFFFFFF),
    activeToolIndigo = Color(0xFF4F46E5),
    folderBadgeBg = Color(0xFFFEF3C7),
    folderBadgeIcon = Color(0xFFD97706),
    noteBadgeBg = Color(0xFFEEF2FF),
    noteBadgeIcon = Color(0xFF4F46E5),
    dangerRed = Color(0xFFDC2626),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    hairlineBorder = Color(0xFFE2E8F0),
    isDark = false
)

val DarkVaultPalette = VaultEditorialPalette(
    backgroundCanvas = Color(0xFF0D0E11),
    cardSurface = Color(0xFF16181D),
    softTintedSurface = Color(0xFF1E2028),
    primaryBrandAccent = Color(0xFFF8FAFC),
    onPrimaryBrandAccent = Color(0xFF0F172A),
    activeToolIndigo = Color(0xFF818CF8),
    folderBadgeBg = Color(0xFF292112),
    folderBadgeIcon = Color(0xFFFBBF24),
    noteBadgeBg = Color(0xFF1E2235),
    noteBadgeIcon = Color(0xFF818CF8),
    dangerRed = Color(0xFFF87171),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    hairlineBorder = Color(0xFF262932),
    isDark = true
)

val LocalVaultPalette = staticCompositionLocalOf { LightVaultPalette }

private val LightM3ColorScheme = lightColorScheme(
    primary = LightVaultPalette.primaryBrandAccent,
    onPrimary = LightVaultPalette.onPrimaryBrandAccent,
    secondary = LightVaultPalette.activeToolIndigo,
    onSecondary = Color.White,
    background = LightVaultPalette.backgroundCanvas,
    onBackground = LightVaultPalette.textPrimary,
    surface = LightVaultPalette.cardSurface,
    onSurface = LightVaultPalette.textPrimary,
    surfaceVariant = LightVaultPalette.softTintedSurface,
    onSurfaceVariant = LightVaultPalette.textSecondary,
    outline = LightVaultPalette.hairlineBorder,
    error = LightVaultPalette.dangerRed,
    onError = Color.White
)

private val DarkM3ColorScheme = darkColorScheme(
    primary = DarkVaultPalette.primaryBrandAccent,
    onPrimary = DarkVaultPalette.onPrimaryBrandAccent,
    secondary = DarkVaultPalette.activeToolIndigo,
    onSecondary = Color(0xFF0F172A),
    background = DarkVaultPalette.backgroundCanvas,
    onBackground = DarkVaultPalette.textPrimary,
    surface = DarkVaultPalette.cardSurface,
    onSurface = DarkVaultPalette.textPrimary,
    surfaceVariant = DarkVaultPalette.softTintedSurface,
    onSurfaceVariant = DarkVaultPalette.textSecondary,
    outline = DarkVaultPalette.hairlineBorder,
    error = DarkVaultPalette.dangerRed,
    onError = Color(0xFF0F172A)
)

val VaultNotesTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun VaultNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val vaultPalette = if (darkTheme) DarkVaultPalette else LightVaultPalette
    val colorScheme = if (darkTheme) DarkM3ColorScheme else LightM3ColorScheme

    CompositionLocalProvider(LocalVaultPalette provides vaultPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VaultNotesTypography,
            content = content
        )
    }
}

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.ThemeMode

@Immutable
data class UnknownExtendedColors(
    val diffAddBg: Color,
    val diffAddText: Color,
    val diffAddAccent: Color,
    val diffRemoveBg: Color,
    val diffRemoveText: Color,
    val diffRemoveAccent: Color,
    val codeSurface: Color,
    val codeHeader: Color,
    val userBubble: Color,
    val userBubbleBorder: Color,
    val isDark: Boolean
)

val LocalUnknownColors = staticCompositionLocalOf {
    UnknownExtendedColors(
        diffAddBg = DiffAddBgLight,
        diffAddText = DiffAddTextLight,
        diffAddAccent = DiffAddAccentLight,
        diffRemoveBg = DiffRemoveBgLight,
        diffRemoveText = DiffRemoveTextLight,
        diffRemoveAccent = DiffRemoveAccentLight,
        codeSurface = Color(0xFFF8FAFC),
        codeHeader = Color(0xFFEEF2F6),
        userBubble = Color(0xFFEFF6FF),
        userBubbleBorder = Color(0xFFBFDBFE),
        isDark = false
    )
}

private val DaylightColorScheme = lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CobaltPrimaryContainerLight,
    onPrimaryContainer = CobaltOnPrimaryContainerLight,
    secondary = CobaltSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CobaltSecondaryContainerLight,
    onSecondaryContainer = DaylightInk,
    tertiary = CobaltTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = CobaltTertiaryContainerLight,
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = DaylightBackground,
    onBackground = DaylightInk,
    surface = DaylightSurface,
    onSurface = DaylightInk,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightSlate,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightSurfaceContainerLow,
    surfaceContainer = DaylightSurfaceContainer,
    surfaceContainerHigh = DaylightSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = DaylightOutline,
    outlineVariant = DaylightOutlineVariant,
    error = SemanticErrorLight,
    onError = Color.White,
    errorContainer = SemanticErrorContainerLight,
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = CobaltOnPrimaryDark,
    primaryContainer = CobaltPrimaryContainerDark,
    onPrimaryContainer = CobaltOnPrimaryContainerDark,
    secondary = CobaltSecondaryDark,
    onSecondary = DarkBackground,
    secondaryContainer = CobaltSecondaryContainerDark,
    onSecondaryContainer = DarkInk,
    tertiary = CobaltTertiaryDark,
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = CobaltTertiaryContainerDark,
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSlate,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF334155),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = SemanticErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = SemanticErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

val UnknownShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun UnknownAiTheme(
    themeMode: ThemeMode = ThemeMode.DAYLIGHT,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DAYLIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) DarkColorScheme else DaylightColorScheme
    val extendedColors = if (isDark) {
        UnknownExtendedColors(
            diffAddBg = DiffAddBgDark,
            diffAddText = DiffAddTextDark,
            diffAddAccent = DiffAddAccentDark,
            diffRemoveBg = DiffRemoveBgDark,
            diffRemoveText = DiffRemoveTextDark,
            diffRemoveAccent = DiffRemoveAccentDark,
            codeSurface = Color(0xFF0D131F),
            codeHeader = Color(0xFF161E2E),
            userBubble = Color(0xFF172554),
            userBubbleBorder = Color(0xFF1E40AF),
            isDark = true
        )
    } else {
        UnknownExtendedColors(
            diffAddBg = DiffAddBgLight,
            diffAddText = DiffAddTextLight,
            diffAddAccent = DiffAddAccentLight,
            diffRemoveBg = DiffRemoveBgLight,
            diffRemoveText = DiffRemoveTextLight,
            diffRemoveAccent = DiffRemoveAccentLight,
            codeSurface = Color(0xFFF8FAFC),
            codeHeader = Color(0xFFEEF2F6),
            userBubble = Color(0xFFEFF6FF),
            userBubbleBorder = Color(0xFFBFDBFE),
            isDark = false
        )
    }

    CompositionLocalProvider(LocalUnknownColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = UnknownShapes,
            content = content
        )
    }
}

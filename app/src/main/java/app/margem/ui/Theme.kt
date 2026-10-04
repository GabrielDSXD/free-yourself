package app.margem.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.margem.Margem
import app.margem.R
import app.margem.data.ThemeMode

private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val Type = Typography(
    displayLarge = style(56, 60, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
    headlineMedium = style(28, 34, FontWeight.SemiBold),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2A6F6A), onPrimary = Color.White,
    secondary = Color(0xFF3D4590), onSecondary = Color.White,
    tertiary = Color(0xFF8A5A12), onTertiary = Color.White,
    background = Color(0xFFEEF1F4), onBackground = Color(0xFF1E2A3A),
    surface = Color(0xFFEEF1F4), onSurface = Color(0xFF1E2A3A),
    surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFDDE3EA), onSurfaceVariant = Color(0xFF5B6B7F),
    secondaryContainer = Color(0xFFDDE3EA), onSecondaryContainer = Color(0xFF1E2A3A),
    outline = Color(0xFFC5CED8), outlineVariant = Color(0xFFDDE3EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6FC2BB), onPrimary = Color(0xFF0E2E2B),
    secondary = Color(0xFF9EA6F0), onSecondary = Color(0xFF12161D),
    tertiary = Color(0xFFE5B567), onTertiary = Color(0xFF12161D),
    background = Color(0xFF12161D), onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF12161D), onSurface = Color(0xFFE6EAF0),
    surfaceContainer = Color(0xFF1B212B),
    surfaceVariant = Color(0xFF262E3A), onSurfaceVariant = Color(0xFF9AA7B8),
    secondaryContainer = Color(0xFF262E3A), onSecondaryContainer = Color(0xFFE6EAF0),
    outline = Color(0xFF3A4452), outlineVariant = Color(0xFF262E3A),
)

@Composable
fun MargemTheme(content: @Composable () -> Unit) {
    val dark = when (Margem.store.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}

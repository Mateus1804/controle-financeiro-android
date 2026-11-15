package com.mateuswilliam.financas.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens de layout compartilhados. O Scaffold reserva o espaço da navegação e da ação fixa.
object FinanceSpacing {
    val small = 8.dp
    val medium = 12.dp
    val section = 20.dp
    val page = 16.dp
    val card = 16.dp
    val cardGap = 12.dp
    val large = 20.dp
    val listBottom = 24.dp
    val touch = 48.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF765C88), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE6F1), onPrimaryContainer = Color(0xFF302038),
    secondary = Color(0xFF506D58), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9DD), onSecondaryContainer = Color(0xFF1B3824),
    tertiary = Color(0xFF695B75), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6C7E6), onTertiaryContainer = Color(0xFF2C2135),
    background = Color.White, onBackground = Color(0xFF1C1B1F),
    surface = Color.White, onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0EAF3), onSurfaceVariant = Color(0xFF504A53),
    surfaceContainerLowest = Color(0xFFF7F3F9), surfaceContainerLow = Color(0xFFF7F3F9),
    surfaceContainer = Color(0xFFF1EBF4), surfaceContainerHigh = Color(0xFFE9E2ED),
    surfaceContainerHighest = Color(0xFFE1D9E6),
    outline = Color(0xFF7B737E), outlineVariant = Color(0xFFD2CAD5),
    error = Color(0xFFAA3545), onError = Color.White,
    errorContainer = Color(0xFFFFDADD), onErrorContainer = Color(0xFF410915),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFD6C7E6), onPrimary = Color(0xFF392A43),
    primaryContainer = Color(0xFF483750), onPrimaryContainer = Color(0xFFF0E1FA),
    secondary = Color(0xFFB4CFB8), onSecondary = Color(0xFF203826),
    secondaryContainer = Color(0xFF344B38), onSecondaryContainer = Color(0xFFDCE9DD),
    tertiary = Color(0xFFCBB7D5), onTertiary = Color(0xFF382A40),
    tertiaryContainer = Color(0xFF4F4057), onTertiaryContainer = Color(0xFFF0E1FA),
    background = Color.Black, onBackground = Color(0xFFF2ECF3),
    surface = Color(0xFF17121A), onSurface = Color(0xFFF2ECF3),
    surfaceVariant = Color(0xFF302733), onSurfaceVariant = Color(0xFFD0C7D1),
    surfaceContainerLowest = Color(0xFF120D15), surfaceContainerLow = Color(0xFF1A141D),
    surfaceContainer = Color(0xFF211A25), surfaceContainerHigh = Color(0xFF2B2330),
    surfaceContainerHighest = Color(0xFF382F3D),
    outline = Color(0xFF9B919D), outlineVariant = Color(0xFF514854),
    error = Color(0xFFFFAFB7), onError = Color(0xFF650C23),
    errorContainer = Color(0xFF85283A), onErrorContainer = Color(0xFFFFDADD),
)

@Immutable
data class FinanceColors(val income: Color, val expense: Color, val chart: List<Color>)
private val LightFinance = FinanceColors(Color(0xFF5B4969), Color(0xFF526C57), listOf(
    Color(0xFF765C88), Color(0xFF7C9E82), Color(0xFF9B7FA8), Color(0xFF88715E), Color(0xFF657A8A), Color(0xFF8A747F)))
private val DarkFinance = FinanceColors(Color(0xFFD6C7E6), Color(0xFFB4CFB8), listOf(
    Color(0xFFD6C7E6), Color(0xFF9ABD9F), Color(0xFFC1A1D0), Color(0xFFC8AA90), Color(0xFFA3B9CA), Color(0xFFD0AEBF)))
val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

private val FinanceTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FinanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) Dark else Light
    CompositionLocalProvider(LocalFinanceColors provides if (darkTheme) DarkFinance else LightFinance) {
        MaterialTheme(colorScheme = colors, typography = FinanceTypography,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
    }
}

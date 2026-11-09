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
    primary = Color(0xFF0F6B63), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF5F0), onPrimaryContainer = Color(0xFF123C37),
    secondary = Color(0xFF46655E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EFE7), onSecondaryContainer = Color(0xFF203D34),
    tertiary = Color(0xFF8A4938), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9DED0), onTertiaryContainer = Color(0xFF482B20),
    background = Color(0xFFFAF7F1), onBackground = Color(0xFF142B27),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF142B27),
    surfaceVariant = Color(0xFFE5EDE6), onSurfaceVariant = Color(0xFF4C605A),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F2EA),
    surfaceContainer = Color(0xFFEDEFE7), surfaceContainerHigh = Color(0xFFE4EAE2),
    surfaceContainerHighest = Color(0xFFD9E3DC),
    outline = Color(0xFF687D75), outlineVariant = Color(0xFFCCDAD1),
    error = Color(0xFFAA3545), onError = Color.White,
    errorContainer = Color(0xFFFFDADD), onErrorContainer = Color(0xFF410915),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FD6C9), onPrimary = Color(0xFF003B35),
    primaryContainer = Color(0xFF114E47), onPrimaryContainer = Color(0xFFDDF5F0),
    secondary = Color(0xFFB9D7CA), onSecondary = Color(0xFF173C30),
    secondaryContainer = Color(0xFF28483D), onSecondaryContainer = Color(0xFFDFEEE5),
    tertiary = Color(0xFFF1BDA8), onTertiary = Color(0xFF4B2C20),
    tertiaryContainer = Color(0xFF603C2F), onTertiaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF000000), onBackground = Color(0xFFE2EEE8),
    surface = Color(0xFF101414), onSurface = Color(0xFFE2EEE8),
    surfaceVariant = Color(0xFF24332E), onSurfaceVariant = Color(0xFFBFCFC7),
    surfaceContainerLowest = Color(0xFF080C0B), surfaceContainerLow = Color(0xFF101414),
    surfaceContainer = Color(0xFF15201C), surfaceContainerHigh = Color(0xFF1E2B25),
    surfaceContainerHighest = Color(0xFF2B3A32),
    outline = Color(0xFF91A69A), outlineVariant = Color(0xFF3C5045),
    error = Color(0xFFFFAFB7), onError = Color(0xFF650C23),
    errorContainer = Color(0xFF85283A), onErrorContainer = Color(0xFFFFDADD),
)

@Immutable
data class FinanceColors(val income: Color, val expense: Color, val chart: List<Color>)
private val LightFinance = FinanceColors(Color(0xFF276A50), Color(0xFF914B37), listOf(
    Color(0xFF0F6B63), Color(0xFF629E8C), Color(0xFFBC8750), Color(0xFFA36D8C), Color(0xFF7A8FA3), Color(0xFF95855D)))
private val DarkFinance = FinanceColors(Color(0xFF9AD6B5), Color(0xFFF1BDA8), listOf(
    Color(0xFFB0ACDD), Color(0xFF83B9A8), Color(0xFFD9B282), Color(0xFFCEA1BD), Color(0xFF9EB8CD), Color(0xFFC0B58B)))
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

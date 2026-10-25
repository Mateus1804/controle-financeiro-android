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
    val page = 20.dp
    val large = 24.dp
    val listBottom = 24.dp
    val touch = 48.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF54558B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E0F8), onPrimaryContainer = Color(0xFF262544),
    secondary = Color(0xFF5E5D72), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E4F0), onSecondaryContainer = Color(0xFF292838),
    tertiary = Color(0xFF3D685B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0ECDD), onTertiaryContainer = Color(0xFF133C30),
    background = Color(0xFFF7F7FA), onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFEAE8EF), onSurfaceVariant = Color(0xFF53515E),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F1F5),
    surfaceContainer = Color(0xFFEEEDF2), surfaceContainerHigh = Color(0xFFE8E7ED),
    surfaceContainerHighest = Color(0xFFE2E1E8),
    outline = Color(0xFF797782), outlineVariant = Color(0xFFD7D5DE),
    error = Color(0xFFAA3545), onError = Color.White,
    errorContainer = Color(0xFFFFDADD), onErrorContainer = Color(0xFF410915),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC6C0F5), onPrimary = Color(0xFF2C2B53),
    primaryContainer = Color(0xFF3F3E64), onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFFCAC5DB), onSecondary = Color(0xFF302D40),
    secondaryContainer = Color(0xFF454253), onSecondaryContainer = Color(0xFFE7E1F4),
    tertiary = Color(0xFFA1D2C0), onTertiary = Color(0xFF0D382A),
    tertiaryContainer = Color(0xFF285143), onTertiaryContainer = Color(0xFFBDEEDC),
    background = Color(0xFF121316), onBackground = Color(0xFFE5E2E9),
    surface = Color(0xFF1B1C20), onSurface = Color(0xFFE5E2E9),
    surfaceVariant = Color(0xFF303036), onSurfaceVariant = Color(0xFFCAC5D1),
    surfaceContainerLowest = Color(0xFF18191D), surfaceContainerLow = Color(0xFF1C1D21),
    surfaceContainer = Color(0xFF202125), surfaceContainerHigh = Color(0xFF292A2E),
    surfaceContainerHighest = Color(0xFF343539),
    outline = Color(0xFF96919E), outlineVariant = Color(0xFF48454F),
    error = Color(0xFFFFAFB7), onError = Color(0xFF650C23),
    errorContainer = Color(0xFF85283A), onErrorContainer = Color(0xFFFFDADD),
)

@Immutable
data class FinanceColors(val income: Color, val expense: Color, val chart: List<Color>)
private val LightFinance = FinanceColors(Color(0xFF276A50), Color(0xFFA43F50), listOf(
    Color(0xFF7475A9), Color(0xFF4B8C7B), Color(0xFFBC8750), Color(0xFFA36D8C), Color(0xFF7A8FA3), Color(0xFF95855D)))
private val DarkFinance = FinanceColors(Color(0xFF9AD6B5), Color(0xFFF1A5B1), listOf(
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
fun FinanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) Dark else Light
    CompositionLocalProvider(LocalFinanceColors provides if (darkTheme) DarkFinance else LightFinance) {
        MaterialTheme(colorScheme = colors, typography = FinanceTypography,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
    }
}

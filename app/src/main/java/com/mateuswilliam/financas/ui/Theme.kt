package com.mateuswilliam.financas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF4055C8)
val Ink = Color(0xFF17213B)
val SoftBlue = Color(0xFFC4CEFF)
val ExpenseRed = Color(0xFFB43F4D)

private val Light = lightColorScheme(
    primary = BrandBlue, onPrimary = Color.White, primaryContainer = Color(0xFFE5E9FF),
    onPrimaryContainer = Ink, secondary = Color(0xFF5965A8),
    tertiary = Color(0xFF187963), onTertiary = Color.White,
    background = Color(0xFFF5F6FC), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEBEDF6), onSurfaceVariant = Color(0xFF586078),
    outline = Color(0xFF7B8199), error = ExpenseRed,
)
private val Dark = darkColorScheme(
    primary = SoftBlue, onPrimary = Ink, primaryContainer = BrandBlue, onPrimaryContainer = Color.White,
    tertiary = Color(0xFF70D9B9), secondary = Color(0xFFB6BFFF),
    background = Color(0xFF10131F), onBackground = Color(0xFFE8EBF7),
    surface = Color(0xFF1A1F30), onSurface = Color(0xFFE8EBF7),
    surfaceVariant = Color(0xFF2A3044), onSurfaceVariant = Color(0xFFC1C7DF),
    error = Color(0xFFFFA5AB),
)

@Composable
fun FinanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography(), content = content)
}

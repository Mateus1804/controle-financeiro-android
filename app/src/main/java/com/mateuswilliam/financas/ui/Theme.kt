package com.mateuswilliam.financas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Pine = Color(0xFF145E46)
val Ink = Color(0xFF0C2821)
val Lime = Color(0xFFD9ED92)
val ExpenseRed = Color(0xFFB43F4D)

private val Light = lightColorScheme(
    primary = Pine, onPrimary = Color.White, primaryContainer = Color(0xFFE4EEDA),
    onPrimaryContainer = Ink, secondary = Color(0xFF596B42),
    background = Color(0xFFF3F5F1), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE8EDE7), onSurfaceVariant = Color(0xFF56665E),
    outline = Color(0xFF77867D), error = ExpenseRed,
)
private val Dark = darkColorScheme(
    primary = Lime, onPrimary = Ink, primaryContainer = Pine, onPrimaryContainer = Color.White,
    background = Color(0xFF101B17), onBackground = Color(0xFFEDF2EB),
    surface = Color(0xFF192720), onSurface = Color(0xFFEDF2EB),
    surfaceVariant = Color(0xFF293A30), onSurfaceVariant = Color(0xFFB8C7BA),
    error = Color(0xFFFFA5AB),
)

@Composable
fun FinanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography(), content = content)
}

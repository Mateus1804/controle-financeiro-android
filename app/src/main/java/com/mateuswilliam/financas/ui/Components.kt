package com.mateuswilliam.financas.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.os.Build
import android.app.DatePickerDialog
import android.content.Context
import android.view.WindowManager
import com.mateuswilliam.financas.FinanceApplication
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateuswilliam.financas.core.Money
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun showFinanceDatePicker(context: Context, theme: Int, selected: LocalDate, onSelected: (LocalDate) -> Unit): DatePickerDialog {
    return DatePickerDialog(context, theme, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
        // Diálogos nativos têm janela própria e precisam respeitar a proteção da tela.
        if ((context.applicationContext as? FinanceApplication)?.settings?.snapshot()?.secureScreen == true)
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        show()
    }
}

@Composable
fun DialogSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }
}

@Composable
fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), description, modifier.size(24.dp))
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyCard(title: String, message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.large), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(R.drawable.ic_records)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun IconBadge(@DrawableRes icon: Int, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@DrawableRes
fun categoryIcon(category: com.mateuswilliam.financas.core.Category): Int = when (category) {
    com.mateuswilliam.financas.core.Category.FOOD -> R.drawable.ic_food
    com.mateuswilliam.financas.core.Category.HOME -> R.drawable.ic_home
    com.mateuswilliam.financas.core.Category.TRANSPORT -> R.drawable.ic_transport
    com.mateuswilliam.financas.core.Category.HEALTH -> R.drawable.ic_health
    com.mateuswilliam.financas.core.Category.EDUCATION -> R.drawable.ic_education
    com.mateuswilliam.financas.core.Category.LEISURE -> R.drawable.ic_leisure
    else -> R.drawable.ic_category
}

@Composable
fun movementColor(movement: Movement): androidx.compose.ui.graphics.Color = when (movement) {
    Movement.INCOME -> LocalFinanceColors.current.income
    Movement.EXPENSE -> LocalFinanceColors.current.expense
    Movement.TRANSFER -> MaterialTheme.colorScheme.secondary
    Movement.REFUND -> MaterialTheme.colorScheme.tertiary
    Movement.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@DrawableRes
fun movementIcon(movement: Movement): Int = when (movement) {
    Movement.INCOME -> R.drawable.ic_income
    Movement.EXPENSE -> R.drawable.ic_expense
    Movement.TRANSFER -> R.drawable.ic_transfer
    Movement.REFUND -> R.drawable.ic_refund
    Movement.UNKNOWN -> R.drawable.ic_pending
}

// A formatação é apenas visual. Cálculos e persistência continuam em centavos inteiros.
@Composable
fun Amount(cents: Long, hidden: Boolean, modifier: Modifier = Modifier, size: Int = 20) {
    androidx.compose.foundation.text.BasicText(
        text = if (hidden) "••••" else Money.format(cents), modifier = modifier.semantics { if (hidden) contentDescription = "Valor oculto" },
        style = LocalTextStyle.current.copy(color = LocalContentColor.current, fontSize = size.sp,
            lineHeight = (size * 1.25f).sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        maxLines = 2,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = minOf(size, 14).sp,
            maxFontSize = size.sp, stepSize = 1.sp),
    )
}

@Composable
fun RecordRow(record: Record, hidden: Boolean, onEdit: (() -> Unit)? = null) {
    val color = movementColor(record.movement)
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f
    val clickable = if (onEdit != null) Modifier.clickable(onClickLabel = "Editar lançamento", onClick = onEdit) else Modifier
    ListItem(
        modifier = Modifier.fillMaxWidth().then(clickable).heightIn(min = 80.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        leadingContent = { IconBadge(if (record.movement == Movement.TRANSFER) R.drawable.ic_transfer else categoryIcon(record.category), color) },
        headlineContent = { Text(privateDisplayText(record.description, hidden), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${privateDisplayText(record.bank, hidden)} · ${shortDate(record.occurredAt)}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppIcon(movementIcon(record.movement), Modifier.size(14.dp))
                    Text("${record.movement.label} · ${record.category.label}", style = MaterialTheme.typography.labelSmall)
                }
                if (largeText) CompositionLocalProvider(LocalContentColor provides color) { Amount(record.amountCents, hidden, size = 18) }
            }
        },
        trailingContent = if (!largeText) {{
            Column(Modifier.widthIn(max = 132.dp), horizontalAlignment = Alignment.End) {
                CompositionLocalProvider(LocalContentColor provides color) { Amount(record.amountCents, hidden, size = 17) }
                if (onEdit != null) AppIcon(R.drawable.ic_chevron_right, Modifier.padding(top = 4.dp).size(16.dp))
            }
        }} else if (onEdit != null) {{ AppIcon(R.drawable.ic_chevron_right) }} else null,
    )
}

// Superfícies adjacentes compartilham o mesmo fundo sem divisórias ou cartões aninhados.
fun groupedShape(index: Int, count: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) 24.dp else 0.dp, topEnd = if (index == 0) 24.dp else 0.dp,
    bottomStart = if (index == count - 1) 24.dp else 0.dp, bottomEnd = if (index == count - 1) 24.dp else 0.dp)

@Composable
fun CategoryBar(label: String, cents: Long, maximum: Long, hidden: Boolean, onClick: (() -> Unit)? = null) {
    Surface(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Amount(cents, hidden, Modifier.widthIn(max = 168.dp), size = 16)
            }
            if (!hidden) LinearProgressIndicator(
                progress = { (cents.toDouble() / maximum.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics {},
                color = if (label.startsWith("Receita")) LocalFinanceColors.current.income else LocalFinanceColors.current.expense,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

fun shortDate(epoch: Long): String = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.forLanguageTag("pt-BR"))
    .format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))

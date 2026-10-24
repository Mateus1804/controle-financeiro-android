package com.mateuswilliam.financas.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), description, modifier.size(24.dp))
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun Amount(cents: Long, hidden: Boolean, modifier: Modifier = Modifier, size: Int = 20) {
    Text(if (hidden) "••••" else Money.format(cents), modifier, fontSize = size.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun RecordRow(record: Record, hidden: Boolean, onEdit: (() -> Unit)? = null) {
    val color = when (record.movement) {
        Movement.EXPENSE -> MaterialTheme.colorScheme.error
        Movement.TRANSFER -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val clickable = if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier
    Row(Modifier.fillMaxWidth().then(clickable).heightIn(min = 48.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(when (record.movement) {
                Movement.EXPENSE -> R.drawable.ic_expense
                Movement.TRANSFER -> R.drawable.ic_transfer
                else -> R.drawable.ic_income
            }), contentDescription = null, tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(record.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("${record.bank} · ${record.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${record.movement.label} · ${shortDate(record.occurredAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(record.amountCents, hidden, size = 15)
            if (onEdit != null) TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Editar", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun CategoryBar(label: String, cents: Long, maximum: Long, hidden: Boolean, onClick: (() -> Unit)? = null) {
    Surface(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).semantics {
            contentDescription = if (hidden) "$label, valor oculto" else "$label, ${Money.format(cents)}" + if (onClick != null) ". Toque para ver os lançamentos." else ""
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Amount(cents, hidden, size = 14)
            }
            LinearProgressIndicator(
                progress = { (cents.toDouble() / maximum.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

fun shortDate(epoch: Long): String = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.forLanguageTag("pt-BR"))
    .format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))

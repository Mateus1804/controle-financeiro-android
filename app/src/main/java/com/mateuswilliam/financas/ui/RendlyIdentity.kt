package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*

@Composable
internal fun DisplayNameDialog(initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Como você quer ser chamado?") }, text = {
        Column {
            Text("Opcional. Use até 40 caracteres. O nome fica neste aparelho e não integra o backup.")
            OutlinedTextField(value, { value = it; error = null }, singleLine = true,
                label = { Text("Nome de exibição") }, isError = error != null,
                modifier = Modifier.fillMaxWidth().testTag("display-name-input"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        val name = value.trim()
        if (name.codePointCount(0, name.length) > 40 || name.any { it.isISOControl() }) error = "Use até 40 caracteres em uma única linha."
        else runCatching { save(name) }.onSuccess { dismiss() }.onFailure { error = it.message }
    }) { Text("Salvar") } }, dismissButton = {
        Row { TextButton({ runCatching { save("") }.onSuccess { dismiss() }.onFailure { error = it.message } }) { Text("Apagar nome") }
            TextButton(dismiss) { Text("Cancelar") } }
    })
}

@Composable
internal fun RendlyCategories(report: ForecastReport, hidden: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Gastos por categoria", style = MaterialTheme.typography.titleMedium)
        val grouped = report.lines.filter { it.included && !it.ignored && it.kind == ForecastKind.EXPENSE }
            .groupBy { it.category }.mapValues { (_, lines) -> lines.sumOf { it.amountCents } }
        if (grouped.isEmpty()) Text("As categorias aparecem após cadastrar despesas.")
        grouped.entries.sortedByDescending { it.value }.forEach { (category, cents) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.label, Modifier.weight(1f)); Amount(cents, hidden, size = 16)
            }
            if (!hidden && grouped.values.all { it >= 0 } && report.totalExpensesCents > 0 && cents > 0) LinearProgressIndicator(
                progress = { (cents.toDouble() / report.totalExpensesCents).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
        }
    }
}

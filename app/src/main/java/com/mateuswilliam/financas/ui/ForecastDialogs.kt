package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*

internal val financeDateFormat: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
internal fun CycleDayEditor(
    currentDay: Int, busy: Boolean = false, preview: (Int) -> BudgetPeriod,
    onDismiss: () -> Unit, onSave: (Int) -> Unit, firstConfiguration: Boolean = false,
) {
    var day by rememberSaveable { mutableIntStateOf(currentDay) }
    val next = preview(day)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text("Início do ciclo") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceField("Dia de início", day, (1..31).toList(), { "Dia $it" }) { if (!busy) day = it }
            Text("${if (firstConfiguration) "Ciclo inicial" else "Primeiro ciclo com a mudança"} · ${next.start.format(financeDateFormat)} a ${next.endExclusive.minusDays(1).format(financeDateFormat)}")
            Text(if (firstConfiguration) "Esta configuração define o ciclo atual. Nos meses mais curtos, o dia é ajustado ao último dia do mês."
                else "A mudança começa na data da prévia. Ciclos com vínculos e ajustes registrados são preservados.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { FilledTonalButton({ onSave(day) }, enabled = !busy, modifier = Modifier.testTag("cycle-save")) { Text("Salvar dia do ciclo") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}

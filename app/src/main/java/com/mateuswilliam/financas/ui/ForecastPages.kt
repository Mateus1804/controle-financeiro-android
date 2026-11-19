package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.core.Record
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun ReserveGoalEditor(existing: Long?, busy: Boolean, hidden: Boolean, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    val amount = rememberTextFieldState(initialText = existing?.let(MoneyInput::fromCents).orEmpty())
    var invalid by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val cents = if (invalid) null else MoneyInput.cents(amount.text.toString())
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { DialogSystemBars(); Text("Meta da reserva") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Deixe em branco para remover a meta. Isso não altera o saldo da reserva.")
            PrivateValueGate(hidden, "Mostrar meta para edição") { MoneyAmountField(amount, attempted, !busy, label = "Meta (R$)", tag = "reserve-goal-input", allowZero = true, onInvalidInput = { invalid = it }) }
        } }, confirmButton = { TextButton({ attempted = true; if (!invalid && (amount.text.isEmpty() || cents != null)) onSave(cents) }, enabled = !busy) { Text("Salvar meta") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
internal fun ForecastProjectionScreen(data: ForecastDataset, selected: BudgetPeriod, simulation: ForecastRule?, hidden: Boolean, busy: Boolean,
    onSimulate: () -> Unit, onDiscard: () -> Unit, onAdopt: () -> Unit, onOpen: (BudgetPeriod) -> Unit,
    records: List<Record>, asOf: Long) {
    val zone = ZoneId.systemDefault()
    val periods = remember(data.cycleRules, selected) {
        val month = YearMonth.from(selected.start)
        List(6) { index -> FinancialPeriods.month(month.plusMonths(index.toLong()), data.cycleRules) }
    }
    val baseline = remember(data, periods, records, asOf, zone) {
        CentralFinance.summarizePeriods(records, data, periods, zone, asOf)
    }
    val simulationResults = remember(data, simulation, periods, records, asOf, zone) {
        simulation?.let {
            CentralFinance.summarizePeriods(records, data.copy(rules = data.rules + it), periods, zone, asOf)
        }
    }
    LazyColumn(Modifier.testTag("forecast-projections"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionTitle("Próximos ciclos", "O mesmo orçamento, considerando o término das parcelas e os ajustes de cada período")
            OutlinedButton(onSimulate, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("forecast-simulate")) { Text(if (simulation == null) "Simular uma nova parcela" else "Editar simulação") }
        }
        if (simulation != null) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simulação · ainda fora do orçamento", style = MaterialTheme.typography.titleSmall)
                    Text(privateDisplayText(simulation.title, hidden))
                    Button(onAdopt, enabled = !busy, modifier = Modifier.testTag("forecast-adopt-simulation")) { Text("Adotar no planejamento") }
                    TextButton(onDiscard, enabled = !busy) { Text("Descartar simulação") }
                }
            }
        }
        baseline.forEachIndexed { index, summary ->
            val base = summary.projectedReport
            val simulated = simulationResults?.get(index)?.projectedReport
            item(key = base.period.start.toString()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${base.period.start.format(financeDateFormat)} a ${base.period.endExclusive.minusDays(1).format(financeDateFormat)}", style = MaterialTheme.typography.titleMedium)
                        Text(if (hidden) "Percentual oculto" else "Gastos totais: ${base.expensePercentage?.toPlainString()?.replace('.', ',')?.plus("% da renda") ?: "renda não informada"}")
                        Text("Margem estimada", style = MaterialTheme.typography.bodySmall)
                        Amount(base.marginCents, hidden, Modifier.testTag("projection-margin-${base.period.start}"))
                        if (simulated != null) {
                            Text(if (hidden) "Simulação: valores ocultos" else "Com a simulação: ${simulated.expensePercentage?.toPlainString()?.replace('.', ',')?.plus("% da renda") ?: "renda não informada"}", style = MaterialTheme.typography.bodySmall)
                            Amount(simulated.marginCents, hidden)
                        }
                        TextButton({ onOpen(base.period) }) { Text("Abrir este ciclo") }
                    }
                }
            }
        }

    }
}

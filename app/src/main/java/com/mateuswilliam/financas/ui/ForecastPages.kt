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
import com.mateuswilliam.financas.data.MonthlyPlanEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ForecastReserveScreen(data: ForecastDataset, report: ForecastReport, hidden: Boolean, busy: Boolean,
    onSet: () -> Unit, onAdd: () -> Unit, onWithdraw: () -> Unit, onGoal: () -> Unit) {
    val balance = report.reserveBalanceCents
    LazyColumn(Modifier.testTag("forecast-reserve"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Reserva de emergência", "Dinheiro informado por você, separado da renda e dos gastos do orçamento") }
        item { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saldo atual informado", style = MaterialTheme.typography.titleMedium)
                if (balance == null) Text("Reserva ainda não informada") else Amount(balance, hidden, Modifier.testTag("reserve-balance"), size = 30)
                Button(onSet, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("reserve-set")) { Text(if (balance == null) "Informar minha reserva" else "Corrigir saldo informado") }
                if (balance != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onAdd, enabled = !busy, modifier = Modifier.weight(1f).testTag("reserve-add")) { Text("Adicionar") }
                    OutlinedButton(onWithdraw, enabled = !busy && balance > 0, modifier = Modifier.weight(1f).testTag("reserve-withdraw")) { Text("Retirar") }
                }
                Text("Nenhuma sobra ou déficit altera esta reserva automaticamente.", style = MaterialTheme.typography.bodySmall)
            }
        } }
        item {
            Text("Meta opcional", style = MaterialTheme.typography.titleMedium)
            data.reserveGoalCents?.let { goal ->
                Amount(goal, hidden)
                if (balance != null && goal > 0 && !hidden) {
                    LinearProgressIndicator(progress = { (balance.toDouble() / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(if (balance >= goal) "Meta alcançada" else "Faltam ${Money.format(goal - balance)}", style = MaterialTheme.typography.bodySmall)
                }
            } ?: Text("Você pode definir uma meta sem informar renda novamente.", style = MaterialTheme.typography.bodySmall)
            TextButton(onGoal, enabled = !busy, modifier = Modifier.testTag("reserve-edit-goal")) { Text("Definir ou alterar meta") }
        }
        item { SectionTitle("Histórico da reserva", "Aportes não são despesas; retiradas não são receitas") }
        data.reserveEvents.sortedByDescending { it.id }.forEach { event -> item(key = event.id) {
            ListItem(headlineContent = { Text(when (event.kind) { ReserveEventKind.SET -> "Saldo informado / correção"; ReserveEventKind.ADD -> "Aporte informado"; ReserveEventKind.WITHDRAW -> "Retirada informada" }) },
                supportingContent = { Column {
                    Text(privateDisplayText(event.reason, hidden), style = MaterialTheme.typography.bodySmall)
                    Text(shortDate(event.createdAt), style = MaterialTheme.typography.labelSmall)
                    event.cycleStart?.let { Text("Destinação do ciclo iniciado em ${it.format(commitmentDateFormat)}", style = MaterialTheme.typography.labelSmall) }
                } }, trailingContent = { Amount(event.amountCents, hidden, size = 16) })
        } }
    }
}

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
    legacyIncome: List<MonthlyPlanEntity>, onReuse: (MonthlyPlanEntity) -> Unit,
    represented: Set<Pair<String, String>> = emptySet()) {
    val periods = remember(data.cycleRules, selected) {
        buildList { var next = selected; repeat(6) { add(next); next = CycleCalendar.periodContaining(next.endExclusive, data.cycleRules) } }
    }
    val projected = remember(data, simulation, periods, represented) { periods.map { period ->
        ForecastCalculator.report(data, period, represented) to simulation?.let { ForecastCalculator.report(data.copy(rules = data.rules + it), period, represented) }
    } }
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
        projected.forEach { (base, simulated) -> item(key = base.period.start.toString()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${base.period.start.format(commitmentDateFormat)} a ${base.period.endExclusive.minusDays(1).format(commitmentDateFormat)}", style = MaterialTheme.typography.titleMedium)
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
        } }
        if (legacyIncome.isNotEmpty()) item {
            FinanceDisclosure("Rendas de referência antigas", "forecast-legacy-income") {
                Text("Valores preservados do cadastro anterior. Confira se o salário já está previsto antes de reaproveitar uma referência. Nenhuma delas foi somada automaticamente.", style = MaterialTheme.typography.bodySmall)
                legacyIncome.sortedByDescending { it.month }.forEach { old ->
                    Text("Referência de ${old.month}", style = MaterialTheme.typography.titleSmall)
                    Amount(old.incomeCents, hidden)
                    TextButton({ onReuse(old) }, enabled = !busy) { Text("Revisar como previsão única") }
                }
            }
        }
    }
}

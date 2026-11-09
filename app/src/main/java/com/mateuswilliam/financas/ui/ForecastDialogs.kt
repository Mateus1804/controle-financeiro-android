package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.LocalDate

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
            Text("${if (firstConfiguration) "Ciclo inicial" else "Próximo ciclo"}: ${next.start.format(commitmentDateFormat)} a ${next.endExclusive.minusDays(1).format(commitmentDateFormat)}")
            Text(if (firstConfiguration) "Esta configuração define o ciclo atual. Nos meses mais curtos, o dia é ajustado ao último dia do mês."
                else "A mudança vale na próxima fronteira. Os ciclos anteriores são preservados. Nos meses mais curtos, o dia é ajustado ao último dia do mês.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { FilledTonalButton({ onSave(day) }, enabled = !busy, modifier = Modifier.testTag("cycle-save")) { Text("Salvar dia do ciclo") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
internal fun ReserveEditor(
    kind: ReserveEventKind, balanceCents: Long?, busy: Boolean, hidden: Boolean,
    onDismiss: () -> Unit, onSave: (Long, String, LocalDate?) -> Unit,
    allocationLimitCents: Long? = null, cycle: BudgetPeriod? = null,
    goalCents: Long? = null, onSaveGoal: ((Long?) -> Unit)? = null,
    allocateInitially: Boolean = false,
) {
    val amount = rememberTextFieldState()
    val goal = rememberTextFieldState(initialText = goalCents?.let(MoneyInput::fromCents).orEmpty())
    var invalid by rememberSaveable { mutableStateOf(false) }
    var invalidGoal by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf(when (kind) {
        ReserveEventKind.SET -> if (balanceCents == null) "Saldo inicial informado" else ""
        ReserveEventKind.ADD -> "Aporte informado"
        ReserveEventKind.WITHDRAW -> "Retirada informada"
    }) }
    var allocate by rememberSaveable { mutableStateOf(allocateInitially) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val cents = if (invalid) null else MoneyInput.cents(amount.text.toString())
    val goalValue = if (invalidGoal) null else MoneyInput.cents(goal.text.toString())
    val error = when {
        cents == null || cents < 0 || (cents == 0L && kind != ReserveEventKind.SET) -> "Informe um valor válido."
        kind == ReserveEventKind.ADD && balanceCents == null -> "Defina o saldo inicial da reserva antes de adicionar."
        kind == ReserveEventKind.ADD && balanceCents != null && cents > Money.MAX_CENTS - balanceCents -> "O saldo ultrapassa o limite monetário."
        kind == ReserveEventKind.WITHDRAW && balanceCents == null -> "Defina o saldo da reserva antes de retirar."
        kind == ReserveEventKind.WITHDRAW && cents > (balanceCents ?: 0) -> "A retirada não pode superar o saldo da reserva."
        allocate && cents > (allocationLimitCents ?: 0).coerceAtLeast(0) -> "O aporte supera a sobra ainda não destinada deste ciclo."
        reason.isBlank() -> "Informe o motivo."
        onSaveGoal != null && goal.text.isNotEmpty() && goalValue == null -> "Informe uma meta válida."
        else -> null
    }
    val title = when (kind) { ReserveEventKind.SET -> "Definir saldo da reserva"; ReserveEventKind.ADD -> "Adicionar à reserva"; ReserveEventKind.WITHDRAW -> "Retirar da reserva" }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (balanceCents == null) "Saldo ainda não informado" else "Saldo atual: ${privateDisplayText(Money.format(balanceCents), hidden)}")
            Text(when (kind) {
                ReserveEventKind.SET -> "Informe o saldo atual. Digite zero para registrar uma reserva sem saldo."
                ReserveEventKind.ADD -> "O aporte não altera seus gastos nem o percentual do ciclo."
                ReserveEventKind.WITHDRAW -> "A retirada reduz a reserva, sem criar renda ou desfazer destinações anteriores."
            }, style = MaterialTheme.typography.bodySmall)
            PrivateValueGate(hidden, "Mostrar valores da reserva para edição") {
                MoneyAmountField(amount, attempted, !busy, label = "Valor (R$)", tag = "reserve-amount", allowZero = kind == ReserveEventKind.SET, onInvalidInput = { invalid = it })
                if (onSaveGoal != null) MoneyAmountField(goal, attempted && goal.text.isNotEmpty(), !busy, label = "Meta da reserva (opcional)", tag = "reserve-goal", allowZero = true, onInvalidInput = { invalidGoal = it })
            }
            OutlinedTextField(reason, { reason = it.take(200) }, enabled = !busy, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth().testTag("reserve-reason"))
            if (kind == ReserveEventKind.ADD && allocationLimitCents != null && cycle != null) {
                ForecastToggle("Destinar a sobra deste ciclo", allocate, !busy) { allocate = it }
                Text("Disponível: ${privateDisplayText(Money.format(allocationLimitCents.coerceAtLeast(0)), hidden)}", style = MaterialTheme.typography.bodySmall)
            }
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { FilledTonalButton({
            attempted = true
            if (error == null && cents != null) {
                if (onSaveGoal != null && goalValue != goalCents) onSaveGoal(goalValue)
                onSave(cents, reason.trim(), if (allocate && kind == ReserveEventKind.ADD) cycle?.start else null)
            }
        }, enabled = !busy, modifier = Modifier.testTag("reserve-save")) { Text("Salvar reserva") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}

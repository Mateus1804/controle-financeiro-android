package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A reserva é uma conta; o histórico legado continua consultável sem criar transferências fictícias. */
@Composable
internal fun ReserveAccountScreen(
    data: ForecastDataset, balances: List<AccountBalance>, records: List<Record>, period: BudgetPeriod,
    hidden: Boolean, busy: Boolean, onSet: () -> Unit, onAdd: () -> Unit,
    onWithdraw: () -> Unit, onGoal: () -> Unit,
    currentReserve: AccountBalance? = balances.firstOrNull { it.account.key == AccountFunds.RESERVE_KEY },
) {
    val reserveKey = AccountFunds.RESERVE_KEY
    val balance = balances.firstOrNull { it.account.key == reserveKey }?.balanceCents
    val currentBalance = currentReserve?.balanceCents
    val canTransfer = !busy && currentBalance != null && currentReserve.archived == false
    val legacyBalance = if (currentBalance == null) ForecastCalculator.reserveBalance(data.reserveEvents) else null
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val transfers = records.filter { record ->
        record.movement == Movement.TRANSFER && record.occurredAt <= now &&
            Instant.ofEpochMilli(record.occurredAt).atZone(zone).toLocalDate() in period &&
            (AccountNames.key(record.bank) == reserveKey || AccountNames.key(record.destination) == reserveKey)
    }.sortedByDescending { it.occurredAt }
    val incoming = transfers.filter { AccountNames.key(it.destination) == reserveKey }.sumOf { it.amountCents }
    val outgoing = transfers.filter { AccountNames.key(it.bank) == reserveKey }.sumOf { it.amountCents }
    LazyColumn(
        Modifier.testTag("forecast-reserve"), contentPadding = PaddingValues(FinanceSpacing.page),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("Reserva de emergência", "Uma conta do seu patrimônio. Guardar e resgatar são transferências entre suas contas.") }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Saldo acumulado da reserva", style = MaterialTheme.typography.titleMedium)
                    if (balance == null) Text("Informe o saldo inicial da conta Reserva, inclusive se for zero.")
                    else Amount(balance, hidden, Modifier.testTag("reserve-balance"), size = 30)
                    Text("Até ${minOf(period.endExclusive.minusDays(1), LocalDate.now()).format(financeDateFormat)}", style = MaterialTheme.typography.bodySmall)
                    if (legacyBalance != null) {
                        Text("Último saldo do controle anterior", style = MaterialTheme.typography.bodySmall)
                        Amount(legacyBalance, hidden, Modifier.testTag("reserve-legacy-balance"), size = 18)
                        Text("Confira esse valor e informe o saldo de referência para incluir a reserva no patrimônio.", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onSet, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("reserve-set")) {
                        Text(if (currentBalance == null) "Informar saldo inicial" else "Consultar ou corrigir saldo")
                    }
                    if (currentReserve?.archived == true) Text("Esta conta está arquivada. Reative-a em Contas antes de fazer novas transferências.")
                    OutlinedButton(onAdd, enabled = canTransfer,
                        modifier = Modifier.fillMaxWidth().testTag("reserve-add")) { Text("Guardar por transferência") }
                    OutlinedButton(onWithdraw, enabled = canTransfer && (currentBalance ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth().testTag("reserve-withdraw")) { Text("Resgatar por transferência") }
                    Text("Nenhuma sobra ou déficit altera esta reserva automaticamente.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionTitle("Transferências do período", "${period.start.format(financeDateFormat)} a ${period.endExclusive.minusDays(1).format(financeDateFormat)}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Guardado", style = MaterialTheme.typography.labelLarge)
                    Amount(incoming, hidden, Modifier.testTag("reserve-period-in"), size = 20)
                }
                Column(Modifier.weight(1f)) {
                    Text("Resgatado", style = MaterialTheme.typography.labelLarge)
                    Amount(outgoing, hidden, Modifier.testTag("reserve-period-out"), size = 20)
                }
            }
            Text("Transferências não alteram receitas, despesas ou patrimônio total.", style = MaterialTheme.typography.bodySmall)
        }
        items(transfers, key = { it.id }) { record ->
            ListItem(
                headlineContent = { Text(if (AccountNames.key(record.destination) == reserveKey) "Guardado na reserva" else "Resgatado da reserva") },
                supportingContent = {
                    Column {
                        Text(privateDisplayText(record.description, hidden))
                        Text(shortDate(record.occurredAt), style = MaterialTheme.typography.labelSmall)
                    }
                },
                trailingContent = { Amount(record.amountCents, hidden, size = 16) },
            )
        }
        if (transfers.isEmpty()) item { Text("Nenhuma transferência da reserva neste período.") }
        item {
            Text("Meta opcional", style = MaterialTheme.typography.titleMedium)
            data.reserveGoalCents?.let { goal ->
                Amount(goal, hidden)
                if (balance != null && goal > 0 && !hidden) {
                    LinearProgressIndicator(progress = { (balance.toDouble() / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(if (balance >= goal) "Meta alcançada" else "Faltam ${Money.format(goal - balance)}", style = MaterialTheme.typography.bodySmall)
                }
            } ?: Text("Uma meta ajuda a acompanhar quanto você deseja guardar.", style = MaterialTheme.typography.bodySmall)
            TextButton(onGoal, enabled = !busy, modifier = Modifier.testTag("reserve-edit-goal")) { Text("Definir ou alterar meta") }
        }
        if (data.reserveEvents.isNotEmpty()) item {
            FinanceDisclosure("Histórico anterior da reserva", "reserve-legacy-history") {
                Text("Estes registros foram preservados para consulta. Como não identificam as contas de origem e destino, não entram nos saldos ou nas transferências atuais. Informe o saldo inicial da conta Reserva para começar o controle centralizado.",
                    style = MaterialTheme.typography.bodySmall)
                data.reserveEvents.sortedWith(compareByDescending<ReserveEvent> { it.createdAt }.thenByDescending { it.id }).forEach { event ->
                    Text(when (event.kind) {
                        ReserveEventKind.SET -> "Saldo informado / correção"
                        ReserveEventKind.ADD -> "Aporte informado"
                        ReserveEventKind.WITHDRAW -> "Retirada informada"
                    }, style = MaterialTheme.typography.titleSmall)
                    Text(shortDate(event.createdAt), style = MaterialTheme.typography.labelSmall)
                    Text(privateDisplayText(event.reason, hidden), style = MaterialTheme.typography.bodySmall)
                    Amount(event.amountCents, hidden, size = 16)
                }
            }
        }
    }
}

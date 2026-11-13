package com.mateuswilliam.financas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.InstallmentDue
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class RecurringAction { CONFIRM, EDIT_MONTH, EDIT_FUTURE, PAUSE, SKIP, EXCLUDE, FIXED }

internal class MovementActions(
    val editRecord: (Record) -> Unit,
    val recurring: (RecurringMonth, RecurringAction) -> Unit,
    val confirmInstallment: (InstallmentDue) -> Unit,
    val purchase: (String, PurchaseManagementAction) -> Unit,
    val recurrenceIds: Set<String>,
)

internal data class MonthlyMovement(
    val key: String, val value: Record, val status: String, val realized: Boolean,
    val recurring: RecurringMonth? = null, val installment: InstallmentDue? = null,
    val stored: Boolean = false,
)

/** Os vínculos substituem a previsão paga; nenhuma linha desta composição entra no Ledger. */
internal fun monthlyMovements(records: List<Record>, month: YearMonth, recurring: List<RecurringMonth>,
    installments: List<InstallmentDue>, zone: ZoneId = ZoneId.systemDefault(), asOf: Long = System.currentTimeMillis()): List<MonthlyMovement> {
    val all = records.associateBy { it.id }
    val result = linkedMapOf<String, MonthlyMovement>()
    Ledger.month(records, month, zone).records.forEach {
        val realized = it.occurredAt <= asOf
        result["record:${it.id}"] = MonthlyMovement("record:${it.id}", it, if (realized) "Realizada" else "Prevista", realized, stored = true)
    }
    recurring.filter { it.month == month }.forEach { occurrence ->
        val paid = occurrence.recordId?.let(all::get)
        if (paid != null) {
            val inPeriod = YearMonth.from(Instant.ofEpochMilli(paid.occurredAt).atZone(zone)) == month
            val key = if (inPeriod) "record:${paid.id}" else "settled:${paid.id}:$month"
            val realized = paid.occurredAt <= asOf
            val status = if (inPeriod) if (realized) "Realizada" else "Prevista"
                else if (realized) "Quitada em ${shortDate(paid.occurredAt)}" else "Agendada para ${shortDate(paid.occurredAt)}"
            result[key] = MonthlyMovement(key, paid, status, inPeriod && realized, recurring = occurrence, stored = true)
        } else if (occurrence.status != RecurringMonthStatus.RECORDED) {
            val key = "recurring:${occurrence.recurringId}:$month"
            val status = if (occurrence.status == RecurringMonthStatus.SKIPPED) "Ignorada neste mês" else if (!occurrence.plan.active) "Pausada" else "Prevista"
            result[key] = MonthlyMovement(key, occurrence.plan.proposedRecord(key, zone), status, false, recurring = occurrence)
        }
    }
    installments.forEach { due ->
        val paid = due.recordId?.let(all::get)
        val inPeriod = paid != null && YearMonth.from(Instant.ofEpochMilli(paid.occurredAt).atZone(zone)) == month
        val key = if (paid != null) if (inPeriod) "record:${paid.id}" else "settled:${paid.id}:$month" else "installment:${due.id}"
        val value = paid ?: Record(key, due.amountCents, Movement.EXPENSE, due.method, due.category,
            due.bank, due.title, LocalDate.parse(due.dueDate).atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
        val realized = paid != null && paid.occurredAt <= asOf
        val status = if (paid == null) "Prevista" else if (inPeriod) if (realized) "Realizada" else "Prevista"
            else if (realized) "Quitada em ${shortDate(paid.occurredAt)}" else "Agendada para ${shortDate(paid.occurredAt)}"
        result[key] = MonthlyMovement(key, value, status, inPeriod && realized, installment = due, stored = paid != null)
    }
    return result.values.sortedWith(compareBy<MonthlyMovement> { if (it.status == "Prevista") 0 else if (it.realized) 1 else 2 }.thenBy { it.value.occurredAt })
}

internal fun LazyListScope.monthlyMovementItems(movements: List<MonthlyMovement>, hidden: Boolean, busy: Boolean, actions: MovementActions) {
    if (movements.isEmpty()) item { EmptyCard("Sem movimentações neste mês", "Cadastre uma receita ou despesa. As previsões aparecem aqui até serem realizadas ou ignoradas.") }
    items(movements, key = { "movement-${it.key}" }) { item ->
        MonthlyMovementRow(item, hidden, busy, actions)
    }
}

@Composable
private fun MonthlyMovementRow(item: MonthlyMovement, hidden: Boolean, busy: Boolean, actions: MovementActions) {
    val largeText = LocalDensity.current.fontScale > 1.25f
    val recurrence = item.recurring
    val installment = item.installment
    val hasPayment = item.stored || item.realized || recurrence?.recordId != null || installment?.recordId != null
    val due = recurrence?.plan?.nextDate ?: installment?.dueDate
    val displayDate = if (!item.realized && hasPayment && due != null) "Vencimento ${LocalDate.parse(due).format(commitmentDateFormat)}" else shortDate(item.value.occurredAt)
    val description = privateDisplayText(item.value.description, hidden)
    val menu = buildList {
        if (hasPayment) add(FinanceAction("Editar lançamento", { actions.editRecord(item.value) }))
        if (recurrence != null && recurrence.recurringId in actions.recurrenceIds) {
            val pending = recurrence.status == RecurringMonthStatus.PENDING
            if (pending && recurrence.plan.active) add(FinanceAction(if (item.value.movement == Movement.INCOME) "Registrar recebimento" else "Registrar pagamento",
                { actions.recurring(recurrence, RecurringAction.CONFIRM) }, "confirm-recurring-${recurrence.recurringId}"))
            if (pending) add(FinanceAction("Alterar só este mês", { actions.recurring(recurrence, RecurringAction.EDIT_MONTH) }, "edit-month-${recurrence.recurringId}"))
            add(FinanceAction("Aplicar deste mês em diante", { actions.recurring(recurrence, RecurringAction.EDIT_FUTURE) }, "edit-recurring-${recurrence.recurringId}"))
            if (pending) {
                add(FinanceAction(if (recurrence.plan.active) "Pausar desta e futuras" else "Retomar desta e futuras", { actions.recurring(recurrence, RecurringAction.PAUSE) }, "active-recurring-${recurrence.recurringId}"))
                if (recurrence.plan.active) add(FinanceAction("Pular este mês", { actions.recurring(recurrence, RecurringAction.SKIP) }, "skip-${recurrence.recurringId}"))
            }
            if (pending && recurrence.plan.movement == Movement.EXPENSE) add(FinanceAction(if (recurrence.fixedExpense) "Retirar de despesas fixas" else "Considerar despesa fixa",
                { actions.recurring(recurrence, RecurringAction.FIXED) }, "fixed-${recurrence.recurringId}"))
            add(FinanceAction("Excluir recorrência", { actions.recurring(recurrence, RecurringAction.EXCLUDE) }, "exclude-recurring-${recurrence.recurringId}", destructive = true))
        }
        if (installment != null) {
            if (!hasPayment) add(FinanceAction("Registrar parcela", { actions.confirmInstallment(installment) }, "confirm-${installment.id}"))
            if (!hasPayment) add(FinanceAction("Editar parcelamento", { actions.purchase(installment.id, PurchaseManagementAction.EDIT) }, "edit-${installment.id}"))
            if (!hasPayment) {
                add(FinanceAction("Excluir esta parcela", { actions.purchase(installment.id, PurchaseManagementAction.EXCLUDE) }, "exclude-${installment.id}", destructive = true))
                add(FinanceAction("Cancelar parcelas em aberto", { actions.purchase(installment.id, PurchaseManagementAction.CANCEL_REMAINING) }, "cancel-${installment.id}", destructive = true))
            }
        }
    }
    val menuTag = when {
        recurrence != null -> "actions-recurring-${recurrence.recurringId}"
        installment != null -> "actions-${installment.id}"
        else -> "actions-record-${item.value.id}"
    }
    val detail = when {
        recurrence != null -> "Recorrente mensal" + if (recurrence.fixedExpense) " · Fixa" else ""
        installment != null -> "Parcela ${installment.number}/${installment.installmentCount}"
        else -> item.value.movement.label
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        ListItem(modifier = Modifier.fillMaxWidth().testTag("movement-${item.key}").clickable(enabled = !busy) {
            if (hasPayment) actions.editRecord(item.value)
            else if (recurrence != null) actions.recurring(recurrence, if (recurrence.status == RecurringMonthStatus.PENDING) RecurringAction.EDIT_MONTH else RecurringAction.EDIT_FUTURE)
            else if (installment != null) actions.confirmInstallment(installment)
        }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            leadingContent = { IconBadge(categoryIcon(item.value.category), movementColor(item.value.movement)) },
            headlineContent = { Text(description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
            supportingContent = { Column {
                Text("${item.status} · $detail", style = MaterialTheme.typography.labelSmall)
                Text("${displayDate} · ${privateDisplayText(item.value.bank, hidden)}", style = MaterialTheme.typography.bodySmall)
                if (largeText) CompositionLocalProvider(LocalContentColor provides movementColor(item.value.movement)) {
                    Amount(item.value.amountCents, hidden, size = 16)
                }
            } },
            trailingContent = { Column(horizontalAlignment = Alignment.End) {
                if (!largeText) CompositionLocalProvider(LocalContentColor provides movementColor(item.value.movement)) { Amount(item.value.amountCents, hidden, Modifier.widthIn(max = 132.dp), size = 16) }
                ContextualActions(description, menuTag, !busy, menu)
            } })
    }
}

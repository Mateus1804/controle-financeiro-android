package com.mateuswilliam.financas.ui

import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import com.mateuswilliam.financas.FinanceState
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun RecurringMovementDialog(model: FinanceViewModel, state: FinanceState, expected: RecurringMonth,
    action: RecurringAction, busy: Boolean, onDismiss: () -> Unit) {
    if (action == RecurringAction.EDIT_MONTH || action == RecurringAction.EDIT_FUTURE) {
        val onlyMonth = action == RecurringAction.EDIT_MONTH
        val configuration by produceState<Result<RecurringPlan?>?>(null, expected.recurringId, expected.month, action) {
            value = if (onlyMonth) Result.success(expected.plan) else try {
                Result.success(model.recurringConfiguration(expected.recurringId, expected.month))
            } catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) { Result.failure(exception) }
        }
        val plan = configuration?.getOrNull()
        if (plan != null) key(expected.recurringId, expected.month, action) {
            RecordEditor(null, null, busy, onDismiss, {}, null, null, state.accounts, state.settings.lastOrigin,
                model::addAccount, recurring = plan, hideAmounts = state.settings.hideAmounts,
                recurrenceScope = expected.month, recurrenceOnlyMonth = onlyMonth, recurringFixed = expected.fixedExpense,
                onSaveRecurring = { edited, fixed ->
                    if (onlyMonth) model.saveRecurringMonth(expected, edited, onDismiss)
                    else model.saveRecurringMovement(edited, expected.month, fixed, onDismiss)
                })
        } else AlertDialog(onDismissRequest = onDismiss,
            title = { DialogSystemBars(); Text("Recorrência mensal") },
            text = { Text(if (configuration == null) "Carregando a configuração…" else "Não foi possível abrir a recorrência. Feche e tente novamente.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Voltar") } })
        return
    }
    val skipping = action == RecurringAction.SKIP
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text(if (skipping) "Ignorar este mês?" else "Excluir recorrência?") },
        text = { Text(if (skipping) "Somente esta ocorrência será ignorada. Os outros meses e os pagamentos registrados serão mantidos."
            else "As previsões desta recorrência deixarão de aparecer. Os pagamentos já registrados serão mantidos no histórico.") },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag(if (skipping) "confirm-skip" else "confirm-exclude-recurring"), onClick = {
            if (skipping) { model.skipRecurringMonth(expected); onDismiss() }
            else state.recurring.find { it.id == expected.recurringId }?.let { model.excludeRecurringForecast(it, onDismiss) } ?: onDismiss()
        }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}

package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import java.time.YearMonth

internal fun LazyListScope.monthlyDashboardCards(
    navigation: ForecastNavigationState, editors: ForecastEditorsState,
    month: YearMonth, salarySummary: SalarySummary, paydayBudget: PaydayBudget?,
    monthComparison: MonthComparison, realized: List<Record>, pendingCount: Int,
    accountBalances: List<AccountBalance>, hasBalance: Boolean, hidden: Boolean, onNewSalary: () -> Unit,
) {
    item {
        val reviewLabel = if (pendingCount == 0) "Revisar notificações"
            else "Revisar notificações (${pendingCount})"
        FilledTonalButton({ navigation.tab = 5 }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dashboard-notification-review")) {
            AppIcon(R.drawable.ic_pending)
            Spacer(Modifier.width(8.dp))
            Text(reviewLabel)
        }
    }
    item { MonthPicker(month) { navigation.monthText = it.toString() } }
    item { MonthlyResultCard(salarySummary, hidden) { editors.salaryEditor = true } }
    item { AccountsOverview(accountBalances, hidden) { editors.accountsOpen = true } }
    item { PaydayBudgetCard(paydayBudget, hasBalance, hidden, partialBalance = accountBalances.any { !it.archived && it.balanceCents == null }, onAccounts = { editors.accountsOpen = true }) { onNewSalary() } }
    item { TextButton({ navigation.accountFilterKey = null; navigation.expenseDimension = null; navigation.recordCategory = null; navigation.tab = 4 }) { Text("Ver extrato do mês") } }
    item { ExpenseChart(realized, month, hidden) {
        navigation.accountFilterKey = null; navigation.expenseDimension = it.dimension; navigation.expenseKey = it.key; navigation.expenseLabel = it.label; navigation.recordCategory = null; navigation.tab = 4
    } }
    item { MonthComparisonCard(monthComparison, hidden) {
        navigation.accountFilterKey = null; navigation.expenseDimension = null; navigation.recordCategory = it; navigation.tab = 4
    } }
}

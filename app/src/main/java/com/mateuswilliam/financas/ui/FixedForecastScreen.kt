package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.YearMonth

@Composable
internal fun FixedForecastScreen(data: ForecastDataset, month: YearMonth, onMonth: (YearMonth) -> Unit, hidden: Boolean, busy: Boolean,
    onEdit: (ForecastLine) -> Unit, onEditFuture: (ForecastLine) -> Unit, onIgnore: (ForecastLine) -> Unit, onStop: (ForecastLine) -> Unit, onNew: () -> Unit, onTemplates: () -> Unit) {
    val lines = remember(data, month) { MonthlyForecasts.lines(data, month)
        .filter { it.kind == ForecastKind.EXPENSE && it.frequency != ForecastFrequency.ONCE } }
    LazyColumn(Modifier.testTag("fixed-screen"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Fixas e parcelas", "Previsões mensais. Não é necessário confirmar pagamentos.") }
        item { MonthPicker(month, onMonth) }
        item { Button(onNew, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Nova despesa fixa ou parcela") } }
        item { TextButton(onTemplates, Modifier.fillMaxWidth()) { Text("Usar modelo de gasto fixo") } }
        if (lines.isEmpty()) item { Text("Nenhuma despesa fixa ou parcela prevista para este mês.") }
        forecastLineItems(lines, hidden, busy, onEdit, onIgnore, onStop, onEditFuture)
    }
}


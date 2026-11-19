package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun ForecastEditorDialogs(
    editors: ForecastEditorsState, model: ForecastViewModel, snapshot: ForecastDataset?,
    period: BudgetPeriod, report: ForecastReport?, today: LocalDate, zone: ZoneId,
    busy: Boolean, hidden: Boolean, onEdit: (ForecastLine) -> Unit,
) {
    val editingPeriod = if (editors.scopeStart != null && editors.scopeEnd != null) BudgetPeriod(LocalDate.parse(editors.scopeStart), LocalDate.parse(editors.scopeEnd)) else period
    val editorReport = remember(snapshot, editingPeriod, editors.editor) {
        if (editors.editor) snapshot?.let { ForecastCalculator.report(it, editingPeriod) } else null
    }
    val selectedLine = editorReport?.lines?.firstOrNull {
        it.itemId == editors.editItem && it.occurrenceKey == editors.editKey &&
            it.reusedInstallmentStart?.toString() == editors.editInstallmentStart
    }
    if (editors.dueDates && report != null) AlertDialog(onDismissRequest = { editors.dueDates = false },
        title = { Text("Próximos vencimentos") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                val upcoming = report.lines.filter { it.included && !it.ignored && it.kind == ForecastKind.EXPENSE && !it.rule.cycleAnchored && it.date >= today }.sortedBy { it.date }
                if (upcoming.isEmpty()) item { Text("Sem vencimentos previstos restantes neste ciclo.") }
                upcoming.forEach { line -> item {
                    TextButton({ editors.dueDates = false; onEdit(line) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.weight(1f)) { Text(privateDisplayText(line.title, hidden)); Text(line.date.toString()) }
                        Amount(line.amountCents, hidden, size = 16)
                    }
                } }
            }
        }, confirmButton = { TextButton({ editors.dueDates = false }) { Text("Fechar") } })
    val selectedRule = snapshot?.rules?.filter { it.itemId == editors.editItem }?.maxByOrNull { it.effectiveFrom }
    if (editors.editor && snapshot != null) key(editors.editItem, editors.editKey, editors.editInstallmentStart,
        editors.initialKind, editors.onlyOccurrence, editors.scopeStart) {
        ForecastEditor(rule = selectedLine?.rule ?: selectedRule, line = selectedLine, period = editingPeriod, busy = busy, hidden = hidden,
            onDismiss = { editors.editor = false; editors.forecastSeed = null },
            onSaveRule = { updated ->
                val selectedKey = selectedLine?.takeIf { selected ->
                    selected.reusedInstallmentStart == null && selected.rule.frequency == updated.frequency &&
                        selected.rule.cycleAnchored == updated.cycleAnchored
                }?.occurrenceKey
                model.saveRule(updated, selectedKey) { editors.editor = false; editors.forecastSeed = null }
            },
            onSaveOverride = { model.saveOverride(it) { editors.editor = false } },
            initialKind = editors.initialKind, initialFrequency = editors.initialFrequency, initialOnlyOccurrence = editors.onlyOccurrence,
            initialTitle = editors.forecastSeed?.description.orEmpty(), initialCategory = editors.forecastSeed?.category,
            initialAmountCents = editors.forecastSeed?.amountCents,
            initialStartDate = editors.forecastSeed?.let { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() },
            initialAccount = editors.forecastSeed?.bank.orEmpty(),
            knownStatements = snapshot.rules.map { it.statementKey }.filter { it.isNotBlank() }.distinct(),
            existingOverride = selectedLine?.takeIf { it.reusedInstallmentStart == null }?.let { selected ->
                snapshot.overrides.firstOrNull { it.itemId == selected.itemId && it.occurrenceKey == selected.occurrenceKey }
            })
    }
    if (editors.simulationEditor) ForecastEditor(editors.simulation, null, period, busy, hidden, { editors.simulationEditor = false },
        { editors.simulation = it; editors.simulationEditor = false }, {}, initialFrequency = ForecastFrequency.INSTALLMENTS,
        initialTitle = "Nova parcela · simulação")
}

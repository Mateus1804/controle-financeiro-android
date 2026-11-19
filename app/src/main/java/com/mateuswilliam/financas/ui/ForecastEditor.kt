package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

@Composable
internal fun ForecastEditor(
    rule: ForecastRule?, line: ForecastLine?, period: BudgetPeriod, busy: Boolean, hidden: Boolean,
    onDismiss: () -> Unit, onSaveRule: (ForecastRule) -> Unit,
    onSaveOverride: (ForecastOverride) -> Unit,
    initialKind: ForecastKind = ForecastKind.EXPENSE, initialTitle: String = "",
    initialCategory: Category? = null, initialFrequency: ForecastFrequency = ForecastFrequency.MONTHLY,
    knownStatements: List<String> = emptyList(),
    existingOverride: ForecastOverride? = null, initialOnlyOccurrence: Boolean = true,
    initialAmountCents: Long? = null, initialStartDate: LocalDate? = null, initialAccount: String = "",
) {
    val source = rule ?: line?.rule
    val canEditOccurrence = line != null && line.reusedInstallmentStart == null && source?.frequency != ForecastFrequency.ONCE
    val id = rememberSaveable { source?.itemId ?: UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(line?.title ?: source?.title ?: initialTitle) }
    var kind by rememberSaveable { mutableStateOf(source?.kind ?: initialKind) }
    var category by rememberSaveable { mutableStateOf(line?.category ?: source?.category ?: initialCategory ?: if (initialKind == ForecastKind.INCOME) Category.SALARY else Category.OTHER) }
    var frequency by rememberSaveable { mutableStateOf(source?.frequency ?: initialFrequency) }
    var group by rememberSaveable { mutableStateOf(source?.group ?: ForecastGroup.COMMITMENT) }
    var estimated by rememberSaveable { mutableStateOf(source?.estimated ?: false) }
    var undated by rememberSaveable { mutableStateOf(source?.cycleAnchored ?: false) }
    var date by rememberSaveable { mutableStateOf((line?.date ?: source?.startDate ?: initialStartDate ?: period.start).toString()) }
    var day by rememberSaveable { mutableIntStateOf(source?.dayOfMonth ?: LocalDate.parse(date).dayOfMonth) }
    var count by rememberSaveable { mutableStateOf(source?.installmentCount?.toString() ?: "") }
    var account by rememberSaveable { mutableStateOf(source?.account ?: initialAccount) }
    var statement by rememberSaveable { mutableStateOf(source?.statementKey.orEmpty()) }
    var statementTotal by rememberSaveable { mutableStateOf(source?.statementTotal ?: false) }
    var onlyOccurrence by rememberSaveable { mutableStateOf(initialOnlyOccurrence && canEditOccurrence) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var invalidMoney by rememberSaveable { mutableStateOf(false) }
    val scoped = onlyOccurrence && canEditOccurrence
    val occurrenceAmount = rememberTextFieldState(initialText = (line?.amountCents ?: source?.amountCents ?: initialAmountCents)?.let { MoneyInput.fromCents(abs(it)) }.orEmpty())
    val ruleAmount = rememberTextFieldState(initialText = (source?.amountCents ?: initialAmountCents)?.let { MoneyInput.fromCents(abs(it)) }.orEmpty())
    val amount = if (scoped) occurrenceAmount else ruleAmount
    val cents = if (invalidMoney) null else MoneyInput.cents(amount.text.toString())
    val start = if (undated && frequency == ForecastFrequency.MONTHLY) period.start else LocalDate.parse(date)
    val candidate = ForecastRule(id, if (line != null) period.start else start, title.trim(), kind,
        cents ?: 0, category, frequency,
        if (source != null && line != null) source!!.startDate else start, day,
        count.toIntOrNull() ?: 1, undated && frequency == ForecastFrequency.MONTHLY,
        group, estimated, source?.active ?: true, if (kind == ForecastKind.EXPENSE) statement.trim() else "",
        kind == ForecastKind.EXPENSE && statementTotal, account.trim())
    val error = when {
        cents == null || cents <= 0 -> "Informe um valor maior que zero."
        title.isBlank() -> "Informe uma descrição."
        !scoped && frequency == ForecastFrequency.INSTALLMENTS && count.toIntOrNull() !in 1..600 -> "Informe de 1 a 600 parcelas."
        scoped && start !in period -> "Escolha uma data dentro deste ciclo."
        !scoped -> ForecastValidation.error(candidate)
        else -> null
    }
    val context = LocalContext.current
    val datePickerTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text(if (source == null) "Nova previsão" else "Editar previsão") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Planeje o que espera receber ou gastar. Nenhum pagamento ou recebimento será registrado.")
            if (canEditOccurrence) {
                ChoiceField("Aplicar alteração", onlyOccurrence, listOf(true, false), { if (it) "Alterar só este mês" else "Aplicar deste mês em diante" }) { if (!busy) onlyOccurrence = it }
            }
            if (line?.reusedInstallmentStart != null) Text(
                "Para preservar a parcela antiga que usa o mesmo vínculo, as alterações aqui valem deste mês em diante.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (line != null && source?.frequency != ForecastFrequency.ONCE) Text(
                if (scoped) "Só esta ocorrência será alterada; os outros meses serão preservados."
                else "A nova regra vale a partir desta ocorrência. Os ajustes individuais feitos nos meses seguintes e o histórico anterior são preservados.", style = MaterialTheme.typography.bodySmall)
            PrivateValueGate(hidden && privateDisplayText(title, true) != title, "Mostrar descrição da previsão para edição") {
                OutlinedTextField(title, { title = it.take(200) }, enabled = !busy, label = { Text("Descrição") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("forecast-title"))
            }
            if (!scoped) ChoiceField("Tipo", kind, ForecastKind.entries, { if (it == ForecastKind.INCOME) "Receita" else "Despesa" }) {
                if (!busy) { kind = it; category = if (it == ForecastKind.INCOME) Category.SALARY else Category.OTHER }
            }
            ChoiceField("Categoria", category, categoriesFor(if (kind == ForecastKind.INCOME) Movement.INCOME else Movement.EXPENSE), { it.label }) { if (!busy) category = it }
            if (!scoped) ChoiceField("Frequência", frequency, ForecastFrequency.entries, {
                when (it) { ForecastFrequency.ONCE -> "Uma vez"; ForecastFrequency.MONTHLY -> "Mensal"; ForecastFrequency.INSTALLMENTS -> "Parcelada" }
            }) { if (!busy) frequency = it }
            PrivateValueGate(hidden, "Mostrar valor da previsão para edição") {
                MoneyAmountField(amount, attempted, !busy, label = if (!scoped && frequency == ForecastFrequency.INSTALLMENTS) "Valor total a parcelar (R$)" else "Valor previsto (R$)", tag = "forecast-amount", onInvalidInput = { invalidMoney = it })
                if (!scoped && frequency == ForecastFrequency.INSTALLMENTS) {
                    OutlinedTextField(count, { if (it.length <= 3 && it.all(Char::isDigit)) count = it }, enabled = !busy, label = { Text("Quantidade de parcelas (1 a 600)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("forecast-installment-count"))
                    val n = count.toIntOrNull()
                    if (n != null && n in 1..600 && cents != null && cents > 0) {
                        val minimum = cents / n
                        val remainder = cents % n
                        Text(if (remainder == 0L) "$n parcelas de ${Money.format(minimum)}" else "$remainder parcelas de ${Money.format(minimum + 1)} e ${n - remainder} de ${Money.format(minimum)}")
                        Text("Os centavos restantes ficam nas primeiras parcelas.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!scoped && kind == ForecastKind.EXPENSE) {
                ChoiceField("Grupo", group, ForecastGroup.entries, { if (it == ForecastGroup.COMMITMENT) "Compromisso" else "Consumo variável" }) { if (!busy) group = it }
                if (frequency == ForecastFrequency.MONTHLY) ForecastToggle("Sem data: estimativa para cada ciclo", undated, !busy) { undated = it }
            }
            if (!scoped) ForecastToggle("Valor estimado", estimated, !busy) { estimated = it }
            if (!undated || frequency != ForecastFrequency.MONTHLY) {
                OutlinedButton(onClick = { showFinanceDatePicker(context, datePickerTheme, LocalDate.parse(date)) { date = it.toString(); day = it.dayOfMonth } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Data: ${LocalDate.parse(date).format(financeDateFormat)}") }
                if (!scoped && frequency != ForecastFrequency.ONCE) ChoiceField("Dia habitual", day, (1..31).toList(), { "Dia $it" }) { if (!busy) day = it }
            }
            if (!scoped) FinanceDisclosure("Mais detalhes", "forecast-details") {
                PrivateValueGate(hidden && privateDisplayText(account, true) != account, "Mostrar conta da previsão para edição") {
                    OutlinedTextField(account, { account = it.take(80) }, enabled = !busy, label = { Text("Conta (opcional)") }, modifier = Modifier.fillMaxWidth())
                }
                if (kind == ForecastKind.EXPENSE) {
                    Text("Vincule apenas itens que pertencem à mesma fatura. Quando o total estiver previsto, os componentes não serão somados novamente.", style = MaterialTheme.typography.bodySmall)
                    PrivateValueGate(hidden && (privateDisplayText(statement, true) != statement || knownStatements.any { privateDisplayText(it, true) != it }), "Mostrar vínculo da fatura para edição") {
                        if (knownStatements.isNotEmpty()) ChoiceField("Fatura existente", statement, listOf("") + knownStatements.distinct(), { it.ifBlank { "Sem vínculo" } }) { if (!busy) statement = it }
                        OutlinedTextField(statement, { statement = it.take(120) }, enabled = !busy, label = { Text("Nome da fatura (opcional)") }, modifier = Modifier.fillMaxWidth())
                    }
                    ForecastToggle("Este valor é o total da fatura", statementTotal, !busy) { statementTotal = it }
                }
            }
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { FilledTonalButton(onClick = {
            attempted = true
            if (error == null) {
                if (scoped && line != null) onSaveOverride(existingOverride?.copy(
                    amountCents = candidate.amountCents, date = start, ignored = line.ignored, title = title.trim(), category = category,
                ) ?: ForecastOverride(id, line.occurrenceKey, candidate.amountCents, start, line.ignored, title = title.trim(), category = category))
                else {
                    val sameSchedule = source != null && frequency == source.frequency &&
                        candidate.cycleAnchored == source.cycleAnchored
                    val newStart = if (sameSchedule && line != null && frequency != ForecastFrequency.ONCE)
                        source?.startDate ?: start else start
                    onSaveRule(candidate.copy(startDate = newStart,
                        effectiveFrom = if (source?.frequency == ForecastFrequency.ONCE) source.effectiveFrom else candidate.effectiveFrom))
                }
            }
        }, enabled = !busy, modifier = Modifier.testTag("forecast-save")) { Text("Salvar previsão") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
internal fun ForecastToggle(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

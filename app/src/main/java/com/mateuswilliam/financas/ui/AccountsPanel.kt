package com.mateuswilliam.financas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
internal fun AccountsOverview(balances: List<AccountBalance>, hidden: Boolean, onOpen: () -> Unit) {
    val known = balances.filter { it.balanceCents != null }
    val unknownCount = balances.size - known.size
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (unknownCount > 0 && known.isNotEmpty()) "Saldo disponível conhecido (parcial)" else "Saldo disponível nas contas",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpen, modifier = Modifier.testTag("open-accounts")) { Text("Contas") }
            }
            if (known.isNotEmpty()) Amount(known.sumOf { it.balanceCents ?: 0 }, hidden, Modifier.testTag("accounts-total"), size = 26)
            else Text("Configure os saldos para saber quanto possui.", style = MaterialTheme.typography.bodyMedium)
            Text(if (unknownCount > 0) "$unknownCount conta(s) sem saldo configurado. O total ainda não inclui essas contas."
                else "Saldo atual, incluindo contas arquivadas. Previsões não alteram este valor.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsManager(balances: List<AccountBalance>, references: List<AccountBalanceReference>, hidden: Boolean,
    busy: Boolean, onDismiss: () -> Unit, onStatement: (AccountOption) -> Unit,
    onSave: (String, Long, Long, String, () -> Unit) -> Unit, onArchive: (String, Boolean) -> Unit) {
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveKey by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DialogSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Minhas contas", style = MaterialTheme.typography.headlineSmall)
            Text("Cadastre suas contas próprias e carteira. Limite de cartão não é saldo disponível.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { editingKey = "" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("new-account-balance")) {
                AppIcon(R.drawable.ic_add); Spacer(Modifier.width(8.dp)); Text("Adicionar conta e saldo")
            }
            balances.forEach { balance ->
                val account = balance.account
                val label = privateDisplayText(account.label, hidden)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        ListItem(modifier = Modifier.clickable(enabled = !busy) { onStatement(account) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            headlineContent = { Text(label + if (balance.archived) " · Arquivada" else "") },
                            supportingContent = { if (balance.balanceCents == null) Text("Saldo não configurado")
                                else Amount(balance.balanceCents!!, hidden, size = 20) },
                            trailingContent = { ContextualActions(label, "account-actions-${account.key}", !busy, listOf(
                                FinanceAction("Ver movimentações", { onStatement(account) }),
                                FinanceAction(if (balance.reference == null) "Configurar saldo" else "Corrigir saldo de referência", { editingKey = account.key }),
                                FinanceAction(if (balance.archived) "Reativar conta" else "Arquivar conta", {
                                    if (balance.archived) onArchive(account.key, false) else archiveKey = account.key
                                }),
                            )) })
                        if (balance.reference == null) TextButton(onClick = { editingKey = account.key }, enabled = !busy,
                            modifier = Modifier.padding(horizontal = 8.dp).testTag("configure-account-${account.key}")) { Text("Configurar saldo") }
                    }
                }
            }
            if (balances.isEmpty()) Text("A configuração é opcional. Você pode começar com suas movimentações e informar os saldos depois.")
        }
    }
    editingKey?.let { key ->
        val balance = balances.find { it.account.key == key }
        key(key) { AccountBalanceEditor(balance, references.filter { it.accountKey == key }, hidden, busy,
            onDismiss = { editingKey = null }, onSave = { name, cents, at, reason -> onSave(name, cents, at, reason) { editingKey = null } }) }
    }
    archiveKey?.let { key ->
        AlertDialog(onDismissRequest = { if (!busy) archiveKey = null }, title = { DialogSystemBars(); Text("Arquivar conta?") },
            text = { Text("A conta sai das opções de novos lançamentos. O saldo permanece no total consolidado e o histórico continua disponível. Você pode reativá-la depois.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onArchive(key, true); archiveKey = null }) { Text("Arquivar") } },
            dismissButton = { TextButton(onClick = { archiveKey = null }, enabled = !busy) { Text("Cancelar") } })
    }
}

@Composable
private fun AccountBalanceEditor(existing: AccountBalance?, history: List<AccountBalanceReference>, hidden: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onSave: (String, Long, Long, String) -> Unit) {
    val correction = history.isNotEmpty()
    var name by rememberSaveable { mutableStateOf(existing?.account?.label.orEmpty()) }
    val amount = rememberTextFieldState() // Empty deliberately: a correction must be entered explicitly.
    var negative by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var reason by rememberSaveable { mutableStateOf(if (correction) "" else "Saldo inicial") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    val cents = if (invalid) null else MoneyInput.cents(amount.text.toString())
    val error = when {
        AccountNames.clean(name).isBlank() -> "Informe o nome da conta."
        cents == null -> "Informe o saldo, inclusive se for zero."
        LocalDate.parse(date).isAfter(LocalDate.now()) -> "Use uma data de referência até hoje."
        reason.isBlank() -> "Informe o motivo da correção."
        else -> null
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text(if (correction) "Corrigir saldo" else "Configurar saldo da conta") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrivateValueGate(hidden, "Mostrar conta para edição") {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nome / identificação da conta") },
                    enabled = !busy && existing == null, modifier = Modifier.fillMaxWidth().testTag("balance-account-name"), singleLine = true)
            }
            existing?.balanceCents?.let { Text(if (hidden) "Saldo atual oculto" else "Saldo calculado atual: ${Money.format(it)}", style = MaterialTheme.typography.bodySmall) }
            PrivateValueGate(hidden, "Mostrar saldo para edição") {
                MoneyAmountField(amount, attempted, !busy, label = "Saldo no início da data (R$)", tag = "account-balance-input", allowZero = true, onInvalidInput = { invalid = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saldo negativo", Modifier.weight(1f))
                Switch(negative, { negative = it }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Saldo negativo" })
            }
            OutlinedButton(enabled = !busy, onClick = { showFinanceDatePicker(context, theme, LocalDate.parse(date)) { date = it.toString() } }, modifier = Modifier.fillMaxWidth().testTag("account-reference-date")) {
                Text("Referência: ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
            }
            Text("Informe o saldo no início desta data, antes das movimentações do dia. Lançamentos anteriores já estão incluídos nesse valor e não serão somados novamente. Saldo inicial e correções não são receitas nem despesas.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(reason, { reason = it.take(200) }, label = { Text(if (correction) "Motivo da correção" else "Observação") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("balance-reason"))
            if (history.isNotEmpty()) FinanceDisclosure("Histórico de referências", "balance-history") {
                history.sortedWith(compareByDescending<AccountBalanceReference> { it.effectiveAt }.thenByDescending { it.id }).forEach { ref ->
                    Text("${shortDate(ref.effectiveAt)} · ${if (hidden) "Valor oculto" else Money.format(ref.balanceCents)}", style = MaterialTheme.typography.bodySmall)
                    Text(privateDisplayText(ref.reason, hidden), style = MaterialTheme.typography.bodySmall)
                }
                Text("Uma nova referência não apaga as anteriores.", style = MaterialTheme.typography.bodySmall)
            }
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("save-account-balance"), onClick = {
            attempted = true
            if (error == null && cents != null) onSave(name, if (negative) -cents else cents,
                LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), reason.trim())
        }) { Text(if (busy) "Salvando…" else "Salvar saldo") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

internal data class FixedExpenseTemplate(val title: String, val category: Category)
internal val fixedExpenseTemplates = listOf(
    FixedExpenseTemplate("Aluguel", Category.HOME), FixedExpenseTemplate("Energia elétrica", Category.HOME),
    FixedExpenseTemplate("Água", Category.HOME), FixedExpenseTemplate("Internet", Category.SERVICES),
    FixedExpenseTemplate("Celular ou telefone", Category.SERVICES),
)

@Composable
internal fun OptionalSetup(onAccounts: () -> Unit, onTemplate: (FixedExpenseTemplate) -> Unit) {
    FinanceDisclosure("Configuração inicial (opcional)", "optional-finance-setup") {
        Text("Configure suas contas e escolha os gastos fixos que utiliza. Você preencherá os valores antes de salvar.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onAccounts, modifier = Modifier.fillMaxWidth()) { Text("Configurar minhas contas") }
        fixedExpenseTemplates.forEach { template ->
            TextButton(onClick = { onTemplate(template) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fixed-template-${template.title}")) {
                Text(template.title)
            }
        }
    }
}

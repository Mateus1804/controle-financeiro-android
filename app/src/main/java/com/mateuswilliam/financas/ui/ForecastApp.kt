package com.mateuswilliam.financas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.MonthlyPlanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ForecastApp(finance: FinanceViewModel, model: ForecastViewModel, resumeCount: Int,
    onLockChange: (Boolean) -> Unit, incomingPlanning: Boolean = false, consumePlanning: () -> Unit = {}) {
    val data by model.dataset.collectAsStateWithLifecycle()
    val settings by finance.preferences.state.collectAsStateWithLifecycle()
    val busyForecast by model.busy.collectAsStateWithLifecycle()
    val busyFinance by finance.busy.collectAsStateWithLifecycle()
    val forecastMessage by model.message.collectAsStateWithLifecycle()
    val financeMessage by finance.message.collectAsStateWithLifecycle()
    val loadError by model.loadError.collectAsStateWithLifecycle()
    val legacyIncome by model.legacyIncome.collectAsStateWithLifecycle()
    val oldState by finance.state.collectAsStateWithLifecycle()
    val staged by finance.stagedRestore.collectAsStateWithLifecycle()
    val busy = busyForecast || busyFinance
    val hidden = settings.hideAmounts
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var browsing by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var followingCurrent by rememberSaveable { mutableStateOf(true) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editItem by rememberSaveable { mutableStateOf<String?>(null) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialKind by rememberSaveable { mutableStateOf(ForecastKind.EXPENSE) }
    var template by rememberSaveable { mutableStateOf<String?>(null) }
    var legacyMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var templates by rememberSaveable { mutableStateOf(false) }
    var cycleEditor by rememberSaveable { mutableStateOf(false) }
    var reserveKind by rememberSaveable { mutableStateOf<ReserveEventKind?>(null) }
    var allocating by rememberSaveable { mutableStateOf(false) }
    var goalEditor by rememberSaveable { mutableStateOf(false) }
    var simulationEditor by rememberSaveable { mutableStateOf(false) }
    var simulation by remember { mutableStateOf<ForecastRule?>(null) }
    var backupDialog by rememberSaveable { mutableIntStateOf(0) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(resumeCount) {
        today = LocalDate.now()
        if (followingCurrent) browsing = today.toString()
        model.refresh()
    }
    LaunchedEffect(incomingPlanning) { if (incomingPlanning) {
        tab = 0; followingCurrent = true; browsing = LocalDate.now().toString(); consumePlanning()
    } }
    LaunchedEffect(forecastMessage) { forecastMessage?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null } }
    LaunchedEffect(financeMessage) { financeMessage?.let {
        if (it.startsWith("Dados restaurados") || it.startsWith("Dados apagados")) model.refresh()
        snackbar.showSnackbar(it); if (finance.message.value == it) finance.message.value = null
    } }
    val snapshot = data
    val period = remember(snapshot?.cycleRules, browsing) { CycleCalendar.periodContaining(LocalDate.parse(browsing), snapshot?.cycleRules.orEmpty()) }
    val reportResult = remember(snapshot, period) { snapshot?.let { runCatching { ForecastCalculator.report(it, period) } } }
    val report = reportResult?.getOrNull()
    val closed = period.endExclusive <= today
    val balance = report?.reserveBalanceCents
    val alerts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && report != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(uri) ?: error("Destino indisponível")
                out.use { it.write(forecastCsv(report).toByteArray(Charsets.UTF_8)) }
            } }.onSuccess { model.message.value = "Previsões do ciclo exportadas." }
                .onFailure { model.message.value = "Não foi possível exportar. Escolha outro destino." }
        }
    }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { finance.writeBackup(it) }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri.toString(); backupDialog = 2 }
    }
    fun newItem(kind: ForecastKind, title: String? = null) {
        editItem = null; editKey = null; legacyMonth = null; template = title; initialKind = kind; editor = true
    }
    fun edit(line: ForecastLine) { editItem = line.itemId; editKey = line.occurrenceKey; legacyMonth = null; template = null; editor = true }
    fun showReserve(kind: ReserveEventKind, allocation: Boolean = false) { allocating = allocation; reserveKind = kind }
    fun select(date: LocalDate) { followingCurrent = false; browsing = date.toString() }
    BackHandler(tab >= 3 && !editor) { tab = 0 }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column {
                if (tab <= 1) Row(Modifier.padding(horizontal = FinanceSpacing.page), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ newItem(ForecastKind.INCOME) }, enabled = !busy && snapshot != null, modifier = Modifier.weight(1f).testTag("forecast-add-income")) {
                        AppIcon(R.drawable.ic_income); Spacer(Modifier.width(6.dp)); Text("Receita")
                    }
                    Button({ newItem(ForecastKind.EXPENSE) }, enabled = !busy && snapshot != null, modifier = Modifier.weight(1f).testTag("forecast-add-expense")) {
                        AppIcon(R.drawable.ic_add); Spacer(Modifier.width(6.dp)); Text("Gasto")
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    listOf("Orçamento" to R.drawable.ic_overview, "Projeções" to R.drawable.ic_calendar, "Reserva" to R.drawable.ic_wallet).forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { AppIcon(selectedIcon(icon, tab == index)) }, label = { Text(label) })
                    }
                }
            }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FinanceSpacing.page, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tab >= 3) "Ajustes e histórico" else "Meu planejamento", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton({ finance.preferences.hideAmounts(!hidden) }) { AppIcon(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, description = if (hidden) "Mostrar valores" else "Ocultar valores") }
                    IconButton({ tab = if (tab == 3) 0 else 3 }, Modifier.testTag("forecast-settings")) { AppIcon(R.drawable.ic_settings, description = "Ajustes") }
                }
                when {
                    loadError != null -> { Text(loadError!!, Modifier.padding(FinanceSpacing.page)); Button({ model.refresh() }) { Text("Tentar novamente") } }
                    snapshot == null -> Text("Abrindo seu planejamento…", Modifier.padding(FinanceSpacing.page))
                    report == null -> Text("Não foi possível calcular este ciclo. Confira as previsões em seus dados e tente novamente.", Modifier.padding(FinanceSpacing.page))
                    tab == 3 -> SettingsScreen(settings, false, false, true, false, {}, {}, {}, {}, {},
                        onExport = { export.launch("previsoes-${period.start}.csv") }, onClear = { clearing = true }, onSimulate = {},
                        onAppearance = finance.preferences::setAppearance, busy = busy,
                        onBackup = { backupDialog = 1 }, onRestore = { backupImport.launch(arrayOf("*/*")) }, onPlanning = { tab = 1 },
                        onLockChange = onLockChange, onSecureScreen = finance.preferences::setSecureScreen,
                        onReminders = { finance.preferences.setReminders(it); if (it && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) alerts.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        plannerMode = true)
                    tab == 4 -> LazyColumn(Modifier.testTag("forecast-legacy-records"), contentPadding = PaddingValues(FinanceSpacing.page)) {
                        item { TextButton({ tab = 0 }) { Text("Voltar ao orçamento") }; Text("Registros anteriores", style = MaterialTheme.typography.titleLarge)
                            Text("Consulta do histórico preservado. As novas previsões são gerenciadas no orçamento.") }
                        oldState.records.forEach { record -> item(key = record.id) { RecordRow(record, hidden) } }
                    }
                    tab == 2 -> ForecastReserveScreen(snapshot, report, hidden, busy,
                        onSet = { showReserve(ReserveEventKind.SET) }, onAdd = { showReserve(ReserveEventKind.ADD) },
                        onWithdraw = { showReserve(ReserveEventKind.WITHDRAW) }, onGoal = { goalEditor = true })
                    tab == 1 -> ForecastProjectionScreen(snapshot, period, simulation, hidden, busy,
                        onSimulate = { simulationEditor = true }, onDiscard = { simulation = null },
                        onAdopt = { simulation?.let { value -> model.saveRule(value) { simulation = null } } }, onOpen = { select(it.start); tab = 0 },
                        legacyIncome = legacyIncome.filterNot { old -> snapshot.rules.any { it.legacySource == "monthly-reference:${old.month}" } },
                        onReuse = { old -> legacyMonth = old.month; editItem = null; editKey = null; template = null; editor = true })
                    else -> LazyColumn(Modifier.testTag("forecast-dashboard"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
                        item { ForecastCyclePicker(period, { select(period.start.minusDays(1)) }, { select(period.endExclusive) }, { followingCurrent = true; browsing = today.toString() }, { cycleEditor = true }) }
                        item { Text(if (closed) "Ciclo encerrado · valores estimados" else "Planejamento do ciclo · valores estimados", style = MaterialTheme.typography.labelMedium) }
                        item { ForecastSummary(report, hidden) }
                        if (report.incomeCents <= 0) item {
                            EmptyCard("Informe sua renda prevista", "Cadastre o salário uma única vez. O mesmo valor alimenta o orçamento e todas as projeções.")
                            TextButton({ newItem(ForecastKind.INCOME) }, modifier = Modifier.testTag("forecast-setup-salary")) { Text("Cadastrar salário mensal") }
                        }
                        if (closed && report.unallocatedCents > 0) item {
                            OutlinedButton({ if (balance == null) showReserve(ReserveEventKind.SET) else showReserve(ReserveEventKind.ADD, true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("forecast-allocate-surplus")) {
                                Text(if (balance == null) "Informar reserva para destinar a sobra" else "Guardar parte da sobra")
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Previsões do período", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton({ templates = true }) { Text("Modelos") }
                            }
                            Text("O vencimento organiza as datas; seu orçamento não depende de confirmar pagamentos.", style = MaterialTheme.typography.bodySmall)
                        }
                        forecastLineItems(report.lines, hidden, busy, ::edit, model::ignore, model::stop)
                        item { FinanceDisclosure("Histórico e dados anteriores", "forecast-legacy-data") {
                            Text("As fontes antigas foram preservadas. Recorrências, parcelas e registros vinculados foram aproveitados uma única vez. Rendas de referência antigas precisam de revisão antes de virar previsão.", style = MaterialTheme.typography.bodySmall)
                            TextButton({ tab = 1 }) { Text("Revisar rendas de referência antigas") }
                            TextButton({ tab = 4 }) { Text("Consultar lançamentos anteriores") }
                        } }
                    }
                }
            }
        }
    }
    val selectedLine = report?.lines?.firstOrNull { it.itemId == editItem && it.occurrenceKey == editKey }
    val selectedRule = snapshot?.rules?.filter { it.itemId == editItem }?.maxByOrNull { it.effectiveFrom }
    val oldIncome = legacyIncome.firstOrNull { it.month == legacyMonth }
    val referenceRule = oldIncome?.let { ForecastRule("monthly-reference:${it.month}", period.start, "Renda anterior · revisar", ForecastKind.INCOME,
        it.incomeCents, Category.OTHER_INCOME, ForecastFrequency.ONCE, period.start, legacySource = "monthly-reference:${it.month}") }
    if (editor && snapshot != null) key(editItem, editKey, template, legacyMonth, initialKind) {
        ForecastEditor(rule = selectedLine?.rule ?: selectedRule ?: referenceRule, line = selectedLine, period = period, busy = busy, hidden = hidden,
            onDismiss = { editor = false }, onSaveRule = { model.saveRule(it) { editor = false } }, onSaveOverride = { model.saveOverride(it) { editor = false } },
            initialKind = initialKind, initialTitle = template.orEmpty(), initialCategory = fixedExpenseTemplates.firstOrNull { it.title == template }?.category,
            knownStatements = snapshot.rules.map { it.statementKey }.filter { it.isNotBlank() }.distinct(),
            existingOverride = snapshot.overrides.firstOrNull { it.itemId == selectedLine?.itemId && it.occurrenceKey == selectedLine?.occurrenceKey })
    }
    if (templates) AlertDialog(onDismissRequest = { templates = false }, title = { DialogSystemBars(); Text("Modelos opcionais") },
        text = { Column { fixedExpenseTemplates.forEach { item -> TextButton({ templates = false; newItem(ForecastKind.EXPENSE, item.title) }, Modifier.fillMaxWidth()) { Text(item.title) } }
            Text("Você informará o valor antes de salvar.", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton({ templates = false }) { Text("Fechar") } })
    if (cycleEditor && snapshot != null) CycleDayEditor(snapshot.cycleRules.lastOrNull { it.effectiveStart <= today }?.day ?: 1, busy,
        preview = { day ->
            val rules = CycleConfiguration.rulesAfterChange(day, today, snapshot)
            CycleCalendar.periodContaining(maxOf(today, rules.last().effectiveStart), rules)
        },
        onDismiss = { cycleEditor = false }, onSave = { model.setCycleDay(it) { cycleEditor = false; followingCurrent = true; browsing = today.toString() } },
        firstConfiguration = snapshot.cycleRules.isEmpty() && snapshot.reserveEvents.none { it.cycleStart != null })
    reserveKind?.let { kind -> key(kind, allocating) {
        ReserveEditor(kind, balance, busy, hidden, { reserveKind = null }, { cents, reason, cycle -> model.saveReserve(kind, cents, reason, cycle) { reserveKind = null } },
            allocationLimitCents = if (allocating) report?.unallocatedCents else null, cycle = if (allocating) period else null, allocateInitially = allocating)
    } }
    if (goalEditor) ReserveGoalEditor(snapshot?.reserveGoalCents, busy, hidden, { goalEditor = false }) { value -> model.saveReserveGoal(value) { goalEditor = false } }
    if (simulationEditor) ForecastEditor(simulation, null, period, busy, hidden, { simulationEditor = false },
        { simulation = it; simulationEditor = false }, {}, initialFrequency = ForecastFrequency.INSTALLMENTS,
        initialTitle = "Nova parcela · simulação")
    if (backupDialog != 0) BackupPasswordDialog(backupDialog == 1, busy, { backupDialog = 0; restoreUri = null }) { secret ->
        val creating = backupDialog == 1; backupDialog = 0
        if (creating) finance.createBackup(secret) { backupExport.launch("planejamento-${today}.cfbackup") }
        else { val uri = restoreUri; restoreUri = null; if (uri != null) finance.stageBackup(uri.toUri(), secret) else secret.fill('\u0000') }
    }
    staged?.let { BackupPreviewDialog(it, busy, finance::cancelRestore, finance::restoreBackup) }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { DialogSystemBars(); Text("Apagar os dados?") },
        text = { Text("Previsões, reserva, histórico e preferências serão apagados deste aparelho. Backups exportados permanecem no destino escolhido.") },
        confirmButton = { TextButton({ clearing = false; finance.clear() }, enabled = !busy) { Text("Apagar") } },
        dismissButton = { TextButton({ clearing = false }) { Text("Cancelar") } })
}

private fun forecastCsv(report: ForecastReport): String {
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    val rows = listOf(listOf("Início do ciclo", "Fim do ciclo", "Descrição", "Data prevista", "Tipo", "Valor previsto BRL", "Incluído no orçamento")) + report.lines.map {
        listOf(report.period.start.toString(), report.period.endExclusive.minusDays(1).toString(), it.title, it.date.toString(),
            if (it.kind == ForecastKind.INCOME) "Receita prevista" else "Gasto previsto", Money.editable(it.amountCents), if (it.included) "Sim" else "Não")
    }
    return "\uFEFF" + rows.joinToString("\r\n") { it.joinToString(";", transform = ::cell) } + "\r\n"
}

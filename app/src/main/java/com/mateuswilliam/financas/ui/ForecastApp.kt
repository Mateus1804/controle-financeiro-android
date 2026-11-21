package com.mateuswilliam.financas.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.mateuswilliam.financas.notifications.BankNotificationListener
import com.mateuswilliam.financas.notifications.hasCaptureAccess
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.core.Record
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun ForecastApp(finance: FinanceViewModel, model: ForecastViewModel, resumeCount: Int, ui: ForecastUiStateViewModel,
    onLockChange: (Boolean) -> Unit, incomingPlanning: Boolean = false, consumePlanning: () -> Unit = {}, incomingDraft: String? = null, consumeDraft: () -> Unit = {}) {
    val data by model.dataset.collectAsStateWithLifecycle()
    val settings by finance.preferences.state.collectAsStateWithLifecycle()
    val busyForecast by model.busy.collectAsStateWithLifecycle()
    val busyFinance by finance.busy.collectAsStateWithLifecycle()
    val forecastMessage by model.message.collectAsStateWithLifecycle()
    val csvDestination by model.csvDestination.collectAsStateWithLifecycle()
    val backupDestination by finance.backupDestination.collectAsStateWithLifecycle()
    val backupCleanupPending by finance.backupCleanupPending.collectAsStateWithLifecycle()
    val financialDataRevision by finance.financialDataRevision.collectAsStateWithLifecycle()
    val financeMessage by finance.message.collectAsStateWithLifecycle()
    val loadError by model.loadError.collectAsStateWithLifecycle()
    val financeState by finance.state.collectAsStateWithLifecycle()
    val staged by finance.stagedRestore.collectAsStateWithLifecycle()
    val busy = busyForecast || busyFinance
    val hidden = settings.hideAmounts
    val navigation = ui.navigation
    val destination = navigation.destination
    val editors = ui.editors
    val review = ui.review
    var permissionVersion by remember { mutableIntStateOf(0) }
    val connected by finance.connected.collectAsStateWithLifecycle()
    val captureError by finance.captureError.collectAsStateWithLifecycle()
    val apps by finance.apps.collectAsStateWithLifecycle()
    val loadingApps by finance.loadingApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val merchantStore = (context.applicationContext as FinanceApplication).merchantCategories
    val hasAccess = remember(resumeCount, connected) { hasCaptureAccess(context) }
    val canAlert = remember(resumeCount, permissionVersion) { NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val captureActive = settings.consented && settings.enabled && settings.packages.isNotEmpty() && hasAccess && connected
    val snapshot = data
    val cycleRules = snapshot?.cycleRules.orEmpty()
    val zone = ZoneId.systemDefault()
    val asOf = financeState.asOf
    val today = remember(asOf, zone) { Instant.ofEpochMilli(asOf).atZone(zone).toLocalDate() }
    val currentMonth = YearMonth.from(CycleCalendar.periodContaining(today, cycleRules).start)
    val month = if (navigation.followingCurrent) currentMonth else YearMonth.parse(navigation.monthText)
    val period = remember(month, cycleRules) { FinancialPeriods.month(month, cycleRules) }
    val realized = remember(financeState.records, asOf) { financeState.records.filter { it.occurredAt <= asOf } }
    val duplicateIndex = remember(financeState.manualRecords) { DuplicateCandidates(financeState.manualRecords) }
    val balanceCutoff = minOf(asOf, period.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    val historicBalance = balanceCutoff < asOf
    val snapshotBalances = financeState.accountBalances
    val historicalBalances = if (historicBalance && financeState.loaded &&
        (destination == RendlyDestination.MONTH || destination == RendlyDestination.RESERVE)) {
        val references = financeState.balanceReferences
        val records = financeState.records
        calculateInBackground(listOf(snapshotBalances, references, records, balanceCutoff)) {
            val options = snapshotBalances.map { it.account }
            val archived = snapshotBalances.filter { it.archived }.map { it.account.key }.toSet()
            AccountBalances.calculate(options, archived, references, records, balanceCutoff)
        }.value
    } else null
    val periodBalances = when {
        !financeState.loaded -> null
        historicBalance -> historicalBalances?.getOrNull()
        else -> snapshotBalances
    }
    val balancesStatus = when {
        historicalBalances?.isFailure == true -> "Não foi possível calcular os saldos do período."
        periodBalances == null -> "Consultando os saldos do período…"
        else -> null
    }
    val currentFunds = remember(financeState.accountBalances) { AccountFunds.of(financeState.accountBalances) }
    val readyData = snapshot?.takeIf { financeState.loaded && loadError == null }
    val records = financeState.records
    val paydayResult = if (destination == RendlyDestination.PLANNER && readyData != null && currentFunds.operating.hasBalance) {
        val available = currentFunds.operating.cents
        calculateInBackground(listOf(readyData, records, available, asOf, zone)) {
            PaydayBudgets.fromForecasts(available, readyData, records, zone, asOf)
        }.value
    } else null
    val paydayStatus = when {
        !financeState.loaded -> "Consultando os saldos…"
        paydayResult?.isFailure == true -> "Não foi possível calcular o orçamento até o próximo salário."
        currentFunds.operating.hasBalance && paydayResult == null -> "Calculando o orçamento até o próximo salário…"
        else -> null
    }
    fun openActual(kind: Movement = Movement.EXPENSE, recordId: String? = null,
        frequency: ForecastFrequency = ForecastFrequency.ONCE, category: Category? = null, description: String = "") {
        review.preset = null
        review.actualKind = kind
        review.initialFrequency = frequency
        review.initialCategory = category
        review.initialDescription = description
        review.editingRecordId = recordId
        review.editingDraftId = null
        review.draftRevision = null
        review.recordEditor = true
    }
    // A fila da sessão é fixada ao abrir a primeira pendência; "Revisar próxima" continua nela.
    fun rememberMerchantCategory(merchantKey: String, suggested: Category, confirmed: Category) {
        when (val change = MerchantCategoryPolicy.change(suggested, confirmed, merchantStore.get(merchantKey))) {
            is MerchantCategoryPolicy.Change.Remember -> merchantStore.remember(merchantKey, change.category)
            MerchantCategoryPolicy.Change.Forget -> merchantStore.forget(merchantKey)
            MerchantCategoryPolicy.Change.None -> Unit
        }
    }
    fun openReview(draft: com.mateuswilliam.financas.data.DraftEntity, continuing: Boolean = false) {
        review.preset = null
        review.initialFrequency = ForecastFrequency.ONCE
        review.initialCategory = null
        review.initialDescription = ""
        if (!continuing) review.reviewIds = ReviewQueue.snapshot(financeState.pending.map { it.id }, draft.id)
        review.reviewAfter = null
        review.editingDraftId = draft.id; review.editingRecordId = null; review.draftRevision = draft.revision; review.recordEditor = true
    }
    LaunchedEffect(incomingDraft, financeState.loaded, financeState.pending, busy, review.recordEditor, editors.editor, editors.backupDialog, staged, settings.namePromptSeen) {
        if (incomingDraft != null && settings.namePromptSeen && financeState.loaded && !busy && !review.recordEditor && !editors.editor && editors.backupDialog == 0 && staged == null) {
            financeState.pending.firstOrNull { it.id == incomingDraft }?.let { navigation.destination = RendlyDestination.REVIEW; openReview(it) }
            consumeDraft()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(resumeCount, financialDataRevision) {
        model.refresh()
    }
    LaunchedEffect(incomingPlanning) { if (incomingPlanning) {
        navigation.destination = RendlyDestination.PLANNER; navigation.followingCurrent = true; consumePlanning()
    } }
    LaunchedEffect(forecastMessage) { forecastMessage?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null } }
    LaunchedEffect(financeMessage) { financeMessage?.let {
        snackbar.showSnackbar(it); if (finance.message.value == it) finance.message.value = null
    } }
    LaunchedEffect(backupCleanupPending) {
        if (backupCleanupPending) snackbar.showSnackbar("Uma cópia temporária do backup aguarda limpeza. Confira em Ajustes.")
    }
    if (!settings.namePromptSeen) DisplayNameDialog(settings.displayName,
        finance.preferences::dismissNamePrompt, finance.preferences::setDisplayName, firstRun = true)
    val centralResult = when (destination) {
        RendlyDestination.MONTH, RendlyDestination.PLANNER,
        RendlyDestination.COMMITMENT, RendlyDestination.SETTINGS -> readyData?.let { availableData ->
            calculateInBackground(listOf(availableData, period, records, asOf, zone)) {
                CentralFinance.summarize(records, availableData, period, zone, asOf)
            }.value
        }
        else -> null
    }
    val central = centralResult?.getOrNull()
    val report = central?.forecast
    val forecastStatus = when {
        loadError != null -> loadError
        centralResult?.isFailure == true -> "Não foi possível calcular as previsões e os agendamentos deste período."
        report == null || !financeState.loaded -> "Carregando previsões e agendamentos…"
        else -> null
    }
    val upcomingDue = if (destination == RendlyDestination.PLANNER || editors.dueDates) remember(report, today) {
        report?.lines.orEmpty().filter {
            it.included && !it.ignored && it.kind == ForecastKind.EXPENSE && !it.rule.cycleAnchored && it.date >= today
        }.sortedBy { it.date }
    } else emptyList()
    val closed = period.endExclusive <= today
    val alerts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionVersion++ }
    fun requestCaptureAccess() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { finance.message.value = "Abra o acesso às notificações nas configurações do Android." }
    }
    fun requestAlerts() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            alerts.launch(Manifest.permission.POST_NOTIFICATIONS)
        else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            .onFailure { finance.message.value = "Abra as configurações de notificações do aplicativo no Android." }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        model.writeCsv(uri)
    }
    LaunchedEffect(csvDestination) {
        val name = model.takeCsvDestination() ?: return@LaunchedEffect
        runCatching { export.launch(name) }.onFailure {
            model.writeCsv(null)
            model.message.value = "Não foi possível abrir o seletor de arquivos."
        }
    }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { finance.writeBackup(it) }
    LaunchedEffect(backupDestination) {
        val name = finance.takeBackupDestination() ?: return@LaunchedEffect
        runCatching { backupExport.launch(name) }.onFailure {
            finance.writeBackup(null)
            finance.message.value = "Não foi possível abrir o seletor de arquivos."
        }
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { editors.restoreUri = uri.toString(); editors.backupDialog = 2 }
    }
    fun rememberScope(chosen: BudgetPeriod) {
        editors.scopeStart = chosen.start.toString()
        editors.scopeEnd = chosen.endExclusive.toString()
    }
    fun newForecast(seed: Record, frequency: ForecastFrequency) {
        val date = Instant.ofEpochMilli(seed.occurredAt).atZone(zone).toLocalDate()
        rememberScope(CycleCalendar.periodContaining(date, cycleRules))
        editors.forecastSeed = seed
        editors.initialFrequency = frequency
        editors.initialKind = if (seed.movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE
        editors.editItem = null
        editors.editKey = null
        editors.editInstallmentStart = null
        editors.editor = true
    }
    fun edit(line: ForecastLine, onlyOccurrence: Boolean = true) {
        editors.forecastSeed = null
        rememberScope(CycleCalendar.periodContaining(line.date, cycleRules))
        editors.onlyOccurrence = onlyOccurrence
        editors.editItem = line.itemId
        editors.editKey = line.occurrenceKey
        editors.editInstallmentStart = line.reusedInstallmentStart?.toString()
        editors.editor = true
    }
    fun selectMonth(value: YearMonth) {
        navigation.monthText = value.toString()
        navigation.followingCurrent = false
    }
    fun reserveTransfer(withdraw: Boolean) {
        val reserve = AccountFunds.RESERVE_NAME
        val origin = financeState.accountBalances.firstOrNull { !it.archived && it.account.key != AccountFunds.RESERVE_KEY }?.account?.label
        if (origin == null) {
            finance.message.value = "Cadastre uma conta de uso para transferir dinheiro entre ela e a reserva."
            editors.accountsOpen = true
            return
        }
        openActual(Movement.TRANSFER)
        review.preset = Record(java.util.UUID.randomUUID().toString(), 0, Movement.TRANSFER, PaymentMethod.UNKNOWN,
            Category.OTHER, if (withdraw) reserve else origin, if (withdraw) "Resgate da reserva" else "Guardar na reserva",
            System.currentTimeMillis(), if (withdraw) origin else reserve)
    }
    var adding by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    BackHandler(navigation.destination != RendlyDestination.MONTH && !editors.editor && !review.recordEditor) { navigation.destination = navigation.destination.parent }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (navigation.destination == RendlyDestination.MONTH && financeState.loadError == null) FloatingActionButton({ adding = true }, Modifier.testTag("add-movement")) { AppIcon(R.drawable.ic_add, description = "Novo lançamento") } },
        bottomBar = { ForecastNavigationBar(navigation.destination.primaryTab) { navigation.destination = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FinanceSpacing.page, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (navigation.destination.primaryTab != navigation.destination) IconButton({ navigation.destination = navigation.destination.parent }) { AppIcon(R.drawable.ic_chevron_left, description = "Voltar") }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(32.dp))
                            Text("Rendly", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(if (settings.displayName.isBlank()) "Olá! 👋🏼" else "Olá, ${settings.displayName}! 👋🏼",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("rendly-greeting"), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton({ finance.preferences.hideAmounts(!hidden) }, Modifier.size(48.dp)) { AppIcon(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, description = if (hidden) "Mostrar valores" else "Ocultar valores") }
                }
                when {
                    financeState.loadError != null && destination != RendlyDestination.MORE && destination != RendlyDestination.SETTINGS -> {
                        Column(Modifier.padding(FinanceSpacing.page).testTag("finance-load-error")) {
                            Text(financeState.loadError.orEmpty())
                            Button({ finance.refreshBalances(); model.refresh() }) { Text("Tentar novamente") }
                        }
                    }
                    navigation.destination == RendlyDestination.REVIEW -> Column {
                        TextButton({ navigation.destination = RendlyDestination.SETTINGS }, Modifier.padding(horizontal = FinanceSpacing.page)) { Text("Configurar captura") }
                        Box(Modifier.weight(1f)) {
                            PendingScreen(financeState.pending, hidden, { openReview(it) }, { id -> finance.dismiss(id) { review.reviewIds = ReviewQueue.snapshot(financeState.pending.map { it.id }, id); review.reviewAfter = id } }, busy, captureActive)
                        }
                    }
                    navigation.destination == RendlyDestination.MONTH -> MonthLedgerScreen(realized, month, ::selectMonth, hidden,
                        navigation.recordCategory, { navigation.recordCategory = it; navigation.expenseDimension = null },
                        financeState.accountBalances.map { it.account }, navigation.accountFilterKey, { navigation.accountFilterKey = it },
                        { openActual(recordId = it.id) }, navigation.expenseDimension?.let { ExpenseFilter(it, navigation.expenseKey, navigation.expenseLabel) },
                        { navigation.expenseDimension = null }, accountTotal = periodBalances?.let { AccountFunds.of(it).wealth }, onAccounts = { editors.accountsOpen = true },
                        period = period, accountBalances = periodBalances, balanceReferences = financeState.balanceReferences,
                        onBalanceReference = { ref ->
                            editors.balanceEditorName = financeState.accountBalances.firstOrNull { it.account.key == ref.accountKey }?.account?.label
                            editors.balanceEditorDate = java.time.Instant.ofEpochMilli(ref.effectiveAt).atZone(zone).toLocalDate().toString()
                        }, forecastLines = report?.lines.orEmpty(), onForecast = { edit(it) }, onCycle = { editors.cycleEditor = true },
                        onIgnoreForecast = model::ignore, onStopForecast = model::stop, onEditFutureForecast = { edit(it, false) }, busy = busy,
                        scheduledRecords = central?.scheduled?.records.orEmpty(),
                        reserveNeedsReference = snapshot?.reserveEvents?.isNotEmpty() == true && !currentFunds.reserve.hasBalance,
                        today = today, forecastStatus = forecastStatus, balanceStatus = balancesStatus,
                        onRetryForecast = if (loadError != null) model::refresh else null)
                    navigation.destination == RendlyDestination.GRAPHS -> {
                        val monthComparison = remember(realized, period, cycleRules, today, zone) {
                            MonthComparisons.compare(realized, period, FinancialPeriods.previous(period, cycleRules), today, zone)
                        }
                        GraphsScreen(realized, month, ::selectMonth, monthComparison, hidden,
                        onFilter = { navigation.accountFilterKey = null; navigation.recordCategory = null; navigation.expenseDimension = it.dimension; navigation.expenseKey = it.key; navigation.expenseLabel = it.label; navigation.destination = RendlyDestination.MONTH },
                        onCategory = { navigation.accountFilterKey = null; navigation.expenseDimension = null; navigation.recordCategory = it; navigation.destination = RendlyDestination.MONTH }, period = period)
                    }
                    navigation.destination == RendlyDestination.MORE -> MoreScreen { navigation.destination = it }
                    navigation.destination == RendlyDestination.SETTINGS -> SettingsScreen(settings, connected, hasAccess, canAlert, captureError,
                        onToggle = { enabled -> if (enabled && !settings.consented) editors.disclosure = true else finance.preferences.setEnabled(enabled) },
                        onApps = { if (!settings.consented) editors.disclosure = true else { editors.appPicker = true; finance.loadApps() } },
                        onAccess = ::requestCaptureAccess,
                        onReconnect = { NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java)) },
                        onAlerts = ::requestAlerts,
                        onExport = report?.takeIf { financeState.loaded }?.let { selected -> { model.prepareCsv(selected) } },
                        onClear = { editors.clearing = true }, onSimulate = finance::simulate,
                        onAppearance = finance.preferences::setAppearance, busy = busy,
                        onBackup = { editors.backupDialog = 1 }, onRestore = { backupImport.launch(arrayOf("*/*")) }, onPlanning = { navigation.destination = RendlyDestination.PROJECTION },
                        onLockChange = onLockChange, onSecureScreen = finance.preferences::setSecureScreen,
                        onReminders = { finance.preferences.setReminders(it); if (it && !canAlert) requestAlerts() },
                        onDisplayName = finance.preferences::setDisplayName,
                        onMerchantCategories = { editors.merchantCategoriesOpen = true },
                        onAccounts = { editors.accountsOpen = true }, onReview = { navigation.destination = RendlyDestination.REVIEW },
                        backupCleanupPending = backupCleanupPending, onRetryBackupCleanup = finance::retryBackupCleanup)
                    loadError != null -> { Text(loadError!!, Modifier.padding(FinanceSpacing.page)); Button({ model.refresh() }) { Text("Tentar novamente") } }
                    snapshot == null -> Text("Abrindo seu planejamento…", Modifier.padding(FinanceSpacing.page))
                    navigation.destination == RendlyDestination.RESERVE -> {
                        if (periodBalances == null) Text(balancesStatus.orEmpty(), Modifier.padding(FinanceSpacing.page))
                        else ReserveAccountScreen(snapshot, periodBalances, realized, period, hidden, busy,
                            onSet = { editors.balanceEditorName = AccountFunds.RESERVE_NAME; editors.balanceEditorDate = null },
                            onAdd = { reserveTransfer(false) }, onWithdraw = { reserveTransfer(true) }, onGoal = { editors.goalEditor = true },
                            currentReserve = snapshotBalances.firstOrNull { it.account.key == AccountFunds.RESERVE_KEY },
                            asOf = asOf)
                    }
                    navigation.destination == RendlyDestination.PROJECTION -> ForecastProjectionScreen(snapshot, period, editors.simulation, hidden, busy,
                        onSimulate = { editors.simulationEditor = true }, onDiscard = { editors.simulation = null },
                        onAdopt = { editors.simulation?.let { value -> model.saveRule(value) { editors.simulation = null } } },
                        onOpen = { selectMonth(YearMonth.from(it.start)); navigation.destination = RendlyDestination.PLANNER },
                        records = financeState.records, asOf = asOf)
                    navigation.destination == RendlyDestination.FIXED -> {
                        val result = if (financeState.loaded) calculateInBackground(listOf(snapshot, records, period)) {
                            PendingForecasts(snapshot, records).lines(period, includeCovered = true)
                        }.value else null
                        val status = when {
                            result?.isFailure == true -> "Não foi possível calcular as previsões deste período."
                            result == null -> "Carregando previsões…"
                            else -> null
                        }
                        FixedForecastScreen(result?.getOrNull().orEmpty(), month, ::selectMonth, hidden, busy,
                            { edit(it) }, { edit(it, false) }, model::ignore, model::stop,
                            { navigation.destination = RendlyDestination.MONTH; openActual(Movement.EXPENSE, frequency = ForecastFrequency.MONTHLY) },
                            { editors.templates = true }, period, status)
                    }
                    navigation.destination == RendlyDestination.COMMITMENT -> {
                        if (central != null) CommitmentScreen(central, hidden)
                        else Text(forecastStatus.orEmpty(), Modifier.padding(FinanceSpacing.page))
                    }
                    else -> LazyColumn(Modifier.testTag("forecast-dashboard"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
                        item { SectionTitle("Planejamento", "Registrado e previsto no mesmo controle, sem contar duas vezes.") }
                        item { PaydayBudgetCard(paydayResult?.getOrNull(), currentFunds.operating.hasBalance, hidden, partialBalance = currentFunds.operating.partial,
                            onAccounts = { editors.accountsOpen = true }, onSalary = {
                                navigation.destination = RendlyDestination.MONTH
                                openActual(Movement.INCOME, frequency = ForecastFrequency.MONTHLY, category = Category.SALARY)
                            }, calculationStatus = paydayStatus) }
                        item { ForecastCyclePicker(period, { selectMonth(month.minusMonths(1)) }, { selectMonth(month.plusMonths(1)) },
                            { navigation.followingCurrent = true }, { editors.cycleEditor = true }) }
                        item { Text(if (closed) "Ciclo encerrado · valores estimados" else "Planejamento do ciclo · valores estimados", style = MaterialTheme.typography.labelMedium) }
                        item { TextButton({ navigation.destination = RendlyDestination.PROJECTION }, Modifier.fillMaxWidth()) { Text("Projetar próximos ciclos") } }
                        item { TextButton({ editors.dueDates = true },
                            enabled = upcomingDue.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            AppIcon(R.drawable.ic_chevron_right); Spacer(Modifier.width(8.dp)); Text("Próximos vencimentos · consultar previsão")
                        } }
                        item {
                            if (central != null) CentralProjectionCard(central, hidden)
                            else Text(forecastStatus.orEmpty())
                        }
                        item { OutlinedButton({ navigation.destination = RendlyDestination.MONTH }, Modifier.fillMaxWidth().testTag("planning-open-month")) { Text("Gerenciar lançamentos na aba Mês") } }

                    }
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Novo lançamento") }, text = {
        Column { listOf(Movement.EXPENSE, Movement.INCOME, Movement.TRANSFER, Movement.REFUND).forEach { kind ->
            TextButton({ adding = false; openActual(kind) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag(if (kind == Movement.INCOME) "add-realized-income" else if (kind == Movement.EXPENSE) "add-realized-expense" else "add-${kind.name}")) {
                Text(if (kind == Movement.TRANSFER) "Transferência entre minhas contas" else kind.label)
            }
        }
            TextButton({ adding = false; editors.balanceEditorName = ""; editors.balanceEditorDate = null }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-initial-balance")) { Text("Saldo inicial") }
        }
    }, confirmButton = { TextButton({ adding = false }) { Text("Cancelar") } })
    if (editors.merchantCategoriesOpen) MerchantCategoriesDialog(merchantStore, hidden) { editors.merchantCategoriesOpen = false }
    if (editors.accountsOpen) AccountsManager(financeState.accountBalances, financeState.balanceReferences, hidden, busy,
        onDismiss = { editors.accountsOpen = false },
        onStatement = { account ->
            navigation.accountFilterKey = account.key; navigation.recordCategory = null; navigation.expenseDimension = null
            editors.accountsOpen = false; navigation.destination = RendlyDestination.MONTH
        }, onSave = finance::configureAccountBalance, onArchive = finance::setAccountArchived)
    val draft = financeState.pending.firstOrNull { it.id == review.editingDraftId }
    val merchant = remember(draft?.id, draft?.revision) {
        draft?.takeIf { it.movement == Movement.EXPENSE }?.let { MerchantIdentity.identify(it.sourceTitle, it.sourceBody) }
    }
    val record = financeState.records.firstOrNull { it.id == review.editingRecordId }
    if (review.recordEditor && (review.editingDraftId == null || draft != null) && (review.editingRecordId == null || record != null)) key(review.editingDraftId, review.editingRecordId, review.actualKind) {
        RecordEditor(draft, record, busy, preset = review.preset, onDismiss = { review.recordEditor = false },
            onSave = { value, merchantKey -> finance.save(value, draft?.id, editing = record != null, expectedRevision = review.draftRevision) {
                review.recordEditor = false
                if (draft != null) {
                    review.reviewAfter = draft.id
                    if (value.movement == Movement.EXPENSE && merchantKey != null && merchantKey == merchant?.key) {
                        rememberMerchantCategory(merchantKey, draft.category, value.category)
                    }
                }
            } },
            onIgnore = draft?.let { { finance.dismiss(it.id) { review.recordEditor = false; review.reviewAfter = it.id } } },
            onDelete = record?.let { { finance.delete(it.id) } },
            accounts = financeState.accounts, lastOrigin = settings.lastOrigin, onAddAccount = finance::addAccount,
            initialMovement = review.actualKind, initialFrequency = review.initialFrequency,
            initialCategory = review.initialCategory, initialDescription = review.initialDescription,
            hideAmounts = hidden, duplicateIndex = duplicateIndex,
            balances = financeState.accountBalances, balancesAsOf = asOf, history = financeState.records, forecastData = snapshot,
            rememberedCategory = merchant?.let { merchantStore.get(it.key) }, merchant = merchant,
            notificationChanged = draft != null && draft.revision != review.draftRevision,
            onAcceptUpdate = { review.draftRevision = draft?.revision },
            realizedOnly = true, onOpenForecast = if (draft == null && record == null && snapshot != null) { { seed, frequency ->
                review.recordEditor = false
                newForecast(seed, frequency)
            } } else null)
    }
    review.reviewAfter?.takeIf { !review.recordEditor && navigation.destination == RendlyDestination.REVIEW }?.let { done ->
        val pendingIds = financeState.pending.map { it.id }.toSet()
        val next = ReviewQueue.next(review.reviewIds, done, pendingIds)?.let { id -> financeState.pending.firstOrNull { it.id == id } }
            ?: financeState.pending.firstOrNull { it.id != done }
        AlertDialog(onDismissRequest = { review.reviewAfter = null }, title = { DialogSystemBars(); Text("Sugestão tratada") },
            text = { Text(if (next != null) "Há mais notificações para revisar. Cada lançamento continua exigindo a sua confirmação."
                else "Não há mais notificações pendentes para revisar.") },
            confirmButton = { if (next != null) TextButton({ openReview(next, continuing = true) }, Modifier.testTag("review-next")) { Text("Revisar próxima") }
                else TextButton({ review.reviewAfter = null }, Modifier.testTag("review-empty-done")) { Text("Concluir") } },
            dismissButton = { if (next != null) TextButton({ review.reviewAfter = null }) { Text("Agora não") } })
    }
    LaunchedEffect(financeState.loaded, draft, record, review.editingDraftId, review.editingRecordId) {
        if (financeState.loaded && ((review.editingDraftId != null && draft == null) || (review.editingRecordId != null && record == null))) review.recordEditor = false
    }
    if (editors.disclosure) DisclosureDialog({ editors.disclosure = false }) {
        finance.preferences.acceptDisclosure(); editors.disclosure = false; editors.appPicker = true; finance.loadApps()
    }
    if (editors.appPicker) AppPickerDialog(apps, loadingApps, settings.packages, { editors.appPicker = false }) {
        finance.preferences.selectPackages(it); editors.appPicker = false
    }
    ForecastEditorDialogs(editors, model, snapshot, period, upcomingDue, zone, busy, hidden) { edit(it) }
    if (editors.templates) AlertDialog(onDismissRequest = { editors.templates = false }, title = { DialogSystemBars(); Text("Modelos opcionais") },
        text = { Column { fixedExpenseTemplates.forEach { item -> TextButton({
            editors.templates = false
            navigation.destination = RendlyDestination.MONTH
            openActual(Movement.EXPENSE, frequency = ForecastFrequency.MONTHLY, category = item.category, description = item.title)
        }, Modifier.fillMaxWidth()) { Text(item.title) } }
            Text("Você informará o valor antes de salvar.", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton({ editors.templates = false }) { Text("Fechar") } })
    if (editors.cycleEditor && snapshot != null) {
        val linkedKeys = remember(records) { records.mapNotNull { it.forecastOccurrenceKey } }
        val currentDay = snapshot.cycleRules.lastOrNull { it.effectiveStart <= today }?.day ?: 1
        fun proposed(day: Int) = CycleConfiguration.rulesAfterChange(day, today, snapshot, linkedKeys)

        CycleDayEditor(currentDay, busy,
            preview = { day ->
                val rules = proposed(day)
                CycleCalendar.periodContaining(maxOf(today, rules.last().effectiveStart), rules)
            },
            onDismiss = { editors.cycleEditor = false },
            onSave = { model.setCycleDay(it) { editors.cycleEditor = false; navigation.followingCurrent = true } },
            firstConfiguration = proposed(currentDay).last().effectiveStart <= today)
    }
    editors.balanceEditorName?.let { name ->
        val existing = financeState.accountBalances.firstOrNull { it.account.key == AccountNames.key(name) }
        AccountBalanceEditor(existing, financeState.balanceReferences.filter { it.accountKey == AccountNames.key(name) }, hidden, busy,
            onDismiss = { editors.balanceEditorName = null },
            onSave = { label, cents, at, reason -> finance.configureAccountBalance(label, cents, at, reason) { editors.balanceEditorName = null } },
            initialName = name, initialDate = editors.balanceEditorDate?.let(LocalDate::parse) ?: today,
            knownBalances = financeState.accountBalances, allReferences = financeState.balanceReferences,
            lockAccountName = name.isNotBlank())
    }
    if (editors.goalEditor) ReserveGoalEditor(snapshot?.reserveGoalCents, busy, hidden, { editors.goalEditor = false }) { value -> model.saveReserveGoal(value) { editors.goalEditor = false } }
    if (editors.backupDialog != 0) BackupPasswordDialog(editors.backupDialog == 1, busy, { editors.backupDialog = 0; editors.restoreUri = null }) { secret ->
        val creating = editors.backupDialog == 1; editors.backupDialog = 0
        if (creating) finance.createBackup(secret, "planejamento-${today}.cfbackup")
        else { val uri = editors.restoreUri; editors.restoreUri = null; if (uri != null) finance.stageBackup(uri.toUri(), secret) else secret.fill('\u0000') }
    }
    staged?.let { BackupPreviewDialog(it, busy, finance::cancelRestore, finance::restoreBackup) }
    if (editors.clearing) AlertDialog(onDismissRequest = { editors.clearing = false }, title = { DialogSystemBars(); Text("Apagar os dados?") },
        text = { Text("Previsões, reserva, histórico e preferências serão apagados deste aparelho. Backups exportados permanecem no destino escolhido.") },
        confirmButton = { TextButton({ editors.clearing = false; finance.clear() }, enabled = !busy) { Text("Apagar") } },
        dismissButton = { TextButton({ editors.clearing = false }) { Text("Cancelar") } })
}

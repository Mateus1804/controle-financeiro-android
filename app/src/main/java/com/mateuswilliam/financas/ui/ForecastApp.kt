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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val financeMessage by finance.message.collectAsStateWithLifecycle()
    val loadError by model.loadError.collectAsStateWithLifecycle()
    val financeState by finance.state.collectAsStateWithLifecycle()
    val staged by finance.stagedRestore.collectAsStateWithLifecycle()
    val busy = busyForecast || busyFinance
    val hidden = settings.hideAmounts
    val navigation = ui.navigation
    val editors = ui.editors
    val review = ui.review
    var today by remember { mutableStateOf(LocalDate.now()) }
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
    val currentMonth = YearMonth.from(CycleCalendar.periodContaining(today, cycleRules).start)
    val month = if (navigation.followingCurrent) currentMonth else YearMonth.parse(navigation.monthText)
    val period = remember(month, cycleRules) { FinancialPeriods.month(month, cycleRules) }
    val zone = ZoneId.systemDefault()
    val asOf = remember(resumeCount, financeState.records) { System.currentTimeMillis() }
    val realized = remember(financeState.records, asOf) { financeState.records.filter { it.occurredAt <= asOf } }
    val duplicateIndex = remember(financeState.manualRecords) { DuplicateCandidates(financeState.manualRecords) }
    val monthComparison = remember(realized, period, cycleRules, today, zone) {
        MonthComparisons.compare(realized, period, FinancialPeriods.previous(period, cycleRules), today, zone)
    }
    val periodBalances = remember(financeState.accountBalances, financeState.balanceReferences, realized, period, asOf, zone) {
        AccountBalances.calculate(financeState.accountBalances.map { it.account },
            financeState.accountBalances.filter { it.archived }.map { it.account.key }.toSet(),
            financeState.balanceReferences, realized,
            minOf(asOf, period.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1))
    }
    val currentFunds = remember(financeState.accountBalances) { AccountFunds.of(financeState.accountBalances) }
    val paydayBudget = remember(snapshot, financeState.records, currentFunds.operating, asOf, zone) {
        runCatching {
            val todayDate = Instant.ofEpochMilli(asOf).atZone(zone).toLocalDate()
            val lines = PaydayBudgets.upcomingLines(snapshot ?: ForecastDataset(), financeState.records, zone, asOf)
            PaydayBudgets.nextPayday(lines, emptyList(), todayDate)?.let { payday ->
                PaydayBudgets.compute(currentFunds.operating.cents, lines, todayDate, payday)
            }
        }.getOrNull()
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
    fun rememberMerchantCategory(source: com.mateuswilliam.financas.data.DraftEntity, confirmed: Category) {
        val found = source.takeIf { it.movement == Movement.EXPENSE }?.let { MerchantIdentity.identify(it.sourceTitle, it.sourceBody) } ?: return
        when (val change = MerchantCategoryPolicy.change(source.category, confirmed, merchantStore.get(found.key))) {
            is MerchantCategoryPolicy.Change.Remember -> merchantStore.remember(found.key, change.category)
            MerchantCategoryPolicy.Change.Forget -> merchantStore.forget(found.key)
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
            financeState.pending.firstOrNull { it.id == incomingDraft }?.let { navigation.tab = 5; openReview(it) }
            consumeDraft()
        }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(resumeCount) {
        today = LocalDate.now()
        model.refresh()
    }
    LaunchedEffect(incomingPlanning) { if (incomingPlanning) {
        navigation.tab = 7; navigation.followingCurrent = true; consumePlanning()
    } }
    LaunchedEffect(forecastMessage) { forecastMessage?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null } }
    LaunchedEffect(financeMessage) { financeMessage?.let {
        if (it.startsWith("Dados restaurados") || it.startsWith("Dados apagados")) model.refresh()
        snackbar.showSnackbar(it); if (finance.message.value == it) finance.message.value = null
    } }
    if (!settings.namePromptSeen) DisplayNameDialog(settings.displayName,
        finance.preferences::dismissNamePrompt, finance.preferences::setDisplayName, firstRun = true)
    val central = remember(snapshot, period, financeState.records, asOf) {
        snapshot?.let { runCatching { CentralFinance.summarize(financeState.records, it, period, zone, asOf) }.getOrNull() }
    }
    val report = central?.forecast
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
        editors.editor = true
    }
    fun edit(line: ForecastLine, onlyOccurrence: Boolean = true) {
        editors.forecastSeed = null
        rememberScope(CycleCalendar.periodContaining(line.date, cycleRules))
        editors.onlyOccurrence = onlyOccurrence
        editors.editItem = line.itemId
        editors.editKey = line.occurrenceKey
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
    fun parentTab() = when (navigation.tab) { 5 -> 6; 6, 7, 8, 9, 10 -> 3; 11 -> 7; else -> 0 }
    var adding by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    BackHandler(navigation.tab != 0 && !editors.editor && !review.recordEditor) { navigation.tab = parentTab() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (navigation.tab == 0) FloatingActionButton({ adding = true }, Modifier.testTag("add-movement")) { AppIcon(R.drawable.ic_add, description = "Novo lançamento") } },
        bottomBar = { ForecastNavigationBar(if (navigation.tab <= 3) navigation.tab else 3) { navigation.tab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FinanceSpacing.page, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (navigation.tab > 3) IconButton({ navigation.tab = parentTab() }) { AppIcon(R.drawable.ic_chevron_left, description = "Voltar") }
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
                    navigation.tab == 5 -> Column {
                        TextButton({ navigation.tab = 6 }, Modifier.padding(horizontal = FinanceSpacing.page)) { Text("Configurar captura") }
                        Box(Modifier.weight(1f)) {
                            PendingScreen(financeState.pending, hidden, { openReview(it) }, { id -> finance.dismiss(id) { review.reviewIds = ReviewQueue.snapshot(financeState.pending.map { it.id }, id); review.reviewAfter = id } }, busy, captureActive)
                        }
                    }
                    navigation.tab == 0 -> MonthLedgerScreen(realized, month, ::selectMonth, hidden,
                        navigation.recordCategory, { navigation.recordCategory = it; navigation.expenseDimension = null },
                        financeState.accountBalances.map { it.account }, navigation.accountFilterKey, { navigation.accountFilterKey = it },
                        { openActual(recordId = it.id) }, navigation.expenseDimension?.let { ExpenseFilter(it, navigation.expenseKey, navigation.expenseLabel) },
                        { navigation.expenseDimension = null }, accountTotal = AccountFunds.of(periodBalances).wealth, onAccounts = { editors.accountsOpen = true },
                        period = period, accountBalances = periodBalances, balanceReferences = financeState.balanceReferences,
                        onBalanceReference = { ref ->
                            editors.balanceEditorName = financeState.accountBalances.firstOrNull { it.account.key == ref.accountKey }?.account?.label
                            editors.balanceEditorDate = java.time.Instant.ofEpochMilli(ref.effectiveAt).atZone(zone).toLocalDate().toString()
                        }, forecastLines = report?.lines.orEmpty(), onForecast = { edit(it) }, onCycle = { editors.cycleEditor = true },
                        onIgnoreForecast = model::ignore, onStopForecast = model::stop, onEditFutureForecast = { edit(it, false) }, busy = busy,
                        scheduledRecords = central?.scheduled?.records.orEmpty(),
                        reserveNeedsReference = snapshot?.reserveEvents?.isNotEmpty() == true && !currentFunds.reserve.hasBalance)
                    navigation.tab == 1 -> GraphsScreen(realized, month, ::selectMonth, monthComparison, hidden,
                        onFilter = { navigation.accountFilterKey = null; navigation.recordCategory = null; navigation.expenseDimension = it.dimension; navigation.expenseKey = it.key; navigation.expenseLabel = it.label; navigation.tab = 0 },
                        onCategory = { navigation.accountFilterKey = null; navigation.expenseDimension = null; navigation.recordCategory = it; navigation.tab = 0 }, period = period)
                    navigation.tab == 3 -> MoreScreen { navigation.tab = it }
                    navigation.tab == 6 -> SettingsScreen(settings, connected, hasAccess, canAlert, captureError,
                        onToggle = { enabled -> if (enabled && !settings.consented) editors.disclosure = true else finance.preferences.setEnabled(enabled) },
                        onApps = { if (!settings.consented) editors.disclosure = true else { editors.appPicker = true; finance.loadApps() } },
                        onAccess = ::requestCaptureAccess,
                        onReconnect = { NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java)) },
                        onAlerts = ::requestAlerts,
                        onExport = { export.launch("previsoes-${period.start}.csv") }, onClear = { editors.clearing = true }, onSimulate = finance::simulate,
                        onAppearance = finance.preferences::setAppearance, busy = busy,
                        onBackup = { editors.backupDialog = 1 }, onRestore = { backupImport.launch(arrayOf("*/*")) }, onPlanning = { navigation.tab = 11 },
                        onLockChange = onLockChange, onSecureScreen = finance.preferences::setSecureScreen,
                        onReminders = { finance.preferences.setReminders(it); if (it && !canAlert) requestAlerts() },
                        plannerMode = true, showCapture = true, onDisplayName = finance.preferences::setDisplayName,
                        onMerchantCategories = { editors.merchantCategoriesOpen = true },
                        onAccounts = { editors.accountsOpen = true }, onReview = { navigation.tab = 5 })
                    loadError != null -> { Text(loadError!!, Modifier.padding(FinanceSpacing.page)); Button({ model.refresh() }) { Text("Tentar novamente") } }
                    snapshot == null -> Text("Abrindo seu planejamento…", Modifier.padding(FinanceSpacing.page))
                    central == null || report == null -> Text("Não foi possível calcular este ciclo. Confira as previsões em seus dados e tente novamente.", Modifier.padding(FinanceSpacing.page))
                    navigation.tab == 8 -> ReserveAccountScreen(snapshot, periodBalances, realized, period, hidden, busy,
                        onSet = { editors.balanceEditorName = AccountFunds.RESERVE_NAME; editors.balanceEditorDate = null },
                        onAdd = { reserveTransfer(false) }, onWithdraw = { reserveTransfer(true) }, onGoal = { editors.goalEditor = true },
                        currentReserve = financeState.accountBalances.firstOrNull { it.account.key == AccountFunds.RESERVE_KEY })
                    navigation.tab == 11 -> ForecastProjectionScreen(snapshot, period, editors.simulation, hidden, busy,
                        onSimulate = { editors.simulationEditor = true }, onDiscard = { editors.simulation = null },
                        onAdopt = { editors.simulation?.let { value -> model.saveRule(value) { editors.simulation = null } } },
                        onOpen = { selectMonth(YearMonth.from(it.start)); navigation.tab = 7 },
                        records = financeState.records, asOf = asOf)
                    navigation.tab == 10 -> CommitmentScreen(central, hidden)
                    navigation.tab == 2 -> FixedForecastScreen(report.lines, month, ::selectMonth, hidden, busy,
                        { edit(it) }, { edit(it, false) }, model::ignore, model::stop,
                        { navigation.tab = 0; openActual(Movement.EXPENSE, frequency = ForecastFrequency.MONTHLY) },
                        { editors.templates = true }, period)
                    else -> LazyColumn(Modifier.testTag("forecast-dashboard"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
                        item { SectionTitle("Planejamento", "Registrado e previsto no mesmo controle, sem contar duas vezes.") }
                        item { PaydayBudgetCard(paydayBudget, currentFunds.operating.hasBalance, hidden, partialBalance = currentFunds.operating.partial,
                            onAccounts = { editors.accountsOpen = true }, onSalary = {
                                navigation.tab = 0
                                openActual(Movement.INCOME, frequency = ForecastFrequency.MONTHLY, category = Category.SALARY)
                            }) }
                        item { ForecastCyclePicker(period, { selectMonth(month.minusMonths(1)) }, { selectMonth(month.plusMonths(1)) },
                            { navigation.followingCurrent = true }, { editors.cycleEditor = true }) }
                        item { Text(if (closed) "Ciclo encerrado · valores estimados" else "Planejamento do ciclo · valores estimados", style = MaterialTheme.typography.labelMedium) }
                        item { TextButton({ navigation.tab = 11 }, Modifier.fillMaxWidth()) { Text("Projetar próximos ciclos") } }
                        item { TextButton({ editors.dueDates = true },
                            enabled = report.lines.any { it.included && !it.ignored && it.date >= today }, modifier = Modifier.fillMaxWidth()) {
                            AppIcon(R.drawable.ic_chevron_right); Spacer(Modifier.width(8.dp)); Text("Próximos vencimentos · consultar previsão")
                        } }
                        item { CentralProjectionCard(central, hidden) }
                        item { OutlinedButton({ navigation.tab = 0 }, Modifier.fillMaxWidth().testTag("planning-open-month")) { Text("Gerenciar lançamentos na aba Mês") } }

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
            editors.accountsOpen = false; navigation.tab = 0
        }, onSave = finance::configureAccountBalance, onArchive = finance::setAccountArchived)
    val draft = financeState.pending.firstOrNull { it.id == review.editingDraftId }
    val merchant = remember(draft?.id) { draft?.takeIf { it.movement == Movement.EXPENSE }?.let { MerchantIdentity.identify(it.sourceTitle, it.sourceBody) } }
    val record = financeState.records.firstOrNull { it.id == review.editingRecordId }
    if (review.recordEditor && (review.editingDraftId == null || draft != null) && (review.editingRecordId == null || record != null)) key(review.editingDraftId, review.editingRecordId, review.actualKind) {
        RecordEditor(draft, record, busy, preset = review.preset, onDismiss = { review.recordEditor = false },
            onSave = { value -> finance.save(value, draft?.id, editing = record != null, expectedRevision = review.draftRevision) {
                review.recordEditor = false
                if (draft != null) { review.reviewAfter = draft.id; rememberMerchantCategory(draft, value.category) }
            } },
            onIgnore = draft?.let { { finance.dismiss(it.id) { review.recordEditor = false; review.reviewAfter = it.id } } },
            onDelete = record?.let { { finance.delete(it.id) } },
            accounts = financeState.accounts, lastOrigin = settings.lastOrigin, onAddAccount = finance::addAccount,
            initialMovement = review.actualKind, initialFrequency = review.initialFrequency,
            initialCategory = review.initialCategory, initialDescription = review.initialDescription,
            hideAmounts = hidden, duplicateIndex = duplicateIndex,
            balances = financeState.accountBalances, history = financeState.records, forecastData = snapshot,
            rememberedCategory = merchant?.let { merchantStore.get(it.key) }, merchantLabel = merchant?.label,
            notificationChanged = draft != null && draft.revision != review.draftRevision,
            onAcceptUpdate = { review.draftRevision = draft?.revision },
            realizedOnly = true, onOpenForecast = if (draft == null && record == null && snapshot != null) { { seed, frequency ->
                review.recordEditor = false
                newForecast(seed, frequency)
            } } else null)
    }
    review.reviewAfter?.takeIf { !review.recordEditor && navigation.tab == 5 }?.let { done ->
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
    val editingPeriod = if (editors.scopeStart != null && editors.scopeEnd != null) BudgetPeriod(LocalDate.parse(editors.scopeStart), LocalDate.parse(editors.scopeEnd)) else period
    val editorReport = remember(snapshot, editingPeriod) { snapshot?.let { ForecastCalculator.report(it, editingPeriod) } }
    val selectedLine = editorReport?.lines?.firstOrNull { it.itemId == editors.editItem && it.occurrenceKey == editors.editKey }
    if (editors.dueDates && report != null) AlertDialog(onDismissRequest = { editors.dueDates = false },
        title = { Text("Próximos vencimentos") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                val upcoming = report.lines.filter { it.included && !it.ignored && it.kind == ForecastKind.EXPENSE && !it.rule.cycleAnchored && it.date >= today }.sortedBy { it.date }
                if (upcoming.isEmpty()) item { Text("Sem vencimentos previstos restantes neste ciclo.") }
                upcoming.forEach { line -> item {
                    TextButton({ editors.dueDates = false; edit(line) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.weight(1f)) { Text(privateDisplayText(line.title, hidden)); Text(line.date.toString()) }
                        Amount(line.amountCents, hidden, size = 16)
                    }
                } }
            }
        }, confirmButton = { TextButton({ editors.dueDates = false }) { Text("Fechar") } })
    val selectedRule = snapshot?.rules?.filter { it.itemId == editors.editItem }?.maxByOrNull { it.effectiveFrom }
    if (editors.editor && snapshot != null) key(editors.editItem, editors.editKey, editors.initialKind, editors.onlyOccurrence, editors.scopeStart) {
        ForecastEditor(rule = selectedLine?.rule ?: selectedRule, line = selectedLine, period = editingPeriod, busy = busy, hidden = hidden,
            onDismiss = { editors.editor = false; editors.forecastSeed = null },
            onSaveRule = { model.saveRule(it) { editors.editor = false; editors.forecastSeed = null } },
            onSaveOverride = { model.saveOverride(it) { editors.editor = false } },
            initialKind = editors.initialKind, initialFrequency = editors.initialFrequency, initialOnlyOccurrence = editors.onlyOccurrence,
            initialTitle = editors.forecastSeed?.description.orEmpty(), initialCategory = editors.forecastSeed?.category,
            initialAmountCents = editors.forecastSeed?.amountCents,
            initialStartDate = editors.forecastSeed?.let { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() },
            initialAccount = editors.forecastSeed?.bank.orEmpty(),
            knownStatements = snapshot.rules.map { it.statementKey }.filter { it.isNotBlank() }.distinct(),
            existingOverride = snapshot.overrides.firstOrNull { it.itemId == selectedLine?.itemId && it.occurrenceKey == selectedLine?.occurrenceKey })
    }
    if (editors.templates) AlertDialog(onDismissRequest = { editors.templates = false }, title = { DialogSystemBars(); Text("Modelos opcionais") },
        text = { Column { fixedExpenseTemplates.forEach { item -> TextButton({
            editors.templates = false
            navigation.tab = 0
            openActual(Movement.EXPENSE, frequency = ForecastFrequency.MONTHLY, category = item.category, description = item.title)
        }, Modifier.fillMaxWidth()) { Text(item.title) } }
            Text("Você informará o valor antes de salvar.", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton({ editors.templates = false }) { Text("Fechar") } })
    if (editors.cycleEditor && snapshot != null) CycleDayEditor(snapshot.cycleRules.lastOrNull { it.effectiveStart <= today }?.day ?: 1, busy,
        preview = { day ->
            val rules = CycleConfiguration.rulesAfterChange(day, today, snapshot)
            val nextCycle = CycleCalendar.periodContaining(maxOf(today, rules.last().effectiveStart), rules)
            FinancialPeriods.month(YearMonth.from(nextCycle.start), rules)
        },
        onDismiss = { editors.cycleEditor = false },
        onSave = { model.setCycleDay(it) { editors.cycleEditor = false; navigation.followingCurrent = true } },
        firstConfiguration = snapshot.cycleRules.isEmpty() && snapshot.reserveEvents.none { it.cycleStart != null })
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
    if (editors.simulationEditor) ForecastEditor(editors.simulation, null, period, busy, hidden, { editors.simulationEditor = false },
        { editors.simulation = it; editors.simulationEditor = false }, {}, initialFrequency = ForecastFrequency.INSTALLMENTS,
        initialTitle = "Nova parcela · simulação")
    if (editors.backupDialog != 0) BackupPasswordDialog(editors.backupDialog == 1, busy, { editors.backupDialog = 0; editors.restoreUri = null }) { secret ->
        val creating = editors.backupDialog == 1; editors.backupDialog = 0
        if (creating) finance.createBackup(secret) { backupExport.launch("planejamento-${today}.cfbackup") }
        else { val uri = editors.restoreUri; editors.restoreUri = null; if (uri != null) finance.stageBackup(uri.toUri(), secret) else secret.fill('\u0000') }
    }
    staged?.let { BackupPreviewDialog(it, busy, finance::cancelRestore, finance::restoreBackup) }
    if (editors.clearing) AlertDialog(onDismissRequest = { editors.clearing = false }, title = { DialogSystemBars(); Text("Apagar os dados?") },
        text = { Text("Previsões, reserva, histórico e preferências serão apagados deste aparelho. Backups exportados permanecem no destino escolhido.") },
        confirmButton = { TextButton({ editors.clearing = false; finance.clear() }, enabled = !busy) { Text("Apagar") } },
        dismissButton = { TextButton({ editors.clearing = false }) { Text("Cancelar") } })
}

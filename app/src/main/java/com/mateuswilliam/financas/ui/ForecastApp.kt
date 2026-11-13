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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun ForecastApp(finance: FinanceViewModel, model: ForecastViewModel, resumeCount: Int,
    onLockChange: (Boolean) -> Unit, incomingPlanning: Boolean = false, consumePlanning: () -> Unit = {}, incomingDraft: String? = null, consumeDraft: () -> Unit = {}) {
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
    var dueDates by rememberSaveable { mutableStateOf(false) }
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
    var recordEditor by rememberSaveable { mutableStateOf(false) }
    var editingRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftRevision by rememberSaveable { mutableStateOf<Int?>(null) }
    var actualKind by rememberSaveable { mutableStateOf(Movement.EXPENSE) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var salaryEditor by rememberSaveable { mutableStateOf(false) }
    var expenseDimension by rememberSaveable { mutableStateOf<ExpenseDimension?>(null) }
    var expenseKey by rememberSaveable { mutableStateOf("") }
    var expenseLabel by rememberSaveable { mutableStateOf("") }
    var recordCategory by rememberSaveable { mutableStateOf<Category?>(null) }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    var appPicker by rememberSaveable { mutableStateOf(false) }
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
    val month = YearMonth.parse(monthText)
    val zone = ZoneId.systemDefault()
    val asOf = remember(resumeCount, oldState.records) { System.currentTimeMillis() }
    val realized = remember(oldState.records, asOf) { oldState.records.filter { it.occurredAt <= asOf } }
    val duplicateIndex = remember(oldState.manualRecords) { DuplicateCandidates(oldState.manualRecords) }
    val salaryReference = key(monthText) {
        val reference by remember(month) { model.monthlyPlan(month) }.collectAsStateWithLifecycle(initialValue = null)
        reference
    }
    val salarySummary = remember(oldState.records, data, month, zone, asOf, salaryReference) {
        runCatching { SalarySummaries.month(oldState.records, data, month, zone, asOf, salaryReference?.incomeCents) }.getOrElse {
            SalarySummaries.month(oldState.records, null, month, zone, asOf, salaryReference?.incomeCents)
        }
    }
    val monthComparison = remember(realized, month, zone) { MonthComparisons.compare(realized, month, LocalDate.now(), zone) }
    val knownBalances = remember(oldState.accountBalances) { oldState.accountBalances.filter { !it.archived && it.balanceCents != null } }
    val paydayBudget = remember(data, oldState.records, oldState.recurring, knownBalances, asOf) {
        runCatching {
            val todayDate = LocalDate.now()
            val lines = data?.let { PaydayBudgets.upcomingLines(it, SalarySummaries.represented(oldState.records, it, asOf), todayDate) }.orEmpty()
            val recurringDates = oldState.recurring.filter { it.active && it.movement == Movement.INCOME && it.category == Category.SALARY }
                .mapNotNull { runCatching { LocalDate.parse(it.nextDate) }.getOrNull() }
            PaydayBudgets.nextPayday(lines, recurringDates, todayDate)?.let { payday ->
                PaydayBudgets.compute(knownBalances.sumOf { it.balanceCents ?: 0L }, lines, data?.reserveGoalCents ?: 0L, todayDate, payday)
            }
        }.getOrNull()
    }
    fun openActual(kind: Movement = Movement.EXPENSE, recordId: String? = null) {
        actualKind = kind; editingRecordId = recordId; editingDraftId = null; draftRevision = null; recordEditor = true
    }
    // A fila da sessão é fixada ao abrir a primeira pendência; "Revisar próxima" continua nela.
    var reviewIds by remember { mutableStateOf(emptyList<String>()) }
    var reviewAfter by rememberSaveable { mutableStateOf<String?>(null) }
    fun rememberMerchantCategory(source: com.mateuswilliam.financas.data.DraftEntity, confirmed: Category) {
        val found = source.takeIf { it.movement == Movement.EXPENSE }?.let { MerchantIdentity.identify(it.sourceTitle, it.sourceBody) } ?: return
        when (val change = MerchantCategoryPolicy.change(source.category, confirmed, merchantStore.get(found.key))) {
            is MerchantCategoryPolicy.Change.Remember -> merchantStore.remember(found.key, change.category)
            MerchantCategoryPolicy.Change.Forget -> merchantStore.forget(found.key)
            MerchantCategoryPolicy.Change.None -> Unit
        }
    }
    fun openReview(draft: com.mateuswilliam.financas.data.DraftEntity, continuing: Boolean = false) {
        if (!continuing) reviewIds = ReviewQueue.snapshot(oldState.pending.map { it.id }, draft.id)
        reviewAfter = null
        editingDraftId = draft.id; editingRecordId = null; draftRevision = draft.revision; recordEditor = true
    }
    LaunchedEffect(incomingDraft, oldState.loaded, oldState.pending, busy, recordEditor, editor, backupDialog, staged) {
        if (incomingDraft != null && oldState.loaded && !busy && !recordEditor && !editor && backupDialog == 0 && staged == null) {
            oldState.pending.firstOrNull { it.id == incomingDraft }?.let { tab = 5; openReview(it) }
            consumeDraft()
        }
    }
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
    if (!settings.namePromptSeen) DisplayNameDialog(settings.displayName,
        finance.preferences::dismissNamePrompt, finance.preferences::setDisplayName, firstRun = true)
    val snapshot = data
    val period = remember(snapshot?.cycleRules, browsing) { CycleCalendar.periodContaining(LocalDate.parse(browsing), snapshot?.cycleRules.orEmpty()) }
    val represented = remember(snapshot, oldState.records, asOf) {
        snapshot?.let { SalarySummaries.represented(oldState.records, it, asOf) }.orEmpty()
    }
    val reportResult = remember(snapshot, period, represented) {
        snapshot?.let { runCatching { ForecastCalculator.report(it, period, represented) } }
    }
    val report = reportResult?.getOrNull()
    val closed = period.endExclusive <= today
    val balance = report?.reserveBalanceCents
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
                if (tab == 0 || tab == 4) Row(Modifier.padding(horizontal = FinanceSpacing.page), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ openActual(Movement.INCOME) }, enabled = !busy && oldState.loaded, modifier = Modifier.weight(1f).testTag("add-realized-income")) {
                        AppIcon(R.drawable.ic_income); Spacer(Modifier.width(6.dp)); Text("Receita")
                    }
                    Button({ openActual() }, enabled = !busy && oldState.loaded, modifier = Modifier.weight(1f).testTag("add-realized-expense")) {
                        AppIcon(R.drawable.ic_add); Spacer(Modifier.width(6.dp)); Text("Despesa")
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    listOf("Orçamento" to R.drawable.ic_overview, "Projeções" to R.drawable.ic_chevron_right, "Reserva" to R.drawable.ic_wallet).forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { AppIcon(selectedIcon(icon, tab == index)) }, label = { Text(label) })
                    }
                }
            }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FinanceSpacing.page, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(32.dp))
                            Text("Rendly", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(if (tab >= 3) "Ajustes e histórico" else if (settings.displayName.isBlank()) "Olá! 👋🏼" else "Olá, ${settings.displayName}! 👋🏼",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("rendly-greeting"), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton({ finance.preferences.hideAmounts(!hidden) }) { AppIcon(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, description = if (hidden) "Mostrar valores" else "Ocultar valores") }
                    if (tab != 0) IconButton({ tab = if (tab == 5) 0 else 5 }, Modifier.testTag("open-notification-review")) {
                        BadgedBox(badge = { if (oldState.pending.isNotEmpty()) Badge { Text(oldState.pending.size.toString()) } }) {
                            AppIcon(R.drawable.ic_pending, description = "Revisão de notificações")
                        }
                    }
                    IconButton({ tab = if (tab == 3) 0 else 3 }, Modifier.testTag("forecast-settings")) { AppIcon(R.drawable.ic_settings, description = "Ajustes") }
                }
                when {
                    tab == 5 -> Column {
                        TextButton({ tab = 3 }, Modifier.padding(horizontal = FinanceSpacing.page)) { Text("Configurar captura") }
                        Box(Modifier.weight(1f)) {
                            PendingScreen(oldState.pending, hidden, { openReview(it) }, { id -> finance.dismiss(id) { reviewIds = ReviewQueue.snapshot(oldState.pending.map { it.id }, id); reviewAfter = id } }, busy, captureActive)
                        }
                    }
                    tab == 4 -> RecordsScreen(realized, month, { monthText = it.toString() }, hidden, recordCategory,
                        { recordCategory = it }, { openActual(recordId = it.id) },
                        expenseFilter = expenseDimension?.let { ExpenseFilter(it, expenseKey, expenseLabel) },
                        onClearExpenseFilter = { expenseDimension = null }, realizedOnly = true)
                    tab == 3 -> SettingsScreen(settings, connected, hasAccess, canAlert, captureError,
                        onToggle = { enabled -> if (enabled && !settings.consented) disclosure = true else finance.preferences.setEnabled(enabled) },
                        onApps = { if (!settings.consented) disclosure = true else { appPicker = true; finance.loadApps() } },
                        onAccess = ::requestCaptureAccess,
                        onReconnect = { NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java)) },
                        onAlerts = ::requestAlerts,
                        onExport = { export.launch("previsoes-${period.start}.csv") }, onClear = { clearing = true }, onSimulate = finance::simulate,
                        onAppearance = finance.preferences::setAppearance, busy = busy,
                        onBackup = { backupDialog = 1 }, onRestore = { backupImport.launch(arrayOf("*/*")) }, onPlanning = { tab = 1 },
                        onLockChange = onLockChange, onSecureScreen = finance.preferences::setSecureScreen,
                        onReminders = { finance.preferences.setReminders(it); if (it && !canAlert) requestAlerts() },
                        plannerMode = true, showCapture = true, onDisplayName = finance.preferences::setDisplayName)
                    loadError != null -> { Text(loadError!!, Modifier.padding(FinanceSpacing.page)); Button({ model.refresh() }) { Text("Tentar novamente") } }
                    snapshot == null -> Text("Abrindo seu planejamento…", Modifier.padding(FinanceSpacing.page))
                    report == null -> Text("Não foi possível calcular este ciclo. Confira as previsões em seus dados e tente novamente.", Modifier.padding(FinanceSpacing.page))
                    tab == 2 -> ForecastReserveScreen(snapshot, report, hidden, busy,
                        onSet = { showReserve(ReserveEventKind.SET) }, onAdd = { showReserve(ReserveEventKind.ADD) },
                        onWithdraw = { showReserve(ReserveEventKind.WITHDRAW) }, onGoal = { goalEditor = true })
                    tab == 1 -> ForecastProjectionScreen(snapshot, period, simulation, hidden, busy,
                        onSimulate = { simulationEditor = true }, onDiscard = { simulation = null },
                        onAdopt = { simulation?.let { value -> model.saveRule(value) { simulation = null } } }, onOpen = { select(it.start); tab = 0 },
                        legacyIncome = legacyIncome.filterNot { old -> snapshot.rules.any { it.legacySource == "monthly-reference:${old.month}" } },
                        onReuse = { old -> legacyMonth = old.month; editItem = null; editKey = null; template = null; editor = true },
                        represented = represented)
                    else -> LazyColumn(Modifier.testTag("forecast-dashboard"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
                        item {
                            val reviewLabel = if (oldState.pending.isEmpty()) "Revisar notificações"
                                else "Revisar notificações (${oldState.pending.size})"
                            FilledTonalButton({ tab = 5 }, Modifier.fillMaxWidth().testTag("dashboard-notification-review")) {
                                AppIcon(R.drawable.ic_pending)
                                Spacer(Modifier.width(8.dp))
                                Text(reviewLabel)
                            }
                        }
                        item { MonthPicker(month) { monthText = it.toString() } }
                        item { MonthlyResultCard(salarySummary, hidden) { salaryEditor = true } }
                        item { PaydayBudgetCard(paydayBudget, knownBalances.isNotEmpty(), hidden) { newItem(ForecastKind.INCOME, "Salário") } }
                        item { TextButton({ expenseDimension = null; recordCategory = null; tab = 4 }) { Text("Ver extrato do mês") } }
                        item { ExpenseChart(realized, month, hidden) {
                            expenseDimension = it.dimension; expenseKey = it.key; expenseLabel = it.label; recordCategory = null; tab = 4
                        } }
                        item { MonthComparisonCard(monthComparison, hidden) {
                            expenseDimension = null; recordCategory = it; tab = 4
                        } }
                        item { SectionTitle("Planejamento", "As previsões ficam fora do extrato e dos gastos realizados.") }
                        item { ForecastCyclePicker(period, { select(period.start.minusDays(1)) }, { select(period.endExclusive) }, { followingCurrent = true; browsing = today.toString() }, { cycleEditor = true }) }
                        item { Text(if (closed) "Ciclo encerrado · valores estimados" else "Planejamento do ciclo · valores estimados", style = MaterialTheme.typography.labelMedium) }
                        item { RendlyCategories(report, hidden) }
                        item { TextButton({ dueDates = true },
                            enabled = report.lines.any { it.included && !it.ignored && it.date >= today }, modifier = Modifier.fillMaxWidth()) {
                            AppIcon(R.drawable.ic_chevron_right); Spacer(Modifier.width(8.dp)); Text("Próximos vencimentos · consultar previsão")
                        } }
                        if (closed && report.unallocatedCents > 0) item {
                            OutlinedButton({ if (balance == null) showReserve(ReserveEventKind.SET) else showReserve(ReserveEventKind.ADD, true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("forecast-allocate-surplus")) {
                                Text(if (balance == null) "Informar reserva para destinar a sobra" else "Guardar parte da sobra")
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Previsões do período", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton({ newItem(ForecastKind.EXPENSE) }, Modifier.testTag("forecast-add-expense")) { Text("Nova previsão") }
                            }
                            Row {
                                TextButton({ newItem(ForecastKind.INCOME) }, Modifier.testTag("forecast-add-income")) { Text("Prever receita") }
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
    if (salaryEditor) key(monthText) {
        SalaryMonthEditor(month, salarySummary.salaryCents, hidden, busy, { salaryEditor = false }) { cents ->
            finance.saveMonthlyIncome(MonthlyIncomePlan(month, cents, salaryReference?.healthyLimitBasisPoints ?: 3000)) {
                salaryEditor = false; model.refresh()
            }
        }
    }
    val draft = oldState.pending.firstOrNull { it.id == editingDraftId }
    val merchant = remember(draft?.id) { draft?.takeIf { it.movement == Movement.EXPENSE }?.let { MerchantIdentity.identify(it.sourceTitle, it.sourceBody) } }
    val record = oldState.records.firstOrNull { it.id == editingRecordId }
    if (recordEditor && (editingDraftId == null || draft != null) && (editingRecordId == null || record != null)) key(editingDraftId, editingRecordId, actualKind) {
        RecordEditor(draft, record, busy, onDismiss = { recordEditor = false },
            onSave = { value -> finance.save(value, draft?.id, editing = record != null, expectedRevision = draftRevision) {
                recordEditor = false
                if (draft != null) { reviewAfter = draft.id; rememberMerchantCategory(draft, value.category) }
            } },
            onIgnore = draft?.let { { finance.dismiss(it.id) { recordEditor = false; reviewAfter = it.id } } },
            onDelete = record?.let { { finance.delete(it.id) } },
            accounts = oldState.accounts, lastOrigin = settings.lastOrigin, onAddAccount = finance::addAccount,
            initialMovement = actualKind, hideAmounts = hidden, duplicateIndex = duplicateIndex,
            balances = oldState.accountBalances, history = oldState.records,
            rememberedCategory = merchant?.let { merchantStore.get(it.key) }, merchantLabel = merchant?.label,
            notificationChanged = draft != null && draft.revision != draftRevision,
            onAcceptUpdate = { draftRevision = draft?.revision },
            realizedOnly = true, onOpenExpenseForecast = if (draft == null && record == null && snapshot != null) {
                { recordEditor = false; newItem(ForecastKind.EXPENSE) }
            } else null)
    }
    reviewAfter?.takeIf { !recordEditor && tab == 5 }?.let { done ->
        val pendingIds = oldState.pending.map { it.id }.toSet()
        val next = ReviewQueue.next(reviewIds, done, pendingIds)?.let { id -> oldState.pending.firstOrNull { it.id == id } }
            ?: oldState.pending.firstOrNull { it.id != done }
        AlertDialog(onDismissRequest = { reviewAfter = null }, title = { DialogSystemBars(); Text("Sugestão tratada") },
            text = { Text(if (next != null) "Há mais notificações para revisar. Cada lançamento continua exigindo a sua confirmação."
                else "Não há mais notificações pendentes para revisar.") },
            confirmButton = { if (next != null) TextButton({ openReview(next, continuing = true) }, Modifier.testTag("review-next")) { Text("Revisar próxima") }
                else TextButton({ reviewAfter = null }, Modifier.testTag("review-empty-done")) { Text("Concluir") } },
            dismissButton = { if (next != null) TextButton({ reviewAfter = null }) { Text("Agora não") } })
    }
    LaunchedEffect(oldState.loaded, draft, record, editingDraftId, editingRecordId) {
        if (oldState.loaded && ((editingDraftId != null && draft == null) || (editingRecordId != null && record == null))) recordEditor = false
    }
    if (disclosure) DisclosureDialog({ disclosure = false }) {
        finance.preferences.acceptDisclosure(); disclosure = false; appPicker = true; finance.loadApps()
    }
    if (appPicker) AppPickerDialog(apps, loadingApps, settings.packages, { appPicker = false }) {
        finance.preferences.selectPackages(it); appPicker = false
    }
    val selectedLine = report?.lines?.firstOrNull { it.itemId == editItem && it.occurrenceKey == editKey }
    if (dueDates && report != null) AlertDialog(onDismissRequest = { dueDates = false },
        title = { Text("Próximos vencimentos") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                val upcoming = report.lines.filter { it.included && !it.ignored && it.kind == ForecastKind.EXPENSE && !it.rule.cycleAnchored && it.date >= today }.sortedBy { it.date }
                if (upcoming.isEmpty()) item { Text("Sem vencimentos previstos restantes neste ciclo.") }
                upcoming.forEach { line -> item {
                    TextButton({ dueDates = false; edit(line) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.weight(1f)) { Text(privateDisplayText(line.title, hidden)); Text(line.date.toString()) }
                        Amount(line.amountCents, hidden, size = 16)
                    }
                } }
            }
        }, confirmButton = { TextButton({ dueDates = false }) { Text("Fechar") } })
    val selectedRule = snapshot?.rules?.filter { it.itemId == editItem }?.maxByOrNull { it.effectiveFrom }
    val oldIncome = legacyIncome.firstOrNull { it.month == legacyMonth }
    val referenceRule = oldIncome?.let { ForecastRule("monthly-reference:${it.month}", period.start, "Renda anterior · revisar", ForecastKind.INCOME,
        it.incomeCents, Category.OTHER_INCOME, ForecastFrequency.ONCE, period.start, legacySource = "monthly-reference:${it.month}") }
    if (editor && snapshot != null) key(editItem, editKey, template, legacyMonth, initialKind) {
        ForecastEditor(rule = selectedLine?.rule ?: selectedRule ?: referenceRule, line = selectedLine, period = period, busy = busy, hidden = hidden,
            onDismiss = { editor = false }, onSaveRule = { model.saveRule(it) { editor = false } }, onSaveOverride = { model.saveOverride(it) { editor = false } },
            initialKind = initialKind, initialFrequency = if (initialKind == ForecastKind.EXPENSE && template == null) ForecastFrequency.ONCE else ForecastFrequency.MONTHLY, initialTitle = template.orEmpty(), initialCategory = fixedExpenseTemplates.firstOrNull { it.title == template }?.category,
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

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val legacyIncome by model.legacyIncome.collectAsStateWithLifecycle()
    val oldState by finance.state.collectAsStateWithLifecycle()
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
    val month = YearMonth.parse(navigation.monthText)
    val zone = ZoneId.systemDefault()
    val asOf = remember(resumeCount, oldState.records) { System.currentTimeMillis() }
    val realized = remember(oldState.records, asOf) { oldState.records.filter { it.occurredAt <= asOf } }
    val duplicateIndex = remember(oldState.manualRecords) { DuplicateCandidates(oldState.manualRecords) }
    val salaryReference = key(navigation.monthText) {
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
        review.actualKind = kind; review.editingRecordId = recordId; review.editingDraftId = null; review.draftRevision = null; review.recordEditor = true
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
        if (!continuing) review.reviewIds = ReviewQueue.snapshot(oldState.pending.map { it.id }, draft.id)
        review.reviewAfter = null
        review.editingDraftId = draft.id; review.editingRecordId = null; review.draftRevision = draft.revision; review.recordEditor = true
    }
    LaunchedEffect(incomingDraft, oldState.loaded, oldState.pending, busy, review.recordEditor, editors.editor, editors.backupDialog, staged, settings.namePromptSeen) {
        if (incomingDraft != null && settings.namePromptSeen && oldState.loaded && !busy && !review.recordEditor && !editors.editor && editors.backupDialog == 0 && staged == null) {
            oldState.pending.firstOrNull { it.id == incomingDraft }?.let { navigation.tab = 5; openReview(it) }
            consumeDraft()
        }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(resumeCount) {
        today = LocalDate.now()
        if (navigation.followingCurrent) navigation.browsing = today.toString()
        model.refresh()
    }
    LaunchedEffect(incomingPlanning) { if (incomingPlanning) {
        navigation.tab = 0; navigation.followingCurrent = true; navigation.browsing = LocalDate.now().toString(); consumePlanning()
    } }
    LaunchedEffect(forecastMessage) { forecastMessage?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null } }
    LaunchedEffect(financeMessage) { financeMessage?.let {
        if (it.startsWith("Dados restaurados") || it.startsWith("Dados apagados")) model.refresh()
        snackbar.showSnackbar(it); if (finance.message.value == it) finance.message.value = null
    } }
    if (!settings.namePromptSeen) DisplayNameDialog(settings.displayName,
        finance.preferences::dismissNamePrompt, finance.preferences::setDisplayName, firstRun = true)
    val snapshot = data
    val period = remember(snapshot?.cycleRules, navigation.browsing) { CycleCalendar.periodContaining(LocalDate.parse(navigation.browsing), snapshot?.cycleRules.orEmpty()) }
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
        if (uri != null) { editors.restoreUri = uri.toString(); editors.backupDialog = 2 }
    }
    fun newItem(kind: ForecastKind, title: String? = null) {
        editors.editItem = null; editors.editKey = null; editors.legacyMonth = null; editors.template = title; editors.initialKind = kind; editors.editor = true
    }
    fun edit(line: ForecastLine) { editors.editItem = line.itemId; editors.editKey = line.occurrenceKey; editors.legacyMonth = null; editors.template = null; editors.editor = true }
    fun showReserve(kind: ReserveEventKind, allocation: Boolean = false) { editors.allocating = allocation; editors.reserveKind = kind }
    fun select(date: LocalDate) { navigation.followingCurrent = false; navigation.browsing = date.toString() }
    BackHandler(navigation.tab >= 3 && !editors.editor) { navigation.tab = 0 }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column {
                if (navigation.tab == 0 || navigation.tab == 4) Row(Modifier.padding(horizontal = FinanceSpacing.page), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ openActual(Movement.INCOME) }, enabled = !busy && oldState.loaded, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("add-realized-income")) {
                        AppIcon(R.drawable.ic_income); Spacer(Modifier.width(6.dp)); Text("Receita")
                    }
                    Button({ openActual() }, enabled = !busy && oldState.loaded, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("add-realized-expense")) {
                        AppIcon(R.drawable.ic_add); Spacer(Modifier.width(6.dp)); Text("Despesa")
                    }
                }
                ForecastNavigationBar(navigation.tab) { navigation.tab = it }
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
                        Text(if (navigation.tab >= 3) "Ajustes e histórico" else if (settings.displayName.isBlank()) "Olá! 👋🏼" else "Olá, ${settings.displayName}! 👋🏼",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("rendly-greeting"), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton({ finance.preferences.hideAmounts(!hidden) }, Modifier.size(48.dp)) { AppIcon(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, description = if (hidden) "Mostrar valores" else "Ocultar valores") }
                    if (navigation.tab != 0) IconButton({ navigation.tab = if (navigation.tab == 5) 0 else 5 }, Modifier.size(48.dp).testTag("open-notification-review")) {
                        BadgedBox(badge = { if (oldState.pending.isNotEmpty()) Badge { Text(oldState.pending.size.toString()) } }) {
                            AppIcon(R.drawable.ic_pending, description = "Revisão de notificações")
                        }
                    }
                    IconButton({ navigation.tab = if (navigation.tab == 3) 0 else 3 }, Modifier.size(48.dp).testTag("forecast-settings")) { AppIcon(R.drawable.ic_settings, description = "Ajustes") }
                }
                when {
                    navigation.tab == 5 -> Column {
                        TextButton({ navigation.tab = 3 }, Modifier.padding(horizontal = FinanceSpacing.page)) { Text("Configurar captura") }
                        Box(Modifier.weight(1f)) {
                            PendingScreen(oldState.pending, hidden, { openReview(it) }, { id -> finance.dismiss(id) { review.reviewIds = ReviewQueue.snapshot(oldState.pending.map { it.id }, id); review.reviewAfter = id } }, busy, captureActive)
                        }
                    }
                    navigation.tab == 4 -> RecordsScreen(realized, month, { navigation.monthText = it.toString() }, hidden, navigation.recordCategory,
                        { navigation.recordCategory = it }, { openActual(recordId = it.id) },
                        expenseFilter = navigation.expenseDimension?.let { ExpenseFilter(it, navigation.expenseKey, navigation.expenseLabel) },
                        onClearExpenseFilter = { navigation.expenseDimension = null },
                        accountFilter = oldState.accountBalances.firstOrNull { it.account.key == navigation.accountFilterKey }?.account
                            ?: oldState.accounts.firstOrNull { it.key == navigation.accountFilterKey },
                        onClearAccount = { navigation.accountFilterKey = null }, realizedOnly = true)
                    navigation.tab == 3 -> SettingsScreen(settings, connected, hasAccess, canAlert, captureError,
                        onToggle = { enabled -> if (enabled && !settings.consented) editors.disclosure = true else finance.preferences.setEnabled(enabled) },
                        onApps = { if (!settings.consented) editors.disclosure = true else { editors.appPicker = true; finance.loadApps() } },
                        onAccess = ::requestCaptureAccess,
                        onReconnect = { NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java)) },
                        onAlerts = ::requestAlerts,
                        onExport = { export.launch("previsoes-${period.start}.csv") }, onClear = { editors.clearing = true }, onSimulate = finance::simulate,
                        onAppearance = finance.preferences::setAppearance, busy = busy,
                        onBackup = { editors.backupDialog = 1 }, onRestore = { backupImport.launch(arrayOf("*/*")) }, onPlanning = { navigation.tab = 1 },
                        onLockChange = onLockChange, onSecureScreen = finance.preferences::setSecureScreen,
                        onReminders = { finance.preferences.setReminders(it); if (it && !canAlert) requestAlerts() },
                        plannerMode = true, showCapture = true, onDisplayName = finance.preferences::setDisplayName)
                    loadError != null -> { Text(loadError!!, Modifier.padding(FinanceSpacing.page)); Button({ model.refresh() }) { Text("Tentar novamente") } }
                    snapshot == null -> Text("Abrindo seu planejamento…", Modifier.padding(FinanceSpacing.page))
                    report == null -> Text("Não foi possível calcular este ciclo. Confira as previsões em seus dados e tente novamente.", Modifier.padding(FinanceSpacing.page))
                    navigation.tab == 2 -> ForecastReserveScreen(snapshot, report, hidden, busy,
                        onSet = { showReserve(ReserveEventKind.SET) }, onAdd = { showReserve(ReserveEventKind.ADD) },
                        onWithdraw = { showReserve(ReserveEventKind.WITHDRAW) }, onGoal = { editors.goalEditor = true })
                    navigation.tab == 1 -> ForecastProjectionScreen(snapshot, period, editors.simulation, hidden, busy,
                        onSimulate = { editors.simulationEditor = true }, onDiscard = { editors.simulation = null },
                        onAdopt = { editors.simulation?.let { value -> model.saveRule(value) { editors.simulation = null } } }, onOpen = { select(it.start); navigation.tab = 0 },
                        legacyIncome = legacyIncome.filterNot { old -> snapshot.rules.any { it.legacySource == "monthly-reference:${old.month}" } },
                        onReuse = { old -> editors.legacyMonth = old.month; editors.editItem = null; editors.editKey = null; editors.template = null; editors.editor = true },
                        represented = represented)
                    else -> LazyColumn(Modifier.testTag("forecast-dashboard"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
                        monthlyDashboardCards(navigation, editors, month, salarySummary, paydayBudget,
                            monthComparison, realized, oldState.pending.size, oldState.accountBalances, knownBalances.isNotEmpty(), hidden,
                            onNewSalary = { newItem(ForecastKind.INCOME, "Salário") })
                        item { SectionTitle("Planejamento", "As previsões ficam fora do extrato e dos gastos realizados.") }
                        item { ForecastCyclePicker(period, { select(period.start.minusDays(1)) }, { select(period.endExclusive) }, { navigation.followingCurrent = true; navigation.browsing = today.toString() }, { editors.cycleEditor = true }) }
                        item { Text(if (closed) "Ciclo encerrado · valores estimados" else "Planejamento do ciclo · valores estimados", style = MaterialTheme.typography.labelMedium) }
                        item { RendlyCategories(report, hidden) }
                        item { TextButton({ editors.dueDates = true },
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
                                TextButton({ editors.templates = true }) { Text("Modelos") }
                            }
                            Text("O vencimento organiza as datas; seu orçamento não depende de confirmar pagamentos.", style = MaterialTheme.typography.bodySmall)
                        }
                        forecastLineItems(report.lines, hidden, busy, ::edit, model::ignore, model::stop)
                        item { FinanceDisclosure("Histórico e dados anteriores", "forecast-legacy-data") {
                            Text("As fontes antigas foram preservadas. Recorrências, parcelas e registros vinculados foram aproveitados uma única vez. Rendas de referência antigas precisam de revisão antes de virar previsão.", style = MaterialTheme.typography.bodySmall)
                            TextButton({ navigation.tab = 1 }) { Text("Revisar rendas de referência antigas") }
                            TextButton({ navigation.tab = 4 }) { Text("Consultar lançamentos anteriores") }
                        } }
                    }
                }
            }
        }
    }
    if (editors.accountsOpen) AccountsManager(oldState.accountBalances, oldState.balanceReferences, hidden, busy,
        onDismiss = { editors.accountsOpen = false },
        onStatement = { account ->
            navigation.accountFilterKey = account.key; navigation.recordCategory = null; navigation.expenseDimension = null
            editors.accountsOpen = false; navigation.tab = 4
        }, onSave = finance::configureAccountBalance, onArchive = finance::setAccountArchived)
    if (editors.salaryEditor) key(navigation.monthText) {
        SalaryMonthEditor(month, salarySummary.salaryCents, hidden, busy, { editors.salaryEditor = false }) { cents ->
            finance.saveMonthlyIncome(MonthlyIncomePlan(month, cents, salaryReference?.healthyLimitBasisPoints ?: 3000)) {
                editors.salaryEditor = false; model.refresh()
            }
        }
    }
    val draft = oldState.pending.firstOrNull { it.id == review.editingDraftId }
    val merchant = remember(draft?.id) { draft?.takeIf { it.movement == Movement.EXPENSE }?.let { MerchantIdentity.identify(it.sourceTitle, it.sourceBody) } }
    val record = oldState.records.firstOrNull { it.id == review.editingRecordId }
    if (review.recordEditor && (review.editingDraftId == null || draft != null) && (review.editingRecordId == null || record != null)) key(review.editingDraftId, review.editingRecordId, review.actualKind) {
        RecordEditor(draft, record, busy, onDismiss = { review.recordEditor = false },
            onSave = { value -> finance.save(value, draft?.id, editing = record != null, expectedRevision = review.draftRevision) {
                review.recordEditor = false
                if (draft != null) { review.reviewAfter = draft.id; rememberMerchantCategory(draft, value.category) }
            } },
            onIgnore = draft?.let { { finance.dismiss(it.id) { review.recordEditor = false; review.reviewAfter = it.id } } },
            onDelete = record?.let { { finance.delete(it.id) } },
            accounts = oldState.accounts, lastOrigin = settings.lastOrigin, onAddAccount = finance::addAccount,
            initialMovement = review.actualKind, hideAmounts = hidden, duplicateIndex = duplicateIndex,
            balances = oldState.accountBalances, history = oldState.records,
            rememberedCategory = merchant?.let { merchantStore.get(it.key) }, merchantLabel = merchant?.label,
            notificationChanged = draft != null && draft.revision != review.draftRevision,
            onAcceptUpdate = { review.draftRevision = draft?.revision },
            realizedOnly = true, onOpenExpenseForecast = if (draft == null && record == null && snapshot != null) {
                { review.recordEditor = false; newItem(ForecastKind.EXPENSE) }
            } else null)
    }
    review.reviewAfter?.takeIf { !review.recordEditor && navigation.tab == 5 }?.let { done ->
        val pendingIds = oldState.pending.map { it.id }.toSet()
        val next = ReviewQueue.next(review.reviewIds, done, pendingIds)?.let { id -> oldState.pending.firstOrNull { it.id == id } }
            ?: oldState.pending.firstOrNull { it.id != done }
        AlertDialog(onDismissRequest = { review.reviewAfter = null }, title = { DialogSystemBars(); Text("Sugestão tratada") },
            text = { Text(if (next != null) "Há mais notificações para revisar. Cada lançamento continua exigindo a sua confirmação."
                else "Não há mais notificações pendentes para revisar.") },
            confirmButton = { if (next != null) TextButton({ openReview(next, continuing = true) }, Modifier.testTag("review-next")) { Text("Revisar próxima") }
                else TextButton({ review.reviewAfter = null }, Modifier.testTag("review-empty-done")) { Text("Concluir") } },
            dismissButton = { if (next != null) TextButton({ review.reviewAfter = null }) { Text("Agora não") } })
    }
    LaunchedEffect(oldState.loaded, draft, record, review.editingDraftId, review.editingRecordId) {
        if (oldState.loaded && ((review.editingDraftId != null && draft == null) || (review.editingRecordId != null && record == null))) review.recordEditor = false
    }
    if (editors.disclosure) DisclosureDialog({ editors.disclosure = false }) {
        finance.preferences.acceptDisclosure(); editors.disclosure = false; editors.appPicker = true; finance.loadApps()
    }
    if (editors.appPicker) AppPickerDialog(apps, loadingApps, settings.packages, { editors.appPicker = false }) {
        finance.preferences.selectPackages(it); editors.appPicker = false
    }
    val selectedLine = report?.lines?.firstOrNull { it.itemId == editors.editItem && it.occurrenceKey == editors.editKey }
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
    val oldIncome = legacyIncome.firstOrNull { it.month == editors.legacyMonth }
    val referenceRule = oldIncome?.let { ForecastRule("monthly-reference:${it.month}", period.start, "Renda anterior · revisar", ForecastKind.INCOME,
        it.incomeCents, Category.OTHER_INCOME, ForecastFrequency.ONCE, period.start, legacySource = "monthly-reference:${it.month}") }
    if (editors.editor && snapshot != null) key(editors.editItem, editors.editKey, editors.template, editors.legacyMonth, editors.initialKind) {
        ForecastEditor(rule = selectedLine?.rule ?: selectedRule ?: referenceRule, line = selectedLine, period = period, busy = busy, hidden = hidden,
            onDismiss = { editors.editor = false }, onSaveRule = { model.saveRule(it) { editors.editor = false } }, onSaveOverride = { model.saveOverride(it) { editors.editor = false } },
            initialKind = editors.initialKind, initialFrequency = if (editors.initialKind == ForecastKind.EXPENSE && editors.template == null) ForecastFrequency.ONCE else ForecastFrequency.MONTHLY, initialTitle = editors.template.orEmpty(), initialCategory = fixedExpenseTemplates.firstOrNull { it.title == editors.template }?.category,
            knownStatements = snapshot.rules.map { it.statementKey }.filter { it.isNotBlank() }.distinct(),
            existingOverride = snapshot.overrides.firstOrNull { it.itemId == selectedLine?.itemId && it.occurrenceKey == selectedLine?.occurrenceKey })
    }
    if (editors.templates) AlertDialog(onDismissRequest = { editors.templates = false }, title = { DialogSystemBars(); Text("Modelos opcionais") },
        text = { Column { fixedExpenseTemplates.forEach { item -> TextButton({ editors.templates = false; newItem(ForecastKind.EXPENSE, item.title) }, Modifier.fillMaxWidth()) { Text(item.title) } }
            Text("Você informará o valor antes de salvar.", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton({ editors.templates = false }) { Text("Fechar") } })
    if (editors.cycleEditor && snapshot != null) CycleDayEditor(snapshot.cycleRules.lastOrNull { it.effectiveStart <= today }?.day ?: 1, busy,
        preview = { day ->
            val rules = CycleConfiguration.rulesAfterChange(day, today, snapshot)
            CycleCalendar.periodContaining(maxOf(today, rules.last().effectiveStart), rules)
        },
        onDismiss = { editors.cycleEditor = false }, onSave = { model.setCycleDay(it) { editors.cycleEditor = false; navigation.followingCurrent = true; navigation.browsing = today.toString() } },
        firstConfiguration = snapshot.cycleRules.isEmpty() && snapshot.reserveEvents.none { it.cycleStart != null })
    editors.reserveKind?.let { kind -> key(kind, editors.allocating) {
        ReserveEditor(kind, balance, busy, hidden, { editors.reserveKind = null }, { cents, reason, cycle -> model.saveReserve(kind, cents, reason, cycle) { editors.reserveKind = null } },
            allocationLimitCents = if (editors.allocating) report?.unallocatedCents else null, cycle = if (editors.allocating) period else null, allocateInitially = editors.allocating)
    } }
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

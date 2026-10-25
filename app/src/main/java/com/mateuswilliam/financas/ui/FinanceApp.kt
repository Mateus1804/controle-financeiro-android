package com.mateuswilliam.financas.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.CaptureIssue
import com.mateuswilliam.financas.core.ExpenseFilter
import com.mateuswilliam.financas.core.ExpenseDimension
import com.mateuswilliam.financas.core.DuplicateCandidates
import com.mateuswilliam.financas.core.ReviewQueue
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.notifications.BankNotificationListener
import com.mateuswilliam.financas.notifications.hasCaptureAccess
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FinanceApp(model: FinanceViewModel, resumeCount: Int, incomingDraft: String?, consumeDraft: () -> Unit,
    incomingPlanning: Boolean = false, consumePlanning: () -> Unit = {}, onLockChange: (Boolean) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val connected by model.connected.collectAsStateWithLifecycle()
    val captureError by model.captureError.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val loadingApps by model.loadingApps.collectAsStateWithLifecycle()
    val stagedRestore by model.stagedRestore.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthString)
    var categoryFilter by rememberSaveable { mutableStateOf<Category?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var expectedRevision by rememberSaveable { mutableStateOf<Int?>(null) }
    var reviewIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var reviewCompleted by rememberSaveable { mutableIntStateOf(0) }
    var reviewAfter by rememberSaveable { mutableStateOf<String?>(null) }
    var planningReturn by rememberSaveable { mutableIntStateOf(0) }
    var selectedRecurringId by rememberSaveable { mutableStateOf<String?>(null) }
    var recurringDue by rememberSaveable { mutableStateOf("") }
    var recurringRecordId by rememberSaveable { mutableStateOf("") }
    var recurringRecordAt by rememberSaveable { mutableLongStateOf(0L) }
    val recurringPlan = state.recurring.find { it.id == selectedRecurringId }
    val recurringPreset = recurringPlan?.copy(nextDate = recurringDue)?.proposedRecord(recurringRecordId, ZoneId.systemDefault())?.copy(occurredAt = recurringRecordAt)
    val duplicateIndex = remember(state.manualRecords) { DuplicateCandidates(state.manualRecords) }
    var backupDialog by rememberSaveable { mutableIntStateOf(0) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingById = remember(state.pending) { state.pending.associateBy { it.id } }
    val reviewIdSet = remember(reviewIds) { reviewIds.toHashSet() }
    val newPendingCount = remember(state.pending, reviewIdSet) { state.pending.count { it.id !in reviewIdSet } }
    val draft = pendingById[selectedDraftId]
    val record = state.records.find { it.id == selectedRecordId }
    var expenseDimension by rememberSaveable { mutableStateOf<ExpenseDimension?>(null) }
    var expenseKey by rememberSaveable { mutableStateOf("") }
    var expenseLabel by rememberSaveable { mutableStateOf("") }
    val expenseFilter = expenseDimension?.let { ExpenseFilter(it, expenseKey, expenseLabel) }
    var disclosure by remember { mutableStateOf(false) }
    var appPicker by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val seen = rememberSaveable { arrayListOf<String>() }
    val context = LocalContext.current
    var manualMovement by rememberSaveable { mutableStateOf(Movement.EXPENSE) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val hasAccess = remember(resumeCount, permissionVersion, connected) { hasCaptureAccess(context) }
    val canAlert = remember(resumeCount, permissionVersion) { NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val alertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionVersion++ }
    fun requestAccess() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { model.message.value = "Abra Configurações > Acesso às notificações e selecione Controle Financeiro." }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val csv = model.exportCsv()
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Arquivo indisponível")
                    output.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
            }.onSuccess { model.message.value = "CSV exportado. Guarde o arquivo em um local de sua confiança." }
                .onFailure { model.message.value = "Não foi possível exportar o arquivo. Tente outro destino." }
        }
    }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { model.writeBackup(it) }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri.toString(); backupDialog = 2 }
    }
    fun requestAlerts() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            .onFailure { model.message.value = "Abra as configurações de notificações do aplicativo no Android." }
    }
    fun showDraft(value: DraftEntity) {
        seen.add(value.id); selectedDraftId = value.id; selectedRecordId = null; selectedRecurringId = null
        expectedRevision = value.revision; editing = true; reviewAfter = null
    }
    fun openDraft(value: DraftEntity) {
        reviewIds = ReviewQueue.snapshot(state.pending.map { it.id }, value.id); reviewCompleted = 0
        showDraft(value)
    }
    fun finishReview(id: String) { reviewCompleted++; editing = false; reviewAfter = id }
    fun finishSession() { reviewAfter = null; seen.addAll(state.pending.map { it.id }); reviewIds = emptyList(); tab = 1 }
    fun openPlanning() { if (tab != 4) planningReturn = tab; tab = 4 }
    fun openManual(movement: Movement = Movement.EXPENSE) { manualMovement = movement; selectedDraftId = null; selectedRecordId = null; selectedRecurringId = null; expectedRevision = null; reviewAfter = null; editing = true }
    BackHandler(tab == 4 && !editing) { tab = planningReturn }

    val captureIssue = CaptureIssue.evaluate(state.settings.consented, state.settings.enabled, state.settings.packages.isNotEmpty(), hasAccess, connected)
    LaunchedEffect(state.loaded, captureIssue) { if (state.loaded) model.preferences.observeCaptureCondition(captureIssue.name) }
    LaunchedEffect(state.loaded, selectedDraftId, selectedRecordId, draft, record) {
        if (state.loaded && editing && ((selectedDraftId != null && draft == null) || (selectedRecordId != null && record == null))) editing = false
    }
    fun resolveCapture() {
        when (captureIssue) {
            CaptureIssue.CONSENT -> disclosure = true
            CaptureIssue.DISABLED -> model.preferences.setEnabled(true)
            CaptureIssue.APPS -> { appPicker = true; model.loadApps() }
            CaptureIssue.ACCESS -> requestAccess()
            else -> tab = 3
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null }
    }
    LaunchedEffect(incomingDraft, state.loaded, state.pending, editing, tab, backupDialog, stagedRestore, busy) {
        if (incomingDraft != null && state.loaded && !editing && !busy && tab != 4 && backupDialog == 0 && stagedRestore == null) {
            state.pending.find { it.id == incomingDraft }?.let { openDraft(it); tab = 1 }
                ?: run { model.message.value = "Esta notificação já foi revisada." }
            consumeDraft()
        }
    }
    LaunchedEffect(incomingPlanning, editing, backupDialog, stagedRestore, busy, reviewAfter) {
        if (incomingPlanning && !editing && !busy && backupDialog == 0 && stagedRestore == null && reviewAfter == null) { openPlanning(); consumePlanning() }
    }
    LaunchedEffect(state.pending, editing, tab, reviewAfter, backupDialog, stagedRestore, busy, state.settings.introSeen) {
        val seenIds = seen.toHashSet()
        if (!editing && !busy && state.settings.introSeen && incomingDraft == null && tab in 0..1 && reviewAfter == null && backupDialog == 0 && stagedRestore == null) state.pending.firstOrNull {
            it.capturedAt >= model.sessionStartedAt && it.id !in seenIds
        }?.let(::openDraft)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { NewRecordAction { openManual() } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                val labels = listOf("Resumo", "Revisar", "Extrato", "Ajustes")
                val icons = listOf(R.drawable.ic_overview, R.drawable.ic_pending, R.drawable.ic_records, R.drawable.ic_settings)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface),
                        icon = { if (index == 1 && state.pending.isNotEmpty()) BadgedBox(badge = { Badge { Text(state.pending.size.toString()) } }) {
                            AppIcon(icons[index])
                        } else AppIcon(icons[index]) }, label = { Text(label, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                when (tab) {
                    0 -> DashboardScreen(state, month, { monthString = it.toString() },
                        { model.preferences.hideAmounts(!state.settings.hideAmounts) },
                        { tab = 1 },
                        onExpenseGroup = { categoryFilter = null; expenseDimension = it.dimension; expenseKey = it.key; expenseLabel = it.label; tab = 2 },
                        notice = if (state.loaded && captureIssue != CaptureIssue.READY && state.settings.dismissedCondition != captureIssue.name) {
                            { CaptureNotice(captureIssue, ::resolveCapture) { model.preferences.dismissCaptureNotice(captureIssue.name) } }
                        } else null, onPlanning = ::openPlanning, onNew = ::openManual)
                    1 -> PendingScreen(state.pending, state.settings.hideAmounts, ::openDraft, { model.dismiss(it) }, busy)
                    2 -> RecordsScreen(state.records, month, { monthString = it.toString() }, state.settings.hideAmounts,
                        categoryFilter, { categoryFilter = it }, { selectedRecordId = it.id; selectedDraftId = null; selectedRecurringId = null; expectedRevision = null; editing = true },
                        expenseFilter = expenseFilter, onClearExpenseFilter = { expenseDimension = null })
                    3 -> SettingsScreen(state.settings, connected, hasAccess, canAlert, captureError,
                        onToggle = { enabled -> if (enabled && !state.settings.consented) disclosure = true else model.preferences.setEnabled(enabled) },
                        onApps = { if (!state.settings.consented) disclosure = true else { appPicker = true; model.loadApps() } },
                        onAccess = ::requestAccess,
                        onReconnect = { NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java)) },
                        onAlerts = ::requestAlerts,
                        onExport = { export.launch("movimentacoes-${YearMonth.now()}.csv") },
                        onClear = { clearDialog = true }, onSimulate = model::simulate,
                        onAppearance = model.preferences::setAppearance, busy = busy,
                        onBackup = { backupDialog = 1 }, onRestore = { backupImport.launch(arrayOf("*/*")) }, onPlanning = ::openPlanning,
                        onLockChange = onLockChange, onSecureScreen = model.preferences::setSecureScreen)
                    4 -> PlanningScreen(state, month, { monthString = it.toString() }, busy, canAlert,
                        onBack = { tab = planningReturn }, onBudget = model::saveBudget,
                        onDeleteBudget = { model.deleteBudget(month.toString(), it) }, onRecurring = model::saveRecurring,
                        onConfirmRecurring = {
                            selectedRecurringId = it.id; recurringDue = it.nextDate; recurringRecordId = UUID.randomUUID().toString(); recurringRecordAt = System.currentTimeMillis()
                            selectedDraftId = null; selectedRecordId = null; expectedRevision = null; editing = true
                        }, onSkip = model::skipRecurring,
                        onReminders = { model.preferences.setReminders(it); if (it && !canAlert) requestAlerts() },
                        onAlerts = ::requestAlerts, onAddAccount = model::addAccount)
                }
            }
        }
    }

    if (editing && (selectedDraftId == null || draft != null) && (selectedRecordId == null || record != null) && (selectedRecurringId == null || recurringPreset != null)) key(selectedDraftId, selectedRecordId, selectedRecurringId) {
        RecordEditor(draft, record, busy, onDismiss = { editing = false },
            onSave = { value ->
                if (selectedRecurringId != null) model.confirmRecurring(selectedRecurringId!!, recurringDue, value) { editing = false }
                else { val currentDraftId = selectedDraftId; model.save(value, currentDraftId, editing = selectedRecordId != null, expectedRevision = expectedRevision) {
                    if (currentDraftId != null) finishReview(currentDraftId) else editing = false
                } }
            },
            onIgnore = draft?.let { current -> { model.dismiss(current.id) { finishReview(current.id) } } },
            onDelete = record?.let { current -> { model.delete(current.id) } },
            accounts = state.accounts, lastOrigin = state.settings.lastOrigin.ifBlank { state.records.firstOrNull()?.bank.orEmpty() }, onAddAccount = model::addAccount,
            preset = recurringPreset, initialMovement = manualMovement, duplicateIndex = duplicateIndex, hideAmounts = state.settings.hideAmounts,
            reviewProgress = if (draft != null) "Revisão ${reviewIds.indexOf(draft.id) + 1} de ${reviewIds.size}" else null,
            newPendingCount = if (draft != null) newPendingCount else 0,
            notificationChanged = draft != null && expectedRevision != draft.revision,
            onAcceptUpdate = { expectedRevision = draft?.revision })
    }
    if (reviewAfter != null && !editing) {
        val next = ReviewQueue.next(reviewIds, reviewAfter!!, pendingById.keys)?.let { pendingById[it] }
        AlertDialog(onDismissRequest = ::finishSession, title = { DialogSystemBars(); Text("Movimentação revisada") },
            text = { Text("$reviewCompleted de ${reviewIds.size} itens tratados nesta sessão. $newPendingCount nova(s) pendência(s) fora da sessão. A próxima revisão só abre ao continuar.") },
            confirmButton = { if (next != null) TextButton(onClick = { showDraft(next) }) { Text("Revisar próxima") }
                else TextButton(onClick = ::finishSession) { Text("Ver pendências") } },
            dismissButton = { if (next != null) TextButton(onClick = ::finishSession) { Text("Concluir sessão") } })
    }
    if (backupDialog != 0) BackupPasswordDialog(backupDialog == 1, busy, onDismiss = { backupDialog = 0; restoreUri = null }, onConfirm = { secret ->
        val creating = backupDialog == 1
        backupDialog = 0
        if (creating) model.createBackup(secret) { backupExport.launch("controle-financeiro-${LocalDate.now()}.cfbackup") }
        else { val uri = restoreUri; restoreUri = null; if (uri != null) model.stageBackup(uri.toUri(), secret) else secret.fill('\u0000') }
    })
    stagedRestore?.let { BackupPreviewDialog(it, busy, model::cancelRestore, model::restoreBackup) }
    if (state.loaded && !busy && !state.settings.introSeen) CaptureIntroduction(
        onActivate = { model.preferences.finishIntro(); disclosure = true },
        onManual = { model.preferences.setEnabled(false); model.preferences.finishIntro() })
    if (disclosure) DisclosureDialog(onDismiss = { disclosure = false }, onAccept = {
        model.preferences.acceptDisclosure(); disclosure = false; appPicker = true; model.loadApps()
    })
    if (appPicker) AppPickerDialog(apps, loadingApps, state.settings.packages,
        onDismiss = { appPicker = false }, onSave = {
            model.preferences.selectPackages(it); appPicker = false
            if (it.isNotEmpty() && !hasAccess && state.settings.enabled) requestAccess()
        })
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { DialogSystemBars(); Text("Apagar todos os dados?") },
        text = { Text("Lançamentos, pendências, contas, orçamentos, recorrências e preferências serão apagados. A captura será desativada. Arquivos de backup exportados permanecem no destino escolhido. Esta ação não pode ser desfeita.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; model.clear() }, enabled = !busy) { Text("Apagar dados") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancelar") } })
}

@Composable
private fun NewRecordAction(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium, elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
        AppIcon(R.drawable.ic_add, description = "Novo lançamento")
    }
}

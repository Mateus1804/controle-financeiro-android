package com.mateuswilliam.financas.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.CaptureIssue
import com.mateuswilliam.financas.core.ExpenseFilter
import com.mateuswilliam.financas.core.ExpenseDimension
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.notifications.BankNotificationListener
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FinanceApp(model: FinanceViewModel, resumeCount: Int, incomingDraft: String?, consumeDraft: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val connected by model.connected.collectAsStateWithLifecycle()
    val captureError by model.captureError.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val loadingApps by model.loadingApps.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthString)
    var categoryFilter by rememberSaveable { mutableStateOf<Category?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var expectedRevision by rememberSaveable { mutableStateOf<Int?>(null) }
    val draft = state.pending.find { it.id == selectedDraftId }
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val hasAccess = remember(resumeCount, permissionVersion) { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }
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

    fun openDraft(value: DraftEntity) { seen.add(value.id); selectedDraftId = value.id; selectedRecordId = null; expectedRevision = value.revision; editing = true }
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
    LaunchedEffect(incomingDraft, state.loaded, state.pending) {
        if (incomingDraft != null && state.loaded) {
            state.pending.find { it.id == incomingDraft }?.let { openDraft(it); tab = 1 }
                ?: run { model.message.value = "Esta notificação já foi revisada." }
            consumeDraft()
        }
    }
    LaunchedEffect(state.pending, editing) {
        if (!editing && incomingDraft == null) state.pending.firstOrNull {
            it.capturedAt >= model.sessionStartedAt && it.id !in seen
        }?.let(::openDraft)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val labels = listOf("Resumo", "Revisar", "Extrato", "Ajustes")
                val icons = listOf(R.drawable.ic_overview, R.drawable.ic_pending, R.drawable.ic_records, R.drawable.ic_settings)
                labels.forEachIndexed { index, label ->
                    if (index == 2) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FloatingActionButton(onClick = { selectedDraftId = null; selectedRecordId = null; expectedRevision = null; editing = true },
                            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            AppIcon(R.drawable.ic_add, description = "Novo lançamento")
                        }
                    }
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface),
                        icon = { if (index == 1 && state.pending.isNotEmpty()) BadgedBox(badge = { Badge { Text(state.pending.size.toString()) } }) {
                            AppIcon(icons[index])
                        } else AppIcon(icons[index]) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                when (tab) {
                    0 -> DashboardScreen(state, month, { monthString = it.toString() },
                        { model.preferences.hideAmounts(!state.settings.hideAmounts) },
                        { tab = 1 }, { categoryFilter = it; expenseDimension = null; tab = 2 },
                        onExpenseGroup = { categoryFilter = null; expenseDimension = it.dimension; expenseKey = it.key; expenseLabel = it.label; tab = 2 },
                        notice = if (state.loaded && captureIssue != CaptureIssue.READY && state.settings.dismissedCondition != captureIssue.name) {
                            { CaptureNotice(captureIssue, ::resolveCapture) { model.preferences.dismissCaptureNotice(captureIssue.name) } }
                        } else null)
                    1 -> PendingScreen(state.pending, state.settings.hideAmounts, ::openDraft, { model.dismiss(it) }, busy)
                    2 -> RecordsScreen(state.records, month, { monthString = it.toString() }, state.settings.hideAmounts,
                        categoryFilter, { categoryFilter = it }, { selectedRecordId = it.id; selectedDraftId = null; expectedRevision = null; editing = true },
                        expenseFilter = expenseFilter, onClearExpenseFilter = { expenseDimension = null })
                    3 -> SettingsScreen(state.settings, connected, hasAccess, canAlert, captureError,
                        onToggle = { enabled -> if (enabled && !state.settings.consented) disclosure = true else model.preferences.setEnabled(enabled) },
                        onApps = { if (!state.settings.consented) disclosure = true else { appPicker = true; model.loadApps() } },
                        onAccess = ::requestAccess,
                        onReconnect = { NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java)) },
                        onAlerts = {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                                .onFailure { model.message.value = "Abra as configurações de notificações do aplicativo no Android." }
                        },
                        onExport = { export.launch("movimentacoes-${YearMonth.now()}.csv") },
                        onClear = { clearDialog = true }, onSimulate = model::simulate,
                        onAppearance = model.preferences::setAppearance, busy = busy)
                }
            }
        }
    }

    if (editing && (selectedDraftId == null || draft != null) && (selectedRecordId == null || record != null)) key(selectedDraftId, selectedRecordId) {
        RecordEditor(draft, record, busy, onDismiss = { editing = false },
            onSave = { model.save(it, selectedDraftId, editing = selectedRecordId != null, expectedRevision = expectedRevision) { editing = false } },
            onIgnore = draft?.let { current -> { model.dismiss(current.id); editing = false } },
            onDelete = record?.let { current -> { model.delete(current.id) } },
            accounts = state.accounts, lastOrigin = state.settings.lastOrigin.ifBlank { state.records.firstOrNull()?.bank.orEmpty() }, onAddAccount = model::addAccount)
    }
    if (state.loaded && !busy && !state.settings.introSeen) CaptureIntroduction(
        onActivate = { model.preferences.finishIntro(); disclosure = true },
        onManual = { model.preferences.finishIntro() })
    if (disclosure) DisclosureDialog(onDismiss = { disclosure = false }, onAccept = {
        model.preferences.acceptDisclosure(); disclosure = false; appPicker = true; model.loadApps()
    })
    if (appPicker) AppPickerDialog(apps, loadingApps, state.settings.packages,
        onDismiss = { appPicker = false }, onSave = {
            model.preferences.selectPackages(it); appPicker = false
            if (it.isNotEmpty() && !hasAccess && state.settings.enabled) requestAccess()
        })
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Apagar todos os meus dados?") },
        text = { Text("Lançamentos, pendências e preferências serão apagados. A captura será desativada. Esta ação não pode ser desfeita.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; model.clear() }, enabled = !busy) { Text("Apagar dados") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancelar") } })
}

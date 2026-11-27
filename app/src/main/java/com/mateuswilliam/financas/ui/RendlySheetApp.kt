package com.mateuswilliam.financas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.CycleViewModel
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.AccountFunds
import com.mateuswilliam.financas.core.AccountOption
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.CycleCalendar
import com.mateuswilliam.financas.core.CycleConfiguration
import com.mateuswilliam.financas.core.FinancialPeriods
import com.mateuswilliam.financas.core.SheetSummary
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RendlySheetApp(finance: FinanceViewModel, cycles: CycleViewModel, onLockChange: (Boolean) -> Unit) {
    val state by finance.state.collectAsStateWithLifecycle()
    val settings by finance.preferences.state.collectAsStateWithLifecycle()
    val calendar by cycles.dataset.collectAsStateWithLifecycle()
    val calendarError by cycles.loadError.collectAsStateWithLifecycle()
    val calendarBusy by cycles.busy.collectAsStateWithLifecycle()
    val calendarMessage by cycles.message.collectAsStateWithLifecycle()
    val saving by finance.busy.collectAsStateWithLifecycle()
    val message by finance.message.collectAsStateWithLifecycle()
    val revision by finance.financialDataRevision.collectAsStateWithLifecycle()
    val busy = saving || calendarBusy
    val hidden = settings.hideAmounts

    var showingRecords by rememberSaveable { mutableStateOf(false) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var cycleEditor by rememberSaveable { mutableStateOf(false) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var accountKey by rememberSaveable { mutableStateOf<String?>(null) }
    var entryKind by rememberSaveable { mutableStateOf<SheetEntryKind?>(null) }
    var editRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var editBalanceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var savingToReserve by rememberSaveable { mutableStateOf(false) }
    var lastRevision by rememberSaveable { mutableLongStateOf(revision) }
    val snackbar = remember { SnackbarHostState() }

    fun openEntry(kind: SheetEntryKind, recordId: String? = null, balanceId: Long? = null, toReserve: Boolean = false) {
        finance.message.value = null
        editRecordId = recordId
        editBalanceId = balanceId
        savingToReserve = toReserve
        entryKind = kind
    }

    val zone = ZoneId.systemDefault()
    val today = remember(state.asOf, zone) { Instant.ofEpochMilli(state.asOf).atZone(zone).toLocalDate() }
    val rules = calendar?.cycleRules.orEmpty()
    val currentMonth = remember(today, rules) { YearMonth.from(CycleCalendar.periodContaining(today, rules).start) }
    val month = selectedMonth?.let(YearMonth::parse) ?: currentMonth
    val period = remember(month, rules) { FinancialPeriods.month(month, rules) }
    val accountRows = state.accountBalances
    val references = state.balanceReferences
    val records = state.records
    val summary = if (state.loaded && calendar != null && calendarError == null && !showingSettings) {
        val asOf = state.asOf
        calculateInBackground(listOf(accountRows, references, records, period, zone, asOf)) {
            SheetSummary.calculate(records, accountRows.map { it.account },
                accountRows.filter { it.archived }.map { it.account.key }.toSet(), references, period, zone, asOf)
        }.value
    } else null
    val editRecord = records.firstOrNull { it.id == editRecordId }
    val editBalance = references.firstOrNull { it.id == editBalanceId }

    LaunchedEffect(revision) {
        if (revision != lastRevision) {
            lastRevision = revision
            selectedMonth = null
            category = null
            accountKey = null
            entryKind = null
        }
    }
    LaunchedEffect(state.loaded, editRecordId, editBalanceId, editRecord, editBalance) {
        if (state.loaded && ((editRecordId != null && editRecord == null) || (editBalanceId != null && editBalance == null))) {
            editRecordId = null
            editBalanceId = null
            entryKind = null
        }
    }
    LaunchedEffect(message, entryKind) {
        if (message != null && entryKind == null) {
            snackbar.showSnackbar(message.orEmpty())
            if (finance.message.value == message) finance.message.value = null
        }
    }
    LaunchedEffect(calendarMessage, cycleEditor) {
        if (calendarMessage != null && !cycleEditor) {
            snackbar.showSnackbar(calendarMessage.orEmpty())
            if (cycles.message.value == calendarMessage) cycles.message.value = null
        }
    }

    fun openCycle() {
        cycles.message.value = null
        cycleEditor = true
    }
    BackHandler(showingSettings) { if (!busy) showingSettings = false }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (showingSettings) IconButton({ showingSettings = false }, enabled = !busy) {
                        AppIcon(R.drawable.ic_chevron_left, description = "Voltar")
                    }
                },
                title = {
                    if (showingSettings) Text("Ajustes")
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(32.dp))
                        Column {
                            Text("Rendly", style = MaterialTheme.typography.titleLarge)
                            if (settings.displayName.isNotBlank()) {
                                Text("Olá, ${privateDisplayText(settings.displayName, hidden)}! 👋🏼", Modifier.testTag("rendly-greeting"),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                actions = {
                    if (!showingSettings) {
                        IconButton({ finance.preferences.hideAmounts(!hidden) }) {
                            AppIcon(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                description = if (hidden) "Mostrar valores" else "Ocultar valores")
                        }
                        IconButton({ showingSettings = true }) { AppIcon(R.drawable.ic_settings, description = "Ajustes") }
                    }
                },
            )
        },
        bottomBar = {
            if (!showingSettings) Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = !showingRecords, onClick = { showingRecords = false },
                        icon = { AppIcon(selectedIcon(R.drawable.ic_overview, !showingRecords)) }, label = { Text("Resumo") })
                    NavigationBarItem(selected = showingRecords, onClick = { showingRecords = true },
                        icon = { AppIcon(selectedIcon(R.drawable.ic_records, showingRecords)) }, label = { Text("Lançamentos") })
                }
            }
        },
        floatingActionButton = {
            if (!showingSettings && state.loaded && calendar != null && !busy) {
                FloatingActionButton(onClick = { openEntry(SheetEntryKind.EXPENSE) }, modifier = Modifier.testTag("add-movement"),
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    AppIcon(R.drawable.ic_add, description = "Novo lançamento")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SheetSettings(finance, settings, rules.lastOrNull { it.effectiveStart <= today }?.day ?: 1,
                onCycle = if (state.loaded && calendar != null && calendarError == null) ::openCycle else null,
                onLockChange = onLockChange, visible = showingSettings)
            if (!showingSettings) {
                val totals = summary?.getOrNull()
                val loadError = state.loadError ?: calendarError
                when {
                    loadError != null -> Column(Modifier.padding(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(loadError)
                        OutlinedButton({ finance.refreshBalances(); cycles.refresh() }) { Text("Tentar novamente") }
                    }
                    summary?.isFailure == true -> Text("Não foi possível calcular os valores deste período.", Modifier.padding(FinanceSpacing.page))
                    totals == null -> Text("Carregando lançamentos…", Modifier.padding(FinanceSpacing.page))
                    showingRecords -> SheetEntries(totals, records, references, month, period, state.asOf, zone, hidden,
                        category, accountKey, onCategory = { category = it }, onAccount = { accountKey = it },
                        onMonth = { selectedMonth = it.toString() },
                        onEdit = { openEntry(SheetEntryKind.of(it.movement), recordId = it.id) },
                        onBalance = { openEntry(SheetEntryKind.OPENING, balanceId = it.id) })
                    else -> SheetOverview(totals, month, period, zone, hidden, onMonth = { selectedMonth = it.toString() },
                        onSaveToReserve = { openEntry(SheetEntryKind.TRANSFER, toReserve = true) },
                        onCategory = { category = it; accountKey = null; showingRecords = true },
                        onToggleArchive = finance::setAccountArchived)
                }
            }
        }
    }

    calendar?.takeIf { cycleEditor }?.let { dataset ->
        val linkedKeys = remember(records) { records.mapNotNull { it.forecastOccurrenceKey } }
        val currentDay = rules.lastOrNull { it.effectiveStart <= today }?.day ?: 1
        fun proposal(day: Int) = CycleConfiguration.rulesAfterChange(day, today, dataset, linkedKeys)
        CycleDayEditor(currentDay, busy, preview = { day ->
            val updated = proposal(day)
            CycleCalendar.periodContaining(maxOf(today, updated.last().effectiveStart), updated)
        }, onDismiss = { cycleEditor = false },
            onSave = { day -> cycles.setCycleDay(day) { cycleEditor = false; selectedMonth = null } },
            firstConfiguration = proposal(currentDay).last().effectiveStart <= today,
            error = calendarMessage)
    }
    entryKind?.takeIf { state.loaded && (editRecordId == null || editRecord != null) &&
        (editBalanceId == null || editBalance != null) }?.let { kind ->
        val options = state.accounts
        val reserveMissing = accountRows.none { it.account.key == AccountFunds.RESERVE_KEY }
        val accounts = if (reserveMissing) options + AccountOption(AccountFunds.RESERVE_KEY, AccountFunds.RESERVE_NAME, 0) else options
        key(editRecordId, editBalanceId, kind) {
            SheetEntryEditor(kind, editRecord, editBalance, accountRows.map { it.account }, accounts, today, zone,
                lastAccount = settings.lastOrigin, toReserve = savingToReserve, hidden = hidden, busy = saving, writeError = message,
                onDismiss = { entryKind = null; finance.message.value = null },
                onAddAccount = finance::addAccount,
                onRecord = { record -> finance.save(record, editRecord != null) { entryKind = null } },
                onBalance = { label, cents, at, reason ->
                    finance.configureAccountBalance(label, cents, at, reason) { entryKind = null }
                },
                onDelete = finance::delete)
        }
    }
}

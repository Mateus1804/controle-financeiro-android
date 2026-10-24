package com.mateuswilliam.financas

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.Ledger
import com.mateuswilliam.financas.core.NotificationInput
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.AccountOption
import com.mateuswilliam.financas.core.MonthlyBudget
import com.mateuswilliam.financas.core.RecurringPlan
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.data.StagedRestore
import com.mateuswilliam.financas.data.CaptureSettings
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.data.DraftChangedException
import com.mateuswilliam.financas.notifications.ReviewNotifications
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FinanceState(
    val records: List<Record> = emptyList(),
    val pending: List<DraftEntity> = emptyList(),
    val settings: CaptureSettings = CaptureSettings(),
    val loaded: Boolean = false,
    val accounts: List<AccountOption> = emptyList(),
    val manualRecords: List<Record> = emptyList(),
    val budgets: List<MonthlyBudget> = emptyList(),
    val recurring: List<RecurringPlan> = emptyList(),
)

data class SelectableApp(val packageName: String, val label: String)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApplication
    private val repository = app.repository
    val preferences = app.settings
    val sessionStartedAt = System.currentTimeMillis()
    val connected = app.listenerConnected
    val captureError = app.captureError
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val apps = MutableStateFlow<List<SelectableApp>>(emptyList())
    val loadingApps = MutableStateFlow(false)
    private val baseState = combine(repository.records, repository.pending, preferences.state, repository.accounts) { records, drafts, settings, accounts ->
        FinanceState(records, drafts, settings, loaded = true, accounts = accounts)
    }
    val state = combine(baseState, repository.manualRecords, repository.budgets, repository.recurring) { base, manual, budgets, recurring ->
        base.copy(manualRecords = manual, budgets = budgets, recurring = recurring)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())
    val stagedRestore = MutableStateFlow<StagedRestore?>(null)
    private var encryptedBackup: ByteArray? = null

    fun save(record: Record, draftId: String?, editing: Boolean, expectedRevision: Int?, onSaved: () -> Unit) = action {
        val saved = try { repository.save(record, draftId, editing, expectedRevision) }
        catch (exception: DraftChangedException) { return@action exception.message.orEmpty() }
        if (draftId != null) ReviewNotifications.cancel(app, draftId)
        if (saved) onSaved()
        if (saved) "Lançamento registrado." else "Este item já foi revisado ou removido."
    }

    fun dismiss(id: String, onDismissed: () -> Unit = {}) = action {
        repository.dismiss(id)
        ReviewNotifications.cancel(app, id)
        onDismissed()
        "Notificação ignorada. Nenhum lançamento foi criado."
    }

    fun delete(id: String) = action { repository.deleteRecord(id); "Lançamento removido." }
    fun clear() = action { repository.clear(); ReviewNotifications.cancelAll(app); "Dados apagados e captura desativada." }

    fun saveBudget(budget: MonthlyBudget, onSaved: () -> Unit) = action { repository.saveBudget(budget); onSaved(); "Limite mensal salvo." }
    fun deleteBudget(month: String, category: Category) = action { repository.deleteBudget(month, category); "Limite mensal removido." }
    fun saveRecurring(plan: RecurringPlan, onSaved: () -> Unit) = action { repository.saveRecurring(plan); onSaved(); "Previsão mensal salva. Nenhum lançamento foi criado." }
    fun skipRecurring(plan: RecurringPlan) = action {
        if (repository.skipRecurring(plan.id, plan.nextDate)) "Vencimento pulado. Nenhum lançamento foi criado." else "Esse vencimento já foi tratado."
    }
    fun confirmRecurring(id: String, due: String, record: Record, onSaved: () -> Unit) = action {
        if (repository.confirmRecurring(id, due, record)) { onSaved(); "Lançamento confirmado e próxima previsão atualizada." }
        else "A previsão mudou ou já foi tratada. Confira o planejamento."
    }

    fun createBackup(password: CharArray, onReady: () -> Unit) {
        if (busy.value) { password.fill('\u0000'); return }
        action {
            encryptedBackup?.fill(0)
            encryptedBackup = app.backups.create(password)
            onReady()
            "Backup criptografado preparado. Escolha o destino."
        }
    }
    fun writeBackup(uri: Uri?) {
        if (uri == null) { encryptedBackup?.fill(0); encryptedBackup = null; return }
        action {
            val bytes = encryptedBackup ?: throw IllegalArgumentException("Prepare o backup novamente. A senha não foi guardada.")
            try {
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                    output.use { it.write(bytes); it.flush() }
                }
            } finally { bytes.fill(0); encryptedBackup = null }
            "Backup salvo. Guarde a senha separadamente e confira a recuperação antes de depender dele."
        }
    }
    fun stageBackup(uri: Uri, password: CharArray) {
        if (busy.value) { password.fill('\u0000'); return }
        action {
            try {
                app.maintenanceReady.await()
                stagedRestore.value?.let { app.backups.discard(it) }
                stagedRestore.value = null
                stagedRestore.value = withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri) ?: error("Arquivo indisponível")
                    app.backups.stage(input, password)
                }
            } finally { password.fill('\u0000') }
            "Backup conferido em uma base temporária. Confira a prévia antes de substituir os dados."
        }
    }
    fun cancelRestore() {
        if (busy.value) return
        val staged = stagedRestore.value ?: return
        stagedRestore.value = null
        viewModelScope.launch { app.backups.discard(staged) }
    }
    fun restoreBackup() = action {
        val staged = stagedRestore.value ?: throw IllegalArgumentException("Selecione um backup para conferir.")
        try { app.backups.restore(staged); ReviewNotifications.cancelAll(app) }
        finally { stagedRestore.value = null }
        "Dados restaurados. A captura e os lembretes ficaram pausados; as autorizações do Android não são transferidas."
    }

    override fun onCleared() {
        encryptedBackup?.fill(0)
        stagedRestore.value?.let { staged -> app.background.launch { app.backups.discard(staged) } }
        super.onCleared()
    }

    private fun action(block: suspend () -> String) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { message.value = block() }
            catch (exception: IllegalArgumentException) { message.value = exception.message ?: "Confira os campos." }
            catch (_: Exception) { message.value = "Não foi possível concluir. Seus dados não foram descartados. Tente novamente." }
            finally { busy.value = false }
        }
    }

    fun loadApps() {
        if (!preferences.state.value.consented || loadingApps.value) return
        loadingApps.value = true
        viewModelScope.launch {
            try {
                apps.value = withContext(Dispatchers.IO) {
                    val manager = app.packageManager
                    manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .map { SelectableApp(it.activityInfo.packageName, it.loadLabel(manager).toString().take(80)) }
                        .distinctBy { it.packageName }.filter { it.packageName != app.packageName }
                        .sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
                }
            } catch (_: Exception) { message.value = "Não foi possível listar os aplicativos disponíveis." }
            finally { loadingApps.value = false }
        }
    }

    fun simulate() {
        if (!BuildConfig.DEBUG) return
        action {
            val examples = listOf(
                "Inter · exemplo" to "Pix enviado de R$ 68,50. Restaurante Exemplo.",
                "PicPay · exemplo" to "Pix recebido de R$ 250,00.",
                "Nubank · exemplo" to "Compra aprovada no cartão de R$ 119,90. Posto de combustível.",
            )
            examples.forEach { (bank, text) ->
                val key = UUID.randomUUID().toString()
                repository.capture(NotificationInput("simulacao.local", "simulacao:$key", bank,
                    "SIMULAÇÃO — dados fictícios", text, System.currentTimeMillis()), simulation = true)
            }
            "Três exemplos fictícios foram adicionados à revisão."
        }
    }

    suspend fun exportCsv(): String = Ledger.csv(repository.exportRecords(), ZoneId.systemDefault())
    suspend fun addAccount(name: String): String = repository.addAccount(name)
}

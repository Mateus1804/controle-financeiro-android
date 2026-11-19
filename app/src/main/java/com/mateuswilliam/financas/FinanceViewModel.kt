package com.mateuswilliam.financas

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.NotificationInput
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.AccountOption
import com.mateuswilliam.financas.core.AccountBalance
import com.mateuswilliam.financas.core.AccountBalanceReference
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.data.StagedRestore
import com.mateuswilliam.financas.data.CaptureSettings
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.data.DraftChangedException
import com.mateuswilliam.financas.notifications.ReviewNotifications
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
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
    val accountBalances: List<AccountBalance> = emptyList(),
    val balanceReferences: List<AccountBalanceReference> = emptyList(),
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
    private val balanceClock = MutableStateFlow(System.currentTimeMillis())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val balances = balanceClock.flatMapLatest { repository.observeAccountBalances(it) }
    private val baseState = combine(repository.ledger, repository.pending, preferences.state, repository.accounts) { ledger, drafts, settings, accounts ->
        FinanceState(ledger.records, drafts, settings, loaded = true, accounts = accounts, manualRecords = ledger.manualRecords)
    }
    val state = combine(baseState, balances, repository.accountBalanceEvents) { base, balances, references ->
        base.copy(accountBalances = balances, balanceReferences = references)
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
    fun refreshBalances() { balanceClock.value = System.currentTimeMillis() }
    fun configureAccountBalance(name: String, cents: Long, effectiveAt: Long, reason: String, onSaved: () -> Unit) = action {
        repository.configureAccountBalance(name, cents, effectiveAt, reason)
        refreshBalances()
        onSaved()
        "Saldo de referência salvo. Nenhuma receita ou despesa foi criada."
    }
    fun setAccountArchived(key: String, archived: Boolean) = action {
        repository.setAccountArchived(key, archived)
        if (archived) "Conta arquivada. Saldo e histórico foram preservados." else "Conta reativada."
    }
    fun clear() = action {
        val preferencesCleared = repository.clear()
        ReviewNotifications.cancelAll(app)
        app.merchantCategories.clear()
        if (preferencesCleared) "Dados apagados e captura desativada."
        else "Os dados financeiros foram apagados, mas as preferências não puderam ser limpas. Captura e lembretes permanecem pausados. Confira o armazenamento e tente novamente."
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
            finally { refreshBalances(); busy.value = false }
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

    suspend fun addAccount(name: String): String = repository.addAccount(name)
}

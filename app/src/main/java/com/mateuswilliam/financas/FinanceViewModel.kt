package com.mateuswilliam.financas

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.AccountOption
import com.mateuswilliam.financas.core.AccountBalance
import com.mateuswilliam.financas.core.AccountBalanceReference
import com.mateuswilliam.financas.data.AccountLedger
import com.mateuswilliam.financas.data.StagedRestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FinanceState(
    val records: List<Record> = emptyList(),
    val loaded: Boolean = false,
    val accounts: List<AccountOption> = emptyList(),
    val accountBalances: List<AccountBalance> = emptyList(),
    val balanceReferences: List<AccountBalanceReference> = emptyList(),
    val asOf: Long = System.currentTimeMillis(),
    val loadError: String? = null,
)

internal class FinanceStateSources(
    val ledger: Flow<AccountLedger>,
    val accounts: Flow<List<AccountOption>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeFinanceState(
    refreshes: Flow<Long>,
    loadSources: suspend () -> FinanceStateSources,
): Flow<FinanceState> = refreshes.flatMapLatest {
    flow {
        val sources = loadSources()
        emitAll(combine(sources.ledger, sources.accounts) { snapshot, options ->
            FinanceState(
                records = snapshot.records,
                loaded = true,
                accounts = options,
                accountBalances = snapshot.balances,
                balanceReferences = snapshot.references,
                asOf = snapshot.asOf,
            )
        })
    }.catch { exception ->
        if (exception !is Exception || exception is CancellationException) throw exception
        emit(FinanceState(loadError = "Não foi possível carregar os dados financeiros. Tente novamente."))
    }
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApplication
    val preferences = app.settings
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val refreshRequests = MutableStateFlow(0L)
    val state = observeFinanceState(refreshRequests) {
        val repository = loadRepository()
        FinanceStateSources(repository.observeAccountLedger(clockChanges()), repository.accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())
    val stagedRestore = MutableStateFlow<StagedRestore?>(null)
    private val dataRevision = MutableStateFlow(0L)
    val financialDataRevision = dataRevision.asStateFlow()
    private val backupDestinationRequest = MutableStateFlow<String?>(null)
    val backupDestination = backupDestinationRequest.asStateFlow()
    val backupCleanupPending = app.backups.cleanupPending
    private var encryptedBackup: ByteArray? = null

    private suspend fun loadRepository() = withContext(Dispatchers.IO) { app.repository }

    private fun clockChanges() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply { addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { app.unregisterReceiver(receiver) }
    }.conflate()

    fun save(record: Record, editing: Boolean, onSaved: () -> Unit) = action {
        val saved = loadRepository().save(record, draftId = null, editing = editing)
        if (saved) onSaved()
        if (saved) "Lançamento salvo." else "Este lançamento já foi removido."
    }

    fun delete(id: String) = action { loadRepository().deleteRecord(id); "Lançamento removido." }
    fun refreshBalances() { refreshRequests.update { it + 1 } }
    fun configureAccountBalance(name: String, cents: Long, effectiveAt: Long, reason: String, onSaved: () -> Unit) = action {
        loadRepository().configureAccountBalance(name, cents, effectiveAt, reason)
        onSaved()
        "Saldo de referência salvo. Nenhuma receita ou despesa foi criada."
    }
    fun setAccountArchived(key: String, archived: Boolean) = action {
        loadRepository().setAccountArchived(key, archived)
        if (archived) "Conta arquivada. Saldo e histórico foram preservados." else "Conta reativada."
    }
    fun createBackup(password: CharArray, fileName: String) {
        if (busy.value) { password.fill('\u0000'); return }
        if (encryptedBackup != null) {
            password.fill('\u0000')
            message.value = "Conclua a exportação do backup que já está aberta."
            return
        }
        action {
            encryptedBackup = app.backups.create(password)
            backupDestinationRequest.value = fileName
            "Backup criptografado preparado. Escolha o destino."
        }
    }

    fun takeBackupDestination(): String? = backupDestinationRequest.value.also { backupDestinationRequest.value = null }

    fun writeBackup(uri: Uri?) {
        val bytes = encryptedBackup
        encryptedBackup = null
        if (uri == null) { bytes?.fill(0); return }
        if (bytes == null) {
            message.value = "Prepare o backup novamente. A senha não foi guardada."
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                    output.use { it.write(bytes); it.flush() }
                }
                message.value = "Backup salvo. Guarde a senha separadamente e confira a recuperação antes de depender dele."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Não foi possível salvar o backup. Escolha outro destino."
            } finally {
                bytes.fill(0)
            }
        }
    }
    fun stageBackup(uri: Uri, password: CharArray) {
        if (busy.value) { password.fill('\u0000'); return }
        action {
            try {
                app.maintenanceReady.await()
                stagedRestore.value?.let { app.backups.discard(it) }
                stagedRestore.value = null
                stagedRestore.value = app.backups.stage(password) {
                    app.contentResolver.openInputStream(uri) ?: error("Arquivo indisponível")
                }
            } finally { password.fill('\u0000') }
            "Backup conferido em uma base temporária. Confira a prévia antes de substituir os dados."
        }
    }
    fun cancelRestore() {
        if (busy.value) return
        val staged = stagedRestore.value ?: return
        stagedRestore.value = null
        app.background.launch { app.backups.discard(staged) }
    }

    fun retryBackupCleanup() {
        app.background.launch { app.backups.removeAbandonedStages() }
    }

    fun restoreBackup() = action {
        val staged = stagedRestore.value ?: throw IllegalArgumentException("Selecione um backup para conferir.")
        try {
            val displayPreferencesSaved = app.backups.restore(staged)
            dataRevision.update { it + 1 }
            if (displayPreferencesSaved) {
                "Dados restaurados."
            } else {
                "Dados financeiros restaurados. Falha ao salvar as preferências de exibição."
            }
        } finally {
            stagedRestore.value = null
        }
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
            catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: IllegalArgumentException) { message.value = exception.message ?: "Confira os campos." }
            catch (_: Exception) { message.value = "Não foi possível concluir. Seus dados não foram descartados. Tente novamente." }
            finally { busy.value = false }
        }
    }

    suspend fun addAccount(name: String): String = loadRepository().addAccount(name)
}

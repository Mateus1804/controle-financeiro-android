package com.mateuswilliam.financas

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.Ledger
import com.mateuswilliam.financas.core.NotificationInput
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.AccountOption
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
    val state = combine(repository.records, repository.pending, preferences.state, repository.accounts) { records, drafts, settings, accounts ->
        FinanceState(records, drafts, settings, loaded = true, accounts = accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceState())

    fun save(record: Record, draftId: String?, editing: Boolean, expectedRevision: Int?, onSaved: () -> Unit) = action {
        val saved = try { repository.save(record, draftId, editing, expectedRevision) }
        catch (exception: DraftChangedException) { onSaved(); return@action exception.message.orEmpty() }
        if (draftId != null) ReviewNotifications.cancel(app, draftId)
        onSaved()
        if (saved) "Lançamento registrado." else "Este item já foi revisado ou removido."
    }

    fun dismiss(id: String) = action {
        repository.dismiss(id)
        ReviewNotifications.cancel(app, id)
        "Notificação ignorada. Nenhum lançamento foi criado."
    }

    fun delete(id: String) = action { repository.deleteRecord(id); "Lançamento removido." }
    fun clear() = action { repository.clear(); ReviewNotifications.cancelAll(app); "Dados apagados e captura desativada." }

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

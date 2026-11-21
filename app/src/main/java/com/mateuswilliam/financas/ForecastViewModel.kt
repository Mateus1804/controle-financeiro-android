package com.mateuswilliam.financas

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.forecastCsv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Planning edits forecast rules, cycle settings and the reserve goal. */
class ForecastViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApplication
    val dataset = MutableStateFlow<ForecastDataset?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val loadError = MutableStateFlow<String?>(null)
    private var observing: Job? = null
    private var preparedCsv: String? = null
    private val csvDestinationRequest = MutableStateFlow<String?>(null)
    val csvDestination = csvDestinationRequest.asStateFlow()
    init { observe() }

    private suspend fun loadRepository() = withContext(Dispatchers.IO) { app.forecasts }

    private fun observe() {
        observing = viewModelScope.launch {
            try {
                app.maintenanceReady.await()
                loadRepository().dataset.collect { dataset.value = it; loadError.value = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadError.value = "Não foi possível abrir o planejamento. Seus dados foram preservados. Tente novamente." }
        }
    }

    fun refresh() { if (observing?.isActive != true) observe() }
    fun saveRule(rule: ForecastRule, replaceOccurrenceKey: String? = null, onSaved: () -> Unit) = action {
        loadRepository().saveRule(rule, replaceOccurrenceKey = replaceOccurrenceKey)
        onSaved()
        "Previsão salva. O orçamento e as projeções foram atualizados."
    }
    fun saveOverride(value: ForecastOverride, onSaved: () -> Unit = {}) = action {
        loadRepository().saveOverride(value)
        onSaved()
        if (value.ignored) "Ocorrência ignorada neste período." else "Ocorrência atualizada. Os outros períodos foram preservados."
    }
    fun ignore(line: ForecastLine) {
        val previous = dataset.value?.overrides?.firstOrNull { it.itemId == line.itemId && it.occurrenceKey == line.occurrenceKey }
            ?: ForecastOverride(line.itemId, line.occurrenceKey)
        saveOverride(previous.copy(ignored = !line.ignored))
    }
    fun stop(line: ForecastLine) = action {
        loadRepository().saveRule(line.rule.copy(effectiveFrom = line.date, active = false))
        "Previsões interrompidas desta ocorrência em diante. O histórico anterior foi preservado."
    }
    fun setCycleDay(day: Int, onSaved: () -> Unit) = action {
        loadRepository().setCycleDay(day, LocalDate.now())
        onSaved()
        "Dia do ciclo salvo. Confira as datas completas do período."
    }
    fun saveReserveGoal(cents: Long?, onSaved: () -> Unit) = action {
        loadRepository().setReserveGoal(cents)
        onSaved()
        "Meta da reserva salva."
    }

    fun prepareCsv(report: ForecastReport) = action {
        if (preparedCsv != null) return@action "Conclua a exportação que já está aberta."
        preparedCsv = withContext(Dispatchers.Default) { forecastCsv(report) }
        csvDestinationRequest.value = "previsoes-${report.period.start}.csv"
        null
    }

    fun takeCsvDestination(): String? = csvDestinationRequest.value.also { csvDestinationRequest.value = null }

    fun writeCsv(uri: Uri?) {
        val content = preparedCsv
        preparedCsv = null
        if (uri == null) return
        if (content == null) {
            message.value = "O CSV preparado não está mais disponível. Exporte novamente."
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                    output.writer(Charsets.UTF_8).use { it.write(content) }
                }
                message.value = "Previsões do ciclo exportadas."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Não foi possível exportar. Escolha outro destino."
            }
        }
    }

    private fun action(block: suspend () -> String?) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { message.value = block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) { message.value = error.message ?: "Confira os valores informados." }
            catch (_: Exception) { message.value = "Não foi possível concluir. Confira os dados antes de tentar novamente." }
            finally { busy.value = false }
        }
    }
}

package com.mateuswilliam.financas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** The planner writes forecasts/reserve events only; it never records receipts or payments. */
class ForecastViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApplication
    private val repository = app.forecasts
    val dataset = MutableStateFlow<ForecastDataset?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val loadError = MutableStateFlow<String?>(null)
    private var observing: Job? = null
    init { observe() }
    private fun observe() {
        observing = viewModelScope.launch {
            try {
                app.maintenanceReady.await()
                repository.initialize()
                repository.dataset.collect { dataset.value = it; loadError.value = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadError.value = "Não foi possível abrir o planejamento. Seus dados foram preservados. Tente novamente." }
        }
    }

    fun saveSalary(month: YearMonth, cents: Long, repeat: Boolean, onSaved: () -> Unit) = action {
        repository.saveSalary(month, cents, repeat)
        onSaved()
        "Salário atualizado no resultado e nas projeções."
    }

    fun refresh() = viewModelScope.launch {
        if (observing?.isActive != true) { observe(); return@launch }
        try {
            repository.initialize()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = "Não foi possível atualizar o planejamento. Tente novamente." }
    }
    fun saveRule(rule: ForecastRule, onSaved: () -> Unit) = action {
        repository.saveRule(rule)
        onSaved()
        "Previsão salva. O orçamento e as projeções foram atualizados."
    }
    fun saveOverride(value: ForecastOverride, onSaved: () -> Unit = {}) = action {
        repository.saveOverride(value)
        onSaved()
        if (value.ignored) "Ocorrência ignorada neste período." else "Ocorrência atualizada. Os outros períodos foram preservados."
    }
    fun ignore(line: ForecastLine) {
        val previous = dataset.value?.overrides?.firstOrNull { it.itemId == line.itemId && it.occurrenceKey == line.occurrenceKey }
            ?: ForecastOverride(line.itemId, line.occurrenceKey)
        saveOverride(previous.copy(ignored = !line.ignored))
    }
    fun stop(line: ForecastLine) = action {
        repository.saveRule(line.rule.copy(effectiveFrom = line.date, active = false))
        "Previsões interrompidas desta ocorrência em diante. O histórico anterior foi preservado."
    }
    fun setCycleDay(day: Int, onSaved: () -> Unit) = action {
        repository.setCycleDay(day, LocalDate.now())
        onSaved()
        "Dia do ciclo salvo. Confira as datas completas do período."
    }
    fun saveReserve(kind: ReserveEventKind, cents: Long, reason: String, cycle: LocalDate?, onSaved: () -> Unit) = action {
        repository.addReserveEvent(kind, cents, reason, cycle)
        onSaved()
        "Reserva atualizada. Renda, gastos e percentual do orçamento foram preservados."
    }
    fun saveReserveGoal(cents: Long?, onSaved: () -> Unit) = action {
        repository.setReserveGoal(cents)
        onSaved()
        "Meta da reserva salva."
    }
    private fun action(block: suspend () -> String) {
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

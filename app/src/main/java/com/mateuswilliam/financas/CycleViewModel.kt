package com.mateuswilliam.financas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateuswilliam.financas.core.ForecastDataset
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Usa o calendário salvo no Rendly v1 e respeita ciclos com vínculos antigos. */
class CycleViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApplication
    private var observer: Job? = null
    val dataset = MutableStateFlow<ForecastDataset?>(null)
    val loadError = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init { refresh() }

    private suspend fun repository() = withContext(Dispatchers.IO) { app.forecasts }

    fun refresh() {
        if (observer?.isActive == true) return
        observer = viewModelScope.launch {
            try {
                repository().dataset.collect { calendar ->
                    dataset.value = calendar
                    loadError.value = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadError.value = "Não foi possível carregar o calendário financeiro."
            }
        }
    }

    fun setCycleDay(day: Int, onSaved: () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try {
                repository().setCycleDay(day, LocalDate.now())
                onSaved()
                message.value = "Ciclo atualizado."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Não foi possível atualizar o ciclo. Tente novamente."
            } finally {
                busy.value = false
            }
        }
    }
}

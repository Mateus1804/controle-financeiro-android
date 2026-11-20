package com.mateuswilliam.financas

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mateuswilliam.financas.data.CapturePreferences
import com.mateuswilliam.financas.data.FinanceDatabase
import com.mateuswilliam.financas.data.FinanceRepository
import com.mateuswilliam.financas.data.BackupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.mateuswilliam.financas.notifications.RecurringReminders

class FinanceApplication : Application() {
    val settings by lazy { CapturePreferences(this) }
    val merchantCategories by lazy { com.mateuswilliam.financas.data.MerchantCategoryStore(this) }
    val database by lazy {
        // Reset financeiro solicitado pelo proprietário: esta versão não migra dados anteriores.
        if (discardLegacyDatabase(this, settings)) {
            settings.rememberOrigin("")
            merchantCategories.clear()
        }
        Room.databaseBuilder(this, FinanceDatabase::class.java, "rendly-v1.db").build()
    }
    val repository by lazy { FinanceRepository(database, settings) }
    val forecasts by lazy { com.mateuswilliam.financas.data.ForecastRepository(database) }
    val backups by lazy { BackupStore(this, repository) }
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val maintenanceReady by lazy { background.async { backups.removeAbandonedStages() } }
    val listenerConnected = MutableStateFlow(false)
    val captureError = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        maintenanceReady
        background.launch {
            settings.state.map { it.remindersEnabled }.distinctUntilChanged().collect { RecurringReminders.schedule(this@FinanceApplication) }
        }
    }
}

internal fun discardLegacyDatabase(context: Context, settings: CapturePreferences): Boolean {
    if (!context.getDatabasePath("financas.db").exists()) return false
    settings.pauseForRestore()
    check(context.deleteDatabase("financas.db")) { "Não foi possível remover o banco anterior." }
    return true
}

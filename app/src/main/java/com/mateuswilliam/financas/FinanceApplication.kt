package com.mateuswilliam.financas

import android.app.Application
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
    val database by lazy { Room.databaseBuilder(this, FinanceDatabase::class.java, "financas.db")
        .addMigrations(FinanceDatabase.MIGRATION_1_2, FinanceDatabase.MIGRATION_2_3, FinanceDatabase.MIGRATION_3_4).build() }
    val repository by lazy { FinanceRepository(database, settings) }
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

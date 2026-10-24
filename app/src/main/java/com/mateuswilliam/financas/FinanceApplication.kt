package com.mateuswilliam.financas

import android.app.Application
import androidx.room.Room
import com.mateuswilliam.financas.data.CapturePreferences
import com.mateuswilliam.financas.data.FinanceDatabase
import com.mateuswilliam.financas.data.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class FinanceApplication : Application() {
    val settings by lazy { CapturePreferences(this) }
    val database by lazy { Room.databaseBuilder(this, FinanceDatabase::class.java, "financas.db").build() }
    val repository by lazy { FinanceRepository(database, settings) }
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val listenerConnected = MutableStateFlow(false)
    val captureError = MutableStateFlow(false)
}

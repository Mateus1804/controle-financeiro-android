package com.mateuswilliam.financas.notifications

import android.content.Context
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.core.CycleCalendar
import com.mateuswilliam.financas.core.ForecastCalculator
import com.mateuswilliam.financas.core.ForecastKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Informational reminders only; delivery never changes forecast or financial data. */
object ForecastReminders {
    private val deliveryMutex = Mutex()

    suspend fun notifyToday(app: FinanceApplication, today: LocalDate = LocalDate.now()) {
        deliveryMutex.withLock {
            if (!app.settings.snapshot().remindersEnabled) return
            app.forecasts.initialize()
            val dataset = app.forecasts.dataset.first()
            val period = CycleCalendar.periodContaining(today, dataset.cycleRules)
            val lines = ForecastCalculator.report(dataset, period).lines.filter {
                it.included && !it.ignored && it.date == today && !it.rule.cycleAnchored
            }
            // Device-local delivery history must not be exported with financial backups.
            val sent = app.getSharedPreferences("forecast_reminder_deliveries", Context.MODE_PRIVATE)
            for (line in lines) {
                if (!app.settings.snapshot().remindersEnabled) return
                // Length prefixes keep identities unambiguous even when IDs contain separators.
                val key = "${line.itemId.length}:${line.itemId}${line.occurrenceKey.length}:${line.occurrenceKey}:$today"
                if (sent.getBoolean(key, false)) continue
                val title = if (line.kind == ForecastKind.EXPENSE) {
                    "Vencimento previsto para hoje"
                } else {
                    "Recebimento previsto para hoje"
                }
                if (RecurringReminders.showInformative(app, "forecast:$key", title)) {
                    sent.edit().putBoolean(key, true).commit()
                }
            }
        }
    }
}

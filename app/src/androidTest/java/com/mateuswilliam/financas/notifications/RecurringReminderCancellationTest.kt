package com.mateuswilliam.financas.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecurringReminderCancellationTest {
    private val app get() = ApplicationProvider.getApplicationContext<FinanceApplication>()
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val id = UUID.randomUUID().toString()
    private val otherId = "$id:other"
    private val november = YearMonth.of(2026, 11)
    private val december = november.plusMonths(1)
    private val plan = RecurringPlan(id, "Conta fictícia", 5000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Carteira", "2026-11-30", 30)
    private val store = ViewModelStore()
    private lateinit var model: FinanceViewModel

    @Before fun setup() = runBlocking<Unit> {
        app.repository.clear()
        app.repository.saveRecurring(plan)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val result = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
            ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
            assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS))
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[FinanceViewModel::class.java]
        }
    }

    @After fun cleanup() = runBlocking<Unit> {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        manager.activeNotifications.filter { it.tag?.startsWith("plan:$id:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
        app.repository.clear()
    }

    @Test fun excludingARecurrenceCancelsEveryMonthlyAlertAndKeepsOtherRecurrences() = runBlocking<Unit> {
        showThreeMonthsAndAnotherRecurrence()
        model.excludeRecurringForecast(plan) {}
        awaitAction()
        awaitTags(setOf("plan:$otherId:2026-12-15"))
    }

    @Test fun futureTemplateEditCancelsOnlyAffectedMonthlyAlerts() = runBlocking<Unit> {
        showThreeMonthsAndAnotherRecurrence()
        model.saveRecurringFromMonth(plan.copy(nextDate = "2026-12-15", dayOfMonth = 15), december) {}
        awaitAction()
        awaitTags(setOf("plan:$id:2026-11-30", "plan:$otherId:2026-12-15"))
    }

    private suspend fun showThreeMonthsAndAnotherRecurrence() {
        listOf("2026-11-30", "2026-12-15", "2027-01-15").forEach {
            assertTrue(RecurringReminders.show(app, plan.copy(nextDate = it)))
        }
        assertTrue(RecurringReminders.show(app, plan.copy(id = otherId, nextDate = "2026-12-15")))
        awaitTags(setOf("plan:$id:2026-11-30", "plan:$id:2026-12-15", "plan:$id:2027-01-15", "plan:$otherId:2026-12-15"))
    }

    private suspend fun awaitAction() = withTimeout(15_000) {
        model.busy.first { !it }
    }

    private suspend fun awaitTags(expected: Set<String>) = withTimeout(5_000) {
        while (true) {
            val actual = manager.activeNotifications.mapNotNull { it.tag }
                .filter { it.startsWith("plan:$id:") }.toSet()
            if (actual == expected) break
            delay(25)
        }
    }
}

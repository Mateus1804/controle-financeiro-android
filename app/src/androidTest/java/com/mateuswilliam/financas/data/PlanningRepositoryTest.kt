package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlanningRepositoryTest {
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private fun plan() = RecurringPlan("aluguel", "Aluguel fictício", 60000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME, "Conta fictícia", "2026-10-31", 31)
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
    }
    @After fun cleanup() { preferences.reset(); database.close() }
    @Test fun budgetsAreStoredByMonthAndCategoryAndCanBeUpdated() = runBlocking<Unit> {
        repository.saveBudget(MonthlyBudget("2026-10", Category.FOOD, 10000))
        repository.saveBudget(MonthlyBudget("2026-10", Category.FOOD, 20000))
        repository.saveBudget(MonthlyBudget("2026-11", Category.FOOD, 30000))
        assertEquals(2, repository.budgets.first().size)
        assertEquals(20000L, repository.budgets.first().first { it.month == "2026-10" }.limitCents)
        repository.deleteBudget("2026-10", Category.FOOD)
        assertEquals("2026-11", repository.budgets.first().single().month)
    }
    @Test fun recurrenceDoesNotCreateRecordsUntilExplicitConfirmationAndIsIdempotent() = runBlocking<Unit> {
        val plan = plan()
        repository.saveRecurring(plan)
        assertTrue(repository.exportRecords().isEmpty())
        assertFalse(Ledger.csv(repository.exportRecords(), ZoneId.of("UTC")).contains(plan.title))
        val results = coroutineScope { listOf(
            async(Dispatchers.Default) { repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("a", ZoneId.of("UTC"))) },
            async(Dispatchers.Default) { repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("b", ZoneId.of("UTC"))) },
        ).map { it.await() } }
        assertEquals(1, results.count { it })
        assertEquals(1, repository.exportRecords().size)
        assertEquals(plan.bank, preferences.snapshot().lastOrigin)
        assertTrue(Ledger.csv(repository.exportRecords(), ZoneId.of("UTC")).contains(plan.title))
        assertEquals("2026-11-30", repository.recurring.first().single().nextDate)
        assertEquals("RECORDED", database.financeDao().allOccurrences().single().status)
    }
    @Test fun skippingAnOccurrenceAdvancesTheDateWithoutRecordingMoney() = runBlocking<Unit> {
        repository.saveRecurring(plan())
        assertTrue(repository.skipRecurring(plan().id, plan().nextDate))
        assertFalse(repository.skipRecurring(plan().id, plan().nextDate))
        assertTrue(repository.exportRecords().isEmpty())
        assertEquals("SKIPPED", database.financeDao().allOccurrences().single().status)
    }
    @Test fun pausedOrChangedForecastCannotBeConfirmedWithAStaleDueDate() = runBlocking<Unit> {
        val plan = plan()
        repository.saveRecurring(plan.copy(active = false))
        assertFalse(repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("a", ZoneId.of("UTC"))))
        repository.saveRecurring(plan.copy(nextDate = "2026-11-30"))
        assertFalse(repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("b", ZoneId.of("UTC"))))
        assertTrue(repository.exportRecords().isEmpty())
    }
    @Test fun deniedAlertsDoNotConsumeReminderAndCaptureIsIndependent() = runBlocking<Unit> {
        repository.saveRecurring(plan())
        preferences.setReminders(true)
        assertFalse(preferences.snapshot().enabled)
        var attempts = 0
        repository.notifyDue(LocalDate.parse("2026-10-31")) { attempts++; false }
        assertEquals("", repository.recurring.first().single().lastRemindedDate)
        repository.notifyDue(LocalDate.parse("2026-10-31")) { attempts++; true }
        repository.notifyDue(LocalDate.parse("2026-11-01")) { attempts++; true }
        assertEquals(2, attempts)
        assertEquals("2026-10-31", repository.recurring.first().single().lastRemindedDate)
    }
    @Test fun futureAndDisabledRemindersAreNotSentAndDeletingARecordPreservesTheOccurrence() = runBlocking<Unit> {
        val plan = plan()
        repository.saveRecurring(plan)
        repository.notifyDue(LocalDate.parse("2026-10-31")) { error("Lembretes estão desativados") }
        preferences.setReminders(true)
        repository.notifyDue(LocalDate.parse("2026-10-30")) { error("Vencimento futuro") }
        repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("confirmado", ZoneId.of("UTC")))
        repository.deleteRecord("confirmado")
        assertEquals("REMOVED", database.financeDao().allOccurrences().single().status)
        assertNull(database.financeDao().allOccurrences().single().recordId)
        BackupSnapshot.validate(repository.snapshot())
    }
    @Test fun clearingDataAlsoRemovesPlanning() = runBlocking<Unit> {
        repository.saveRecurring(plan())
        repository.saveBudget(MonthlyBudget("2026-10", Category.HOME, 10000))
        repository.skipRecurring(plan().id, plan().nextDate)
        repository.clear()
        assertTrue(repository.recurring.first().isEmpty()); assertTrue(repository.budgets.first().isEmpty())
        assertTrue(database.financeDao().allOccurrences().isEmpty())
    }
}

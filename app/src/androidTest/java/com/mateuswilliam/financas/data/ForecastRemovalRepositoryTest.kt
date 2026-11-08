package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import java.io.ByteArrayInputStream
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

class ForecastRemovalRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val month = YearMonth.of(2026, 11)
    private val next = month.plusMonths(1)
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repo: FinanceRepository
    private val dao get() = database.financeDao()
    private val plan = RecurringPlan("conta-ficticia", "Conta fictícia", 4000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Carteira", month.atDay(10).toString(), 10)
    private val purchase = InstallmentPurchase("compra", "Compra fictícia", "Carteira", Category.OTHER,
        8000, 4, month.atDay(12))

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repo = FinanceRepository(database, preferences)
    }

    @After fun cleanup() { preferences.reset(); database.close() }

    @Test fun removingRecurrencePreservesPaymentAndStopsForecastsRemindersAndStaleActions() = runBlocking<Unit> {
        repo.saveRecurring(plan)
        repo.setFixedRecurring(plan.id, month)
        val paid = plan.proposedRecord("pagamento", ZoneId.of("UTC")).copy(amountCents = 4500)
        assertTrue(repo.confirmRecurring(plan.id, plan.nextDate, paid))
        val current = repo.recurring.first().single()
        assertFalse(repo.archiveRecurringForecast(plan))
        assertEquals(4000L, repo.commitmentReport(next).committedCents)
        assertTrue(repo.archiveRecurringForecast(current))
        assertFalse(repo.archiveRecurringForecast(current))
        assertTrue(repo.recurring.first().isEmpty())
        assertEquals(4500L, repo.commitmentReport(month).committedCents)
        assertEquals(0L, repo.commitmentReport(next).committedCents)
        assertEquals(listOf(paid), repo.exportRecords())
        assertFalse(repo.skipRecurring(current.id, current.nextDate))
        assertFalse(repo.confirmRecurring(current.id, current.nextDate, paid.copy(id = "outro")))
        assertTrue(runCatching { repo.saveRecurring(current) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repo.setFixedRecurring(current.id, null) }.exceptionOrNull() is IllegalArgumentException)
        preferences.setReminders(true)
        repo.notifyDue(next.atEndOfMonth()) { error("Previsão excluída gerou lembrete.") }
        BackupSnapshot.validate(repo.snapshot())
    }

    @Test fun aPausedRecurrenceCannotBeSkippedFromAStaleDialog() = runBlocking<Unit> {
        repo.saveRecurring(plan.copy(active = false))
        assertFalse(repo.skipRecurring(plan.id, plan.nextDate))
        assertEquals(plan.nextDate, dao.recurringById(plan.id)?.nextDate)
        assertNull(dao.occurrence(plan.id, plan.nextDate))
    }

    @Test fun removingOneInstallmentKeepsTheNumbersAmountsAndHistoryOfTheOtherOnes() = runBlocking<Unit> {
        repo.createPurchase(purchase)
        val paid = payment("paga")
        assertTrue(repo.confirmInstallment(id(1), paid))
        val before = repo.purchaseManagement(id(2))!!
        assertTrue(repo.excludeInstallmentForecast(id(2), before.revision))
        assertFalse(repo.excludeInstallmentForecast(id(2), before.revision))
        val after = repo.purchaseManagement(id(3))!!
        assertNull(repo.purchaseManagement(id(2)))
        assertFalse(repo.confirmInstallment(id(2), payment("indevida"), after.revision))
        assertFalse(repo.adjustInstallment(id(2), after.revision, InstallmentAdjustment(2100, next.atDay(12))))
        assertEquals(2, after.openCount)
        assertEquals(2200L, repo.commitmentReport(month).committedCents)
        assertEquals(0L, repo.commitmentReport(next).committedCents)
        assertTrue(repo.commitmentSetup(next).first().installments.isEmpty())
        assertEquals(3, repo.commitmentSetup(next.plusMonths(1)).first().installments.single().number)
        assertEquals(2000L, repo.commitmentReport(next.plusMonths(1)).committedCents)
        assertEquals(2000L, repo.commitmentReport(next.plusMonths(2)).committedCents)
        assertEquals(listOf(paid), repo.exportRecords())
        assertEquals(0, repo.cancelOpenInstallments(purchase.id, after.revision, 3))
        assertEquals(2, repo.cancelOpenInstallments(purchase.id, after.revision, 2))
        assertEquals(2200L, repo.commitmentReport(month).committedCents)
        assertEquals(0L, repo.commitmentReport(next.plusMonths(1)).committedCents)
        BackupSnapshot.validate(repo.snapshot())
    }

    @Test fun removalAndConfirmationOfTheSameInstallmentCannotBothSucceed() = runBlocking<Unit> {
        repo.createPurchase(purchase)
        val another = FinanceRepository(database, preferences)
        val start = repo.purchaseManagement(id(1))!!
        val paid = payment("simultanea")
        val (removed, confirmed) = coroutineScope {
            val remove = async { repo.excludeInstallmentForecast(id(1), start.revision) }
            val confirm = async { another.confirmInstallment(id(1), paid, start.revision) }
            remove.await() to confirm.await()
        }
        assertTrue(removed xor confirmed)
        assertEquals(if (confirmed) listOf(paid) else emptyList(), repo.exportRecords())
        val remaining = repo.purchaseManagement(id(2))!!
        assertFalse(remaining.canReplacePurchase)
        assertFalse(repo.editPurchase(remaining.revision, purchase.copy(totalCents = 9000)))
    }

    @Test fun encryptedBackupDoesNotBringBackRemovedForecasts() = runBlocking<Unit> {
        repo.createPurchase(purchase)
        repo.saveRecurring(plan)
        repo.setFixedRecurring(plan.id, month)
        val paid = plan.proposedRecord("recorrente-paga", ZoneId.of("UTC")).copy(amountCents = 4500)
        assertTrue(repo.confirmRecurring(plan.id, plan.nextDate, paid))
        assertTrue(repo.archiveRecurringForecast(repo.recurring.first().single()))
        val first = repo.purchaseManagement(id(1))!!
        assertTrue(repo.excludeInstallmentForecast(id(1), first.revision))
        val original = repo.snapshot()
        val store = BackupStore(context, repo)
        val file = store.create(password())
        repo.clear()
        val staged = store.stage(ByteArrayInputStream(file), password())
        store.restore(staged)
        val restored = repo.snapshot()
        assertEquals(original.recurring, restored.recurring)
        assertEquals(original.commitments, restored.commitments)
        assertEquals(original.occurrences, restored.occurrences)
        assertTrue(repo.recurring.first().isEmpty())
        assertEquals(listOf(paid), repo.exportRecords())
        assertEquals(4500L, repo.commitmentReport(month).committedCents)
        assertEquals(2000L, repo.commitmentReport(next).committedCents)
    }

    @Test fun versionThreeRemainsReadableAndCannotSilentlyDropExclusions() = runBlocking<Unit> {
        repo.createPurchase(purchase)
        repo.saveRecurring(plan)
        val snapshot = repo.snapshot()
        val old = JSONObject(BackupSnapshot.encode(snapshot).toString(Charsets.UTF_8)).put("version", 3)
        assertTrue(runCatching { BackupSnapshot.decode(old.toString().toByteArray()) }.isFailure)
        old.getJSONArray("recurring").getJSONObject(0).remove("archived")
        assertTrue(runCatching { BackupSnapshot.decode(old.toString().toByteArray()) }.isFailure)
        val installments = old.getJSONArray("commitments")
        repeat(installments.length()) { installments.getJSONObject(it).remove("excluded") }
        assertEquals(snapshot, BackupSnapshot.decode(old.toString().toByteArray()))
    }

    @Test fun backupRejectsAnExcludedForecastMarkedActiveOrLinkedAsPaid() = runBlocking<Unit> {
        repo.createPurchase(purchase)
        repo.saveRecurring(plan)
        assertTrue(repo.confirmInstallment(id(1), payment("paga")))
        val snapshot = repo.snapshot()
        val invalidRecurring = snapshot.copy(recurring = snapshot.recurring.map { it.copy(archived = true) })
        val invalidInstallment = snapshot.copy(commitments = snapshot.commitments.map {
            if (it.recordId != null) it.copy(excluded = true) else it
        })
        assertTrue(runCatching { BackupSnapshot.validate(invalidRecurring) }.isFailure)
        assertTrue(runCatching { BackupSnapshot.validate(invalidInstallment) }.isFailure)
    }

    private fun id(number: Int) = "installment:${purchase.id}:$number"
    private fun payment(id: String) = Record(id, 2200, Movement.EXPENSE, PaymentMethod.CARD,
        Category.OTHER, "Carteira", "Pagamento fictício", 1_792_800_000_000L)
    private fun password() = "Senha fictícia de teste 2026!".toCharArray()
}

package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class PurchaseManagementRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val month = YearMonth.of(2026, 11)
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private val dao get() = database.financeDao()
    private fun purchase() = InstallmentPurchase("compra", "Compra fictícia", "Conta fictícia", Category.OTHER,
        10000, 3, month.atDay(10))
    private fun record(id: String = "pagamento", amount: Long = 3400) = Record(id, amount, Movement.EXPENSE,
        PaymentMethod.CARD, Category.OTHER, "Conta fictícia", "Pagamento fictício", 1_792_800_000_000L)
    private fun id(number: Int) = "installment:compra:$number"

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
    }

    @After fun cleanup() { preferences.reset(); database.close() }

    @Test fun editingBeforeConfirmationRebuildsForecastsAndKeepsTheConsumedNotification() = runBlocking<Unit> {
        val draft = repository.capture(NotificationInput("fixture.bank", "fixture:edit-purchase", "Banco fictício",
            "Compra", "Compra de R$ 100,00", 1_792_800_000_000L), simulation = true)!!
        repository.createPurchase(purchase(), draft.id, 1)
        val snapshot = repository.purchaseManagement(id(1))!!
        assertTrue(snapshot.canReplacePurchase)
        val edited = purchase().copy(title = "Compra corrigida", bank = "Outra conta", totalCents = 20001,
            installmentCount = 4, paidInstallments = 1, nextDueDate = month.plusMonths(1).atDay(10))
        assertTrue(repository.editPurchase(snapshot.revision, edited))
        assertEquals(draft.id, dao.purchaseById("compra")!!.sourceDraftId)
        assertEquals("SAVED", dao.draftById(draft.id)?.status)
        assertEquals(listOf(2, 3, 4), dao.allCommitments().map { it.installmentNumber })
        assertEquals(listOf(5000L, 5000L, 5000L), dao.allCommitments().map { it.amountCents })
        assertEquals(0L, repository.commitmentReport(month).committedCents)
        assertEquals(5000L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertFalse(repository.editPurchase(snapshot.revision, purchase()))
        assertTrue(repository.exportRecords().isEmpty())
        BackupSnapshot.validate(repository.snapshot())
    }

    @Test fun anOpenParcelCanMoveMonthsWithoutChangingTheConfirmedRowOrItsRecord() = runBlocking<Unit> {
        repository.createPurchase(purchase())
        val paid = record()
        assertTrue(repository.confirmInstallment(id(1), paid))
        val original = dao.commitmentById(id(1))
        val draft = repository.purchaseManagement(id(2))!!
        assertFalse(draft.canReplacePurchase)
        assertFalse(repository.editPurchase(draft.revision, purchase().copy(totalCents = 11000)))
        assertFalse(repository.adjustInstallment(id(1), draft.revision, InstallmentAdjustment(5000, month.atDay(11))))

        val newDate = month.plusMonths(3).atDay(20)
        assertTrue(repository.adjustInstallment(id(2), draft.revision, InstallmentAdjustment(5501, newDate)))
        assertEquals(original, dao.commitmentById(id(1)))
        assertEquals(paid, repository.exportRecords().single())
        assertEquals(3400L, repository.commitmentReport(month).committedCents)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(3333L, repository.commitmentReport(month.plusMonths(2)).committedCents)
        assertEquals(5501L, repository.commitmentReport(month.plusMonths(3)).committedCents)
        assertFalse(repository.confirmInstallment(id(2), record("desatualizado"), expectedPurchaseRevision = draft.revision))
        BackupSnapshot.validate(repository.snapshot())
    }

    @Test fun cancellationKeepsLinkedPaymentsAndCannotReopenForecastsAfterUnlink() = runBlocking<Unit> {
        repository.createPurchase(purchase())
        val paid = record()
        assertTrue(repository.confirmInstallment(id(1), paid))
        val draft = repository.purchaseManagement(id(2))!!
        assertEquals(2, draft.openCount)
        assertEquals(0, repository.cancelOpenInstallments("compra", draft.revision, 3))
        assertEquals(2, repository.cancelOpenInstallments("compra", draft.revision, draft.openCount))
        assertEquals(0, repository.cancelOpenInstallments("compra", draft.revision, draft.openCount))
        assertEquals(paid, repository.exportRecords().single())
        assertEquals(3400L, repository.commitmentReport(month).committedCents)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(2)).committedCents)
        assertNull(repository.purchaseManagement(id(2)))
        assertFalse(repository.confirmInstallment(id(2), record("cancelada")))
        assertEquals(3, dao.allCommitments().size)
        repository.deleteRecord(paid.id)
        assertEquals(0L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.validate(repository.snapshot())
    }

    @Test fun simultaneousAdjustmentAndConfirmationDoNotUseStaleTerms() = runBlocking<Unit> {
        repository.createPurchase(purchase())
        val other = FinanceRepository(database, preferences)
        val snapshot = repository.purchaseManagement(id(1))!!
        val adjusted = InstallmentAdjustment(4444, month.atDay(20))
        val result = coroutineScope {
            val edit = async(Dispatchers.Default) { repository.adjustInstallment(id(1), snapshot.revision, adjusted) }
            val pay = async(Dispatchers.Default) { other.confirmInstallment(id(1), record(), expectedPurchaseRevision = snapshot.revision) }
            edit.await() to pay.await()
        }
        assertTrue(result.first xor result.second)
        val row = dao.commitmentById(id(1))!!
        if (result.first) {
            assertEquals(4444L, row.amountCents); assertNull(row.recordId)
            assertTrue(repository.exportRecords().isEmpty())
        } else {
            assertEquals(3334L, row.amountCents); assertEquals("pagamento", row.recordId)
            assertEquals(3400L, repository.exportRecords().single().amountCents)
        }
        BackupSnapshot.validate(repository.snapshot())
    }
}

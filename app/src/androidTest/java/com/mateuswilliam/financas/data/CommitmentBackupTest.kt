package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.YearMonth
import java.time.ZoneId

class CommitmentBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val month = YearMonth.of(2026, 11)
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private lateinit var store: BackupStore
    private fun password() = "Senha fictícia de teste 2026!".toCharArray()
    private fun purchase(id: String) = InstallmentPurchase(id, "Compra fictícia", "Conta fictícia", Category.HOME, 10000, 3, month.atDay(10))
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
        store = BackupStore(context, repository)
    }
    @After fun cleanup() = runBlocking { store.removeAbandonedStages(); preferences.reset(); database.close() }

    @Test fun encryptedRoundTripPreservesNewTablesAndConfirmedLinks() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 300000, 3500))
        repository.createPurchase(purchase("compra"))
        repository.confirmInstallment("installment:compra:1", Record("pago", 3400, Movement.EXPENSE, PaymentMethod.CARD,
            Category.HOME, "Conta fictícia", "Compra fictícia", 1_792_800_000_000L))
        val plan = RecurringPlan("aluguel", "Aluguel fictício", 60000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Conta fictícia", "2026-11-30", 30)
        repository.saveRecurring(plan); repository.setFixedRecurring(plan.id, month)
        repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("aluguel-pago", ZoneId.of("UTC")))
        val before = repository.snapshot()
        val file = store.create(password())
        repository.clear()
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 1))
        val preview = store.stage(ByteArrayInputStream(file), password())
        assertEquals(1, preview.monthlyPlans); assertEquals(1, preview.purchases)
        assertEquals(4, preview.commitments); assertEquals(1, preview.fixedRecurring)
        assertEquals(1L, repository.commitmentReport(month).incomeCents)
        store.restore(preview)
        val after = repository.snapshot()
        assertEquals(before.monthlyPlans, after.monthlyPlans)
        assertEquals(before.purchases, after.purchases)
        assertEquals(before.fixedRecurring, after.fixedRecurring)
        assertEquals(before.commitments.toSet(), after.commitments.toSet())
        assertEquals(before.records.toSet(), after.records.toSet())
        assertEquals(63400L, repository.commitmentReport(month).committedCents)
        assertEquals(300000L, repository.commitmentReport(month).incomeCents)
    }
    @Test fun versionOneRemainsReadableAndStartsWithoutInventedCommitments() = runBlocking<Unit> {
        val legacy = CompatibilityFixtures.snapshot()
        assertTrue(legacy.monthlyPlans.isEmpty()); assertTrue(legacy.purchases.isEmpty())
        assertTrue(legacy.fixedRecurring.isEmpty()); assertTrue(legacy.commitments.isEmpty())
        repository.restore(legacy)
        assertEquals(0L, repository.commitmentReport(month).committedCents)
        val encoded = BackupSnapshot.encode(repository.snapshot())
        assertEquals(2, JSONObject(encoded.toString(Charsets.UTF_8)).getInt("version"))
        assertEquals(legacy.records.toSet(), BackupSnapshot.decode(encoded).records.toSet())
    }
    @Test fun incompleteSchedulesDanglingLinksAndDuplicateSourcesAreRejected() = runBlocking<Unit> {
        repository.createPurchase(purchase("compra"))
        val before = repository.snapshot()
        val row = before.commitments.first()
        for (invalid in listOf(before.copy(commitments = before.commitments.drop(1)),
            before.copy(commitments = before.commitments.map { if (it.id == row.id) it.copy(recordId = "ausente") else it }),
            before.copy(commitments = before.commitments.map { if (it.id == row.id) it.copy(amountCents = 1) else it }),
            before.copy(monthlyPlans = listOf(MonthlyPlanEntity("2026-11", -1, 3000))))) {
            assertTrue(runCatching { repository.restore(invalid) }.isFailure)
        }
        val draft = repository.capture(NotificationInput("fixture.bank", "fixture:duplicate", "Banco fictício", "Compra",
            "Compra de R$ 10,00", 1_792_800_000_000L), simulation = true)!!
        repository.save(Record("origem-consumida", 1000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Conta fictícia", "Exemplo fictício", 1_792_800_000_000L), draft.id)
        val withSource = repository.snapshot()
        assertTrue(runCatching { repository.restore(withSource.copy(purchases = withSource.purchases.map {
            it.copy(sourceDraftId = draft.id)
        })) }.isFailure)
        assertEquals(before.purchases, repository.snapshot().purchases)
        assertEquals(3334L, repository.commitmentReport(month).committedCents)
    }
    @Test fun payloadVersionAndIntegerFieldsCannotSilentlyDropOrTruncateNewData() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 300000))
        repository.createPurchase(purchase("compra"))
        val original = BackupSnapshot.encode(repository.snapshot()).toString(Charsets.UTF_8)
        val disguised = JSONObject(original).put("version", 1)
        assertTrue(runCatching { BackupSnapshot.decode(disguised.toString().toByteArray()) }.isFailure)
        val decimal = JSONObject(original)
        decimal.getJSONArray("purchases").getJSONObject(0).put("installmentCount", 3.5)
        assertTrue(runCatching { BackupSnapshot.decode(decimal.toString().toByteArray()) }.isFailure)
        val tooLarge = JSONObject(original)
        tooLarge.getJSONArray("monthlyPlans").getJSONObject(0).put("healthyLimitBasisPoints", Long.MAX_VALUE)
        assertTrue(runCatching { BackupSnapshot.decode(tooLarge.toString().toByteArray()) }.isFailure)
    }
    @Test fun failingRestoreRollsBackNewAndOldTablesTogether() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 300000))
        repository.createPurchase(purchase("novo"))
        val incoming = repository.snapshot()
        repository.clear()
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 200000))
        repository.createPurchase(purchase("original"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER falha_compromisso BEFORE INSERT ON commitments WHEN NEW.purchaseId = 'novo' BEGIN SELECT RAISE(ABORT, 'falha fictícia'); END")
        assertTrue(runCatching { repository.restore(incoming) }.isFailure)
        assertEquals("original", repository.snapshot().purchases.single().id)
        assertEquals(200000L, repository.commitmentReport(month).incomeCents)
        assertEquals(3, repository.snapshot().commitments.size)
    }
}

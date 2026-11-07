package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CommitmentRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val month = YearMonth.of(2026, 11)
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private fun purchase(id: String = "compra") = InstallmentPurchase(id, "Notebook fictício", "Conta fictícia",
        Category.EDUCATION, 10000, 3, month.atEndOfMonth(), paidInstallments = 1, dayOfMonth = 31)
    private fun record(id: String, cents: Long = 3400) = Record(id, cents, Movement.EXPENSE, PaymentMethod.CARD,
        Category.EDUCATION, "Conta fictícia", "Pagamento fictício", month.atDay(10).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    private fun recurring() = RecurringPlan("aluguel", "Aluguel fictício", 60000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Conta fictícia", "2026-11-30", 31)
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
    }
    @After fun cleanup() { preferences.reset(); database.close() }

    @Test fun incomeAndLimitAreStoredByMonthWithoutChangingPreviousOrFutureMonths() = runBlocking<Unit> {
        assertEquals(CommitmentState.NO_INCOME, repository.commitmentReport(month).state)
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 300000, 2500))
        repository.saveMonthlyPlan(MonthlyIncomePlan(month.plusMonths(1), 400000, 4000))
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 310000, 3000))
        assertEquals(310000L, repository.commitmentReport(month).incomeCents)
        assertEquals(400000L, repository.commitmentReport(month.plusMonths(1)).incomeCents)
        assertEquals(4000, repository.commitmentReport(month.plusMonths(1)).healthyLimitBasisPoints)
    }
    @Test fun observedReportRefreshesWhenIncomeForecastAndLinkedPaymentChange() = runBlocking<Unit> {
        val updates = Channel<CommitmentReport>(Channel.UNLIMITED)
        val collection = launch { repository.observeCommitmentReport(month).collect { updates.send(it) } }
        suspend fun nextWhere(matches: (CommitmentReport) -> Boolean): CommitmentReport = withTimeout(5000) {
            var report = updates.receive()
            while (!matches(report)) report = updates.receive()
            report
        }
        try {
            assertEquals(CommitmentState.NO_INCOME, nextWhere { true }.state)
            repository.saveMonthlyPlan(MonthlyIncomePlan(month, 100000, 4000))
            assertEquals(CommitmentState.COMFORTABLE, nextWhere { it.incomeCents == 100000L }.state)
            repository.createPurchase(purchase())
            assertEquals(1, nextWhere { it.committedCents == 3333L }.entries.size)
            val rent = recurring()
            repository.saveRecurring(rent)
            repository.setFixedRecurring(rent.id, month)
            assertEquals(2, nextWhere { it.committedCents == 63333L }.entries.size)
            val linked = record("pagamento-vinculado", 3400)
            assertTrue(repository.confirmInstallment("installment:compra:2", linked))
            assertEquals(2, nextWhere { it.committedCents == 63400L }.entries.size)
            assertTrue(repository.save(linked.copy(amountCents = 3500), null, editing = true))
            assertEquals(2, nextWhere { it.committedCents == 63500L }.entries.size)
            repository.skipRecurring(rent.id, rent.nextDate)
            assertEquals(1, nextWhere { it.committedCents == 3500L }.entries.size)
        } finally {
            collection.cancelAndJoin()
            updates.cancel()
        }
    }

    @Test fun ongoingPurchaseCreatesOnlyOpenObligationsAndNoFinancialRecord() = runBlocking<Unit> {
        assertTrue(repository.createPurchase(purchase()))
        assertFalse(repository.createPurchase(purchase()))
        assertTrue(repository.exportRecords().isEmpty())
        val rows = database.financeDao().allCommitments()
        assertEquals(listOf(2, 3), rows.map { it.installmentNumber })
        assertEquals(listOf(3333L, 3333L), rows.map { it.amountCents })
        assertEquals(3333L, repository.commitmentReport(month).committedCents)
        assertEquals(3333L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(0L, repository.commitmentReport(month.minusMonths(1)).committedCents)
    }
    @Test fun confirmationUsesActualAmountOnceAndKeepsTheDueMonth() = runBlocking<Unit> {
        repository.createPurchase(purchase())
        val paid = record("pago").copy(occurredAt = month.plusMonths(1).atDay(2).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        assertTrue(repository.confirmInstallment("installment:compra:2", paid))
        val report = repository.commitmentReport(month)
        assertEquals(3400L, report.committedCents)
        assertEquals(1, report.entries.size)
        assertEquals(1, repository.exportRecords().size)
        assertEquals(3333L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(3400L, Ledger.month(repository.exportRecords(), month.plusMonths(1), ZoneId.of("UTC")).expenses)
    }
    @Test fun concurrentConfirmationAcrossRepositoriesCreatesOnlyOneRecord() = runBlocking<Unit> {
        repository.createPurchase(purchase())
        val other = FinanceRepository(database, preferences)
        val results = coroutineScope { listOf(
            async(Dispatchers.Default) { repository.confirmInstallment("installment:compra:2", record("a")) },
            async(Dispatchers.Default) { other.confirmInstallment("installment:compra:2", record("b")) },
        ).awaitAll() }
        assertEquals(1, results.count { it })
        assertEquals(1, repository.exportRecords().size)
        assertEquals(3400L, repository.commitmentReport(month).committedCents)
    }
    @Test fun deletingOrConvertingTheLinkedRecordReturnsTheObligationToItsForecast() = runBlocking<Unit> {
        repository.createPurchase(purchase())
        repository.confirmInstallment("installment:compra:2", record("pago"))
        repository.save(record("pago", 3500), null, editing = true)
        assertEquals(3500L, repository.commitmentReport(month).committedCents)
        repository.save(record("pago", 3500).copy(movement = Movement.INCOME), null, editing = true)
        assertEquals(3333L, repository.commitmentReport(month).committedCents)
        assertNull(database.financeDao().commitmentById("installment:compra:2")!!.recordId)
        assertTrue(repository.confirmInstallment("installment:compra:2", record("novo")))
        repository.deleteRecord("novo")
        assertEquals(3333L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.validate(repository.snapshot())
    }
    @Test fun fixedRecurrencesAreExplicitAndConfirmedMonthsSurvivePlanEdits() = runBlocking<Unit> {
        val plan = recurring()
        repository.saveRecurring(plan)
        assertEquals(0L, repository.commitmentReport(month).committedCents)
        repository.setFixedRecurring(plan.id, month)
        assertEquals(60000L, repository.commitmentReport(month).committedCents)
        repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("aluguel-pago", ZoneId.of("UTC")).copy(amountCents = 62000))
        assertEquals(62000L, repository.commitmentReport(month).committedCents)
        assertEquals(1, repository.commitmentReport(month).entries.size)
        repository.saveRecurring(plan.copy(title = "Novo aluguel", amountCents = 70000, nextDate = "2026-12-31"))
        assertEquals(62000L, repository.commitmentReport(month).committedCents)
        assertEquals(70000L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        repository.setFixedRecurring(plan.id, null)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(62000L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.validate(repository.snapshot())
    }
    @Test fun markingAfterConfirmationReusesTheExistingExpenseAndDoesNotInventHistory() = runBlocking<Unit> {
        val plan = recurring()
        repository.saveRecurring(plan)
        repository.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("aluguel-pago", ZoneId.of("UTC")).copy(amountCents = 61000))
        repository.setFixedRecurring(plan.id, month)
        repository.setFixedRecurring(plan.id, month)
        assertEquals(61000L, repository.commitmentReport(month).committedCents)
        assertEquals(1, database.financeDao().allCommitments().size)
        assertEquals(1, repository.exportRecords().size)
        repository.deleteRecord("aluguel-pago")
        assertEquals(61000L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.validate(repository.snapshot())
    }

    @Test fun changingAFixedPlanToIncomeRetainsHistoryButRequiresANewOptIn() = runBlocking<Unit> {
        val plan = recurring()
        repository.saveRecurring(plan)
        repository.setFixedRecurring(plan.id, month)
        repository.confirmRecurring(plan.id, plan.nextDate, record("aluguel-confirmado", 62000))
        val next = plan.copy(nextDate = "2026-12-31")
        repository.saveRecurring(next.copy(movement = Movement.INCOME))
        assertNull(database.financeDao().fixedRecurringById(plan.id))
        assertEquals(62000L, repository.commitmentReport(month).committedCents)
        repository.saveRecurring(next)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        BackupSnapshot.validate(repository.snapshot())
    }
    @Test fun fixedRecurringExpenseCanBecomeIncomeAndExpenseAgainWithoutBreakingBackup() = runBlocking<Unit> {
        val plan = recurring()
        repository.saveRecurring(plan); repository.setFixedRecurring(plan.id, month)
        val paid = plan.proposedRecord("recorrente-editavel", ZoneId.of("UTC")).copy(amountCents = 62000)
        repository.confirmRecurring(plan.id, plan.nextDate, paid)
        repository.save(paid.copy(movement = Movement.INCOME), null, editing = true)
        assertEquals(60000L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.encode(repository.snapshot())
        repository.save(paid.copy(amountCents = 63000), null, editing = true)
        assertEquals(63000L, repository.commitmentReport(month).committedCents)
        assertEquals("recorrente-editavel", repository.snapshot().commitments.single().recordId)
        BackupSnapshot.encode(repository.snapshot())
    }
    @Test fun fixedForecastConfirmedAsIncomeCanBeCorrectedToExpense() = runBlocking<Unit> {
        val plan = recurring()
        repository.saveRecurring(plan); repository.setFixedRecurring(plan.id, month)
        val recorded = plan.proposedRecord("corrigir-movimento", ZoneId.of("UTC")).copy(movement = Movement.INCOME)
        repository.confirmRecurring(plan.id, plan.nextDate, recorded)
        assertEquals(60000L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.encode(repository.snapshot())
        repository.save(recorded.copy(movement = Movement.EXPENSE, amountCents = 61000), null, editing = true)
        assertEquals(61000L, repository.commitmentReport(month).committedCents)
        BackupSnapshot.encode(repository.snapshot())
    }
    @Test fun skippedRecurrencesAndIncomePlansDoNotBecomeFixedExpenses() = runBlocking<Unit> {
        val plan = recurring()
        repository.saveRecurring(plan)
        repository.setFixedRecurring(plan.id, month)
        repository.skipRecurring(plan.id, plan.nextDate)
        assertEquals(0L, repository.commitmentReport(month).committedCents)
        repository.saveRecurring(plan.copy(id = "salario", movement = Movement.INCOME))
        assertTrue(runCatching { repository.setFixedRecurring("salario", month) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(repository.exportRecords().isEmpty())
    }
    @Test fun notificationPurchaseChecksRevisionAndConsumesTheSourceOnlyOnce() = runBlocking<Unit> {
        val input = NotificationInput("fixture.bank", "fixture:purchase", "Banco da notificação", "Compra", "Compra de R$ 100,00", 1_792_800_000_000L)
        val draft = repository.capture(input, simulation = true)!!
        repository.capture(input.copy(body = "Compra aprovada de R$ 100,00"), simulation = true)
        assertTrue(runCatching { repository.createPurchase(purchase(), draft.id, 1) }.exceptionOrNull() is DraftChangedException)
        assertTrue(database.financeDao().allPurchases().isEmpty())
        assertTrue(repository.createPurchase(purchase(), draft.id, 2))
        assertFalse(repository.createPurchase(purchase("duplicada"), draft.id, 2))
        val saved = database.financeDao().draftById(draft.id)!!
        assertEquals("SAVED", saved.status)
        assertEquals("", saved.sourceBody)
        assertEquals("fixture.bank", saved.sourcePackage)
        assertEquals("Conta fictícia", database.financeDao().allPurchases().single().bank)
        assertTrue(repository.exportRecords().isEmpty())
        BackupSnapshot.validate(repository.snapshot())
    }
    @Test fun configurationAndPurchasesSurviveClosingAndReopeningTheDatabase() = runBlocking<Unit> {
        val name = "commitment-reopen-test"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        try {
            val repo = FinanceRepository(first, preferences)
            repo.saveMonthlyPlan(MonthlyIncomePlan(month, 300000, 3500))
            repo.createPurchase(purchase())
        } finally { first.close() }
        val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        try {
            val report = FinanceRepository(reopened, preferences).commitmentReport(month)
            assertEquals(300000L, report.incomeCents)
            assertEquals(3500, report.healthyLimitBasisPoints)
            assertEquals(3333L, report.committedCents)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}

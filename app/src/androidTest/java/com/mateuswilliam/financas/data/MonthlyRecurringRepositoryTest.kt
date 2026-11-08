package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayInputStream

class MonthlyRecurringRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val november = YearMonth.of(2026, 11)
    private val december = november.plusMonths(1)
    private val plan = RecurringPlan("rent", "Aluguel", 60000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Carteira", "2026-11-30", 31)
    private lateinit var db: FinanceDatabase
    private lateinit var settings: CapturePreferences
    private lateinit var repo: FinanceRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        settings = CapturePreferences(context).also { it.reset() }
        repo = FinanceRepository(db, settings)
    }
    @After fun cleanup() { settings.reset(); db.close() }
    private suspend fun month(month: YearMonth) = repo.observeRecurringMonths(month).first().single()

    @Test fun monthlyAmountAndDueDateEditsKeepTheOtherCompetencesAndIdentity() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        val nov = month(november); val dec = month(december)
        assertTrue(repo.saveRecurringMonth(dec, dec.plan.copy(amountCents = 70000, nextDate = "2026-12-15")))
        assertEquals(60000L, month(november).plan.amountCents)
        assertEquals("2026-11-30", month(november).plan.nextDate)
        assertEquals(70000L, month(december).plan.amountCents)
        assertEquals(december, month(december).month)
        assertEquals(1, repo.observeRecurringMonths(december).first().size)
        assertFalse(repo.saveRecurringMonth(dec, dec.plan.copy(amountCents = 90000)))
        assertEquals(60000L, repo.commitmentReport(november).committedCents)
        assertEquals(70000L, repo.commitmentReport(december).committedCents)
        assertTrue(repo.exportRecords().isEmpty())
        assertEquals(nov, month(november))
    }

    @Test fun confirmingDecemberDoesNotConsumeNovemberAndCannotBeRepeatedAfterDueEdit() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        val dec = month(december)
        val record = dec.plan.proposedRecord("paid-dec", ZoneId.of("UTC"))
        assertTrue(repo.confirmRecurringMonth(dec, record))
        assertEquals(RecurringMonthStatus.PENDING, month(november).status)
        assertEquals(RecurringMonthStatus.RECORDED, month(december).status)
        assertFalse(repo.saveRecurringMonth(month(december), dec.plan.copy(nextDate = "2026-12-20")))
        assertFalse(repo.confirmRecurringMonth(dec, record.copy(id = "duplicate")))
        assertEquals(1, repo.exportRecords().size)
        assertEquals(60000L, repo.commitmentReport(december).committedCents)
        repo.deleteRecord(record.id)
        val reopened = month(december)
        assertEquals(RecurringMonthStatus.PENDING, reopened.status)
        assertNull(reopened.recordId)
        assertTrue(repo.confirmRecurringMonth(reopened, record.copy(id = "paid-again")))
        assertEquals(1, repo.exportRecords().size)
        BackupSnapshot.validate(repo.snapshot())
    }

    @Test fun futureTemplateRevisionPreservesUnseenEarlierCompetences() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        repo.saveRecurringFromMonth(plan.copy(amountCents = 75000, nextDate = "2026-12-15", dayOfMonth = 15), december)
        assertEquals(60000L, month(november).plan.amountCents)
        assertEquals("2026-11-30", month(november).plan.nextDate)
        assertEquals(75000L, month(december).plan.amountCents)
        assertEquals("2026-12-15", month(december).plan.nextDate)
        assertEquals("2027-01-15", month(december.plusMonths(1)).plan.nextDate)
        assertTrue(repo.skipRecurringMonth(month(december)))
        assertEquals(RecurringMonthStatus.PENDING, month(november).status)
        assertEquals(0L, repo.commitmentReport(december).committedCents)
        val encoded = BackupSnapshot.encode(repo.snapshot())
        val decoded = BackupSnapshot.decode(encoded)
        repo.clear(); repo.restore(decoded)
        assertEquals(60000L, month(november).plan.amountCents)
        assertEquals(RecurringMonthStatus.SKIPPED, month(december).status)
        assertEquals("2027-01-15", month(december.plusMonths(1)).plan.nextDate)
    }

    @Test fun concurrentMonthConfirmationAcrossRepositoriesCreatesOnePayment() = runBlocking<Unit> {
        repo.saveRecurring(plan)
        val expected = month(november)
        val other = FinanceRepository(db, settings)
        val results = coroutineScope {
            listOf(async(Dispatchers.Default) { repo.confirmRecurringMonth(expected, expected.plan.proposedRecord("a", ZoneId.of("UTC"))) },
                async(Dispatchers.Default) { other.confirmRecurringMonth(expected, expected.plan.proposedRecord("b", ZoneId.of("UTC"))) }).awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(1, repo.exportRecords().size)
    }

    @Test fun changingFixedClassificationFromDecemberPreservesUnseenNovember() = runBlocking<Unit> {
        repo.saveRecurring(plan)
        repo.setFixedRecurringFromMonth(plan.id, november, true)
        repo.setFixedRecurringFromMonth(plan.id, december, false)
        assertEquals(60000L, repo.commitmentReport(november).committedCents)
        assertEquals(0L, repo.commitmentReport(december).committedCents)
        assertTrue(month(november).fixedExpense)
        assertFalse(month(december).fixedExpense)
        repo.setFixedRecurringFromMonth(plan.id, december.plusMonths(1), true)
        assertEquals(0L, repo.commitmentReport(december).committedCents)
        assertEquals(60000L, repo.commitmentReport(december.plusMonths(1)).committedCents)
    }

    @Test fun monthlyExpenseCannotBeConfirmedAsIncomeAndCreateAPhantomExpense() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        val expected = month(november)
        val income = expected.plan.proposedRecord("wrong-type", ZoneId.of("UTC")).copy(movement = Movement.INCOME)
        assertTrue(runCatching { repo.confirmRecurringMonth(expected, income) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(repo.exportRecords().isEmpty())
        assertEquals(RecurringMonthStatus.PENDING, month(november).status)
    }

    @Test fun versionFourBackupRetainsPaymentsAndSeedsMonthlyHistoryInVersionFive() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        val paid = plan.proposedRecord("legacy-paid", ZoneId.of("UTC")).copy(amountCents = 62500)
        assertTrue(repo.confirmRecurring(plan.id, plan.nextDate, paid))
        val v4 = JSONObject(BackupSnapshot.encode(repo.snapshot()).toString(Charsets.UTF_8)).put("version", 4)
        v4.remove("recurringRevisions"); v4.remove("recurringMonths")
        v4.getJSONArray("recurring").getJSONObject(0).remove("startMonth")
        val upgraded = BackupSnapshot.decode(v4.toString().toByteArray())
        repo.clear(); repo.restore(upgraded)
        assertEquals(listOf(paid), repo.exportRecords())
        assertEquals(RecurringMonthStatus.RECORDED, month(november).status)
        assertEquals(62500L, month(november).plan.amountCents)
        assertEquals(62500L, repo.commitmentReport(november).committedCents)
        assertEquals(60000L, repo.commitmentReport(december).committedCents)
        val v5 = JSONObject(BackupSnapshot.encode(repo.snapshot()).toString(Charsets.UTF_8))
        assertEquals(5, v5.getInt("version"))
        assertTrue(v5.getJSONArray("recurringRevisions").length() > 0)
        assertTrue(v5.getJSONArray("recurringMonths").length() > 0)
    }

    @Test fun reopeningRoomKeepsMonthlyEditsSkippedStateAndOriginalHabitualDay() = runBlocking<Unit> {
        val name = "monthly-recurring-reopen"
        context.deleteDatabase(name)
        var fileDb = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        try {
            var fileRepo = FinanceRepository(fileDb, settings)
            fileRepo.saveRecurring(plan); fileRepo.setFixedRecurring(plan.id, november)
            val dec = fileRepo.observeRecurringMonths(december).first().single()
            assertTrue(fileRepo.saveRecurringMonth(dec, dec.plan.copy(amountCents = 71000, nextDate = "2026-12-20")))
            assertTrue(fileRepo.skipRecurringMonth(fileRepo.observeRecurringMonths(november).first().single()))
            fileDb.close()
            fileDb = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            fileRepo = FinanceRepository(fileDb, settings)
            val restored = fileRepo.observeRecurringMonths(december).first().single()
            assertEquals(71000L, restored.plan.amountCents)
            assertEquals("2026-12-20", restored.plan.nextDate)
            assertEquals(31, restored.plan.dayOfMonth)
            assertEquals(RecurringMonthStatus.SKIPPED, fileRepo.observeRecurringMonths(november).first().single().status)
            assertEquals("2027-01-31", fileRepo.observeRecurringMonths(december.plusMonths(1)).first().single().plan.nextDate)
        } finally { fileDb.close(); context.deleteDatabase(name) }
    }

    @Test fun backupCannotDetachThePaidMonthlyStateFromItsRecordedOccurrence() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        val expected = month(november)
        assertTrue(repo.confirmRecurringMonth(expected, plan.proposedRecord("paid", ZoneId.of("UTC"))))
        val snapshot = repo.snapshot()
        val invalid = snapshot.copy(recurringMonths = snapshot.recurringMonths.map {
            it.copy(status = "PENDING", recordId = null)
        })
        assertTrue(runCatching { BackupSnapshot.validate(invalid) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repo.restore(invalid) }.exceptionOrNull() is IllegalArgumentException)
        val json = JSONObject(BackupSnapshot.encode(snapshot).toString(Charsets.UTF_8))
        json.getJSONArray("recurringMonths").getJSONObject(0).apply {
            put("status", "PENDING"); put("recordId", JSONObject.NULL)
        }
        val malformed = json.toString().toByteArray()
        assertTrue(runCatching { BackupSnapshot.decode(malformed) }.exceptionOrNull() is IllegalArgumentException)
        val encrypted = BackupCrypto.encrypt(malformed, "Senha fictícia segura 2026!".toCharArray())
        assertTrue(runCatching {
            BackupStore(context, repo).stage(ByteArrayInputStream(encrypted), "Senha fictícia segura 2026!".toCharArray())
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(RecurringMonthStatus.RECORDED, month(november).status)
        assertEquals(60000L, repo.commitmentReport(november).committedCents)
    }

    @Test fun pausingAnEarlierRulePreservesItsAmountAndLaterConfiguredDueDay() = runBlocking<Unit> {
        repo.saveRecurring(plan)
        repo.saveRecurringFromMonth(plan.copy(amountCents = 75000, nextDate = "2026-12-15", dayOfMonth = 15), december)
        repo.setRecurringActiveFromMonth(plan.id, november, false)
        assertEquals(60000L, month(november).plan.amountCents)
        assertEquals("2026-11-30", month(november).plan.nextDate)
        assertFalse(month(november).plan.active)
        assertEquals(75000L, month(december).plan.amountCents)
        assertEquals("2026-12-15", month(december).plan.nextDate)
        repo.setRecurringActiveFromMonth(plan.id, november, true)
        assertTrue(month(november).plan.active)
        assertEquals(60000L, month(november).plan.amountCents)
        assertEquals("2027-01-15", month(december.plusMonths(1)).plan.nextDate)
    }

    @Test fun configurationUsesItsEffectiveRuleWithoutCopyingMonthlyOverridesOrLaterValues() = runBlocking<Unit> {
        repo.saveRecurring(plan)
        val nov = month(november)
        assertTrue(repo.saveRecurringMonth(nov, nov.plan.copy(amountCents = 71000, nextDate = "2026-11-20")))
        repo.saveRecurringFromMonth(plan.copy(amountCents = 75000, nextDate = "2026-12-15", dayOfMonth = 15), december)
        val oldRule = repo.recurringConfiguration(plan.id, november)!!
        assertEquals(60000L, oldRule.amountCents)
        assertEquals("2026-11-30", oldRule.nextDate)
        assertEquals(31, oldRule.dayOfMonth)
        assertEquals(71000L, month(november).plan.amountCents)
        val newRule = repo.recurringConfiguration(plan.id, december)!!
        assertEquals(75000L, newRule.amountCents)
        assertEquals("2026-12-15", newRule.nextDate)
        assertNull(repo.recurringConfiguration(plan.id, november.minusMonths(1)))
    }

    @Test fun legacyDueDateCorrectionCannotCreateASecondPaymentInTheSameCompetence() = runBlocking<Unit> {
        repo.saveRecurring(plan); repo.setFixedRecurring(plan.id, november)
        assertTrue(repo.confirmRecurring(plan.id, plan.nextDate, plan.proposedRecord("first", ZoneId.of("UTC"))))
        val changed = plan.copy(nextDate = "2026-11-15", dayOfMonth = 15)
        repo.saveRecurring(changed)
        assertFalse(repo.confirmRecurring(plan.id, changed.nextDate, changed.proposedRecord("duplicate", ZoneId.of("UTC"))))
        assertEquals(1, repo.exportRecords().size)
        assertEquals(1, repo.observeRecurringMonths(november).first().size)
        assertEquals(60000L, repo.commitmentReport(november).committedCents)
    }
}

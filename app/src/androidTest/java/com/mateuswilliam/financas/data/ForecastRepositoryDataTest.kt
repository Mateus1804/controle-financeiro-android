package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ForecastRepositoryDataTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun legacyImportIsIdempotentPreservesSnapshotsLinksAdjustmentsAndReferenceIncome()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,FinanceDatabase::class.java).build()
        try {
            val dao=db.financeDao()
            dao.upsertMonthlyPlan(MonthlyPlanEntity("2026-11",999999,8000))
            dao.insertRecord(RecordEntity("paid",null,12345,Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"","Real",1793491200000,""))
            dao.upsertRecurring(RecurringEntity("r","Rent",12000,Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"","2026-12-05",5,false,archived=true,startMonth="2026-10"))
            dao.upsertRecurringRevision(RecurringRevisionEntity("r","2026-10","Rent",12000,Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"","2026-10-05",5,true))
            dao.insertRecurringMonth(RecurringMonthEntity("r","2026-11","Edited rent",12345,Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"","2026-11-06",5,true,"RECORDED","paid",true))
            dao.insertPurchase(PurchaseEntity("p","Computer","",Category.OTHER,PaymentMethod.CARD,10001,3,1,"2026-11-09",9,null))
            dao.insertCommitment(CommitmentEntity("p2","Computer",3333,"2026-11-10","p",2,null,null,adjusted=true))
            dao.insertCommitment(CommitmentEntity("p3","Computer",3333,"2026-12-09","p",3,null,null,excluded=true))
            val repo=ForecastRepository(db)
            repo.initialize(); val first=repo.dataset.first();repo.initialize()
            assertEquals(first,repo.dataset.first())
            assertTrue(first.rules.none { it.kind==ForecastKind.INCOME || it.legacySource=="record:paid" })
            val report=ForecastCalculator.report(first,BudgetPeriod(LocalDate.parse("2026-11-01"),LocalDate.parse("2026-12-01")))
            assertEquals(15678L,report.totalExpensesCents)
            assertTrue(report.lines.any { it.title=="Computer · 2/3" && it.date==LocalDate.parse("2026-11-10") })
            assertEquals(1,dao.allRecords().size);assertEquals(1,dao.allMonthlyPlans().size)
            assertEquals(first.rules.size,dao.allForecastRules().size)
        } finally { db.close() }
    }
    @Test fun archivedFutureRevisionsAndPendingSnapshotsCannotReactivateAndBackupKeepsLongSources()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,FinanceDatabase::class.java).build()
        val prefs=CapturePreferences(context).also { it.reset() }
        try {
            val dao=db.financeDao()
            val current=LocalDate.now().withDayOfMonth(1)
            val future=current.plusMonths(1)
            val month=future.toString().take(7)
            dao.upsertRecurring(RecurringEntity("archived","Archived",100,Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,
                "Bank",future.withDayOfMonth(5).toString(),5,false,archived=true,startMonth=current.toString().take(7)))
            dao.upsertRecurringRevision(RecurringRevisionEntity("archived",current.toString().take(7),"Original",100,
                Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"Bank",current.withDayOfMonth(5).toString(),5,true))
            dao.upsertRecurringRevision(RecurringRevisionEntity("archived",month,"Future active",200,
                Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"Bank",future.withDayOfMonth(5).toString(),5,true))
            dao.insertRecurringMonth(RecurringMonthEntity("archived",month,"Future pending",250,Movement.EXPENSE,
                PaymentMethod.PIX,Category.HOME,"Bank",future.withDayOfMonth(5).toString(),5,true,"PENDING",null,true))
            val longId="installment:longpurchase:1";val longTitle="t".repeat(200)
            dao.insertPurchase(PurchaseEntity("longpurchase",longTitle,"Bank",Category.OTHER,PaymentMethod.CARD,100,1,0,future.withDayOfMonth(5).toString(),5,null))
            dao.insertCommitment(CommitmentEntity(longId,"$longTitle · 1/1",100,future.withDayOfMonth(5).toString(),"longpurchase",1,null,null))
            val repo=ForecastRepository(db);repo.initialize()
            val boundaryId="legacy:commitment:${"c".repeat(256)}"
            repo.saveRule(ForecastRule(boundaryId,current,"z".repeat(256),ForecastKind.EXPENSE,1,Category.OTHER,
                ForecastFrequency.ONCE,current,active=false,legacySource="commitment:${"c".repeat(256)}"))
            val imported=repo.dataset.first()
            val futurePeriod=BudgetPeriod(future,future.plusMonths(1))
            val report=ForecastCalculator.report(imported,futurePeriod)
            assertTrue(report.lines.none { it.itemId=="legacy:recurring:archived" && it.included })
            assertEquals(100L,report.totalExpensesCents)
            val restored=BackupSnapshot.decode(BackupSnapshot.encode(FinanceRepository(db,prefs).snapshot()))
            assertTrue(restored.forecastRules.any { it.itemId=="legacy:commitment:$longId" && it.title=="$longTitle · 1/1" })
            assertTrue(restored.forecastRules.any { it.itemId==boundaryId && it.title.length==256 })
            assertEquals(2,dao.allRecurringRevisions().size)
            assertTrue(dao.allRecurringMonths().single().active)
            assertEquals(imported,ForecastDataset(restored.forecastRules.map { it.model() },restored.forecastOverrides.map { it.model() },
                restored.cycleRules.map { it.model() },restored.reserveEvents.map { it.model() }))
        } finally { prefs.reset();db.close() }
    }
    @Test fun reserveAllocationIsAtomicAndCycleChangesKeepCurrentBoundary()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,FinanceDatabase::class.java).build()
        try {
            val repo=ForecastRepository(db);repo.initialize()
            val today=LocalDate.parse("2026-11-08")
            repo.setCycleDay(5,today)
            val initial=repo.dataset.first();assertEquals(LocalDate.parse("1970-01-01"),initial.cycleRules.single().effectiveStart)
            repo.saveRule(ForecastRule("salary",today.withDayOfMonth(1),"Salary",ForecastKind.INCOME,80000,Category.SALARY,ForecastFrequency.MONTHLY,today.withDayOfMonth(1),5))
            repo.addReserveEvent(ReserveEventKind.SET,0,"Initial zero")
            val start=LocalDate.parse("2026-11-05")
            val successes=coroutineScope { listOf(async { runCatching { repo.addReserveEvent(ReserveEventKind.ADD,50000,"A",start) }.isSuccess },async { runCatching { repo.addReserveEvent(ReserveEventKind.ADD,50000,"B",start) }.isSuccess }).map { it.await() } }
            assertEquals(1,successes.count { it });assertEquals(50000L,ForecastCalculator.reserveBalance(repo.dataset.first().reserveEvents))
            assertTrue(runCatching { repo.addReserveEvent(ReserveEventKind.WITHDRAW,50001,"Too much") }.isFailure)
            repo.setCycleDay(12,today)
            val rules=repo.dataset.first().cycleRules
            assertEquals(LocalDate.parse("2026-12-05"),rules.last().effectiveStart)
            assertEquals(BudgetPeriod(start,LocalDate.parse("2026-12-05")),CycleCalendar.periodContaining(today,rules))
        } finally { db.close() }
    }
    @Test fun firstExplicitCycleChangePreservesExistingCalendarAllocations()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,FinanceDatabase::class.java).build()
        try {
            val repo=ForecastRepository(db);repo.initialize()
            val start=LocalDate.parse("2026-10-01")
            val oldPeriod=BudgetPeriod(start,LocalDate.parse("2026-11-01"))
            repo.saveRule(ForecastRule("salary",start,"Salary",ForecastKind.INCOME,80000,Category.SALARY,
                ForecastFrequency.MONTHLY,start,1))
            repo.addReserveEvent(ReserveEventKind.SET,0,"Initial")
            repo.addReserveEvent(ReserveEventKind.ADD,50000,"October allocation",start)
            assertTrue(repo.dataset.first().cycleRules.isEmpty())
            repo.setCycleDay(5,LocalDate.parse("2026-11-08"))
            val changed=repo.dataset.first()
            assertEquals(1,changed.cycleRules.first().day)
            assertEquals(LocalDate.parse("2026-12-01"),changed.cycleRules.last().effectiveStart)
            assertEquals(oldPeriod,CycleCalendar.periodContaining(start,changed.cycleRules))
            val report=ForecastCalculator.report(changed,oldPeriod)
            assertEquals(50000L,report.allocatedCents)
            assertEquals(30000L,report.unallocatedCents)
            assertTrue(runCatching { repo.addReserveEvent(ReserveEventKind.ADD,30001,"Cannot allocate twice",start) }.isFailure)
            assertEquals(50000L,ForecastCalculator.reserveBalance(repo.dataset.first().reserveEvents))
        } finally { db.close() }
    }
    @Test fun snapshotRoundTripKeepsForecastAndLegacyRestoreOnlyImportsAfterInitialize()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,FinanceDatabase::class.java).build()
        val prefs=CapturePreferences(context).also { it.reset() }
        val finance=FinanceRepository(db,prefs);val repo=ForecastRepository(db)
        try {
            repo.initialize();repo.addReserveEvent(ReserveEventKind.SET,200000,"Initial");repo.setReserveGoal(500000)
            repo.setCycleDay(5,LocalDate.parse("2026-11-08"))
            val before=repo.dataset.first();val data=BackupSnapshot.encode(finance.snapshot())
            finance.clear();finance.restore(BackupSnapshot.decode(data))
            assertEquals(before,repo.dataset.first())
            val json=JSONObject(BackupSnapshot.encode(finance.snapshot()).toString(Charsets.UTF_8))
            val orphaned=JSONObject(json.toString())
            orphaned.getJSONArray("reserveEvents").put(JSONObject().apply {
                put("id",999);put("kind","ADD");put("amountCents",100);put("createdAt",System.currentTimeMillis()+1)
                put("reason","Invalid cycle linkage");put("cycleStart","2026-11-06")
            })
            assertTrue(runCatching { BackupSnapshot.decode(orphaned.toString().toByteArray()) }.isFailure)
            json.put("version",6)
            assertTrue(runCatching { BackupSnapshot.decode(json.toString().toByteArray()) }.isFailure)
            listOf("forecastRules","forecastOverrides","cycleRules","reserveEvents","forecastMeta").forEach { json.remove(it) }
            val old=BackupSnapshot.decode(json.toString().toByteArray())
            assertTrue(old.forecastMeta.isEmpty());finance.restore(old)
            assertTrue(finance.snapshot().forecastMeta.isEmpty())
            repo.initialize();assertEquals("1",finance.snapshot().forecastMeta.single().value)
        } finally { prefs.reset();db.close() }
    }
}

package com.mateuswilliam.financas.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.PaymentMethod
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.AccountOption
import com.mateuswilliam.financas.core.MonthlyBudget
import com.mateuswilliam.financas.core.RecurringPlan
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "drafts", indices = [
    Index(value = ["sourceKey"], unique = true),
    Index(value = ["status", "capturedAt", "id"], orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC]),
])
data class DraftEntity(
    @PrimaryKey val id: String,
    val sourceKey: String,
    val sourcePackage: String,
    val bank: String,
    val sourceTitle: String,
    val sourceBody: String,
    val amountCents: Long?,
    val movement: Movement,
    val method: PaymentMethod,
    val category: Category,
    val explanation: String,
    val occurredAt: Long,
    val capturedAt: Long,
    val status: String = "PENDING",
    val revision: Int = 1,
)

@Entity(tableName = "records", indices = [
    Index(value = ["sourceDraftId"], unique = true),
    Index(value = ["occurredAt", "id"], orders = [Index.Order.DESC, Index.Order.ASC]),
])
data class RecordEntity(
    @PrimaryKey val id: String,
    val sourceDraftId: String?,
    val amountCents: Long,
    val movement: Movement,
    val method: PaymentMethod,
    val category: Category,
    val bank: String,
    val description: String,
    val occurredAt: Long,
    val destination: String,
) {
    fun toRecord() = Record(id, amountCents, movement, method, category, bank, description, occurredAt, destination)
    companion object {
        fun from(record: Record, sourceDraftId: String?) = RecordEntity(
            record.id, sourceDraftId, record.amountCents, record.movement, record.method,
            record.category, record.bank.trim(), record.description.trim(), record.occurredAt, record.destination.trim(),
        )
    }
}

class Converters {
    @TypeConverter fun movement(value: String) = Movement.valueOf(value)
    @TypeConverter fun movement(value: Movement) = value.name
    @TypeConverter fun method(value: String) = PaymentMethod.valueOf(value)
    @TypeConverter fun method(value: PaymentMethod) = value.name
    @TypeConverter fun category(value: String) = Category.valueOf(value)
    @TypeConverter fun category(value: Category) = value.name
}

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val nameKey: String, val label: String, val lastUsedAt: Long) {
    fun toOption() = AccountOption(nameKey, label, lastUsedAt)
}

@Entity(tableName = "budgets", primaryKeys = ["month", "category"])
data class BudgetEntity(val month: String, val category: Category, val limitCents: Long) {
    fun toBudget() = MonthlyBudget(month, category, limitCents)
}

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val id: String, val title: String, val amountCents: Long, val movement: Movement,
    val method: PaymentMethod, val category: Category, val bank: String, val nextDate: String,
    val dayOfMonth: Int, val active: Boolean = true, val lastRemindedDate: String = "",
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "''") val startMonth: String = "",
) {
    fun toPlan() = RecurringPlan(id, title, amountCents, movement, method, category, bank, nextDate, dayOfMonth, active, lastRemindedDate)
    companion object {
        fun from(plan: RecurringPlan) = RecurringEntity(plan.id, plan.title, plan.amountCents, plan.movement,
            plan.method, plan.category, plan.bank, plan.nextDate, plan.dayOfMonth, plan.active, plan.lastRemindedDate)
    }
}

@Entity(tableName = "occurrences", primaryKeys = ["recurringId", "dueDate"], indices = [Index(value = ["recordId"], unique = true)])
data class OccurrenceEntity(val recurringId: String, val dueDate: String, val status: String, val recordId: String?)

data class UsedAccount(val label: String, val lastUsedAt: Long)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM recurring_revisions ORDER BY recurringId, effectiveMonth") suspend fun allRecurringRevisions(): List<RecurringRevisionEntity>
    @Query("SELECT * FROM recurring_revisions WHERE recurringId = :id AND effectiveMonth <= :month ORDER BY effectiveMonth DESC LIMIT 1")
    suspend fun recurringRevision(id: String, month: String): RecurringRevisionEntity?
    @Upsert suspend fun upsertRecurringRevision(row: RecurringRevisionEntity)
    @Query("DELETE FROM recurring_revisions WHERE recurringId = :id AND effectiveMonth > :month")
    suspend fun deleteFutureRecurringRevisions(id: String, month: String)
    @Query("DELETE FROM recurring_revisions") suspend fun clearRecurringRevisions()
    @Query("SELECT * FROM recurring_months ORDER BY month, dueDate, recurringId") suspend fun allRecurringMonths(): List<RecurringMonthEntity>
    @Query("SELECT * FROM recurring_months WHERE month = :month ORDER BY dueDate, recurringId") suspend fun recurringMonths(month: String): List<RecurringMonthEntity>
    @Query("SELECT * FROM recurring_months WHERE recurringId = :id AND month = :month LIMIT 1") suspend fun recurringMonth(id: String, month: String): RecurringMonthEntity?
    @Query("SELECT * FROM recurring_months WHERE recordId = :id LIMIT 1") suspend fun recurringMonthByRecord(id: String): RecurringMonthEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRecurringMonth(row: RecurringMonthEntity): Long
    @Update suspend fun updateRecurringMonth(row: RecurringMonthEntity): Int
    @Query("UPDATE recurring_months SET status = 'PENDING', recordId = NULL, revision = revision + 1, lastRemindedDate = '' WHERE recordId = :id")
    suspend fun reopenRecurringMonth(id: String)
    @Query("DELETE FROM recurring_months") suspend fun clearRecurringMonths()
    @Query("SELECT * FROM monthly_plans ORDER BY month") suspend fun allMonthlyPlans(): List<MonthlyPlanEntity>
    @Query("SELECT * FROM monthly_plans WHERE month = :month") suspend fun monthlyPlan(month: String): MonthlyPlanEntity?
    @Query("SELECT * FROM monthly_plans WHERE month = :month") fun observeMonthlyPlan(month: String): Flow<MonthlyPlanEntity?>
    @Upsert suspend fun upsertMonthlyPlan(plan: MonthlyPlanEntity)
    @Query("DELETE FROM monthly_plans") suspend fun clearMonthlyPlans()
    @Query("SELECT * FROM purchases ORDER BY id") suspend fun allPurchases(): List<PurchaseEntity>
    @Query("SELECT * FROM purchases WHERE id = :id") suspend fun purchaseById(id: String): PurchaseEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPurchase(purchase: PurchaseEntity)
    @Update suspend fun updatePurchase(purchase: PurchaseEntity): Int
    @Query("DELETE FROM purchases") suspend fun clearPurchases()
    @Query("SELECT * FROM fixed_recurring ORDER BY recurringId") suspend fun allFixedRecurring(): List<FixedRecurringEntity>
    @Query("SELECT * FROM fixed_recurring ORDER BY recurringId") fun observeFixedRecurring(): Flow<List<FixedRecurringEntity>>
    @Query("SELECT * FROM fixed_recurring WHERE recurringId = :id") suspend fun fixedRecurringById(id: String): FixedRecurringEntity?
    @Upsert suspend fun upsertFixedRecurring(plan: FixedRecurringEntity)
    @Query("DELETE FROM fixed_recurring WHERE recurringId = :id") suspend fun removeFixedRecurring(id: String)
    @Query("DELETE FROM fixed_recurring") suspend fun clearFixedRecurring()
    @Query("SELECT * FROM commitments ORDER BY dueDate, id") suspend fun allCommitments(): List<CommitmentEntity>
    @Query("""
        SELECT c.* FROM commitments AS c
        LEFT JOIN purchases AS p ON p.id = c.purchaseId
        LEFT JOIN recurring AS r ON r.id = c.recurringId
        WHERE c.dueDate BETWEEN :start AND :end
          AND (p.id IS NULL OR p.openCancelled = 0 OR c.recordId IS NOT NULL)
          AND c.excluded = 0
          AND (r.id IS NULL OR r.archived = 0 OR c.recordId IS NOT NULL)
        ORDER BY c.dueDate, c.id
    """)
    suspend fun commitmentsInPeriod(start: String, end: String): List<CommitmentEntity>
    @Query("""
        SELECT c.id, p.title, p.bank, p.category, p.method, COALESCE(r.amountCents, c.amountCents) AS amountCents,
               c.dueDate, c.installmentNumber AS number, p.installmentCount, c.recordId, p.revision AS purchaseRevision
        FROM commitments AS c
        JOIN purchases AS p ON p.id = c.purchaseId
        LEFT JOIN records AS r ON r.id = c.recordId
        WHERE c.dueDate BETWEEN :start AND :end AND c.installmentNumber IS NOT NULL
          AND (p.openCancelled = 0 OR c.recordId IS NOT NULL)
          AND c.excluded = 0
        ORDER BY c.dueDate, c.id
    """)
    fun observeInstallmentDues(start: String, end: String): Flow<List<InstallmentDue>>
    @Query("SELECT * FROM commitments WHERE id = :id") suspend fun commitmentById(id: String): CommitmentEntity?
    @Query("SELECT * FROM commitments WHERE purchaseId = :id ORDER BY installmentNumber")
    suspend fun installmentsForPurchase(id: String): List<CommitmentEntity>
    @Query("DELETE FROM commitments WHERE purchaseId = :id AND recordId IS NULL AND adjusted = 0 AND excluded = 0")
    suspend fun deleteReplaceableInstallments(id: String): Int
    @Query("UPDATE commitments SET amountCents = :amount, dueDate = :date, adjusted = 1 WHERE id = :id AND purchaseId IS NOT NULL AND recordId IS NULL AND excluded = 0")
    suspend fun adjustOpenInstallment(id: String, amount: Long, date: String): Int
    @Query("UPDATE commitments SET excluded = 1 WHERE id = :id AND purchaseId IS NOT NULL AND recordId IS NULL AND excluded = 0")
    suspend fun excludeOpenInstallment(id: String): Int
    @Query("SELECT * FROM commitments WHERE recordId = :id") suspend fun commitmentByRecord(id: String): CommitmentEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCommitment(commitment: CommitmentEntity)
    @Query("UPDATE commitments SET recordId = :recordId WHERE id = :id AND recordId IS NULL AND excluded = 0")
    suspend fun linkCommitment(id: String, recordId: String): Int
    @Query("UPDATE commitments SET recordId = NULL WHERE recordId = :id") suspend fun unlinkCommitment(id: String)
    @Query("DELETE FROM commitments") suspend fun clearCommitments()
    @Query("SELECT * FROM budgets ORDER BY month, category") fun budgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets ORDER BY month, category") suspend fun allBudgets(): List<BudgetEntity>
    @Upsert suspend fun upsertBudget(budget: BudgetEntity)
    @Query("DELETE FROM budgets WHERE month = :month AND category = :category") suspend fun deleteBudget(month: String, category: Category)
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("SELECT * FROM recurring WHERE archived = 0 ORDER BY nextDate, id") fun recurring(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM recurring ORDER BY nextDate, id") suspend fun allRecurring(): List<RecurringEntity>
    @Query("SELECT * FROM recurring WHERE id = :id LIMIT 1") suspend fun recurringById(id: String): RecurringEntity?
    @Upsert suspend fun upsertRecurring(plan: RecurringEntity)
    @Query("UPDATE recurring SET nextDate = :next, lastRemindedDate = '' WHERE id = :id AND nextDate = :expected")
    suspend fun advanceRecurring(id: String, expected: String, next: String): Int
    @Query("UPDATE recurring SET lastRemindedDate = :due WHERE id = :id AND nextDate = :due AND active = 1 AND archived = 0")
    suspend fun markReminded(id: String, due: String)
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("SELECT * FROM occurrences ORDER BY recurringId, dueDate") suspend fun allOccurrences(): List<OccurrenceEntity>
    @Query("SELECT * FROM occurrences WHERE recurringId = :id ORDER BY dueDate") suspend fun occurrencesForRecurring(id: String): List<OccurrenceEntity>
    @Query("SELECT * FROM occurrences WHERE dueDate BETWEEN :start AND :end") suspend fun occurrencesInPeriod(start: String, end: String): List<OccurrenceEntity>
    @Query("SELECT * FROM occurrences WHERE recordId = :id LIMIT 1") suspend fun occurrenceByRecord(id: String): OccurrenceEntity?
    @Query("SELECT * FROM occurrences WHERE recurringId = :id AND dueDate = :due LIMIT 1") suspend fun occurrence(id: String, due: String): OccurrenceEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOccurrence(occurrence: OccurrenceEntity)
    @Upsert suspend fun upsertOccurrence(occurrence: OccurrenceEntity)
    @Query("UPDATE occurrences SET recordId = NULL, status = 'REMOVED' WHERE recordId = :id") suspend fun unlinkOccurrence(id: String)
    @Query("DELETE FROM occurrences") suspend fun clearOccurrences()
    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC, nameKey ASC")
    fun accounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC, nameKey ASC")
    suspend fun allAccounts(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE nameKey = :key LIMIT 1") suspend fun accountByKey(key: String): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccount(account: AccountEntity)
    @Query("UPDATE accounts SET lastUsedAt = MAX(lastUsedAt, :time) WHERE nameKey = :key")
    suspend fun touchAccount(key: String, time: Long)
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    // Só nomes e datas atravessam a consulta do catálogo; descrições e demais campos ficam fora dela.
    @Query("""
        SELECT label, MAX(occurredAt) AS lastUsedAt FROM (
            SELECT bank AS label, occurredAt FROM records
            UNION ALL SELECT destination AS label, occurredAt FROM records
        ) WHERE label != '' GROUP BY label ORDER BY lastUsedAt DESC, label ASC
    """)
    fun usedAccounts(): Flow<List<UsedAccount>>
    @Query("SELECT * FROM drafts WHERE status = 'PENDING' ORDER BY capturedAt DESC, id ASC")
    fun pending(): Flow<List<DraftEntity>>
    @Query("SELECT * FROM records ORDER BY occurredAt DESC, id ASC")
    fun records(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM records ORDER BY occurredAt DESC, id ASC")
    suspend fun allRecords(): List<RecordEntity>
    @Query("SELECT * FROM drafts ORDER BY capturedAt DESC, id") suspend fun allDrafts(): List<DraftEntity>
    @Query("SELECT * FROM drafts WHERE sourceKey = :key LIMIT 1")
    suspend fun draftBySource(key: String): DraftEntity?
    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun draftById(id: String): DraftEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDraft(draft: DraftEntity)
    @Query("UPDATE drafts SET sourceTitle = :title, sourceBody = :body, amountCents = :amount, movement = :movement, method = :method, category = :category, explanation = :explanation, revision = revision + 1 WHERE id = :id AND status = 'PENDING'")
    suspend fun updateDraft(id: String, title: String, body: String, amount: Long?, movement: Movement, method: PaymentMethod, category: Category, explanation: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecord(record: RecordEntity)
    @Update suspend fun updateRecord(record: RecordEntity): Int
    @Query("SELECT * FROM records WHERE id IN (:ids)") suspend fun recordsByIds(ids: List<String>): List<RecordEntity>
    @Query("SELECT * FROM records WHERE id = :id LIMIT 1") suspend fun recordById(id: String): RecordEntity?
    @Query("UPDATE drafts SET status = :status, sourceTitle = '', sourceBody = '' WHERE id = :id AND status = 'PENDING'")
    suspend fun reviewDraft(id: String, status: String)
    @Query("DELETE FROM records WHERE id = :id") suspend fun deleteRecord(id: String)
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
    @Query("DELETE FROM records") suspend fun clearRecords()
}

@Database(entities = [DraftEntity::class, RecordEntity::class, AccountEntity::class, BudgetEntity::class, RecurringEntity::class, OccurrenceEntity::class, MonthlyPlanEntity::class, PurchaseEntity::class, FixedRecurringEntity::class, CommitmentEntity::class, RecurringRevisionEntity::class, RecurringMonthEntity::class], version = 8, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `startMonth` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE recurring SET startMonth = (SELECT MIN(value) FROM (SELECT substr(recurring.nextDate,1,7) AS value UNION ALL SELECT substr(o.dueDate,1,7) FROM occurrences o WHERE o.recurringId = recurring.id UNION ALL SELECT f.fromMonth FROM fixed_recurring f WHERE f.recurringId = recurring.id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_revisions` (`recurringId` TEXT NOT NULL, `effectiveMonth` TEXT NOT NULL, `title` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `movement` TEXT NOT NULL, `method` TEXT NOT NULL, `category` TEXT NOT NULL, `bank` TEXT NOT NULL, `anchorDate` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `active` INTEGER NOT NULL, `fixedFromMonth` TEXT, PRIMARY KEY(`recurringId`, `effectiveMonth`), FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("INSERT INTO recurring_revisions SELECT r.id,r.startMonth,r.title,r.amountCents,r.movement,r.method,r.category,r.bank,r.nextDate,r.dayOfMonth,r.active,f.fromMonth FROM recurring r LEFT JOIN fixed_recurring f ON f.recurringId=r.id")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_months` (`recurringId` TEXT NOT NULL, `month` TEXT NOT NULL, `title` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `movement` TEXT NOT NULL, `method` TEXT NOT NULL, `category` TEXT NOT NULL, `bank` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `active` INTEGER NOT NULL, `status` TEXT NOT NULL, `recordId` TEXT, `fixedExpense` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `overridden` INTEGER NOT NULL, `lastRemindedDate` TEXT NOT NULL, PRIMARY KEY(`recurringId`, `month`), FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`recordId`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_months_recordId` ON `recurring_months` (`recordId`)")
                // Ocorrências legadas permanecem intactas, inclusive pagamentos repetidos no mesmo mês.
                db.execSQL("INSERT INTO recurring_months SELECT o.recurringId,substr(o.dueDate,1,7),COALESCE(c.label,r.title),COALESCE(c.amountCents,p.amountCents,r.amountCents),CASE WHEN c.id IS NOT NULL THEN 'EXPENSE' ELSE r.movement END,COALESCE(p.method,r.method),COALESCE(p.category,r.category),COALESCE(p.bank,r.bank),o.dueDate,r.dayOfMonth,r.active,CASE WHEN o.status='RECORDED' THEN 'RECORDED' WHEN o.status='SKIPPED' THEN 'SKIPPED' ELSE 'PENDING' END,o.recordId,CASE WHEN c.id IS NOT NULL OR (f.recurringId IS NOT NULL AND substr(o.dueDate,1,7)>=f.fromMonth) THEN 1 ELSE 0 END,0,0,'' FROM occurrences o JOIN recurring r ON r.id=o.recurringId LEFT JOIN records p ON p.id=o.recordId LEFT JOIN commitments c ON c.recurringId=o.recurringId AND c.dueDate=o.dueDate LEFT JOIN fixed_recurring f ON f.recurringId=o.recurringId WHERE o.dueDate=(SELECT x.dueDate FROM occurrences x WHERE x.recurringId=o.recurringId AND substr(x.dueDate,1,7)=substr(o.dueDate,1,7) ORDER BY CASE x.status WHEN 'RECORDED' THEN 0 WHEN 'REMOVED' THEN 1 ELSE 2 END,x.dueDate DESC LIMIT 1)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `commitments` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `purchases` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `purchases` ADD COLUMN `openCancelled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `commitments` ADD COLUMN `adjusted` INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `monthly_plans` (`month` TEXT NOT NULL, `incomeCents` INTEGER NOT NULL, `healthyLimitBasisPoints` INTEGER NOT NULL, PRIMARY KEY(`month`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `bank` TEXT NOT NULL, `category` TEXT NOT NULL, `method` TEXT NOT NULL, `totalCents` INTEGER NOT NULL, `installmentCount` INTEGER NOT NULL, `paidInstallments` INTEGER NOT NULL, `nextDueDate` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `sourceDraftId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`sourceDraftId`) REFERENCES `drafts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_sourceDraftId` ON `purchases` (`sourceDraftId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `fixed_recurring` (`recurringId` TEXT NOT NULL, `fromMonth` TEXT NOT NULL, PRIMARY KEY(`recurringId`), FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `commitments` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `dueDate` TEXT NOT NULL, `purchaseId` TEXT, `installmentNumber` INTEGER, `recurringId` TEXT, `recordId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`recordId`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_commitments_purchaseId_installmentNumber` ON `commitments` (`purchaseId`, `installmentNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_commitments_recurringId_dueDate` ON `commitments` (`recurringId`, `dueDate`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_commitments_recordId` ON `commitments` (`recordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitments_dueDate_id` ON `commitments` (`dueDate`, `id`)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_records_occurredAt_id` ON `records` (`occurredAt` DESC, `id` ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drafts_status_capturedAt_id` ON `drafts` (`status` ASC, `capturedAt` DESC, `id` ASC)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`nameKey` TEXT NOT NULL, `label` TEXT NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`nameKey`))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`month` TEXT NOT NULL, `category` TEXT NOT NULL, `limitCents` INTEGER NOT NULL, PRIMARY KEY(`month`, `category`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recurring` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `movement` TEXT NOT NULL, `method` TEXT NOT NULL, `category` TEXT NOT NULL, `bank` TEXT NOT NULL, `nextDate` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `active` INTEGER NOT NULL, `lastRemindedDate` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `occurrences` (`recurringId` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `status` TEXT NOT NULL, `recordId` TEXT, PRIMARY KEY(`recurringId`, `dueDate`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_occurrences_recordId` ON `occurrences` (`recordId`)")
            }
        }
    }
}

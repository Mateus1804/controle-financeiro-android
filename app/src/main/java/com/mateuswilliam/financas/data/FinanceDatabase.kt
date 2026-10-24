package com.mateuswilliam.financas.data

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

@Entity(tableName = "drafts", indices = [Index(value = ["sourceKey"], unique = true)])
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

@Entity(tableName = "records", indices = [Index(value = ["sourceDraftId"], unique = true)])
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
) {
    fun toPlan() = RecurringPlan(id, title, amountCents, movement, method, category, bank, nextDate, dayOfMonth, active, lastRemindedDate)
    companion object {
        fun from(plan: RecurringPlan) = RecurringEntity(plan.id, plan.title, plan.amountCents, plan.movement,
            plan.method, plan.category, plan.bank, plan.nextDate, plan.dayOfMonth, plan.active, plan.lastRemindedDate)
    }
}

@Entity(tableName = "occurrences", primaryKeys = ["recurringId", "dueDate"], indices = [Index(value = ["recordId"], unique = true)])
data class OccurrenceEntity(val recurringId: String, val dueDate: String, val status: String, val recordId: String?)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM budgets ORDER BY month, category") fun budgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets ORDER BY month, category") suspend fun allBudgets(): List<BudgetEntity>
    @Upsert suspend fun upsertBudget(budget: BudgetEntity)
    @Query("DELETE FROM budgets WHERE month = :month AND category = :category") suspend fun deleteBudget(month: String, category: Category)
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("SELECT * FROM recurring ORDER BY nextDate, id") fun recurring(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM recurring ORDER BY nextDate, id") suspend fun allRecurring(): List<RecurringEntity>
    @Query("SELECT * FROM recurring WHERE id = :id LIMIT 1") suspend fun recurringById(id: String): RecurringEntity?
    @Upsert suspend fun upsertRecurring(plan: RecurringEntity)
    @Query("UPDATE recurring SET nextDate = :next, lastRemindedDate = '' WHERE id = :id AND nextDate = :expected")
    suspend fun advanceRecurring(id: String, expected: String, next: String): Int
    @Query("UPDATE recurring SET lastRemindedDate = :due WHERE id = :id AND nextDate = :due") suspend fun markReminded(id: String, due: String)
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("SELECT * FROM occurrences ORDER BY recurringId, dueDate") suspend fun allOccurrences(): List<OccurrenceEntity>
    @Query("SELECT * FROM occurrences WHERE recurringId = :id AND dueDate = :due LIMIT 1") suspend fun occurrence(id: String, due: String): OccurrenceEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOccurrence(occurrence: OccurrenceEntity)
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
    @Query("SELECT * FROM drafts WHERE status = 'PENDING' ORDER BY capturedAt DESC")
    fun pending(): Flow<List<DraftEntity>>
    @Query("SELECT * FROM records ORDER BY occurredAt DESC")
    fun records(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM records ORDER BY occurredAt DESC")
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
    @Query("SELECT * FROM records WHERE id = :id LIMIT 1") suspend fun recordById(id: String): RecordEntity?
    @Query("UPDATE drafts SET status = :status, sourceTitle = '', sourceBody = '' WHERE id = :id AND status = 'PENDING'")
    suspend fun reviewDraft(id: String, status: String)
    @Query("DELETE FROM records WHERE id = :id") suspend fun deleteRecord(id: String)
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
    @Query("DELETE FROM records") suspend fun clearRecords()
}

@Database(entities = [DraftEntity::class, RecordEntity::class, AccountEntity::class, BudgetEntity::class, RecurringEntity::class, OccurrenceEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    companion object {
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

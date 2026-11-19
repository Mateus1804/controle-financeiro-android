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
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.PaymentMethod
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.AccountOption
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
    val originalRecordId: String? = null,
    @ColumnInfo(defaultValue = "'EXPENSE'") val reversalMovement: Movement = Movement.EXPENSE,
    val forecastItemId: String? = null, val forecastOccurrenceKey: String? = null,
) {
    fun toRecord() = Record(id, amountCents, movement, method, category, bank, description, occurredAt, destination, originalRecordId, reversalMovement, forecastItemId, forecastOccurrenceKey)
    companion object {
        fun from(record: Record, sourceDraftId: String?) = RecordEntity(
            record.id, sourceDraftId, record.amountCents, record.movement, record.method,
            record.category, record.bank.trim(), record.description.trim(), record.occurredAt, record.destination.trim(), record.originalRecordId, record.reversalMovement, record.forecastItemId, record.forecastOccurrenceKey,
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
data class AccountEntity(@PrimaryKey val nameKey: String, val label: String, val lastUsedAt: Long, @ColumnInfo(defaultValue = "0") val archived: Boolean = false) {
    fun toOption() = AccountOption(nameKey, label, lastUsedAt)
}

@Entity(tableName = "account_balance_events", indices = [Index(value = ["accountKey", "effectiveAt"])])
data class AccountBalanceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountKey: String, val balanceCents: Long, val effectiveAt: Long, val createdAt: Long, val reason: String,
) {
    fun toReference() = com.mateuswilliam.financas.core.AccountBalanceReference(id, accountKey, balanceCents, effectiveAt, createdAt, reason)
}

data class UsedAccount(val label: String, val lastUsedAt: Long)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM forecast_rules ORDER BY itemId,effectiveFrom") suspend fun allForecastRules(): List<ForecastRuleEntity>
    @Query("SELECT * FROM forecast_overrides ORDER BY itemId,occurrenceKey") suspend fun allForecastOverrides(): List<ForecastOverrideEntity>
    @Query("SELECT * FROM cycle_rules ORDER BY effectiveStart") suspend fun allCycleRules(): List<CycleRuleEntity>
    @Query("SELECT * FROM reserve_events ORDER BY createdAt,id") suspend fun allReserveEvents(): List<ReserveEventEntity>
    @Query("SELECT * FROM forecast_meta ORDER BY `key`") suspend fun allForecastMeta(): List<ForecastMetaEntity>
    @Upsert suspend fun upsertForecastRule(row:ForecastRuleEntity)
    @Upsert suspend fun upsertForecastOverride(row:ForecastOverrideEntity)
    @Upsert suspend fun upsertCycleRule(row:CycleRuleEntity)
    @Upsert suspend fun upsertForecastMeta(row:ForecastMetaEntity)
    @Insert suspend fun insertReserveEvent(row:ReserveEventEntity):Long
    @Query("DELETE FROM forecast_rules WHERE itemId=:id AND effectiveFrom>:date") suspend fun deleteFutureForecastRules(id:String,date:String)
    @Query("DELETE FROM forecast_overrides WHERE itemId = :id AND occurrenceKey = :key")
    suspend fun deleteForecastOverride(id: String, key: String)
    @Query("DELETE FROM cycle_rules WHERE effectiveStart>=:date") suspend fun deleteFutureCycleRules(date:String)
    @Query("DELETE FROM forecast_rules") suspend fun clearForecastRules()
    @Query("DELETE FROM forecast_overrides") suspend fun clearForecastOverrides()
    @Query("DELETE FROM cycle_rules") suspend fun clearCycleRules()
    @Query("DELETE FROM reserve_events") suspend fun clearReserveEvents()
    @Query("DELETE FROM forecast_meta") suspend fun clearForecastMeta()

    @Query("SELECT * FROM account_balance_events ORDER BY effectiveAt, createdAt, id") fun accountBalanceEvents(): Flow<List<AccountBalanceEventEntity>>
    @Query("SELECT * FROM account_balance_events ORDER BY effectiveAt, createdAt, id") suspend fun allAccountBalanceEvents(): List<AccountBalanceEventEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccountBalanceEvent(event: AccountBalanceEventEntity): Long
    @Query("DELETE FROM account_balance_events") suspend fun clearAccountBalanceEvents()
    @Query("UPDATE accounts SET archived = :archived WHERE nameKey = :key") suspend fun setAccountArchived(key: String, archived: Boolean)
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
    @Query("SELECT * FROM records WHERE originalRecordId = :id") suspend fun refundsForRecord(id: String): List<RecordEntity>
    @Query("SELECT * FROM records WHERE id = :id LIMIT 1") suspend fun recordById(id: String): RecordEntity?
    @Query("UPDATE drafts SET status = :status, sourceTitle = '', sourceBody = '' WHERE id = :id AND status = 'PENDING'")
    suspend fun reviewDraft(id: String, status: String)
    @Query("DELETE FROM records WHERE id = :id") suspend fun deleteRecord(id: String)
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
    @Query("DELETE FROM records") suspend fun clearRecords()
}

@Database(entities = [DraftEntity::class, RecordEntity::class, AccountEntity::class, AccountBalanceEventEntity::class,
    ForecastRuleEntity::class, ForecastOverrideEntity::class, CycleRuleEntity::class, ReserveEventEntity::class, ForecastMetaEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}

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
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.PaymentMethod
import com.mateuswilliam.financas.core.Record
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

@Dao
interface FinanceDao {
    @Query("SELECT * FROM drafts WHERE status = 'PENDING' ORDER BY capturedAt DESC")
    fun pending(): Flow<List<DraftEntity>>
    @Query("SELECT * FROM records ORDER BY occurredAt DESC")
    fun records(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM records ORDER BY occurredAt DESC")
    suspend fun allRecords(): List<RecordEntity>
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

@Database(entities = [DraftEntity::class, RecordEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}

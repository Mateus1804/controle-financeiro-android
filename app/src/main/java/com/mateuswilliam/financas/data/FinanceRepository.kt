package com.mateuswilliam.financas.data

import androidx.room.withTransaction
import com.mateuswilliam.financas.core.NotificationInput
import com.mateuswilliam.financas.core.NotificationParser
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.RecordValidation
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CaptureResult(val id: String, val isNew: Boolean)
class DraftChangedException : IllegalStateException("A notificação mudou. Abra a pendência novamente para conferir os dados atuais.")

class FinanceRepository(private val database: FinanceDatabase, private val settings: CapturePreferences) {
    private val dao = database.financeDao()
    private val writes = Mutex()
    val pending = dao.pending()
    val records = dao.records().map { rows -> rows.map { it.toRecord() } }

    suspend fun capture(input: NotificationInput, simulation: Boolean = false): CaptureResult? = writes.withLock {
        if (!simulation && !settings.permits(input.packageName)) return@withLock null
        if (NotificationParser.isSensitive(input.title, input.body)) return@withLock null
        val suggestion = NotificationParser.parse(input)
        database.withTransaction {
            val existing = dao.draftBySource(input.sourceKey)
            if (existing != null) {
                if (existing.status != "PENDING") return@withTransaction null
                if (existing.sourceTitle != input.title || existing.sourceBody != input.body) {
                    dao.updateDraft(existing.id, input.title, input.body, suggestion.amountCents,
                        suggestion.movement, suggestion.method, suggestion.category, suggestion.explanation)
                }
                CaptureResult(existing.id, false)
            } else {
                val id = UUID.randomUUID().toString()
                dao.insertDraft(DraftEntity(id, input.sourceKey, input.packageName, input.bank,
                    input.title, input.body, suggestion.amountCents, suggestion.movement, suggestion.method,
                    suggestion.category, suggestion.explanation, input.occurredAt, System.currentTimeMillis()))
                CaptureResult(id, true)
            }
        }
    }

    suspend fun save(record: Record, draftId: String?, editing: Boolean = false, expectedRevision: Int? = null): Boolean = writes.withLock {
        RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
        database.withTransaction {
            if (editing) {
                val previous = dao.recordById(record.id) ?: return@withTransaction false
                return@withTransaction dao.updateRecord(RecordEntity.from(record, previous.sourceDraftId)) == 1
            }
            if (draftId != null) {
                val draft = dao.draftById(draftId) ?: return@withTransaction false
                if (draft.status != "PENDING") return@withTransaction false
                if (expectedRevision != null && expectedRevision != draft.revision) throw DraftChangedException()
            }
            dao.insertRecord(RecordEntity.from(record, draftId))
            if (draftId != null) dao.reviewDraft(draftId, "SAVED")
            true
        }
    }

    suspend fun dismiss(id: String) = writes.withLock { dao.reviewDraft(id, "DISMISSED") }
    suspend fun deleteRecord(id: String) = writes.withLock { dao.deleteRecord(id) }
    suspend fun exportRecords(): List<Record> = dao.allRecords().map { it.toRecord() }
    suspend fun clear() = writes.withLock {
        withContext(Dispatchers.IO) { settings.reset() }
        database.withTransaction { dao.clearRecords(); dao.clearDrafts() }
    }
}

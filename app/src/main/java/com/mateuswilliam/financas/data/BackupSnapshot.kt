package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class BackupDisplay(val appearance: Appearance, val hideAmounts: Boolean, val lastOrigin: String)
data class FinanceSnapshot(
    val createdAt: Long, val records: List<RecordEntity>, val drafts: List<DraftEntity>,
    val accounts: List<AccountEntity>, val budgets: List<BudgetEntity>, val recurring: List<RecurringEntity>,
    val occurrences: List<OccurrenceEntity>, val display: BackupDisplay,
)

/** JSON de dados, sem desserialização de classes arbitrárias ou SQL recebido do arquivo. */
object BackupSnapshot {
    const val FORMAT_VERSION = 1
    private const val MAX_ROWS = 50_000
    private fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
    private fun <T> array(rows: List<T>, convert: (T) -> JSONObject) = JSONArray().apply { rows.forEach { put(convert(it)) } }

    fun encode(snapshot: FinanceSnapshot): ByteArray {
        validate(snapshot)
        return json(
            "format" to "controle-financeiro", "version" to FORMAT_VERSION, "createdAt" to snapshot.createdAt,
            "display" to json("appearance" to snapshot.display.appearance.name, "hideAmounts" to snapshot.display.hideAmounts, "lastOrigin" to snapshot.display.lastOrigin),
            "records" to array(snapshot.records) { r -> json("id" to r.id, "sourceDraftId" to r.sourceDraftId,
                "amountCents" to r.amountCents, "movement" to r.movement.name, "method" to r.method.name,
                "category" to r.category.name, "bank" to r.bank, "description" to r.description, "occurredAt" to r.occurredAt, "destination" to r.destination) },
            "drafts" to array(snapshot.drafts) { d -> json("id" to d.id, "sourceKey" to d.sourceKey, "sourcePackage" to d.sourcePackage,
                "bank" to d.bank, "sourceTitle" to d.sourceTitle, "sourceBody" to d.sourceBody, "amountCents" to d.amountCents,
                "movement" to d.movement.name, "method" to d.method.name, "category" to d.category.name,
                "explanation" to d.explanation, "occurredAt" to d.occurredAt, "capturedAt" to d.capturedAt, "status" to d.status, "revision" to d.revision) },
            "accounts" to array(snapshot.accounts) { a -> json("nameKey" to a.nameKey, "label" to a.label, "lastUsedAt" to a.lastUsedAt) },
            "budgets" to array(snapshot.budgets) { b -> json("month" to b.month, "category" to b.category.name, "limitCents" to b.limitCents) },
            "recurring" to array(snapshot.recurring) { r -> json("id" to r.id, "title" to r.title, "amountCents" to r.amountCents,
                "movement" to r.movement.name, "method" to r.method.name, "category" to r.category.name, "bank" to r.bank,
                "nextDate" to r.nextDate, "dayOfMonth" to r.dayOfMonth, "active" to r.active, "lastRemindedDate" to r.lastRemindedDate) },
            "occurrences" to array(snapshot.occurrences) { o -> json("recurringId" to o.recurringId, "dueDate" to o.dueDate, "status" to o.status, "recordId" to o.recordId) },
        ).toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupCrypto.MAX_PAYLOAD_BYTES) { "O backup excede o limite de 16 MiB." }
        }
    }

    fun decode(bytes: ByteArray): FinanceSnapshot {
        require(bytes.size <= BackupCrypto.MAX_PAYLOAD_BYTES) { "Backup maior que o limite permitido." }
        val j = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { throw IllegalArgumentException("Conteúdo de backup inválido.") }
        require(j.text("format", 40) == "controle-financeiro" && j.long("version") == FORMAT_VERSION.toLong()) { "Formato ou versão de dados não suportado." }
        val display = j.getJSONObject("display")
        val snapshot = FinanceSnapshot(j.long("createdAt"),
            j.rows("records") { r -> RecordEntity(r.text("id", 128), r.nullableText("sourceDraftId", 128), r.long("amountCents"),
                Movement.valueOf(r.text("movement", 30)), PaymentMethod.valueOf(r.text("method", 30)), Category.valueOf(r.text("category", 30)),
                r.text("bank", 80), r.text("description", 200), r.long("occurredAt"), r.text("destination", 80)) },
            j.rows("drafts") { d -> DraftEntity(d.text("id", 128), d.text("sourceKey", 2048), d.text("sourcePackage", 300),
                d.text("bank", 80), d.text("sourceTitle", 2000), d.text("sourceBody", 20_000), if (d.isNull("amountCents")) null else d.long("amountCents"),
                Movement.valueOf(d.text("movement", 30)), PaymentMethod.valueOf(d.text("method", 30)), Category.valueOf(d.text("category", 30)),
                d.text("explanation", 2000), d.long("occurredAt"), d.long("capturedAt"), d.text("status", 30), d.long("revision").also { require(it in 1L..Int.MAX_VALUE.toLong()) }.toInt()) },
            j.rows("accounts") { a -> AccountEntity(a.text("nameKey", 160), a.text("label", 80), a.long("lastUsedAt")) },
            j.rows("budgets") { b -> BudgetEntity(b.text("month", 12), Category.valueOf(b.text("category", 30)), b.long("limitCents")) },
            j.rows("recurring") { r -> RecurringEntity(r.text("id", 128), r.text("title", 200), r.long("amountCents"),
                Movement.valueOf(r.text("movement", 30)), PaymentMethod.valueOf(r.text("method", 30)), Category.valueOf(r.text("category", 30)),
                r.text("bank", 80), r.text("nextDate", 20), r.long("dayOfMonth").also { require(it in 1L..31L) }.toInt(), r.bool("active"), r.text("lastRemindedDate", 20)) },
            j.rows("occurrences") { o -> OccurrenceEntity(o.text("recurringId", 128), o.text("dueDate", 20), o.text("status", 30), o.nullableText("recordId", 128)) },
            BackupDisplay(Appearance.valueOf(display.text("appearance", 20)), display.bool("hideAmounts"), display.text("lastOrigin", 80)),
        )
        validate(snapshot)
        return snapshot
    }

    fun validate(snapshot: FinanceSnapshot) {
        require(snapshot.createdAt > 0) { "Data de backup inválida." }
        val total = listOf(snapshot.records.size, snapshot.drafts.size, snapshot.accounts.size, snapshot.budgets.size, snapshot.recurring.size, snapshot.occurrences.size).sum()
        require(total <= MAX_ROWS) { "O backup excede o limite de 50 mil itens." }
        fun unique(values: List<String>) { require(values.all { it.isNotBlank() } && values.distinct().size == values.size) { "Identificadores repetidos ou vazios no backup." } }
        unique(snapshot.records.map { it.id }); unique(snapshot.drafts.map { it.id }); unique(snapshot.drafts.map { it.sourceKey })
        unique(snapshot.accounts.map { it.nameKey }); unique(snapshot.budgets.map { "${it.month}:${it.category}" })
        unique(snapshot.recurring.map { it.id }); unique(snapshot.occurrences.map { "${it.recurringId}:${it.dueDate}" })
        val recordIds = snapshot.records.associateBy { it.id }
        val draftIds = snapshot.drafts.associateBy { it.id }
        val recurringIds = snapshot.recurring.map { it.id }.toSet()
        val sourceIds = snapshot.records.mapNotNull { it.sourceDraftId }
        unique(sourceIds)
        snapshot.records.forEach { r ->
            require(r.id.length <= 128 && RecordValidation.error(r.toRecord()) == null) { "Lançamento inválido no backup." }
            require(r.sourceDraftId == null || draftIds[r.sourceDraftId]?.status == "SAVED") { "Vínculo de pendência inválido." }
        }
        snapshot.drafts.forEach { d ->
            require(d.revision > 0 && d.occurredAt > 0 && d.capturedAt > 0 && d.sourcePackage.isNotBlank()) { "Pendência inválida no backup." }
            require(d.amountCents == null || d.amountCents in 0..Money.MAX_CENTS) { "Valor de pendência inválido." }
            require(d.status in setOf("PENDING", "SAVED", "DISMISSED")) { "Estado de revisão inválido." }
            require(d.status == "PENDING" || (d.sourceBody.isEmpty() && d.sourceTitle.isEmpty())) { "Texto original indevido em pendência revisada." }
        }
        snapshot.accounts.forEach { a -> require(a.label.isNotBlank() && a.label.length <= 80 && a.nameKey == AccountNames.key(a.label) && a.lastUsedAt >= 0) { "Conta inválida no backup." } }
        snapshot.budgets.forEach { require(Budgets.error(it.toBudget()) == null) { "Orçamento inválido no backup." } }
        snapshot.recurring.forEach { require(Recurrences.error(it.toPlan()) == null && (it.lastRemindedDate.isEmpty() || it.lastRemindedDate == it.nextDate)) { "Recorrência inválida no backup." } }
        val linkedRecords = snapshot.occurrences.mapNotNull { it.recordId }
        unique(linkedRecords)
        snapshot.occurrences.forEach { o ->
            require(o.recurringId in recurringIds && runCatching { LocalDate.parse(o.dueDate) }.isSuccess) { "Vencimento inválido no backup." }
            require(o.status in setOf("RECORDED", "SKIPPED", "REMOVED")) { "Estado de vencimento inválido." }
            require(if (o.status == "RECORDED") o.recordId in recordIds else o.recordId == null) { "Vínculo de lançamento recorrente inválido." }
        }
        require(snapshot.display.lastOrigin.length <= 80) { "Preferência de origem inválida." }
    }

    private fun JSONObject.long(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "Número inteiro inválido em $key." }
        return (value as Number).toLong()
    }
    private fun JSONObject.text(key: String, max: Int): String {
        val value = get(key)
        require(value is String && value.length <= max && '\u0000' !in value) { "Texto inválido em $key." }
        return value
    }
    private fun JSONObject.nullableText(key: String, max: Int): String? = if (isNull(key)) null else text(key, max)
    private fun JSONObject.bool(key: String): Boolean = get(key).also { require(it is Boolean) { "Preferência inválida em $key." } } as Boolean
    private fun <T> JSONObject.rows(key: String, convert: (JSONObject) -> T): List<T> {
        val list = getJSONArray(key)
        require(list.length() <= MAX_ROWS) { "Quantidade de itens inválida em $key." }
        return List(list.length()) { convert(list.getJSONObject(it)) }
    }
}

package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import org.json.JSONArray
import org.json.JSONObject

data class BackupDisplay(val appearance: Appearance, val hideAmounts: Boolean, val lastOrigin: String)
data class FinanceSnapshot(
    val createdAt: Long, val records: List<RecordEntity> = emptyList(), val drafts: List<DraftEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(), val display: BackupDisplay,
    val accountBalanceEvents: List<AccountBalanceEventEntity> = emptyList(),
    val forecastRules: List<ForecastRuleEntity> = emptyList(), val forecastOverrides: List<ForecastOverrideEntity> = emptyList(),
    val cycleRules: List<CycleRuleEntity> = emptyList(), val reserveEvents: List<ReserveEventEntity> = emptyList(),
    val forecastMeta: List<ForecastMetaEntity> = emptyList(),
)

internal fun normalizeDraftText(value: String): String = value.replace("\u0000", "")

internal fun validDraftSource(sourceKey: String, sourcePackage: String): Boolean =
    sourceKey.isNotBlank() && sourceKey.length <= 2048 && '\u0000' !in sourceKey &&
        sourcePackage.isNotBlank() && sourcePackage.length <= 300 && '\u0000' !in sourcePackage

/** Formato Rendly novo. Não interpreta backups do Controle Financeiro nem versões anteriores. */
object BackupSnapshot {
    const val FORMAT_VERSION = 1
    private const val MAX_ROWS = 50_000
    private fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
    private fun <T> array(rows: List<T>, convert: (T) -> JSONObject) = JSONArray().apply { rows.forEach { put(convert(it)) } }
    fun encode(snapshot: FinanceSnapshot): ByteArray {
        validate(snapshot)
        return json("format" to "rendly", "version" to FORMAT_VERSION, "createdAt" to snapshot.createdAt,
            "display" to json("appearance" to snapshot.display.appearance.name, "hideAmounts" to snapshot.display.hideAmounts, "lastOrigin" to snapshot.display.lastOrigin),
            "records" to array(snapshot.records) { r -> json("id" to r.id, "sourceDraftId" to r.sourceDraftId,
                "amountCents" to r.amountCents, "movement" to r.movement.name, "method" to r.method.name, "category" to r.category.name,
                "bank" to r.bank, "description" to r.description, "occurredAt" to r.occurredAt, "destination" to r.destination,
                "originalRecordId" to r.originalRecordId, "reversalMovement" to r.reversalMovement.name,
                "forecastItemId" to r.forecastItemId, "forecastOccurrenceKey" to r.forecastOccurrenceKey) },
            "drafts" to array(snapshot.drafts) { d -> json("id" to d.id, "sourceKey" to d.sourceKey, "sourcePackage" to d.sourcePackage,
                "bank" to normalizeDraftText(d.bank), "sourceTitle" to normalizeDraftText(d.sourceTitle),
                "sourceBody" to normalizeDraftText(d.sourceBody), "amountCents" to d.amountCents,
                "movement" to d.movement.name, "method" to d.method.name, "category" to d.category.name,
                "explanation" to normalizeDraftText(d.explanation),
                "occurredAt" to d.occurredAt, "capturedAt" to d.capturedAt, "status" to d.status, "revision" to d.revision) },
            "accounts" to array(snapshot.accounts) { a -> json("nameKey" to a.nameKey, "label" to a.label, "lastUsedAt" to a.lastUsedAt, "archived" to a.archived) },
            "accountBalanceEvents" to array(snapshot.accountBalanceEvents) { e -> json("id" to e.id, "accountKey" to e.accountKey,
                "balanceCents" to e.balanceCents, "effectiveAt" to e.effectiveAt, "createdAt" to e.createdAt, "reason" to e.reason) },
            "forecastRules" to array(snapshot.forecastRules, ForecastSnapshots::ruleJson),
            "forecastOverrides" to array(snapshot.forecastOverrides, ForecastSnapshots::overrideJson),
            "cycleRules" to array(snapshot.cycleRules) { json("effectiveStart" to it.effectiveStart, "day" to it.day) },
            "reserveEvents" to array(snapshot.reserveEvents) { json("id" to it.id, "kind" to it.kind, "amountCents" to it.amountCents, "createdAt" to it.createdAt, "reason" to it.reason, "cycleStart" to it.cycleStart) },
            "forecastMeta" to array(snapshot.forecastMeta) { json("key" to it.key, "value" to it.value) }
        ).toString().toByteArray(Charsets.UTF_8)
    }
    fun decode(bytes: ByteArray): FinanceSnapshot {
        require(bytes.size <= BackupCrypto.MAX_PAYLOAD_BYTES) { "Backup muito grande." }
        val j = JSONObject(bytes.toString(Charsets.UTF_8))
        require(j.text("format", 40) == "rendly" && j.long("version") == FORMAT_VERSION.toLong()) { "Formato de backup incompatível. Esta versão aceita somente backups novos do Rendly." }
        val display = j.getJSONObject("display")
        val snapshot = FinanceSnapshot(j.long("createdAt"),
            records = j.rows("records") { r -> RecordEntity(r.text("id",128),r.nullableText("sourceDraftId",128),r.long("amountCents"),
                Movement.valueOf(r.text("movement",30)),PaymentMethod.valueOf(r.text("method",30)),Category.valueOf(r.text("category",30)),
                r.text("bank",80),r.text("description",200),r.long("occurredAt"),r.text("destination",80),r.nullableText("originalRecordId",128),
                Movement.valueOf(r.text("reversalMovement",30)),r.nullableText("forecastItemId",200),r.nullableText("forecastOccurrenceKey",40)) },
            drafts = j.rows("drafts") { d -> DraftEntity(d.text("id",128),d.text("sourceKey",2048),d.text("sourcePackage",300),d.draftText("bank",80),
                d.draftText("sourceTitle",2000),d.draftText("sourceBody",20000),if(d.isNull("amountCents")) null else d.long("amountCents"),Movement.valueOf(d.text("movement",30)),
                PaymentMethod.valueOf(d.text("method",30)),Category.valueOf(d.text("category",30)),d.draftText("explanation",2000),d.long("occurredAt"),d.long("capturedAt"),
                d.text("status",30),d.integer("revision",1,Int.MAX_VALUE)) },
            accounts = j.rows("accounts") { AccountEntity(it.text("nameKey",160),it.text("label",80),it.long("lastUsedAt"),it.bool("archived")) },
            display = BackupDisplay(Appearance.valueOf(display.text("appearance",20)),display.bool("hideAmounts"),display.text("lastOrigin",80)),
            accountBalanceEvents = j.rows("accountBalanceEvents") { AccountBalanceEventEntity(it.long("id"),it.text("accountKey",160),it.long("balanceCents"),it.long("effectiveAt"),it.long("createdAt"),it.text("reason",200)) },
            forecastRules = j.rows("forecastRules",ForecastSnapshots::rule), forecastOverrides = j.rows("forecastOverrides",ForecastSnapshots::overrideRow),
            cycleRules = j.rows("cycleRules") { CycleRuleEntity(it.text("effectiveStart",10),it.integer("day",1,31)) },
            reserveEvents = j.rows("reserveEvents") { ReserveEventEntity(it.long("id"),it.text("kind",20),it.long("amountCents"),it.long("createdAt"),it.text("reason",200),it.nullableText("cycleStart",10)) },
            forecastMeta = j.rows("forecastMeta") { ForecastMetaEntity(it.text("key",40),it.text("value",100)) })
        validate(snapshot)
        return snapshot
    }
    fun validate(s: FinanceSnapshot) {
        require(s.createdAt > 0)
        require(listOf(s.records.size,s.drafts.size,s.accounts.size,s.accountBalanceEvents.size,s.forecastRules.size,s.forecastOverrides.size,s.cycleRules.size,s.reserveEvents.size,s.forecastMeta.size).sum() <= MAX_ROWS) { "O backup excede 50 mil itens." }
        fun unique(values: List<String>) { require(values.all { it.isNotBlank() } && values.distinct().size == values.size) { "Identificadores repetidos ou vazios." } }
        unique(s.records.map { it.id }); unique(s.drafts.map { it.id }); unique(s.drafts.map { it.sourceKey }); unique(s.accounts.map { it.nameKey })
        unique(s.records.mapNotNull { it.sourceDraftId })
        val drafts = s.drafts.associateBy { it.id }; val records = s.records.associateBy { it.id }
        s.records.forEach { r ->
            require(r.id.length <= 128 && RecordValidation.error(r.toRecord()) == null) { "Lançamento inválido." }
            require(r.sourceDraftId == null || drafts[r.sourceDraftId]?.status == "SAVED") { "Vínculo de notificação inválido." }
            r.originalRecordId?.let { id ->
                require(id != r.id && r.movement == Movement.REFUND)
                records[id]?.let { require(Refunds.applyOriginal(r.toRecord(),it.toRecord()) == r.toRecord()) }
            }
            r.forecastItemId?.let { id ->
                require(s.forecastRules.any { it.itemId == id && (if(r.movement == Movement.INCOME) "INCOME" else "EXPENSE") == it.kind })
                require(ForecastSnapshots.validOccurrenceKey(r.forecastOccurrenceKey!!))
            }
        }
        s.drafts.forEach { d ->
            require(d.id.length <= 128 && '\u0000' !in d.id && validDraftSource(d.sourceKey, d.sourcePackage))
            require(d.bank.length <= 80 && d.sourceTitle.length <= 2000 && d.sourceBody.length <= 20000 && d.explanation.length <= 2000)
            require(d.revision > 0 && d.occurredAt > 0 && d.capturedAt > 0 && (d.amountCents == null || d.amountCents in 0..Money.MAX_CENTS))
            require(d.status in setOf("PENDING","SAVED","DISMISSED") && (d.status == "PENDING" || d.sourceBody.isEmpty() && d.sourceTitle.isEmpty()))
        }
        s.accounts.forEach { require(it.label.isNotBlank() && it.label.length <= 80 && it.nameKey == AccountNames.key(it.label) && it.lastUsedAt >= 0) }
        unique(s.accountBalanceEvents.map { it.id.toString() })
        val keys = s.accounts.map { it.nameKey }.toSet()
        s.accountBalanceEvents.forEach { require(it.id > 0 && it.accountKey in keys && it.balanceCents in -Money.MAX_CENTS..Money.MAX_CENTS && it.effectiveAt > 0 && it.createdAt > 0 && it.reason.isNotBlank() && it.reason.length <= 200 && '\u0000' !in it.reason) }
        ForecastSnapshots.validate(s)
        require(s.display.lastOrigin.length <= 80 && '\u0000' !in s.display.lastOrigin)
    }
    internal fun JSONObject.integer(key: String, min: Int, max: Int): Int = long(key).also { require(it in min.toLong()..max.toLong()) }.toInt()
    internal fun JSONObject.long(key: String): Long = get(key).also { require(it is Int || it is Long) { "Número inteiro inválido: $key" } }.let { (it as Number).toLong() }
    private fun JSONObject.boundedText(key: String, max: Int): String = get(key).also { require(it is String && it.length <= max) { "Texto inválido: $key" } } as String
    internal fun JSONObject.text(key: String, max: Int): String = boundedText(key, max).also { require('\u0000' !in it) { "Texto inválido: $key" } }
    // Backups V1 existentes podem conter caracteres nulos no texto exibido da notificação.
    private fun JSONObject.draftText(key: String, max: Int): String = normalizeDraftText(boundedText(key, max))
    internal fun JSONObject.nullableText(key: String, max: Int): String? { require(has(key)); return if(isNull(key)) null else text(key,max) }
    internal fun JSONObject.bool(key: String): Boolean = get(key).also { require(it is Boolean) } as Boolean
    private fun <T> JSONObject.rows(key: String, convert: (JSONObject) -> T): List<T> {
        val list = getJSONArray(key); require(list.length() <= MAX_ROWS)
        return List(list.length()) { convert(list.getJSONObject(it)) }
    }
}

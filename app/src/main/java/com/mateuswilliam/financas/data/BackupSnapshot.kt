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
    val monthlyPlans: List<MonthlyPlanEntity> = emptyList(), val purchases: List<PurchaseEntity> = emptyList(),
    val fixedRecurring: List<FixedRecurringEntity> = emptyList(), val commitments: List<CommitmentEntity> = emptyList(),
    val recurringRevisions: List<RecurringRevisionEntity> = emptyList(), val recurringMonths: List<RecurringMonthEntity> = emptyList(),
    val accountBalanceEvents: List<AccountBalanceEventEntity> = emptyList(),
)

/** JSON de dados, sem desserialização de classes arbitrárias ou SQL recebido do arquivo. */
object BackupSnapshot {
    const val FORMAT_VERSION = 6
    private const val MAX_ROWS = 50_000
    private fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
    private fun <T> array(rows: List<T>, convert: (T) -> JSONObject) = JSONArray().apply { rows.forEach { put(convert(it)) } }

    fun encode(original: FinanceSnapshot): ByteArray {
        val snapshot = RecurringSnapshots.upgrade(original)
        validate(snapshot)
        return json(
            "format" to "controle-financeiro", "version" to FORMAT_VERSION, "createdAt" to snapshot.createdAt,
            "display" to json("appearance" to snapshot.display.appearance.name, "hideAmounts" to snapshot.display.hideAmounts, "lastOrigin" to snapshot.display.lastOrigin),
            "records" to array(snapshot.records) { r -> json("id" to r.id, "sourceDraftId" to r.sourceDraftId,
                "amountCents" to r.amountCents, "movement" to r.movement.name, "method" to r.method.name,
                "category" to r.category.name, "bank" to r.bank, "description" to r.description, "occurredAt" to r.occurredAt, "destination" to r.destination, "originalRecordId" to r.originalRecordId, "reversalMovement" to r.reversalMovement.name) },
            "drafts" to array(snapshot.drafts) { d -> json("id" to d.id, "sourceKey" to d.sourceKey, "sourcePackage" to d.sourcePackage,
                "bank" to d.bank, "sourceTitle" to d.sourceTitle, "sourceBody" to d.sourceBody, "amountCents" to d.amountCents,
                "movement" to d.movement.name, "method" to d.method.name, "category" to d.category.name,
                "explanation" to d.explanation, "occurredAt" to d.occurredAt, "capturedAt" to d.capturedAt, "status" to d.status, "revision" to d.revision) },
            "accounts" to array(snapshot.accounts) { a -> json("nameKey" to a.nameKey, "label" to a.label, "lastUsedAt" to a.lastUsedAt, "archived" to a.archived) },
            "accountBalanceEvents" to array(snapshot.accountBalanceEvents) { e -> json("id" to e.id, "accountKey" to e.accountKey,
                "balanceCents" to e.balanceCents, "effectiveAt" to e.effectiveAt, "createdAt" to e.createdAt, "reason" to e.reason) },
            "budgets" to array(snapshot.budgets) { b -> json("month" to b.month, "category" to b.category.name, "limitCents" to b.limitCents) },
            "recurring" to array(snapshot.recurring) { r -> json("id" to r.id, "title" to r.title, "amountCents" to r.amountCents,
                "movement" to r.movement.name, "method" to r.method.name, "category" to r.category.name, "bank" to r.bank,
                "nextDate" to r.nextDate, "dayOfMonth" to r.dayOfMonth, "active" to r.active,
                "lastRemindedDate" to r.lastRemindedDate, "archived" to r.archived, "startMonth" to r.startMonth) },
            "recurringRevisions" to array(snapshot.recurringRevisions) { r -> json("recurringId" to r.recurringId,
                "effectiveMonth" to r.effectiveMonth, "title" to r.title, "amountCents" to r.amountCents,
                "movement" to r.movement.name, "method" to r.method.name, "category" to r.category.name,
                "bank" to r.bank, "anchorDate" to r.anchorDate, "dayOfMonth" to r.dayOfMonth,
                "active" to r.active, "fixedFromMonth" to r.fixedFromMonth) },
            "recurringMonths" to array(snapshot.recurringMonths) { r -> json("recurringId" to r.recurringId,
                "month" to r.month, "title" to r.title, "amountCents" to r.amountCents,
                "movement" to r.movement.name, "method" to r.method.name, "category" to r.category.name,
                "bank" to r.bank, "dueDate" to r.dueDate, "dayOfMonth" to r.dayOfMonth,
                "active" to r.active, "status" to r.status, "recordId" to r.recordId,
                "fixedExpense" to r.fixedExpense, "revision" to r.revision, "overridden" to r.overridden,
                "lastRemindedDate" to r.lastRemindedDate) },
            "monthlyPlans" to array(snapshot.monthlyPlans) { p -> json("month" to p.month, "incomeCents" to p.incomeCents, "healthyLimitBasisPoints" to p.healthyLimitBasisPoints) },
            "purchases" to array(snapshot.purchases) { p -> json("id" to p.id, "title" to p.title, "bank" to p.bank,
                "category" to p.category.name, "method" to p.method.name, "totalCents" to p.totalCents,
                "installmentCount" to p.installmentCount, "paidInstallments" to p.paidInstallments,
                "nextDueDate" to p.nextDueDate, "dayOfMonth" to p.dayOfMonth, "sourceDraftId" to p.sourceDraftId,
                "revision" to p.revision, "openCancelled" to p.openCancelled) },
            "fixedRecurring" to array(snapshot.fixedRecurring) { p -> json("recurringId" to p.recurringId, "fromMonth" to p.fromMonth) },
            "commitments" to array(snapshot.commitments) { c -> json("id" to c.id, "label" to c.label,
                "amountCents" to c.amountCents, "dueDate" to c.dueDate, "purchaseId" to c.purchaseId,
                "installmentNumber" to c.installmentNumber, "recurringId" to c.recurringId, "recordId" to c.recordId,
                "adjusted" to c.adjusted, "excluded" to c.excluded) },
            "occurrences" to array(snapshot.occurrences) { o -> json("recurringId" to o.recurringId, "dueDate" to o.dueDate, "status" to o.status, "recordId" to o.recordId) },
        ).toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupCrypto.MAX_PAYLOAD_BYTES) { "O backup excede o limite de 16 MiB." }
        }
    }

    fun decode(bytes: ByteArray): FinanceSnapshot {
        require(bytes.size <= BackupCrypto.MAX_PAYLOAD_BYTES) { "Backup maior que o limite permitido." }
        val j = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { throw IllegalArgumentException("Conteúdo de backup inválido.") }
        val version = j.long("version")
        require(j.text("format", 40) == "controle-financeiro" && version in 1L..FORMAT_VERSION.toLong()) { "Formato ou versão de dados não suportado." }
        require(version >= 5 || (!j.has("recurringRevisions") && !j.has("recurringMonths"))) { "Competências mensais exigem a versão 5 do backup." }
        if (version == 1L) require(listOf("monthlyPlans", "purchases", "fixedRecurring", "commitments").none { j.has(it) }) {
            "Dados de comprometimento exigem a versão 2 do backup."
        }
        require(version >= 6 || !j.has("accountBalanceEvents")) { "Saldos exigem a versão 6 do backup." }
        val display = j.getJSONObject("display")
        val snapshot = FinanceSnapshot(j.long("createdAt"),
            j.rows("records") { r ->
                require(version >= 6 || (!r.has("originalRecordId") && !r.has("reversalMovement"))) { "Estornos vinculados exigem a versão 6 do backup." }
                if (version >= 6) require(r.has("originalRecordId"))
                RecordEntity(r.text("id", 128), r.nullableText("sourceDraftId", 128), r.long("amountCents"),
                Movement.valueOf(r.text("movement", 30)), PaymentMethod.valueOf(r.text("method", 30)), Category.valueOf(r.text("category", 30)),
                r.text("bank", 80), r.text("description", 200), r.long("occurredAt"), r.text("destination", 80), if (version >= 6) r.nullableText("originalRecordId", 128) else null,
                if (version >= 6) Movement.valueOf(r.text("reversalMovement", 30)) else Movement.EXPENSE) },
            j.rows("drafts") { d -> DraftEntity(d.text("id", 128), d.text("sourceKey", 2048), d.text("sourcePackage", 300),
                d.text("bank", 80), d.text("sourceTitle", 2000), d.text("sourceBody", 20_000), if (d.isNull("amountCents")) null else d.long("amountCents"),
                Movement.valueOf(d.text("movement", 30)), PaymentMethod.valueOf(d.text("method", 30)), Category.valueOf(d.text("category", 30)),
                d.text("explanation", 2000), d.long("occurredAt"), d.long("capturedAt"), d.text("status", 30), d.long("revision").also { require(it in 1L..Int.MAX_VALUE.toLong()) }.toInt()) },
            j.rows("accounts") { a ->
                require(version >= 6 || !a.has("archived")) { "Arquivamento de contas exige a versão 6 do backup." }
                AccountEntity(a.text("nameKey", 160), a.text("label", 80), a.long("lastUsedAt"), version >= 6 && a.bool("archived")) },
            j.rows("budgets") { b -> BudgetEntity(b.text("month", 12), Category.valueOf(b.text("category", 30)), b.long("limitCents")) },
            j.rows("recurring") { r ->
                require(version >= 4 || !r.has("archived")) { "Exclusão de previsões exige a versão 4 do backup." }
                require(version >= 5 || !r.has("startMonth")) { "Competências mensais exigem a versão 5 do backup." }
                RecurringEntity(r.text("id", 128), r.text("title", 200), r.long("amountCents"),
                    Movement.valueOf(r.text("movement", 30)), PaymentMethod.valueOf(r.text("method", 30)), Category.valueOf(r.text("category", 30)),
                    r.text("bank", 80), r.text("nextDate", 20), r.long("dayOfMonth").also { require(it in 1L..31L) }.toInt(),
                    r.bool("active"), r.text("lastRemindedDate", 20), archived = version >= 4 && r.bool("archived"),
                    startMonth = if (version >= 5) r.text("startMonth", 7) else "")
            },
            j.rows("occurrences") { o -> OccurrenceEntity(o.text("recurringId", 128), o.text("dueDate", 20), o.text("status", 30), o.nullableText("recordId", 128)) },
            BackupDisplay(Appearance.valueOf(display.text("appearance", 20)), display.bool("hideAmounts"), display.text("lastOrigin", 80)),
            if (version >= 2) j.rows("monthlyPlans") { p -> MonthlyPlanEntity(p.text("month", 12), p.long("incomeCents"), p.integer("healthyLimitBasisPoints", 0, 10000)) } else emptyList(),
            if (version >= 2) j.rows("purchases") { p ->
                require(version >= 3 || (!p.has("revision") && !p.has("openCancelled"))) { "Gestão de parcelas exige a versão 3 do backup." }
                PurchaseEntity(p.text("id", 128), p.text("title", 200), p.text("bank", 80),
                    Category.valueOf(p.text("category", 30)), PaymentMethod.valueOf(p.text("method", 30)), p.long("totalCents"),
                    p.integer("installmentCount", 1, Installments.MAX_INSTALLMENTS), p.integer("paidInstallments", 0, Installments.MAX_INSTALLMENTS - 1),
                    p.text("nextDueDate", 20), p.integer("dayOfMonth", 1, 31), p.nullableText("sourceDraftId", 128),
                    revision = if (version >= 3) p.long("revision") else 0L, openCancelled = version >= 3 && p.bool("openCancelled"))
            } else emptyList(),
            if (version >= 2) j.rows("fixedRecurring") { p -> FixedRecurringEntity(p.text("recurringId", 128), p.text("fromMonth", 12)) } else emptyList(),
            if (version >= 2) j.rows("commitments") { c ->
                require(version >= 3 || !c.has("adjusted")) { "Ajustes de parcelas exigem a versão 3 do backup." }
                require(version >= 4 || !c.has("excluded")) { "Exclusão de parcelas exige a versão 4 do backup." }
                CommitmentEntity(c.text("id", 256), c.text("label", 256), c.long("amountCents"),
                    c.text("dueDate", 20), c.nullableText("purchaseId", 128), if (c.isNull("installmentNumber")) null else c.integer("installmentNumber", 1, Installments.MAX_INSTALLMENTS),
                    c.nullableText("recurringId", 128), c.nullableText("recordId", 128),
                    adjusted = version >= 3 && c.bool("adjusted"), excluded = version >= 4 && c.bool("excluded"))
            } else emptyList(),
            if (version >= 5) j.rows("recurringRevisions") { r -> RecurringRevisionEntity(r.text("recurringId", 128), r.text("effectiveMonth", 7),
                r.text("title", 200), r.long("amountCents"), Movement.valueOf(r.text("movement", 30)), PaymentMethod.valueOf(r.text("method", 30)),
                Category.valueOf(r.text("category", 30)), r.text("bank", 80), r.text("anchorDate", 20), r.integer("dayOfMonth", 1, 31),
                r.bool("active"), r.nullableText("fixedFromMonth", 7)) } else emptyList(),
            if (version >= 5) j.rows("recurringMonths") { r -> RecurringMonthEntity(r.text("recurringId", 128), r.text("month", 7),
                r.text("title", 200), r.long("amountCents"), Movement.valueOf(r.text("movement", 30)), PaymentMethod.valueOf(r.text("method", 30)),
                Category.valueOf(r.text("category", 30)), r.text("bank", 80), r.text("dueDate", 20), r.integer("dayOfMonth", 1, 31),
                r.bool("active"), r.text("status", 20), r.nullableText("recordId", 128), r.bool("fixedExpense"), r.long("revision"),
                r.bool("overridden"), r.text("lastRemindedDate", 20)) } else emptyList(),
            if (version >= 6) j.rows("accountBalanceEvents") { e -> AccountBalanceEventEntity(e.long("id"), e.text("accountKey", 160),
                e.long("balanceCents"), e.long("effectiveAt"), e.long("createdAt"), e.text("reason", 200)) } else emptyList(),
        )
        val upgraded = if (version < 5) RecurringSnapshots.upgrade(snapshot) else snapshot
        validate(upgraded)
        return upgraded
    }

    fun validate(snapshot: FinanceSnapshot) {
        require(snapshot.createdAt > 0) { "Data de backup inválida." }
        val total = listOf(snapshot.records.size, snapshot.drafts.size, snapshot.accounts.size, snapshot.budgets.size, snapshot.recurring.size, snapshot.occurrences.size, snapshot.monthlyPlans.size, snapshot.purchases.size, snapshot.fixedRecurring.size, snapshot.commitments.size, snapshot.recurringRevisions.size, snapshot.recurringMonths.size, snapshot.accountBalanceEvents.size).sum()
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
        require(snapshot.accountBalanceEvents.map { it.id }.distinct().size == snapshot.accountBalanceEvents.size) { "Referências de saldo repetidas." }
        val accountKeys = snapshot.accounts.map { it.nameKey }.toSet()
        snapshot.accountBalanceEvents.forEach { e ->
            require(e.id > 0 && e.accountKey in accountKeys && e.balanceCents in -Money.MAX_CENTS..Money.MAX_CENTS &&
                e.effectiveAt > 0 && e.createdAt > 0 &&
                e.reason.isNotBlank() && e.reason.length <= 200 && '\u0000' !in e.reason) { "Referência de saldo inválida." }
        }
        snapshot.records.forEach { row ->
            row.originalRecordId?.let { id ->
                require(id != row.id && row.movement == Movement.REFUND) { "Vínculo de estorno inválido." }
                recordIds[id]?.let { original -> require(Refunds.applyOriginal(row.toRecord(), original.toRecord()) == row.toRecord()) { "Natureza de estorno inválida." } }
            }
        }
        snapshot.budgets.forEach { require(Budgets.error(it.toBudget()) == null) { "Orçamento inválido no backup." } }
        snapshot.recurring.forEach { plan ->
            require(Recurrences.error(plan.toPlan()) == null &&
                (plan.lastRemindedDate.isEmpty() || plan.lastRemindedDate == plan.nextDate)) { "Recorrência inválida no backup." }
            require(!plan.archived || (!plan.active && plan.lastRemindedDate.isEmpty())) { "Uma previsão excluída não pode estar ativa." }
        }
        val linkedRecords = snapshot.occurrences.mapNotNull { it.recordId }
        unique(linkedRecords)
        snapshot.occurrences.forEach { o ->
            require(o.recurringId in recurringIds && runCatching { LocalDate.parse(o.dueDate) }.isSuccess) { "Vencimento inválido no backup." }
            require(o.status in setOf("RECORDED", "SKIPPED", "REMOVED")) { "Estado de vencimento inválido." }
            require(if (o.status == "RECORDED") o.recordId in recordIds else o.recordId == null) { "Vínculo de lançamento recorrente inválido." }
        }
        CommitmentSnapshotValidation.validate(snapshot)
        RecurringSnapshots.validate(snapshot)
        require(snapshot.display.lastOrigin.length <= 80) { "Preferência de origem inválida." }
    }

    private fun JSONObject.integer(key: String, min: Int, max: Int): Int = long(key).also {
        require(it in min.toLong()..max.toLong()) { "Inteiro fora do limite em $key." }
    }.toInt()
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

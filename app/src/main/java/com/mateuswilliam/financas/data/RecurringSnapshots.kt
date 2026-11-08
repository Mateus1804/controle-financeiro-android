package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.time.YearMonth

/** A mesma conversão é usada pelos backups antigos e pela migração Room 7→8. */
internal object RecurringSnapshots {
    fun upgrade(snapshot: FinanceSnapshot): FinanceSnapshot {
        val fixed = snapshot.fixedRecurring.associateBy { it.recurringId }
        val occurrences = snapshot.occurrences.groupBy { it.recurringId }
        val origins = snapshot.recurring.map { r ->
            if (r.startMonth.isNotBlank()) r else r.copy(startMonth =
                (occurrences[r.id].orEmpty().map { it.dueDate.take(7) } + listOfNotNull(r.nextDate.take(7), fixed[r.id]?.fromMonth)).min())
        }
        val rules = snapshot.recurringRevisions.ifEmpty { origins.map {
            RecurringRevisionEntity.from(it.toPlan(), YearMonth.parse(it.startMonth), fixed[it.id]?.fromMonth)
        } }
        val records = snapshot.records.associateBy { it.id }
        val commitments = snapshot.commitments.filter { it.recurringId != null }.associateBy { it.recurringId to it.dueDate }
        val existing = snapshot.recurringMonths.associateBy { it.recurringId to it.month }.toMutableMap()
        origins.forEach { origin ->
            occurrences[origin.id].orEmpty().groupBy { it.dueDate.take(7) }.forEach { (month, rows) ->
                if ((origin.id to month) in existing) return@forEach
                val occurrence = rows.sortedWith(compareBy<OccurrenceEntity> { when (it.status) { "RECORDED" -> 0; "REMOVED" -> 1; else -> 2 } }
                    .thenByDescending { it.dueDate }).first()
                val paid = occurrence.recordId?.let { records[it] }
                val commitment = commitments[origin.id to occurrence.dueDate]
                val plan = origin.toPlan().copy(title = commitment?.label ?: origin.title,
                    amountCents = commitment?.amountCents ?: paid?.amountCents ?: origin.amountCents,
                    movement = if (commitment != null) Movement.EXPENSE else origin.movement,
                    method = paid?.method ?: origin.method, category = paid?.category ?: origin.category,
                    bank = paid?.bank ?: origin.bank, nextDate = occurrence.dueDate)
                existing[origin.id to month] = RecurringMonthEntity.from(plan, YearMonth.parse(month),
                    commitment != null || (fixed[origin.id]?.fromMonth?.let { month >= it } == true && plan.movement == Movement.EXPENSE))
                    .copy(status = when (occurrence.status) { "RECORDED" -> "RECORDED"; "SKIPPED" -> "SKIPPED"; else -> "PENDING" }, recordId = paid?.id)
            }
        }
        return snapshot.copy(recurring = origins, recurringRevisions = rules, recurringMonths = existing.values.toList())
    }

    fun validate(snapshot: FinanceSnapshot) {
        val origins = snapshot.recurring.associateBy { it.id }
        val records = snapshot.records.associateBy { it.id }
        val occurrences = snapshot.occurrences.associateBy { it.recurringId to it.dueDate }
        fun month(value: String) = YearMonth.parse(value).also { require(it.toString() == value && it.year in 1..9999) }
        require(snapshot.recurringRevisions.map { it.recurringId to it.effectiveMonth }.distinct().size == snapshot.recurringRevisions.size)
        require(snapshot.recurringMonths.map { it.recurringId to it.month }.distinct().size == snapshot.recurringMonths.size)
        require(snapshot.recurringMonths.mapNotNull { it.recordId }.distinct().size == snapshot.recurringMonths.count { it.recordId != null })
        snapshot.recurring.forEach { origin ->
            month(origin.startMonth)
            require(snapshot.recurringRevisions.any { it.recurringId == origin.id && it.effectiveMonth == origin.startMonth }) { "Regra inicial da recorrência ausente." }
        }
        snapshot.recurringRevisions.forEach { rule ->
            val origin = origins[rule.recurringId] ?: error("Recorrência da regra ausente.")
            require(month(rule.effectiveMonth) >= month(origin.startMonth))
            require(Recurrences.error(rule.toPlan()) == null) { "Configuração recorrente inválida." }
            rule.fixedFromMonth?.let { month(it); require(rule.movement == Movement.EXPENSE) }
        }
        snapshot.recurringMonths.forEach { row ->
            val origin = origins[row.recurringId] ?: error("Recorrência da competência ausente.")
            require(month(row.month) >= month(origin.startMonth) && row.revision >= 0)
            require(YearMonth.from(LocalDate.parse(row.dueDate)) == month(row.month)) { "Vencimento fora da competência." }
            require(Recurrences.error(row.toPlan()) == null) { "Previsão mensal inválida." }
            require(row.status in setOf("PENDING", "RECORDED", "SKIPPED"))
            require(if (row.status == "RECORDED") row.recordId in records else row.recordId == null) { "Estado ou vínculo mensal inválido." }
            val occurrence = occurrences[row.recurringId to row.dueDate]
            require(when (row.status) {
                "RECORDED" -> occurrence?.status == "RECORDED" && occurrence.recordId == row.recordId
                "SKIPPED" -> occurrence?.status in setOf("SKIPPED", "REMOVED") && occurrence?.recordId == null
                else -> occurrence?.status != "RECORDED"
            }) { "O estado mensal não corresponde ao vencimento registrado." }
            require(!row.fixedExpense || row.movement == Movement.EXPENSE)
            require(row.lastRemindedDate.isEmpty() || row.lastRemindedDate == row.dueDate)
        }
    }
}

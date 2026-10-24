package com.mateuswilliam.financas.core

import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class ExpenseDimension(val label: String) {
    CATEGORY("Categoria"), BANK("Conta / banco"), METHOD("Pagamento");

    fun key(record: Record): String = when (this) {
        CATEGORY -> record.category.name
        BANK -> AccountNames.key(record.bank)
        METHOD -> record.method.name
    }

    fun label(record: Record): String = when (this) {
        CATEGORY -> record.category.label
        BANK -> AccountNames.clean(record.bank)
        METHOD -> record.method.label
    }
}

data class ExpenseGroup(val key: String, val label: String, val cents: Long, val basisPoints: Int)
data class ExpenseReport(val total: Long, val refunds: Long, val groups: List<ExpenseGroup>)
data class ExpenseFilter(val dimension: ExpenseDimension, val key: String, val label: String) {
    fun matches(record: Record): Boolean = record.movement == Movement.EXPENSE && dimension.key(record) == key
}

object ExpenseReports {
    fun month(records: List<Record>, month: YearMonth, zone: ZoneId, dimension: ExpenseDimension): ExpenseReport {
        val totals = linkedMapOf<String, Pair<String, Long>>()
        var refunds = 0L
        records.forEach { record ->
            if (YearMonth.from(Instant.ofEpochMilli(record.occurredAt).atZone(zone)) != month) return@forEach
            if (record.movement == Movement.REFUND) refunds = Math.addExact(refunds, record.amountCents)
            if (record.movement != Movement.EXPENSE) return@forEach
            val key = dimension.key(record)
            val previous = totals[key]
            totals[key] = (previous?.first ?: dimension.label(record)) to Math.addExact(previous?.second ?: 0, record.amountCents)
        }
        val ordered = totals.entries.sortedWith(compareByDescending<Map.Entry<String, Pair<String, Long>>> { it.value.second }.thenBy { it.key })
        val total = ordered.fold(0L) { sum, entry -> Math.addExact(sum, entry.value.second) }
        if (total == 0L) return ExpenseReport(0, refunds, emptyList())
        // Distribuo o resíduo pelas maiores frações para a legenda somar exatamente 100,00%.
        val denominator = BigInteger.valueOf(total)
        val shares = ordered.map { BigInteger.valueOf(it.value.second).multiply(BigInteger.valueOf(10000)).divideAndRemainder(denominator) }
        val percentages = shares.map { it[0].toInt() }.toMutableList()
        val remainder = 10000 - percentages.sum()
        shares.indices.sortedWith(compareByDescending<Int> { shares[it][1] }.thenBy { ordered[it].key })
            .take(remainder).forEach { percentages[it]++ }
        return ExpenseReport(total, refunds, ordered.mapIndexed { index, entry ->
            ExpenseGroup(entry.key, entry.value.first, entry.value.second, percentages[index])
        })
    }
}

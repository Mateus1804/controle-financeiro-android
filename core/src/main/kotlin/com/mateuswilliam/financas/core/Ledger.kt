package com.mateuswilliam.financas.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MonthlySummary(
    val records: List<Record>,
    val income: Long,
    val expenses: Long,
    val categories: Map<Category, Long>,
) {
    val result: Long get() = income - expenses
}

object Ledger {
    fun month(records: List<Record>, month: YearMonth, zone: ZoneId): MonthlySummary {
        val selected = records.filter {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == month
        }.sortedByDescending { it.occurredAt }
        val expenses = selected.sumOf {
            it.expenseContribution()
        }
        val categories = Category.entries.associateWith { category -> selected.filter { it.category == category }.sumOf {
            it.expenseContribution()
        } }.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMap()
        return MonthlySummary(selected, selected.sumOf { it.incomeContribution() }, expenses, categories)
    }

    fun csv(records: List<Record>, zone: ZoneId): String {
        fun cell(value: String): String {
            // Planilhas podem executar fórmulas mesmo em células entre aspas.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val header = listOf("Identificador", "Data", "Movimentação", "Meio", "Categoria", "Conta", "Destino", "Descrição", "Valor BRL", "Natureza do estorno", "Lançamento original")
        val rows = records.map { record -> listOf(
            record.id, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(record.occurredAt).atZone(zone)),
            record.movement.label, record.method.label, if (record.movement == Movement.TRANSFER) "" else record.category.label, record.bank,
            record.destination, record.description, Money.editable(record.amountCents),
            if (record.movement == Movement.REFUND) record.reversalMovement.label else "", record.originalRecordId.orEmpty(),
        ) }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { it.joinToString(";", transform = ::cell) } + "\r\n"
    }
}

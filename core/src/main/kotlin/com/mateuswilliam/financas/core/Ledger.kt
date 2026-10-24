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
            when (it.movement) { Movement.EXPENSE -> it.amountCents; Movement.REFUND -> -it.amountCents; else -> 0L }
        }
        val categories = Category.entries.associateWith { category -> selected.filter { it.category == category }.sumOf {
            when (it.movement) { Movement.EXPENSE -> it.amountCents; Movement.REFUND -> -it.amountCents; else -> 0L }
        } }.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMap()
        return MonthlySummary(selected, selected.filter { it.movement == Movement.INCOME }.sumOf { it.amountCents }, expenses, categories)
    }

    fun csv(records: List<Record>, zone: ZoneId): String {
        fun cell(value: String): String {
            // Planilhas podem executar fórmulas mesmo em células entre aspas.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val header = listOf("Identificador", "Data", "Movimentação", "Meio", "Categoria", "Conta", "Destino", "Descrição", "Valor BRL")
        val rows = records.map { record -> listOf(
            record.id, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(record.occurredAt).atZone(zone)),
            record.movement.label, record.method.label, record.category.label, record.bank,
            record.destination, record.description, Money.editable(record.amountCents),
        ) }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { it.joinToString(";", transform = ::cell) } + "\r\n"
    }
}

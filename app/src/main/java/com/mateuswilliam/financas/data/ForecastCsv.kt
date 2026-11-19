package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*

internal fun forecastCsv(report: ForecastReport): String {
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    val rows = listOf(listOf("Início do ciclo", "Fim do ciclo", "Descrição", "Data prevista", "Tipo", "Valor previsto BRL", "Incluído no orçamento")) + report.lines.map {
        listOf(report.period.start.toString(), report.period.endExclusive.minusDays(1).toString(), it.title, it.date.toString(),
            if (it.kind == ForecastKind.INCOME) "Receita prevista" else "Gasto previsto", Money.editable(it.amountCents), if (it.included) "Sim" else "Não")
    }
    return "\uFEFF" + rows.joinToString("\r\n") { it.joinToString(";", transform = ::cell) } + "\r\n"
}

package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.ForecastKind
import com.mateuswilliam.financas.core.ForecastReport
import com.mateuswilliam.financas.core.Money
import com.mateuswilliam.financas.core.encodeCsv

internal fun forecastCsv(report: ForecastReport): String {
    val rows = listOf(listOf("Início do ciclo", "Fim do ciclo", "Descrição", "Data prevista", "Tipo", "Valor previsto BRL", "Incluído no orçamento")) + report.lines.map {
        listOf(report.period.start.toString(), report.period.endExclusive.minusDays(1).toString(), it.title, it.date.toString(),
            if (it.kind == ForecastKind.INCOME) "Receita prevista" else "Gasto previsto", Money.editable(it.amountCents), if (it.included) "Sim" else "Não")
    }
    return encodeCsv(rows)
}

package com.mateuswilliam.financas.core

/** Legenda do mapa de gastos: maiores grupos em destaque e o restante agrupado em "Outros". */
data class ExpenseLegend(val visible: List<ExpenseGroup>, val hasOthers: Boolean) {
    companion object {
        const val OTHERS_KEY = "__remaining__"
        const val DEFAULT_VISIBLE = 5

        /**
         * [ExpenseReport.groups] já vem ordenado por valor. "Outros" soma centavos e pontos-base
         * dos grupos restantes, então a legenda continua somando o total e 100,00%.
         */
        fun of(report: ExpenseReport, maxVisible: Int = DEFAULT_VISIBLE): ExpenseLegend {
            require(maxVisible > 0) { "maxVisible deve ser positivo" }
            val groups = report.groups.sortedWith(compareByDescending<ExpenseGroup> { it.cents }.thenBy { it.key })
            if (groups.size <= maxVisible + 1) return ExpenseLegend(groups, false)
            val rest = groups.drop(maxVisible)
            val others = ExpenseGroup(OTHERS_KEY, "Outros", rest.sumOf { it.cents }, rest.sumOf { it.basisPoints })
            return ExpenseLegend(groups.take(maxVisible) + others, true)
        }
    }
}

/** Texto do percentual sobre o total gasto; fatias minúsculas não aparecem como "0,00%". */
object ExpenseLegendText {
    fun percentage(basisPoints: Int): String =
        if (basisPoints <= 0) "<0,01%" else "${basisPoints / 100},${(basisPoints % 100).toString().padStart(2, '0')}%"
}

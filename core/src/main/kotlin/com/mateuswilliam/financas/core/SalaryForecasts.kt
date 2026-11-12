package com.mateuswilliam.financas.core

import java.time.YearMonth

/** Monthly salary settings are stored as forecast rules so existing backups retain them. */
object SalaryForecasts {
    private const val recurringId = "salary:recurring"
    private fun monthId(month: YearMonth) = "salary:month:$month"

    fun manages(itemId: String): Boolean = itemId == recurringId || itemId.startsWith("salary:month:")

    fun rule(month: YearMonth, cents: Long, repeat: Boolean): ForecastRule {
        val date = month.atDay(1)
        return ForecastRule(
            if (repeat) recurringId else monthId(month), date, "Salário líquido",
            ForecastKind.INCOME, cents, Category.SALARY,
            if (repeat) ForecastFrequency.MONTHLY else ForecastFrequency.ONCE, date,
        )
    }

    internal fun forMonth(data: ForecastDataset, month: YearMonth): ForecastRule? {
        val date = month.atDay(1)
        val settings = data.rules.filter { it.effectiveFrom <= date }
        val oneMonth = settings.filter { it.itemId == monthId(month) }.maxByOrNull { it.effectiveFrom }
        val recurring = settings.filter { it.itemId == recurringId }.maxByOrNull { it.effectiveFrom }
        val cents = oneMonth?.amountCents ?: recurring?.amountCents ?: data.monthlySalaryReferences[month]
        return cents?.takeIf { it > 0 }?.let { rule(month, it, repeat = false) }
    }
}

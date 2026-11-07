package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth

enum class PurchaseAmountBasis { TOTAL, INSTALLMENT }

object CommitmentForms {
    private val percentage = Regex("([0-9]{1,3})(?:[,.]([0-9]{1,2}))?")

    fun schedule(
        cents: Long?, basis: PurchaseAmountBasis, count: Int?, paid: Int?,
        nextDueDate: LocalDate, dayOfMonth: Int,
    ): InstallmentSchedule {
        val amount = requireNotNull(cents) { "Informe um valor válido para calcular as parcelas." }
        val installmentCount = requireNotNull(count) { "Informe a quantidade total de parcelas." }
        val paidInstallments = requireNotNull(paid) { "Informe quantas parcelas já foram pagas, mesmo que seja zero." }
        val schedule = when (basis) {
            PurchaseAmountBasis.TOTAL -> Installments.fromTotal(amount, installmentCount, nextDueDate, paidInstallments, dayOfMonth)
            PurchaseAmountBasis.INSTALLMENT -> Installments.fromInstallment(amount, installmentCount, nextDueDate, paidInstallments, dayOfMonth)
        }
        return schedule.requirePersistableDates()
    }

    fun monthlyPlan(month: YearMonth, incomeCents: Long?, limit: String): MonthlyIncomePlan {
        val income = requireNotNull(incomeCents) { "Informe a renda líquida prevista ou digite zero." }
        val parsed = percentage.matchEntire(limit.trim())
            ?: throw IllegalArgumentException("Informe um limite de 0% a 100%, com até duas casas decimais.")
        val whole = parsed.groupValues[1].toInt()
        val hundredths = parsed.groupValues[2].padEnd(2, '0').toInt()
        val plan = MonthlyIncomePlan(month, income, whole * 100 + hundredths)
        CommitmentPlanning.validate(plan)
        return plan
    }

    fun formatPercent(basisPoints: Int): String {
        val fraction = (basisPoints % 100).toString().padStart(2, '0').trimEnd('0')
        val whole = (basisPoints / 100).toString()
        return if (fraction.isEmpty()) whole else "$whole,$fraction"
    }
}

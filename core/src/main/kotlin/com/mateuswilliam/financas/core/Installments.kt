package com.mateuswilliam.financas.core

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/** Compromisso previsto; não representa um lançamento confirmado ou um pagamento. */
data class Installment(val number: Int, val dueDate: LocalDate, val amountCents: Long)

data class InstallmentSchedule(
    val totalCents: Long,
    val installmentCount: Int,
    val paidInstallments: Int,
    val dayOfMonth: Int,
    val pending: List<Installment>,
) {
    val remainingCents: Long get() = pending.sumOf { it.amountCents }
}

object Installments {
    // Limite técnico de geração em memória; não é uma condição oferecida por bancos.
    const val MAX_INSTALLMENTS = 600

    internal class Terms(
        val firstMonth: YearMonth,
        val numbers: IntRange,
        private val dayOfMonth: Int,
        private val baseCents: Long,
        private val extraCents: Long,
    ) {
        fun installment(number: Int): Installment {
            val month = firstMonth.plusMonths((number - numbers.first).toLong())
            val amount = baseCents + if (number.toLong() <= extraCents) 1L else 0L
            return Installment(number, month.atDay(minOf(dayOfMonth, month.lengthOfMonth())), amount)
        }
    }

    /** nextDueDate é a próxima parcela aberta, inclusive em compras já iniciadas. */
    fun fromTotal(
        totalCents: Long,
        installmentCount: Int,
        nextDueDate: LocalDate,
        paidInstallments: Int = 0,
        dayOfMonth: Int = nextDueDate.dayOfMonth,
    ): InstallmentSchedule {
        val terms = termsFromTotal(totalCents, installmentCount, nextDueDate, paidInstallments, dayOfMonth)
        return InstallmentSchedule(totalCents, installmentCount, paidInstallments, dayOfMonth,
            terms.numbers.map(terms::installment))
    }

    internal fun termsFromTotal(
        totalCents: Long,
        installmentCount: Int,
        nextDueDate: LocalDate,
        paidInstallments: Int = 0,
        dayOfMonth: Int = nextDueDate.dayOfMonth,
    ): Terms {
        require(installmentCount in 1..MAX_INSTALLMENTS) {
            "Informe de 1 a $MAX_INSTALLMENTS parcelas."
        }
        require(totalCents in 1..Money.MAX_CENTS) {
            "Informe um valor total maior que zero e dentro do limite monetário."
        }
        require(totalCents >= installmentCount.toLong()) {
            "Cada parcela precisa ter pelo menos um centavo."
        }
        require(paidInstallments in 0 until installmentCount) {
            "Informe as parcelas já pagas, mantendo pelo menos uma parcela em aberto."
        }
        require(dayOfMonth in 1..31) { "Informe um dia de vencimento entre 1 e 31." }
        val month = YearMonth.from(nextDueDate)
        require(nextDueDate == month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))) {
            "A próxima data precisa corresponder ao dia de vencimento ajustado ao mês."
        }

        try {
            month.plusMonths((installmentCount - paidInstallments - 1).toLong())
        } catch (exception: DateTimeException) {
            throw IllegalArgumentException("O período das parcelas ultrapassa as datas permitidas.", exception)
        }
        // A numeração original conserva a distribuição dos centavos já pagos.
        return Terms(month, paidInstallments + 1..installmentCount, dayOfMonth,
            totalCents / installmentCount, totalCents % installmentCount)
    }

    /** O valor informado representa cada parcela; o total inclui também as já pagas. */
    fun fromInstallment(
        installmentCents: Long,
        installmentCount: Int,
        nextDueDate: LocalDate,
        paidInstallments: Int = 0,
        dayOfMonth: Int = nextDueDate.dayOfMonth,
    ): InstallmentSchedule {
        require(installmentCount in 1..MAX_INSTALLMENTS) {
            "Informe de 1 a $MAX_INSTALLMENTS parcelas."
        }
        require(installmentCents in 1..Money.MAX_CENTS) {
            "Informe um valor de parcela maior que zero e dentro do limite monetário."
        }
        require(installmentCents <= Money.MAX_CENTS / installmentCount) {
            "O valor total da compra ultrapassa o limite monetário."
        }
        return fromTotal(installmentCents * installmentCount, installmentCount, nextDueDate,
            paidInstallments, dayOfMonth)
    }
}

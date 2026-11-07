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

    /** nextDueDate é a próxima parcela aberta, inclusive em compras já iniciadas. */
    fun fromTotal(
        totalCents: Long,
        installmentCount: Int,
        nextDueDate: LocalDate,
        paidInstallments: Int = 0,
        dayOfMonth: Int = nextDueDate.dayOfMonth,
    ): InstallmentSchedule {
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

        val baseCents = totalCents / installmentCount
        val extraCents = totalCents % installmentCount
        var date = nextDueDate
        val pending = try {
            (paidInstallments + 1..installmentCount).map { number ->
                // A numeração original conserva a distribuição dos centavos já pagos.
                val amount = baseCents + if (number.toLong() <= extraCents) 1L else 0L
                val installment = Installment(number, date, amount)
                if (number < installmentCount) {
                    date = LocalDate.parse(Recurrences.followingDate(date.toString(), dayOfMonth))
                }
                installment
            }
        } catch (exception: DateTimeException) {
            throw IllegalArgumentException("O período das parcelas ultrapassa as datas permitidas.", exception)
        }
        return InstallmentSchedule(totalCents, installmentCount, paidInstallments, dayOfMonth, pending)
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

package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.time.LocalDate
import java.time.YearMonth

/** Uma obrigação do mês, fornecida uma única vez, mesmo quando já confirmada. */
data class FixedCommitment(val id: String, val label: String, val amountCents: Long, val dueDate: LocalDate)

enum class CommitmentState(val label: String) {
    NO_INCOME("Informe a renda mensal"), COMFORTABLE("Confortável"),
    ATTENTION("Atenção"), ABOVE_LIMIT("Acima do limite")
}

enum class CommitmentSliceKind { EXPENSE, OTHER, FREE }

data class CommitmentEntry(val id: String, val label: String, val amountCents: Long, val incomePercentage: BigDecimal?)

data class CommitmentSlice(
    val key: String,
    val kind: CommitmentSliceKind,
    val label: String,
    val amountCents: Long,
    val incomePercentage: BigDecimal,
    val entryIds: List<String>,
    val startMicroDegrees: Int,
    val sweepMicroDegrees: Int,
)

data class CommitmentReport(
    val month: YearMonth,
    val incomeCents: Long,
    val committedCents: Long,
    val freeCents: Long,
    val healthyLimitBasisPoints: Int,
    val limitCents: Long,
    val incomePercentage: BigDecimal?,
    val state: CommitmentState,
    val entries: List<CommitmentEntry>,
    val slices: List<CommitmentSlice>,
    val limitMarkerMicroDegrees: Int?,
) {
    val largestExpense: CommitmentEntry? get() = entries.firstOrNull()
    val exceedsIncome: Boolean get() = incomeCents > 0 && committedCents > incomeCents
}

object CommitmentReports {
    const val MICRODEGREES_PER_DEGREE = 1_000_000
    private const val FULL_CIRCLE = 360_000_000
    private const val TOP = -90_000_000
    private val basisPoints = BigInteger.valueOf(10000)
    private val hundred = BigInteger.valueOf(100)

    private data class SlicePart(
        val key: String, val kind: CommitmentSliceKind, val label: String,
        val amountCents: Long, val entryIds: List<String>,
    )

    /** Os 3.000 pontos-base iniciais são ajustáveis e não constituem orientação financeira. */
    fun month(
        commitments: List<FixedCommitment>,
        month: YearMonth,
        incomeCents: Long,
        healthyLimitBasisPoints: Int = 3000,
    ): CommitmentReport {
        require(incomeCents in 0..Money.MAX_CENTS) { "Informe uma renda prevista dentro do limite monetário." }
        require(healthyLimitBasisPoints in 0..10000) { "Informe um limite entre 0% e 100% da renda." }
        commitments.forEach {
            require(it.id.isNotBlank() && it.label.isNotBlank()) { "Informe o identificador e o nome de cada compromisso." }
            require(it.amountCents in 1..Money.MAX_CENTS) { "Informe compromissos positivos e dentro do limite monetário." }
        }
        val selected = commitments.filter { YearMonth.from(it.dueDate) == month }
        require(selected.map { it.id }.toSet().size == selected.size) {
            "O mesmo compromisso não pode aparecer mais de uma vez no mês."
        }
        val entries = selected.sortedWith(compareByDescending<FixedCommitment> { it.amountCents }.thenBy { it.id })
            .map { CommitmentEntry(it.id, it.label.trim(), it.amountCents, percentage(it.amountCents, incomeCents)) }
        val total = entries.fold(0L) { sum, entry -> Math.addExact(sum, entry.amountCents) }
        val free = (incomeCents - total).coerceAtLeast(0L)
        val income = BigInteger.valueOf(incomeCents)
        val scaledLimit = income.multiply(BigInteger.valueOf(healthyLimitBasisPoints.toLong()))
        val state = state(total, incomeCents, scaledLimit)
        val parts = if (incomeCents > 0) parts(entries, income, free) else emptyList()
        val denominator = maxOf(incomeCents, total)
        val slices = if (incomeCents > 0) slices(parts, denominator, incomeCents) else emptyList()
        // O marcador cabe no círculo; o limite em centavos cabe na renda validada.
        // As conversões comuns preservam a API 26, sem depender de BigInteger.*ValueExact (API 31).
        val marker = if (incomeCents > 0) TOP + scaledLimit.multiply(BigInteger.valueOf(FULL_CIRCLE.toLong()))
            .divide(BigInteger.valueOf(denominator).multiply(basisPoints)).toInt() else null
        return CommitmentReport(month, incomeCents, total, free, healthyLimitBasisPoints,
            scaledLimit.divide(basisPoints).toLong(), percentage(total, incomeCents),
            state, entries, slices, marker)
    }

    /** Adapta previsões abertas; confirmações e dados persistidos serão vinculados na integração. */
    fun fromInstallments(purchaseId: String, label: String, schedule: InstallmentSchedule): List<FixedCommitment> {
        require(purchaseId.isNotBlank() && label.isNotBlank()) { "Informe o identificador e o nome da compra." }
        return schedule.pending.map { part ->
            FixedCommitment("installment:$purchaseId:${part.number}", "${label.trim()} · ${part.number}/${schedule.installmentCount}",
                part.amountCents, part.dueDate)
        }
    }

    private fun percentage(cents: Long, incomeCents: Long): BigDecimal? = if (incomeCents == 0L) null else
        BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(incomeCents), MathContext.DECIMAL128)

    private fun state(total: Long, incomeCents: Long, scaledLimit: BigInteger): CommitmentState {
        if (incomeCents == 0L) return CommitmentState.NO_INCOME
        if (total == 0L) return CommitmentState.COMFORTABLE
        val scaledTotal = BigInteger.valueOf(total).multiply(basisPoints)
        return when {
            scaledTotal > scaledLimit -> CommitmentState.ABOVE_LIMIT
            scaledTotal.multiply(hundred) >= scaledLimit.multiply(BigInteger.valueOf(80)) -> CommitmentState.ATTENTION
            else -> CommitmentState.COMFORTABLE
        }
    }

    private fun parts(entries: List<CommitmentEntry>, income: BigInteger, free: Long): List<SlicePart> {
        // O corte usa o valor individual e a renda, antes de qualquer arredondamento ou agrupamento.
        val (small, individual) = entries.partition {
            BigInteger.valueOf(it.amountCents).multiply(hundred) < income.multiply(BigInteger.valueOf(3))
        }
        val parts = individual.map {
            SlicePart("expense:${it.id}", CommitmentSliceKind.EXPENSE, it.label, it.amountCents, listOf(it.id))
        }.toMutableList()
        if (small.isNotEmpty()) parts.add(SlicePart("other", CommitmentSliceKind.OTHER, "Outros",
            small.fold(0L) { sum, entry -> Math.addExact(sum, entry.amountCents) }, small.map { it.id }))
        val ordered = parts.sortedWith(compareByDescending<SlicePart> { it.amountCents }.thenBy { it.key }).toMutableList()
        if (free > 0) ordered.add(SlicePart("free", CommitmentSliceKind.FREE, "Livre", free, emptyList()))
        return ordered
    }

    private fun slices(parts: List<SlicePart>, denominator: Long, incomeCents: Long): List<CommitmentSlice> {
        val divisor = BigInteger.valueOf(denominator)
        val shares = parts.map {
            BigInteger.valueOf(it.amountCents).multiply(BigInteger.valueOf(FULL_CIRCLE.toLong())).divideAndRemainder(divisor)
        }
        // Cada parte é menor ou igual ao denominador: o quociente cabe em FULL_CIRCLE.
        val angles = shares.map { it[0].toInt() }.toMutableList()
        val missing = FULL_CIRCLE - angles.sum()
        if (missing > 0) shares.indices.sortedWith(compareByDescending<Int> { shares[it][1] }.thenBy { it })
            .take(missing).forEach { angles[it]++ }
        var start = TOP
        return parts.mapIndexed { index, part ->
            // O último limite fecha em 270°, sem alterar centavos nem percentuais da renda.
            val slice = CommitmentSlice(part.key, part.kind, part.label, part.amountCents,
                percentage(part.amountCents, incomeCents)!!, part.entryIds, start, angles[index])
            start += angles[index]
            slice
        }
    }
}

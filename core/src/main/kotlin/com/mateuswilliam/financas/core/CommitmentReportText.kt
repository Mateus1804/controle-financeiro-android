package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Formatação do relatório; as comparações financeiras continuam no core. */
object CommitmentReportText {
    private val hundredth = BigDecimal("0.01")
    private val fullIncome = BigDecimal.valueOf(100)

    fun percentage(value: BigDecimal, limitBasisPoints: Int? = null): String {
        if (value.signum() > 0 && value < hundredth) return "< 0,01%"
        val limit = limitBasisPoints?.let { BigDecimal.valueOf(it.toLong(), 2) }
        var decimals = 2
        var rounded = value.setScale(decimals, RoundingMode.HALF_UP)
        // Amplia a precisão só quando o arredondamento confundiria excesso, folga ou igualdade.
        while (rounded.masksDifferenceFrom(value, fullIncome) ||
            (limit != null && rounded.masksDifferenceFrom(value, limit))
        ) {
            rounded = value.setScale(++decimals, RoundingMode.HALF_UP)
        }
        return rounded.stripTrailingZeros().toPlainString().replace('.', ',') + "%"
    }

    private fun BigDecimal.masksDifferenceFrom(exact: BigDecimal, boundary: BigDecimal): Boolean =
        compareTo(boundary) == 0 && exact.compareTo(boundary) != 0
}

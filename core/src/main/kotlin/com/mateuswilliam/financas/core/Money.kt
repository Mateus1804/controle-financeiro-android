package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    const val MAX_CENTS = 1_000_000_000_000L
    private val brazilian = Regex("\\d+(?:,\\d{1,2})?")
    private val grouped = Regex("\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?")
    private val decimalPoint = Regex("\\d+\\.\\d{1,2}")

    fun parse(text: String): Long? {
        val value = text.trim().removePrefix("R$").trim().replace("\u00a0", "")
        val normalized = when {
            brazilian.matches(value) || grouped.matches(value) -> value.replace(".", "").replace(',', '.')
            decimalPoint.matches(value) -> value
            else -> return null
        }
        return runCatching {
            BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact().takeIf { it in 0..MAX_CENTS }
        }.getOrNull()
    }

    fun editable(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

    fun format(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(cents, 2))
}

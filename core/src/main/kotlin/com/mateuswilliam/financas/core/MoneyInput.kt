package com.mateuswilliam.financas.core

/** A entrada numérica representa centavos; textos formatados mantêm sua unidade em reais. */
object MoneyInput {
    fun cents(digits: String): Long? = digits.takeIf { value ->
        value.isNotEmpty() && value.all { it in '0'..'9' }
    }?.toLongOrNull()?.takeIf { it in 0..Money.MAX_CENTS }

    fun normalize(text: String): String? {
        if (text.isEmpty()) return ""
        if (text.length > 64) return null
        if (text.all { it in '0'..'9' }) {
            val digits = text.trimStart('0').ifEmpty { "0" }
            return digits.takeIf { cents(it) != null }
        }
        return Money.parse(text)?.toString()
    }

    fun fromCents(cents: Long): String {
        require(cents in 0..Money.MAX_CENTS)
        return cents.toString()
    }
}

package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class MoneyInputTest {
    @Test fun `digitos representam centavos e nao reais`() {
        listOf("1" to 1L, "12" to 12L, "123" to 123L, "1234" to 1234L, "123456" to 123456L).forEach { (input, cents) ->
            assertEquals(cents, MoneyInput.cents(MoneyInput.normalize(input)!!))
        }
    }

    @Test fun `colagem formatada preserva a unidade em reais`() {
        listOf("R$ 1.234,56" to "123456", "42,90" to "4290", "42.90" to "4290", "12,3" to "1230").forEach { (input, digits) ->
            assertEquals(digits, MoneyInput.normalize(input))
        }
    }

    @Test fun `campo vazio e zero continuam distintos`() {
        assertEquals("", MoneyInput.normalize(""))
        assertNull(MoneyInput.cents(""))
        assertEquals("0", MoneyInput.normalize("000"))
        assertEquals(0L, MoneyInput.cents("0"))
    }

    @Test fun `zeros a esquerda nao mudam o valor`() {
        assertEquals("1", MoneyInput.normalize("0001"))
        assertEquals("1234", MoneyInput.normalize("001234"))
    }

    @Test fun `limite continua igual ao do registro financeiro`() {
        assertEquals(Money.MAX_CENTS.toString(), MoneyInput.normalize(Money.MAX_CENTS.toString()))
        assertNull(MoneyInput.normalize((Money.MAX_CENTS + 1).toString()))
        assertNull(MoneyInput.normalize("R$ 10.000.000.000,01"))
        assertNull(MoneyInput.normalize("9".repeat(100)))
    }

    @Test fun `conteudo invalido ou ambiguo nao vira dinheiro`() {
        listOf("-1", "1,234", "12.34,56", "12 reais", "NaN", "1e3", " ", "١٢٣").forEach {
            assertNull(it, MoneyInput.normalize(it))
        }
    }

    @Test fun `valores existentes e sugeridos nao sao convertidos duas vezes`() {
        listOf(0L, 1L, 4290L, 123456L, Money.MAX_CENTS).forEach {
            assertEquals(it, MoneyInput.cents(MoneyInput.fromCents(it)))
        }
    }
}

package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun `interpreta reais sem perder centavos`() { assertEquals(123456L, Money.parse("R$ 1.234,56")) }
    @Test fun `aceita centavos e separador decimal do teclado`() {
        assertEquals(10L, Money.parse("0,10")); assertEquals(4290L, Money.parse("42.90"))
    }
    @Test fun `separa agrupamento de milhar de casas decimais`() {
        assertEquals(123400L, Money.parse("1.234")); assertEquals(1230L, Money.parse("12,3"))
    }
    @Test fun `recusa formatos ambiguos e fracionamento de centavo`() {
        listOf("1,234", "12.34,56", "10,2,3", "1e4", "NaN", "", "-3", "12 reais").forEach { assertNull(it, Money.parse(it)) }
    }
    @Test fun `recusa estouro de inteiro e valores acima do limite`() {
        assertNull(Money.parse("999999999999999999999999999999")); assertNull(Money.parse("10000000000,01"))
    }
    @Test fun `formata valor editavel de forma reversivel`() {
        listOf(1L, 10L, 100L, 1099L, Money.MAX_CENTS).forEach { assertEquals(it, Money.parse(Money.editable(it))) }
    }
}

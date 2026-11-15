package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class AccountTotalTest {
    private fun account(name: String, cents: Long?, archived: Boolean = false) =
        AccountBalance(AccountOption(AccountNames.key(name), name, 0L), archived, cents, null)

    @Test fun `soma os saldos de todos os bancos ativos`() {
        val total = AccountTotal.of(listOf(account("Banco A", 150000), account("Banco B", 50000), account("Carteira", 0)))
        assertEquals(200000L, total.cents); assertEquals(3, total.knownAccounts); assertFalse(total.partial)
    }
    @Test fun `conta arquivada nao entra e conta sem saldo deixa o total parcial`() {
        val total = AccountTotal.of(listOf(account("Banco A", 100000), account("Antiga", 999999, archived = true), account("Nova", null)))
        assertEquals(100000L, total.cents); assertTrue(total.partial); assertTrue(total.hasBalance)
    }
    @Test fun `sem contas nao ha saldo`() {
        val total = AccountTotal.of(emptyList())
        assertEquals(0L, total.cents); assertFalse(total.hasBalance)
    }
    @Test fun `saldo negativo e preservado`() {
        assertEquals(-2500L, AccountTotal.of(listOf(account("Banco A", -2500))).cents)
    }
}

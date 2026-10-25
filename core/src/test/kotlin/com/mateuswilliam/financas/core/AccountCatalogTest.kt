package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class AccountCatalogTest {
    private fun record(bank: String, destination: String = "", time: Long = 10) =
        Record(bank, 100, Movement.EXPENSE, PaymentMethod.PIX, Category.OTHER, bank, "Teste", time, destination)

    @Test fun `normaliza espacos e caixa sem juntar contas distintas`() {
        assertEquals(AccountNames.key(" Inter  pessoal "), AccountNames.key("INTER pessoal"))
        assertNotEquals(AccountNames.key("Inter pessoal"), AccountNames.key("Inter trabalho"))
    }
    @Test fun `combina cadastro e historico sem modificar os registros`() {
        val records = listOf(record("  Inter  pessoal "), record("INTER pessoal", "Carteira"))
        val options = AccountCatalog.options(listOf(AccountOption("inter pessoal", "Inter pessoal", 20)), records)
        assertEquals(2, options.size)
        assertEquals("Inter pessoal", options.first().label)
        assertEquals("  Inter  pessoal ", records.first().bank)
    }
    @Test fun `ordem usa recencia e chave como desempate`() {
        val options = AccountCatalog.options(emptyList(), listOf(record("Z", time = 20), record("B"), record("A")))
        assertEquals(listOf("Z", "A", "B"), options.map { it.label })
    }
    @Test fun `destinos tambem sao reutilizaveis e nomes vazios sao ignorados`() {
        assertEquals(listOf("Banco", "Carteira"), AccountCatalog.options(emptyList(), listOf(record("Banco", "Carteira"))).map { it.label })
        assertTrue(AccountCatalog.options(emptyList(), listOf(record("  "))).isEmpty())
    }
    @Test fun `projecao leve preserva rotulo cadastrado recencia e contas distintas`() {
        val options = AccountCatalog.merge(listOf(AccountOption("inter pessoal", "Inter pessoal", 1)), listOf(
            AccountOption("", "  INTER\u00a0pessoal ", 30), AccountOption("", "Inter trabalho", 20),
            AccountOption("", "Carteira", 30), AccountOption("", " ", 100),
        ))
        assertEquals(listOf("Carteira", "Inter pessoal", "Inter trabalho"), options.map { it.label })
        assertEquals(listOf(30L, 30L, 20L), options.map { it.lastUsedAt })
    }
    @Test fun `catalogo de nomes projetados equivale ao historico completo`() {
        val history = listOf(record("Inter pessoal", "Carteira", 30), record("Inter trabalho", time = 20))
        val projected = history.flatMap { listOf(AccountOption("", it.bank, it.occurredAt), AccountOption("", it.destination, it.occurredAt)) }
        assertEquals(AccountCatalog.options(emptyList(), history), AccountCatalog.merge(emptyList(), projected))
    }

}

package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class AccountBalancesTest {
    private val accounts = listOf(AccountOption("a", "A", 0), AccountOption("b", "B", 0))
    private fun reference(id: Long = 1, key: String = "a", amount: Long = 1000, date: Long = 100) =
        AccountBalanceReference(id, key, amount, date, id, "Conferência")
    private fun record(id: String = "1", amount: Long = 100, date: Long = 100, movement: Movement = Movement.EXPENSE) =
        Record(id, amount, movement, PaymentMethod.PIX, Category.OTHER, "A", "Movimento", date, "B")
    private fun balances(refs: List<AccountBalanceReference>, records: List<Record>, date: Long = 200) =
        AccountBalances.calculate(accounts, setOf("b"), refs, records, date)

    @Test fun `unknown balances stay unknown and archived accounts retain funds`() {
        val result = balances(listOf(reference(key = "b")), emptyList())
        assertNull(result[0].balanceCents)
        assertEquals(1000L, result[1].balanceCents)
        assertTrue(result[1].archived)
    }
    @Test fun `baseline includes its day and ignores earlier and future transactions`() {
        val rows = listOf(record("old", date = 99), record(), record("future", date = 201))
        assertEquals(900L, balances(listOf(reference()), rows)[0].balanceCents)
        val refs = listOf(reference(), reference(2, amount = -500, date = 150), reference(3, amount = 9000, date = 201))
        assertEquals(-500L, balances(refs, rows)[0].balanceCents)
        assertEquals(900L, balances(refs, rows, 149)[0].balanceCents)
    }
    @Test fun `transfer edits and deletion recalculate both accounts atomically`() {
        val refs = listOf(reference(), reference(2, "b"))
        val transfer = record(movement = Movement.TRANSFER)
        assertEquals(listOf(900L, 1100L), balances(refs, listOf(transfer)).map { it.balanceCents })
        assertEquals(listOf(800L, 1200L), balances(refs, listOf(transfer.copy(amountCents = 200))).map { it.balanceCents })
        assertEquals(listOf(1000L, 1000L), balances(refs, emptyList()).map { it.balanceCents })
    }
    @Test fun `refund credits expenses and debits income even without original`() {
        val refund = record(movement = Movement.REFUND)
        val incomeRefund = refund.copy(id = "2", originalRecordId = "deleted", reversalMovement = Movement.INCOME, amountCents = 40)
        assertEquals(1060L, balances(listOf(reference()), listOf(refund, incomeRefund))[0].balanceCents)
    }
}

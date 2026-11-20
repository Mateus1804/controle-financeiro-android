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

    @Test fun `reads ledger rows once across accounts with different cutoffs`() {
        val a = accounts[0]
        val b = accounts[1]
        val unknown = AccountOption("c", "C", 0)
        val duplicate = a.copy(label = "A duplicada", lastUsedAt = 200)
        val aReference = reference()
        val bReference = reference(9, "b", Long.MIN_VALUE, 200).copy(createdAt = 20)
        val references = CountingList(listOf(
            reference(12, "b", 6000, 201),
            bReference,
            reference(10, "b", 5000, 200),
            reference(8, "b", 4000, 200).copy(createdAt = 20),
            bReference.copy(balanceCents = 3000),
            reference(11, "b", 2000, 199),
            aReference,
        ))
        val rows = CountingList(listOf(
            record("future", date = 201, movement = Movement.TRANSFER),
            record("legacy-self-transfer", date = 200, movement = Movement.TRANSFER).copy(bank = "B", destination = " b "),
            record("cutoff", date = 200, movement = Movement.TRANSFER),
            record("no-reference", date = 200, movement = Movement.INCOME).copy(bank = "C"),
            record("before-b-reference", date = 199, movement = Movement.TRANSFER),
            record("at-a-reference", date = 100, movement = Movement.TRANSFER),
            record("old", date = 99, movement = Movement.TRANSFER),
        ))

        val result = AccountBalances.calculate(listOf(b, unknown, a, duplicate), setOf("b"), references, rows, 200)

        assertEquals(listOf(
            AccountBalance(b, true, Long.MIN_VALUE + 100, bReference),
            AccountBalance(unknown, false, null, null),
            AccountBalance(a, false, 700L, aReference),
            AccountBalance(duplicate, false, 700L, aReference),
        ), result)
        assertEquals("Reference reads", references.size, references.reads)
        assertEquals("Record reads", rows.size, rows.reads)
    }

    private class CountingList<T>(private val values: List<T>) : AbstractList<T>() {
        var reads = 0
            private set

        override val size get() = values.size

        override fun get(index: Int): T {
            reads += 1
            return values[index]
        }
    }
}

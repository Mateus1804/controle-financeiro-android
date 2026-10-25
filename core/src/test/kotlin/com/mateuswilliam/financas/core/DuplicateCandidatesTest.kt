package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class DuplicateCandidatesTest {
    private val time = 1_792_800_000_000L
    private fun record(id: String, bank: String = "Inter pessoal", description: String = "Mercado Exemplo") =
        Record(id, 4290, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD, bank, description, time)
    @Test fun sameOriginAmountAndNearbyDateAreOnlyASuggestion() {
        val manual = record("manual")
        val candidates = DuplicateCandidates(listOf(manual)).find(record("novo", "  INTER PESSOAL "))
        assertEquals(manual, candidates.single().record)
        assertEquals("manual", manual.id)
    }
    @Test fun valueAloneIsInsufficientAndDifferentMovementIsExcluded() {
        val index = DuplicateCandidates(listOf(record("manual")))
        assertTrue(index.find(record("novo", "Outra origem", "Consulta médica")).isEmpty())
        assertTrue(index.find(record("novo").copy(movement = Movement.INCOME)).isEmpty())
    }
    @Test fun sameBankWithDistinctAccountsRemainsExplicitlyUncertain() {
        val result = DuplicateCandidates(listOf(record("manual"))).find(record("novo", "Inter trabalho", "Descrição diferente"))
        assertTrue(result.single().reasons.any { it.contains("conta pode ser diferente") })
    }
    @Test fun normalizedMeaningfulDescriptionCanMatchAnotherLabel() {
        val result = DuplicateCandidates(listOf(record("manual", "Conta A", "Farmácia Exemplo")))
            .find(record("novo", "Conta B", "Compra aprovada no cartão: Farmacia Exemplo"))
        assertTrue(result.single().reasons.contains("Descrição semelhante"))
    }
    @Test fun windowBoundaryAndUnknownAmountsAreHandled() {
        val index = DuplicateCandidates(listOf(record("manual")))
        assertEquals(1, index.find(record("novo").copy(occurredAt = time + DuplicateCandidates.WINDOW_MILLIS)).size)
        assertTrue(index.find(record("novo").copy(occurredAt = time + DuplicateCandidates.WINDOW_MILLIS + 1)).isEmpty())
        assertTrue(index.find(record("novo").copy(amountCents = 0)).isEmpty())
        assertTrue(index.find(record("novo").copy(movement = Movement.UNKNOWN)).isEmpty())
    }
    @Test fun limitedResultsUseStableIdentifiersAndIgnoreTheRecordBeingEdited() {
        val records = listOf(record("c"), record("b"), record("a"))
        assertEquals(listOf("a", "b"), DuplicateCandidates(records).find(record("novo"), 2).map { it.record.id })
        assertEquals(2, DuplicateCandidates(records).find(record("a")).size)
    }
}

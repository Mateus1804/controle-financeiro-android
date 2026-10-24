package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class RecordValidationTest {
    private val valid = Record("id", 100, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD, "Banco", "Compra", 1)
    @Test fun `aceita registro revisado`() { assertNull(RecordValidation.error(valid)) }
    @Test fun `recusa zero negativos e estouro`() {
        listOf(0L, -1L, Money.MAX_CENTS + 1).forEach { assertNotNull(RecordValidation.error(valid.copy(amountCents = it))) }
    }
    @Test fun `nao permite confirmar tipo indefinido`() { assertNotNull(RecordValidation.error(valid.copy(movement = Movement.UNKNOWN))) }
    @Test fun `transferencia exige destino diferente da origem`() {
        assertNotNull(RecordValidation.error(valid.copy(movement = Movement.TRANSFER)))
        assertNotNull(RecordValidation.error(valid.copy(movement = Movement.TRANSFER, destination = " banco ")))
        assertNull(RecordValidation.error(valid.copy(movement = Movement.TRANSFER, destination = "Carteira")))
        assertNotNull(RecordValidation.error(valid.copy(movement = Movement.TRANSFER, bank = "Inter pessoal", destination = " INTER  pessoal ")))
        assertNull(RecordValidation.error(valid.copy(movement = Movement.TRANSFER, bank = "Inter pessoal", destination = "Inter trabalho")))
    }
    @Test fun `campos obrigatorios e limites sao validados`() {
        assertNotNull(RecordValidation.error(valid.copy(bank = " ")))
        assertNotNull(RecordValidation.error(valid.copy(description = "x".repeat(201))))
        assertNotNull(RecordValidation.error(valid.copy(occurredAt = 0)))
    }
}

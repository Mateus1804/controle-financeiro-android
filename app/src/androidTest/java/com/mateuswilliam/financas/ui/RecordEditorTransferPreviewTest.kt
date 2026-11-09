package com.mateuswilliam.financas.ui

import com.mateuswilliam.financas.core.*
import org.junit.Assert.*
import org.junit.Test

class RecordEditorTransferPreviewTest {
    private val reference = AccountBalanceReference(1, "origem", 10000, 1, 1, "Inicial")
    private val old = Record("transfer", 2000, Movement.TRANSFER, PaymentMethod.PIX,
        Category.OTHER, "Origem", "Teste", 10, "Destino")

    @Test fun editingTransferRemovesOriginalBeforeApplyingReplacement() {
        val source = AccountBalance(AccountOption("origem", "Origem", 0), false, 8000, reference)
        val destination = AccountBalance(AccountOption("destino", "Destino", 0), false, 7000, reference.copy(accountKey = "destino"))
        val edited = old.copy(amountCents = 3000)
        assertEquals(7000L, transferPreviewBalance(source, edited, old, 20))
        assertEquals(8000L, transferPreviewBalance(destination, edited, old, 20))
        assertEquals(15000L, transferPreviewBalance(source, edited, old, 20)!! + transferPreviewBalance(destination, edited, old, 20)!!)
    }

    @Test fun changingSourceRestoresPreviousAccountAndDebitsNewOne() {
        val previousSource = AccountBalance(AccountOption("origem", "Origem", 0), false, 8000, reference)
        val nextSource = AccountBalance(AccountOption("nova", "Nova", 0), false, 6000, reference.copy(accountKey = "nova"))
        val edited = old.copy(bank = "Nova", amountCents = 3000)
        assertEquals(10000L, transferPreviewBalance(previousSource, edited, old, 20))
        assertEquals(3000L, transferPreviewBalance(nextSource, edited, old, 20))
    }

    @Test fun convertingExpenseToTransferRestoresItsAccountBeforeMovingMoney() {
        val expense = old.copy(movement = Movement.EXPENSE, amountCents = 10000, destination = "")
        val edited = old.copy(bank = "Nova", amountCents = 20000)
        fun balance(key: String, cents: Long) = AccountBalance(AccountOption(key, key, 0), false, cents, reference.copy(accountKey = key))
        assertEquals(50000L, transferPreviewBalance(balance("origem", 40000), edited, expense, 20))
        assertEquals(30000L, transferPreviewBalance(balance("nova", 50000), edited, expense, 20))
        assertEquals(70000L, transferPreviewBalance(balance("destino", 50000), edited, expense, 20))
    }

    @Test fun unknownBalancesStayUnknown() {
        val unknown = AccountBalance(AccountOption("origem", "Origem", 0), false, null, null)
        assertNull(transferPreviewBalance(unknown, old, null, 20))
    }

    @Test fun transferIncludedInReferenceIsNotDeductedTwice() {
        val source = AccountBalance(AccountOption("origem", "Origem", 0), false, 8000, reference.copy(effectiveAt = 15))
        assertEquals(8000L, transferPreviewBalance(source, old.copy(amountCents = 3000), old, 20))
    }
}

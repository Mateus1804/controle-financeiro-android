package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReserveAccountScreenTest {
    @get:Rule val rule = createComposeRule()
    private val day = LocalDate.of(2025, 11, 5)
    private val period = BudgetPeriod(day, day.plusMonths(1))
    private val at = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val reserve = "Reserva de emergência"
    private fun transfer(id: String, value: Long, source: String, destination: String) = Record(
        id, value, Movement.TRANSFER, PaymentMethod.PIX, Category.OTHER, source, id, at, destination)

    @Test fun reserveUsesAccountBalanceAndTransfersWithoutLegacyDoubleCounting() {
        val balances = listOf(AccountBalance(AccountOption(AccountNames.key(reserve), reserve, at), false, 100000, null))
        val legacy = ForecastDataset(reserveEvents = listOf(ReserveEvent(1, ReserveEventKind.SET, 900000, at, "Antigo")))
        rule.setContent { FinanceTheme {
            ReserveAccountScreen(legacy, balances,
                listOf(transfer("Aporte", 120000, "Conta principal", reserve), transfer("Resgate", 20000, reserve, "Conta principal")),
                period, false, false, {}, {}, {}, {})
        } }
        rule.onNodeWithTag("reserve-balance").assertTextContains(Money.format(100000), substring = true)
        rule.onNodeWithTag("forecast-reserve").performScrollToNode(hasTestTag("reserve-period-in"))
        rule.onNodeWithTag("reserve-period-in").assertTextContains(Money.format(120000), substring = true)
        rule.onNodeWithTag("reserve-period-out").assertTextContains(Money.format(20000), substring = true)
        rule.onAllNodesWithText(Money.format(900000), substring = true).assertCountEquals(0)
    }

    @Test fun legacyValueDoesNotPretendToBeAccountBalance() {
        val data = ForecastDataset(reserveEvents = listOf(ReserveEvent(1, ReserveEventKind.SET, 900000, at, "Antigo")))
        rule.setContent { FinanceTheme { ReserveAccountScreen(data, emptyList(), emptyList(), period, false, false, {}, {}, {}, {}) } }
        rule.onNodeWithText("Informe o saldo inicial da conta Reserva, inclusive se for zero.").assertIsDisplayed()
        rule.onNodeWithTag("reserve-add").assertIsNotEnabled()
        rule.onNodeWithTag("reserve-withdraw").assertIsNotEnabled()
        rule.onAllNodesWithTag("reserve-balance").assertCountEquals(0)
    }

    @Test fun hiddenReserveDoesNotExposeBalance() {
        val balances = listOf(AccountBalance(AccountOption(AccountNames.key(reserve), reserve, at), false, 123456, null))
        rule.setContent { FinanceTheme { ReserveAccountScreen(ForecastDataset(), balances, emptyList(), period, true, false, {}, {}, {}, {}) } }
        rule.onNodeWithTag("reserve-balance").assertTextContains("••••", substring = true)
        rule.onAllNodesWithText(Money.format(123456), substring = true).assertCountEquals(0)
    }
    @Test fun typingAnExistingAccountRequiresACorrectionReason() {
        val reference = AccountBalanceReference(1, "principal", 100000, at, at, "Inicial")
        val balance = AccountBalance(AccountOption("principal", "Principal", at), false, 100000, reference)
        var saved = false
        rule.setContent { FinanceTheme {
            AccountBalanceEditor(null, emptyList(), false, false, {}, { _, _, _, _ -> saved = true },
                knownBalances = listOf(balance), allReferences = listOf(reference))
        } }
        rule.onNodeWithTag("balance-account-name").performTextInput("Principal")
        rule.onNodeWithText("Corrigir saldo").assertExists()
        rule.onNodeWithTag("account-balance-input").performScrollTo().performTextInput("500,00")
        rule.onNodeWithTag("save-account-balance").performClick()
        org.junit.Assert.assertFalse(saved)
        rule.onNodeWithTag("balance-reason").performScrollTo().performTextInput("Conferência do extrato")
        rule.onNodeWithTag("save-account-balance").performClick()
        org.junit.Assert.assertTrue(saved)
    }

}

package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReserveTransfersDataTest {
    @Test fun withdrawalsRespectBalanceAtTheirDateAndEditingDoesNotCountTheOldWithdrawalTwice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db, CapturePreferences(context))
            val start = LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val reserve = AccountFunds.RESERVE_NAME
            repo.configureAccountBalance("Principal", 0, start, "Inicial")
            repo.configureAccountBalance(reserve, 100000, start, "Inicial")
            val withdrawal = Record("resgate", 60000, Movement.TRANSFER, PaymentMethod.PIX,
                Category.OTHER, reserve, "Resgate", start + 100, "Principal")
            assertTrue(repo.save(withdrawal, null))
            assertTrue(repo.save(withdrawal.copy(amountCents = 80000), null, editing = true))
            assertTrue(runCatching { repo.save(withdrawal.copy(amountCents = 100001), null, editing = true) }.isFailure)
            assertEquals(80000L, repo.exportRecords().single().amountCents)
            assertTrue(runCatching { repo.save(withdrawal.copy(id = "excesso", amountCents = 20001, occurredAt = start + 200), null) }.isFailure)
            val balances = repo.observeAccountBalances(start + 300).first()
            assertEquals(20000L, balances.single { it.account.key == AccountFunds.RESERVE_KEY }.balanceCents)
            assertEquals(100000L, balances.sumOf { it.balanceCents ?: 0 })
            assertTrue(runCatching { repo.configureAccountBalance(reserve, -1, start, "Inválido") }.isFailure)
        } finally { db.close() }
    }
}

package com.mateuswilliam.financas

import com.mateuswilliam.financas.core.AccountOption
import com.mateuswilliam.financas.data.AccountLedger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceStateObservationTest {
    private enum class Source { LEDGER, ACCOUNTS }

    @Test fun refreshRecoversFromLedgerReadFailure() = verifyRefresh(Source.LEDGER)
    @Test fun refreshRecoversFromAccountReadFailure() = verifyRefresh(Source.ACCOUNTS)

    @Test fun refreshRetriesRepositoryInitialization() = runTest {
        val refreshes = MutableStateFlow(0L)
        var attempts = 0
        val sources by lazy {
            if (++attempts == 1) error("Falha de abertura simulada")
            FinanceStateSources(
                flowOf(AccountLedger(42, emptyList(), emptyList(), emptyList(), emptyList())),
                flowOf(emptyList()),
            )
        }
        val state = observeFinanceState(refreshes) { sources }
            .stateIn(backgroundScope, SharingStarted.Eagerly, FinanceState())

        assertEquals(0, attempts)
        runCurrent()
        assertEquals(1, attempts)
        assertFalse(state.value.loaded)
        assertNotNull(state.value.loadError)

        refreshes.value++
        runCurrent()
        assertEquals(2, attempts)
        assertTrue(state.value.loaded)
        assertNull(state.value.loadError)
    }

    private fun verifyRefresh(failedSource: Source) = runTest {
        val refreshes = MutableStateFlow(0L)
        val failRead = CompletableDeferred<Unit>()
        val subscriptions = mutableMapOf<Source, Int>()
        fun <T> observed(source: Source, value: T) = flow {
            val attempt = subscriptions.getOrDefault(source, 0) + 1
            subscriptions[source] = attempt
            if (source == failedSource && attempt == 1) {
                failRead.await()
                error("Falha de leitura simulada")
            }
            emit(value)
            awaitCancellation()
        }
        val snapshot = AccountLedger(42, emptyList(), emptyList(), emptyList(), emptyList())
        val accounts = listOf(AccountOption("carteira", "Carteira", 42))
        val state = observeFinanceState(refreshes) {
            FinanceStateSources(
                observed(Source.LEDGER, snapshot),
                observed(Source.ACCOUNTS, accounts),
            )
        }.stateIn(backgroundScope, SharingStarted.Eagerly, FinanceState())

        runCurrent()
        assertEquals(Source.entries.associateWith { 1 }, subscriptions)
        failRead.complete(Unit)
        runCurrent()
        assertFalse(state.value.loaded)
        assertNotNull(state.value.loadError)

        refreshes.value++
        runCurrent()
        assertEquals(Source.entries.associateWith { 2 }, subscriptions)
        assertTrue(state.value.loaded)
        assertNull(state.value.loadError)
        assertEquals(snapshot.asOf, state.value.asOf)
        assertEquals(accounts, state.value.accounts)
    }
}

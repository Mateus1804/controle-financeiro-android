package com.mateuswilliam.financas

import com.mateuswilliam.financas.data.AccountLedger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val snapshot = AccountLedger(42, emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun refreshRetriesRepositoryInitialization() = runTest {
        val refreshes = MutableStateFlow(0L)
        var attempts = 0
        val ledger by lazy {
            if (++attempts == 1) error("Falha de abertura simulada")
            flowOf(snapshot)
        }
        val state = observeFinanceState(refreshes) { ledger }
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

    @Test fun refreshRecoversFromLedgerReadFailure() = runTest {
        val refreshes = MutableStateFlow(0L)
        val failRead = CompletableDeferred<Unit>()
        var subscriptions = 0
        val ledger = flow {
            if (++subscriptions == 1) {
                failRead.await()
                error("Falha de leitura simulada")
            }
            emit(snapshot)
        }
        val state = observeFinanceState(refreshes) { ledger }
            .stateIn(backgroundScope, SharingStarted.Eagerly, FinanceState())

        runCurrent()
        assertEquals(1, subscriptions)
        failRead.complete(Unit)
        runCurrent()
        assertFalse(state.value.loaded)
        assertNotNull(state.value.loadError)

        refreshes.value++
        runCurrent()
        assertEquals(2, subscriptions)
        assertTrue(state.value.loaded)
        assertNull(state.value.loadError)
        assertEquals(snapshot.asOf, state.value.asOf)
    }
}

package com.mateuswilliam.financas.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private val input = NotificationInput("banco.exemplo", "evento:1", "Banco fictício", "Compra aprovada", "Compra aprovada de R$ 42,90 no supermercado", 1_792_800_000_000)

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context)
        preferences.reset()
        repository = FinanceRepository(database, preferences)
    }
    @After fun close() { preferences.reset(); database.close() }
    private fun authorize() { preferences.acceptDisclosure(); preferences.selectPackages(setOf(input.packageName)) }
    private fun record(id: String = "registro-1") = Record(id, 4290, Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, input.bank, "Compra fictícia", input.occurredAt)

    @Test fun captureRequiresConsentEnableAndSelection() = runBlocking {
        assertNull(repository.capture(input))
        preferences.selectPackages(setOf(input.packageName))
        assertNull(repository.capture(input))
        preferences.acceptDisclosure()
        assertNotNull(repository.capture(input))
        preferences.setEnabled(false)
        assertNull(repository.capture(input.copy(sourceKey = "evento:2")))
        assertEquals(1, repository.pending.first().size)
    }

    @Test fun otherAppsAndAuthenticationAreNotStored() = runBlocking {
        authorize()
        assertNull(repository.capture(input.copy(packageName = "outro.aplicativo")))
        assertNull(repository.capture(input.copy(title = "Código de autenticação", body = "123456")))
        assertTrue(repository.pending.first().isEmpty())
    }

    @Test fun notificationUpdateChangesPendingWithoutDuplicating() = runBlocking {
        authorize()
        val first = repository.capture(input)!!
        val update = repository.capture(input.copy(body = "Compra aprovada de R$ 48,90 no supermercado"))!!
        assertEquals(first.id, update.id)
        assertFalse(update.isNew)
        assertEquals(1, repository.pending.first().size)
        assertEquals(4890L, repository.pending.first().single().amountCents)
    }

    @Test fun distinctEventsWithSameAmountArePreserved() = runBlocking {
        authorize()
        repository.capture(input)
        repository.capture(input.copy(sourceKey = "evento:2"))
        assertEquals(2, repository.pending.first().size)
    }

    @Test fun concurrentConfirmationCreatesExactlyOneRecord() = runBlocking {
        authorize()
        val draft = repository.capture(input)!!
        val results = coroutineScope {
            listOf(async(Dispatchers.Default) { repository.save(record("a"), draft.id) },
                async(Dispatchers.Default) { repository.save(record("b"), draft.id) }).map { it.await() }
        }
        assertEquals(1, results.count { it })
        assertEquals(1, repository.records.first().size)
        assertTrue(repository.pending.first().isEmpty())
        assertEquals("", database.financeDao().draftById(draft.id)?.sourceBody)
        assertNull(repository.capture(input))
    }

    @Test fun ignoringClearsOriginalTextAndDoesNotCreateRecord() = runBlocking {
        authorize()
        val draft = repository.capture(input)!!
        repository.dismiss(draft.id)
        assertTrue(repository.records.first().isEmpty())
        assertEquals("", database.financeDao().draftById(draft.id)?.sourceBody)
        assertNull(repository.capture(input))
    }

    @Test fun invalidRecordDoesNotConsumeDraft() = runBlocking {
        authorize()
        val draft = repository.capture(input)!!
        val result = runCatching { repository.save(record().copy(amountCents = 0), draft.id) }
        assertTrue(result.isFailure)
        assertEquals(1, repository.pending.first().size)
        assertTrue(repository.records.first().isEmpty())
    }

    @Test fun editingPreservesSourceAndUpdatesInsteadOfInserting() = runBlocking {
        authorize()
        val draft = repository.capture(input)!!
        repository.save(record(), draft.id)
        assertTrue(repository.save(record().copy(amountCents = 5000), null, editing = true))
        assertEquals(1, repository.records.first().size)
        assertEquals(5000L, repository.records.first().single().amountCents)
        assertEquals(draft.id, database.financeDao().recordById("registro-1")?.sourceDraftId)
    }

    @Test fun clearingDataAlsoRevokesLocalConsent() = runBlocking {
        authorize()
        repository.capture(input)
        repository.save(record(), null)
        repository.clear()
        assertTrue(repository.pending.first().isEmpty())
        assertTrue(repository.records.first().isEmpty())
        assertFalse(preferences.state.value.consented)
        assertNull(repository.capture(input))
    }

    @Test fun changedNotificationCannotBeConfirmedFromOldForm() = runBlocking {
        authorize()
        val capture = repository.capture(input)!!
        val opened = database.financeDao().draftById(capture.id)!!
        repository.capture(input.copy(body = "Compra aprovada de R$ 50,00 no supermercado"))
        val failed = runCatching { repository.save(record(), capture.id, expectedRevision = opened.revision) }
        assertTrue(failed.exceptionOrNull() is DraftChangedException)
        assertTrue(repository.records.first().isEmpty())
        assertEquals(5000L, repository.pending.first().single().amountCents)
    }

    @Test fun accountNamesArePersistentAndOnlyEquivalentNamesAreDeduplicated() = runBlocking {
        assertEquals("Inter pessoal", repository.addAccount(" Inter  pessoal "))
        assertEquals("Inter pessoal", repository.addAccount("INTER pessoal"))
        repository.addAccount("Inter trabalho")
        assertEquals(2, database.financeDao().allAccounts().size)
        val reopened = FinanceRepository(database, preferences)
        assertEquals(setOf("Inter pessoal", "Inter trabalho"), reopened.accounts.first().map { it.label }.toSet())
    }

    @Test fun legacyNamesAreAvailableWithoutRewritingHistory() = runBlocking {
        val legacy = RecordEntity.from(record().copy(bank = "Banco   antigo"), null)
        database.financeDao().insertRecord(legacy)
        assertEquals("Banco antigo", repository.accounts.first().single().label)
        assertEquals("Banco   antigo", database.financeDao().allRecords().single().bank)
    }

    @Test fun originDestinationAndNotificationIdentityRemainIndependent() = runBlocking {
        repository.save(record("transferencia").copy(movement = Movement.TRANSFER, bank = "Origem", destination = "Destino"), null)
        assertEquals("Origem", preferences.state.value.lastOrigin)
        assertEquals(2, database.financeDao().allAccounts().size)
        authorize()
        val draft = repository.capture(input)!!
        repository.save(record("notificacao").copy(bank = "Conta escolhida"), draft.id)
        assertEquals("Origem", CapturePreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext()).state.value.lastOrigin)
        assertEquals(input.packageName, database.financeDao().draftById(draft.id)!!.sourcePackage)
        assertEquals(input.bank, database.financeDao().draftById(draft.id)!!.bank)
        assertEquals("Conta escolhida", repository.exportRecords().first { it.id == "notificacao" }.bank)
        repository.clear()
        assertTrue(database.financeDao().allAccounts().isEmpty())
    }
}

package com.mateuswilliam.financas.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.discardLegacyDatabase
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate

class BackupStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private lateinit var store: BackupStore
    private fun password() = "Backup fictício 2026!".toCharArray()
    private fun record(id: String) = Record(id, 4290, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD, "Inter pessoal", "Mercado fictício", 1_792_800_000_000L)
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
        store = BackupStore(context, repository)
    }
    @After fun cleanup() = runBlocking { store.removeAbandonedStages(); preferences.reset(); database.close() }

    @Test fun roundTripRestoresEveryFinancialTableAndPausesDeviceFeatures() = runBlocking<Unit> {
        repository.save(record("manual"), null)
        val input = NotificationInput("fixture.bank", "fixture:1", "Banco fictício", "Compra aprovada", "Compra aprovada de R$ 42,90", 1_792_800_000_000L)
        val draft = repository.capture(input, simulation = true)!!
        repository.save(record("notificacao"), draft.id)
        repository.capture(input.copy(sourceKey = "fixture:2"), simulation = true)
        ForecastRepository(database).saveRule(ForecastRule("previsao", java.time.LocalDate.parse("2026-10-01"), "Aluguel fictício", ForecastKind.EXPENSE,
            60000, Category.HOME, ForecastFrequency.MONTHLY, java.time.LocalDate.parse("2026-10-31")))
        preferences.setAppearance(Appearance.DARK)
        val expected = repository.snapshot()
        val secret = password()
        val file = store.create(secret)
        assertTrue(secret.all { it == '\u0000' })
        repository.clear()
        repository.save(record("local-antes-da-restauracao"), null)
        preferences.acceptDisclosure(); preferences.selectPackages(setOf("fixture.bank")); preferences.setReminders(true); preferences.setLockEnabled(true)
        val preview = store.stage(ByteArrayInputStream(file), password())
        assertEquals("local-antes-da-restauracao", repository.exportRecords().single().id)
        assertEquals(2, preview.records); assertEquals(1, preview.pending)
        store.restore(preview)
        val restored = repository.snapshot()
        assertEquals(expected.records.toSet(), restored.records.toSet())
        assertEquals(expected.drafts.toSet(), restored.drafts.toSet())
        assertEquals(expected.accounts.toSet(), restored.accounts.toSet())
        assertEquals(expected.forecastRules, restored.forecastRules)
        assertEquals(Appearance.DARK, preferences.snapshot().appearance)
        assertFalse(preferences.snapshot().enabled); assertFalse(preferences.snapshot().remindersEnabled)
        assertTrue(preferences.snapshot().consented); assertTrue(preferences.snapshot().lockEnabled)
        assertEquals("fixture.bank", restored.drafts.first().sourcePackage)
        assertFalse(context.getDatabasePath(preview.databaseName).exists())
    }
    @Test fun wrongPasswordPreservesLocalDataAndClearsTheProvidedPassword() = runBlocking<Unit> {
        repository.save(record("preservado"), null)
        val file = store.create(password())
        val secret = "Senha incorreta fictícia".toCharArray()
        assertTrue(runCatching { store.stage(ByteArrayInputStream(file), secret) }.exceptionOrNull() is BackupAuthenticationException)
        assertEquals("preservado", repository.exportRecords().single().id)
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test fun oldNonInstallmentCountsDoNotPreventRestoringOrCreatingBackups() = runBlocking {
        val start = LocalDate.of(2026, 10, 1)
        val rule = ForecastRule("monthly", start, "Aluguel fictício", ForecastKind.EXPENSE, 60000,
            Category.HOME, ForecastFrequency.MONTHLY, start)
        // The old editor could store this unused value before the current validation was added.
        database.financeDao().upsertForecastRule(ForecastRuleEntity.from(rule).copy(installmentCount = 0))
        val encoded = BackupSnapshot.encode(repository.snapshot())
        assertEquals(1, BackupSnapshot.decode(encoded).forecastRules.single().installmentCount)

        val payload = JSONObject(encoded.toString(Charsets.UTF_8))
        val row = payload.getJSONArray("forecastRules").getJSONObject(0)
        row.put("installmentCount", 601)
        val fromOldApp = BackupCrypto.encrypt(payload.toString().toByteArray(Charsets.UTF_8), password())
        store.restore(store.stage(ByteArrayInputStream(fromOldApp), password()))
        assertEquals(rule, repository.snapshot().forecastRules.single().model())

        row.put("frequency", ForecastFrequency.INSTALLMENTS.name)
        assertTrue(runCatching { BackupSnapshot.decode(payload.toString().toByteArray(Charsets.UTF_8)) }.isFailure)
    }
    @Test fun tamperedBackupIsRejectedBeforeReplacingAnyData() = runBlocking<Unit> {
        repository.save(record("preservado"), null)
        val file = store.create(password()).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertTrue(runCatching { store.stage(ByteArrayInputStream(file), password()) }.isFailure)
        assertEquals("preservado", repository.exportRecords().single().id)
    }
    @Test fun decimalMoneyAndUnsupportedPayloadVersionsAreRejected() = runBlocking<Unit> {
        repository.save(record("preservado"), null)
        val json = JSONObject(BackupSnapshot.encode(repository.snapshot()).toString(Charsets.UTF_8))
        json.getJSONArray("records").getJSONObject(0).put("amountCents", 42.9)
        val file = BackupCrypto.encrypt(json.toString().toByteArray(), password())
        assertTrue(runCatching { store.stage(ByteArrayInputStream(file), password()) }.isFailure)
        json.put("version", 999)
        assertTrue(runCatching { BackupSnapshot.decode(json.toString().toByteArray()) }.isFailure)
        assertEquals(4290L, repository.exportRecords().single().amountCents)
    }
    @Test fun cancellingPreviewDeletesTemporaryDatabaseAndPreservesCurrentRecords() = runBlocking<Unit> {
        repository.save(record("preservado"), null)
        val preview = store.stage(ByteArrayInputStream(store.create(password())), password())
        assertTrue(context.getDatabasePath(preview.databaseName).exists())
        store.discard(preview)
        assertFalse(context.getDatabasePath(preview.databaseName).exists())
        assertEquals("preservado", repository.exportRecords().single().id)
    }
    @Test fun oversizedInputIsBoundedAndClosedWithoutTouchingTheLiveDatabase() = runBlocking<Unit> {
        repository.save(record("preservado"), null)
        var delivered = 0
        var closed = false
        val input = object : java.io.InputStream() {
            override fun read(): Int = error("A leitura utiliza blocos limitados")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill(0, offset, offset + length); delivered += length; return length
            }
            override fun close() { closed = true }
        }
        val secret = password()
        val failure = runCatching { store.stage(input, secret) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(delivered <= BackupCrypto.MAX_FILE_BYTES + 8192)
        assertTrue(closed); assertTrue(secret.all { it == '\u0000' })
        assertEquals("preservado", repository.exportRecords().single().id)
    }
    @Test fun failedLiveTransactionRollsBackDeletedRowsAndRestoresCaptureState() = runBlocking<Unit> {
        repository.save(record("novo-backup"), null)
        val snapshot = repository.snapshot()
        repository.clear()
        repository.save(record("base-original"), null)
        preferences.acceptDisclosure()
        preferences.setReminders(true)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER falha_restauracao BEFORE INSERT ON records WHEN NEW.id = 'novo-backup' BEGIN SELECT RAISE(ABORT, 'falha fictícia'); END")
        assertTrue(runCatching { repository.restore(snapshot) }.isFailure)
        assertEquals("base-original", repository.exportRecords().single().id)
        assertTrue(preferences.snapshot().enabled)
        assertTrue(preferences.snapshot().remindersEnabled)
    }
    @Test fun failedPausePersistencePreventsRestoringOrClearingTheLiveDatabase() = runBlocking<Unit> {
        repository.save(record("backup-novo"), null)
        val snapshot = repository.snapshot()
        repository.clear()
        repository.save(record("base-preservada"), null)
        val isolated = context.getSharedPreferences("failing-restore-test", Context.MODE_PRIVATE)
        try {
            check(isolated.edit().clear().putBoolean("enabled", true).putBoolean("disclosure_v1", true).commit())
            val failing = FinanceRepository(database, CapturePreferences(FailingPreferencesContext(context, isolated)))
            assertTrue(runCatching { failing.restore(snapshot) }.exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { failing.clear() }.exceptionOrNull() is IllegalStateException)
            assertEquals("base-preservada", repository.exportRecords().single().id)
        } finally { isolated.edit().clear().commit() }
    }
    @Test fun failedDeletionKeepsExistingFinancialDataAndAppProtection() = runBlocking<Unit> {
        repository.save(record("base-protegida"), null)
        preferences.setLockEnabled(true)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER falha_exclusao BEFORE DELETE ON records BEGIN SELECT RAISE(ABORT, 'falha fictícia'); END")
        assertTrue(runCatching { repository.clear() }.isFailure)
        assertEquals("base-protegida", repository.exportRecords().single().id)
        assertTrue(preferences.snapshot().lockEnabled)
        assertTrue(preferences.snapshot().secureScreen)
        assertFalse(preferences.snapshot().enabled)
    }

    @Test fun legacyDatabaseRemainsAvailableWhenPersistingThePauseFails() {
        val file = File.createTempFile("legacy-test", ".db", context.cacheDir)
        val isolated = context.getSharedPreferences("failing-legacy-pause-test", Context.MODE_PRIVATE)
        val legacyContext = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String) = file
            override fun deleteDatabase(name: String): Boolean {
                assertFalse(isolated.getBoolean("enabled", true))
                assertFalse(isolated.getBoolean("reminders_enabled", true))
                return file.delete()
            }
        }
        try {
            check(isolated.edit().putBoolean("enabled", true).putBoolean("reminders_enabled", true).commit())
            val failing = CapturePreferences(FailingPreferencesContext(context, isolated))
            assertTrue(runCatching { discardLegacyDatabase(legacyContext, failing) }.exceptionOrNull() is IllegalStateException)
            assertTrue(file.exists())
            val retry = CapturePreferences(FailingPreferencesContext(context, isolated, successfulCommits = 1))
            assertTrue(discardLegacyDatabase(legacyContext, retry))
            assertFalse(file.exists())
        } finally {
            file.delete()
            isolated.edit().clear().commit()
        }
    }
    @Test fun preferenceCleanupFailureIsReportedAfterFinancialDataWasRemoved() = runBlocking<Unit> {
        repository.save(record("a-apagar"), null)
        val isolated = context.getSharedPreferences("failing-clear-test", Context.MODE_PRIVATE)
        try {
            check(isolated.edit().clear().putBoolean("enabled", true).putBoolean("lock_enabled", true).commit())
            val failingSettings = CapturePreferences(FailingPreferencesContext(context, isolated, successfulCommits = 1))
            val failing = FinanceRepository(database, failingSettings)
            assertFalse(failing.clear())
            assertTrue(repository.exportRecords().isEmpty())
            assertFalse(failingSettings.snapshot().enabled)
            assertFalse(failingSettings.snapshot().remindersEnabled)
            assertTrue(failingSettings.snapshot().lockEnabled)
        } finally { isolated.edit().clear().commit() }
    }
    @Test fun duplicateIdentifiersAndDanglingLinksCannotEnterRestoredDatabase() = runBlocking<Unit> {
        repository.save(record("preservado"), null)
        val snapshot = repository.snapshot()
        assertTrue(runCatching { repository.restore(snapshot.copy(records = snapshot.records + snapshot.records)) }.isFailure)
        assertTrue(runCatching { repository.restore(snapshot.copy(records = listOf(snapshot.records.single().copy(sourceDraftId = "ausente")))) }.isFailure)
        assertEquals("preservado", repository.exportRecords().single().id)
    }
    @Test fun androidRejectsThePreviousEnvelope() {
        val vector = java.util.Base64.getDecoder().decode("Q0ZBQktQDQoAAAABAAknwAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhuOMJiLVRW1KPociHMYM7y2gIARYBCdokhkFKdsxh6rPg7ZN3WLfT3eeROPPq7CISZ+")
        assertTrue(runCatching { BackupCrypto.decrypt(vector, "Senha fictícia de teste 2026!".toCharArray()) }.exceptionOrNull() is IllegalArgumentException)
    }

}

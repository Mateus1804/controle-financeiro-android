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
    private fun notification() = NotificationInput("fixture.bank", "fixture:tag|ç", "Banco fictício",
        "Compra aprovada", "Compra aprovada de R$ 42,90\nPão e café", 1_792_800_000_000L)
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
        store = BackupStore(context, repository)
    }
    @After fun cleanup() = runBlocking { BackupStore(context, repository).removeAbandonedStages(); preferences.reset(); database.close() }

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
        assertTrue(store.restore(preview))
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

    @Test fun capturedDraftTextWithNullCharactersRoundTrips() = runBlocking<Unit> {
        val input = notification().copy(bank = "Banco fi\u0000ctício", title = "Compra apro\u0000vada",
            body = "Compra aprovada de R$ 42,\u000090\nPão e café")
        val captured = repository.capture(input, simulation = true)!!
        val draft = repository.snapshot().drafts.single()
        assertEquals("Banco fictício", draft.bank)
        assertEquals("Compra aprovada", draft.sourceTitle)
        assertEquals("Compra aprovada de R$ 42,90\nPão e café", draft.sourceBody)
        assertEquals(4290L, draft.amountCents)
        assertEquals("fixture:tag|ç", draft.sourceKey)
        assertEquals(captured.id, repository.capture(notification(), simulation = true)?.id)
        assertEquals(draft.revision, repository.snapshot().drafts.single().revision)

        val file = store.create(password())
        repository.clear()
        assertTrue(store.restore(store.stage(ByteArrayInputStream(file), password())))
        assertEquals(draft, repository.snapshot().drafts.single())
    }

    @Test fun previousRendlyBackupsWithNullInDraftTextAreStillRestorable() = runBlocking<Unit> {
        val input = notification()
        repository.capture(input, simulation = true)
        val snapshot = repository.snapshot()
        val oldDraft = snapshot.drafts.single().copy(bank = "Banco fi\u0000ctício", sourceTitle = "Compra apro\u0000vada",
            sourceBody = "Compra aprovada de R$ 42,90\nPão\u0000 e café", explanation = "Confira\u0000 o valor.")
        val payload = JSONObject(BackupSnapshot.encode(snapshot.copy(drafts = listOf(oldDraft))).toString(Charsets.UTF_8))
        val row = payload.getJSONArray("drafts").getJSONObject(0)
        assertEquals("Banco fictício", row.getString("bank"))
        assertEquals("Compra aprovada", row.getString("sourceTitle"))
        assertEquals("Compra aprovada de R$ 42,90\nPão e café", row.getString("sourceBody"))
        assertEquals("Confira o valor.", row.getString("explanation"))

        row.put("bank", oldDraft.bank).put("sourceTitle", oldDraft.sourceTitle)
            .put("sourceBody", oldDraft.sourceBody).put("explanation", oldDraft.explanation)
        val oldFile = BackupCrypto.encrypt(payload.toString().toByteArray(Charsets.UTF_8), password())
        repository.clear()
        assertTrue(store.restore(store.stage(ByteArrayInputStream(oldFile), password())))
        assertEquals(snapshot.drafts.single().copy(explanation = "Confira o valor."), repository.snapshot().drafts.single())
    }

    @Test fun nullCharactersInOpaqueDraftFieldsAreRejected() = runBlocking<Unit> {
        repository.capture(notification(), simulation = true)
        val payload = JSONObject(BackupSnapshot.encode(repository.snapshot()).toString(Charsets.UTF_8))
        val row = payload.getJSONArray("drafts").getJSONObject(0)
        for (field in listOf("id", "sourceKey", "sourcePackage")) {
            val value = row.getString(field)
            row.put(field, "$value\u0000-outro")
            assertTrue(runCatching { BackupSnapshot.decode(payload.toString().toByteArray(Charsets.UTF_8)) }
                .exceptionOrNull() is IllegalArgumentException)
            row.put(field, value)
        }
        assertNull(repository.capture(notification().copy(sourceKey = "fixture:tag\u0000|ç"), simulation = true))
        assertEquals(1, repository.snapshot().drafts.size)
    }

    @Test fun sensitiveTextIsFilteredBeforeAndAfterRemovingNullCharacters() = runBlocking<Unit> {
        for (body in listOf("A senha\u0000expirou", "A se\u0000nha expirou")) {
            assertNull(repository.capture(notification().copy(body = body), simulation = true))
        }
        assertTrue(repository.snapshot().drafts.isEmpty())
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

    @Test fun failedStageDeletionWarnsAfterRestoreAndIsRetriedAtTheNextBackup() = runBlocking<Unit> {
        val deletion = object : ContextWrapper(context) {
            var failDeletion = false
            override fun deleteDatabase(name: String): Boolean = !failDeletion && super.deleteDatabase(name)
        }
        store = BackupStore(deletion, repository)
        repository.save(record("restaurado"), null)
        val file = store.create(password())
        repository.clear()
        repository.save(record("substituído"), null)
        val preview = store.stage(ByteArrayInputStream(file), password())

        deletion.failDeletion = true
        assertTrue(store.restore(preview))
        assertEquals("restaurado", repository.exportRecords().single().id)
        assertTrue(context.getDatabasePath(preview.databaseName).exists())
        assertTrue(store.cleanupPending.value)
        assertTrue(runCatching { store.restore(preview) }.exceptionOrNull() is IllegalArgumentException)

        deletion.failDeletion = false
        store.create(password())
        assertFalse(context.getDatabasePath(preview.databaseName).exists())
        assertFalse(store.cleanupPending.value)
    }

    @Test fun cleanupChecksForLeftoverSidecarFilesWithoutRemovingActivePreviews() = runBlocking<Unit> {
        val abandonedName = "restore-stage-00000000-0000-0000-0000-000000000000.db"
        val deletion = object : ContextWrapper(context) {
            var leaveSidecar = true
            override fun deleteDatabase(name: String): Boolean =
                if (leaveSidecar && name == abandonedName) true else super.deleteDatabase(name)
        }
        store = BackupStore(deletion, repository)
        repository.save(record("preservado"), null)
        val preview = store.stage(ByteArrayInputStream(store.create(password())), password())
        val sidecar = File(context.getDatabasePath(abandonedName).path + "-wal").also { it.writeText("dados fictícios") }

        store.removeAbandonedStages()
        assertTrue(sidecar.exists())
        assertTrue(store.cleanupPending.value)
        assertTrue(context.getDatabasePath(preview.databaseName).exists())

        deletion.leaveSidecar = false
        store.removeAbandonedStages()
        assertFalse(sidecar.exists())
        assertFalse(store.cleanupPending.value)
        assertTrue(store.restore(preview))
    }

    @Test fun startupCleanupDoesNotNeedTheLiveRepository() = runBlocking<Unit> {
        val preview = store.stage(ByteArrayInputStream(store.create(password())), password())
        val file = context.getDatabasePath(preview.databaseName)
        assertTrue(file.exists())

        val deletion = object : ContextWrapper(context) {
            var blocked = true
            override fun deleteDatabase(name: String): Boolean {
                if (blocked && name == preview.databaseName) throw SecurityException("Exclusão bloqueada no teste.")
                return super.deleteDatabase(name)
            }
        }
        val afterRestart = BackupStore(deletion) { error("O banco financeiro está indisponível.") }
        afterRestart.removeAbandonedStages()
        assertTrue(file.exists())
        assertTrue(afterRestart.cleanupPending.value)

        deletion.blocked = false
        afterRestart.removeAbandonedStages()
        assertFalse(file.exists())
        assertFalse(afterRestart.cleanupPending.value)
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
    @Test fun restoreReportsDisplayPersistenceFailureAfterRestoringFinancialData() = runBlocking<Unit> {
        repository.save(record("backup-restaurado"), null)
        preferences.setAppearance(Appearance.DARK)
        preferences.hideAmounts(true)
        val file = store.create(password())
        repository.clear()
        repository.save(record("base-local"), null)
        val isolated = context.getSharedPreferences("failing-display-restore-test", Context.MODE_PRIVATE)
        try {
            check(isolated.edit().clear().putBoolean("disclosure_v1", true)
                .putBoolean("enabled", true).putBoolean("reminders_enabled", true).commit())
            val failingSettings = CapturePreferences(FailingPreferencesContext(context, isolated, successfulCommits = 1))
            val failingStore = BackupStore(context, FinanceRepository(database, failingSettings))
            val preview = failingStore.stage(ByteArrayInputStream(file), password())

            assertFalse(failingStore.restore(preview))
            assertEquals("backup-restaurado", repository.exportRecords().single().id)
            assertFalse(failingSettings.snapshot().enabled)
            assertFalse(failingSettings.snapshot().remindersEnabled)
            assertFalse(context.getDatabasePath(preview.databaseName).exists())
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

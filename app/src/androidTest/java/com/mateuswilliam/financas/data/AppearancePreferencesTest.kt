package com.mateuswilliam.financas.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mateuswilliam.financas.FinanceApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePreferencesTest {
    private val app get() = ApplicationProvider.getApplicationContext<FinanceApplication>()

    @Before fun reset() = runBlocking<Unit> { app.repository.clear(); Unit }

    @Test fun defaultFollowsSystemAndExplicitChoicesOverrideIt() {
        assertEquals(Appearance.SYSTEM, app.settings.state.value.appearance)
        for (systemIsDark in listOf(false, true)) {
            assertEquals(systemIsDark, Appearance.SYSTEM.usesDarkTheme(systemIsDark))
            assertFalse(Appearance.LIGHT.usesDarkTheme(systemIsDark))
            assertTrue(Appearance.DARK.usesDarkTheme(systemIsDark))
        }
    }

    @Test fun newPreferencesInstanceReadsThePersistedChoice() {
        app.settings.setAppearance(Appearance.DARK)
        assertEquals(Appearance.DARK, CapturePreferences(app).state.value.appearance)
    }

    @Test fun unknownStoredChoiceFallsBackToSystem() {
        app.getSharedPreferences("capture_settings", Context.MODE_PRIVATE).edit(commit = true) {
            putString("appearance", "FUTURE_THEME")
        }
        assertEquals(Appearance.SYSTEM, CapturePreferences(app).state.value.appearance)
    }

    @Test fun changingAppearancePreservesConsentSelectionAndPrivacyChoice() {
        app.settings.acceptDisclosure()
        app.settings.selectPackages(setOf("banco.exemplo"))
        app.settings.hideAmounts(true)
        app.settings.setAppearance(Appearance.LIGHT)
        val persisted = CapturePreferences(app)
        assertTrue(persisted.permits("banco.exemplo"))
        assertTrue(persisted.state.value.hideAmounts)
        assertEquals(Appearance.LIGHT, persisted.state.value.appearance)
    }

    @Test fun clearingAllDataAlsoResetsAppearance() = runBlocking {
        app.settings.setAppearance(Appearance.DARK)
        app.repository.clear()
        assertEquals(Appearance.SYSTEM, CapturePreferences(app).state.value.appearance)
    }

    @Test fun dismissingTheSameNoticeSurvivesAReopenedPreferencesInstance() {
        app.settings.observeCaptureCondition("ACCESS")
        app.settings.dismissCaptureNotice("ACCESS")
        app.settings.observeCaptureCondition("ACCESS")
        val reopened = CapturePreferences(app)
        assertEquals("ACCESS", reopened.state.value.dismissedCondition)
        assertFalse(reopened.state.value.consented)
    }

    @Test fun activeCaptureThenRevocationCanShowANewNotice() {
        app.settings.observeCaptureCondition("ACCESS")
        app.settings.dismissCaptureNotice("ACCESS")
        app.settings.observeCaptureCondition("READY")
        app.settings.observeCaptureCondition("ACCESS")
        assertEquals("", CapturePreferences(app).state.value.dismissedCondition)
    }
}

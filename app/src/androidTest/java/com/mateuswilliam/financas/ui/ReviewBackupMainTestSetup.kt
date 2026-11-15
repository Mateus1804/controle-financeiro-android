package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import kotlinx.coroutines.runBlocking

/** Prepara somente dados fictícios no emulador dedicado, pela Activity normal do produto. */
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.resetReviewBackupMain() {
    val app = activity.application as FinanceApplication
    runBlocking {
        app.maintenanceReady.await()
        app.repository.clear()
        Unit
    }
    app.settings.finishIntro()
    app.settings.dismissNamePrompt()
    val model = ViewModelProvider(activity)[ForecastViewModel::class.java]
    model.refresh()
    waitUntil(10000) { model.dataset.value?.rules?.isEmpty() == true }
    waitUntil(10000) {
        onAllNodes(hasTestTag("add-realized-income") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
            onAllNodesWithTag("forecast-dashboard").fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
}

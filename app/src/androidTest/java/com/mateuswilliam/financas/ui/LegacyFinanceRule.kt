package com.mateuswilliam.financas.ui

import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mateuswilliam.financas.MainActivity

/** Old ledger interaction contracts remain regression tested separately from the primary planner. */
internal fun createLegacyFinanceRule(): AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> {
    val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_LEGACY_TEST_UI, true)
    return AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(intent)) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }
}

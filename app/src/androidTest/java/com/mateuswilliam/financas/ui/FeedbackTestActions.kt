package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.junit.Assert.assertTrue

/** As jornadas verificam o resultado da ação; não dependem da duração virtual do SnackbarHost. */
internal fun ComposeTestRule.dismissFeedback(prefix: String) {
    waitForIdle()
    val message = hasText(prefix, substring = true)
    val dismissible = SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss) and
        (message or hasAnyDescendant(message))
    if (onAllNodes(dismissible).fetchSemanticsNodes().isNotEmpty()) {
        onNode(dismissible).performSemanticsAction(SemanticsActions.Dismiss) { dismiss -> assertTrue(dismiss()) }
    }
    waitUntil(8000) { onAllNodesWithText(prefix, substring = true).fetchSemanticsNodes().isEmpty() }
}

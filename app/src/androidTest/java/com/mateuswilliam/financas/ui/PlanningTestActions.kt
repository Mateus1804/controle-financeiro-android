package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue

internal fun ComposeTestRule.showMovements() {
    if (onAllNodesWithTag("dashboard-list").fetchSemanticsNodes().isEmpty()) {
        onNode(hasText("Resumo") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        waitForIdle()
    }
}
internal fun ComposeTestRule.scrollAnalysisTo(matcher: SemanticsMatcher) {
    if (onAllNodesWithTag("planning-list").fetchSemanticsNodes().isEmpty()) {
        showMovements()
        onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("open-planning"))
        onNodeWithTag("open-planning").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    onNodeWithTag("planning-list").performScrollToNode(matcher)
}
internal fun ComposeTestRule.chooseInstallments() {
    onNodeWithText("Única").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    onNodeWithText("Parcelada").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
}
internal fun ComposeTestRule.openPlanningActions(itemId: String) {
    val trigger = "actions-$itemId"
    scrollPlanningToTag(trigger)
    onNodeWithTag(trigger).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
}
internal fun ComposeTestRule.performPlanningAction(tag: String) {
    val itemId = when {
        tag.startsWith("exclude-recurring-") -> "recurring-${tag.removePrefix("exclude-recurring-")}"
        tag.startsWith("confirm-recurring-") -> "recurring-${tag.removePrefix("confirm-recurring-")}"
        tag.startsWith("edit-recurring-") -> "recurring-${tag.removePrefix("edit-recurring-")}"
        tag.startsWith("edit-month-") -> "recurring-${tag.removePrefix("edit-month-")}"
        tag.startsWith("active-recurring-") -> "recurring-${tag.removePrefix("active-recurring-")}"
        tag.startsWith("skip-") -> "recurring-${tag.removePrefix("skip-")}"
        tag.startsWith("fixed-") -> "recurring-${tag.removePrefix("fixed-")}"
        tag.startsWith("confirm-installment:") -> tag.removePrefix("confirm-")
        tag.startsWith("edit-installment:") -> tag.removePrefix("edit-")
        tag.startsWith("exclude-installment:") -> tag.removePrefix("exclude-")
        tag.startsWith("cancel-installment:") -> tag.removePrefix("cancel-")
        else -> null
    }
    if (itemId == null) scrollPlanningToTag(tag) else openPlanningActions(itemId)
    onNodeWithTag(tag).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
}
internal fun ComposeTestRule.scrollPlanningToTag(tag: String) {
    val movement = tag.startsWith("movement-") || tag.startsWith("actions-recurring-") || tag.startsWith("actions-installment:")
    if (movement) showMovements()
    waitUntil(5000) {
        try {
            if (movement) onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag(tag))
            else scrollAnalysisTo(hasTestTag(tag))
            true
        } catch (_: AssertionError) { false }
    }
}

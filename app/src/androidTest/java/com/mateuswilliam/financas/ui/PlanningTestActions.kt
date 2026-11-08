package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.ComposeTestRule

internal fun ComposeTestRule.openPlanningActions(itemId: String) {
    val trigger = "actions-$itemId"
    scrollPlanningToTag(trigger)
    onNodeWithTag(trigger).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
}

internal fun ComposeTestRule.performPlanningAction(tag: String) {
    val itemId = when {
        tag.startsWith("exclude-recurring-") -> "recurring-${tag.removePrefix("exclude-recurring-")}"
        tag.startsWith("fixed-") -> "recurring-${tag.removePrefix("fixed-")}"
        tag.startsWith("edit-installment:") -> tag.removePrefix("edit-")
        tag.startsWith("exclude-installment:") -> tag.removePrefix("exclude-")
        tag.startsWith("cancel-installment:") -> tag.removePrefix("cancel-")
        else -> null
    }
    if (itemId == null) scrollPlanningToTag(tag)
    else openPlanningActions(itemId)
    onNodeWithTag(tag).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
}

internal fun ComposeTestRule.scrollPlanningToTag(tag: String) {
    waitUntil(5000) {
        try {
            onNodeWithTag("planning-list").performScrollToNode(hasTestTag(tag))
            true
        } catch (_: AssertionError) {
            false
        }
    }
}

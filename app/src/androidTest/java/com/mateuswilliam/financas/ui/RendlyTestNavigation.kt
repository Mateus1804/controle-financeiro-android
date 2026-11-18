package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.mateuswilliam.financas.core.Movement

/** Caminhos visíveis do produto; não troca o estado da Activity nem instala uma interface alternativa. */
private fun ComposeContentTestRule.clickRendlyTab(label: String) {
    val node = onNode(hasText(label) and hasClickAction())
    try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
    node.performClick()
}
internal fun ComposeContentTestRule.openRendlyMonth() {
    clickRendlyTab("Mês")
    waitUntil(10000) { onAllNodesWithTag("records-list").fetchSemanticsNodes().isNotEmpty() }
}
internal fun ComposeContentTestRule.openRendlyMoreDestination(tag: String, destination: Int) {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
    clickRendlyTab("Mais")
    onNodeWithTag("more-$destination").performScrollTo().performClick()
    waitUntil(10000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
internal fun ComposeContentTestRule.openRendlyPlanner() = openRendlyMoreDestination("forecast-dashboard",7)
internal fun ComposeContentTestRule.openRendlySalary() = openRendlyPlanner()
internal fun ComposeContentTestRule.openRendlySettings() = openRendlyMoreDestination("settings-list",6)
internal fun ComposeContentTestRule.openRendlyCommitment() = openRendlyMoreDestination("commitment-screen",10)
internal fun ComposeContentTestRule.openRendlyReserve() = openRendlyMoreDestination("forecast-reserve",8)
internal fun ComposeContentTestRule.openRendlyReview() {
    if (onAllNodesWithTag("pending-list").fetchSemanticsNodes().isNotEmpty()) return
    openRendlySettings()
    onNodeWithTag("settings-list").performScrollToNode(hasTestTag("open-notification-review"))
    onNodeWithTag("open-notification-review").performClick()
}
internal fun ComposeContentTestRule.openRendlyGraphs() { clickRendlyTab("Gráficos") }
internal fun ComposeContentTestRule.openRendlyProjections() {
    openRendlyPlanner()
    onNodeWithTag("forecast-dashboard").performScrollToNode(hasText("Projetar próximos ciclos"))
    onNodeWithText("Projetar próximos ciclos").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
    waitUntil(10000) { onAllNodesWithTag("forecast-projections").fetchSemanticsNodes().isNotEmpty() }
}
internal fun ComposeContentTestRule.newRendlyRecord(kind: Movement = Movement.EXPENSE) {
    openRendlyMonth()
    onNodeWithTag("add-movement").performClick()
    onNodeWithTag(if (kind == Movement.INCOME) "add-realized-income" else if (kind == Movement.EXPENSE) "add-realized-expense" else "add-${kind.name}").performClick()
}
internal fun ComposeContentTestRule.openRecordDetails() {
    if (onAllNodesWithContentDescription("Meio de pagamento").fetchSemanticsNodes().isEmpty())
        onNodeWithTag("record-payment-details").performScrollTo().performClick()
}

/** Cadastro único: os dados são digitados uma vez e seguem para as opções de previsão. */
internal fun ComposeContentTestRule.newRendlyForecast(
    kind: Movement, title: String, amountDigits: String,
    frequency: com.mateuswilliam.financas.core.ForecastFrequency = com.mateuswilliam.financas.core.ForecastFrequency.MONTHLY,
    category: com.mateuswilliam.financas.core.Category? = null,
) {
    newRendlyRecord(kind)
    onNodeWithTag("amount-input").performScrollTo().performTextReplacement(amountDigits)
    onNodeWithTag("record-description").performScrollTo().performTextReplacement(title)
    androidx.test.espresso.Espresso.closeSoftKeyboard()
    if (category != null) {
        onNodeWithContentDescription("Categoria").performScrollTo().performClick()
        onNodeWithText(category.label).performScrollTo().performClick()
    }
    onNodeWithContentDescription("Frequência").performScrollTo().performClick()
    onNodeWithText(if (frequency == com.mateuswilliam.financas.core.ForecastFrequency.INSTALLMENTS) "Parcelada" else "Mensal").performClick()
    onNode(hasText("Continuar previsão") and hasClickAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
    waitUntil(10000) { onAllNodesWithTag("forecast-title").fetchSemanticsNodes().isNotEmpty() }
    if (frequency == com.mateuswilliam.financas.core.ForecastFrequency.ONCE) {
        onNodeWithContentDescription("Frequência").performScrollTo().performClick()
        onNodeWithText("Uma vez").performClick()
    }
}

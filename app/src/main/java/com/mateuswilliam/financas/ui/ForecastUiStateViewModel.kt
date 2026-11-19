package com.mateuswilliam.financas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.core.Record
import java.time.YearMonth
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Estado de interface: não grava lançamentos, valores digitados, senhas ou dados no Room. */
class ForecastUiStateViewModel(handle: SavedStateHandle) : ViewModel() {
    internal val navigation = ForecastNavigationState(handle)
    internal val editors = ForecastEditorsState(handle)
    internal val review = ForecastReviewState(handle)
}

private class SavedUiProperty<T>(private val handle: SavedStateHandle, private val key: String, initial: T) : ReadWriteProperty<Any?, T> {
    private var value by mutableStateOf(if (handle.contains(key)) handle.get<T>(key) as T else initial)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
        handle[key] = value
    }
}

internal class ForecastNavigationState(handle: SavedStateHandle) {
    var accountFilterKey: String? by SavedUiProperty(handle, "navigation.accountFilterKey", null)
    private var destinationId: Int by SavedUiProperty(handle, "navigation.tab.v2", RendlyDestination.MONTH.id)
    var destination: RendlyDestination
        get() = RendlyDestination.entries.firstOrNull { it.id == destinationId } ?: RendlyDestination.MONTH
        set(value) { destinationId = value.id }
    var followingCurrent: Boolean by SavedUiProperty(handle, "navigation.followingCurrent", true)
    var monthText: String by SavedUiProperty(handle, "navigation.monthText", YearMonth.now().toString())
    var expenseDimension: ExpenseDimension? by SavedUiProperty(handle, "navigation.expenseDimension", null)
    var expenseKey: String by SavedUiProperty(handle, "navigation.expenseKey", "")
    var expenseLabel: String by SavedUiProperty(handle, "navigation.expenseLabel", "")
    var recordCategory: Category? by SavedUiProperty(handle, "navigation.recordCategory", null)
}

internal class ForecastEditorsState(handle: SavedStateHandle) {
    var balanceEditorName: String? by SavedUiProperty(handle, "editors.balanceEditorName", null)
    var balanceEditorDate: String? by SavedUiProperty(handle, "editors.balanceEditorDate", null)
    // Valores em edição sobrevivem à rotação apenas em memória.
    var forecastSeed by mutableStateOf<Record?>(null)
    var accountsOpen: Boolean by SavedUiProperty(handle, "editors.accountsOpen", false)
    var editor: Boolean by SavedUiProperty(handle, "editors.editor", false)
    var editItem: String? by SavedUiProperty(handle, "editors.editItem", null)
    var editKey: String? by SavedUiProperty(handle, "editors.editKey", null)
    var scopeStart: String? by SavedUiProperty(handle, "editors.scopeStart", null)
    var scopeEnd: String? by SavedUiProperty(handle, "editors.scopeEnd", null)
    var onlyOccurrence: Boolean by SavedUiProperty(handle, "editors.onlyOccurrence", true)
    var initialFrequency: ForecastFrequency by SavedUiProperty(handle, "editors.initialFrequency", ForecastFrequency.ONCE)
    var initialKind: ForecastKind by SavedUiProperty(handle, "editors.initialKind", ForecastKind.EXPENSE)
    var dueDates: Boolean by SavedUiProperty(handle, "editors.dueDates", false)
    var templates: Boolean by SavedUiProperty(handle, "editors.templates", false)
    var cycleEditor: Boolean by SavedUiProperty(handle, "editors.cycleEditor", false)
    var goalEditor: Boolean by SavedUiProperty(handle, "editors.goalEditor", false)
    var simulationEditor: Boolean by SavedUiProperty(handle, "editors.simulationEditor", false)
    var backupDialog: Int by SavedUiProperty(handle, "editors.backupDialog", 0)
    var restoreUri: String? by SavedUiProperty(handle, "editors.restoreUri", null)
    var clearing: Boolean by SavedUiProperty(handle, "editors.clearing", false)
    var disclosure: Boolean by SavedUiProperty(handle, "editors.disclosure", false)
    var merchantCategoriesOpen: Boolean by SavedUiProperty(handle, "editors.merchantCategoriesOpen", false)
    var appPicker: Boolean by SavedUiProperty(handle, "editors.appPicker", false)
    // Simulações não adotadas sobrevivem à rotação, mas não são persistidas em disco.
    var simulation by mutableStateOf<ForecastRule?>(null)
}

internal class ForecastReviewState(handle: SavedStateHandle) {
    var preset by mutableStateOf<Record?>(null)
    var recordEditor: Boolean by SavedUiProperty(handle, "review.recordEditor", false)
    var editingRecordId: String? by SavedUiProperty(handle, "review.editingRecordId", null)
    var editingDraftId: String? by SavedUiProperty(handle, "review.editingDraftId", null)
    var draftRevision: Int? by SavedUiProperty(handle, "review.draftRevision", null)
    var actualKind: Movement by SavedUiProperty(handle, "review.actualKind", Movement.EXPENSE)
    var initialFrequency: ForecastFrequency by SavedUiProperty(handle, "review.initialFrequency", ForecastFrequency.ONCE)
    var initialCategory: Category? by SavedUiProperty(handle, "review.initialCategory", null)
    var initialDescription: String by SavedUiProperty(handle, "review.initialDescription", "")
    var reviewIds: List<String> by SavedUiProperty(handle, "review.reviewIds", emptyList())
    var reviewAfter: String? by SavedUiProperty(handle, "review.reviewAfter", null)
}

package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.mateuswilliam.financas.core.MoneyInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow

private fun centInputTransformation(reportInvalid: (Boolean) -> Unit) = InputTransformation {
    val incoming = asCharSequence().toString()
    val normalized = MoneyInput.normalize(incoming)
    reportInvalid(normalized == null)
    when {
        normalized == null -> revertAllChanges()
        normalized == incoming -> Unit
        incoming.all { it in '0'..'9' } -> delete(0, incoming.length - normalized.length)
        else -> {
            // A colagem formatada é convertida uma vez; a digitação continua em centavos.
            replace(0, length, normalized)
            placeCursorAtEnd()
        }
    }
}

private val CurrencyOutputTransformation = OutputTransformation {
    if (length > 0) {
        repeat((3 - length).coerceAtLeast(0)) { insert(0, "0") }
        insert(length - 2, ",")
        var separator = length - 6
        while (separator > 0) {
            insert(separator, ".")
            separator -= 3
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyAmountField(state: TextFieldState, attempted: Boolean, enabled: Boolean, onInvalidInput: (Boolean) -> Unit) {
    val feedback = remember { MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    var rejected by rememberSaveable { mutableStateOf(false) }
    val notifyParent by rememberUpdatedState(onInvalidInput)
    val transformation = remember(feedback) { centInputTransformation { feedback.tryEmit(it) } }
    LaunchedEffect(feedback) { feedback.collect { rejected = it; notifyParent(it) } }
    val cents = MoneyInput.cents(state.text.toString())
    val invalid = (attempted || state.text.isNotEmpty()) && (cents == null || cents <= 0)
    OutlinedTextField(
        state = state,
        label = { Text("Valor (R$)") },
        prefix = { Text("R$") },
        placeholder = { Text("0,00") },
        modifier = Modifier.fillMaxWidth().testTag("amount-input"),
        enabled = enabled,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        inputTransformation = transformation,
        outputTransformation = CurrencyOutputTransformation,
        isError = invalid || rejected,
        supportingText = {
            Text(if (rejected) "Entrada inválida ou acima do limite. Digite números ou cole um valor como 1.234,56."
                else if (invalid) "Informe um valor maior que zero."
                else "Digite só números: 1234 vira 12,34. Valores em reais também podem ser colados.")
        },
    )
}

package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.mateuswilliam.financas.core.MoneyInput

private val CentInputTransformation = InputTransformation {
    val incoming = asCharSequence().toString()
    val normalized = MoneyInput.normalize(incoming)
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
fun MoneyAmountField(state: TextFieldState, attempted: Boolean, enabled: Boolean) {
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
        inputTransformation = CentInputTransformation,
        outputTransformation = CurrencyOutputTransformation,
        isError = invalid,
        supportingText = {
            Text(if (invalid) "Informe um valor maior que zero."
                else "Digite só números: 1234 vira 12,34. Também aceito colar um valor em reais.")
        },
    )
}

package com.mateuswilliam.financas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Oculta valores também em descrições livres e notificações; o texto armazenado é preservado.
private val currencyInText = Regex("(?i)(?:R\\$|BRL)[\\s\\u00A0\\u202F]*[+-]?[\\s\\u00A0\\u202F]*\\d(?:[\\d.,]*\\d)?|(?<![\\d/])\\d+(?:\\.\\d{3})*,\\d{2}(?!\\d)")
fun privateDisplayText(text: String, hidden: Boolean): String = if (hidden) currencyInText.replace(text, "••••") else text

// A abertura do editor não desativa a ocultação global. A revelação é explícita e local à tela.
@androidx.compose.runtime.Composable
fun PrivateValueGate(hidden: Boolean, action: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    var revealed by androidx.compose.runtime.remember(hidden) { androidx.compose.runtime.mutableStateOf(false) }
    if (!hidden || revealed) content()
    if (hidden) androidx.compose.material3.TextButton(onClick = { revealed = !revealed }) {
        androidx.compose.material3.Text(if (revealed) "Ocultar novamente" else action)
    }
}

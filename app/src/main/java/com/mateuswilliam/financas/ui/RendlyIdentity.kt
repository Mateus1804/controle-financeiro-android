package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun DisplayNameDialog(initial: String, dismiss: () -> Unit, save: (String) -> Unit, firstRun: Boolean = false) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Como você quer ser chamado?") }, text = {
        Column {
            Text("Opcional. Use até 40 caracteres. O nome fica neste aparelho e não integra o backup.")
            OutlinedTextField(value, { value = it; error = null }, singleLine = true,
                label = { Text("Nome de exibição") }, isError = error != null,
                modifier = Modifier.fillMaxWidth().testTag("display-name-input"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        val name = value.trim()
        if (name.codePointCount(0, name.length) > 40 || name.any { it.isISOControl() }) error = "Use até 40 caracteres em uma única linha."
        else runCatching { save(name) }.onSuccess { dismiss() }.onFailure { error = it.message }
    }) { Text("Salvar") } }, dismissButton = {
        Row { if (!firstRun) TextButton({ runCatching { save("") }.onSuccess { dismiss() }.onFailure { error = it.message } }) { Text("Apagar nome") }
            TextButton(dismiss) { Text(if (firstRun) "Agora não" else "Cancelar") } }
    })
}

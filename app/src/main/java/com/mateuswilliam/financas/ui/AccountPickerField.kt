package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.AccountNames
import com.mateuswilliam.financas.core.AccountOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerField(label: String, value: String, options: List<AccountOption>, onChange: (String) -> Unit,
                       onAdd: suspend (String) -> String) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { choosing = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                Text(value.ifBlank { "Escolher conta" }, modifier = Modifier.padding(vertical = 8.dp))
            }
            IconButton(onClick = { adding = true; error = null }, modifier = Modifier.size(48.dp)) {
                AppIcon(R.drawable.ic_add, description = "Adicionar $label")
            }
        }
    }
    if (choosing) ModalBottomSheet(onDismissRequest = { choosing = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it.take(80) }, label = { Text("Buscar conta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                val matches = options.filter { it.label.contains(AccountNames.clean(query), ignoreCase = true) }
                items(matches, key = { it.key }) { option ->
                    TextButton(onClick = { onChange(option.label); choosing = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(option.label)
                    }
                }
                if (matches.isEmpty()) item { Text("Nenhuma conta encontrada. Adicione uma opção pelo botão +.") }
            }
            OutlinedButton(onClick = { choosing = false; adding = true; error = null }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar conta") }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { if (!saving) adding = false },
        title = { Text("Adicionar conta ou banco") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(80); error = null }, label = { Text("Nome da conta") }, singleLine = true, enabled = !saving)
                Text("Ex.: Inter pessoal, Inter trabalho ou Carteira. Não informe número de conta ou senha.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch {
                try { onChange(onAdd(name)); adding = false; name = "" }
                catch (exception: IllegalArgumentException) { error = exception.message }
                catch (_: Exception) { error = "Não foi possível salvar a conta. Tente novamente." }
                finally { saving = false }
            }
        }) { Text(if (saving) "Salvando…" else "Adicionar") } },
        dismissButton = { TextButton(onClick = { adding = false }, enabled = !saving) { Text("Cancelar") } })
}

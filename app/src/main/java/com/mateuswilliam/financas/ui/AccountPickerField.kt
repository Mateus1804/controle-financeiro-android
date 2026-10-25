package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.AccountNames
import com.mateuswilliam.financas.core.AccountOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerField(label: String, value: String, options: List<AccountOption>, onChange: (String) -> Unit,
                       onAdd: suspend (String) -> String, hidden: Boolean = false) {
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
            OutlinedButton(onClick = { choosing = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                AppIcon(R.drawable.ic_bank); Spacer(Modifier.width(8.dp))
                Text(privateDisplayText(value.ifBlank { "Escolher conta" }, hidden), Modifier.weight(1f).padding(vertical = 8.dp))
                AppIcon(R.drawable.ic_chevron_down)
            }
            IconButton(onClick = { adding = true; error = null }, modifier = Modifier.size(48.dp)) {
                AppIcon(R.drawable.ic_add, description = "Adicionar $label")
            }
        }
    }
    if (choosing) ModalBottomSheet(onDismissRequest = { choosing = false }) {
        DialogSystemBars()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it.take(80) }, label = { Text("Buscar conta") }, leadingIcon = { AppIcon(R.drawable.ic_search) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val matches = remember(options, query) { options.filter { it.label.contains(AccountNames.clean(query), ignoreCase = true) } }
            Text("Contas recentes primeiro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(matches, key = { it.key }) { option ->
                    ListItem(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(option.label); choosing = false },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        leadingContent = { IconBadge(R.drawable.ic_wallet) },
                        headlineContent = { Text(privateDisplayText(option.label, hidden)) },
                        trailingContent = if (AccountNames.key(value) == option.key) {{ AppIcon(R.drawable.ic_check) }} else null)

                }
                if (matches.isEmpty()) item { Text("Nenhuma conta encontrada. Adicione uma opção pelo botão +.") }
            }
            OutlinedButton(onClick = { choosing = false; adding = true; error = null }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar conta") }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { if (!saving) adding = false },
        title = { DialogSystemBars(); Text("Adicionar conta ou banco") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(80); error = null }, label = { Text("Nome da conta") }, singleLine = true, enabled = !saving, modifier = Modifier.testTag("account-name-input"))
                Text("Ex.: Inter pessoal, Inter trabalho ou Carteira. Não informe número de conta ou senha.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { FilledTonalButton(enabled = !saving, onClick = {
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

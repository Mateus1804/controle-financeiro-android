package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.CaptureIssue

@Composable
fun CaptureIntroduction(onActivate: () -> Unit, onManual: () -> Unit) {
    AlertDialog(onDismissRequest = onManual,
        title = { DialogSystemBars(); Text("Seu dinheiro, com mais clareza") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Registre receitas e despesas ou receba sugestões a partir das notificações dos aplicativos que escolher.")
                Text("O processamento ocorre neste aparelho. Cada sugestão precisa da sua revisão antes de entrar nos totais.")
                Text("A captura é opcional. Você pode começar manualmente e configurar esse recurso em Ajustes depois.")
            }
        },
        confirmButton = { TextButton(onClick = onActivate) { Text("Ativar preenchimento por notificações") } },
        dismissButton = { TextButton(onClick = onManual) { Text("Usar manualmente") } })
}

@Composable
fun CaptureNotice(issue: CaptureIssue, onResolve: () -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(issue.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Text("×", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { contentDescription = "Fechar aviso de captura" })
                }
            }
            TextButton(onClick = onResolve) { Text(issue.action) }
        }
    }
}

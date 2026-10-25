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
        title = { DialogSystemBars(); Text("Finanças com clareza") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(com.mateuswilliam.financas.R.drawable.ic_wallet)
                Text("Receitas, despesas e transferências em um só lugar. O registro manual está sempre disponível.")
                Text("As sugestões por notificações são processadas neste aparelho e dependem de revisão e confirmação antes de entrar nos totais.")
                Text("A captura é opcional e limitada aos aplicativos selecionados. O consentimento e as autorizações são solicitados apenas ao ativar o recurso, também disponível em Ajustes.")
            }
        },
        confirmButton = { FilledTonalButton(onClick = onActivate) { Text("Ativar preenchimento por notificações") } },
        dismissButton = { TextButton(onClick = onManual) { Text("Usar manualmente") } })
}

@Composable
fun CaptureNotice(issue: CaptureIssue, onResolve: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(issue.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    AppIcon(com.mateuswilliam.financas.R.drawable.ic_close, description = "Fechar aviso de captura")
                }
            }
            TextButton(onClick = onResolve) { Text(issue.action) }
        }
    }
}

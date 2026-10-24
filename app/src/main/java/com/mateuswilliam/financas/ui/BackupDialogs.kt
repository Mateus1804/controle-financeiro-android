package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.mateuswilliam.financas.core.BackupCrypto
import com.mateuswilliam.financas.data.StagedRestore

@Composable
fun BackupPasswordDialog(creating: Boolean, busy: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    // A senha não participa de rememberSaveable, preferências, Intent ou logs.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val error = when {
        creating -> BackupCrypto.passwordError(password.toCharArray()) ?: if (password != repeated) "As senhas precisam ser iguais." else null
        password.isEmpty() || password.length > 256 -> "Informe a senha original do backup."
        else -> null
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { DialogSystemBars(); Text(if (creating) "Criar backup criptografado" else "Abrir backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (creating) "O arquivo inclui lançamentos, pendências, contas, orçamentos e recorrências. Use uma senha longa e exclusiva. Sem essa senha, o backup não pode ser recuperado."
                    else "A senha será usada somente para abrir este arquivo. Os dados locais só serão substituídos após a conferência em uma base temporária e a confirmação da prévia.")
                OutlinedTextField(password, { password = it.take(256) }, label = { Text("Senha do backup") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
                if (creating) OutlinedTextField(repeated, { repeated = it.take(256) }, label = { Text("Repetir senha") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
                if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Text("A senha não é salva no aplicativo. O destino é escolhido pelo seletor de arquivos do Android.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            attempted = true
            if (error == null) { val secret = password.toCharArray(); password = ""; repeated = ""; onConfirm(secret) }
        }) { Text(if (creating) "Preparar backup" else "Conferir backup") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { password = ""; repeated = ""; onDismiss() }) { Text("Cancelar") } })
}

@Composable
fun BackupPreviewDialog(preview: StagedRestore, busy: Boolean, onCancel: () -> Unit, onRestore: () -> Unit) {
    var confirmed by remember(preview.databaseName) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { DialogSystemBars(); Text("Conferir restauração") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backup de ${shortDate(preview.createdAt)} conferido em uma base temporária.")
            Text("${preview.records} lançamentos · ${preview.pending} pendências · ${preview.accounts} contas cadastradas\n${preview.budgets} orçamentos · ${preview.recurring} recorrências")
            Text("A restauração substitui os dados financeiros locais. Crie um backup atual antes de continuar. Captura e lembretes ficam pausados; consentimento e permissões do Android não são transferidos.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(confirmed, { confirmed = it }, enabled = !busy)
                Text("Substituir os dados locais por este backup", Modifier.weight(1f).padding(top = 8.dp))
            }
        } },
        confirmButton = { TextButton(onClick = onRestore, enabled = confirmed && !busy) { Text(if (busy) "Restaurando…" else "Restaurar backup") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancelar") } })
}

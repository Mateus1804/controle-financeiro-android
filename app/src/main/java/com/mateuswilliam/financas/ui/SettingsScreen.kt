package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.BuildConfig
import com.mateuswilliam.financas.SelectableApp
import com.mateuswilliam.financas.data.CaptureSettings
import com.mateuswilliam.financas.data.Appearance

@Composable
fun SettingsScreen(
    settings: CaptureSettings, connected: Boolean, hasAccess: Boolean, canAlert: Boolean, captureError: Boolean,
    onToggle: (Boolean) -> Unit, onApps: () -> Unit, onAccess: () -> Unit,
    onReconnect: () -> Unit, onAlerts: () -> Unit, onExport: () -> Unit,
    onClear: () -> Unit, onSimulate: () -> Unit, onAppearance: (Appearance) -> Unit, busy: Boolean,
) {
    var privacy by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Meus ajustes", "Privacidade e controle em primeiro lugar.") }
        item {
            SettingsCard {
                Text("Aparência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Escolha um tema ou acompanhe a configuração do aparelho.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Appearance.entries.forEachIndexed { index, appearance ->
                        SegmentedButton(
                            selected = settings.appearance == appearance,
                            onClick = { onAppearance(appearance) },
                            shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                        ) { Text(appearance.label) }
                    }
                }
            }
        }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Capturar notificações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Somente dos aplicativos que eu selecionar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(settings.enabled, onToggle, modifier = Modifier.semantics { contentDescription = "Capturar notificações" })
                }
                val status = when {
                    !settings.enabled -> "Captura desativada. O cadastro manual continua disponível."
                    settings.packages.isEmpty() -> "Escolha os bancos ou carteiras que deseja acompanhar."
                    !hasAccess -> "Falta autorizar o acesso às notificações no Android."
                    !connected -> "Acesso concedido. Aguardando a conexão do serviço."
                    else -> "Captura ativa para ${settings.packages.size} aplicativo(s)."
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (captureError) Text("Uma notificação não pôde ser processada. Confira seus lançamentos e o espaço disponível no aparelho.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onApps, modifier = Modifier.fillMaxWidth()) { Text("Escolher aplicativos") }
                if (settings.consented && settings.packages.isNotEmpty()) {
                    OutlinedButton(onClick = onAccess, modifier = Modifier.fillMaxWidth()) { Text(if (hasAccess) "Gerenciar acesso no Android" else "Autorizar no Android") }
                    if (hasAccess && !connected && settings.enabled) TextButton(onClick = onReconnect) { Text("Solicitar reconexão") }
                }
                if (settings.enabled && !canAlert) OutlinedButton(onClick = onAlerts, modifier = Modifier.fillMaxWidth()) { Text("Permitir alertas de revisão") }
                Text("A interpretação depende do texto disponível. O valor, a movimentação, o meio e a categoria podem ser corrigidos antes da confirmação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard {
                Text("Meus dados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Os registros ficam neste aparelho. O CSV exporta os lançamentos confirmados; ele ainda não substitui um backup restaurável.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Exportar lançamentos em CSV") }
                TextButton(onClick = { privacy = true }) { Text("Privacidade e uso dos dados") }
                TextButton(onClick = onClear, enabled = !busy) { Text("Apagar todos os meus dados", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (BuildConfig.DEBUG) item {
            SettingsCard {
                Text("Experimentar a revisão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Exemplos fictícios mostram o preenchimento e a confirmação, sem acessar bancos. Eles ficam identificados como simulação.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onSimulate, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Criar notificações fictícias") }
            }
        }
        item { Text("Controle Financeiro · ${BuildConfig.VERSION_NAME}\nPrimeira versão em desenvolvimento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text("Privacidade") },
        text = { Text("Processo as notificações autorizadas neste aparelho para sugerir lançamentos. Não transmito os textos bancários, não solicito credenciais de banco e não integro anúncios ou serviços de análise.\n\nO Android concede acesso amplo às notificações; o aplicativo filtra a seleção antes de ler o conteúdo. Descarto mensagens identificadas como códigos, senhas ou autenticação.\n\nApago o texto original após confirmar ou ignorar a pendência. Mantenho identificadores locais para evitar reprocessar o mesmo evento.\n\nA exportação CSV contém os dados que você confirmou e é salva no destino escolhido. Os dados locais são removidos ao desinstalar ou usar a opção de apagar. A restauração de backup ainda está em desenvolvimento.") },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text("Entendi") } })
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun DisclosureDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Antes de ativar a captura") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Para sugerir receitas e despesas, o Controle Financeiro acessa o título e o texto das notificações dos aplicativos selecionados, inclusive quando esta tela está fechada.")
                Text("A autorização do Android permite acesso amplo às notificações. Dentro do aplicativo, apenas as origens escolhidas são processadas. Você pode alterar a seleção ou desligar a captura a qualquer momento.")
                Text("As pendências são armazenadas e interpretadas neste aparelho. Nenhum texto bancário é transmitido. Mensagens identificadas como autenticação são descartadas. O texto original é apagado ao confirmar ou ignorar a pendência.")
                Text("Você revisa os dados antes de criar qualquer lançamento. Recusar a captura permite continuar usando o cadastro manual.")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Concordar e continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } })
}

@Composable
fun AppPickerDialog(apps: List<SelectableApp>, loading: Boolean, selected: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selection by remember { mutableStateOf(selected.toSet()) }
    var search by remember { mutableStateOf("") }
    val visible = apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Bancos e carteiras") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Selecione as origens que deseja acompanhar. A lista mostra aplicativos com ícone de abertura disponíveis neste perfil do Android.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(search, { search = it.take(80) }, label = { Text("Buscar aplicativo") }, singleLine = true)
                Text("${selection.size} selecionado(s)", style = MaterialTheme.typography.labelMedium)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().toggleable(value = app.packageName in selection, role = Role.Checkbox) {
                            selection = if (it) selection + app.packageName else selection - app.packageName
                        }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(app.packageName in selection, onCheckedChange = null)
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!loading && visible.isEmpty()) item { Text("Nenhum aplicativo encontrado. Confira o nome ou se ele está instalado neste perfil.") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selection) }, enabled = !loading) { Text("Salvar seleção") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

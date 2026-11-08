package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalDensity
import com.mateuswilliam.financas.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
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
    onBackup: () -> Unit, onRestore: () -> Unit, onPlanning: () -> Unit,
    onLockChange: (Boolean) -> Unit, onSecureScreen: (Boolean) -> Unit, onReminders: (Boolean) -> Unit = {},
) {
    var privacy by remember { mutableStateOf(false) }
    LazyColumn(Modifier.testTag("settings-list"), contentPadding = PaddingValues(FinanceSpacing.page, 16.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
        item { SectionTitle("Ajustes", "Preferências, captura e proteção dos dados") }
        item {
            SettingsCard {
                SettingsHeading("Aparência", R.drawable.ic_visibility)
                Text("Escolha um tema ou acompanhe a configuração do aparelho.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (LocalDensity.current.fontScale > 1.3f) {
                    Appearance.entries.forEach { appearance ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = settings.appearance == appearance, role = Role.RadioButton,
                            onClick = { onAppearance(appearance) }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = settings.appearance == appearance, onClick = null)
                            Spacer(Modifier.width(12.dp)); Text(appearance.label)
                        }
                    }
                } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Appearance.entries.forEachIndexed { index, appearance ->
                        SegmentedButton(
                            selected = settings.appearance == appearance,
                            onClick = { onAppearance(appearance) },
                            shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                            icon = { if (settings.appearance == appearance) AppIcon(selectedIcon(R.drawable.ic_check, true), Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = FinanceSpacing.touch),
                        ) { Text(appearance.label) }
                    }
                }
                }
            }
        }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Capturar notificações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Somente dos aplicativos selecionados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Button(onClick = onApps, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Escolher aplicativos") }
                if (settings.consented && settings.packages.isNotEmpty()) {
                    OutlinedButton(onClick = onAccess, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text(if (hasAccess) "Gerenciar acesso no Android" else "Autorizar no Android") }
                    if (hasAccess && !connected && settings.enabled) TextButton(onClick = onReconnect) { Text("Solicitar reconexão") }
                }
                if (settings.enabled && !canAlert) OutlinedButton(onClick = onAlerts, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Permitir alertas de revisão") }
                FinanceDisclosure("Sobre a captura", "capture-explanation") {
                    Text("A interpretação depende do texto disponível. O valor, a movimentação, o meio e a categoria podem ser corrigidos antes da confirmação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SettingsHeading("Dados do aplicativo", R.drawable.ic_records) }
                    ContextualActions("Dados do aplicativo", "actions-app-data", !busy, listOf(
                        FinanceAction("Privacidade e uso dos dados", { privacy = true }),
                        FinanceAction("Apagar todos os dados", onClear, destructive = true),
                    ))
                }
                Text("Os registros ficam neste aparelho. O CSV é um relatório; o backup permite recuperar os dados do aplicativo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Exportar lançamentos em CSV") }
            }
        }
        item {
            SettingsCard {
                SettingsHeading("Backup e recuperação", R.drawable.ic_backup)
                Text("Protegido por senha. A senha não pode ser recuperada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onBackup, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Criar backup criptografado") }
                OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Restaurar backup") }
                FinanceDisclosure("Sobre o backup e a restauração", "backup-explanation") {
                    Text("O backup guarda os dados do aplicativo. A restauração apresenta uma conferência e pede confirmação antes de substituir os dados atuais.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SettingsCard {
                SettingsHeading("Planejamento", R.drawable.ic_calendar)
                Text("Análises, projeções e limites mensais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onPlanning, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Projeções e orçamentos") }
            }
        }
        item {
            SettingsCard {
                SettingsHeading("Proteção do aplicativo", R.drawable.ic_lock)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Autenticar ao abrir", Modifier.weight(1f))
                    Switch(settings.lockEnabled, onLockChange, modifier = Modifier.semantics { contentDescription = "Autenticar ao abrir" })
                }
                FinanceDisclosure("Sobre a autenticação", "lock-explanation") {
                    Text("Biometria ou credencial do aparelho ao iniciar e após 30 segundos em segundo plano. Alterar este controle exige autenticação. O banco local não recebe criptografia adicional.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ocultar no seletor e bloquear capturas", Modifier.weight(1f))
                    Switch(settings.secureScreen, onSecureScreen, modifier = Modifier.semantics { contentDescription = "Ocultar no seletor e bloquear capturas" })
                }
                FinanceDisclosure("Sobre a proteção da tela", "screen-protection-explanation") {
                    Text("Protege a imagem no seletor de aplicativos e impede capturas e compartilhamento da tela quando respeitado pelo Android. A proteção é ativada junto com o bloqueio e pode ser ajustada separadamente.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lembretes de vencimentos", Modifier.weight(1f))
                    Switch(settings.remindersEnabled, onReminders,
                        modifier = Modifier.semantics { contentDescription = "Lembretes de vencimentos" })
                }
                Text("Lembretes das movimentações previstas, sujeitos às permissões e às condições do Android.", style = MaterialTheme.typography.bodySmall)
                if (!canAlert) TextButton(onClick = onAlerts) { Text("Permitir alertas") }
            }
        }
        if (BuildConfig.DEBUG) item {
            SettingsCard {
                SettingsHeading("Experimentar a revisão", R.drawable.ic_pending)
                Text("Exemplos fictícios, identificados como simulação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onSimulate, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Criar notificações fictícias") }
            }
        }
        item { Text("Controle Financeiro · ${BuildConfig.VERSION_NAME}\nDados locais · revisão obrigatória das sugestões", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { DialogSystemBars(); Text("Privacidade") },
        text = { Text(modifier = Modifier.verticalScroll(rememberScrollState()), text = "As notificações autorizadas são processadas neste aparelho para sugerir lançamentos. O aplicativo não transmite textos bancários, não solicita credenciais de banco e não inclui anúncios ou serviços de análise.\n\nO Android concede acesso amplo às notificações; o aplicativo filtra as origens selecionadas antes de ler o conteúdo. Mensagens identificadas como códigos, senhas ou autenticação são descartadas.\n\nO texto original é apagado após a confirmação ou o descarte da pendência. Identificadores locais são mantidos para evitar o reprocessamento do mesmo evento.\n\nO CSV contém os lançamentos confirmados. O backup criptografado também inclui pendências e planejamento; o arquivo e a senha ficam sob responsabilidade do usuário. O bloqueio da interface não criptografa o banco local.\n\nOs dados locais são removidos ao desinstalar ou usar a opção de apagar. A recuperação depende de um backup válido e da senha original.") },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text("Entendi") } })
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun DisclosureDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { DialogSystemBars(); Text("Antes de ativar a captura") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Para sugerir receitas e despesas, o Controle Financeiro acessa o título e o texto das notificações dos aplicativos selecionados, inclusive quando esta tela está fechada.")
                Text("A autorização do Android permite acesso amplo às notificações. Dentro do aplicativo, apenas as origens escolhidas são processadas. A seleção pode ser alterada e a captura desativada a qualquer momento.")
                Text("As pendências são armazenadas e interpretadas neste aparelho. Nenhum texto bancário é transmitido. Mensagens identificadas como autenticação são descartadas. O texto original é apagado ao confirmar ou ignorar a pendência.")
                Text("A revisão e a confirmação são obrigatórias antes de criar qualquer lançamento. Recusar a captura permite continuar usando o cadastro manual.")
            }
        },
        confirmButton = { FilledTonalButton(onClick = onAccept) { Text("Concordar e continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } })
}

@Composable
fun AppPickerDialog(apps: List<SelectableApp>, loading: Boolean, selected: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selection by remember { mutableStateOf(selected.toSet()) }
    var search by remember { mutableStateOf("") }
    val visible = remember(apps, search) { apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) } }
    AlertDialog(onDismissRequest = onDismiss, title = { DialogSystemBars(); Text("Bancos e carteiras") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("As origens podem ser selecionadas abaixo. A lista mostra aplicativos com ícone de abertura disponíveis neste perfil do Android.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(search, { search = it.take(80) }, label = { Text("Buscar aplicativo") }, singleLine = true)
                Text("${selection.size} selecionado(s)", style = MaterialTheme.typography.labelMedium)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch).toggleable(value = app.packageName in selection, role = Role.Checkbox) {
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

@Composable
private fun SettingsHeading(title: String, icon: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon)
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    }
}

package com.mateuswilliam.financas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.data.Appearance
import com.mateuswilliam.financas.data.CaptureSettings
import java.time.LocalDate

@Composable
internal fun SheetSettings(
    finance: FinanceViewModel,
    settings: CaptureSettings,
    cycleDay: Int,
    onCycle: (() -> Unit)?,
    onLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Mantém os seletores de documentos registrados enquanto o conteúdo dos Ajustes está fechado.
    visible: Boolean = true,
) {
    var editingName by rememberSaveable { mutableStateOf(false) }
    var creatingBackup by rememberSaveable { mutableStateOf(false) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    val busy by finance.busy.collectAsStateWithLifecycle()
    val backupDestination by finance.backupDestination.collectAsStateWithLifecycle()
    val backupCleanupPending by finance.backupCleanupPending.collectAsStateWithLifecycle()
    val staged by finance.stagedRestore.collectAsStateWithLifecycle()

    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        finance.writeBackup(it)
    }
    val importDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        restoreUri = it?.toString()
    }
    LaunchedEffect(backupDestination) {
        val filename = finance.takeBackupDestination() ?: return@LaunchedEffect
        runCatching { exportDocument.launch(filename) }.onFailure {
            finance.writeBackup(null)
            finance.message.value = "Não foi possível abrir o seletor de arquivos."
        }
    }

    if (visible) {
        LazyColumn(
            modifier.fillMaxSize().testTag("settings-list"),
            contentPadding = PaddingValues(horizontal = FinanceSpacing.page, vertical = FinanceSpacing.section),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.section),
        ) {
            item {
                SheetSettingsSection("Preferências") {
                    SheetSettingAction(
                        label = "Nome de exibição",
                        detail = privateDisplayText(settings.displayName, settings.hideAmounts).ifBlank { "Opcional" },
                        onClick = { editingName = true },
                        modifier = Modifier.testTag("display-name-settings"),
                    )
                    HorizontalDivider()
                    SheetSettingAction("Início do ciclo", "Dia $cycleDay", onCycle)
                }
            }
            item {
                SheetSettingsSection("Aparência") {
                    SheetAppearanceOptions(settings.appearance, finance.preferences::setAppearance)
                }
            }
            item {
                SheetSettingsSection("Privacidade") {
                    SheetSettingSwitch(
                        "Autenticar ao abrir", "Biometria ou credencial do aparelho",
                        settings.lockEnabled, onLockChange,
                    )
                    HorizontalDivider()
                    SheetSettingSwitch(
                        "Proteger a tela", "Bloqueia capturas e oculta a tela nos apps recentes",
                        settings.secureScreen, finance.preferences::setSecureScreen,
                    )
                }
            }
            item {
                SheetSettingsSection("Backup e recuperação") {
                    Text(
                        "Guarde a senha para recuperar seus dados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { creatingBackup = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch),
                    ) { Text("Criar backup criptografado") }
                    OutlinedButton(
                        onClick = {
                            runCatching { importDocument.launch(arrayOf("*/*")) }.onFailure {
                                finance.message.value = "Não foi possível abrir o seletor de arquivos."
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch),
                    ) { Text("Restaurar backup") }
                    if (backupCleanupPending) {
                        Text(
                            "Uma cópia temporária da restauração ainda está no aparelho.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = finance::retryBackupCleanup,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = FinanceSpacing.touch),
                        ) { Text("Tentar limpar novamente") }
                    }
                }
            }
        }
        if (editingName) {
            DisplayNameDialog(settings.displayName, { editingName = false }, finance.preferences::setDisplayName)
        }
    }

    if (creatingBackup || restoreUri != null) {
        BackupPasswordDialog(creatingBackup, busy, onDismiss = {
            creatingBackup = false
            restoreUri = null
        }) { secret ->
            val creating = creatingBackup
            val source = restoreUri
            creatingBackup = false
            restoreUri = null
            when {
                creating -> finance.createBackup(secret, "rendly-${LocalDate.now()}.cfbackup")
                source != null -> finance.stageBackup(source.toUri(), secret)
                else -> secret.fill('\u0000')
            }
        }
    }
    staged?.let { BackupPreviewDialog(it, busy, finance::cancelRestore, finance::restoreBackup) }
}

@Composable
private fun SheetSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.small)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SheetSettingAction(label: String, detail: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)
            .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
            .padding(vertical = FinanceSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetSettingText(label, detail, Modifier.weight(1f))
        if (onClick != null) AppIcon(R.drawable.ic_chevron_right)
    }
}

@Composable
private fun SheetSettingSwitch(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = FinanceSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetSettingText(label, detail, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun SheetSettingText(label: String, detail: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetAppearanceOptions(selected: Appearance, onChange: (Appearance) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (fontScale > 1.3f || maxWidth < 280.dp * fontScale) {
            Column(Modifier.selectableGroup()) {
                Appearance.entries.forEach { appearance ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)
                            .selectable(selected = appearance == selected, role = Role.RadioButton, onClick = { onChange(appearance) }),
                        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(appearance == selected, onClick = null)
                        Text(appearance.label)
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Appearance.entries.forEachIndexed { index, appearance ->
                    SegmentedButton(
                        selected = appearance == selected,
                        onClick = { onChange(appearance) },
                        shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                        modifier = Modifier.heightIn(min = FinanceSpacing.touch),
                    ) { Text(appearance.label) }
                }
            }
        }
    }
}

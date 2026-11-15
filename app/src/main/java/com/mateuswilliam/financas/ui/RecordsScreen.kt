package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun PendingScreen(pending: List<DraftEntity>, hidden: Boolean, onReview: (DraftEntity) -> Unit, onIgnore: (String) -> Unit, busy: Boolean, captureEnabled: Boolean = true) {
    LazyColumn(Modifier.testTag("pending-list"), contentPadding = PaddingValues(FinanceSpacing.page, 16.dp, FinanceSpacing.page, FinanceSpacing.listBottom)) {
        item { Box(Modifier.padding(bottom = 12.dp)) { SectionTitle("Caixa de revisão", "Sugestões locais. Nenhum lançamento é registrado sem confirmação.") } }
        if (pending.isEmpty()) item { EmptyCard(if (captureEnabled) "Nenhuma notificação para revisar" else "Captura desativada", "As sugestões dos aplicativos autorizados aparecem aqui. Configure a captura em Ajustes.") }
        itemsIndexed(pending, key = { _, it -> it.id }) { index, draft ->
            Surface(shape = groupedShape(index, pending.size), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        leadingContent = { IconBadge(categoryIcon(draft.category), movementColor(draft.movement)) },
                        overlineContent = { Text(privateDisplayText(draft.bank, hidden), style = MaterialTheme.typography.labelMedium) },
                        headlineContent = { Text(privateDisplayText(draft.sourceTitle.ifBlank { "Nova notificação" }, hidden)) },
                        trailingContent = { ContextualActions(privateDisplayText(draft.sourceTitle.ifBlank { "Nova notificação" }, hidden),
                            "actions-pending-${draft.id}", !busy,
                            listOf(FinanceAction("Descartar", { onIgnore(draft.id) }, "ignore-${draft.id}"))) },
                        supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${draft.movement.label} · ${draft.method.label} · ${shortDate(draft.occurredAt)}", style = MaterialTheme.typography.bodySmall)
                            if (draft.amountCents != null) Amount(draft.amountCents, hidden, size = 18) else Text("Valor a preencher")
                            FinanceDisclosure("Detalhes da sugestão", "pending-details-${draft.id}") {
                                Text(privateDisplayText(draft.explanation, hidden), style = MaterialTheme.typography.bodySmall)
                            }
                        } })
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onReview(draft) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch).testTag("review-${draft.id}")) { Text("Revisar") }
                    }
                }
            }
        }
    }
}

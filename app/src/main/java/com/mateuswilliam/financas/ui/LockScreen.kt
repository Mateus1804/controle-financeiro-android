package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mateuswilliam.financas.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(busy: Boolean, message: String?, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(28.dp).widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(R.drawable.ic_lock)
            Text("Controle Financeiro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Acesso protegido", style = MaterialTheme.typography.titleLarge)
            Text("A autenticação do aparelho é necessária para abrir os dados. A captura autorizada pode continuar em segundo plano.")
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onUnlock, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (busy) "Aguardando autenticação…" else "Desbloquear")
            }
        }
    }
}

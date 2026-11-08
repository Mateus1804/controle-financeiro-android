package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*

/** Preview starts with today's balances and removes an edited record before applying its replacement. */
internal fun transferPreviewBalance(balance: AccountBalance, edited: Record, existing: Record?, now: Long): Long? {
    val current = balance.balanceCents ?: return null
    val key = balance.account.key
    fun contribution(record: Record): Long = when (record.movement) {
        Movement.TRANSFER -> (if (AccountNames.key(record.bank) == key) -record.amountCents else 0L) +
            (if (AccountNames.key(record.destination) == key) record.amountCents else 0L)
        Movement.INCOME -> if (AccountNames.key(record.bank) == key) record.amountCents else 0L
        Movement.EXPENSE -> if (AccountNames.key(record.bank) == key) -record.amountCents else 0L
        Movement.REFUND -> if (AccountNames.key(record.bank) != key) 0L else
            if (record.reversalMovement == Movement.INCOME) -record.amountCents else record.amountCents
        Movement.UNKNOWN -> 0L
    }
    val start = balance.reference?.effectiveAt ?: Long.MIN_VALUE
    return runCatching {
        val withoutOriginal = if (existing != null && existing.occurredAt in start..now)
            Math.subtractExact(current, contribution(existing)) else current
        // Earlier transactions are already included in the opening reference.
        if (edited.occurredAt >= start) Math.addExact(withoutOriginal, contribution(edited)) else withoutOriginal
    }.getOrNull()
}

@Composable
internal fun TransferPreview(record: Record, existing: Record?, balances: List<AccountBalance>, hidden: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val future = record.occurredAt > System.currentTimeMillis()
            Text(if (future) "Prévia após a transferência agendada · ${shortDate(record.occurredAt)}"
                else "Prévia dos saldos após a transferência", style = MaterialTheme.typography.titleSmall)
            if (future) Text("Simulação para a data agendada; esta transferência ainda não altera o saldo disponível atual.", style = MaterialTheme.typography.bodySmall)
            val affectedAccounts = buildList {
                add("Origem" to record.bank)
                add("Destino" to record.destination)
                if (existing != null) {
                    val newKeys = setOf(AccountNames.key(record.bank), AccountNames.key(record.destination))
                    if (AccountNames.key(existing.bank) !in newKeys) add("Conta anterior · efeito original desfeito" to existing.bank)
                    if (existing.movement == Movement.TRANSFER && AccountNames.key(existing.destination) !in newKeys)
                        add("Destino anterior · crédito desfeito" to existing.destination)
                }
            }.distinctBy { AccountNames.key(it.second) }
            affectedAccounts.forEach { (label, name) ->
                val balance = balances.firstOrNull { it.account.key == AccountNames.key(name) }
                Text("$label: ${privateDisplayText(name.ifBlank { "Escolha uma conta" }, hidden)}", style = MaterialTheme.typography.bodySmall)
                val projected = balance?.let { transferPreviewBalance(it, record, existing, System.currentTimeMillis()) }
                if (projected == null) Text("Saldo desconhecido · configure uma referência", style = MaterialTheme.typography.bodySmall)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Amount(balance.balanceCents!!, hidden, size = 14)
                    Text("→")
                    Amount(projected, hidden, size = 14)
                }
            }
            Text(if (existing != null && existing.movement != Movement.TRANSFER)
                "O efeito financeiro do lançamento original será removido da conta e do resultado. A nova transferência apenas move dinheiro entre suas contas; ela não compõe receitas ou despesas."
                else "O total entre suas contas é preservado. Transferências não alteram receitas, despesas ou resultado.", style = MaterialTheme.typography.bodySmall)
            Text(if (future) "A simulação usa os saldos atuais e esta alteração; outras movimentações até a data agendada podem mudar o saldo. Tarifas devem ser registradas como despesa separada."
                else "Se houver tarifa, registre uma despesa separada. A prévia usa os saldos atuais e esta alteração.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

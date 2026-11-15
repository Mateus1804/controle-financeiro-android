package com.mateuswilliam.financas.core

/** Conta total: soma dos saldos de todas as contas ativas. Contas sem saldo informado marcam o total como parcial. */
data class AccountTotal(val cents: Long, val knownAccounts: Int, val missingAccounts: Int) {
    val partial: Boolean get() = missingAccounts > 0
    val hasBalance: Boolean get() = knownAccounts > 0

    companion object {
        fun of(accounts: List<AccountBalance>): AccountTotal {
            val active = accounts.filter { !it.archived }
            return AccountTotal(active.sumOf { it.balanceCents ?: 0L }, active.count { it.balanceCents != null }, active.count { it.balanceCents == null })
        }
    }
}

package com.mateuswilliam.financas.core

/** Archived money remains part of wealth; unknown opening balances are never silently treated as known zero. */
data class AccountFunds(val operating: AccountTotal, val reserve: AccountTotal, val wealth: AccountTotal) {
    companion object {
        const val RESERVE_NAME = "Reserva de emergência"
        val RESERVE_KEY = AccountNames.key(RESERVE_NAME)
        fun isReserve(name: String): Boolean = AccountNames.key(name) == RESERVE_KEY
        private fun total(rows: List<AccountBalance>) = AccountTotal(
            rows.fold(0L) { total, row -> Math.addExact(total, row.balanceCents ?: 0) },
            rows.count { it.balanceCents != null }, rows.count { it.balanceCents == null })
        fun of(accounts: List<AccountBalance>) = AccountFunds(
            total(accounts.filter { !it.archived && it.account.key != RESERVE_KEY }),
            total(accounts.filter { it.account.key == RESERVE_KEY }), total(accounts))
    }
}

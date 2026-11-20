package com.mateuswilliam.financas.core

data class AccountBalanceReference(
    val id: Long, val accountKey: String, val balanceCents: Long,
    val effectiveAt: Long, val createdAt: Long, val reason: String,
)
data class AccountBalance(
    val account: AccountOption, val archived: Boolean, val balanceCents: Long?,
    val reference: AccountBalanceReference?,
)

object AccountBalances {
    fun calculate(
        accounts: List<AccountOption>,
        archivedKeys: Set<String>,
        references: List<AccountBalanceReference>,
        records: List<Record>,
        asOf: Long,
    ): List<AccountBalance> {
        if (accounts.isEmpty()) return emptyList()

        val accountKeys = accounts.mapTo(HashSet()) { it.key }
        val referenceOrder = compareBy<AccountBalanceReference> { it.effectiveAt }
            .thenBy { it.createdAt }.thenBy { it.id }
        val balances = HashMap<String, RunningBalance>()
        for (reference in references) {
            if (reference.effectiveAt > asOf || reference.accountKey !in accountKeys) continue
            val current = balances[reference.accountKey]
            if (current == null || referenceOrder.compare(reference, current.reference) > 0) {
                balances[reference.accountKey] = RunningBalance(reference)
            }
        }

        val earliestReference = balances.values.minOfOrNull { it.reference.effectiveAt }
        if (earliestReference != null) {
            for (record in records) {
                if (record.occurredAt !in earliestReference..asOf) continue
                val source = AccountNames.key(record.bank)
                if (record.movement == Movement.TRANSFER) {
                    val destination = AccountNames.key(record.destination)
                    if (source == destination) continue
                    balances[source]?.add(record.occurredAt, -record.amountCents)
                    balances[destination]?.add(record.occurredAt, record.amountCents)
                } else {
                    balances[source]?.add(record.occurredAt, record.cashContribution())
                }
            }
        }

        return accounts.map { account ->
            val balance = balances[account.key]
            AccountBalance(account, account.key in archivedKeys, balance?.cents, balance?.reference)
        }
    }

    private class RunningBalance(val reference: AccountBalanceReference) {
        var cents = reference.balanceCents
            private set

        fun add(occurredAt: Long, delta: Long) {
            if (occurredAt >= reference.effectiveAt) cents = Math.addExact(cents, delta)
        }
    }

    fun touchesReserve(record: Record): Boolean = AccountFunds.isReserve(record.bank) ||
        (record.movement == Movement.TRANSFER && AccountFunds.isReserve(record.destination))

    /**
     * Checks the full timeline at each affected instant. An older deficit can be corrected
     * without requiring unrelated historic balances to be rewritten.
     */
    fun worsensReserveDeficit(
        before: List<Record>, after: List<Record>,
        beforeReferences: List<AccountBalanceReference>, afterReferences: List<AccountBalanceReference> = beforeReferences,
    ): Boolean {
        val key = AccountFunds.RESERVE_KEY
        fun changes(records: List<Record>) = records.filter(::touchesReserve)
            .groupBy { it.occurredAt }.mapValues { (_, rows) -> rows.sumOf { contribution(it, key) } }
        fun baselines(references: List<AccountBalanceReference>) = references.filter { it.accountKey == key }
            .groupBy { it.effectiveAt }.mapValues { (_, refs) ->
                refs.maxWith(compareBy<AccountBalanceReference> { it.createdAt }.thenBy { it.id }).balanceCents
            }
        val oldChanges = changes(before)
        val newChanges = changes(after)
        val oldBaselines = baselines(beforeReferences)
        val newBaselines = baselines(afterReferences)
        val instants = (oldChanges.keys + newChanges.keys + oldBaselines.keys + newBaselines.keys).sorted()
        var old: Long? = null
        var new: Long? = null
        for (at in instants) {
            old = (oldBaselines[at] ?: old)?.let { Math.addExact(it, oldChanges[at] ?: 0L) }
            new = (newBaselines[at] ?: new)?.let { Math.addExact(it, newChanges[at] ?: 0L) }
            if (new != null && new < 0 && (old == null || new < old)) return true
        }
        return false
    }

    private fun contribution(record: Record, key: String): Long {
        val source = AccountNames.key(record.bank) == key
        return when (record.movement) {
            Movement.TRANSFER -> (if (source) -record.amountCents else 0L) +
                (if (AccountNames.key(record.destination) == key) record.amountCents else 0L)
            else -> if (source) record.cashContribution() else 0L
        }
    }
}

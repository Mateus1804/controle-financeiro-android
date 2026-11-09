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
    fun calculate(accounts: List<AccountOption>, archivedKeys: Set<String>, references: List<AccountBalanceReference>, records: List<Record>, asOf: Long): List<AccountBalance> =
        accounts.map { account ->
            val reference = references.filter { it.accountKey == account.key && it.effectiveAt <= asOf }
                .maxWithOrNull(compareBy<AccountBalanceReference> { it.effectiveAt }.thenBy { it.createdAt }.thenBy { it.id })
            val balance = reference?.let { start ->
                records.asSequence().filter { it.occurredAt in start.effectiveAt..asOf }.fold(start.balanceCents) { total, record ->
                    Math.addExact(total, contribution(record, account.key))
                }
            }
            AccountBalance(account, account.key in archivedKeys, balance, reference)
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

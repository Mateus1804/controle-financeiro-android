package com.mateuswilliam.financas.core

import java.time.ZoneId

data class SheetSummary(
    val ledger: MonthlySummary,
    val spending: ExpenseReport,
    val balances: List<AccountBalance>,
    val funds: AccountFunds,
    val savedToReserve: Long,
    val withdrawnFromReserve: Long,
    val balanceAsOf: Long,
) {
    companion object {
        fun calculate(
            records: List<Record>,
            accounts: List<AccountOption>,
            archived: Set<String>,
            references: List<AccountBalanceReference>,
            period: BudgetPeriod,
            zone: ZoneId,
            asOf: Long,
        ): SheetSummary {
            val cutoff = minOf(asOf, period.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
            val effective = records.filter { it.occurredAt <= cutoff }
            val ledger = Ledger.period(effective, period, zone)
            val balances = AccountBalances.calculate(accounts, archived, references, effective, cutoff, assumeZeroOpening = true)
            var saved = 0L
            var withdrawn = 0L
            for (record in ledger.records) {
                if (record.movement != Movement.TRANSFER) continue
                val fromReserve = AccountFunds.isReserve(record.bank)
                val toReserve = AccountFunds.isReserve(record.destination)
                if (toReserve && !fromReserve) saved = Math.addExact(saved, record.amountCents)
                if (fromReserve && !toReserve) withdrawn = Math.addExact(withdrawn, record.amountCents)
            }
            return SheetSummary(ledger, ExpenseReports.period(ledger.records, period, zone, ExpenseDimension.CATEGORY),
                balances, AccountFunds.of(balances), saved, withdrawn, cutoff)
        }
    }
}

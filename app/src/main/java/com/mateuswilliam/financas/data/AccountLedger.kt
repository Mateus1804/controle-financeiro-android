package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.AccountBalance
import com.mateuswilliam.financas.core.AccountBalanceReference
import com.mateuswilliam.financas.core.AccountBalances
import com.mateuswilliam.financas.core.AccountCatalog
import com.mateuswilliam.financas.core.Record
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal data class AccountLedger(
    val asOf: Long,
    val records: List<Record>,
    val manualRecords: List<Record>,
    val balances: List<AccountBalance>,
    val references: List<AccountBalanceReference>,
)

internal class AccountLedgerHistory(
    savedAccounts: List<AccountEntity>,
    recordRows: List<RecordEntity>,
    referenceRows: List<AccountBalanceEventEntity>,
) {
    private val records = recordRows.map(RecordEntity::toRecord)
    private val manualRecords = records.filterIndexed { index, _ -> recordRows[index].sourceDraftId == null }
    private val references = referenceRows.map(AccountBalanceEventEntity::toReference)
    private val accounts = AccountCatalog.options(savedAccounts.map(AccountEntity::toOption), records)
    private val archivedKeys = savedAccounts.filter(AccountEntity::archived).mapTo(HashSet(), AccountEntity::nameKey)

    fun at(asOf: Long) = AccountLedger(
        asOf, records, manualRecords, AccountBalances.calculate(accounts, archivedKeys, references, records, asOf), references,
    )

    fun observe(now: () -> Long, zone: () -> ZoneId): Flow<AccountLedger> = flow {
        while (true) {
            val asOf = now()
            emit(at(asOf))
            val nextAt = nextChangeAfter(asOf, zone())
            delay((nextAt - now()).coerceAtLeast(1L))
        }
    }

    private fun nextChangeAfter(asOf: Long, zone: ZoneId): Long {
        val midnight = Instant.ofEpochMilli(asOf).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // O extrato vem em ordem decrescente e as referências em ordem crescente do Room.
        val nextRecord = records.asSequence().takeWhile { it.occurredAt > asOf }.lastOrNull()?.occurredAt
        val nextReference = references.firstOrNull { it.effectiveAt > asOf }?.effectiveAt
        return minOf(midnight, nextRecord ?: midnight, nextReference ?: midnight)
    }
}

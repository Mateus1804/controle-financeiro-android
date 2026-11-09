package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import java.time.*

/** Does not mutate any legacy table. Stable source keys make provenance inspectable. */
internal object LegacyForecastImporter {
    suspend fun import(dao:FinanceDao) {
        val records=dao.allRecords().associateBy { it.id }
        val linked=mutableSetOf<String>()
        val months=dao.allRecurringMonths()
        val occurrences=dao.allOccurrences()
        val commitments=dao.allCommitments()
        val purchases=dao.allPurchases().associateBy { it.id }
        val recurring=dao.allRecurring().associateBy { it.id }
        suspend fun put(rule:ForecastRule) { dao.upsertForecastRule(ForecastRuleEntity.from(rule)) }
        fun kind(m:Movement) = if(m==Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE
        fun date(r:RecordEntity)=Instant.ofEpochMilli(r.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
        fun signed(r:RecordEntity)=if(r.movement==Movement.REFUND) -r.amountCents else r.amountCents
        fun nature(r:RecordEntity)=kind(if(r.movement==Movement.REFUND) r.reversalMovement else r.movement)
        val revisions=dao.allRecurringRevisions()
        for(r in recurring.values) {
            val id="legacy:recurring:${r.id}"
            val rows=revisions.filter { it.recurringId==r.id }
            if(rows.isEmpty()) {
                val start=YearMonth.parse(r.startMonth.ifBlank { r.nextDate.take(7) }).atDay(1)
                if(r.movement!=Movement.TRANSFER) put(ForecastRule(id,start,r.title,kind(r.movement),r.amountCents,r.category,
                    ForecastFrequency.MONTHLY,start,r.dayOfMonth,active=r.active,account=r.bank,legacySource="recurring:${r.id}"))
            } else for(v in rows) if(v.movement!=Movement.TRANSFER) {
                val start=YearMonth.parse(v.effectiveMonth).atDay(1)
                put(ForecastRule(id,start,v.title,kind(v.movement),v.amountCents,v.category,
                    ForecastFrequency.MONTHLY,start,v.dayOfMonth,active=v.active,account=v.bank,legacySource="recurring:${r.id}"))
            }

        }
        for(m in months) {
            m.recordId?.let { linked.add(it) }
            if(m.movement==Movement.TRANSFER) continue
            val id="legacy:recurring:${m.recurringId}"
            // A snapshot before the first revision still needs an eligible base rule.
            val first=YearMonth.parse(m.month).atDay(1)
            if(revisions.none { it.recurringId==m.recurringId && it.effectiveMonth<=m.month }) {
                put(ForecastRule(id,first,m.title,kind(m.movement),m.amountCents,m.category,ForecastFrequency.MONTHLY,
                    first,m.dayOfMonth,active=m.active,account=m.bank,legacySource="recurring:${m.recurringId}"))
            }
            dao.upsertForecastOverride(ForecastOverrideEntity.from(ForecastOverride(id,"month:${m.month}",
                amountCents=m.amountCents,date=LocalDate.parse(m.dueDate),ignored=m.status=="SKIPPED",
                active=m.active || m.status=="RECORDED",title=m.title,category=m.category)))
        }
        val occurrenceMonths=months.map { "${it.recurringId}:${it.month}" }.toMutableSet()
        for(o in occurrences) {
            o.recordId?.let { linked.add(it) }
            if(months.any { it.recurringId==o.recurringId && it.dueDate==o.dueDate }) continue
            val r=recurring[o.recurringId] ?: continue
            if(r.movement==Movement.TRANSFER) continue
            val actual=records[o.recordId]
            val d=LocalDate.parse(o.dueDate)
            val monthKey="${o.recurringId}:${o.dueDate.take(7)}"
            if(occurrenceMonths.add(monthKey)) {
                val itemId="legacy:recurring:${o.recurringId}"
                val first=d.withDayOfMonth(1)
                if(revisions.none { it.recurringId==r.id && it.effectiveMonth<=o.dueDate.take(7) })
                    put(ForecastRule(itemId,first,r.title,kind(r.movement),r.amountCents,r.category,ForecastFrequency.MONTHLY,first,r.dayOfMonth,account=r.bank,legacySource="recurring:${r.id}"))
                dao.upsertForecastOverride(ForecastOverrideEntity.from(ForecastOverride(itemId,"month:${o.dueDate.take(7)}",
                    amountCents=actual?.let(::signed) ?: r.amountCents,date=d,ignored=o.status=="SKIPPED",
                    active=o.status=="RECORDED" || r.active)))
                continue
            }
            val id="legacy:occurrence:${o.recurringId}:${o.dueDate}"
            put(ForecastRule(id,d,r.title,actual?.let(::nature) ?: kind(r.movement),actual?.let(::signed) ?: r.amountCents,
                actual?.category ?: r.category,ForecastFrequency.ONCE,d,active=o.status=="RECORDED" || r.active,
                account=r.bank,legacySource="occurrence:${o.recurringId}:${o.dueDate}"))
            if(o.status=="SKIPPED") dao.upsertForecastOverride(ForecastOverrideEntity.from(ForecastOverride(id,"once",ignored=true)))
        }
        for(c in commitments) {
            c.recordId?.let { linked.add(it) }
            if(c.recurringId!=null && (months.any { it.recurringId==c.recurringId && it.dueDate==c.dueDate } ||
                occurrences.any { it.recurringId==c.recurringId && it.dueDate==c.dueDate })) continue
            // Monthly recurrence already projects non-materialized dates.
            if(c.recurringId!=null && recurring.containsKey(c.recurringId)) {
                val id="legacy:recurring:${c.recurringId}"
                dao.upsertForecastOverride(ForecastOverrideEntity.from(ForecastOverride(id,"month:${c.dueDate.take(7)}",
                    amountCents=records[c.recordId]?.let(::signed) ?: c.amountCents,date=LocalDate.parse(c.dueDate),
                    ignored=c.excluded,active=c.recordId!=null || recurring[c.recurringId]?.active==true,title=c.label)))
                continue
            }
            val p=purchases[c.purchaseId]
            val actual=records[c.recordId]
            val d=LocalDate.parse(c.dueDate)
            val label=if(c.installmentNumber!=null && p!=null) "${p.title} · ${c.installmentNumber}/${p.installmentCount}" else c.label
            val id="legacy:commitment:${c.id}"
            put(ForecastRule(id,d,label,actual?.let(::nature) ?: ForecastKind.EXPENSE,actual?.let(::signed) ?: c.amountCents,
                actual?.category ?: p?.category ?: Category.OTHER,ForecastFrequency.ONCE,d,
                active=c.recordId!=null || p?.openCancelled!=true,account=p?.bank ?: "",legacySource="commitment:${c.id}"))
            if(c.excluded) dao.upsertForecastOverride(ForecastOverrideEntity.from(ForecastOverride(id,"once",ignored=true)))
        }
        for(p in purchases.values) {
            // v2+ normally persists the complete pending schedule. Fill only genuinely absent rows.
            val schedule=Installments.fromTotal(p.totalCents,p.installmentCount,LocalDate.parse(p.nextDueDate),p.paidInstallments,p.dayOfMonth)
            for(i in schedule.pending) if(commitments.none { it.purchaseId==p.id && it.installmentNumber==i.number }) {
                val id="legacy:purchase:${p.id}:${i.number}"
                put(ForecastRule(id,i.dueDate,"${p.title} · ${i.number}/${p.installmentCount}",ForecastKind.EXPENSE,i.amountCents,
                    p.category,ForecastFrequency.ONCE,i.dueDate,active=!p.openCancelled,account=p.bank,legacySource="purchase:${p.id}:${i.number}"))
            }
        }
        // Install archive boundaries after materialized snapshots so historical fallback rules cannot reactivate the future.
        for(r in recurring.values.filter { it.archived && it.movement!=Movement.TRANSFER }) {
            val id="legacy:recurring:${r.id}"
            val rows=revisions.filter { it.recurringId==r.id }
            if(r.archived) {
                // The legacy archive has no timestamp; stop unknown future while preserving every materialized month.
                val stop=LocalDate.now().withDayOfMonth(1)
                val base=rows.lastOrNull { it.effectiveMonth<=stop.toString().take(7) }
                // A previously scheduled revision must never reactivate an archived source.
                dao.deleteFutureForecastRules(id,stop.toString())
                val recordedKeys=(months.filter { it.recurringId==r.id && (it.status=="RECORDED" || it.recordId!=null) }.map { "month:${it.month}" } +
                    occurrences.filter { it.recurringId==r.id && (it.status=="RECORDED" || it.recordId!=null) }.map { "month:${it.dueDate.take(7)}" } +
                    commitments.filter { it.recurringId==r.id && it.recordId!=null }.map { "month:${it.dueDate.take(7)}" }).toSet()
                for(o in dao.allForecastOverrides().filter { it.itemId==id }) {
                    val month=o.occurrenceKey.removePrefix("month:")
                    val futureMonth=runCatching { YearMonth.parse(month).atDay(1)>=stop }.getOrDefault(false)
                    val futureDate=o.date?.let { LocalDate.parse(it)>=stop }==true
                    if((futureMonth || futureDate) && o.occurrenceKey !in recordedKeys)
                        dao.upsertForecastOverride(o.copy(active=false))
                }
                put(ForecastRule(id,stop,base?.title ?: r.title,kind(base?.movement ?: r.movement),base?.amountCents ?: r.amountCents,
                    base?.category ?: r.category,ForecastFrequency.MONTHLY,stop,base?.dayOfMonth ?: r.dayOfMonth,active=false,
                    account=base?.bank ?: r.bank,legacySource="recurring:${r.id}"))
            }
        }
        val purchaseSources=purchases.values.mapNotNull { it.sourceDraftId }.toSet()
        for(r in records.values) {
            if(r.id in linked || r.sourceDraftId in purchaseSources || r.movement==Movement.TRANSFER) continue
            val d=date(r)
            put(ForecastRule("legacy:record:${r.id}",d,r.description.ifBlank { "Lançamento importado" },nature(r),signed(r),r.category,
                ForecastFrequency.ONCE,d,group=ForecastGroup.VARIABLE,account=r.bank,legacySource="record:${r.id}"))
        }
    }
}

package com.mateuswilliam.financas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.mateuswilliam.financas.core.*
import java.time.YearMonth

/** Regras com vigência civil: meses antigos ainda não abertos usam a regra original. */
@Entity(tableName = "recurring_revisions", primaryKeys = ["recurringId", "effectiveMonth"], foreignKeys = [
    ForeignKey(entity = RecurringEntity::class, parentColumns = ["id"], childColumns = ["recurringId"]),
])
data class RecurringRevisionEntity(
    val recurringId: String, val effectiveMonth: String,
    val title: String, val amountCents: Long, val movement: Movement, val method: PaymentMethod,
    val category: Category, val bank: String, val anchorDate: String, val dayOfMonth: Int, val active: Boolean,
    val fixedFromMonth: String? = null,
) {
    fun toPlan() = RecurringPlan(recurringId, title, amountCents, movement, method, category, bank, anchorDate, dayOfMonth, active)
    companion object {
        fun from(plan: RecurringPlan, month: YearMonth, fixedFromMonth: String? = null) = RecurringRevisionEntity(plan.id, month.toString(),
            plan.title, plan.amountCents, plan.movement, plan.method, plan.category, plan.bank, plan.nextDate, plan.dayOfMonth, plan.active, fixedFromMonth)
    }
}

@Entity(tableName = "recurring_months", primaryKeys = ["recurringId", "month"], indices = [
    Index(value = ["recordId"], unique = true),
], foreignKeys = [
    ForeignKey(entity = RecurringEntity::class, parentColumns = ["id"], childColumns = ["recurringId"]),
    ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.SET_NULL),
])
data class RecurringMonthEntity(
    val recurringId: String, val month: String,
    val title: String, val amountCents: Long, val movement: Movement, val method: PaymentMethod,
    val category: Category, val bank: String, val dueDate: String, val dayOfMonth: Int,
    val active: Boolean, val status: String, val recordId: String?, val fixedExpense: Boolean,
    val revision: Long = 0L, val overridden: Boolean = false, val lastRemindedDate: String = "",
) {
    fun toPlan() = RecurringPlan(recurringId, title, amountCents, movement, method, category, bank, dueDate,
        dayOfMonth, active, lastRemindedDate)
    fun toMonth() = RecurringMonth(recurringId, YearMonth.parse(month), toPlan(),
        RecurringMonthStatus.valueOf(status), recordId, fixedExpense, revision)
    companion object {
        fun from(plan: RecurringPlan, month: YearMonth, fixed: Boolean) = RecurringMonthEntity(plan.id, month.toString(),
            plan.title, plan.amountCents, plan.movement, plan.method, plan.category, plan.bank, plan.nextDate,
            plan.dayOfMonth, plan.active, "PENDING", null, fixed)
    }
}

package com.mateuswilliam.financas.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.time.YearMonth

@Entity(tableName = "monthly_plans")
data class MonthlyPlanEntity(@PrimaryKey val month: String, val incomeCents: Long, val healthyLimitBasisPoints: Int) {
    fun toPlan() = MonthlyIncomePlan(YearMonth.parse(month), incomeCents, healthyLimitBasisPoints)
}

@Entity(tableName = "purchases", indices = [Index(value = ["sourceDraftId"], unique = true)], foreignKeys = [
    ForeignKey(entity = DraftEntity::class, parentColumns = ["id"], childColumns = ["sourceDraftId"]),
])
data class PurchaseEntity(
    @PrimaryKey val id: String, val title: String, val bank: String, val category: Category,
    val method: PaymentMethod, val totalCents: Long, val installmentCount: Int, val paidInstallments: Int,
    val nextDueDate: String, val dayOfMonth: Int, val sourceDraftId: String?,
    @ColumnInfo(defaultValue = "0") val revision: Long = 0L,
    @ColumnInfo(defaultValue = "0") val openCancelled: Boolean = false,
) {
    fun toPurchase() = InstallmentPurchase(id, title, bank, category, totalCents, installmentCount,
        LocalDate.parse(nextDueDate), paidInstallments, dayOfMonth, method)
    companion object {
        fun from(p: InstallmentPurchase, draftId: String?) = PurchaseEntity(p.id, p.title.trim(), p.bank.trim(), p.category,
            p.method, p.totalCents, p.installmentCount, p.paidInstallments, p.nextDueDate.toString(), p.dayOfMonth, draftId)
    }
}

@Entity(tableName = "fixed_recurring", foreignKeys = [
    ForeignKey(entity = RecurringEntity::class, parentColumns = ["id"], childColumns = ["recurringId"]),
])
data class FixedRecurringEntity(@PrimaryKey val recurringId: String, val fromMonth: String)

@Entity(tableName = "commitments", indices = [
    Index(value = ["purchaseId", "installmentNumber"], unique = true),
    Index(value = ["recurringId", "dueDate"], unique = true),
    Index(value = ["recordId"], unique = true), Index(value = ["dueDate", "id"]),
], foreignKeys = [
    ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchaseId"]),
    ForeignKey(entity = RecurringEntity::class, parentColumns = ["id"], childColumns = ["recurringId"]),
    ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.SET_NULL),
])
data class CommitmentEntity(
    @PrimaryKey val id: String, val label: String, val amountCents: Long, val dueDate: String,
    val purchaseId: String?, val installmentNumber: Int?, val recurringId: String?, val recordId: String?,
    @ColumnInfo(defaultValue = "0") val adjusted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
) {
    fun toCommitment() = FixedCommitment(id, label, amountCents, LocalDate.parse(dueDate))
}
/** Parcela do mês, com o valor efetivo quando vinculada a um lançamento. */
data class InstallmentDue(
    val id: String, val title: String, val bank: String, val category: Category, val method: PaymentMethod,
    val amountCents: Long, val dueDate: String, val number: Int, val installmentCount: Int, val recordId: String?,
    val purchaseRevision: Long,
)

data class PurchaseManagement(
    val purchase: InstallmentPurchase, val revision: Long, val installmentId: String,
    val installment: Installment, val openCount: Int, val canReplacePurchase: Boolean,
)

package com.mateuswilliam.financas.core

enum class Movement(val label: String) {
    EXPENSE("Despesa"), INCOME("Receita"), TRANSFER("Transferência"),
    REFUND("Estorno"), UNKNOWN("A confirmar")
}

enum class PaymentMethod(val label: String) {
    PIX("Pix"), CARD("Cartão"), BOLETO("Boleto"),
    BANK_TRANSFER("TED / transferência"), CASH("Dinheiro"), UNKNOWN("Não informado")
}

enum class Category(val label: String) {
    FOOD("Alimentação"), HOME("Moradia"), TRANSPORT("Transporte"),
    HEALTH("Saúde"), EDUCATION("Educação"), LEISURE("Lazer"), OTHER("Outros"),
    SERVICES("Serviços"), SALARY("Salário"), SELF_EMPLOYED("Trabalho autônomo"),
    SALES("Vendas"), INVESTMENT("Rendimentos"), RENTAL_INCOME("Aluguéis recebidos"),
    BENEFITS("Benefícios"), OTHER_INCOME("Outras receitas")
}

data class NotificationInput(
    val packageName: String,
    val sourceKey: String,
    val bank: String,
    val title: String,
    val body: String,
    val occurredAt: Long,
)

data class Suggestion(
    val amountCents: Long?,
    val movement: Movement,
    val method: PaymentMethod,
    val category: Category,
    val explanation: String,
)

data class Record(
    val id: String,
    val amountCents: Long,
    val movement: Movement,
    val method: PaymentMethod,
    val category: Category,
    val bank: String,
    val description: String,
    val occurredAt: Long,
    val destination: String = "",
    val originalRecordId: String? = null,
    val reversalMovement: Movement = Movement.EXPENSE,
)

object RecordValidation {
    fun error(record: Record): String? = when {
        record.originalRecordId != null && (record.movement != Movement.REFUND || record.originalRecordId.isBlank() ||
            record.originalRecordId.length > 128 || '\u0000' in record.originalRecordId || record.originalRecordId == record.id) -> "Vínculo de estorno inválido."
        record.movement == Movement.REFUND && record.reversalMovement !in setOf(Movement.EXPENSE, Movement.INCOME) -> "Escolha a natureza do estorno."
        record.amountCents !in 1..Money.MAX_CENTS -> "Informe um valor maior que zero e dentro do limite."
        record.movement == Movement.UNKNOWN -> "Escolha se é despesa, receita, transferência ou estorno."
        record.id.isBlank() || record.id.length > 128 || '\u0000' in record.id -> "Identificador de lançamento inválido."
        record.bank.isBlank() || record.bank.length > 80 || '\u0000' in record.bank -> "Informe a conta ou o banco de origem (até 80 caracteres)."
        record.description.isBlank() || record.description.length > 200 || '\u0000' in record.description -> "Informe uma descrição de até 200 caracteres."
        record.occurredAt <= 0 -> "Informe uma data válida."
        record.movement == Movement.TRANSFER && record.destination.isBlank() -> "Informe a conta de destino."
        record.destination.length > 80 || '\u0000' in record.destination -> "Use até 80 caracteres para a conta de destino."
        record.movement == Movement.TRANSFER && AccountNames.key(record.bank) == AccountNames.key(record.destination) ->
            "A origem e o destino precisam ser diferentes."
        else -> null
    }
}

/** Categories offered for new entries; stored legacy categories remain valid. */
fun categoriesFor(movement: Movement, reversalMovement: Movement = Movement.EXPENSE): List<Category> =
    when (if (movement == Movement.REFUND) reversalMovement else movement) {
        Movement.INCOME -> listOf(Category.SALARY, Category.SELF_EMPLOYED, Category.SALES,
            Category.INVESTMENT, Category.RENTAL_INCOME, Category.BENEFITS, Category.OTHER_INCOME)
        Movement.EXPENSE, Movement.UNKNOWN -> listOf(Category.FOOD, Category.HOME, Category.TRANSPORT,
            Category.HEALTH, Category.EDUCATION, Category.LEISURE, Category.OTHER, Category.SERVICES)
        else -> emptyList()
    }

fun Record.incomeContribution(): Long = when {
    movement == Movement.INCOME -> amountCents
    movement == Movement.REFUND && reversalMovement == Movement.INCOME -> -amountCents
    else -> 0L
}

fun Record.expenseContribution(): Long = when {
    movement == Movement.EXPENSE -> amountCents
    movement == Movement.REFUND && reversalMovement == Movement.EXPENSE -> -amountCents
    else -> 0L
}

/** Net cash change across all owned accounts, so transfers cancel out. */
fun Record.cashContribution(): Long = incomeContribution() - expenseContribution()

object Refunds {
    fun applyOriginal(record: Record, original: Record): Record {
        require(record.movement == Movement.REFUND) { "O lançamento precisa ser um estorno." }
        require(original.movement == Movement.EXPENSE || original.movement == Movement.INCOME) { "A origem precisa ser uma despesa ou receita." }
        require(original.id != record.id && original.originalRecordId == null) { "Vínculo de estorno inválido." }
        require(original.id.isNotBlank() && original.id.length <= 128 && '\u0000' !in original.id) { "Identificador inválido." }
        return record.copy(originalRecordId = original.id, reversalMovement = original.movement, category = original.category)
    }
}

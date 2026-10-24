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
    HEALTH("Saúde"), EDUCATION("Educação"), LEISURE("Lazer"), OTHER("Outros")
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
)

object RecordValidation {
    fun error(record: Record): String? = when {
        record.amountCents !in 1..Money.MAX_CENTS -> "Informe um valor maior que zero e dentro do limite."
        record.movement == Movement.UNKNOWN -> "Escolha se é despesa, receita, transferência ou estorno."
        record.bank.isBlank() || record.bank.length > 80 -> "Informe a conta ou o banco de origem (até 80 caracteres)."
        record.description.isBlank() || record.description.length > 200 -> "Informe uma descrição de até 200 caracteres."
        record.occurredAt <= 0 -> "Informe uma data válida."
        record.movement == Movement.TRANSFER && record.destination.isBlank() -> "Informe a conta de destino."
        record.destination.length > 80 -> "Use até 80 caracteres para a conta de destino."
        record.movement == Movement.TRANSFER && record.bank.trim().equals(record.destination.trim(), true) ->
            "A origem e o destino precisam ser diferentes."
        else -> null
    }
}

package com.mateuswilliam.financas.core

import java.text.Normalizer

/** Regras genéricas de português; os exemplos de teste são sintéticos, não homologações bancárias. */
object NotificationParser {
    private val currency = Regex("(?:R\\$|BRL)\\s*(\\d[\\d.,]*\\d|\\d)", RegexOption.IGNORE_CASE)
    private val sensitive = Regex("\\b(codigo|senha|token|otp|autenticacao|verification|passcode)\\b")
    private val refund = Regex("\\b(estorno|estornada|estornado|reembolso|reembolsado|devolucao)\\b")
    private val incoming = Regex("pix recebido|recebeu (?:um )?pix|voce recebeu|salario creditado|deposito recebido|transferencia recebida")
    private val outgoing = Regex("compra (?:aprovada|realizada)|pagamento (?:realizado|efetuado|aprovado)|pix enviado|voce pagou|transferencia enviada|debito realizado")

    fun isSensitive(title: String, body: String): Boolean =
        sensitive.containsMatchIn(normalize("$title $body"))

    fun parse(input: NotificationInput): Suggestion {
        val combined = "${input.title} ${input.body}"
        val text = normalize(combined)
        // Múltiplos valores podem representar compra, saldo e limite. A escolha depende de revisão.
        val amounts = currency.findAll(combined).mapNotNull { Money.parse(it.groupValues[1]) }.toSet()
        val amount = amounts.singleOrNull()
        val isIncoming = incoming.containsMatchIn(text)
        val isOutgoing = outgoing.containsMatchIn(text)
        val promotional = Regex("\\b(oferta|promocao|cashback disponivel|limite disponivel|emprestimo aprovado|credito pre-aprovado)\\b")
            .containsMatchIn(text)
        val movement = when {
            promotional -> Movement.UNKNOWN
            refund.containsMatchIn(text) -> Movement.REFUND
            text.contains("entre suas contas") || text.contains("entre minhas contas") -> Movement.TRANSFER
            isIncoming && !isOutgoing -> Movement.INCOME
            isOutgoing && !isIncoming -> Movement.EXPENSE
            else -> Movement.UNKNOWN
        }
        val method = when {
            Regex("\\bpix\\b").containsMatchIn(text) -> PaymentMethod.PIX
            Regex("\\b(cartao|credito|debito)\\b").containsMatchIn(text) -> PaymentMethod.CARD
            text.contains("boleto") -> PaymentMethod.BOLETO
            Regex("\\b(ted|transferencia)\\b").containsMatchIn(text) -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.UNKNOWN
        }
        val explanation = when {
            amounts.size > 1 -> "Há mais de um valor na mensagem. Confira o valor da movimentação."
            amount == null -> "O valor não ficou claro. Preencha apenas se esta mensagem representar uma movimentação."
            movement == Movement.UNKNOWN -> "Confira se a mensagem representa uma movimentação e escolha o tipo."
            else -> "Valor e tipo sugeridos pelo texto. Confira os campos antes de confirmar."
        }
        return Suggestion(amount, movement, method, category(text), explanation)
    }

    private fun category(text: String): Category {
        val rules = listOf(
            Category.FOOD to "\\b(ifood|supermercado|restaurante|padaria|lanchonete|alimentacao)\\b",
            Category.HOME to "\\b(aluguel|condominio|energia|conta de luz|conta de agua|moradia)\\b",
            Category.TRANSPORT to "\\b(uber|gasolina|combustivel|estacionamento|pedagio|transporte)\\b",
            Category.HEALTH to "\\b(farmacia|drogaria|consulta medica|saude)\\b",
            Category.EDUCATION to "\\b(faculdade|mensalidade escolar|curso|educacao)\\b",
            Category.LEISURE to "\\b(cinema|netflix|spotify|lazer)\\b",
        )
        return rules.firstOrNull { Regex(it.second).containsMatchIn(text) }?.first ?: Category.OTHER
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
}

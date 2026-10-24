package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {
    private fun parse(text: String, bank: String = "Banco de exemplo") = NotificationParser.parse(
        NotificationInput("banco.exemplo", "evento-1", bank, "", text, 1),
    )
    @Test fun `sugere despesa de cartao e alimentacao`() {
        val result = parse("Compra aprovada de R$ 42,90 em Supermercado Exemplo")
        assertEquals(4290L, result.amountCents); assertEquals(Movement.EXPENSE, result.movement)
        assertEquals(PaymentMethod.CARD, parse("Compra aprovada no cartão de R$ 42,90").method)
        assertEquals(Category.FOOD, result.category)
    }
    @Test fun `identifica receita por pix sem depender do nome do banco`() {
        listOf("Inter", "PicPay", "Mercado Pago", "PagBank", "Nubank", "Outro banco").forEach { bank ->
            val result = parse("Pix recebido de R$ 250,00", bank)
            assertEquals(Movement.INCOME, result.movement); assertEquals(PaymentMethod.PIX, result.method)
        }
    }
    @Test fun `identifica pix enviado e moradia`() {
        val result = parse("Pix enviado: R$ 800,00. Aluguel.")
        assertEquals(Movement.EXPENSE, result.movement); assertEquals(Category.HOME, result.category)
    }
    @Test fun `transferencia propria nao vira receita ou despesa`() {
        assertEquals(Movement.TRANSFER, parse("Pix enviado de R$ 200,00 entre suas contas").movement)
    }
    @Test fun `pix recebido pode ser corrigido para transferencia pelo usuario`() {
        val initial = parse("Pix recebido de R$ 200,00")
        assertEquals(Movement.INCOME, initial.movement)
        val corrected = Record("id", 20000, Movement.TRANSFER, initial.method, Category.OTHER, "Origem", "Entre contas", 1, "Destino")
        assertNull(RecordValidation.error(corrected))
    }
    @Test fun `estorno tem tipo proprio`() {
        assertEquals(Movement.REFUND, parse("Estorno de R$ 30,00 no cartão").movement)
    }
    @Test fun `nao escolhe entre compra e saldo`() {
        val result = parse("Compra aprovada de R$ 20,00. Saldo R$ 400,00.")
        assertNull(result.amountCents); assertTrue(result.explanation.contains("mais de um valor"))
    }
    @Test fun `valor repetido no titulo e corpo nao cria ambiguidade`() {
        assertEquals(2000L, parse("Compra aprovada R$ 20,00. Compra: R$ 20,00").amountCents)
    }
    @Test fun `mensagem promocional nao recebe tipo financeiro confirmado`() {
        assertEquals(Movement.UNKNOWN, parse("Oferta: empréstimo aprovado de R$ 5.000,00").movement)
    }
    @Test fun `mensagem sem valor continua revisavel sem inventar quantia`() {
        val result = parse("Seu extrato está disponível")
        assertNull(result.amountCents); assertEquals(Movement.UNKNOWN, result.movement)
    }
    @Test fun `nao transforma numeros de conta em valor`() {
        assertNull(parse("Pix recebido na conta 12345, agência 0123").amountCents)
    }
    @Test fun `nome Mercado Pago nao e categoria alimentacao`() {
        assertEquals(Category.OTHER, parse("Mercado Pago: Pix recebido de R$ 20,00").category)
    }
    @Test fun `mensagens contraditorias exigem escolha do tipo`() {
        assertEquals(Movement.UNKNOWN, parse("Pix recebido e Pix enviado: R$ 20,00").movement)
    }
    @Test fun `codigo senha e autenticacao sao descartados antes de persistir`() {
        assertTrue(NotificationParser.isSensitive("Código de acesso", "Use 123456"))
        assertTrue(NotificationParser.isSensitive("Banco", "Token de autenticação. R$ 40,00"))
        assertFalse(NotificationParser.isSensitive("Compra aprovada", "R$ 40,00 no restaurante"))
    }
}

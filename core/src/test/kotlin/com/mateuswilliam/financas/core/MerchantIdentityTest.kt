package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class MerchantIdentityTest {
    @Test fun `identifica estabelecimento em compra aprovada e normaliza a chave`() {
        val a = MerchantIdentity.identify("Compra aprovada", "Compra aprovada em PADARIA SÃO JOÃO no valor de R$ 25,00.")
        val b = MerchantIdentity.identify("Nubank", "Compra de R$ 12,00 aprovada em Padaria Sao Joao")
        assertEquals("padaria sao joao", a?.key)
        assertEquals(a?.key, b?.key)
    }
    @Test fun `identifica pagamento para estabelecimento`() {
        assertEquals("mercado bom preco", MerchantIdentity.identify("Pix", "Você pagou R$ 40,00 para Mercado Bom Preco.")?.key)
    }
    @Test fun `sem padrao claro ou com nome generico nao ha estabelecimento`() {
        assertNull(MerchantIdentity.identify("Aviso", "Seu saldo foi atualizado."))
        assertNull(MerchantIdentity.identify("Compra", "Compra aprovada no cartao final 1234."))
        assertNull(MerchantIdentity.identify("", ""))
    }
    @Test fun `rotulos explicitos ficticios de diferentes emissores usam a mesma chave`() {
        // Nomes dos emissores dão variedade; estes textos NÃO são amostras bancárias reais.
        listOf("Nubank", "Inter", "Itaú", "Banco do Brasil", "Santander", "Caixa", "Bradesco", "PicPay").forEach { issuer ->
            assertEquals(issuer, "padaria sao joao", MerchantIdentity.identify(issuer,
                "Compra aprovada. Estabelecimento: Padaria São João; valor R$ 12,34.")?.key)
        }
        assertEquals("farmacia bairro", MerchantIdentity.identify("Exemplo fictício",
            "Pagamento realizado. Local: Farmácia Bairro; valor R$ 1.234,56.")?.key)
    }
    @Test fun `rotulo sem compra aprovada ou pagamento e ignorado`() {
        assertNull(MerchantIdentity.identify("Exemplo fictício", "Pix recebido. Local: Mercado Bairro."))
        assertNull(MerchantIdentity.identify("Promoção fictícia", "Estabelecimento: Mercado Bairro."))
        assertNull(MerchantIdentity.identify("Exemplo fictício", "Compra recusada. Local: Mercado Bairro."))
        assertNull(MerchantIdentity.identify("Compra aprovada", "Estabelecimento: 123456; valor R$ 20,00."))
    }
    @Test fun `politica grava correcao, esquece retorno a sugestao e ignora repeticao`() {
        assertEquals(MerchantCategoryPolicy.Change.Remember(Category.FOOD), MerchantCategoryPolicy.change(Category.OTHER, Category.FOOD, null))
        assertEquals(MerchantCategoryPolicy.Change.None, MerchantCategoryPolicy.change(Category.OTHER, Category.FOOD, Category.FOOD))
        assertEquals(MerchantCategoryPolicy.Change.Forget, MerchantCategoryPolicy.change(Category.OTHER, Category.OTHER, Category.FOOD))
        assertEquals(MerchantCategoryPolicy.Change.None, MerchantCategoryPolicy.change(Category.OTHER, Category.OTHER, null))
    }
}

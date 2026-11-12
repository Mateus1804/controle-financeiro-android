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
    @Test fun `politica grava correcao, esquece retorno a sugestao e ignora repeticao`() {
        assertEquals(MerchantCategoryPolicy.Change.Remember(Category.FOOD), MerchantCategoryPolicy.change(Category.OTHER, Category.FOOD, null))
        assertEquals(MerchantCategoryPolicy.Change.None, MerchantCategoryPolicy.change(Category.OTHER, Category.FOOD, Category.FOOD))
        assertEquals(MerchantCategoryPolicy.Change.Forget, MerchantCategoryPolicy.change(Category.OTHER, Category.OTHER, Category.FOOD))
        assertEquals(MerchantCategoryPolicy.Change.None, MerchantCategoryPolicy.change(Category.OTHER, Category.OTHER, null))
    }
}

package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class ExpenseLegendTest {
    private fun report(vararg cents: Long): ExpenseReport {
        val total = cents.sum()
        val groups = cents.mapIndexed { i, c -> ExpenseGroup("g$i", "Grupo $i", c, (c * 10000 / total).toInt()) }
        return ExpenseReport(total, 0, groups)
    }

    @Test fun `ate seis grupos aparecem todos sem Outros`() {
        val legend = ExpenseLegend.of(report(600, 500, 400, 300, 200, 100))
        assertFalse(legend.hasOthers); assertEquals(6, legend.visible.size)
    }
    @Test fun `acima de seis grupos os menores viram Outros preservando o total`() {
        val source = report(700, 600, 500, 400, 300, 200, 100)
        val legend = ExpenseLegend.of(source)
        assertTrue(legend.hasOthers)
        assertEquals(ExpenseLegend.OTHERS_KEY, legend.visible.last().key)
        assertEquals(300L, legend.visible.last().cents)
        assertEquals(source.total, legend.visible.sumOf { it.cents })
        assertEquals(source.groups.sumOf { it.basisPoints }, legend.visible.sumOf { it.basisPoints })
    }
    @Test fun `legenda sai ordenada por valor mesmo com entrada desordenada`() {
        val legend = ExpenseLegend.of(ExpenseReport(600, 0, listOf(ExpenseGroup("a", "A", 100, 1667), ExpenseGroup("b", "B", 500, 8333))))
        assertEquals(listOf("b", "a"), legend.visible.map { it.key })
    }
    @Test fun `categoria unica ocupa cem por cento e periodo vazio nao tem legenda`() {
        assertEquals(10000, ExpenseLegend.of(report(900)).visible.single().basisPoints)
        assertTrue(ExpenseLegend.of(ExpenseReport(0, 0, emptyList())).visible.isEmpty())
    }
    @Test fun `percentual formata e protege fatias minusculas`() {
        assertEquals("12,05%", ExpenseLegendText.percentage(1205))
        assertEquals("100,00%", ExpenseLegendText.percentage(10000))
        assertEquals("<0,01%", ExpenseLegendText.percentage(0))
    }
}

package com.mateuswilliam.financas.ui

import org.junit.Assert.*
import org.junit.Test

/** Geometria de desenho; os centavos e percentuais do relatório não passam por este ajuste. */
class ExpenseSliceLayoutTest {
    @Test fun tinySlicesKeepVisibleSpaceAndTheRingCloses() {
        val angles = expenseSliceAngles(listOf(999998, 1, 1))
        assertEquals(3, angles.size)
        assertTrue("Fatias pequenas precisam sobreviver ao vão de 1,6 grau", angles.all { it >= 4f })
        assertEquals(360.0, angles.sum().toDouble(), 0.001)
        assertTrue(angles.first() > angles.last())
    }
    @Test fun emptyAndSingleGroupDoNotInventDistribution() {
        assertTrue(expenseSliceAngles(emptyList()).isEmpty())
        assertEquals(listOf(360f), expenseSliceAngles(listOf(1)))
        assertEquals(listOf(180f, 180f), expenseSliceAngles(listOf(100, 100)))
    }
}

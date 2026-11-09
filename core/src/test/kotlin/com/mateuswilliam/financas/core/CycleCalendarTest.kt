package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CycleCalendarTest {
    private fun date(value:String)=LocalDate.parse(value)
    @Test fun fifthCrossesMonthAndYearWithHalfOpenBoundaries() {
        val rules=listOf(CycleRule(date("1970-01-05"),5))
        val period=CycleCalendar.periodContaining(date("2026-11-08"),rules)
        assertEquals(BudgetPeriod(date("2026-11-05"),date("2026-12-05")),period)
        assertTrue(date("2026-11-05") in period)
        assertTrue(date("2026-12-02") in period)
        assertFalse(date("2026-12-05") in period)
        assertEquals(date("2027-01-05"),CycleCalendar.periodContaining(date("2026-12-31"),rules).endExclusive)
    }
    @Test fun clampedMonthsRecoverChosenDay() {
        for(day in 29..31) {
            val rules=listOf(CycleRule(date("1970-01-01"),day))
            val feb=CycleCalendar.periodContaining(date("2027-02-28"),rules)
            assertEquals(date("2027-02-28"),feb.start)
            assertEquals(LocalDate.of(2027,3,day),feb.endExclusive)
            assertEquals(date("2028-02-29"),CycleCalendar.periodContaining(date("2028-02-29"),rules).start)
        }
    }
    @Test fun transitionAtExistingBoundaryPreservesHistoryAndHasNoGaps() {
        val old=listOf(CycleRule(date("1970-01-05"),5))
        val changed=old+CycleRule(date("2026-12-05"),15)
        assertEquals(CycleCalendar.periodContaining(date("2026-11-20"),old),CycleCalendar.periodContaining(date("2026-11-20"),changed))
        val transition=CycleCalendar.periodContaining(date("2026-12-05"),changed)
        assertEquals(BudgetPeriod(date("2026-12-05"),date("2026-12-15")),transition)
        assertEquals(transition.endExclusive,CycleCalendar.periodContaining(transition.endExclusive,changed).start)
    }
}

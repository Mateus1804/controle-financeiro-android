package com.mateuswilliam.financas.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val date = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
    private fun record(id: String, movement: Movement, amount: Long) =
        Record(id, amount, movement, PaymentMethod.PIX, Category.FOOD, "Exemplo", "Exemplo", date)

    @Test fun `transferencias nao alteram o resultado mensal`() {
        val summary = Ledger.month(listOf(record("1", Movement.INCOME, 10000), record("2", Movement.EXPENSE, 3000), record("3", Movement.TRANSFER, 9000)), YearMonth.of(2026, 10), zone)
        assertEquals(10000L, summary.income); assertEquals(3000L, summary.expenses); assertEquals(7000L, summary.result)
    }
    @Test fun `estornos reduzem despesas sem inflar receitas`() {
        val summary = Ledger.month(listOf(record("1", Movement.EXPENSE, 3000), record("2", Movement.REFUND, 1000)), YearMonth.of(2026, 10), zone)
        assertEquals(0L, summary.income); assertEquals(2000L, summary.expenses); assertEquals(2000L, summary.categories[Category.FOOD])
    }
    @Test fun `inicio do mes respeita o fuso do aparelho`() {
        val boundary = record("1", Movement.INCOME, 100).copy(occurredAt = Instant.parse("2026-11-01T01:00:00Z").toEpochMilli())
        assertEquals(100L, Ledger.month(listOf(boundary), YearMonth.of(2026, 10), zone).income)
        assertEquals(0L, Ledger.month(listOf(boundary), YearMonth.of(2026, 11), zone).income)
    }
    @Test fun `csv preserva texto aspas acentos e quebras de linha`() {
        val csv = Ledger.csv(listOf(record("1", Movement.EXPENSE, 1099).copy(description = "Pão; \"café\"\nextra")), zone)
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("\"Pão; \"\"café\"\"\nextra\"")); assertTrue(csv.contains("10,99"))
    }
    @Test fun `csv neutraliza formulas nos campos de texto`() {
        val csv = Ledger.csv(listOf(record("1", Movement.EXPENSE, 100).copy(description = "  =HYPERLINK(\"x\")", bank = "@SUM(A1)")), zone)
        assertTrue(csv.contains("'  =HYPERLINK")); assertTrue(csv.contains("'@SUM"))
    }
    @Test fun `historico vazio tem totais zero e csv com cabecalho`() {
        assertEquals(0L, Ledger.month(emptyList(), YearMonth.of(2026, 10), zone).result)
        assertEquals(2, Ledger.csv(emptyList(), zone).split("\r\n").size)
    }
}

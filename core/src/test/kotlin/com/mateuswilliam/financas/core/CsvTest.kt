package com.mateuswilliam.financas.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun `csv preserva acentos delimitadores aspas e quebras de linha`() {
        val rows = listOf(
            listOf("Descrição", "Valor previsto BRL"),
            listOf("Pão; \"café\"\nextra", "10,99"),
        )

        assertEquals(
            "\uFEFF\"Descrição\";\"Valor previsto BRL\"\r\n\"Pão; \"\"café\"\"\nextra\";\"10,99\"\r\n",
            encodeCsv(rows),
        )
    }

    @Test fun `csv neutraliza formulas mesmo apos espacos em branco`() {
        val rows = listOf(listOf("  =HYPERLINK(\"x\")", "@SUM(A1)", "\t+1", "\r-2", "texto"))

        assertEquals(
            "\uFEFF\"'  =HYPERLINK(\"\"x\"\")\";\"'@SUM(A1)\";\"'\t+1\";\"'\r-2\";\"texto\"\r\n",
            encodeCsv(rows),
        )
    }
}

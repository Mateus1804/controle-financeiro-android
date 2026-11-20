package com.mateuswilliam.financas.core

fun encodeCsv(rows: List<List<String>>): String {
    fun cell(value: String): String {
        // Planilhas podem executar fórmulas mesmo em células entre aspas.
        val safe = when (value.trimStart().firstOrNull()) {
            '=', '+', '-', '@' -> "'$value"
            else -> value
        }
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    return "\uFEFF" + rows.joinToString("\r\n") { it.joinToString(";", transform = ::cell) } + "\r\n"
}

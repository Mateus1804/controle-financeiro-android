package com.mateuswilliam.financas.data

enum class Appearance(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro");

    fun usesDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredName(name: String?): Appearance = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

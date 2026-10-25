package com.mateuswilliam.financas.core

import java.util.Locale

object AccountNames {
    private val spaces = Regex("[\\s\\p{Z}]+")
    fun clean(name: String): String = name.trim().replace(spaces, " ")
    fun key(name: String): String = clean(name).lowercase(Locale.ROOT)
}

data class AccountOption(val key: String, val label: String, val lastUsedAt: Long)

object AccountCatalog {
    fun options(saved: List<AccountOption>, records: List<Record>): List<AccountOption> {
        return merge(saved, records.flatMap { listOf(AccountOption("", it.bank, it.occurredAt), AccountOption("", it.destination, it.occurredAt)) })
    }

    fun merge(saved: List<AccountOption>, used: List<AccountOption>): List<AccountOption> {
        val options = linkedMapOf<String, AccountOption>()
        fun include(name: String, time: Long) {
            val key = AccountNames.key(name)
            if (key.isBlank()) return
            val previous = options[key]
            options[key] = AccountOption(key, previous?.label ?: AccountNames.clean(name), maxOf(previous?.lastUsedAt ?: 0, time))
        }
        saved.forEach { include(it.label, it.lastUsedAt) }
        used.forEach { include(it.label, it.lastUsedAt) }
        return options.values.sortedWith(compareByDescending<AccountOption> { it.lastUsedAt }.thenBy { it.key })
    }
}

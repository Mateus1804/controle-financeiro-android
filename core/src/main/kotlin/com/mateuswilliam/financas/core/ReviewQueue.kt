package com.mateuswilliam.financas.core

object ReviewQueue {
    fun snapshot(ids: List<String>, first: String): List<String> {
        val distinct = ids.distinct()
        val index = distinct.indexOf(first)
        return if (index < 0) listOf(first) else distinct.drop(index) + distinct.take(index)
    }

    fun next(ids: List<String>, current: String, pending: Set<String>): String? =
        ids.drop(ids.indexOf(current).let { if (it < 0) ids.size else it + 1 }).firstOrNull { it in pending }
}

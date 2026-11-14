package com.mateuswilliam.financas.ui

internal const val MIN_EXPENSE_SLICE_DEGREES = 4f

/** Ajusta apenas o desenho: reserva arco visível e retira a diferença das fatias maiores. */
internal fun expenseSliceAngles(values: List<Long>): List<Float> {
    val total = values.sumOf { it.toDouble() }
    if (total <= 0) return emptyList()
    val original = values.map { (it / total * 360).toFloat() }
    val minimum = minOf(MIN_EXPENSE_SLICE_DEGREES, 360f / values.size)
    val needed = original.sumOf { (minimum - it).coerceAtLeast(0f).toDouble() }
    val movable = original.sumOf { (it - minimum).coerceAtLeast(0f).toDouble() }
    return original.map { angle ->
        if (angle < minimum) minimum
        else if (movable > 0) (angle - (angle - minimum) * needed / movable).toFloat()
        else angle
    }
}

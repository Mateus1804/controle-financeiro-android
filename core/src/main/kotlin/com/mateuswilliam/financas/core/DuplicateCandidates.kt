package com.mateuswilliam.financas.core

import java.text.Normalizer
import java.util.Locale

data class DuplicateCandidate(val record: Record, val reasons: List<String>)

/** Índice somente dos lançamentos manuais. Um candidato nunca altera ou elimina dados. */
class DuplicateCandidates(manualRecords: List<Record>) {
    private val byAmountAndMovement = manualRecords.groupBy { it.amountCents to it.movement }

    fun find(proposed: Record, limit: Int = 3): List<DuplicateCandidate> {
        if (proposed.amountCents <= 0 || proposed.movement == Movement.UNKNOWN || limit <= 0) return emptyList()
        val description = tokens(proposed.description)
        return byAmountAndMovement[proposed.amountCents to proposed.movement].orEmpty().asSequence()
            .filter { it.id != proposed.id && kotlin.math.abs(it.occurredAt - proposed.occurredAt) <= WINDOW_MILLIS }
            .mapNotNull { record ->
                val sameAccount = AccountNames.key(record.bank) == AccountNames.key(proposed.bank)
                val sameBank = bankHint(record.bank)?.let { it == bankHint(proposed.bank) } == true
                val other = tokens(record.description)
                val overlap = description.intersect(other).size
                val union = description.union(other).size
                val similarText = overlap >= 2 && union > 0 && overlap * 2 >= union
                if (!sameAccount && !sameBank && !similarText) null
                else DuplicateCandidate(record, buildList {
                    add("Mesmo valor e tipo, em até 48 horas")
                    if (sameAccount) add("Mesma conta informada") else if (sameBank) add("Banco semelhante; a conta pode ser diferente")
                    if (similarText) add("Descrição semelhante")
                })
            }.sortedWith(compareByDescending<DuplicateCandidate> { it.reasons.size }
                .thenBy { kotlin.math.abs(it.record.occurredAt - proposed.occurredAt) }.thenBy { it.record.id })
            .take(limit).toList()
    }

    companion object {
        const val WINDOW_MILLIS = 48 * 60 * 60 * 1000L
        private val stop = setOf("de", "da", "do", "das", "dos", "em", "no", "na", "para", "por", "com", "um", "uma", "voce", "pix", "enviado", "recebido", "compra", "aprovada", "realizada", "pagamento", "efetuado", "realizado", "cartao", "valor", "reais", "brl")
        private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        private fun tokens(value: String) = Regex("[a-z]{2,}").findAll(normalize(value)).map { it.value }.filter { it !in stop }.toSet()
        private fun bankHint(value: String): String? {
            val words = tokens(value)
            return listOf("inter", "nubank", "picpay", "mercado", "pagbank", "pagseguro").firstOrNull { it in words }
                ?.let { if (it == "pagseguro") "pagbank" else it }
        }
    }
}

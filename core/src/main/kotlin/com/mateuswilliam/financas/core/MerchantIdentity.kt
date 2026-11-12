package com.mateuswilliam.financas.core

import java.text.Normalizer

/** Estabelecimento reconhecido em uma notificação: [key] estável para comparar, [label] para exibir. */
data class Merchant(val key: String, val label: String)

object MerchantIdentity {
    // Só aceita padrões explícitos de compra/pagamento; sem padrão claro não há estabelecimento.
    private val patterns = listOf(
        Regex("(?:compra|pagamento|debito|credito)\\b[^.\\n]{0,40}?\\b(?:em|no|na|para)\\s+([^.\\n,;]{3,60})", RegexOption.IGNORE_CASE),
        Regex("voce pagou\\s+(?:r\\$\\s*[\\d.,]+\\s+)?(?:para|a|em)\\s+([^.\\n,;]{3,60})", RegexOption.IGNORE_CASE),
    )
    private val tail = Regex("\\s+(?:no valor|valor|r\\$|em \\d{1,2}/|as \\d|via |com final|cartao|aprovad|realizad|efetuad).*$", RegexOption.IGNORE_CASE)
    private val generic = setOf("conta", "cartao", "credito", "debito", "pix", "sua conta", "seu cartao", "loja", "estabelecimento")

    private val genericStart = setOf("cartao", "conta", "seu", "sua", "final")

    fun identify(title: String, body: String): Merchant? {
        val text = "$title. $body".replace(Regex("\\s+"), " ")
        for (pattern in patterns) {
            val raw = pattern.find(normalizeAccents(text))?.groupValues?.get(1) ?: continue
            val label = raw.replace(tail, "").trim(' ', '-', ':', '*')
            val key = label.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()
            if (key.length >= 3 && key !in generic && key.substringBefore(' ') !in genericStart && key.any { it.isLetter() }) return Merchant(key, label.take(60))
        }
        return null
    }

    private fun normalizeAccents(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** Decide o que gravar quando o usuário confirma uma compra: corrigiu a sugestão, voltou a ela ou nada mudou. */
object MerchantCategoryPolicy {
    sealed interface Change { data class Remember(val category: Category) : Change; data object Forget : Change; data object None : Change }

    fun change(suggestedByParser: Category, confirmed: Category, remembered: Category?): Change = when {
        confirmed != suggestedByParser -> if (confirmed == remembered) Change.None else Change.Remember(confirmed)
        remembered != null -> Change.Forget
        else -> Change.None
    }
}

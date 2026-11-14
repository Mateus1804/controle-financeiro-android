package com.mateuswilliam.financas.data

import android.content.Context
import androidx.core.content.edit
import com.mateuswilliam.financas.core.Category

/** Preferência local (fora do banco e do backup): categoria escolhida por estabelecimento. */
class MerchantCategoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("merchant_categories", Context.MODE_PRIVATE)

    fun get(key: String): Category? = preferences.getString(key, null)?.let { name -> Category.entries.firstOrNull { it.name == name } }

    fun remember(key: String, category: Category) {
        if (preferences.all.size >= MAX_ENTRIES && !preferences.contains(key)) return
        preferences.edit { putString(key, category.name) }
    }

    fun entries(): List<MerchantCategoryEntry> = preferences.all.mapNotNull { (key, value) ->
        val category = Category.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        MerchantCategoryEntry(key, category)
    }.sortedBy { it.key }

    fun forget(key: String) = preferences.edit { remove(key) }
    fun clear() = preferences.edit { clear() }

    private companion object { const val MAX_ENTRIES = 500 }
}

data class MerchantCategoryEntry(val key: String, val category: Category)

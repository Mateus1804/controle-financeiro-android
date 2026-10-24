package com.mateuswilliam.financas.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/** Simula falha confirmada de persistência, sem alterar as preferências de uso do aplicativo. */
internal class FailingPreferencesContext(context: Context, private val isolated: SharedPreferences, private var successfulCommits: Int = 0) : ContextWrapper(context) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = object : SharedPreferences by isolated {
        override fun edit(): SharedPreferences.Editor {
            val editor = isolated.edit()
            return object : SharedPreferences.Editor by editor {
                override fun commit(): Boolean = if (successfulCommits-- > 0) editor.commit() else false
            }
        }
    }
}

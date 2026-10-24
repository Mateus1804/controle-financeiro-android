package com.mateuswilliam.financas.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CaptureSettings(
    val consented: Boolean = false,
    val enabled: Boolean = false,
    val packages: Set<String> = emptySet(),
    val hideAmounts: Boolean = false,
    val appearance: Appearance = Appearance.SYSTEM,
)

class CapturePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state: StateFlow<CaptureSettings> = mutable
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = CaptureSettings(
        preferences.getBoolean("disclosure_v1", false),
        preferences.getBoolean("enabled", false),
        preferences.getStringSet("packages", emptySet()).orEmpty().toSet(),
        preferences.getBoolean("hide_amounts", false),
        Appearance.fromStoredName(preferences.getString("appearance", null)),
    )

    fun acceptDisclosure() { preferences.edit { putBoolean("disclosure_v1", true); putBoolean("enabled", true) } }
    fun setEnabled(enabled: Boolean) { preferences.edit { putBoolean("enabled", enabled && read().consented) } }
    fun selectPackages(packages: Set<String>) { preferences.edit { putStringSet("packages", packages.toSet()) } }
    fun hideAmounts(hide: Boolean) { preferences.edit { putBoolean("hide_amounts", hide) } }
    fun setAppearance(appearance: Appearance) { preferences.edit { putString("appearance", appearance.name) } }
    fun permits(packageName: String): Boolean = read().let { it.consented && it.enabled && packageName in it.packages }
    // Persiste a revogação antes de concluir a exclusão. O repositório chama esta operação em IO.
    fun reset() { preferences.edit(commit = true) { clear() }; mutable.value = read() }
}

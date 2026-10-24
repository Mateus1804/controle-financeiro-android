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
    val lastOrigin: String = "",
    val introSeen: Boolean = false,
    val captureCondition: String = "",
    val dismissedCondition: String = "",
    val remindersEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
    val secureScreen: Boolean = false,
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
        preferences.getString("last_origin", "").orEmpty(),
        preferences.getBoolean("intro_seen", false),
        preferences.getString("capture_condition", "").orEmpty(),
        preferences.getString("dismissed_condition", "").orEmpty(),
        preferences.getBoolean("reminders_enabled", false),
        preferences.getBoolean("lock_enabled", false),
        preferences.getBoolean("secure_screen", false),
    )

    fun acceptDisclosure() { preferences.edit { putBoolean("disclosure_v1", true); putBoolean("enabled", true) } }
    fun setEnabled(enabled: Boolean) { preferences.edit { putBoolean("enabled", enabled && read().consented) } }
    fun selectPackages(packages: Set<String>) { preferences.edit { putStringSet("packages", packages.toSet()) } }
    fun hideAmounts(hide: Boolean) { preferences.edit { putBoolean("hide_amounts", hide) } }
    fun setAppearance(appearance: Appearance) { preferences.edit { putString("appearance", appearance.name) } }
    fun rememberOrigin(name: String) { preferences.edit { putString("last_origin", name.trim()) } }
    fun finishIntro() { preferences.edit { putBoolean("intro_seen", true) } }
    fun observeCaptureCondition(condition: String) {
        if (read().captureCondition != condition) preferences.edit {
            putString("capture_condition", condition); remove("dismissed_condition")
        }
    }
    fun dismissCaptureNotice(condition: String) { preferences.edit { putString("dismissed_condition", condition) } }
    fun snapshot(): CaptureSettings = read()
    fun setReminders(enabled: Boolean) { preferences.edit { putBoolean("reminders_enabled", enabled) }; mutable.value = read() }
    fun setSecureScreen(enabled: Boolean) { preferences.edit { putBoolean("secure_screen", enabled) }; mutable.value = read() }
    fun setLockEnabled(enabled: Boolean) {
        preferences.edit(commit = true) { putBoolean("lock_enabled", enabled); if (enabled) putBoolean("secure_screen", true) }
        mutable.value = read()
    }
    fun pauseCapture() { preferences.edit(commit = true) { putBoolean("enabled", false) }; mutable.value = read() }
    fun restoreDisplay(display: BackupDisplay) {
        preferences.edit(commit = true) {
            putString("appearance", display.appearance.name); putBoolean("hide_amounts", display.hideAmounts)
            putString("last_origin", display.lastOrigin); putBoolean("enabled", false)
            putBoolean("reminders_enabled", false); remove("capture_condition"); remove("dismissed_condition")
        }
        mutable.value = read()
    }
    fun permits(packageName: String): Boolean = read().let { it.consented && it.enabled && packageName in it.packages }
    // Persiste a revogação antes de concluir a exclusão. O repositório chama esta operação em IO.
    fun reset() { preferences.edit(commit = true) { clear() }; mutable.value = read() }
}

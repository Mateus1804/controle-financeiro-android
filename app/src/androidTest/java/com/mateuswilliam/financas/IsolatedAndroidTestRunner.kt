package com.mateuswilliam.financas

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** A suíte limpa a base: a execução automatizada fica restrita ao emulador dedicado. */
class IsolatedAndroidTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        val emulator = Build.HARDWARE in setOf("ranchu", "goldfish", "vbox86") || Build.FINGERPRINT.startsWith("generic")
        if (!emulator) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", "Testes não iniciados: use um emulador dedicado, sem dados reais. A suíte reinicia a base de desenvolvimento.")
            })
            return
        }
        super.onStart()
    }
}

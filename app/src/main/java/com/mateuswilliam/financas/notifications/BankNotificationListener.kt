package com.mateuswilliam.financas.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.core.NotificationInput
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {
    private val app get() = application as FinanceApplication

    override fun onListenerConnected() {
        app.listenerConnected.value = true
        val settings = app.settings.state.value
        if (settings.consented && settings.enabled && settings.packages.isNotEmpty()) app.settings.observeCaptureCondition("READY")
    }
    override fun onListenerDisconnected() { app.listenerConnected.value = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // A seleção é aplicada antes de ler título e texto. O app nunca processa os próprios alertas.
        if (sbn.packageName == packageName || !app.settings.permits(sbn.packageName)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val input = runCatching {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(160)
            val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.take(8)?.joinToString("\n")
                ?: "").take(2000)
            if (title.isBlank() && body.isBlank()) return
            val bank = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
                .getOrDefault(sbn.packageName).take(80)
            // A chave do evento distingue compras iguais e mantém atualizações do mesmo evento juntas.
            // Alguns bancos reutilizam identificadores; a compatibilidade exige teste no aparelho.
            val eventAt = sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime
            NotificationInput(sbn.packageName, "${sbn.packageName}|${sbn.key}|$eventAt", bank, title, body, eventAt)
        }.getOrNull() ?: return
        val isVisible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        app.background.launch {
            runCatching {
                val result = app.repository.capture(input)
                if (result?.isNew == true && !isVisible) ReviewNotifications.show(this@BankNotificationListener, result.id)
            }.onSuccess { app.captureError.value = false }.onFailure { app.captureError.value = true }
            // Conteúdo bancário não é enviado para logs. O serviço trata somente eventos futuros.
        }
    }

    override fun onDestroy() { app.listenerConnected.value = false; super.onDestroy() }
}

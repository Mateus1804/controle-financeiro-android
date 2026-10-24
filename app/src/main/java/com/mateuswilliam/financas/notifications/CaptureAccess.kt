package com.mateuswilliam.financas.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

fun hasCaptureAccess(context: Context): Boolean {
    val service = ComponentName(context, BankNotificationListener::class.java)
    if (Build.VERSION.SDK_INT >= 27) {
        return context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(service) == true
    }
    // No Android 8.0, consulto a configuração atual sem reutilizar o cache por pacote.
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabled?.split(':')?.any { ComponentName.unflattenFromString(it) == service } == true
}

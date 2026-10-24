package com.mateuswilliam.financas.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.R

object ReviewNotifications {
    const val EXTRA_DRAFT_ID = "review_draft_id"
    private const val CHANNEL = "review_transactions"
    private const val NOTIFICATION_ID = 101

    fun show(context: Context, draftId: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Movimentações para revisar", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java)
            .setAction("com.mateuswilliam.financas.REVIEW.$draftId")
            .putExtra(EXTRA_DRAFT_ID, draftId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nova notificação para revisar")
            .setContentText("Confira os dados antes de registrar uma movimentação.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try { manager.notify(draftId, NOTIFICATION_ID, notification) } catch (_: SecurityException) {
            // A autorização pode ser revogada entre a checagem e a publicação. A pendência já está salva.
        }
    }

    fun cancel(context: Context, id: String) { context.getSystemService(NotificationManager::class.java).cancel(id, NOTIFICATION_ID) }
    fun cancelAll(context: Context) { context.getSystemService(NotificationManager::class.java).cancelAll() }
}

package com.mateuswilliam.financas.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.RecurringPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import java.time.LocalDate

object RecurringReminders {
    const val EXTRA_PLANNING = "open_planning"
    private const val JOB_ID = 2301
    private const val CHANNEL = "planned_transactions"
    private const val NOTIFICATION_ID = 2302

    @Synchronized
    fun schedule(context: Context) {
        // O coletor pode estar atrasado. A preferência atual prevalece sobre qualquer emissão antiga.
        val enabled = (context.applicationContext as FinanceApplication).settings.snapshot().remindersEnabled
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled) { scheduler.cancel(JOB_ID); return }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, RecurringReminderService::class.java))
            .setPeriodic(6 * 60 * 60 * 1000L).setPersisted(true).build())
    }

    fun show(context: Context, plan: RecurringPlan): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Contas e receitas previstas", NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val pending = PendingIntent.getActivity(context, 2302,
            Intent(context, MainActivity::class.java).setAction("com.mateuswilliam.financas.PLANNING")
                .putExtra(EXTRA_PLANNING, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Há uma movimentação prevista para conferir")
            .setContentText("Abra o planejamento. Uma previsão só entra nos totais após confirmação.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pending).build()
        return try { manager.notify("plan:${plan.id}:${plan.nextDate}", NOTIFICATION_ID, notification); true }
            catch (_: SecurityException) { false }
    }
    fun cancel(context: Context, id: String, due: String) {
        context.getSystemService(NotificationManager::class.java).cancel("plan:$id:$due", NOTIFICATION_ID)
    }
}

class RecurringReminderService : JobService() {
    private var job: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as FinanceApplication
        job = app.background.launch {
            var retry = false
            try { app.repository.notifyDue(LocalDate.now()) { RecurringReminders.show(this@RecurringReminderService, it) } }
            catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { retry = true }
            finally { if (isActive) jobFinished(params, retry) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); return true }
}

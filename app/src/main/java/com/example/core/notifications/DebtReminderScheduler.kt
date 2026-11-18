package com.example.core.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.DebtStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DebtReminderScheduler(private val context: Context) {
    fun schedule(debtId: String, title: String, person: String, dueEpochMs: Long?) {
        cancel(debtId)
        if (dueEpochMs == null) return
        val dueDate = Instant.ofEpochMilli(dueEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val triggerAt = dueDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (dueDate.isBefore(LocalDate.now())) return
        val scheduledAt = if (triggerAt <= System.currentTimeMillis()) System.currentTimeMillis() + 60_000L else triggerAt
        val intent = Intent(context, DebtReminderReceiver::class.java).apply {
            putExtra(EXTRA_DEBT_ID, debtId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PERSON, person)
        }
        val pending = PendingIntent.getBroadcast(
            context, debtId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pending)
    }

    fun cancel(debtId: String) {
        val intent = Intent(context, DebtReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, debtId.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }

    companion object {
        const val CHANNEL_ID = "debt_due_reminders"
        const val EXTRA_DEBT_ID = "debt_id"
        const val EXTRA_TITLE = "debt_title"
        const val EXTRA_PERSON = "debt_person"

        fun showReminder(context: Context, debtId: String, title: String, person: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Debt due reminders", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val openAppIntent = PendingIntent.getActivity(
                context,
                debtId.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openAppIntent)
                .setContentTitle("Debt due today")
                .setContentText("$title • $person")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title with $person is due today. Open FinVault to review the balance."))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(debtId.hashCode(), notification)
            } catch (_: SecurityException) {
                // Notification permission can be denied; the due date remains visible in-app.
            }
        }
    }
}

class DebtReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(DebtReminderScheduler.EXTRA_DEBT_ID) ?: return
        val title = intent.getStringExtra(DebtReminderScheduler.EXTRA_TITLE) ?: "Debt"
        val person = intent.getStringExtra(DebtReminderScheduler.EXTRA_PERSON) ?: ""
        DebtReminderScheduler.showReminder(context, id, title, person)
    }
}

class DebtReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = FinVaultDatabase.create(context)
                val debts = db.debtDao().observeActiveDebts().first()
                val scheduler = DebtReminderScheduler(context)
                debts.filter { it.status == DebtStatus.ACTIVE }.forEach {
                    scheduler.schedule(it.id, it.title, it.personName, it.dueEpochMs)
                }
                db.close()
            } finally {
                pending.finish()
            }
        }
    }
}

package com.likkho.nativeapp.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.likkho.nativeapp.LikkhoApp
import com.likkho.nativeapp.MainActivity
import com.likkho.nativeapp.data.local.LikkhoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val logId = intent.getStringExtra("logId") ?: return
        val title = intent.getStringExtra("title") ?: "Diary Reminder"
        val body = intent.getStringExtra("body") ?: "Time to check your diary entry."
        val notifId = intent.getIntExtra("notifId", (logId.hashCode() and 0x7FFFFFFF))

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_log_id", logId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, LikkhoApp.REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notifId, notification)

        // Mark reminderFired in Room Database
        val db = LikkhoDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            db.diaryDao().getLogById(logId)?.let { current ->
                db.diaryDao().upsertLog(current.copy(reminderFired = true))
            }
        }
    }
}

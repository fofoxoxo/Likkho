package com.likkho.nativeapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.likkho.nativeapp.data.local.LikkhoDatabase

class LikkhoApp : Application() {

    val database by lazy { LikkhoDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val micChannel = NotificationChannel(
                MIC_CHANNEL_ID,
                "Microphone Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps microphone recording active while app is in background"
                setShowBadge(false)
            }

            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Diary Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled diary reminder notifications"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(micChannel)
            manager?.createNotificationChannel(reminderChannel)
        }
    }

    companion object {
        const val MIC_CHANNEL_ID = "likkho_mic_recording_channel"
        const val REMINDER_CHANNEL_ID = "likkho_diary_reminders_channel"
    }
}

package com.likkho.diary.nativeapp.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.likkho.diary.nativeapp.NativeMainActivity
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.ReminderRepeat
import com.likkho.diary.nativeapp.model.StandaloneReminder
import com.likkho.diary.nativeapp.model.VaultMode
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Exact `AlarmManager` scheduler & `BroadcastReceiver` handler for Likkho Reminders.
 *
 * - Supports Everyday (`StandaloneReminder`) schedules (`Once`, `Daily`, `Weekly`, `Monthly`)
 *   and legacy `DiaryLog` reminder notifications.
 * - Uses `java.util.Calendar` (`minSdk = 24` safe, zero `java.time` usage).
 * - Automatically advances repeating reminders on trigger & after device reboot (`BOOT_COMPLETED`).
 */
object ReminderAlarmScheduler {

    const val CHANNEL_ID = "likkho_reminders_channel"
    const val ACTION_REMINDER_ALARM = "com.likkho.diary.ACTION_REMINDER_ALARM"
    const val ACTION_REMINDER_DONE = "com.likkho.diary.ACTION_REMINDER_DONE"
    const val ACTION_REMINDER_SNOOZE_10M = "com.likkho.diary.ACTION_REMINDER_SNOOZE_10M"
    const val ACTION_REMINDER_DISMISS = "com.likkho.diary.ACTION_REMINDER_DISMISS"

    const val EXTRA_REMINDER_ID = "reminderId"
    const val EXTRA_VAULT_MODE = "vaultMode"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    const val EXTRA_NOTIF_ID = "notifId"
    const val EXTRA_REPEAT = "repeat"
    const val EXTRA_IS_SNOOZE = "isSnooze"

    private const val SNOOZE_DURATION_MS = 10L * 60L * 1000L
    private const val RECEIVER_CLASS = "com.likkho.diary.ReminderReceiver"

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun requestCodeForReminder(reminderId: String): Int {
        val positiveHash = abs(reminderId.hashCode().toLong())
        return (positiveHash % 400_000L).toInt() + 100_000
    }

    fun requestCodeForSnooze(reminderId: String): Int {
        return requestCodeForReminder(reminderId) + 500_000
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * Advances [currentTriggerMs] strictly past [nowMs] according to [repeat].
     * Preserves target day-of-month cleanly across shorter months for `MONTHLY` schedules.
     */
    fun computeNextOccurrenceMs(
        currentTriggerMs: Long,
        repeat: ReminderRepeat,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        if (repeat == ReminderRepeat.NONE) return null
        val startMs = if (currentTriggerMs > 0L) currentTriggerMs else nowMs
        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        val targetDom = cal.get(Calendar.DAY_OF_MONTH)

        when (repeat) {
            ReminderRepeat.DAILY -> {
                val dayMs = 24L * 60L * 60L * 1000L
                val diff = nowMs - cal.timeInMillis
                if (diff > dayMs * 2L) {
                    val jumpDays = ((diff / dayMs) - 1L).toInt().coerceAtLeast(0)
                    if (jumpDays > 0) cal.add(Calendar.DAY_OF_YEAR, jumpDays)
                }
                var guard = 0
                while (cal.timeInMillis <= nowMs && guard++ < 4000) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            ReminderRepeat.WEEKLY -> {
                val weekMs = 7L * 24L * 60L * 60L * 1000L
                val diff = nowMs - cal.timeInMillis
                if (diff > weekMs * 2L) {
                    val jumpWeeks = ((diff / weekMs) - 1L).toInt().coerceAtLeast(0)
                    if (jumpWeeks > 0) cal.add(Calendar.WEEK_OF_YEAR, jumpWeeks)
                }
                var guard = 0
                while (cal.timeInMillis <= nowMs && guard++ < 1000) {
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            ReminderRepeat.MONTHLY -> {
                var guard = 0
                while (cal.timeInMillis <= nowMs && guard++ < 600) {
                    cal.add(Calendar.MONTH, 1)
                    val maxDom = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(targetDom, maxDom))
                }
            }

            ReminderRepeat.NONE -> return null
        }
        return cal.timeInMillis
    }

    fun scheduleReminder(
        context: Context,
        reminder: StandaloneReminder,
        mode: VaultMode = VaultMode.PRIMARY
    ) {
        val appCtx = context.applicationContext
        if (reminder.isCompleted) {
            cancelReminder(appCtx, reminder.id)
            return
        }
        val now = System.currentTimeMillis()
        if (reminder.triggerAtMs <= now - 60_000L) {
            // Do not immediately re-fire stale past one-off timestamps when toggling/re-syncing
            return
        }
        val effectiveTriggerMs = maxOf(reminder.triggerAtMs, now + 1500L)
        val reqCode = requestCodeForReminder(reminder.id)
        scheduleExactBroadcast(
            context = appCtx,
            requestCode = reqCode,
            triggerAtMs = effectiveTriggerMs,
            title = reminder.title.ifBlank { "Likkho Reminder" },
            body = reminder.note.ifBlank { "Scheduled reminder" },
            reminderId = reminder.id,
            repeat = reminder.repeat,
            vaultMode = mode,
            isSnooze = false
        )
    }

    fun scheduleSnooze(
        context: Context,
        reminder: StandaloneReminder,
        snoozeAtMs: Long,
        mode: VaultMode = VaultMode.PRIMARY
    ) {
        val appCtx = context.applicationContext
        val effectiveTrigger = maxOf(snoozeAtMs, System.currentTimeMillis() + 1500L)
        val isRepeating = reminder.repeat != ReminderRepeat.NONE
        val reqCode = if (isRepeating) {
            requestCodeForSnooze(reminder.id)
        } else {
            requestCodeForReminder(reminder.id)
        }
        scheduleExactBroadcast(
            context = appCtx,
            requestCode = reqCode,
            triggerAtMs = effectiveTrigger,
            title = reminder.title.ifBlank { "Likkho Reminder" },
            body = reminder.note.ifBlank { "Snoozed reminder" },
            reminderId = reminder.id,
            repeat = reminder.repeat,
            vaultMode = mode,
            isSnooze = isRepeating
        )
    }

    fun cancelReminder(context: Context, reminderId: String) {
        if (reminderId.isBlank()) return
        val appCtx = context.applicationContext
        val reqCode = requestCodeForReminder(reminderId)
        val snoozeCode = requestCodeForSnooze(reminderId)
        cancelBroadcast(appCtx, reqCode)
        cancelBroadcast(appCtx, snoozeCode)
        runCatching {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(reqCode)
            nm?.cancel(snoozeCode)
        }
    }

    fun scheduleDiaryReminder(
        context: Context,
        logId: String,
        title: String,
        body: String,
        triggerAtMs: Long
    ) {
        val reqCode = abs(logId.hashCode())
        scheduleExactBroadcast(
            context = context.applicationContext,
            requestCode = reqCode,
            triggerAtMs = triggerAtMs,
            title = title,
            body = body,
            reminderId = "",
            repeat = ReminderRepeat.NONE,
            vaultMode = VaultMode.DECOY,
            isSnooze = false
        )
    }

    fun cancelDiaryReminder(context: Context, logId: String) {
        val reqCode = abs(logId.hashCode())
        cancelBroadcast(context.applicationContext, reqCode)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleExactBroadcast(
        context: Context,
        requestCode: Int,
        triggerAtMs: Long,
        title: String,
        body: String,
        reminderId: String,
        repeat: ReminderRepeat,
        vaultMode: VaultMode,
        isSnooze: Boolean
    ) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent().apply {
                component = ComponentName(context, RECEIVER_CLASS)
                action = ACTION_REMINDER_ALARM
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_NOTIF_ID, requestCode)
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_REPEAT, repeat.wireValue)
                putExtra(EXTRA_VAULT_MODE, vaultMode.wireValue)
                putExtra(EXTRA_IS_SNOOZE, isSnooze)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                }
            } else {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                }
            }
        }
    }

    private fun cancelBroadcast(context: Context, requestCode: Int) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent().apply {
                component = ComponentName(context, RECEIVER_CLASS)
                action = ACTION_REMINDER_ALARM
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }

    /**
     * Re-registers all active upcoming reminders (and advances overdue repeating schedules).
     * Safe to invoke from IO threads on app launch, restore, or `BOOT_COMPLETED`.
     */
    fun rescheduleAllPendingAlarms(
        context: Context,
        mode: VaultMode = VaultMode.PRIMARY
    ): List<StandaloneReminder> {
        val appCtx = context.applicationContext
        val repository = NativeVaultRepository(appCtx)
        val existing = repository.loadReminders(mode)
        if (existing.isEmpty()) return existing

        val now = System.currentTimeMillis()
        var mutated = false
        val updated = existing.map { item ->
            if (item.isCompleted) {
                cancelReminder(appCtx, item.id)
                item
            } else if (item.triggerAtMs <= now && item.repeat != ReminderRepeat.NONE) {
                val nextAt = computeNextOccurrenceMs(item.triggerAtMs, item.repeat, now)
                if (nextAt != null) {
                    mutated = true
                    val advanced = item.copy(triggerAtMs = nextAt, updatedAt = now)
                    scheduleReminder(appCtx, advanced, mode)
                    advanced
                } else {
                    item
                }
            } else if (item.triggerAtMs > now) {
                scheduleReminder(appCtx, item, mode)
                item
            } else {
                item
            }
        }

        if (mutated) {
            val sorted = updated.sortedWith(
                compareBy<StandaloneReminder> { it.isCompleted }.thenBy { it.triggerAtMs }
            )
            repository.saveReminders(mode, sorted)
            return sorted
        }
        return existing
    }

    @JvmStatic
    fun handleBroadcastAsync(
        context: Context,
        intent: Intent?,
        pendingResult: BroadcastReceiver.PendingResult?
    ) {
        val appCtx = context.applicationContext
        val runResult = runCatching {
            backgroundExecutor.execute {
                try {
                    handleBroadcastInternal(appCtx, intent)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
        if (runResult.isFailure) {
            pendingResult?.finish()
        }
    }

    private fun handleBroadcastInternal(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) {
            rescheduleAllPendingAlarms(context, VaultMode.PRIMARY)
            notifyMainActivityRemindersChanged()
            return
        }

        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, (System.currentTimeMillis() and 0x7FFFFFFFL).toInt())
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        val vaultMode = VaultMode.fromWire(intent.getStringExtra(EXTRA_VAULT_MODE))

        when (action) {
            ACTION_REMINDER_DISMISS -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
                return
            }

            ACTION_REMINDER_DONE -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
                if (reminderId.isNotBlank()) {
                    val repo = NativeVaultRepository(context)
                    val now = System.currentTimeMillis()
                    val current = repo.loadReminders(vaultMode)
                    val target = current.firstOrNull { it.id == reminderId }
                    if (target != null) {
                        repo.upsertReminder(
                            vaultMode,
                            target.copy(
                                isCompleted = true,
                                completedAt = now,
                                updatedAt = now
                            )
                        )
                    }
                    cancelReminder(context, reminderId)
                    notifyMainActivityRemindersChanged()
                }
                return
            }

            ACTION_REMINDER_SNOOZE_10M -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)
                val now = System.currentTimeMillis()
                val snoozeAt = now + SNOOZE_DURATION_MS
                if (reminderId.isNotBlank()) {
                    val repo = NativeVaultRepository(context)
                    val current = repo.loadReminders(vaultMode)
                    val target = current.firstOrNull { it.id == reminderId }
                    if (target != null) {
                        if (target.repeat == ReminderRepeat.NONE) {
                            val updated = target.copy(
                                triggerAtMs = snoozeAt,
                                isCompleted = false,
                                completedAt = null,
                                updatedAt = now
                            )
                            repo.upsertReminder(vaultMode, updated)
                            scheduleReminder(context, updated, vaultMode)
                        } else {
                            scheduleSnooze(context, target, snoozeAt, vaultMode)
                        }
                        notifyMainActivityRemindersChanged()
                        return
                    }
                }
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Likkho Reminder"
                val body = intent.getStringExtra(EXTRA_BODY) ?: "Snoozed reminder"
                scheduleExactBroadcast(
                    context = context,
                    requestCode = notifId,
                    triggerAtMs = snoozeAt,
                    title = title,
                    body = body,
                    reminderId = reminderId,
                    repeat = ReminderRepeat.fromWire(intent.getStringExtra(EXTRA_REPEAT)),
                    vaultMode = vaultMode,
                    isSnooze = true
                )
                return
            }
        }

        // Standard Alarm Fire: post notification + advance repeating schedule if applicable
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        var title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Likkho Reminder"
        var body = intent.getStringExtra(EXTRA_BODY)?.takeIf { it.isNotBlank() } ?: "You have a scheduled reminder."
        var repeat = ReminderRepeat.fromWire(intent.getStringExtra(EXTRA_REPEAT))

        if (reminderId.isNotBlank()) {
            val repo = NativeVaultRepository(context)
            val current = repo.loadReminders(vaultMode)
            val target = current.firstOrNull { it.id == reminderId }
            if (target != null) {
                if (target.isCompleted) {
                    // Reminder was already completed before alarm fired
                    return
                }
                title = target.title.ifBlank { title }
                body = target.note.ifBlank { body }
                repeat = target.repeat

                if (!isSnooze && target.repeat != ReminderRepeat.NONE) {
                    val now = System.currentTimeMillis()
                    val nextTrigger = computeNextOccurrenceMs(target.triggerAtMs, target.repeat, now)
                    if (nextTrigger != null) {
                        val updated = target.copy(triggerAtMs = nextTrigger, updatedAt = now)
                        repo.upsertReminder(vaultMode, updated)
                        scheduleReminder(context, updated, vaultMode)
                        notifyMainActivityRemindersChanged()
                    }
                }
            }
        }

        postReminderNotification(
            context = context,
            notifId = notifId,
            title = title,
            body = body,
            reminderId = reminderId,
            repeat = repeat,
            vaultMode = vaultMode
        )
    }

    private fun postReminderNotification(
        context: Context,
        notifId: Int,
        title: String,
        body: String,
        reminderId: String,
        repeat: ReminderRepeat,
        vaultMode: VaultMode
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Likkho Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(soundUri, null)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, NativeMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Likkho Reminder")
            .setContentText("You have a scheduled reminder.")
            .build()

        val subtitleBody = if (repeat != ReminderRepeat.NONE) {
            "$body · Repeats ${repeat.displayLabel}"
        } else {
            body
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(subtitleBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitleBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (reminderId.isNotBlank()) {
            val snoozeIntent = Intent().apply {
                component = ComponentName(context, RECEIVER_CLASS)
                action = ACTION_REMINDER_SNOOZE_10M
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_REPEAT, repeat.wireValue)
                putExtra(EXTRA_VAULT_MODE, vaultMode.wireValue)
            }
            val snoozePi = PendingIntent.getBroadcast(
                context,
                notifId + 2_000_000,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Snooze 10m", snoozePi)

            if (repeat == ReminderRepeat.NONE) {
                val doneIntent = Intent().apply {
                    component = ComponentName(context, RECEIVER_CLASS)
                    action = ACTION_REMINDER_DONE
                    putExtra(EXTRA_NOTIF_ID, notifId)
                    putExtra(EXTRA_REMINDER_ID, reminderId)
                    putExtra(EXTRA_VAULT_MODE, vaultMode.wireValue)
                }
                val donePi = PendingIntent.getBroadcast(
                    context,
                    notifId + 1_000_000,
                    doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Mark Done", donePi)
            } else {
                val dismissIntent = Intent().apply {
                    component = ComponentName(context, RECEIVER_CLASS)
                    action = ACTION_REMINDER_DISMISS
                    putExtra(EXTRA_NOTIF_ID, notifId)
                }
                val dismissPi = PendingIntent.getBroadcast(
                    context,
                    notifId + 1_000_000,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Dismiss", dismissPi)
            }
        }

        nm.notify(notifId, builder.build())
    }

    private fun notifyMainActivityRemindersChanged() {
        mainHandler.post {
            NativeMainActivity.currentInstance?.refreshRemindersFromVault()
        }
    }
}

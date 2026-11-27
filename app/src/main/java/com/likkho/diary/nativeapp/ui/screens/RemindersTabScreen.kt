package com.likkho.diary.nativeapp.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.ReminderRepeat
import com.likkho.diary.nativeapp.model.StandaloneReminder
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.notifications.ReminderAlarmScheduler
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogOutlineButton
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogPrimaryButton
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineBorder
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineDialog
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class ReminderFilter(val label: String) {
    UPCOMING("Upcoming"),
    COMPLETED("Completed"),
    ALL("All")
}

/**
 * Everyday Tab 3: Native Reminders & Alarm Notifications.
 *
 * Features:
 * - `Upcoming` / `Completed` / `All` filters + live Search from [EverydayShellScreen]
 * - Overdue / Today / Repeat visual indicators
 * - Create / Edit / Delete modal with native Date & Time pickers, quick presets, and Repeat (`Once`, `Daily`, `Weekly`, `Monthly`)
 * - 1-tap Mark Done / Reopen & quick Snooze (`+10m`, `+30m`, `+1h`, `+3h`, `Tomorrow 9 AM`, or Custom)
 * - Exact `AlarmManager` scheduling & automatic repeating rescheduling via [ReminderAlarmScheduler]
 */
@Composable
fun RemindersTabScreen(
    reminders: List<StandaloneReminder>,
    searchQuery: String,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onRemindersUpdated: (List<StandaloneReminder>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedFilter by remember { mutableStateOf(ReminderFilter.UPCOMING) }
    var isCreatingReminder by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<StandaloneReminder?>(null) }
    var snoozingReminder by remember { mutableStateOf<StandaloneReminder?>(null) }
    var pendingDeleteReminder by remember { mutableStateOf<StandaloneReminder?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var canExactAlarms by remember {
        mutableStateOf(ReminderAlarmScheduler.canScheduleExactAlarms(context))
    }
    var exactBannerDismissed by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowMs = System.currentTimeMillis()
                canExactAlarms = ReminderAlarmScheduler.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2800L)
            feedbackBanner = null
        }
    }

    BackHandler(
        enabled = isCreatingReminder ||
            editingReminder != null ||
            snoozingReminder != null ||
            pendingDeleteReminder != null
    ) {
        when {
            pendingDeleteReminder != null -> pendingDeleteReminder = null
            snoozingReminder != null -> snoozingReminder = null
            isCreatingReminder || editingReminder != null -> {
                isCreatingReminder = false
                editingReminder = null
            }
        }
    }

    fun persistUpsertReminder(reminder: StandaloneReminder, feedback: String?) {
        ensureNotificationPermissionIfNeeded()
        scope.launch {
            val sorted = withContext(Dispatchers.IO) {
                val updated = repository.upsertReminder(vaultMode, reminder)
                if (reminder.isCompleted) {
                    ReminderAlarmScheduler.cancelReminder(context, reminder.id)
                } else {
                    ReminderAlarmScheduler.scheduleReminder(context, reminder, vaultMode)
                }
                updated
            }
            onRemindersUpdated(sorted)
            nowMs = System.currentTimeMillis()
            if (!feedback.isNullOrBlank()) {
                feedbackBanner = feedback
            }
        }
    }

    fun persistDeleteReminder(reminder: StandaloneReminder) {
        scope.launch {
            val remaining = withContext(Dispatchers.IO) {
                ReminderAlarmScheduler.cancelReminder(context, reminder.id)
                repository.deleteReminder(vaultMode, reminder.id)
            }
            onRemindersUpdated(remaining)
            feedbackBanner = "Deleted \"${reminder.title.ifBlank { "Reminder" }}\"."
        }
    }

    fun applySnooze(reminder: StandaloneReminder, snoozeAtMs: Long, label: String) {
        ensureNotificationPermissionIfNeeded()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val now = System.currentTimeMillis()
        scope.launch {
            val sorted = withContext(Dispatchers.IO) {
                if (reminder.repeat == ReminderRepeat.NONE) {
                    val updated = reminder.copy(
                        triggerAtMs = snoozeAtMs,
                        isCompleted = false,
                        completedAt = null,
                        updatedAt = now
                    )
                    val list = repository.upsertReminder(vaultMode, updated)
                    ReminderAlarmScheduler.scheduleReminder(context, updated, vaultMode)
                    list
                } else {
                    val ensuredNextMs = if (reminder.triggerAtMs <= now) {
                        ReminderAlarmScheduler.computeNextOccurrenceMs(
                            reminder.triggerAtMs,
                            reminder.repeat,
                            now
                        ) ?: snoozeAtMs
                    } else {
                        reminder.triggerAtMs
                    }
                    val updated = reminder.copy(
                        triggerAtMs = ensuredNextMs,
                        isCompleted = false,
                        completedAt = null,
                        updatedAt = now
                    )
                    val list = repository.upsertReminder(vaultMode, updated)
                    ReminderAlarmScheduler.scheduleReminder(context, updated, vaultMode)
                    ReminderAlarmScheduler.scheduleSnooze(context, updated, snoozeAtMs, vaultMode)
                    list
                }
            }
            onRemindersUpdated(sorted)
            nowMs = System.currentTimeMillis()
            feedbackBanner = if (reminder.repeat == ReminderRepeat.NONE) {
                "Snoozed \"$label\" (${formatShortDateTime(snoozeAtMs)})."
            } else {
                "Snoozed to ${formatShortTime(snoozeAtMs)} · ${reminder.repeat.displayLabel} schedule kept."
            }
        }
    }

    fun toggleReminderDone(reminder: StandaloneReminder) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val now = System.currentTimeMillis()
        if (!reminder.isCompleted) {
            val completed = reminder.copy(
                isCompleted = true,
                completedAt = now,
                updatedAt = now
            )
            persistUpsertReminder(
                completed,
                "Marked \"${reminder.title.ifBlank { "Reminder" }}\" done."
            )
        } else {
            val nextTriggerMs = when {
                reminder.triggerAtMs > now -> reminder.triggerAtMs
                reminder.repeat != ReminderRepeat.NONE ->
                    ReminderAlarmScheduler.computeNextOccurrenceMs(reminder.triggerAtMs, reminder.repeat, now)
                        ?: (now + 60L * 60L * 1000L)
                else -> roundToNextMinute(now + 60L * 60L * 1000L)
            }
            val reopened = reminder.copy(
                isCompleted = false,
                completedAt = null,
                triggerAtMs = nextTriggerMs,
                updatedAt = now
            )
            persistUpsertReminder(
                reopened,
                "Reopened for ${formatShortDateTime(nextTriggerMs)}."
            )
        }
    }

    val counts = remember(reminders) {
        val upcoming = reminders.count { !it.isCompleted }
        val completed = reminders.count { it.isCompleted }
        mapOf(
            ReminderFilter.UPCOMING to upcoming,
            ReminderFilter.COMPLETED to completed,
            ReminderFilter.ALL to reminders.size
        )
    }

    val filteredReminders = remember(reminders, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        reminders
            .filter { item ->
                val matchesFilter = when (selectedFilter) {
                    ReminderFilter.UPCOMING -> !item.isCompleted
                    ReminderFilter.COMPLETED -> item.isCompleted
                    ReminderFilter.ALL -> true
                }
                if (!matchesFilter) return@filter false
                if (q.isEmpty()) return@filter true

                val dateText = formatFullDateTime(item.triggerAtMs).lowercase(Locale.getDefault())
                item.title.lowercase(Locale.getDefault()).contains(q) ||
                    item.note.lowercase(Locale.getDefault()).contains(q) ||
                    item.repeat.displayLabel.lowercase(Locale.getDefault()).contains(q) ||
                    dateText.contains(q)
            }
            .sortedWith(
                if (selectedFilter == ReminderFilter.COMPLETED) {
                    compareByDescending<StandaloneReminder> { it.completedAt ?: it.updatedAt }
                } else {
                    compareBy<StandaloneReminder> { it.isCompleted }
                        .thenBy { if (!it.isCompleted) it.triggerAtMs else Long.MAX_VALUE }
                        .thenByDescending { it.completedAt ?: it.updatedAt }
                }
            )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReminderFilter.entries.forEach { filter ->
                    val selected = selectedFilter == filter
                    val count = counts[filter] ?: 0
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .background(if (selected) colors.accent else colors.bg)
                            .border(
                                LikkhoHairlineBorder,
                                if (selected) colors.accentHover else colors.border,
                                RectangleShape
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = filter.label,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 11.sp,
                            color = if (selected) Color.White else colors.text
                        )
                        Text(
                            text = "$count",
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 10.sp,
                                color = if (selected) Color.White.copy(alpha = 0.9f) else colors.muted,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                }
            }
            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarms && !exactBannerDismissed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.08f))
                        .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.45f), RectangleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Allow exact alarms so reminders ring on time.",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 11.sp,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .background(colors.accent)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Allow",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = colors.muted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { exactBannerDismissed = true }
                    )
                }
            }

            if (!feedbackBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.emerald.copy(alpha = 0.08f))
                        .border(LikkhoHairlineBorder, colors.emerald, RectangleShape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.emerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = feedbackBanner.orEmpty(),
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filteredReminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchQuery.isNotBlank()) {
                                    Icons.Outlined.Search
                                } else {
                                    Icons.Outlined.NotificationsActive
                                },
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "No matching reminders"
                                selectedFilter == ReminderFilter.COMPLETED -> "No completed reminders yet"
                                else -> "No upcoming reminders"
                            },
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() ->
                                    "No reminders matched \"$searchQuery\"."
                                selectedFilter == ReminderFilter.COMPLETED ->
                                    "Mark an active reminder done to keep track of finished tasks here."
                                else ->
                                    "Schedule one-time or repeating (Daily, Weekly, Monthly) alerts with exact device notifications."
                            },
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank() && selectedFilter != ReminderFilter.COMPLETED) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LikkhoDialogPrimaryButton(
                                text = "New Reminder",
                                leadingIcon = Icons.Outlined.Add,
                                onClick = { isCreatingReminder = true }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReminders, key = { it.id }) { reminder ->
                        ReminderCardItem(
                            reminder = reminder,
                            nowMs = nowMs,
                            onToggleDone = { toggleReminderDone(reminder) },
                            onSnoozeClick = { snoozingReminder = reminder },
                            onEditClick = { editingReminder = reminder },
                            onDeleteClick = { pendingDeleteReminder = reminder }
                        )
                    }
                }
            }
        }

        // Bottom-right rectangular FAB
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .shadow(8.dp, RectangleShape)
                .height(42.dp)
                .background(colors.accent)
                .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                .clickable { isCreatingReminder = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "New Reminder",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Reminder",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        }

        if (isCreatingReminder || editingReminder != null) {
            ReminderEditorDialog(
                initialReminder = editingReminder,
                onDismiss = {
                    isCreatingReminder = false
                    editingReminder = null
                },
                onSave = { saved ->
                    val wasEditing = editingReminder != null
                    isCreatingReminder = false
                    editingReminder = null
                    persistUpsertReminder(
                        saved,
                        if (wasEditing) {
                            "Updated \"${saved.title}\" (${formatShortDateTime(saved.triggerAtMs)})."
                        } else {
                            "Scheduled \"${saved.title}\" for ${formatShortDateTime(saved.triggerAtMs)}."
                        }
                    )
                }
            )
        }

        snoozingReminder?.let { target ->
            SnoozeReminderDialog(
                reminder = target,
                onDismiss = { snoozingReminder = null },
                onSelectSnoozeMs = { snoozeAtMs, label ->
                    snoozingReminder = null
                    applySnooze(target, snoozeAtMs, label)
                }
            )
        }

        pendingDeleteReminder?.let { target ->
            LikkhoHairlineDialog(
                onDismissRequest = { pendingDeleteReminder = null },
                title = "Delete Reminder",
                subtitle = "CANCEL SCHEDULED ALERT",
                leadingIcon = Icons.Outlined.WarningAmber,
                leadingIconTint = colors.crimson,
                borderColor = colors.crimson,
                footer = {
                    LikkhoDialogOutlineButton(
                        text = "Cancel",
                        onClick = { pendingDeleteReminder = null }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LikkhoDialogPrimaryButton(
                        text = "Delete",
                        bgColor = colors.crimson,
                        onClick = {
                            val toDelete = target
                            pendingDeleteReminder = null
                            persistDeleteReminder(toDelete)
                        }
                    )
                }
            ) {
                Text(
                    text = "Permanently delete \"${target.title.ifBlank { "Untitled Reminder" }}\" and cancel its scheduled notification?",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.text
                )
            }
        }
    }
}

@Composable
private fun ReminderCardItem(
    reminder: StandaloneReminder,
    nowMs: Long,
    onToggleDone: () -> Unit,
    onSnoozeClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val isOverdue = !reminder.isCompleted && reminder.triggerAtMs <= nowMs
    val isToday = !reminder.isCompleted && isSameCalendarDay(reminder.triggerAtMs, nowMs)
    val isTomorrow = !reminder.isCompleted && isTomorrowCalendarDay(reminder.triggerAtMs, nowMs)

    val stripeColor = when {
        reminder.isCompleted -> colors.emerald
        isOverdue -> colors.crimson
        isToday -> colors.accent
        else -> colors.border
    }

    val statusChipLabel = when {
        reminder.isCompleted -> "DONE"
        isOverdue -> "OVERDUE"
        isToday -> "TODAY"
        isTomorrow -> "TOMORROW"
        else -> null
    }
    val statusChipColor = when {
        reminder.isCompleted -> colors.emerald
        isOverdue -> colors.crimson
        else -> colors.accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (reminder.isCompleted) 0.78f else 1f)
            .background(colors.surface)
            .border(
                LikkhoHairlineBorder,
                if (isOverdue) colors.crimson.copy(alpha = 0.6f) else colors.border,
                RectangleShape
            )
            .clickable { onEditClick() }
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(stripeColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .background(if (reminder.isCompleted) colors.emerald else colors.bg)
                        .border(
                            LikkhoHairlineBorder,
                            if (reminder.isCompleted) colors.emerald else colors.border,
                            RectangleShape
                        )
                        .clickable { onToggleDone() },
                    contentAlignment = Alignment.Center
                ) {
                    if (reminder.isCompleted) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Reopen",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = reminder.title.ifBlank { "Untitled Reminder" },
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = if (reminder.isCompleted) colors.muted else colors.text,
                        textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (reminder.note.isNotBlank()) {
                        Text(
                            text = reminder.note,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = colors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Date, Status & Repeat Metadata Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = if (isOverdue) colors.crimson else colors.muted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatShortDateTime(reminder.triggerAtMs),
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOverdue) colors.crimson else colors.text,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }

                if (statusChipLabel != null) {
                    Box(
                        modifier = Modifier
                            .background(statusChipColor.copy(alpha = 0.1f))
                            .border(LikkhoHairlineBorder, statusChipColor.copy(alpha = 0.45f), RectangleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusChipLabel,
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = statusChipColor
                        )
                    }
                }

                if (reminder.repeat != ReminderRepeat.NONE) {
                    Row(
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.08f))
                            .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.4f), RectangleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Repeat,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = reminder.repeat.displayLabel.uppercase(Locale.US),
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = colors.accent
                        )
                    }
                }
            }

            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!reminder.isCompleted) {
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.emerald.copy(alpha = 0.6f), RectangleShape)
                                .clickable { onToggleDone() }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.emerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Done",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = colors.emerald
                            )
                        }

                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                .clickable { onSnoozeClick() }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Snooze,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Snooze",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = colors.text
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.6f), RectangleShape)
                                .clickable { onToggleDone() }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Reopen",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = colors.accent
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onEditClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Reminder",
                            tint = colors.text,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onDeleteClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Reminder",
                            tint = colors.crimson,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    initialReminder: StandaloneReminder?,
    onDismiss: () -> Unit,
    onSave: (StandaloneReminder) -> Unit
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val titleFocusRequester = remember { FocusRequester() }

    val defaultTriggerMs = remember(initialReminder) {
        if (initialReminder != null && initialReminder.triggerAtMs > 0L) {
            initialReminder.triggerAtMs
        } else {
            roundToNextHourOrHalfHour(System.currentTimeMillis() + 60L * 60L * 1000L)
        }
    }

    var title by remember(initialReminder) { mutableStateOf(initialReminder?.title.orEmpty()) }
    var note by remember(initialReminder) { mutableStateOf(initialReminder?.note.orEmpty()) }
    var triggerAtMs by remember(initialReminder) { mutableLongStateOf(defaultTriggerMs) }
    var repeat by remember(initialReminder) {
        mutableStateOf(initialReminder?.repeat ?: ReminderRepeat.NONE)
    }
    var reopenIfCompleted by remember(initialReminder) {
        mutableStateOf(initialReminder?.isCompleted == true)
    }

    LaunchedEffect(Unit) {
        if (initialReminder == null) {
            titleFocusRequester.requestFocus()
        }
    }

    fun openNativeDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMs }
        val themeRes = if (colors.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        DatePickerDialog(
            context,
            themeRes,
            { _, year, month, dayOfMonth ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = triggerAtMs
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                triggerAtMs = updated.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openNativeTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMs }
        val themeRes = if (colors.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        TimePickerDialog(
            context,
            themeRes,
            { _, hourOfDay, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = triggerAtMs
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                triggerAtMs = updated.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(context)
        ).show()
    }

    val isPastSelected = triggerAtMs <= System.currentTimeMillis()
    val relativeLabel = formatRelativeDelta(triggerAtMs, System.currentTimeMillis())

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = if (initialReminder == null) "New Reminder" else "Edit Reminder",
        subtitle = "SCHEDULED ALARM & ALERT",
        leadingIcon = Icons.Outlined.NotificationsActive,
        maxWidth = 460.dp,
        footer = {
            LikkhoDialogOutlineButton(text = "Cancel", onClick = onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = if (initialReminder == null) "Save Reminder" else "Update",
                enabled = title.trim().isNotEmpty(),
                onClick = {
                    val now = System.currentTimeMillis()
                    val markCompleted = if (initialReminder?.isCompleted == true) {
                        !reopenIfCompleted
                    } else {
                        false
                    }
                    val model = StandaloneReminder(
                        id = initialReminder?.id ?: "rem_${now}_${UUID.randomUUID().toString().take(4)}",
                        title = title.trim(),
                        note = note.trim(),
                        triggerAtMs = triggerAtMs,
                        repeat = repeat,
                        isCompleted = markCompleted,
                        completedAt = if (markCompleted) initialReminder?.completedAt ?: now else null,
                        createdAt = initialReminder?.createdAt ?: now,
                        updatedAt = now
                    )
                    onSave(model)
                }
            )
        }
    ) {
        // Title
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "REMINDER TITLE",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                if (title.isEmpty()) {
                    Text(
                        text = "e.g., Pay electricity bill, Take medicine...",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                )
            }
        }

        // Note (Optional)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "NOTES (OPTIONAL)",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                if (note.isEmpty()) {
                    Text(
                        text = "Add context, account number, or instructions...",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = TextStyle(
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.text
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Quick Presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "QUICK SCHEDULE",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickPresets = listOf(
                    "+15 min" to { roundToNextMinute(System.currentTimeMillis() + 15L * 60_000L) },
                    "+1 hour" to { roundToNextMinute(System.currentTimeMillis() + 60L * 60_000L) },
                    "+3 hours" to { roundToNextMinute(System.currentTimeMillis() + 3L * 60L * 60_000L) },
                    "Tonight 8 PM" to { computeTodayOrTomorrowTime(20, 0) },
                    "Tomorrow 9 AM" to { computeTomorrowTime(9, 0) }
                )
                quickPresets.forEach { (label, compute) ->
                    Box(
                        modifier = Modifier
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { triggerAtMs = compute() }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.text
                        )
                    }
                }
            }
        }

        // Date & Time Pickers
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "DATE & TIME",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(40.dp)
                        .background(colors.surface)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { openNativeDatePicker() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = "Pick Date",
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatPickerDate(triggerAtMs),
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(0.95f)
                        .height(40.dp)
                        .background(colors.surface)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { openNativeTimePicker() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Pick Time",
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatShortTime(triggerAtMs),
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isPastSelected) colors.crimson.copy(alpha = 0.08f) else colors.bg
                    )
                    .border(
                        LikkhoHairlineBorder,
                        if (isPastSelected) colors.crimson.copy(alpha = 0.45f) else colors.hairline,
                        RectangleShape
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = relativeLabel,
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 10.sp,
                    color = if (isPastSelected) colors.crimson else colors.muted
                )
                if (isPastSelected) {
                    Text(
                        text = "Set +15m",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                        modifier = Modifier.clickable {
                            triggerAtMs = roundToNextMinute(System.currentTimeMillis() + 15L * 60_000L)
                        }
                    )
                }
            }
        }

        // Repeat Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "REPEAT",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderRepeat.entries.forEach { option ->
                    val selected = repeat == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(if (selected) colors.accent else colors.surface)
                            .border(
                                LikkhoHairlineBorder,
                                if (selected) colors.accentHover else colors.border,
                                RectangleShape
                            )
                            .clickable { repeat = option },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.displayLabel,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else colors.text,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (initialReminder?.isCompleted == true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .clickable { reopenIfCompleted = !reopenIfCompleted }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(if (reopenIfCompleted) colors.accent else colors.bg)
                        .border(
                            LikkhoHairlineBorder,
                            if (reopenIfCompleted) colors.accent else colors.border,
                            RectangleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (reopenIfCompleted) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = "Reopen as upcoming & schedule alarm",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text
                )
            }
        }
    }
}

@Composable
private fun SnoozeReminderDialog(
    reminder: StandaloneReminder,
    onDismiss: () -> Unit,
    onSelectSnoozeMs: (Long, String) -> Unit
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current

    val options = remember(reminder.id) {
        listOf(
            "In 10 minutes" to { roundToNextMinute(System.currentTimeMillis() + 10L * 60_000L) },
            "In 30 minutes" to { roundToNextMinute(System.currentTimeMillis() + 30L * 60_000L) },
            "In 1 hour" to { roundToNextMinute(System.currentTimeMillis() + 60L * 60_000L) },
            "In 3 hours" to { roundToNextMinute(System.currentTimeMillis() + 3L * 60L * 60_000L) },
            "Tomorrow 9:00 AM" to { computeTomorrowTime(9, 0) }
        )
    }

    fun pickCustomSnoozeDateTime() {
        val base = Calendar.getInstance().apply {
            timeInMillis = maxOf(reminder.triggerAtMs, System.currentTimeMillis() + 15L * 60_000L)
        }
        val themeRes = if (colors.isDark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        DatePickerDialog(
            context,
            themeRes,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    themeRes,
                    { _, hourOfDay, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onSelectSnoozeMs(picked.timeInMillis, reminder.title.ifBlank { "Reminder" })
                    },
                    base.get(Calendar.HOUR_OF_DAY),
                    base.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context)
                ).show()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = "Snooze Reminder",
        subtitle = reminder.title.ifBlank { "SELECT NEW TIME" }.uppercase(Locale.US),
        leadingIcon = Icons.Outlined.Snooze,
        footer = {
            LikkhoDialogOutlineButton(text = "Cancel", onClick = onDismiss)
        }
    ) {
        options.forEach { (label, computeMs) ->
            val previewMs = computeMs()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .clickable {
                        onSelectSnoozeMs(computeMs(), reminder.title.ifBlank { "Reminder" })
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.text
                )
                Text(
                    text = formatShortDateTime(previewMs),
                    style = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.accent,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.6f), RectangleShape)
                .clickable { pickCustomSnoozeDateTime() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Pick custom date & time...",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.accent
            )
        }
    }
}

private fun roundToNextMinute(rawMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = rawMs
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun roundToNextHourOrHalfHour(rawMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = rawMs
        val min = get(Calendar.MINUTE)
        set(Calendar.MINUTE, if (min < 30) 30 else 0)
        if (min >= 30) add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun computeTodayOrTomorrowTime(hourOfDay: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now + 60_000L) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}

private fun computeTomorrowTime(hourOfDay: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun isSameCalendarDay(aMs: Long, bMs: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = aMs }
    val b = Calendar.getInstance().apply { timeInMillis = bMs }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun isTomorrowCalendarDay(targetMs: Long, nowMs: Long): Boolean {
    val tomorrow = Calendar.getInstance().apply {
        timeInMillis = nowMs
        add(Calendar.DAY_OF_YEAR, 1)
    }
    val target = Calendar.getInstance().apply { timeInMillis = targetMs }
    return target.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
}

private fun formatShortDateTime(ms: Long): String {
    if (ms <= 0L) return "Unscheduled"
    val now = System.currentTimeMillis()
    val timeStr = formatShortTime(ms)
    return when {
        isSameCalendarDay(ms, now) -> "Today · $timeStr"
        isTomorrowCalendarDay(ms, now) -> "Tomorrow · $timeStr"
        else -> {
            val fmt = SimpleDateFormat("dd MMM · hh:mm a", Locale.US)
            fmt.format(Date(ms))
        }
    }
}

private fun formatFullDateTime(ms: Long): String {
    if (ms <= 0L) return ""
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(ms))
}

private fun formatPickerDate(ms: Long): String {
    return SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(Date(ms))
}

private fun formatShortTime(ms: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date(ms))
}

private fun formatRelativeDelta(targetMs: Long, nowMs: Long): String {
    val diffMs = targetMs - nowMs
    val absMin = kotlin.math.abs(diffMs) / 60_000L
    val hours = absMin / 60L
    val mins = absMin % 60L
    val days = hours / 24L
    val remHours = hours % 24L

    val span = when {
        absMin < 1L -> "less than a minute"
        days >= 1L -> if (remHours > 0L) "${days}d ${remHours}h" else "${days}d"
        hours >= 1L -> if (mins > 0L) "${hours}h ${mins}m" else "${hours}h"
        else -> "${absMin}m"
    }
    return if (diffMs >= 0L) {
        "Rings in $span (${formatShortDateTime(targetMs)})"
    } else {
        "In the past by $span"
    }
}

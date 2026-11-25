package com.likkho.diary.nativeapp.ui.screens

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.QuickNoteChecklistItem
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Synchronizes active Quick Notes with Android Notifications (`likkho_quick_notes_channel`),
 * Home Screen Widget (`LikkhoWidgetProvider`), Quick Settings Tile (`QuickNoteTileService`),
 * and pinned Home Screen Shortcuts.
 */
object QuickNoteSystemSync {

    private const val PREFS_NAME = "likkho_saf_prefs"
    private const val WIDGET_KEY_TITLE = "widget_quick_note_title"
    private const val WIDGET_KEY_BODY = "widget_quick_note_body"
    private const val CHANNEL_ID = "likkho_quick_notes_channel"
    private const val ACTION_QUICK_NOTE = "com.likkho.diary.ACTION_QUICK_NOTE"
    private const val ACTION_DONE = "com.likkho.diary.ACTION_QUICK_NOTE_DONE"
    private const val ACTION_DISMISS = "com.likkho.diary.ACTION_QUICK_NOTE_DISMISS"

    fun syncActiveNoteToHomeWidgetAndTile(context: Context, notes: List<QuickNote>) {
        val appCtx = context.applicationContext
        val activeNotes = notes.filter { !it.isDone }
        val chosen = activeNotes.firstOrNull { it.isPinnedToNotification } ?: activeNotes.firstOrNull()

        val title: String
        val summary: String
        if (chosen == null) {
            title = ""
            summary = ""
        } else {
            title = chosen.title.ifBlank { "Quick Note" }.trim()
            summary = when {
                chosen.isChecklist && chosen.items.isNotEmpty() -> {
                    val pending = chosen.items.filter { !it.checked }.map { "☐ ${it.text}" }
                    val done = chosen.items.filter { it.checked }.map { "☑ ${it.text}" }
                    (pending + done).joinToString(" · ")
                }
                chosen.content.isNotBlank() -> chosen.content.replace(Regex("\\s+"), " ").trim()
                chosen.items.isNotEmpty() -> chosen.items.joinToString(" · ") { it.text }
                else -> ""
            }.take(220)
        }

        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(WIDGET_KEY_TITLE, title)
            .putString(WIDGET_KEY_BODY, summary)
            .apply()

        // Refresh LikkhoWidgetProvider if registered in app manifest
        runCatching {
            val widgetClass = Class.forName("com.likkho.diary.LikkhoWidgetProvider")
            val method = widgetClass.getMethod("updateAllWidgets", Context::class.java)
            method.invoke(null, appCtx)
        }.onFailure {
            runCatching {
                val component = ComponentName(appCtx, "com.likkho.diary.LikkhoWidgetProvider")
                val manager = AppWidgetManager.getInstance(appCtx)
                val ids = manager?.getAppWidgetIds(component)
                if (ids != null && ids.isNotEmpty()) {
                    val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        setComponent(component)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    appCtx.sendBroadcast(updateIntent)
                }
            }
        }

        // Request Android Quick Settings Tile refresh (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                val tileComp = ComponentName(appCtx, "com.likkho.diary.QuickNoteTileService")
                TileService.requestListeningState(appCtx, tileComp)
            }
        }
    }

    fun syncQuickNoteNotification(
        context: Context,
        note: QuickNote?,
        cancelNoteId: String? = null
    ) {
        val appCtx = context.applicationContext
        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (note == null || note.isDone || !note.isPinnedToNotification) {
            val idToCancel = note?.id ?: cancelNoteId
            if (!idToCancel.isNullOrBlank()) {
                val notifId = 88000 + abs(idToCancel.hashCode() % 1000)
                runCatching { nm.cancel(notifId) }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Likkho Quick Notes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val notifId = 88000 + abs(note.id.hashCode() % 1000)

        val openIntent = resolveLaunchIntent(appCtx).apply {
            action = ACTION_QUICK_NOTE
            putExtra("action", "quick_note")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            appCtx,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val receiverComponent = ComponentName(appCtx, "com.likkho.diary.QuickNoteReceiver")
        val doneIntent = Intent(ACTION_DONE).apply {
            component = receiverComponent
            putExtra("noteId", note.id)
            putExtra("notifId", notifId)
        }
        val donePi = PendingIntent.getBroadcast(
            appCtx,
            notifId + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(ACTION_DISMISS).apply {
            component = receiverComponent
            putExtra("noteId", note.id)
            putExtra("notifId", notifId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            appCtx,
            notifId + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val itemsToSync = if (note.items.isNotEmpty()) {
            note.items
        } else {
            note.content
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { idx, line ->
                    QuickNoteChecklistItem(id = "line_$idx", text = line, checked = false)
                }
        }

        val safeTitle = note.title.ifBlank { "Quick Note" }
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle("📝 $safeTitle")

        val summary = StringBuilder()
        var doneCount = 0
        itemsToSync.forEach { item ->
            if (item.checked) doneCount++
            val line = if (note.isChecklist) {
                "${if (item.checked) "☑" else "☐"} ${item.text}"
            } else {
                item.text
            }
            inbox.addLine(line)
            if (summary.isNotEmpty()) summary.append(" · ")
            summary.append(line)
        }
        if (note.isChecklist && itemsToSync.isNotEmpty()) {
            inbox.setSummaryText("$doneCount/${itemsToSync.size} completed")
        } else {
            inbox.setSummaryText("Likkho Quick Note")
        }

        val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📝 $safeTitle")
            .setContentText(if (summary.isNotEmpty()) summary.toString() else "Quick Note")
            .setStyle(inbox)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.checkbox_on_background, "Done", donePi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi)

        runCatching { nm.notify(notifId, builder.build()) }
    }

    fun requestPinHomeScreenShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!sm.isRequestPinShortcutSupported) return false
            val launchIntent = resolveLaunchIntent(context).apply {
                action = ACTION_QUICK_NOTE
                putExtra("action", "quick_note")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pinInfo = ShortcutInfo.Builder(context, "shortcut_quick_note")
                .setShortLabel("Quick Note")
                .setLongLabel("Likkho Quick Note")
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_edit))
                .setIntent(launchIntent)
                .build()
            sm.requestPinShortcut(pinInfo, null)
            true
        }.getOrDefault(false)
    }

    private fun resolveLaunchIntent(context: Context): Intent {
        val nativeMain = runCatching {
            Class.forName("com.likkho.diary.nativeapp.NativeMainActivity")
        }.getOrNull()
        if (nativeMain != null) {
            return Intent(context, nativeMain)
        }
        val capMain = runCatching {
            Class.forName("com.likkho.diary.MainActivity")
        }.getOrNull()
        if (capMain != null) {
            return Intent(context, capMain)
        }
        return context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    }
}

/**
 * Native Compose Quick Note Creator & Editor Modal.
 * Syncs saved notes directly with notifications, Home Widget, and Quick Settings Tile.
 */
@Composable
fun QuickNoteModal(
    initialNote: QuickNote? = null,
    onDismiss: () -> Unit,
    onSave: (QuickNote) -> Unit
) {
    val colors = LikkhoTheme.colors
    val haptic = LocalHapticFeedback.current
    val titleFocusRequester = remember { FocusRequester() }

    val isChecklist = initialNote?.isChecklist ?: false

    var title by remember(initialNote?.id) { mutableStateOf(initialNote?.title ?: "") }
    var content by remember(initialNote?.id) {
        mutableStateOf(
            when {
                !initialNote?.content.isNullOrBlank() -> initialNote!!.content
                !initialNote?.items.isNullOrEmpty() -> initialNote!!.items.joinToString("\n") { it.text }
                else -> ""
            }
        )
    }
    var items by remember(initialNote?.id) {
        mutableStateOf(
            if (!initialNote?.items.isNullOrEmpty()) {
                initialNote!!.items
            } else {
                listOf(
                    QuickNoteChecklistItem(
                        id = "item_${System.currentTimeMillis()}_1",
                        text = "",
                        checked = false
                    )
                )
            }
        )
    }
    var newTodoInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
    }

    fun addTodoItem() {
        val trimmed = newTodoInput.trim()
        if (trimmed.isEmpty()) return
        items = items + QuickNoteChecklistItem(
            id = "item_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
            text = trimmed,
            checked = false
        )
        newTodoInput = ""
    }

    fun submitNote() {
        val trimmedTitle = title.trim()
        val finalItems: List<QuickNoteChecklistItem>
        val finalContent: String

        if (isChecklist) {
            val cleaned = items.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }.toMutableList()
            val pendingLine = newTodoInput.trim()
            if (pendingLine.isNotEmpty()) {
                cleaned.add(
                    QuickNoteChecklistItem(
                        id = "item_${System.currentTimeMillis()}_last",
                        text = pendingLine,
                        checked = false
                    )
                )
            }
            if (cleaned.isEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                errorMsg = "Please enter at least one item."
                return
            }
            finalItems = cleaned
            finalContent = cleaned.joinToString("\n") { if (it.checked) "[x] ${it.text}" else "[ ] ${it.text}" }
        } else {
            finalContent = content.trim()
            if (finalContent.isEmpty() && trimmedTitle.isEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                errorMsg = "Please enter a note title or content."
                return
            }
            val lines = if (finalContent.isNotEmpty()) {
                finalContent.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            finalItems = lines.mapIndexed { idx, line ->
                QuickNoteChecklistItem(id = "line_$idx", text = line, checked = false)
            }
        }

        val now = System.currentTimeMillis()
        val fallbackTitle = "Quick Note · ${SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(now))}"
        val saved = QuickNote(
            id = initialNote?.id ?: "qn_${now}_${UUID.randomUUID().toString().take(4)}",
            title = trimmedTitle.ifEmpty { fallbackTitle },
            items = finalItems,
            createdAt = initialNote?.createdAt ?: now,
            updatedAt = now,
            isDone = if (isChecklist) finalItems.isNotEmpty() && finalItems.all { it.checked } else false,
            content = finalContent,
            isChecklist = isChecklist,
            isPinnedToNotification = true
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSave(saved)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 640.dp)
                .background(colors.bg)
                .border(1.dp, colors.border)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(colors.accent.copy(alpha = 0.10f))
                            .border(1.dp, colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (initialNote != null) "Edit Quick Note" else "Quick Note",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = colors.border)

            // Body
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = colors.crimson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.crimson.copy(alpha = 0.08f))
                            .border(1.dp, colors.crimson)
                            .padding(10.dp)
                    )
                }

                // Title
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "TITLE",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = colors.muted
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (title.isEmpty()) {
                            Text(
                                text = "Note title (e.g. Shopping, Ideas, Code, Reminders)...",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                errorMsg = null
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(titleFocusRequester)
                        )
                    }
                }

                // Content Canvas or Checklist
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = if (isChecklist) "ITEMS" else "CONTENT CANVAS",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = colors.muted
                    )

                    if (!isChecklist) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp)
                                .background(colors.surface)
                                .border(1.dp, colors.border)
                                .padding(12.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (content.isEmpty()) {
                                Text(
                                    text = "Type your quick note content here...",
                                    fontFamily = LikkhoFontFamilies.Sans,
                                    fontSize = 12.sp,
                                    color = colors.muted
                                )
                            }
                            BasicTextField(
                                value = content,
                                onValueChange = {
                                    content = it
                                    errorMsg = null
                                },
                                textStyle = TextStyle(
                                    fontFamily = LikkhoFontFamilies.Sans,
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    color = colors.text
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .border(1.dp, colors.border)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items.forEachIndexed { idx, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(if (item.checked) colors.emerald else colors.bg)
                                            .border(1.dp, if (item.checked) colors.emerald else colors.border)
                                            .clickable {
                                                items = items.map {
                                                    if (it.id == item.id) it.copy(checked = !it.checked) else it
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.checked) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(colors.bg)
                                            .border(1.dp, colors.hairline)
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (item.text.isEmpty()) {
                                            Text(
                                                text = "Item ${idx + 1}",
                                                fontFamily = LikkhoFontFamilies.Sans,
                                                fontSize = 12.sp,
                                                color = colors.muted
                                            )
                                        }
                                        BasicTextField(
                                            value = item.text,
                                            onValueChange = { updatedText ->
                                                items = items.map {
                                                    if (it.id == item.id) it.copy(text = updatedText) else it
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontFamily = LikkhoFontFamilies.Sans,
                                                fontSize = 12.sp,
                                                color = if (item.checked) colors.muted else colors.text,
                                                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            cursorBrush = SolidColor(colors.accent),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (items.size > 1) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable { items = items.filterNot { it.id == item.id } },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Remove item",
                                                tint = colors.muted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(colors.bg)
                                        .border(1.dp, colors.border)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (newTodoInput.isEmpty()) {
                                        Text(
                                            text = "Type item & press Enter...",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontSize = 12.sp,
                                            color = colors.muted
                                        )
                                    }
                                    BasicTextField(
                                        value = newTodoInput,
                                        onValueChange = {
                                            newTodoInput = it
                                            errorMsg = null
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontSize = 12.sp,
                                            color = colors.text
                                        ),
                                        cursorBrush = SolidColor(colors.accent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { addTodoItem() }),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .alpha(if (newTodoInput.trim().isEmpty()) 0.45f else 1f)
                                        .background(colors.surface)
                                        .border(1.dp, colors.border)
                                        .clickable(enabled = newTodoInput.trim().isNotEmpty()) { addTodoItem() }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Add",
                                        fontFamily = LikkhoFontFamilies.Sans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = colors.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Shows in Notification Shade",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 11.sp,
                    color = colors.muted,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .background(colors.bg)
                            .border(1.dp, colors.border)
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.text
                        )
                    }
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .background(colors.accent)
                            .clickable { submitNote() }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Save Note",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full Quick Notes Manager Screen (parity with `src/components/QuickNotesPage.tsx`).
 */
@Composable
fun QuickNotesManagerScreen(
    notes: List<QuickNote>,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onNotesUpdated: (List<QuickNote>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var editingNote by remember { mutableStateOf<QuickNote?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    BackHandler {
        when {
            isCreatingNew || editingNote != null -> {
                isCreatingNew = false
                editingNote = null
            }
            else -> onBack()
        }
    }

    fun persistAndSync(next: List<QuickNote>, targetForNotif: QuickNote?, cancelId: String?, feedback: String?) {
        onNotesUpdated(next)
        scope.launch(Dispatchers.IO) {
            repository.saveQuickNotes(vaultMode, next)
        }
        QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(context, next)
        QuickNoteSystemSync.syncQuickNoteNotification(context, targetForNotif, cancelId)
        if (!feedback.isNullOrBlank()) {
            toastMsg = feedback
        }
    }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            kotlinx.coroutines.delay(2800L)
            toastMsg = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.CheckBox,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Quick Notes",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .height(36.dp)
                    .background(colors.accent)
                    .border(1.dp, colors.accentHover)
                    .clickable { isCreatingNew = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Add",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!toastMsg.isNullOrBlank()) {
                item(key = "toast") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.emerald.copy(alpha = 0.10f))
                            .border(1.dp, colors.emerald)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.emerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = toastMsg!!,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.text
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.bg)
                                .border(1.dp, colors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckBox,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No quick notes yet",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + Add above, use the Likkho Home Widget, or add the Quick Note tile in your Android Quick Settings shade.",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .background(colors.bg)
                                .border(1.dp, colors.border)
                                .clickable {
                                    val pinned = QuickNoteSystemSync.requestPinHomeScreenShortcut(context)
                                    toastMsg = if (pinned) {
                                        "Quick Note shortcut requested."
                                    } else {
                                        "Shortcut pinning is not supported on this launcher."
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pin Quick Note Shortcut",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.accent
                            )
                        }
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    QuickNoteCardItem(
                        note = note,
                        onToggleChecklistItem = { itemId ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val updated = notes.map { n ->
                                if (n.id != note.id) n else {
                                    val nextItems = n.items.map { item ->
                                        if (item.id == itemId) item.copy(checked = !item.checked) else item
                                    }
                                    val allDone = nextItems.isNotEmpty() && nextItems.all { it.checked }
                                    n.copy(
                                        items = nextItems,
                                        isDone = allDone,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                            }
                            val target = updated.firstOrNull { it.id == note.id }
                            persistAndSync(
                                next = updated,
                                targetForNotif = if (target?.isDone == true) null else target,
                                cancelId = target?.id,
                                feedback = null
                            )
                        },
                        onToggleNoteDone = {
                            val updated = notes.map { n ->
                                if (n.id != note.id) n else {
                                    val nextDone = !n.isDone
                                    n.copy(
                                        isDone = nextDone,
                                        items = n.items.map { it.copy(checked = nextDone) },
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                            }
                            val target = updated.firstOrNull { it.id == note.id }
                            val msg = if (target?.isDone == true) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                "Note marked as completed."
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                "Note restored to active."
                            }
                            persistAndSync(
                                next = updated,
                                targetForNotif = if (target?.isDone == true) null else target,
                                cancelId = target?.id,
                                feedback = msg
                            )
                        },
                        onToggleNotification = {
                            val updated = notes.map { n ->
                                if (n.id != note.id) n else n.copy(
                                    isPinnedToNotification = !n.isPinnedToNotification,
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            val target = updated.firstOrNull { it.id == note.id }
                            val msg = if (target?.isPinnedToNotification == true) {
                                "Pinned to Android Notification bar."
                            } else {
                                "Removed from Notification bar."
                            }
                            persistAndSync(
                                next = updated,
                                targetForNotif = if (target?.isPinnedToNotification == true && target.isDone.not()) target else null,
                                cancelId = target?.id,
                                feedback = msg
                            )
                        },
                        onEdit = { editingNote = note },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val remaining = notes.filterNot { it.id == note.id }
                            persistAndSync(
                                next = remaining,
                                targetForNotif = null,
                                cancelId = note.id,
                                feedback = "Quick Note deleted."
                            )
                        }
                    )
                }
            }
        }
    }

    if (isCreatingNew || editingNote != null) {
        QuickNoteModal(
            initialNote = editingNote,
            onDismiss = {
                isCreatingNew = false
                editingNote = null
            },
            onSave = { saved ->
                val exists = notes.any { it.id == saved.id }
                val nextNotes = if (exists) {
                    notes.map { if (it.id == saved.id) saved else it }
                } else {
                    listOf(saved) + notes
                }
                val msg = if (saved.isPinnedToNotification && !saved.isDone) {
                    "Quick Note saved & posted to Android Notification."
                } else {
                    "Quick Note saved."
                }
                persistAndSync(
                    next = nextNotes,
                    targetForNotif = if (saved.isPinnedToNotification && !saved.isDone) saved else null,
                    cancelId = saved.id,
                    feedback = msg
                )
                isCreatingNew = false
                editingNote = null
            }
        )
    }
}

@Composable
private fun QuickNoteCardItem(
    note: QuickNote,
    onToggleChecklistItem: (String) -> Unit,
    onToggleNoteDone: () -> Unit,
    onToggleNotification: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (note.isDone) 0.78f else 1f)
            .background(if (note.isDone) colors.surface else colors.bg)
            .border(1.dp, colors.border)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (note.isChecklist) Icons.Outlined.CheckBox else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = note.title.ifBlank { "Quick Note" },
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (note.isDone) colors.muted else colors.text,
                        textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(note.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis())),
                    style = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted,
                        fontFeatureSettings = "tnum"
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(if (note.isDone) colors.emerald.copy(alpha = 0.14f) else colors.accent.copy(alpha = 0.14f))
                        .border(1.dp, if (note.isDone) colors.emerald.copy(alpha = 0.45f) else colors.accent.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (note.isDone) "COMPLETED" else "ACTIVE",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (note.isDone) colors.emerald else colors.accent
                    )
                }

                if (note.isPinnedToNotification && !note.isDone) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(colors.accent.copy(alpha = 0.10f))
                            .border(1.dp, colors.accent.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Pinned to notification",
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colors.hairline)

        // Content / Checklist Display
        if (note.isChecklist && note.items.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleChecklistItem(item.id) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(if (item.checked) colors.emerald else colors.bg)
                                .border(1.dp, if (item.checked) colors.emerald else colors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.checked) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Text(
                            text = item.text,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            fontWeight = if (item.checked) FontWeight.Normal else FontWeight.Medium,
                            color = if (item.checked) colors.muted else colors.text,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }
        } else {
            val bodyText = note.content.ifBlank {
                note.items.joinToString("\n") { it.text }
            }.ifBlank { "Empty note" }
            Text(
                text = bodyText,
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(1.dp, colors.hairline)
                    .padding(10.dp)
            )
        }

        HorizontalDivider(thickness = 1.dp, color = colors.hairline)

        // Card Footer Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .background(if (note.isDone) colors.surface else colors.emerald.copy(alpha = 0.10f))
                        .border(1.dp, if (note.isDone) colors.border else colors.emerald)
                        .clickable { onToggleNoteDone() }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = if (note.isDone) colors.text else colors.emerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (note.isDone) "Reopen" else "Mark as Done",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (note.isDone) colors.text else colors.emerald
                    )
                }

                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .background(if (note.isPinnedToNotification) colors.accent.copy(alpha = 0.10f) else colors.surface)
                        .border(1.dp, if (note.isPinnedToNotification) colors.accent else colors.border)
                        .clickable { onToggleNotification() }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinnedToNotification) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        tint = if (note.isPinnedToNotification) colors.accent else colors.muted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (note.isPinnedToNotification) "Pinned" else "Pin to Notif",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (note.isPinnedToNotification) colors.accent else colors.muted
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(colors.surface)
                        .border(1.dp, colors.border)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit note",
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(colors.surface)
                        .border(1.dp, colors.border)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete note",
                        tint = colors.crimson,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

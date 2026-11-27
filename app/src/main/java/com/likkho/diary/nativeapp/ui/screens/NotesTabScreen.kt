package com.likkho.diary.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogOutlineButton
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogPrimaryButton
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineBorder
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineDialog
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NotesFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    CHECKLISTS("Checklists"),
    DONE("Done")
}

/**
 * Everyday Tab 1: Native Notes & Checklists.
 *
 * Features:
 * - Live search filtering from EverydayShellScreen header
 * - Filter chips (`All`, `Active`, `Checklists`, `Done`)
 * - Interactive 1-tap checklist item toggle
 * - Notification-shade pin toggle synced via [QuickNoteSystemSync]
 * - Rectangular `+ Note` & `+ Checklist` creation actions
 */
@Composable
fun NotesTabScreen(
    notes: List<QuickNote>,
    searchQuery: String,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onNotesUpdated: (List<QuickNote>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var selectedFilter by remember { mutableStateOf(NotesFilter.ALL) }
    var creatingIsChecklist by remember { mutableStateOf<Boolean?>(null) }
    var editingNote by remember { mutableStateOf<QuickNote?>(null) }
    var pendingDeleteNote by remember { mutableStateOf<QuickNote?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    BackHandler(
        enabled = creatingIsChecklist != null || editingNote != null || pendingDeleteNote != null
    ) {
        when {
            pendingDeleteNote != null -> pendingDeleteNote = null
            creatingIsChecklist != null || editingNote != null -> {
                creatingIsChecklist = null
                editingNote = null
            }
        }
    }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2800L)
            feedbackBanner = null
        }
    }

    fun persistAndSync(
        nextNotes: List<QuickNote>,
        targetForNotif: QuickNote?,
        cancelId: String?,
        feedback: String?
    ) {
        onNotesUpdated(nextNotes)
        scope.launch(Dispatchers.IO) {
            repository.saveQuickNotes(vaultMode, nextNotes)
            QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(context, nextNotes)
            QuickNoteSystemSync.syncQuickNoteNotification(context, targetForNotif, cancelId)
        }
        if (!feedback.isNullOrBlank()) {
            feedbackBanner = feedback
        }
    }

    val counts = remember(notes) {
        val active = notes.count { !it.isDone }
        val checklists = notes.count { it.isChecklist }
        val done = notes.count { it.isDone }
        mapOf(
            NotesFilter.ALL to notes.size,
            NotesFilter.ACTIVE to active,
            NotesFilter.CHECKLISTS to checklists,
            NotesFilter.DONE to done
        )
    }

    val filteredNotes = remember(notes, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        notes
            .filter { note ->
                val matchesFilter = when (selectedFilter) {
                    NotesFilter.ALL -> true
                    NotesFilter.ACTIVE -> !note.isDone
                    NotesFilter.CHECKLISTS -> note.isChecklist
                    NotesFilter.DONE -> note.isDone
                }
                if (!matchesFilter) return@filter false
                if (q.isEmpty()) return@filter true

                note.title.lowercase(Locale.getDefault()).contains(q) ||
                    note.content.lowercase(Locale.getDefault()).contains(q) ||
                    note.items.any { it.text.lowercase(Locale.getDefault()).contains(q) }
            }
            .sortedWith(
                compareBy<QuickNote> { it.isDone }
                    .thenByDescending { it.isPinnedToNotification }
                    .thenByDescending { maxOf(it.updatedAt, it.createdAt) }
            )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Filter Pills Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NotesFilter.entries.forEach { filter ->
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
                        text = feedbackBanner!!,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filteredNotes.isEmpty()) {
                NotesEmptyState(
                    hasNotes = notes.isNotEmpty(),
                    hasSearchQuery = searchQuery.trim().isNotEmpty(),
                    searchQuery = searchQuery,
                    filterLabel = selectedFilter.label,
                    onCreateNote = { creatingIsChecklist = false },
                    onCreateChecklist = { creatingIsChecklist = true },
                    onPinShortcut = {
                        val pinned = QuickNoteSystemSync.requestPinHomeScreenShortcut(context)
                        feedbackBanner = if (pinned) {
                            "Quick Note shortcut requested."
                        } else {
                            "Shortcut pinning is not supported on this launcher."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
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
                    items(
                        items = filteredNotes,
                        key = { it.id }
                    ) { note ->
                        EverydayNoteCard(
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
                                    nextNotes = updated,
                                    targetForNotif = if (target?.isDone == true) null else target,
                                    cancelId = target?.id,
                                    feedback = null
                                )
                            },
                            onToggleDone = {
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
                                    "Marked as completed."
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    "Restored to active."
                                }
                                persistAndSync(
                                    nextNotes = updated,
                                    targetForNotif = if (target?.isDone == true) null else target,
                                    cancelId = target?.id,
                                    feedback = msg
                                )
                            },
                            onTogglePin = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val updated = notes.map { n ->
                                    if (n.id != note.id) n else n.copy(
                                        isPinnedToNotification = !n.isPinnedToNotification,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                                val target = updated.firstOrNull { it.id == note.id }
                                val msg = if (target?.isPinnedToNotification == true) {
                                    "Pinned to Notification bar."
                                } else {
                                    "Unpinned from Notification bar."
                                }
                                persistAndSync(
                                    nextNotes = updated,
                                    targetForNotif = if (target?.isPinnedToNotification == true && !target.isDone) target else null,
                                    cancelId = target?.id,
                                    feedback = msg
                                )
                            },
                            onEdit = { editingNote = note },
                            onDelete = { pendingDeleteNote = note }
                        )
                    }
                }
            }
        }

        // Bottom-Right Floating Action Buttons (+ Checklist & + Note)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .shadow(4.dp, RectangleShape)
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .clickable { creatingIsChecklist = true }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckBox,
                    contentDescription = "New Checklist",
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Checklist",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.text
                )
            }

            Row(
                modifier = Modifier
                    .height(44.dp)
                    .shadow(4.dp, RectangleShape)
                    .background(colors.accent)
                    .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                    .clickable { creatingIsChecklist = false }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New Note",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Note",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }

    if (creatingIsChecklist != null || editingNote != null) {
        QuickNoteModal(
            initialNote = editingNote,
            initialIsChecklist = creatingIsChecklist ?: false,
            onDismiss = {
                creatingIsChecklist = null
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
                    "Saved & pinned to Notification."
                } else {
                    "Note saved."
                }
                persistAndSync(
                    nextNotes = nextNotes,
                    targetForNotif = if (saved.isPinnedToNotification && !saved.isDone) saved else null,
                    cancelId = saved.id,
                    feedback = msg
                )
                creatingIsChecklist = null
                editingNote = null
            }
        )
    }

    pendingDeleteNote?.let { target ->
        LikkhoHairlineDialog(
            onDismissRequest = { pendingDeleteNote = null },
            title = "Delete Note",
            leadingIcon = Icons.Outlined.WarningAmber,
            leadingIconTint = colors.crimson,
            borderColor = colors.crimson,
            maxWidth = 380.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    onClick = { pendingDeleteNote = null }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Delete",
                    bgColor = colors.crimson,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val remaining = notes.filterNot { it.id == target.id }
                        persistAndSync(
                            nextNotes = remaining,
                            targetForNotif = null,
                            cancelId = target.id,
                            feedback = "Deleted \"${target.title.ifBlank { "Note" }}\"."
                        )
                        pendingDeleteNote = null
                    }
                )
            }
        ) {
            Text(
                text = "Delete \"${target.title.ifBlank { "Quick Note" }}\" permanently?",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.text
            )
        }
    }
}

@Composable
private fun EverydayNoteCard(
    note: QuickNote,
    onToggleChecklistItem: (String) -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val stampMs = note.updatedAt.takeIf { it > 0L } ?: note.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    val formattedStamp = remember(stampMs) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(stampMs))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (note.isDone) 0.76f else 1f)
            .background(if (note.isDone) colors.surface else colors.bg)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
            .clickable { onEdit() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row
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
                        tint = if (note.isDone) colors.muted else colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = note.title.ifBlank { "Quick Note" },
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (note.isDone) colors.muted else colors.text,
                        textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedStamp,
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.muted,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    if (note.isChecklist && note.items.isNotEmpty()) {
                        val doneItems = note.items.count { it.checked }
                        Text(
                            text = "• $doneItems/${note.items.size}",
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (doneItems == note.items.size) colors.emerald else colors.accent,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .background(
                        if (note.isDone) colors.emerald.copy(alpha = 0.12f)
                        else colors.accent.copy(alpha = 0.10f)
                    )
                    .border(
                        LikkhoHairlineBorder,
                        if (note.isDone) colors.emerald.copy(alpha = 0.45f)
                        else colors.accent.copy(alpha = 0.45f),
                        RectangleShape
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (note.isDone) "DONE" else if (note.isChecklist) "LIST" else "NOTE",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (note.isDone) colors.emerald else colors.accent
                )
            }
        }

        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

        // Body / Checklist
        if (note.isChecklist && note.items.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                                .border(
                                    LikkhoHairlineBorder,
                                    if (item.checked) colors.emerald else colors.border,
                                    RectangleShape
                                ),
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
                            fontSize = 13.sp,
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
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.text,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                    .padding(10.dp)
            )
        }

        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

        // Action Bar
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
                        .border(
                            LikkhoHairlineBorder,
                            if (note.isDone) colors.border else colors.emerald,
                            RectangleShape
                        )
                        .clickable { onToggleDone() }
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
                        text = if (note.isDone) "Reopen" else "Done",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (note.isDone) colors.text else colors.emerald
                    )
                }

                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .background(
                            if (note.isPinnedToNotification) colors.accent.copy(alpha = 0.10f)
                            else colors.surface
                        )
                        .border(
                            LikkhoHairlineBorder,
                            if (note.isPinnedToNotification) colors.accent else colors.border,
                            RectangleShape
                        )
                        .clickable { onTogglePin() }
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
                        text = if (note.isPinnedToNotification) "Pinned" else "Pin Notif",
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
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
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
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
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

@Composable
private fun NotesEmptyState(
    hasNotes: Boolean,
    hasSearchQuery: Boolean,
    searchQuery: String,
    filterLabel: String,
    onCreateNote: () -> Unit,
    onCreateChecklist: () -> Unit,
    onPinShortcut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    Box(
        modifier = modifier.padding(24.dp),
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
                    imageVector = if (hasSearchQuery) Icons.Outlined.Search else Icons.Outlined.CheckBox,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when {
                    hasSearchQuery -> "No matching notes"
                    hasNotes -> "No $filterLabel notes"
                    else -> "No Notes Yet"
                },
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.text,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    hasSearchQuery -> "No notes match \"${searchQuery.trim()}\"."
                    hasNotes -> "Switch the filter above or tap + Note to add another entry."
                    else -> "Create quick notes or interactive checklists synced directly with your Notification shade & Home widget."
                },
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.muted,
                textAlign = TextAlign.Center
            )
            if (!hasSearchQuery && !hasNotes) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LikkhoDialogPrimaryButton(
                        text = "New Note",
                        leadingIcon = Icons.Outlined.Add,
                        onClick = onCreateNote
                    )
                    LikkhoDialogOutlineButton(
                        text = "Checklist",
                        leadingIcon = Icons.Outlined.CheckBox,
                        onClick = onCreateChecklist
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onPinShortcut() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Pin Quick Note Shortcut",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.accent
                    )
                }
            }
        }
    }
}

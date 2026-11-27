package com.likkho.diary.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.RecurringTracker
import com.likkho.diary.nativeapp.model.TrackerDayEntry
import com.likkho.diary.nativeapp.model.TrackerEntryStatus
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
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private enum class TrackerListFilter(val label: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    ALL("All")
}

private data class TrackerPreset(
    val name: String,
    val unit: String,
    val defaultQty: Double,
    val rate: Double,
    val hex: String
)

private val TRACKER_PRESETS = listOf(
    TrackerPreset("Milk", "L", 1.0, 65.0, "#3366CC"),
    TrackerPreset("Newspaper", "copy", 1.0, 6.0, "#0E7490"),
    TrackerPreset("Maid", "visit", 1.0, 0.0, "#14866D"),
    TrackerPreset("Water Jar", "jar", 1.0, 40.0, "#3366CC"),
    TrackerPreset("Medicine", "dose", 1.0, 0.0, "#B32424"),
    TrackerPreset("Gym", "session", 1.0, 0.0, "#6B4C9A")
)

private val TRACKER_ACCENT_SWATCHES = listOf(
    "#3366CC",
    "#14866D",
    "#B32424",
    "#9A6700",
    "#6B4C9A",
    "#0E7490"
)

private val CURRENCY_OPTIONS = listOf("₹", "$", "€", "£")

private data class MonthlyTrackerSummary(
    val presentDays: Int,
    val absentDays: Int,
    val skippedDays: Int,
    val totalQuantity: Double,
    val totalAmount: Double
)

/**
 * Everyday Tab 2: Calendar-Based Recurring Trackers ("Milk ka Hisaab").
 *
 * Features:
 * - Searchable Active/Archived/All tracker list with today-status quick toggle badge & monthly summary pill
 * - Full Monthly Calendar grid (`< Month YYYY >`) with 1-tap `Present ✓` / `Missed ✗` / `Skipped` / `Clear` cycling
 * - Long-press (or day-note tap) editor for custom day quantity & note
 * - Automatic monthly summary card (Present days, Missed days, Skipped days, Total Qty, Total ₹ Amount)
 */
@Composable
fun TrackersTabScreen(
    trackers: List<RecurringTracker>,
    searchQuery: String,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onTrackersUpdated: (List<RecurringTracker>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val todayCalendar = remember { Calendar.getInstance() }
    val todayYear = todayCalendar.get(Calendar.YEAR)
    val todayMonth = todayCalendar.get(Calendar.MONTH)
    val todayDateKey = remember { formatDateKey(todayYear, todayMonth, todayCalendar.get(Calendar.DAY_OF_MONTH)) }
    val currentMonthPrefix = remember { formatMonthPrefix(todayYear, todayMonth) }

    var listFilter by remember { mutableStateOf(TrackerListFilter.ACTIVE) }
    var selectedTrackerId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewedYear by rememberSaveable { mutableIntStateOf(todayYear) }
    var viewedMonth by rememberSaveable { mutableIntStateOf(todayMonth) }

    var isCreatingTracker by remember { mutableStateOf(false) }
    var editingTracker by remember { mutableStateOf<RecurringTracker?>(null) }
    var pendingDeleteTracker by remember { mutableStateOf<RecurringTracker?>(null) }
    var editingDayContext by remember { mutableStateOf<Pair<RecurringTracker, String>?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    val selectedTracker = remember(trackers, selectedTrackerId) {
        selectedTrackerId?.let { id -> trackers.firstOrNull { it.id == id } }
    }

    LaunchedEffect(selectedTrackerId, trackers) {
        if (selectedTrackerId != null && selectedTracker == null) {
            selectedTrackerId = null
        }
    }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2800L)
            feedbackBanner = null
        }
    }

    BackHandler(
        enabled = pendingDeleteTracker != null ||
            editingDayContext != null ||
            isCreatingTracker ||
            editingTracker != null ||
            selectedTracker != null
    ) {
        when {
            pendingDeleteTracker != null -> pendingDeleteTracker = null
            editingDayContext != null -> editingDayContext = null
            isCreatingTracker || editingTracker != null -> {
                isCreatingTracker = false
                editingTracker = null
            }
            selectedTracker != null -> selectedTrackerId = null
        }
    }

    fun persistUpsertTracker(tracker: RecurringTracker, feedback: String?) {
        val updated = trackers.toMutableList().apply {
            val idx = indexOfFirst { it.id == tracker.id }
            if (idx >= 0) this[idx] = tracker else add(0, tracker)
        }
        onTrackersUpdated(updated)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.upsertTracker(vaultMode, tracker)
            }
            onTrackersUpdated(saved)
        }
        if (!feedback.isNullOrBlank()) feedbackBanner = feedback
    }

    fun persistDayEntry(tracker: RecurringTracker, dateKey: String, entry: TrackerDayEntry?) {
        val nextEntries = tracker.entries.toMutableMap().apply {
            if (entry == null) remove(dateKey) else put(dateKey, entry)
        }
        val optimisticTracker = tracker.copy(entries = nextEntries, updatedAt = System.currentTimeMillis())
        val optimisticList = trackers.map { if (it.id == tracker.id) optimisticTracker else it }
        onTrackersUpdated(optimisticList)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.setTrackerDayEntry(vaultMode, tracker.id, dateKey, entry)
            }
            onTrackersUpdated(saved)
        }
    }

    fun cycleDayEntry(tracker: RecurringTracker, dateKey: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val existing = tracker.entries[dateKey]
        val now = System.currentTimeMillis()
        val nextEntry = when (existing?.status) {
            null -> TrackerDayEntry(
                dateKey = dateKey,
                status = TrackerEntryStatus.PRESENT,
                quantity = tracker.defaultQuantity,
                note = "",
                updatedAt = now
            )
            TrackerEntryStatus.PRESENT -> existing.copy(
                status = TrackerEntryStatus.ABSENT,
                updatedAt = now
            )
            TrackerEntryStatus.ABSENT -> existing.copy(
                status = TrackerEntryStatus.SKIPPED,
                updatedAt = now
            )
            TrackerEntryStatus.SKIPPED -> null
        }
        persistDayEntry(tracker, dateKey, nextEntry)
    }

    fun persistDeleteTracker(tracker: RecurringTracker) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val remaining = trackers.filterNot { it.id == tracker.id }
        onTrackersUpdated(remaining)
        if (selectedTrackerId == tracker.id) {
            selectedTrackerId = null
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.deleteTracker(vaultMode, tracker.id)
            }
            onTrackersUpdated(saved)
        }
        feedbackBanner = "Deleted \"${tracker.name}\"."
    }

    if (selectedTracker != null) {
        TrackerCalendarDetailView(
            tracker = selectedTracker,
            viewedYear = viewedYear,
            viewedMonth = viewedMonth,
            todayDateKey = todayDateKey,
            feedbackBanner = feedbackBanner,
            onBackToList = { selectedTrackerId = null },
            onPrevMonth = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, viewedYear)
                    set(Calendar.MONTH, viewedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                viewedYear = cal.get(Calendar.YEAR)
                viewedMonth = cal.get(Calendar.MONTH)
            },
            onNextMonth = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, viewedYear)
                    set(Calendar.MONTH, viewedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 1)
                }
                viewedYear = cal.get(Calendar.YEAR)
                viewedMonth = cal.get(Calendar.MONTH)
            },
            onJumpToday = {
                viewedYear = todayYear
                viewedMonth = todayMonth
            },
            onCycleDate = { dateKey ->
                cycleDayEntry(selectedTracker, dateKey)
            },
            onOpenDayEditor = { dateKey ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                editingDayContext = selectedTracker to dateKey
            },
            onEditTracker = { editingTracker = selectedTracker },
            onMarkTodayPresent = {
                val existing = selectedTracker.entries[todayDateKey]
                val next = TrackerDayEntry(
                    dateKey = todayDateKey,
                    status = TrackerEntryStatus.PRESENT,
                    quantity = existing?.quantity ?: selectedTracker.defaultQuantity,
                    note = existing?.note ?: "",
                    updatedAt = System.currentTimeMillis()
                )
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                persistDayEntry(selectedTracker, todayDateKey, next)
                feedbackBanner = "Marked today as Present."
            },
            modifier = modifier
        )
    } else {
        val counts = remember(trackers) {
            mapOf(
                TrackerListFilter.ACTIVE to trackers.count { !it.archived },
                TrackerListFilter.ARCHIVED to trackers.count { it.archived },
                TrackerListFilter.ALL to trackers.size
            )
        }

        val filteredTrackers = remember(trackers, searchQuery, listFilter) {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            trackers.filter { t ->
                val matchesFilter = when (listFilter) {
                    TrackerListFilter.ACTIVE -> !t.archived
                    TrackerListFilter.ARCHIVED -> t.archived
                    TrackerListFilter.ALL -> true
                }
                if (!matchesFilter) return@filter false
                if (q.isEmpty()) return@filter true
                t.name.lowercase(Locale.getDefault()).contains(q) ||
                    t.unitLabel.lowercase(Locale.getDefault()).contains(q) ||
                    t.notes.lowercase(Locale.getDefault()).contains(q)
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackerListFilter.entries.forEach { filter ->
                        val selected = listFilter == filter
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
                                .clickable { listFilter = filter }
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

                if (filteredTrackers.isEmpty()) {
                    TrackersEmptyState(
                        hasTrackers = trackers.isNotEmpty(),
                        hasSearchQuery = searchQuery.trim().isNotEmpty(),
                        searchQuery = searchQuery,
                        filterLabel = listFilter.label,
                        onCreateTracker = { isCreatingTracker = true },
                        onQuickAddPreset = { preset ->
                            val now = System.currentTimeMillis()
                            val created = RecurringTracker(
                                id = "trk_${now}_${UUID.randomUUID().toString().take(4)}",
                                name = preset.name,
                                unitLabel = preset.unit,
                                defaultQuantity = preset.defaultQty,
                                ratePerUnit = preset.rate,
                                currencySymbol = "₹",
                                accentHex = preset.hex,
                                notes = "",
                                archived = false,
                                entries = emptyMap(),
                                createdAt = now,
                                updatedAt = now
                            )
                            persistUpsertTracker(created, "Created \"${created.name}\" tracker.")
                            selectedTrackerId = created.id
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
                        items(filteredTrackers, key = { it.id }) { tracker ->
                            val monthSummary = remember(tracker, currentMonthPrefix) {
                                computeMonthlySummary(tracker, currentMonthPrefix)
                            }
                            TrackerSummaryCard(
                                tracker = tracker,
                                todayEntry = tracker.entries[todayDateKey],
                                monthSummary = monthSummary,
                                onOpenCalendar = {
                                    viewedYear = todayYear
                                    viewedMonth = todayMonth
                                    selectedTrackerId = tracker.id
                                },
                                onCycleToday = { cycleDayEntry(tracker, todayDateKey) },
                                onEdit = { editingTracker = tracker }
                            )
                        }
                    }
                }
            }

            // Bottom-Right Floating Rectangular Button (+ Tracker)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .height(44.dp)
                    .shadow(4.dp, RectangleShape)
                    .background(colors.accent)
                    .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                    .clickable { isCreatingTracker = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New Tracker",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Tracker",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }

    if (isCreatingTracker || editingTracker != null) {
        TrackerEditorDialog(
            initialTracker = editingTracker,
            onDismiss = {
                isCreatingTracker = false
                editingTracker = null
            },
            onSave = { savedTracker ->
                val isNew = editingTracker == null
                persistUpsertTracker(
                    savedTracker,
                    if (isNew) "Added \"${savedTracker.name}\"." else "Updated \"${savedTracker.name}\"."
                )
                if (isNew) {
                    viewedYear = todayYear
                    viewedMonth = todayMonth
                    selectedTrackerId = savedTracker.id
                }
                isCreatingTracker = false
                editingTracker = null
            },
            onToggleArchive = { target ->
                val toggled = target.copy(
                    archived = !target.archived,
                    updatedAt = System.currentTimeMillis()
                )
                persistUpsertTracker(
                    toggled,
                    if (toggled.archived) "Archived \"${toggled.name}\"." else "Restored \"${toggled.name}\"."
                )
                editingTracker = null
            },
            onRequestDelete = { target ->
                editingTracker = null
                pendingDeleteTracker = target
            }
        )
    }

    editingDayContext?.let { (tracker, dateKey) ->
        TrackerDayEntryDialog(
            tracker = tracker,
            dateKey = dateKey,
            initialEntry = tracker.entries[dateKey],
            onDismiss = { editingDayContext = null },
            onSave = { updatedEntry ->
                persistDayEntry(tracker, dateKey, updatedEntry)
                editingDayContext = null
            }
        )
    }

    pendingDeleteTracker?.let { target ->
        LikkhoHairlineDialog(
            onDismissRequest = { pendingDeleteTracker = null },
            title = "Delete Tracker",
            leadingIcon = Icons.Outlined.WarningAmber,
            leadingIconTint = colors.crimson,
            borderColor = colors.crimson,
            maxWidth = 380.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    onClick = { pendingDeleteTracker = null }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Delete",
                    bgColor = colors.crimson,
                    onClick = {
                        persistDeleteTracker(target)
                        pendingDeleteTracker = null
                    }
                )
            }
        ) {
            Text(
                text = "Delete \"${target.name}\" and all ${target.entries.size} recorded calendar entries permanently?",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.text
            )
        }
    }
}

@Composable
private fun TrackerSummaryCard(
    tracker: RecurringTracker,
    todayEntry: TrackerDayEntry?,
    monthSummary: MonthlyTrackerSummary,
    onOpenCalendar: () -> Unit,
    onCycleToday: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val accentColor = remember(tracker.accentHex) {
        parseHexColor(tracker.accentHex, colors.accent)
    }

    val unitSuffix = tracker.unitLabel.trim().let { if (it.isEmpty()) "" else " $it" }
    val subtitleParts = buildList {
        add("Default ${formatQty(tracker.defaultQuantity)}$unitSuffix")
        if (tracker.ratePerUnit > 0.0) {
            val perUnit = if (tracker.unitLabel.isNotBlank()) "/${tracker.unitLabel.trim()}" else "/day"
            add("${formatAmount(tracker.currencySymbol, tracker.ratePerUnit)}$perUnit")
        }
        if (tracker.archived) add("Archived")
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (tracker.archived) 0.72f else 1f)
            .background(colors.bg)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
            .clickable { onOpenCalendar() }
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Title + Today Quick Badge + Edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tracker.name,
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleParts,
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TodayQuickBadge(
                        entry = todayEntry,
                        unitLabel = tracker.unitLabel,
                        onClick = onCycleToday
                    )
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
                            contentDescription = "Edit tracker",
                            tint = colors.muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

            // Bottom Row: Monthly Summary Pill + Open Calendar cue
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .background(colors.surface)
                        .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${monthSummary.presentDays}d ✓",
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.emerald,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    if (monthSummary.absentDays > 0) {
                        Text(
                            text = "${monthSummary.absentDays}d ✗",
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = colors.crimson,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                    if (monthSummary.totalQuantity > 0.0 && tracker.unitLabel.isNotBlank()) {
                        Text(
                            text = "${formatQty(monthSummary.totalQuantity)}$unitSuffix",
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 11.sp,
                                color = colors.text,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                    if (tracker.ratePerUnit > 0.0) {
                        Text(
                            text = formatAmount(tracker.currencySymbol, monthSummary.totalAmount),
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.accent,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Calendar",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.accent
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayQuickBadge(
    entry: TrackerDayEntry?,
    unitLabel: String,
    onClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val (bg, border, textColor, label) = when (entry?.status) {
        TrackerEntryStatus.PRESENT -> {
            val qtyStr = entry.quantity?.let { q ->
                if (unitLabel.isNotBlank()) " ${formatQty(q)} ${unitLabel.trim()}" else " ${formatQty(q)}"
            }.orEmpty()
            Quad(
                colors.emerald.copy(alpha = 0.14f),
                colors.emerald,
                colors.emerald,
                "Today ✓$qtyStr"
            )
        }
        TrackerEntryStatus.ABSENT -> Quad(
            colors.crimson.copy(alpha = 0.12f),
            colors.crimson,
            colors.crimson,
            "Today ✗"
        )
        TrackerEntryStatus.SKIPPED -> Quad(
            colors.surface,
            colors.border,
            colors.muted,
            "Today —"
        )
        null -> Quad(
            colors.surface,
            colors.border,
            colors.text,
            "Mark Today"
        )
    }

    Box(
        modifier = Modifier
            .height(30.dp)
            .background(bg)
            .border(LikkhoHairlineBorder, border, RectangleShape)
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor,
            maxLines = 1
        )
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun TrackerCalendarDetailView(
    tracker: RecurringTracker,
    viewedYear: Int,
    viewedMonth: Int,
    todayDateKey: String,
    feedbackBanner: String?,
    onBackToList: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToday: () -> Unit,
    onCycleDate: (String) -> Unit,
    onOpenDayEditor: (String) -> Unit,
    onEditTracker: () -> Unit,
    onMarkTodayPresent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val accentColor = remember(tracker.accentHex) {
        parseHexColor(tracker.accentHex, colors.accent)
    }
    val monthPrefix = remember(viewedYear, viewedMonth) {
        formatMonthPrefix(viewedYear, viewedMonth)
    }
    val monthTitle = remember(viewedYear, viewedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
    }
    val monthSummary = remember(tracker, monthPrefix) {
        computeMonthlySummary(tracker, monthPrefix)
    }
    val daysInMonth = remember(viewedYear, viewedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val firstDayOffset = remember(viewedYear, viewedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        // Sunday = 1 -> offset 0 ... Saturday = 7 -> offset 6
        (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
    }
    val monthNotes = remember(tracker.entries, monthPrefix) {
        tracker.entries.values
            .filter { it.dateKey.startsWith(monthPrefix) && it.note.isNotBlank() }
            .sortedBy { it.dateKey }
    }

    val isViewingCurrentMonth = todayDateKey.startsWith(monthPrefix)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Detail Sub-Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 10.dp, vertical = 9.dp),
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
                        .size(34.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onBackToList() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to Trackers",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp)
                        .background(accentColor)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tracker.name,
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val rateMeta = buildList {
                        add("${formatQty(tracker.defaultQuantity)}${if (tracker.unitLabel.isNotBlank()) " ${tracker.unitLabel}" else ""}")
                        if (tracker.ratePerUnit > 0.0) {
                            add("${formatAmount(tracker.currencySymbol, tracker.ratePerUnit)}/${tracker.unitLabel.ifBlank { "day" }}")
                        }
                    }.joinToString(" · ")
                    Text(
                        text = rateMeta,
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(colors.emerald.copy(alpha = 0.12f))
                        .border(LikkhoHairlineBorder, colors.emerald, RectangleShape)
                        .clickable { onMarkTodayPresent() }
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Today ✓",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.emerald
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onEditTracker() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit tracker",
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.border)

        if (!feedbackBanner.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.emerald.copy(alpha = 0.08f))
                    .border(LikkhoHairlineBorder, colors.emerald, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.emerald,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = feedbackBanner,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Navigator & Calendar Card
            item(key = "calendar_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                ) {
                    // < Month YYYY >
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                .clickable { onPrevMonth() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Previous month",
                                tint = colors.text,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = monthTitle,
                                fontFamily = LikkhoFontFamilies.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colors.text
                            )
                            if (!isViewingCurrentMonth) {
                                Box(
                                    modifier = Modifier
                                        .background(colors.bg)
                                        .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
                                        .clickable { onJumpToday() }
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Today",
                                        fontFamily = LikkhoFontFamilies.Sans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = colors.accent
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                .clickable { onNextMonth() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Next month",
                                tint = colors.text,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    // Weekday Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
                            Text(
                                text = dayName,
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = colors.muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    // 6x7 Calendar Grid
                    val totalCells = firstDayOffset + daysInMonth
                    val rows = (totalCells + 6) / 7
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0 until 7) {
                                    val cellIdx = row * 7 + col
                                    val dayNumber = cellIdx - firstDayOffset + 1
                                    if (dayNumber < 1 || dayNumber > daysInMonth) {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                        )
                                    } else {
                                        val dateKey = formatDateKey(viewedYear, viewedMonth, dayNumber)
                                        val entry = tracker.entries[dateKey]
                                        val isToday = dateKey == todayDateKey
                                        CalendarDayCell(
                                            dayNumber = dayNumber,
                                            entry = entry,
                                            defaultQty = tracker.defaultQuantity,
                                            unitLabel = tracker.unitLabel,
                                            isToday = isToday,
                                            onClick = { onCycleDate(dateKey) },
                                            onLongClick = { onOpenDayEditor(dateKey) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    // Legend & Gesture Hint
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tap day: ✓ / ✗ / Skip · Long-press: qty & note",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 10.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Automatic Monthly Summary Card
            item(key = "monthly_summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MONTHLY SUMMARY",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.7.sp,
                            color = colors.muted
                        )
                        Text(
                            text = monthTitle,
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.muted
                        )
                    }
                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bg)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatTile(
                            label = "Present ✓",
                            value = "${monthSummary.presentDays} d",
                            valueColor = colors.emerald,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Missed ✗",
                            value = "${monthSummary.absentDays} d",
                            valueColor = colors.crimson,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Skipped",
                            value = "${monthSummary.skippedDays} d",
                            valueColor = colors.muted,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bg)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TOTAL QUANTITY",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp,
                                color = colors.muted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val unitSuffix = tracker.unitLabel.trim().let { if (it.isEmpty()) "" else " $it" }
                            Text(
                                text = "${formatQty(monthSummary.totalQuantity)}$unitSuffix",
                                style = TextStyle(
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = colors.text,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }

                        if (tracker.ratePerUnit > 0.0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ESTIMATED BILL",
                                    fontFamily = LikkhoFontFamilies.Sans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp,
                                    color = colors.muted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatAmount(tracker.currencySymbol, monthSummary.totalAmount),
                                    style = TextStyle(
                                        fontFamily = LikkhoFontFamilies.Mono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = colors.accent,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                        }
                    }

                    if (tracker.ratePerUnit > 0.0) {
                        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                        val unitStr = tracker.unitLabel.trim().ifBlank { "unit" }
                        Text(
                            text = "${formatQty(monthSummary.totalQuantity)} $unitStr × ${formatAmount(tracker.currencySymbol, tracker.ratePerUnit)} = ${formatAmount(tracker.currencySymbol, monthSummary.totalAmount)}",
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 11.sp,
                                color = colors.muted,
                                fontFeatureSettings = "tnum"
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (tracker.notes.isNotBlank()) {
                item(key = "tracker_notes") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "TRACKER NOTES",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp,
                            color = colors.muted
                        )
                        Text(
                            text = tracker.notes,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.text
                        )
                    }
                }
            }

            if (monthNotes.isNotEmpty()) {
                item(key = "day_notes_header") {
                    Text(
                        text = "DAY NOTES THIS MONTH (${monthNotes.size})",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.7.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(monthNotes, key = { it.dateKey }) { dayEntry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { onOpenDayEditor(dayEntry.dateKey) }
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatFriendlyDate(dayEntry.dateKey),
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = colors.accent
                                )
                                val statusBadge = when (dayEntry.status) {
                                    TrackerEntryStatus.PRESENT -> "✓"
                                    TrackerEntryStatus.ABSENT -> "✗"
                                    TrackerEntryStatus.SKIPPED -> "Skip"
                                }
                                Text(
                                    text = statusBadge,
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = when (dayEntry.status) {
                                        TrackerEntryStatus.PRESENT -> colors.emerald
                                        TrackerEntryStatus.ABSENT -> colors.crimson
                                        TrackerEntryStatus.SKIPPED -> colors.muted
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dayEntry.note,
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.text
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit day entry",
                            tint = colors.muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    entry: TrackerDayEntry?,
    defaultQty: Double,
    unitLabel: String,
    isToday: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val (bgColor, borderColor, badgeText, badgeColor) = when (entry?.status) {
        TrackerEntryStatus.PRESENT -> {
            val qtyVal = entry.quantity ?: defaultQty
            val showQty = abs(qtyVal - defaultQty) > 0.001 || (unitLabel.isNotBlank() && qtyVal > 0.0)
            val label = if (showQty) "✓${formatQty(qtyVal)}" else "✓"
            Quad(
                colors.emerald.copy(alpha = 0.14f),
                if (isToday) colors.accent else colors.emerald.copy(alpha = 0.7f),
                label,
                colors.emerald
            )
        }
        TrackerEntryStatus.ABSENT -> Quad(
            colors.crimson.copy(alpha = 0.12f),
            if (isToday) colors.accent else colors.crimson.copy(alpha = 0.7f),
            "✗",
            colors.crimson
        )
        TrackerEntryStatus.SKIPPED -> Quad(
            colors.surface,
            if (isToday) colors.accent else colors.border,
            "—",
            colors.muted
        )
        null -> Quad(
            colors.bg,
            if (isToday) colors.accent else colors.hairline,
            "",
            colors.muted
        )
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .background(bgColor)
            .border(if (isToday) 1.5.dp else LikkhoHairlineBorder, borderColor, RectangleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(3.dp)
    ) {
        Text(
            text = "$dayNumber",
            style = TextStyle(
                fontFamily = LikkhoFontFamilies.Mono,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                color = if (isToday) colors.accent else colors.text,
                fontFeatureSettings = "tnum"
            ),
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (!entry?.note.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(colors.accent)
                    .align(Alignment.TopEnd)
            )
        }

        if (badgeText.isNotEmpty()) {
            Text(
                text = badgeText,
                style = TextStyle(
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = badgeColor,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface)
            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = TextStyle(
                fontFamily = LikkhoFontFamilies.Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = valueColor,
                fontFeatureSettings = "tnum"
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontSize = 10.sp,
            color = colors.muted,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackersEmptyState(
    hasTrackers: Boolean,
    hasSearchQuery: Boolean,
    searchQuery: String,
    filterLabel: String,
    onCreateTracker: () -> Unit,
    onQuickAddPreset: (TrackerPreset) -> Unit,
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
                .padding(22.dp),
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
                    imageVector = if (hasSearchQuery) Icons.Outlined.Search else Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when {
                    hasSearchQuery -> "No matching trackers"
                    hasTrackers -> "No $filterLabel trackers"
                    else -> "No Trackers Yet"
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
                    hasSearchQuery -> "No trackers match \"${searchQuery.trim()}\"."
                    hasTrackers -> "Switch the filter above or create a new tracker."
                    else -> "Track daily deliveries, habits, or attendance with 1-tap calendar marking and automatic monthly totals."
                },
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.muted,
                textAlign = TextAlign.Center
            )
            if (!hasSearchQuery && !hasTrackers) {
                Spacer(modifier = Modifier.height(16.dp))
                LikkhoDialogPrimaryButton(
                    text = "Create Tracker",
                    leadingIcon = Icons.Outlined.Add,
                    onClick = onCreateTracker
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "OR START WITH A QUICK TEMPLATE",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TRACKER_PRESETS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                .clickable { onQuickAddPreset(preset) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ ${preset.name}",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = colors.text
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Create / Edit Tracker Dialog ====================

@Composable
private fun TrackerEditorDialog(
    initialTracker: RecurringTracker?,
    onDismiss: () -> Unit,
    onSave: (RecurringTracker) -> Unit,
    onToggleArchive: (RecurringTracker) -> Unit,
    onRequestDelete: (RecurringTracker) -> Unit
) {
    val colors = LikkhoTheme.colors
    val focusRequester = remember { FocusRequester() }

    var name by remember(initialTracker?.id) { mutableStateOf(initialTracker?.name ?: "") }
    var unitLabel by remember(initialTracker?.id) { mutableStateOf(initialTracker?.unitLabel ?: "") }
    var defaultQtyText by remember(initialTracker?.id) {
        mutableStateOf(formatQty(initialTracker?.defaultQuantity ?: 1.0))
    }
    var rateText by remember(initialTracker?.id) {
        val r = initialTracker?.ratePerUnit ?: 0.0
        mutableStateOf(if (r > 0.0) formatQty(r) else "")
    }
    var currencySymbol by remember(initialTracker?.id) {
        mutableStateOf(initialTracker?.currencySymbol ?: "₹")
    }
    var accentHex by remember(initialTracker?.id) {
        mutableStateOf(initialTracker?.accentHex ?: "#3366CC")
    }
    var notes by remember(initialTracker?.id) { mutableStateOf(initialTracker?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (initialTracker == null) {
            focusRequester.requestFocus()
        }
    }

    fun submit() {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            errorMsg = "Please enter a tracker name (e.g. Milk, Maid, Newspaper)."
            return
        }
        val parsedQty = defaultQtyText.trim().toDoubleOrNull()
        if (parsedQty == null || parsedQty <= 0.0) {
            errorMsg = "Default quantity must be greater than 0."
            return
        }
        val parsedRate = if (rateText.trim().isEmpty()) {
            0.0
        } else {
            val r = rateText.trim().toDoubleOrNull()
            if (r == null || r < 0.0) {
                errorMsg = "Rate per unit must be 0 or greater."
                return
            }
            r
        }
        val now = System.currentTimeMillis()
        val saved = RecurringTracker(
            id = initialTracker?.id ?: "trk_${now}_${UUID.randomUUID().toString().take(4)}",
            name = cleanName,
            unitLabel = unitLabel.trim(),
            defaultQuantity = parsedQty,
            ratePerUnit = parsedRate,
            currencySymbol = currencySymbol.ifBlank { "₹" },
            accentHex = accentHex,
            notes = notes.trim(),
            archived = initialTracker?.archived ?: false,
            entries = initialTracker?.entries ?: emptyMap(),
            createdAt = initialTracker?.createdAt ?: now,
            updatedAt = now
        )
        onSave(saved)
    }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = if (initialTracker != null) "Edit Tracker" else "New Tracker",
        leadingIcon = Icons.Outlined.CalendarMonth,
        maxWidth = 440.dp,
        maxHeight = 640.dp,
        bodyPadding = 16.dp,
        footer = {
            if (initialTracker != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(colors.crimson.copy(alpha = 0.08f))
                        .border(LikkhoHairlineBorder, colors.crimson.copy(alpha = 0.6f), RectangleShape)
                        .clickable { onRequestDelete(initialTracker) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete tracker",
                        tint = colors.crimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onToggleArchive(initialTracker) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (initialTracker.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = if (initialTracker.archived) "Restore tracker" else "Archive tracker",
                        tint = colors.text,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            LikkhoDialogOutlineButton(text = "Cancel", onClick = onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = "Save",
                leadingIcon = Icons.Outlined.Check,
                onClick = { submit() }
            )
        }
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
                    .border(LikkhoHairlineBorder, colors.crimson, RectangleShape)
                    .padding(10.dp)
            )
        }

        if (initialTracker == null) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "QUICK TEMPLATES",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = colors.muted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TRACKER_PRESETS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .background(colors.surface)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                .clickable {
                                    name = preset.name
                                    unitLabel = preset.unit
                                    defaultQtyText = formatQty(preset.defaultQty)
                                    rateText = if (preset.rate > 0.0) formatQty(preset.rate) else ""
                                    accentHex = preset.hex
                                    errorMsg = null
                                }
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.name,
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 11.sp,
                                color = colors.text
                            )
                        }
                    }
                }
            }
        }

        // Name
        TrackerLabeledField(
            label = "NAME",
            value = name,
            placeholder = "e.g. Milk, Maid, Newspaper, Gym...",
            onValueChange = {
                name = it
                errorMsg = null
            },
            modifier = Modifier.focusRequester(focusRequester)
        )

        // Unit & Default Qty
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackerLabeledField(
                label = "DEFAULT QTY",
                value = defaultQtyText,
                placeholder = "1",
                keyboardType = KeyboardType.Decimal,
                onValueChange = {
                    defaultQtyText = it
                    errorMsg = null
                },
                modifier = Modifier.weight(1f)
            )
            TrackerLabeledField(
                label = "UNIT (OPTIONAL)",
                value = unitLabel,
                placeholder = "L, kg, visit...",
                onValueChange = { unitLabel = it },
                modifier = Modifier.weight(1f)
            )
        }

        // Optional Rate & Currency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TrackerLabeledField(
                label = "RATE PER UNIT (OPTIONAL)",
                value = rateText,
                placeholder = "0 (no bill)",
                keyboardType = KeyboardType.Decimal,
                onValueChange = {
                    rateText = it
                    errorMsg = null
                },
                modifier = Modifier.weight(1f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "CURRENCY",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = colors.muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CURRENCY_OPTIONS.forEach { sym ->
                        val selected = currencySymbol == sym
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(if (selected) colors.accent else colors.surface)
                                .border(
                                    LikkhoHairlineBorder,
                                    if (selected) colors.accentHover else colors.border,
                                    RectangleShape
                                )
                                .clickable { currencySymbol = sym },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sym,
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selected) Color.White else colors.text
                            )
                        }
                    }
                }
            }
        }

        // Accent Swatches
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ACCENT COLOR",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = colors.muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TRACKER_ACCENT_SWATCHES.forEach { hex ->
                    val swatchColor = parseHexColor(hex, colors.accent)
                    val selected = accentHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(swatchColor)
                            .border(
                                if (selected) 2.dp else LikkhoHairlineBorder,
                                if (selected) colors.text else colors.border,
                                RectangleShape
                            )
                            .clickable { accentHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Notes
        TrackerLabeledField(
            label = "NOTES (OPTIONAL)",
            value = notes,
            placeholder = "Vendor phone, delivery timing, reminders...",
            singleLine = false,
            onValueChange = { notes = it }
        )
    }
}

// ==================== Day Entry Detail Modal (Long-Press) ====================

@Composable
private fun TrackerDayEntryDialog(
    tracker: RecurringTracker,
    dateKey: String,
    initialEntry: TrackerDayEntry?,
    onDismiss: () -> Unit,
    onSave: (TrackerDayEntry?) -> Unit
) {
    val colors = LikkhoTheme.colors
    var selectedStatus by remember(dateKey) {
        mutableStateOf<TrackerEntryStatus?>(initialEntry?.status ?: TrackerEntryStatus.PRESENT)
    }
    var qtyText by remember(dateKey) {
        mutableStateOf(formatQty(initialEntry?.quantity ?: tracker.defaultQuantity))
    }
    var note by remember(dateKey) { mutableStateOf(initialEntry?.note ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = formatFriendlyDate(dateKey),
        subtitle = tracker.name,
        leadingIcon = Icons.Outlined.CalendarMonth,
        maxWidth = 400.dp,
        footer = {
            LikkhoDialogOutlineButton(
                text = "Clear Day",
                textColor = colors.crimson,
                borderColor = colors.crimson.copy(alpha = 0.6f),
                bgColor = colors.crimson.copy(alpha = 0.08f),
                onClick = { onSave(null) }
            )
            Spacer(modifier = Modifier.weight(1f))
            LikkhoDialogOutlineButton(text = "Cancel", onClick = onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = "Save",
                leadingIcon = Icons.Outlined.Check,
                onClick = {
                    val status = selectedStatus
                    if (status == null) {
                        onSave(null)
                        return@LikkhoDialogPrimaryButton
                    }
                    val parsedQty = if (status == TrackerEntryStatus.PRESENT) {
                        val q = qtyText.trim().toDoubleOrNull()
                        if (q == null || q < 0.0) {
                            errorMsg = "Enter a valid quantity."
                            return@LikkhoDialogPrimaryButton
                        }
                        q
                    } else {
                        qtyText.trim().toDoubleOrNull()
                    }
                    onSave(
                        TrackerDayEntry(
                            dateKey = dateKey,
                            status = status,
                            quantity = parsedQty,
                            note = note.trim(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            )
        }
    ) {
        if (!errorMsg.isNullOrBlank()) {
            Text(
                text = errorMsg!!,
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.crimson,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.crimson.copy(alpha = 0.08f))
                    .border(LikkhoHairlineBorder, colors.crimson, RectangleShape)
                    .padding(10.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "STATUS",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = colors.muted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChoiceButton(
                    label = "Present ✓",
                    selected = selectedStatus == TrackerEntryStatus.PRESENT,
                    activeColor = colors.emerald,
                    icon = Icons.Outlined.Check,
                    onClick = { selectedStatus = TrackerEntryStatus.PRESENT },
                    modifier = Modifier.weight(1f)
                )
                StatusChoiceButton(
                    label = "Missed ✗",
                    selected = selectedStatus == TrackerEntryStatus.ABSENT,
                    activeColor = colors.crimson,
                    icon = Icons.Outlined.Close,
                    onClick = { selectedStatus = TrackerEntryStatus.ABSENT },
                    modifier = Modifier.weight(1f)
                )
                StatusChoiceButton(
                    label = "Skipped",
                    selected = selectedStatus == TrackerEntryStatus.SKIPPED,
                    activeColor = colors.accent,
                    icon = Icons.Outlined.Remove,
                    onClick = { selectedStatus = TrackerEntryStatus.SKIPPED },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val unitSuffix = tracker.unitLabel.trim().let { if (it.isEmpty()) "" else " ($it)" }
        TrackerLabeledField(
            label = "QUANTITY$unitSuffix",
            value = qtyText,
            placeholder = formatQty(tracker.defaultQuantity),
            keyboardType = KeyboardType.Decimal,
            onValueChange = {
                qtyText = it
                errorMsg = null
            }
        )

        TrackerLabeledField(
            label = "DAY NOTE (OPTIONAL)",
            value = note,
            placeholder = "e.g. Extra 0.5L for guests, paid advance...",
            singleLine = false,
            onValueChange = { note = it }
        )
    }
}

@Composable
private fun StatusChoiceButton(
    label: String,
    selected: Boolean,
    activeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    Row(
        modifier = modifier
            .height(36.dp)
            .background(if (selected) activeColor.copy(alpha = 0.14f) else colors.surface)
            .border(
                LikkhoHairlineBorder,
                if (selected) activeColor else colors.border,
                RectangleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) activeColor else colors.muted,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            color = if (selected) activeColor else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackerLabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            color = colors.muted
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.height(42.dp) else Modifier.height(74.dp))
                .background(colors.surface)
                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                .padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 9.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    color = colors.muted,
                    maxLines = if (singleLine) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    color = colors.text
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== Helpers ====================

private fun computeMonthlySummary(
    tracker: RecurringTracker,
    monthPrefix: String
): MonthlyTrackerSummary {
    var present = 0
    var absent = 0
    var skipped = 0
    var qtySum = 0.0

    tracker.entries.values.forEach { entry ->
        if (!entry.dateKey.startsWith(monthPrefix)) return@forEach
        when (entry.status) {
            TrackerEntryStatus.PRESENT -> {
                present++
                qtySum += entry.quantity ?: tracker.defaultQuantity
            }
            TrackerEntryStatus.ABSENT -> absent++
            TrackerEntryStatus.SKIPPED -> skipped++
        }
    }
    return MonthlyTrackerSummary(
        presentDays = present,
        absentDays = absent,
        skippedDays = skipped,
        totalQuantity = qtySum,
        totalAmount = qtySum * tracker.ratePerUnit
    )
}

private fun formatMonthPrefix(year: Int, monthZeroBased: Int): String =
    String.format(Locale.US, "%04d-%02d-", year, monthZeroBased + 1)

private fun formatDateKey(year: Int, monthZeroBased: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, day)

private fun formatFriendlyDate(dateKey: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = parser.parse(dateKey) ?: return dateKey
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(parsed)
    } catch (_: Exception) {
        dateKey
    }
}

private fun formatQty(value: Double): String {
    val df = DecimalFormat("#.##")
    return df.format(value)
}

private fun formatAmount(currency: String, value: Double): String {
    val df = DecimalFormat("#,##0.##")
    return "$currency${df.format(value)}"
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        val parsed = android.graphics.Color.parseColor(hex.trim())
        Color(parsed)
    } catch (_: Exception) {
        fallback
    }
}

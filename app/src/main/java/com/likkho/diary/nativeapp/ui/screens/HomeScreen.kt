package com.likkho.diary.nativeapp.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.export.NativeExportFormat
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingDiaryUnlockAction {
    OPEN,
    EXPORT,
    DELETE
}

private data class PendingDiaryUnlockRequest(
    val log: DiaryLog,
    val action: PendingDiaryUnlockAction
)

private data class ExportFormatMeta(
    val format: NativeExportFormat,
    val label: String,
    val desc: String
)

private val NATIVE_EXPORT_FORMATS = listOf(
    ExportFormatMeta(NativeExportFormat.TXT, ".txt", "Plain Text Document"),
    ExportFormatMeta(NativeExportFormat.MD, ".md", "Markdown Document"),
    ExportFormatMeta(NativeExportFormat.PDF, ".pdf", "PDF Printable Document (Full Canvas)"),
    ExportFormatMeta(NativeExportFormat.RTF, ".rtf", "Rich Text Format"),
    ExportFormatMeta(NativeExportFormat.HTML, ".html", "Web Page Archive"),
    ExportFormatMeta(NativeExportFormat.JSON, ".json", "Structured JSON Data"),
    ExportFormatMeta(NativeExportFormat.CSV, ".csv", "Comma-Separated Values"),
    ExportFormatMeta(NativeExportFormat.TSV, ".tsv", "Tab-Separated Values"),
    ExportFormatMeta(NativeExportFormat.XML, ".xml", "Extensible Markup Language")
)

/**
 * Homepage Diary List Screen.
 *
 * Parity with `src/App.tsx`:
 * - Top Wikipedia editorial header (`Likkho` / live Search bar, Instant Lock, Settings menu)
 * - Optional status & processing banners
 * - Pinned + chronologically sorted diary list with 1:1 PFP (cached thumbnail + Monograph fallback),
 *   pin/lock indicators, 1-line preview, Created/Modified/Reminder timestamps, and 3-dots action menu
 * - Individual Diary PIN unlock dialog with shared progressive brute-force cooldown
 * - Delete confirmation dialog & Multi-format Export picker dialog
 * - Floating rectangular `+ Create` button
 */
@Composable
fun HomeScreen(
    logs: List<DiaryLog>,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    statusBanner: String? = null,
    processingBanner: String? = null,
    onDismissStatusBanner: () -> Unit = {},
    onLockApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateDiary: () -> Unit,
    onOpenDiary: (DiaryLog) -> Unit,
    onTogglePin: (DiaryLog) -> Unit,
    onShareLikkho: (DiaryLog) -> Unit,
    onExportDiary: (DiaryLog, NativeExportFormat) -> Unit,
    onDeleteDiary: (DiaryLog) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val isBusy = !processingBanner.isNullOrBlank()

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var openMenuLogId by remember { mutableStateOf<String?>(null) }

    var pendingUnlockRequest by remember { mutableStateOf<PendingDiaryUnlockRequest?>(null) }
    var pendingDeleteLog by remember { mutableStateOf<DiaryLog?>(null) }
    var exportingLog by remember { mutableStateOf<DiaryLog?>(null) }

    BackHandler(
        enabled = openMenuLogId != null ||
            pendingUnlockRequest != null ||
            pendingDeleteLog != null ||
            exportingLog != null ||
            isSearchOpen
    ) {
        when {
            openMenuLogId != null -> openMenuLogId = null
            pendingUnlockRequest != null -> pendingUnlockRequest = null
            pendingDeleteLog != null -> pendingDeleteLog = null
            exportingLog != null -> exportingLog = null
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
            }
        }
    }

    val sortedAndFilteredLogs = remember(logs, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        val base = if (q.isEmpty()) {
            logs
        } else {
            logs.filter { log ->
                log.heading.lowercase(Locale.getDefault()).contains(q) ||
                    log.plainPreview.lowercase(Locale.getDefault()).contains(q) ||
                    log.dateStamp.lowercase(Locale.getDefault()).contains(q) ||
                    (log.updatedDateStamp ?: "").lowercase(Locale.getDefault()).contains(q) ||
                    stripHtmlTags(log.contentHtml).lowercase(Locale.getDefault()).contains(q)
            }
        }
        base.sortedWith(
            compareByDescending<DiaryLog> { it.pinned }
                .thenByDescending { maxOf(it.updatedAt, it.createdAt) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Editorial Top Header
            HomeHeaderBar(
                isSearchOpen = isSearchOpen,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenSearch = { isSearchOpen = true },
                onCloseSearch = {
                    isSearchOpen = false
                    searchQuery = ""
                },
                onLockApp = onLockApp,
                onOpenSettings = onOpenSettings
            )

            // Optional Processing / Status Banners
            if (!processingBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(1.dp, colors.accent)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = processingBanner,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (!statusBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(1.dp, colors.emerald)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusBanner,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss banner",
                        tint = colors.muted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDismissStatusBanner() }
                    )
                }
            }

            // Diary List / Empty State
            if (sortedAndFilteredLogs.isEmpty()) {
                HomeEmptyState(
                    hasSearchQuery = searchQuery.trim().isNotEmpty(),
                    searchQuery = searchQuery
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(
                        items = sortedAndFilteredLogs,
                        key = { it.id }
                    ) { log ->
                        DiaryLogRow(
                            log = log,
                            mediaEngine = mediaEngine,
                            isMenuOpen = openMenuLogId == log.id,
                            isBusy = isBusy,
                            onRowClick = {
                                if (!log.diaryLockPin.isNullOrEmpty()) {
                                    pendingUnlockRequest = PendingDiaryUnlockRequest(
                                        log = log,
                                        action = PendingDiaryUnlockAction.OPEN
                                    )
                                } else {
                                    onOpenDiary(log)
                                }
                            },
                            onToggleMenu = {
                                openMenuLogId = if (openMenuLogId == log.id) null else log.id
                            },
                            onDismissMenu = {
                                if (openMenuLogId == log.id) openMenuLogId = null
                            },
                            onTogglePin = {
                                openMenuLogId = null
                                onTogglePin(log)
                            },
                            onShare = {
                                openMenuLogId = null
                                onShareLikkho(log)
                            },
                            onExport = {
                                openMenuLogId = null
                                if (!log.diaryLockPin.isNullOrEmpty()) {
                                    pendingUnlockRequest = PendingDiaryUnlockRequest(
                                        log = log,
                                        action = PendingDiaryUnlockAction.EXPORT
                                    )
                                } else {
                                    exportingLog = log
                                }
                            },
                            onDelete = {
                                openMenuLogId = null
                                if (!log.diaryLockPin.isNullOrEmpty()) {
                                    pendingUnlockRequest = PendingDiaryUnlockRequest(
                                        log = log,
                                        action = PendingDiaryUnlockAction.DELETE
                                    )
                                } else {
                                    pendingDeleteLog = log
                                }
                            }
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                    }
                }
            }
        }

        // Floating Rectangular "+ Create" Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .height(54.dp)
                .shadow(6.dp)
                .background(colors.accent)
                .border(1.dp, colors.accentHover)
                .clickable { onCreateDiary() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Create new diary log",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Create",
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }

    // Individual Diary PIN Unlock Dialog
    pendingUnlockRequest?.let { req ->
        DiaryPinUnlockDialog(
            log = req.log,
            repository = repository,
            onDismiss = { pendingUnlockRequest = null },
            onUnlocked = {
                pendingUnlockRequest = null
                when (req.action) {
                    PendingDiaryUnlockAction.OPEN -> onOpenDiary(req.log)
                    PendingDiaryUnlockAction.EXPORT -> exportingLog = req.log
                    PendingDiaryUnlockAction.DELETE -> pendingDeleteLog = req.log
                }
            }
        )
    }

    // Delete Confirmation Dialog
    pendingDeleteLog?.let { targetLog ->
        DeleteDiaryConfirmDialog(
            log = targetLog,
            onDismiss = { pendingDeleteLog = null },
            onConfirmDelete = {
                pendingDeleteLog = null
                onDeleteDiary(targetLog)
            }
        )
    }

    // Multi-Format Export Picker Dialog
    exportingLog?.let { targetLog ->
        ExportDiaryFormatDialog(
            log = targetLog,
            onDismiss = { exportingLog = null },
            onSelectFormat = { fmt ->
                exportingLog = null
                onExportDiary(targetLog, fmt)
            }
        )
    }
}

@Composable
private fun HomeHeaderBar(
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onLockApp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSearchOpen) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(colors.bg)
                            .border(1.dp, colors.accent)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search diaries...",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.bg)
                            .border(1.dp, colors.border)
                            .clickable { onCloseSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close search",
                            tint = colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "Likkho",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = colors.text
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isSearchOpen) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.bg)
                            .border(1.dp, colors.border)
                            .clickable { onOpenSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search diaries",
                            tint = colors.text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .background(colors.bg)
                        .border(1.dp, colors.border)
                        .clickable { onLockApp() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Instant Lock",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Lock",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.text
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.bg)
                        .border(1.dp, colors.border)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open Settings",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

@Composable
private fun HomeEmptyState(
    hasSearchQuery: Boolean,
    searchQuery: String
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(colors.surface)
                .border(1.dp, colors.border),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasSearchQuery) "No Matching Diaries Found" else "No Logs Yet",
            fontFamily = LikkhoFontFamilies.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasSearchQuery) {
                "No diary entry contains \"$searchQuery\"."
            } else {
                "Tap the create button below to start writing."
            },
            fontFamily = LikkhoFontFamilies.Sans,
            fontSize = 12.sp,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DiaryLogRow(
    log: DiaryLog,
    mediaEngine: NativeMediaEngine,
    isMenuOpen: Boolean,
    isBusy: Boolean,
    onRowClick: () -> Unit,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val isLockedDiary = !log.diaryLockPin.isNullOrEmpty()
    val nowMs = System.currentTimeMillis()
    val hasUpcomingReminder = log.reminderAt != null && log.reminderAt > nowMs

    val pfpBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = log.pfpDataUrl) {
        value = withContext(Dispatchers.IO) {
            decodePfpThumbnailBitmap(mediaEngine, log.pfpDataUrl)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1:1 Square PFP or Fallback "Likkho" Monograph
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White)
                .border(1.dp, colors.border),
            contentAlignment = Alignment.Center
        ) {
            if (pfpBitmap != null) {
                Image(
                    bitmap = pfpBitmap!!,
                    contentDescription = log.heading,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Likkho",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF202122),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Heading, 1-line preview, Created / Modified / Reminder timestamps
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (log.pinned) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (isLockedDiary) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked Diary",
                        tint = colors.crimson,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = log.heading.ifBlank { "Untitled" },
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val previewText = when {
                isLockedDiary -> ""
                log.plainPreview == "Reminder scheduled." -> ""
                else -> log.plainPreview
            }
            Text(
                text = previewText,
                fontFamily = LikkhoFontFamilies.Prose,
                fontSize = 13.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val createdDate = log.dateStamp.ifBlank { formatShortDate(log.createdAt) }
            val createdTime = log.timeStamp.ifBlank { formatShortTime(log.createdAt) }
            val modifiedEpoch = if (log.updatedAt > 0L) log.updatedAt else log.createdAt
            val modifiedDate = log.updatedDateStamp?.takeIf { it.isNotBlank() } ?: formatShortDate(modifiedEpoch)
            val modifiedTime = log.updatedTimeStamp?.takeIf { it.isNotBlank() } ?: formatShortTime(modifiedEpoch)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Created:",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = colors.text.copy(alpha = 0.8f)
                )
                Text(
                    text = "$createdDate · $createdTime",
                    style = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Modified:",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = colors.text.copy(alpha = 0.8f)
                )
                Text(
                    text = "$modifiedDate · $modifiedTime",
                    style = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasUpcomingReminder) {
                    Text(
                        text = "·",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted
                    )
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = "Reminder",
                        tint = colors.accent,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = formatShortTime(log.reminderAt!!),
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.accent,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // 3-Dots Dropdown Menu
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onToggleMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Log options",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = onDismissMenu,
                modifier = Modifier
                    .background(colors.bg)
                    .border(1.dp, colors.border)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (log.pinned) "Unpin from Top" else "Pin to Top",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.text
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = onTogglePin
                )
                DropdownMenuItem(
                    enabled = !isBusy,
                    text = {
                        Text(
                            text = "Share (.likkho)",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.text
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = onShare
                )
                DropdownMenuItem(
                    enabled = !isBusy,
                    text = {
                        Text(
                            text = if (isBusy) "Exporting..." else "Export",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.text
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = onExport
                )
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.crimson
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.crimson,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun DiaryPinUnlockDialog(
    log: DiaryLog,
    repository: NativeVaultRepository,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var inputPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var cooldownSec by remember {
        mutableIntStateOf(repository.getRemainingPasscodeCooldownSeconds())
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(cooldownSec) {
        while (cooldownSec > 0) {
            delay(500L)
            cooldownSec = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
        }
    }

    fun submitUnlock() {
        scope.launch {
            val rem = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            if (rem > 0) {
                cooldownSec = rem
                errorMsg = "Too many wrong attempts. Wait ${rem}s."
                return@launch
            }
            if (inputPin.trim() == log.diaryLockPin) {
                withContext(Dispatchers.IO) {
                    repository.resetPasscodeFailures()
                }
                cooldownSec = 0
                onUnlocked()
            } else {
                val fail = withContext(Dispatchers.IO) {
                    repository.recordPasscodeFailure()
                }
                if (fail.cooldownSeconds > 0) {
                    cooldownSec = fail.cooldownSeconds
                    errorMsg = "Incorrect passcode. Locked for ${fail.cooldownSeconds}s."
                } else {
                    errorMsg = "Incorrect diary passcode."
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(colors.bg)
                .border(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.crimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Unlock \"${log.heading}\"",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = colors.muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDismiss() }
                )
            }
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (cooldownSec > 0) {
                    Text(
                        text = "Locked for ${cooldownSec}s due to continuous wrong attempts.",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.crimson
                    )
                } else if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = colors.crimson
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Enter Diary Passcode",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = colors.text
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .alpha(if (cooldownSec > 0) 0.5f else 1f)
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputPin.isEmpty()) {
                            Text(
                                text = "Passcode...",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                        BasicTextField(
                            value = inputPin,
                            onValueChange = {
                                inputPin = it
                                errorMsg = null
                            },
                            enabled = cooldownSec == 0,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { if (cooldownSec == 0) submitUnlock() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .background(colors.surface)
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .alpha(if (cooldownSec > 0) 0.5f else 1f)
                            .background(colors.accent)
                            .clickable(enabled = cooldownSec == 0) { submitUnlock() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (cooldownSec > 0) "Wait ${cooldownSec}s" else "Unlock",
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

@Composable
private fun DeleteDiaryConfirmDialog(
    log: DiaryLog,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(colors.bg)
                .border(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = colors.crimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Delete Entry?",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = colors.muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDismiss() }
                )
            }
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Are you sure you want to permanently delete \"${log.heading}\"? This action cannot be undone.",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.text
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp),
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .background(colors.crimson)
                            .clickable { onConfirmDelete() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Delete",
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

@Composable
private fun ExportDiaryFormatDialog(
    log: DiaryLog,
    onDismiss: () -> Unit,
    onSelectFormat: (NativeExportFormat) -> Unit
) {
    val colors = LikkhoTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(colors.bg)
                .border(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Export \"${log.heading}\"",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = colors.muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDismiss() }
                )
            }
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose a file format to save to Downloads/Likkho:",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    color = colors.muted
                )

                val chunked = NATIVE_EXPORT_FORMATS.chunked(2)
                chunked.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(colors.surface)
                                    .border(1.dp, colors.border)
                                    .clickable { onSelectFormat(item.format) }
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.accent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.desc,
                                    fontFamily = LikkhoFontFamilies.Sans,
                                    fontSize = 10.sp,
                                    color = colors.muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun decodePfpThumbnailBitmap(
    mediaEngine: NativeMediaEngine,
    pfpRef: String?
): ImageBitmap? {
    if (pfpRef.isNullOrBlank()) return null
    return try {
        if (pfpRef.startsWith("data:image/")) {
            val comma = pfpRef.indexOf(',')
            if (comma < 0) return null
            val bytes = Base64.decode(pfpRef.substring(comma + 1), Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = calculateSimpleSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                NativeMediaEngine.DEFAULT_PFP_PREVIEW_PX
            )
            val bmp = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
            bmp?.asImageBitmap()
        } else {
            val previewFile = mediaEngine.getOrGeneratePreviewFile(
                mediaIdOrRef = pfpRef,
                maxPreviewPx = NativeMediaEngine.DEFAULT_PFP_PREVIEW_PX,
                isPfp = true
            ) ?: return null
            BitmapFactory.decodeFile(previewFile.absolutePath)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

private fun calculateSimpleSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    var curW = width
    var curH = height
    while (curW / 2 >= maxDim && curH / 2 >= maxDim) {
        sample *= 2
        curW /= 2
        curH /= 2
    }
    return sample
}

private val HTML_TAG_REGEX = Regex("<[^>]*>")

private fun stripHtmlTags(html: String): String {
    if (html.isBlank()) return ""
    return html.replace(HTML_TAG_REGEX, " ")
}

private fun formatShortDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(epochMs))
}

private fun formatShortTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(epochMs))
}

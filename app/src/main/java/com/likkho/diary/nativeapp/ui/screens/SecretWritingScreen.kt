package com.likkho.diary.nativeapp.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.core.text.HtmlCompat
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.export.NativeExportFormat
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.DiaryLog
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
import java.util.Date
import java.util.Locale

private enum class SecretEntryUnlockAction {
    OPEN,
    EXPORT,
    DELETE
}

private data class SecretEntryUnlockRequest(
    val log: DiaryLog,
    val action: SecretEntryUnlockAction
)

private data class SecretExportFormatMeta(
    val format: NativeExportFormat,
    val label: String,
    val desc: String
)

private val SECRET_EXPORT_FORMATS = listOf(
    SecretExportFormatMeta(NativeExportFormat.TXT, ".txt", "Plain Text Document"),
    SecretExportFormatMeta(NativeExportFormat.MD, ".md", "Markdown Document"),
    SecretExportFormatMeta(NativeExportFormat.PDF, ".pdf", "PDF Printable Document"),
    SecretExportFormatMeta(NativeExportFormat.RTF, ".rtf", "Rich Text Format"),
    SecretExportFormatMeta(NativeExportFormat.HTML, ".html", "Web Page Archive"),
    SecretExportFormatMeta(NativeExportFormat.JSON, ".json", "Structured JSON Data"),
    SecretExportFormatMeta(NativeExportFormat.CSV, ".csv", "Comma-Separated Values"),
    SecretExportFormatMeta(NativeExportFormat.TSV, ".tsv", "Tab-Separated Values"),
    SecretExportFormatMeta(NativeExportFormat.XML, ".xml", "Extensible Markup Language")
)

/**
 * Pure Native Compose Secret Writing Journal Screen (`VaultMode.DECOY`).
 *
 * Distraction-free Wikipedia editorial journal list:
 * - Searchable, pinned + chronological entries
 * - Clean readable previews & attachment counters (legacy Decoy entries fully preserved)
 * - Individual entry PIN unlock dialog with progressive brute-force cooldown
 * - Multi-format Export & `.likkho` share archive
 * - Optional safe migration banner if legacy diaries exist in `real_vault`
 */
@Composable
fun SecretWritingScreen(
    logs: List<DiaryLog>,
    legacyPrimaryLogsCount: Int = 0,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    isLoadingLogs: Boolean = false,
    statusBanner: String? = null,
    processingBanner: String? = null,
    onDismissStatusBanner: () -> Unit = {},
    onMigrateLegacyPrimaryLogs: () -> Unit = {},
    onLockApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateEntry: () -> Unit,
    onOpenEntry: (DiaryLog) -> Unit,
    onTogglePin: (DiaryLog) -> Unit,
    onShareLikkho: (DiaryLog) -> Unit,
    onExportEntry: (DiaryLog, NativeExportFormat) -> Unit,
    onDeleteEntry: (DiaryLog) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val isBusy = !processingBanner.isNullOrBlank()

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var openMenuLogId by remember { mutableStateOf<String?>(null) }

    var pendingUnlockRequest by remember { mutableStateOf<SecretEntryUnlockRequest?>(null) }
    var pendingDeleteLog by remember { mutableStateOf<DiaryLog?>(null) }
    var exportingLog by remember { mutableStateOf<DiaryLog?>(null) }
    var showMigrateLegacyDialog by remember { mutableStateOf(false) }

    BackHandler(
        enabled = openMenuLogId != null ||
            pendingUnlockRequest != null ||
            pendingDeleteLog != null ||
            exportingLog != null ||
            showMigrateLegacyDialog ||
            isSearchOpen
    ) {
        when {
            openMenuLogId != null -> openMenuLogId = null
            pendingUnlockRequest != null -> pendingUnlockRequest = null
            pendingDeleteLog != null -> pendingDeleteLog = null
            exportingLog != null -> exportingLog = null
            showMigrateLegacyDialog -> showMigrateLegacyDialog = false
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
                    extractReadablePreviewText(log).lowercase(Locale.getDefault()).contains(q)
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
            SecretWritingHeaderBar(
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

            // Processing / Status Banners
            if (!processingBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
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
                        .border(LikkhoHairlineBorder, colors.emerald, RectangleShape)
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

            if (sortedAndFilteredLogs.isEmpty()) {
                if (isLoadingLogs) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (legacyPrimaryLogsCount > 0 && searchQuery.isBlank()) {
                            LegacyDiariesMoveBanner(
                                count = legacyPrimaryLogsCount,
                                onMoveClick = { showMigrateLegacyDialog = true }
                            )
                        }
                        SecretWritingEmptyState(
                            hasSearchQuery = searchQuery.trim().isNotEmpty(),
                            searchQuery = searchQuery
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (legacyPrimaryLogsCount > 0 && searchQuery.isBlank()) {
                        item(key = "legacy_migration_banner") {
                            LegacyDiariesMoveBanner(
                                count = legacyPrimaryLogsCount,
                                onMoveClick = { showMigrateLegacyDialog = true }
                            )
                        }
                    }

                    items(
                        items = sortedAndFilteredLogs,
                        key = { it.id }
                    ) { log ->
                        SecretEntryRow(
                            log = log,
                            mediaEngine = mediaEngine,
                            isMenuOpen = openMenuLogId == log.id,
                            isBusy = isBusy,
                            onRowClick = {
                                if (!log.diaryLockPin.isNullOrEmpty()) {
                                    pendingUnlockRequest = SecretEntryUnlockRequest(
                                        log = log,
                                        action = SecretEntryUnlockAction.OPEN
                                    )
                                } else {
                                    onOpenEntry(log)
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
                                    pendingUnlockRequest = SecretEntryUnlockRequest(
                                        log = log,
                                        action = SecretEntryUnlockAction.EXPORT
                                    )
                                } else {
                                    exportingLog = log
                                }
                            },
                            onDelete = {
                                openMenuLogId = null
                                if (!log.diaryLockPin.isNullOrEmpty()) {
                                    pendingUnlockRequest = SecretEntryUnlockRequest(
                                        log = log,
                                        action = SecretEntryUnlockAction.DELETE
                                    )
                                } else {
                                    pendingDeleteLog = log
                                }
                            }
                        )
                        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                    }
                }
            }
        }

        // Floating Rectangular "+ Write" Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .height(50.dp)
                .shadow(6.dp, RectangleShape)
                .background(colors.accent)
                .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                .clickable { onCreateEntry() }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = "Write new thought",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Write",
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }

    pendingUnlockRequest?.let { req ->
        SecretEntryPinUnlockDialog(
            log = req.log,
            repository = repository,
            onDismiss = { pendingUnlockRequest = null },
            onUnlocked = {
                pendingUnlockRequest = null
                when (req.action) {
                    SecretEntryUnlockAction.OPEN -> onOpenEntry(req.log)
                    SecretEntryUnlockAction.EXPORT -> exportingLog = req.log
                    SecretEntryUnlockAction.DELETE -> pendingDeleteLog = req.log
                }
            }
        )
    }

    pendingDeleteLog?.let { targetLog ->
        SecretDeleteConfirmDialog(
            log = targetLog,
            onDismiss = { pendingDeleteLog = null },
            onConfirmDelete = {
                pendingDeleteLog = null
                onDeleteEntry(targetLog)
            }
        )
    }

    exportingLog?.let { targetLog ->
        SecretExportFormatDialog(
            log = targetLog,
            onDismiss = { exportingLog = null },
            onSelectFormat = { fmt ->
                exportingLog = null
                onExportEntry(targetLog, fmt)
            }
        )
    }

    if (showMigrateLegacyDialog) {
        LikkhoHairlineDialog(
            onDismissRequest = { showMigrateLegacyDialog = false },
            title = "Move Legacy Diaries?",
            subtitle = "ONE-TIME SECURE MIGRATION",
            leadingIcon = Icons.Outlined.DriveFileMove,
            maxWidth = 410.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Later",
                    onClick = { showMigrateLegacyDialog = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Move $legacyPrimaryLogsCount Entries",
                    onClick = {
                        showMigrateLegacyDialog = false
                        onMigrateLegacyPrimaryLogs()
                    }
                )
            }
        ) {
            Text(
                text = "$legacyPrimaryLogsCount older diary ${if (legacyPrimaryLogsCount == 1) "entry was" else "entries were"} found in standard storage from an earlier version.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
            Text(
                text = "Moving them here places all your private diaries strictly inside Secret Writing with their original formatting, photos, audio recordings, and locks intact, while removing them from Everyday storage.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun LegacyDiariesMoveBanner(
    count: Int,
    onMoveClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(colors.surface)
            .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.65f), RectangleShape)
            .clickable { onMoveClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.accent.copy(alpha = 0.1f))
                .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.4f), RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DriveFileMove,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Found $count Legacy ${if (count == 1) "Diary" else "Diaries"}",
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.text
            )
            Text(
                text = "Tap to safely import them into Secret Writing",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 11.sp,
                color = colors.muted
            )
        }
        Box(
            modifier = Modifier
                .background(colors.accent)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Move",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SecretWritingHeaderBar(
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
                            .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search reflections...",
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
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Secret Writing",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isSearchOpen) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { onOpenSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search entries",
                            tint = colors.text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onLockApp() }
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock Vault",
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
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
                        .size(38.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Settings",
                        tint = colors.text,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.border)
    }
}

@Composable
private fun SecretWritingEmptyState(
    hasSearchQuery: Boolean,
    searchQuery: String
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(colors.surface)
                .border(LikkhoHairlineBorder, colors.border, RectangleShape),
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
            text = if (hasSearchQuery) "No Matching Entries" else "Quiet Pages",
            fontFamily = LikkhoFontFamilies.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasSearchQuery) {
                "No entry contains \"$searchQuery\"."
            } else {
                "Tap Write below for distraction-free private reflections."
            },
            fontFamily = LikkhoFontFamilies.Sans,
            fontSize = 12.sp,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SecretEntryRow(
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
    val isLockedEntry = !log.diaryLockPin.isNullOrEmpty()
    val thumbRef = remember(log.pfpDataUrl, log.canvasImages) {
        log.pfpDataUrl?.takeIf { it.isNotBlank() }
            ?: log.canvasImages.firstOrNull { it.dataUrl.isNotBlank() }?.dataUrl
    }

    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = thumbRef) {
        value = withContext(Dispatchers.IO) {
            decodeSecretThumbnailBitmap(mediaEngine, thumbRef)
        }
    }

    val previewText = remember(log.id, isLockedEntry, log.plainPreview, log.contentHtml) {
        if (isLockedEntry) "" else extractReadablePreviewText(log)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1:1 Editorial Thumbnail / Monograph Fallback
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(if (thumbBitmap != null) colors.surface else colors.bg)
                .border(LikkhoHairlineBorder, colors.border, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!,
                    contentDescription = log.heading,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = log.heading.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "L",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center
                )
            }
        }

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
                if (isLockedEntry) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked",
                        tint = colors.crimson,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = log.heading.ifBlank { "Untitled Reflection" },
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLockedEntry) {
                Text(
                    text = "Locked with entry PIN",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 11.sp,
                    color = colors.crimson.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else if (previewText.isNotEmpty()) {
                Text(
                    text = previewText,
                    fontFamily = LikkhoFontFamilies.Prose,
                    fontSize = 13.sp,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val createdDate = log.dateStamp.ifBlank { formatSecretShortDate(log.createdAt) }
            val createdTime = log.timeStamp.ifBlank { formatSecretShortTime(log.createdAt) }
            val modifiedEpoch = if (log.updatedAt > 0L) log.updatedAt else log.createdAt
            val modifiedDate = log.updatedDateStamp?.takeIf { it.isNotBlank() } ?: formatSecretShortDate(modifiedEpoch)
            val modifiedTime = log.updatedTimeStamp?.takeIf { it.isNotBlank() } ?: formatSecretShortTime(modifiedEpoch)
            val showModified = log.updatedAt > log.createdAt + 60_000L

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (showModified) "$modifiedDate · $modifiedTime" else "$createdDate · $createdTime",
                    style = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isLockedEntry && log.canvasImages.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${log.canvasImages.size}",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.accent
                        )
                    }
                }

                if (!isLockedEntry && log.audioAttachments.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${log.audioAttachments.size}",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.accent
                        )
                    }
                }
            }
        }

        // 3-Dots Dropdown Menu
        Box {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clickable { onToggleMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Entry options",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = onDismissMenu,
                shape = RectangleShape,
                containerColor = colors.bg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(LikkhoHairlineBorder, colors.border),
                modifier = Modifier.widthIn(min = 176.dp)
            ) {
                DropdownMenuItem(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(40.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = onTogglePin
                )
                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                DropdownMenuItem(
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(40.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = onShare
                )
                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                DropdownMenuItem(
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(40.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = onExport
                )
                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                DropdownMenuItem(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(40.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun SecretEntryPinUnlockDialog(
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
                    errorMsg = "Incorrect entry passcode."
                }
            }
        }
    }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = "Unlock \"${log.heading.ifBlank { "Entry" }}\"",
        leadingIcon = Icons.Outlined.Lock,
        leadingIconTint = colors.crimson,
        maxWidth = 360.dp,
        footer = {
            LikkhoDialogOutlineButton(
                text = "Cancel",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = if (cooldownSec > 0) "Wait ${cooldownSec}s" else "Unlock",
                onClick = { submitUnlock() },
                enabled = cooldownSec == 0
            )
        }
    ) {
        if (cooldownSec > 0) {
            Text(
                text = "Locked for ${cooldownSec}s due to continuous wrong attempts.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.crimson,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.crimson.copy(alpha = 0.08f))
                    .border(LikkhoHairlineBorder, colors.crimson, RectangleShape)
                    .padding(10.dp)
            )
        } else if (!errorMsg.isNullOrBlank()) {
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Enter Entry PIN",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.text
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
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
                        fontSize = 13.sp,
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
    }
}

@Composable
private fun SecretDeleteConfirmDialog(
    log: DiaryLog,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors
    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = "Delete Entry?",
        leadingIcon = Icons.Outlined.WarningAmber,
        leadingIconTint = colors.crimson,
        borderColor = colors.crimson,
        titleColor = colors.crimson,
        maxWidth = 380.dp,
        footer = {
            LikkhoDialogOutlineButton(
                text = "Cancel",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = "Delete",
                onClick = onConfirmDelete,
                bgColor = colors.crimson,
                leadingIcon = Icons.Outlined.DeleteOutline
            )
        }
    ) {
        Text(
            text = "Are you sure you want to permanently delete \"${log.heading.ifBlank { "Untitled" }}\"? This action cannot be undone.",
            fontFamily = LikkhoFontFamilies.Sans,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.text
        )
    }
}

@Composable
internal fun SecretExportFormatDialog(
    log: DiaryLog,
    onDismiss: () -> Unit,
    onSelectFormat: (NativeExportFormat) -> Unit
) {
    val colors = LikkhoTheme.colors
    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = "Export \"${log.heading.ifBlank { "Entry" }}\"",
        leadingIcon = Icons.Outlined.FileDownload,
        leadingIconTint = colors.accent,
        maxWidth = 420.dp,
        footer = {
            LikkhoDialogOutlineButton(
                text = "Close",
                onClick = onDismiss
            )
        }
    ) {
        Text(
            text = "Choose a file format to save to Downloads/Likkho:",
            fontFamily = LikkhoFontFamilies.Sans,
            fontSize = 12.sp,
            color = colors.muted
        )

        SECRET_EXPORT_FORMATS.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
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

private fun decodeSecretThumbnailBitmap(
    mediaEngine: NativeMediaEngine,
    ref: String?
): ImageBitmap? {
    if (ref.isNullOrBlank()) return null
    return try {
        if (ref.startsWith("data:image/")) {
            val comma = ref.indexOf(',')
            if (comma < 0) return null
            val bytes = Base64.decode(ref.substring(comma + 1), Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = calculateSecretSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                NativeMediaEngine.DEFAULT_PFP_PREVIEW_PX
            )
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )?.asImageBitmap()
        } else {
            val previewFile = mediaEngine.getOrGeneratePreviewFile(
                mediaIdOrRef = ref,
                maxPreviewPx = NativeMediaEngine.DEFAULT_PFP_PREVIEW_PX,
                isPfp = true
            ) ?: return null
            BitmapFactory.decodeFile(previewFile.absolutePath)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

private fun calculateSecretSampleSize(width: Int, height: Int, maxDim: Int): Int {
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

private fun extractReadablePreviewText(log: DiaryLog): String {
    val cleanPlain = log.plainPreview.trim()
    if (cleanPlain.isNotEmpty() && cleanPlain != "Reminder scheduled.") {
        return cleanPlain
    }
    if (log.contentHtml.isBlank()) return ""
    return HtmlCompat.fromHtml(log.contentHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(180)
}

private fun formatSecretShortDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(epochMs))
}

private fun formatSecretShortTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(epochMs))
}

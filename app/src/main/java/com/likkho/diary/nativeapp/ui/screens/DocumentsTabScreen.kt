package com.likkho.diary.nativeapp.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.DocumentCategory
import com.likkho.diary.nativeapp.model.PersonalDocument
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogOutlineButton
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogPrimaryButton
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineBorder
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineDialog
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private sealed class DocumentFilter {
    data object All : DocumentFilter()
    data object Pinned : DocumentFilter()
    data class Category(val value: DocumentCategory) : DocumentFilter()
}

private enum class DocFileKind {
    PDF,
    IMAGE,
    OTHER,
    NONE
}

/**
 * Everyday Tab 4: Encrypted Personal Documents Vault (`likkho_docs/`).
 *
 * Features:
 * - Category & Pinned filters + live Search across title, ID/number, filename, dates & notes
 * - Import PDF or Image via system document picker (`ActivityResultContracts.OpenDocument`) into `.nomedia` `likkho_docs/`
 * - 1-tap Copy ID/Reference Number & Pin/Unpin
 * - Built-in Ready-to-Use Viewer: full-screen pinch-to-zoom Image Viewer + native `PdfRenderer` multi-page PDF reader
 * - FileProvider Share intent with automatic temporary cache file cleanup
 */
@Composable
fun DocumentsTabScreen(
    documents: List<PersonalDocument>,
    searchQuery: String,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onDocumentsUpdated: (List<PersonalDocument>) -> Unit,
    onSystemPickerStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var activeFilter by remember { mutableStateOf<DocumentFilter>(DocumentFilter.All) }
    var isCreatingDoc by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<PersonalDocument?>(null) }
    var viewingDoc by remember { mutableStateOf<PersonalDocument?>(null) }
    var pendingDeleteDoc by remember { mutableStateOf<PersonalDocument?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cleanupSharedDocsCache(context)
        }
    }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2800L)
            feedbackBanner = null
        }
    }

    BackHandler(
        enabled = viewingDoc != null ||
            isCreatingDoc ||
            editingDoc != null ||
            pendingDeleteDoc != null
    ) {
        when {
            pendingDeleteDoc != null -> pendingDeleteDoc = null
            viewingDoc != null -> viewingDoc = null
            isCreatingDoc || editingDoc != null -> {
                isCreatingDoc = false
                editingDoc = null
            }
        }
    }

    fun copyIdNumber(doc: PersonalDocument) {
        if (doc.idNumber.isBlank()) return
        clipboard.setText(AnnotatedString(doc.idNumber.trim()))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        feedbackBanner = "Copied ID number for \"${doc.title.ifBlank { "Document" }}\"."
    }

    fun togglePin(doc: PersonalDocument) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val updated = doc.copy(pinned = !doc.pinned, updatedAt = System.currentTimeMillis())
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                repository.upsertDocument(vaultMode, updated)
            }
            onDocumentsUpdated(next)
            feedbackBanner = if (updated.pinned) {
                "Pinned \"${updated.title}\" to top."
            } else {
                "Unpinned \"${updated.title}\"."
            }
        }
    }

    fun shareDocument(doc: PersonalDocument) {
        val fId = doc.fileId
        if (fId.isNullOrBlank()) {
            val summary = buildString {
                append(doc.title.ifBlank { "Personal Document" })
                if (doc.idNumber.isNotBlank()) append("\nID: ${doc.idNumber}")
                if (doc.issuedDate.isNotBlank()) append("\nIssued: ${doc.issuedDate}")
                if (doc.expiryDate.isNotBlank()) append("\nExpiry: ${doc.expiryDate}")
                if (doc.notes.isNotBlank()) append("\n\n${doc.notes}")
            }
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, doc.title.ifBlank { "Document Details" })
                putExtra(Intent.EXTRA_TEXT, summary)
            }
            onSystemPickerStateChanged(true)
            runCatching {
                context.startActivity(Intent.createChooser(textIntent, "Share Document Details"))
            }.onFailure {
                onSystemPickerStateChanged(false)
                feedbackBanner = "Unable to open share sheet."
            }
            return
        }

        scope.launch {
            val shareIntentRes = withContext(Dispatchers.IO) {
                runCatching {
                    cleanupSharedDocsCache(context)
                    val source = repository.resolveExistingDocumentFile(fId)
                        ?: error("Document file not found in vault.")
                    val safeShareName = sanitizeShareFileName(
                        doc.fileName.ifBlank { source.name },
                        source.extension.ifBlank { "bin" }
                    )
                    val cacheDir = File(context.cacheDir, SHARED_DOCS_CACHE_DIR).apply {
                        if (!exists()) mkdirs()
                    }
                    val tempFile = File(cacheDir, safeShareName)
                    source.inputStream().use { inp ->
                        tempFile.outputStream().use { out ->
                            inp.copyTo(out, 32_768)
                        }
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile,
                        safeShareName
                    )
                    val mime = doc.mimeType.ifBlank { inferMimeType(safeShareName) }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TITLE, doc.title.ifBlank { safeShareName })
                        putExtra(Intent.EXTRA_SUBJECT, doc.title.ifBlank { safeShareName })
                        clipData = ClipData.newUri(context.contentResolver, safeShareName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    Intent.createChooser(sendIntent, "Share ${doc.title.ifBlank { "Document" }}").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            shareIntentRes.onSuccess { chooser ->
                onSystemPickerStateChanged(true)
                runCatching { context.startActivity(chooser) }
                    .onFailure { err ->
                        onSystemPickerStateChanged(false)
                        feedbackBanner = "Share failed: ${err.message}"
                    }
            }.onFailure { err ->
                feedbackBanner = "Share error: ${err.message}"
            }
        }
    }

    fun openWithExternalApp(doc: PersonalDocument) {
        val fId = doc.fileId ?: return
        scope.launch {
            val viewIntentRes = withContext(Dispatchers.IO) {
                runCatching {
                    cleanupSharedDocsCache(context)
                    val source = repository.resolveExistingDocumentFile(fId)
                        ?: error("Document file not found.")
                    val safeName = sanitizeShareFileName(
                        doc.fileName.ifBlank { source.name },
                        source.extension.ifBlank { "bin" }
                    )
                    val cacheDir = File(context.cacheDir, SHARED_DOCS_CACHE_DIR).apply {
                        if (!exists()) mkdirs()
                    }
                    val tempFile = File(cacheDir, safeName)
                    source.inputStream().use { inp ->
                        tempFile.outputStream().use { out -> inp.copyTo(out, 32_768) }
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile,
                        safeName
                    )
                    val mime = doc.mimeType.ifBlank { inferMimeType(safeName) }
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            viewIntentRes.onSuccess { intent ->
                onSystemPickerStateChanged(true)
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        onSystemPickerStateChanged(false)
                        feedbackBanner = "No external app found to open this file type."
                    }
            }.onFailure { err ->
                feedbackBanner = "Unable to open file: ${err.message}"
            }
        }
    }

    val pinnedCount = remember(documents) { documents.count { it.pinned } }
    val categoryCounts = remember(documents) {
        DocumentCategory.entries.associateWith { cat ->
            documents.count { it.category == cat }
        }
    }

    val filteredDocs = remember(documents, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        documents
            .filter { doc ->
                val matchesCat = when (activeFilter) {
                    DocumentFilter.All -> true
                    DocumentFilter.Pinned -> doc.pinned
                    is DocumentFilter.Category -> doc.category == (activeFilter as DocumentFilter.Category).value
                }
                if (!matchesCat) return@filter false
                if (q.isEmpty()) return@filter true

                doc.title.lowercase(Locale.getDefault()).contains(q) ||
                    doc.category.displayLabel.lowercase(Locale.getDefault()).contains(q) ||
                    doc.idNumber.lowercase(Locale.getDefault()).contains(q) ||
                    doc.fileName.lowercase(Locale.getDefault()).contains(q) ||
                    doc.issuedDate.lowercase(Locale.getDefault()).contains(q) ||
                    doc.expiryDate.lowercase(Locale.getDefault()).contains(q) ||
                    doc.notes.lowercase(Locale.getDefault()).contains(q)
            }
            .sortedWith(
                compareByDescending<PersonalDocument> { it.pinned }
                    .thenByDescending { maxOf(it.updatedAt, it.createdAt) }
            )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Filter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocumentFilterChip(
                    label = "All",
                    count = documents.size,
                    selected = activeFilter == DocumentFilter.All,
                    onClick = { activeFilter = DocumentFilter.All }
                )
                if (pinnedCount > 0 || activeFilter == DocumentFilter.Pinned) {
                    DocumentFilterChip(
                        label = "Pinned",
                        count = pinnedCount,
                        selected = activeFilter == DocumentFilter.Pinned,
                        onClick = { activeFilter = DocumentFilter.Pinned }
                    )
                }
                DocumentCategory.entries.forEach { cat ->
                    val count = categoryCounts[cat] ?: 0
                    val isSelected = activeFilter == DocumentFilter.Category(cat)
                    if (count > 0 || isSelected) {
                        DocumentFilterChip(
                            label = cat.displayLabel,
                            count = count,
                            selected = isSelected,
                            onClick = { activeFilter = DocumentFilter.Category(cat) }
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
                        text = feedbackBanner.orEmpty(),
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filteredDocs.isEmpty()) {
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
                                    Icons.Outlined.Description
                                },
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching documents" else "No Documents Stored",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No documents matched \"$searchQuery\"."
                            } else {
                                "Store Aadhaar, PAN, Passport, Insurance, Travel, or Medical PDFs & photos privately offline with quick ID copy and built-in viewer."
                            },
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LikkhoDialogPrimaryButton(
                                text = "Add Document",
                                leadingIcon = Icons.Outlined.Add,
                                onClick = { isCreatingDoc = true }
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
                    items(filteredDocs, key = { it.id }) { doc ->
                        PersonalDocumentCard(
                            doc = doc,
                            onOpenViewer = {
                                if (!doc.fileId.isNullOrBlank()) {
                                    viewingDoc = doc
                                } else {
                                    editingDoc = doc
                                }
                            },
                            onCopyId = { copyIdNumber(doc) },
                            onShare = { shareDocument(doc) },
                            onTogglePin = { togglePin(doc) },
                            onEdit = { editingDoc = doc },
                            onDelete = { pendingDeleteDoc = doc }
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
                .clickable { isCreatingDoc = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add Document",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Document",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        }

        if (isCreatingDoc || editingDoc != null) {
            DocumentEditorDialog(
                initialDoc = editingDoc,
                vaultMode = vaultMode,
                repository = repository,
                onSystemPickerStateChanged = onSystemPickerStateChanged,
                onDismiss = { replacedOrNewTempFileId ->
                    if (!replacedOrNewTempFileId.isNullOrBlank()) {
                        scope.launch(Dispatchers.IO) {
                            repository.resolveExistingDocumentFile(replacedOrNewTempFileId)?.delete()
                        }
                    }
                    isCreatingDoc = false
                    editingDoc = null
                },
                onSave = { savedDoc, obsoleteOldFileId ->
                    val wasEditing = editingDoc != null
                    isCreatingDoc = false
                    editingDoc = null
                    scope.launch {
                        val next = withContext(Dispatchers.IO) {
                            if (!obsoleteOldFileId.isNullOrBlank() && obsoleteOldFileId != savedDoc.fileId) {
                                val stillReferenced = documents.any { it.id != savedDoc.id && it.fileId == obsoleteOldFileId }
                                if (!stillReferenced) {
                                    repository.resolveExistingDocumentFile(obsoleteOldFileId)?.delete()
                                }
                            }
                            repository.upsertDocument(vaultMode, savedDoc)
                        }
                        onDocumentsUpdated(next)
                        feedbackBanner = if (wasEditing) {
                            "Updated \"${savedDoc.title}\"."
                        } else {
                            "Saved \"${savedDoc.title}\" to Documents vault."
                        }
                    }
                }
            )
        }

        pendingDeleteDoc?.let { target ->
            LikkhoHairlineDialog(
                onDismissRequest = { pendingDeleteDoc = null },
                title = "Delete Document",
                subtitle = "REMOVE FROM ENCRYPTED VAULT",
                leadingIcon = Icons.Outlined.WarningAmber,
                leadingIconTint = colors.crimson,
                borderColor = colors.crimson,
                footer = {
                    LikkhoDialogOutlineButton(
                        text = "Cancel",
                        onClick = { pendingDeleteDoc = null }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LikkhoDialogPrimaryButton(
                        text = "Delete",
                        bgColor = colors.crimson,
                        onClick = {
                            val toDelete = target
                            pendingDeleteDoc = null
                            scope.launch {
                                val remaining = withContext(Dispatchers.IO) {
                                    repository.deleteDocument(vaultMode, toDelete.id, pruneFile = true)
                                }
                                onDocumentsUpdated(remaining)
                                feedbackBanner = "Deleted \"${toDelete.title.ifBlank { "Document" }}\"."
                            }
                        }
                    )
                }
            ) {
                Text(
                    text = "Permanently remove \"${target.title.ifBlank { "Untitled Document" }}\" and its stored file from your vault?",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.text
                )
            }
        }

        viewingDoc?.let { activeDoc ->
            FullScreenDocumentViewer(
                doc = activeDoc,
                repository = repository,
                onCopyId = { copyIdNumber(activeDoc) },
                onShare = { shareDocument(activeDoc) },
                onOpenExternal = { openWithExternalApp(activeDoc) },
                onDismiss = { viewingDoc = null }
            )
        }
    }
}

@Composable
private fun DocumentFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Row(
        modifier = Modifier
            .height(30.dp)
            .background(if (selected) colors.accent else colors.bg)
            .border(
                LikkhoHairlineBorder,
                if (selected) colors.accentHover else colors.border,
                RectangleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
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

@Composable
private fun PersonalDocumentCard(
    doc: PersonalDocument,
    onOpenViewer: () -> Unit,
    onCopyId: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val kind = remember(doc.mimeType, doc.fileName, doc.fileId) {
        resolveDocFileKind(doc)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.surface)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
            .clickable { onOpenViewer() }
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (doc.pinned) colors.accent else colors.border)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Meta Badges + Pin/Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.08f))
                            .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.4f), RectangleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = doc.category.displayLabel.uppercase(Locale.US),
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = colors.accent
                        )
                    }

                    if (kind != DocFileKind.NONE) {
                        Row(
                            modifier = Modifier
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = when (kind) {
                                    DocFileKind.PDF -> Icons.Outlined.PictureAsPdf
                                    DocFileKind.IMAGE -> Icons.Outlined.Image
                                    else -> Icons.Outlined.AttachFile
                                },
                                contentDescription = null,
                                tint = if (kind == DocFileKind.PDF) colors.crimson else colors.emerald,
                                modifier = Modifier.size(11.dp)
                            )
                            val sizePart = if (doc.fileSizeBytes > 0L) " · ${formatFileSize(doc.fileSizeBytes)}" else ""
                            Text(
                                text = "${kind.name}$sizePart",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 9.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (doc.pinned) colors.accent.copy(alpha = 0.1f) else colors.bg)
                            .border(
                                LikkhoHairlineBorder,
                                if (doc.pinned) colors.accent.copy(alpha = 0.5f) else colors.hairline,
                                RectangleShape
                            )
                            .clickable { onTogglePin() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = if (doc.pinned) "Unpin" else "Pin",
                            tint = if (doc.pinned) colors.accent else colors.muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onEdit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Document",
                            tint = colors.text,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Document",
                            tint = colors.crimson,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Title
            Text(
                text = doc.title.ifBlank { doc.fileName.ifBlank { "Untitled Document" } },
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Quick-copy ID Number Box
            if (doc.idNumber.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                        .clickable { onCopyId() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Badge,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = doc.idNumber,
                            style = TextStyle(
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.text,
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy ID",
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Copy ID",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = colors.accent
                        )
                    }
                }
            }

            // Dates / Notes
            if (doc.issuedDate.isNotBlank() || doc.expiryDate.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (doc.issuedDate.isNotBlank()) {
                        Text(
                            text = "Issued: ${doc.issuedDate}",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.muted
                        )
                    }
                    if (doc.expiryDate.isNotBlank()) {
                        Text(
                            text = "Expiry: ${doc.expiryDate}",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.muted
                        )
                    }
                }
            }

            if (doc.notes.isNotBlank()) {
                Text(
                    text = doc.notes,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

            // Bottom Actions: View + Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!doc.fileId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .background(colors.accent)
                                .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                                .clickable { onOpenViewer() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when (kind) {
                                    DocFileKind.PDF -> "Open PDF"
                                    DocFileKind.IMAGE -> "View Image"
                                    else -> "Open File"
                                },
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { onShare() }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (doc.fileId.isNullOrBlank()) "Share Details" else "Share",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = colors.text
                        )
                    }
                }

                if (doc.fileName.isNotBlank()) {
                    Text(
                        text = doc.fileName,
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 9.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentEditorDialog(
    initialDoc: PersonalDocument?,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onSystemPickerStateChanged: (Boolean) -> Unit,
    onDismiss: (uncommittedTempFileId: String?) -> Unit,
    onSave: (PersonalDocument, obsoleteOldFileId: String?) -> Unit
) {
    val colors = LikkhoTheme.colors
    val scope = rememberCoroutineScope()
    val titleFocus = remember { FocusRequester() }

    var title by remember(initialDoc) { mutableStateOf(initialDoc?.title.orEmpty()) }
    var category by remember(initialDoc) {
        mutableStateOf(initialDoc?.category ?: DocumentCategory.ID_CARD)
    }
    var idNumber by remember(initialDoc) { mutableStateOf(initialDoc?.idNumber.orEmpty()) }
    var issuedDate by remember(initialDoc) { mutableStateOf(initialDoc?.issuedDate.orEmpty()) }
    var expiryDate by remember(initialDoc) { mutableStateOf(initialDoc?.expiryDate.orEmpty()) }
    var notes by remember(initialDoc) { mutableStateOf(initialDoc?.notes.orEmpty()) }
    var pinned by remember(initialDoc) { mutableStateOf(initialDoc?.pinned ?: false) }

    var attachedFileId by remember(initialDoc) { mutableStateOf(initialDoc?.fileId) }
    var attachedFileName by remember(initialDoc) { mutableStateOf(initialDoc?.fileName.orEmpty()) }
    var attachedMimeType by remember(initialDoc) { mutableStateOf(initialDoc?.mimeType.orEmpty()) }
    var attachedSizeBytes by remember(initialDoc) { mutableStateOf(initialDoc?.fileSizeBytes ?: 0L) }
    var newlyImportedFileId by remember(initialDoc) { mutableStateOf<String?>(null) }

    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onSystemPickerStateChanged(false)
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        importError = null
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                repository.importDocumentFromUri(uri)
            }
            isImporting = false
            if (imported == null) {
                importError = "Could not import selected file. Choose a valid PDF or image."
            } else {
                // Clean up any previously picked uncommitted file inside this dialog session
                newlyImportedFileId?.let { prevTmp ->
                    if (prevTmp != initialDoc?.fileId) {
                        scope.launch(Dispatchers.IO) {
                            repository.resolveExistingDocumentFile(prevTmp)?.delete()
                        }
                    }
                }
                newlyImportedFileId = imported.fileId
                attachedFileId = imported.fileId
                attachedFileName = imported.fileName
                attachedMimeType = imported.mimeType
                attachedSizeBytes = imported.sizeBytes
                if (title.isBlank()) {
                    title = imported.fileName.substringBeforeLast('.').replace('_', ' ').trim()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialDoc == null) {
            titleFocus.requestFocus()
        }
    }

    LikkhoHairlineDialog(
        onDismissRequest = { onDismiss(newlyImportedFileId) },
        title = if (initialDoc == null) "Add Document" else "Edit Document",
        subtitle = "OFFLINE ENCRYPTED DOCUMENT VAULT",
        leadingIcon = Icons.Outlined.Description,
        maxWidth = 460.dp,
        maxHeight = 680.dp,
        footer = {
            LikkhoDialogOutlineButton(
                text = "Cancel",
                onClick = { onDismiss(newlyImportedFileId) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = if (initialDoc == null) "Save Document" else "Update",
                enabled = !isImporting && (title.trim().isNotEmpty() || !attachedFileId.isNullOrBlank()),
                onClick = {
                    val now = System.currentTimeMillis()
                    val resolvedTitle = title.trim().ifBlank {
                        attachedFileName.substringBeforeLast('.').ifBlank { "Untitled Document" }
                    }
                    val doc = PersonalDocument(
                        id = initialDoc?.id ?: "doc_${now}_${UUID.randomUUID().toString().take(4)}",
                        title = resolvedTitle,
                        category = category,
                        idNumber = idNumber.trim(),
                        fileId = attachedFileId,
                        fileName = attachedFileName,
                        mimeType = attachedMimeType,
                        fileSizeBytes = attachedSizeBytes,
                        notes = notes.trim(),
                        issuedDate = issuedDate.trim(),
                        expiryDate = expiryDate.trim(),
                        pinned = pinned,
                        createdAt = initialDoc?.createdAt ?: now,
                        updatedAt = now
                    )
                    val obsoleteOld = if (initialDoc?.fileId != null && initialDoc.fileId != attachedFileId) {
                        initialDoc.fileId
                    } else {
                        null
                    }
                    onSave(doc, obsoleteOld)
                }
            )
        }
    ) {
        // File Import Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ATTACHED PDF OR IMAGE",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .clickable(enabled = !isImporting) {
                        onSystemPickerStateChanged(true)
                        openDocLauncher.launch(arrayOf("application/pdf", "image/*"))
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Importing securely into vault...",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            color = colors.accent
                        )
                    } else if (!attachedFileId.isNullOrBlank()) {
                        Icon(
                            imageVector = if (attachedMimeType.contains("pdf")) {
                                Icons.Outlined.PictureAsPdf
                            } else {
                                Icons.Outlined.Image
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachedFileName.ifBlank { "Attached file" },
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatFileSize(attachedSizeBytes)} · Tap to replace",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Choose PDF or Image",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.accent
                            )
                            Text(
                                text = "Stored privately in encrypted vault folder",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                if (!attachedFileId.isNullOrBlank() && !isImporting) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                if (newlyImportedFileId != null && newlyImportedFileId != initialDoc?.fileId) {
                                    val tmp = newlyImportedFileId!!
                                    scope.launch(Dispatchers.IO) {
                                        repository.resolveExistingDocumentFile(tmp)?.delete()
                                    }
                                    newlyImportedFileId = null
                                }
                                attachedFileId = null
                                attachedFileName = ""
                                attachedMimeType = ""
                                attachedSizeBytes = 0L
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Detach file",
                            tint = colors.crimson,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (!importError.isNullOrBlank()) {
                Text(
                    text = importError.orEmpty(),
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 11.sp,
                    color = colors.crimson
                )
            }
        }

        // Title
        DocTextField(
            label = "DOCUMENT TITLE",
            value = title,
            onValueChange = { title = it },
            placeholder = "e.g., Aadhaar Card, Passport, PAN...",
            focusRequester = titleFocus
        )

        // Category Grid (3x2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "CATEGORY",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )
            DocumentCategory.entries.chunked(3).forEach { rowCats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowCats.forEach { cat ->
                        val active = category == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(if (active) colors.accent else colors.surface)
                                .border(
                                    LikkhoHairlineBorder,
                                    if (active) colors.accentHover else colors.border,
                                    RectangleShape
                                )
                                .clickable { category = cat },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.displayLabel,
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) Color.White else colors.text,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ID / Reference Number
        DocTextField(
            label = "ID / REFERENCE NUMBER (1-TAP COPY)",
            value = idNumber,
            onValueChange = { idNumber = it },
            placeholder = "e.g., ABCDE1234F or Policy No.",
            monospace = true
        )

        // Issued & Expiry Dates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DocTextField(
                    label = "ISSUED DATE",
                    value = issuedDate,
                    onValueChange = { issuedDate = it },
                    placeholder = "e.g., 14 Aug 2021"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DocTextField(
                    label = "EXPIRY DATE",
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    placeholder = "e.g., 13 Aug 2031"
                )
            }
        }

        // Notes
        DocTextField(
            label = "NOTES (OPTIONAL)",
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Issuing authority, nominee, bank branch...",
            singleLine = false
        )

        // Pin Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                .clickable { pinned = !pinned }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(if (pinned) colors.accent else colors.bg)
                    .border(
                        LikkhoHairlineBorder,
                        if (pinned) colors.accent else colors.border,
                        RectangleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (pinned) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(
                text = "Pin document to top of list",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text
            )
        }
    }
}

@Composable
private fun DocTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    monospace: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val colors = LikkhoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
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
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = if (monospace) LikkhoFontFamilies.Mono else LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    color = colors.muted
                )
            }
            val fieldMod = if (focusRequester != null) {
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                maxLines = if (singleLine) 1 else 4,
                textStyle = TextStyle(
                    fontFamily = if (monospace) LikkhoFontFamilies.Mono else LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    color = colors.text
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (monospace) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
                ),
                modifier = fieldMod
            )
        }
    }
}

/**
 * Full-screen Built-in Document Viewer:
 * - Pinch-to-zoom & pan Image Viewer (EXIF orientation aware)
 * - Multi-page native `android.graphics.pdf.PdfRenderer` reader with smooth scroll & zoom
 * - 1-tap Copy ID Number & Share actions
 */
@Composable
private fun FullScreenDocumentViewer(
    doc: PersonalDocument,
    repository: NativeVaultRepository,
    onCopyId: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val kind = remember(doc.id, doc.fileId, doc.mimeType, doc.fileName) {
        resolveDocFileKind(doc)
    }
    var resolvedFile by remember(doc.id, doc.fileId) { mutableStateOf<File?>(null) }
    var fileLookupDone by remember(doc.id, doc.fileId) { mutableStateOf(false) }

    LaunchedEffect(doc.id, doc.fileId) {
        fileLookupDone = false
        resolvedFile = withContext(Dispatchers.IO) {
            doc.fileId?.let { repository.resolveExistingDocumentFile(it) }
        }
        fileLookupDone = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(120f)
            .background(Color(0xFF0B0E11))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Viewer Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(Color(0xFF14181E))
                .padding(horizontal = 8.dp),
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
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title.ifBlank { doc.fileName.ifBlank { "Document" } },
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(doc.category.displayLabel.uppercase(Locale.US))
                            if (doc.fileSizeBytes > 0L) append(" · ${formatFileSize(doc.fileSizeBytes)}")
                        },
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 9.sp,
                        color = Color(0xFF9AA0A6),
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (doc.idNumber.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .background(colors.accent.copy(alpha = 0.22f))
                            .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
                            .clickable { onCopyId() }
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy ID",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Copy ID",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E242C))
                        .border(LikkhoHairlineBorder, Color(0xFF3A4047), RectangleShape)
                        .clickable { onShare() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share Document",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF272D34))

        // Main Viewer Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                !fileLookupDone -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                resolvedFile == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.crimson,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Document file not found in local vault.",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }

                kind == DocFileKind.IMAGE -> {
                    ZoomableImageCanvas(file = resolvedFile!!)
                }

                kind == DocFileKind.PDF -> {
                    NativePdfRendererViewer(
                        file = resolvedFile!!,
                        onOpenExternal = onOpenExternal
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = doc.fileName.ifBlank { "Document File" },
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${doc.mimeType.ifBlank { "File" }} · ${formatFileSize(doc.fileSizeBytes)}",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 11.sp,
                            color = Color(0xFF9AA0A6)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LikkhoDialogPrimaryButton(
                                text = "Open With App",
                                leadingIcon = Icons.Outlined.OpenInNew,
                                onClick = onOpenExternal
                            )
                            LikkhoDialogOutlineButton(
                                text = "Share",
                                leadingIcon = Icons.Outlined.Share,
                                textColor = Color.White,
                                borderColor = Color(0xFF3A4047),
                                bgColor = Color(0xFF1A1F24),
                                onClick = onShare
                            )
                        }
                    }
                }
            }
        }

        // Bottom ID Quick-Bar (if ID Number is set)
        if (doc.idNumber.isNotBlank()) {
            HorizontalDivider(thickness = 1.dp, color = Color(0xFF272D34))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF14181E))
                    .clickable { onCopyId() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "ID",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color(0xFF9AA0A6)
                    )
                    Text(
                        text = doc.idNumber,
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Tap to copy",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }
        }
    }
}

@Composable
private fun ZoomableImageCanvas(file: File) {
    val colors = LikkhoTheme.colors
    var bitmap by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    var loadFailed by remember(file.absolutePath) { mutableStateOf(false) }

    var scale by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var offsetX by remember(file.absolutePath) { mutableFloatStateOf(0f) }
    var offsetY by remember(file.absolutePath) { mutableFloatStateOf(0f) }

    LaunchedEffect(file.absolutePath) {
        loadFailed = false
        val decoded = withContext(Dispatchers.IO) {
            decodeOrientedSampledBitmap(file, maxDimension = 2048)
        }
        if (decoded != null) {
            bitmap = decoded.asImageBitmap()
        } else {
            loadFailed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        when {
            loadFailed -> {
                Text(
                    text = "Unable to decode image file.",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    color = Color(0xFF9AA0A6),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            bitmap == null -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.Center)
                )
            }

            else -> {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Document Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(file.absolutePath) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(file.absolutePath) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = nextScale
                                if (nextScale <= 1.01f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    val maxOffsetX = (size.width * (nextScale - 1f)) * 0.6f
                                    val maxOffsetY = (size.height * (nextScale - 1f)) * 0.6f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )

                // Zoom Badge Bottom-Right
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .background(Color(0xCC14181E))
                        .border(LikkhoHairlineBorder, Color(0xFF3A4047), RectangleShape)
                        .clickable {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx · Reset", scale),
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private data class PdfPageInfo(
    val pageCount: Int,
    val aspectRatios: List<Float>
)

@Composable
private fun NativePdfRendererViewer(
    file: File,
    onOpenExternal: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val pdfMutex = remember(file.absolutePath) { Mutex() }
    val isDisposed = remember(file.absolutePath) { AtomicBoolean(false) }
    var pfd by remember(file.absolutePath) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember(file.absolutePath) { mutableStateOf<PdfRenderer?>(null) }
    var pdfInfo by remember(file.absolutePath) { mutableStateOf<PdfPageInfo?>(null) }
    var errorMsg by remember(file.absolutePath) { mutableStateOf<String?>(null) }

    val pageCache = remember(file.absolutePath) { mutableStateMapOf<Int, ImageBitmap>() }
    val listState = rememberLazyListState()
    var zoomScale by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    val horizontalScrollState = rememberScrollState()

    val currentPageIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(file.absolutePath) {
        errorMsg = null
        val openRes = withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdf = PdfRenderer(fd)
                val count = pdf.pageCount
                val ratios = ArrayList<Float>(count)
                for (i in 0 until count) {
                    pdf.openPage(i).use { p ->
                        val w = p.width.coerceAtLeast(1).toFloat()
                        val h = p.height.coerceAtLeast(1).toFloat()
                        ratios.add((w / h).coerceIn(0.35f, 2.5f))
                    }
                }
                Triple(fd, pdf, PdfPageInfo(count, ratios))
            }
        }
        openRes.onSuccess { (fd, pdf, info) ->
            if (isDisposed.get() || !isActive) {
                CoroutineScope(Dispatchers.IO).launch {
                    pdfMutex.withLock {
                        runCatching { pdf.close() }
                        runCatching { fd.close() }
                    }
                }
            } else {
                pfd = fd
                renderer = pdf
                pdfInfo = info
            }
        }.onFailure { err ->
            errorMsg = err.message ?: "Unable to render PDF."
        }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            isDisposed.set(true)
            val r = renderer
            val fd = pfd
            renderer = null
            pfd = null
            pageCache.clear()
            if (r != null || fd != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    pdfMutex.withLock {
                        runCatching { r?.close() }
                        runCatching { fd?.close() }
                    }
                }
            }
        }
    }

    val activeRenderer = renderer
    val info = pdfInfo

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            errorMsg != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = colors.crimson,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "Unable to preview this PDF inline (${errorMsg.orEmpty()}).",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 13.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    LikkhoDialogPrimaryButton(
                        text = "Open With External PDF App",
                        leadingIcon = Icons.Outlined.OpenInNew,
                        onClick = onOpenExternal
                    )
                }
            }

            activeRenderer == null || info == null -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.Center)
                )
            }

            info.pageCount == 0 -> {
                Text(
                    text = "PDF has 0 pages.",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    color = Color(0xFF9AA0A6),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(file.absolutePath) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        if (zoomChange != 1f) {
                                            zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (pressedCount == 0) {
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    val viewportWidth = maxWidth
                    val zoomedWidth = (viewportWidth * zoomScale).coerceAtLeast(viewportWidth)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState, enabled = zoomScale > 1.02f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(zoomedWidth)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = 72.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(info.pageCount, key = { it }) { pageIdx ->
                                val aspect = info.aspectRatios.getOrElse(pageIdx) { 0.707f }
                                val rendered = pageCache[pageIdx]

                                LaunchedEffect(pageIdx, activeRenderer) {
                                    if (pageCache[pageIdx] == null) {
                                        val bmp = withContext(Dispatchers.IO) {
                                            pdfMutex.withLock {
                                                if (isDisposed.get() || !isActive) return@withLock null
                                                runCatching {
                                                    activeRenderer.openPage(pageIdx).use { page ->
                                                        val targetW = 1440
                                                        val targetH = ((targetW.toFloat() * page.height.toFloat()) /
                                                            page.width.coerceAtLeast(1).toFloat())
                                                            .toInt()
                                                            .coerceIn(200, 2400)
                                                        val out = Bitmap.createBitmap(
                                                            targetW,
                                                            targetH,
                                                            Bitmap.Config.ARGB_8888
                                                        )
                                                        out.eraseColor(AndroidColor.WHITE)
                                                        page.render(
                                                            out,
                                                            null,
                                                            null,
                                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                                        )
                                                        out.asImageBitmap()
                                                    }
                                                }.getOrNull()
                                            }
                                        }
                                        if (bmp != null) {
                                            // Keep cache bounded for very large PDFs
                                            if (pageCache.size > 10) {
                                                val farthest = pageCache.keys.maxByOrNull { kotlin.math.abs(it - pageIdx) }
                                                if (farthest != null && farthest != pageIdx) {
                                                    pageCache.remove(farthest)
                                                }
                                            }
                                            pageCache[pageIdx] = bmp
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspect)
                                        .background(Color.White)
                                        .border(LikkhoHairlineBorder, Color(0xFF3A4047), RectangleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rendered != null) {
                                        Image(
                                            bitmap = rendered,
                                            contentDescription = "PDF Page ${pageIdx + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            color = colors.accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Page Indicator & Zoom Controls Overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .background(Color(0xE614181E))
                        .border(LikkhoHairlineBorder, Color(0xFF3A4047), RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Page ${currentPageIndex + 1} of ${info.pageCount}",
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color(0xFF3A4047))
                    )
                    Icon(
                        imageVector = Icons.Outlined.ZoomOut,
                        contentDescription = "Zoom out",
                        tint = if (zoomScale > 1.05f) Color.White else Color(0xFF666B72),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(enabled = zoomScale > 1.05f) {
                                zoomScale = (zoomScale - 0.5f).coerceAtLeast(1f)
                            }
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx", zoomScale),
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.clickable { zoomScale = 1f }
                    )
                    Icon(
                        imageVector = Icons.Outlined.ZoomIn,
                        contentDescription = "Zoom in",
                        tint = if (zoomScale < 2.95f) Color.White else Color(0xFF666B72),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(enabled = zoomScale < 2.95f) {
                                zoomScale = (zoomScale + 0.5f).coerceAtMost(3f)
                            }
                    )
                }
            }
        }
    }
}

private const val SHARED_DOCS_CACHE_DIR = "shared_docs"
private const val SHARED_DOCS_MAX_AGE_MS = 30L * 60L * 1000L

private fun cleanupSharedDocsCache(context: Context) {
    runCatching {
        val dir = File(context.cacheDir, SHARED_DOCS_CACHE_DIR)
        if (dir.exists() && dir.isDirectory) {
            val cutoff = System.currentTimeMillis() - SHARED_DOCS_MAX_AGE_MS
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }
}

private fun resolveDocFileKind(doc: PersonalDocument): DocFileKind {
    if (doc.fileId.isNullOrBlank()) return DocFileKind.NONE
    val mime = doc.mimeType.lowercase(Locale.US)
    val ext = doc.fileName.substringAfterLast('.', doc.fileId.substringAfterLast('.', "")).lowercase(Locale.US)
    return when {
        mime == "application/pdf" || ext == "pdf" -> DocFileKind.PDF
        mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> DocFileKind.IMAGE
        else -> DocFileKind.OTHER
    }
}

private fun sanitizeShareFileName(rawName: String, fallbackExt: String): String {
    val cleaned = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
    return if (cleaned.isNotBlank()) cleaned else "document_${System.currentTimeMillis()}.$fallbackExt"
}

private fun inferMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun decodeOrientedSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sample = 1
        while (rawW / sample > maxDimension || rawH / sample > maxDimension) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val exifOrientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) {
            decoded
        } else {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        }
    } catch (_: Exception) {
        null
    }
}

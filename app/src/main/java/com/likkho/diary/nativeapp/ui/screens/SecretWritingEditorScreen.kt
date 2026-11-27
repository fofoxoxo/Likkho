package com.likkho.diary.nativeapp.ui.screens

import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.text.HtmlCompat
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.export.NativeExportFormat
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CanvasAudioAttachment
import com.likkho.diary.nativeapp.model.CanvasDraggableImage
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Pure Native Compose Secret Writing Reader & Editor (`VaultMode.DECOY`).
 *
 * Designed as a calm, distraction-free journal workspace:
 * - Clean Editorial Read Mode with rich HTML rendering, photo lightbox, and legacy voice player
 * - Clean Distraction-Free Edit Mode with Title, readable multi-line text, and Photo attachments
 * - Preserves all legacy `DiaryLog` fields (`pfpDataUrl`, `canvasBgDataUrl`, `canvasImages`,
 *   `audioAttachments`, `contentHtml`, `diaryLockPin`, `pinned`) without data loss
 */
@Composable
fun SecretWritingEditorScreen(
    initialLog: DiaryLog?,
    startInEditMode: Boolean,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    onSaveLog: (DiaryLog, Boolean) -> Unit,
    onDeleteLog: (String) -> Unit,
    onShareLikkho: (DiaryLog) -> Unit,
    onExportEntry: (DiaryLog, NativeExportFormat) -> Unit,
    onSystemPickerStateChanged: (Boolean) -> Unit,
    onExitWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sessionKey = remember { initialLog?.id?.takeIf { it.isNotBlank() } ?: "draft_${System.nanoTime()}_${UUID.randomUUID()}" }
    val nowAtOpen = remember(sessionKey) { System.currentTimeMillis() }
    val stableLogId = remember(sessionKey) {
        initialLog?.id?.takeIf { it.isNotBlank() } ?: "log_${nowAtOpen}_${UUID.randomUUID().toString().take(6)}"
    }
    val stableCreatedAt = remember(sessionKey) { initialLog?.createdAt?.takeIf { it > 0L } ?: nowAtOpen }
    val stableDateStamp = remember(sessionKey) {
        initialLog?.dateStamp?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(stableCreatedAt))
    }
    val stableTimeStamp = remember(sessionKey) {
        initialLog?.timeStamp?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(stableCreatedAt))
    }

    val initialEditableBody = remember(sessionKey) {
        if (initialLog == null) "" else htmlToEditableText(initialLog.contentHtml, initialLog.plainPreview)
    }

    var isEditMode by remember(sessionKey) { mutableStateOf(startInEditMode || initialLog == null) }
    var heading by remember(sessionKey) { mutableStateOf(initialLog?.heading.orEmpty()) }
    var bodyText by remember(sessionKey) { mutableStateOf(initialEditableBody) }
    var baselineHeading by remember(sessionKey) { mutableStateOf(initialLog?.heading.orEmpty()) }
    var baselineBodyText by remember(sessionKey) { mutableStateOf(initialEditableBody) }

    var isPinned by remember(sessionKey) { mutableStateOf(initialLog?.pinned ?: false) }
    var entryLockPin by remember(sessionKey) { mutableStateOf(initialLog?.diaryLockPin) }
    var canvasImages by remember(sessionKey) { mutableStateOf(initialLog?.canvasImages ?: emptyList()) }
    var audioAttachments by remember(sessionKey) { mutableStateOf(initialLog?.audioAttachments ?: emptyList()) }
    var currentContentHtml by remember(sessionKey) { mutableStateOf(initialLog?.contentHtml.orEmpty()) }
    var lastHtmlDecodedText by remember(sessionKey) { mutableStateOf(initialEditableBody) }
    var lastUpdatedAt by remember(sessionKey) { mutableLongStateOf(initialLog?.updatedAt ?: nowAtOpen) }
    var baselineImageCount by remember(sessionKey) { mutableIntStateOf(initialLog?.canvasImages?.size ?: 0) }
    var baselineAudioCount by remember(sessionKey) { mutableIntStateOf(initialLog?.audioAttachments?.size ?: 0) }
    var hasSavedOnce by remember(sessionKey) { mutableStateOf(initialLog != null) }

    var isMenuOpen by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var viewingPhotoRef by remember { mutableStateOf<String?>(null) }
    var isImportingPhoto by remember { mutableStateOf(false) }

    // Audio Playback state (for legacy voice attachments)
    var playingAudioId by remember(stableLogId) { mutableStateOf<String?>(null) }
    var isAudioPaused by remember(stableLogId) { mutableStateOf(false) }
    var audioProgressMs by remember(stableLogId) { mutableIntStateOf(0) }
    var audioDurationMs by remember(stableLogId) { mutableIntStateOf(0) }
    var audioSpeed by remember(stableLogId) { mutableFloatStateOf(1.0f) }
    var activeMediaPlayer by remember(stableLogId) { mutableStateOf<MediaPlayer?>(null) }
    var activeTempAudioFile by remember(stableLogId) { mutableStateOf<File?>(null) }

    fun stopLocalAudio() {
        runCatching { activeMediaPlayer?.stop() }
        runCatching { activeMediaPlayer?.release() }
        activeMediaPlayer = null
        activeTempAudioFile?.let { tmp ->
            if (tmp.name.startsWith("tmp_secret_play_")) {
                runCatching { tmp.delete() }
            }
        }
        activeTempAudioFile = null
        playingAudioId = null
        isAudioPaused = false
        audioProgressMs = 0
        audioDurationMs = 0
    }

    DisposableEffect(stableLogId) {
        onDispose { stopLocalAudio() }
    }

    LaunchedEffect(playingAudioId, isAudioPaused) {
        while (playingAudioId != null && !isAudioPaused) {
            val mp = activeMediaPlayer
            if (mp != null) {
                runCatching {
                    audioProgressMs = mp.currentPosition
                    audioDurationMs = mp.duration.coerceAtLeast(1)
                }
            }
            delay(250L)
        }
    }

    fun toggleAudioAttachment(att: CanvasAudioAttachment) {
        if (playingAudioId == att.id && activeMediaPlayer != null) {
            val mp = activeMediaPlayer!!
            if (isAudioPaused) {
                runCatching {
                    mp.start()
                    isAudioPaused = false
                }
            } else {
                runCatching {
                    mp.pause()
                    isAudioPaused = true
                }
            }
            return
        }
        stopLocalAudio()
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                resolveSecretPlayableAudioFile(context, repository, mediaEngine, att)
            }
            if (file == null || !file.exists()) {
                Toast.makeText(context, "Audio file unavailable.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        stopLocalAudio()
                    }
                    prepare()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioSpeed != 1.0f) {
                        runCatching {
                            val params: PlaybackParams = playbackParams
                            params.speed = audioSpeed
                            playbackParams = params
                        }
                    }
                    audioDurationMs = duration.coerceAtLeast(1)
                    start()
                }
                activeTempAudioFile = file
                activeMediaPlayer = mp
                playingAudioId = att.id
                isAudioPaused = false
            }.onFailure {
                activeTempAudioFile = file
                stopLocalAudio()
                Toast.makeText(context, "Unable to play voice note.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cycleAudioSpeed() {
        val next = when {
            audioSpeed < 1.25f -> 1.5f
            audioSpeed < 1.75f -> 2.0f
            else -> 1.0f
        }
        audioSpeed = next
        val mp = activeMediaPlayer
        if (mp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val wasPlaying = mp.isPlaying
                val params = mp.playbackParams
                params.speed = next
                mp.playbackParams = params
                if (!wasPlaying && mp.isPlaying) mp.pause()
            }
        }
    }

    val hasUnsavedChanges = remember(
        heading,
        baselineHeading,
        bodyText,
        baselineBodyText,
        canvasImages.size,
        baselineImageCount,
        audioAttachments.size,
        baselineAudioCount
    ) {
        heading != baselineHeading ||
            bodyText != baselineBodyText ||
            canvasImages.size != baselineImageCount ||
            audioAttachments.size != baselineAudioCount
    }

    fun buildCurrentDiaryLog(overridePin: String? = entryLockPin, overridePinned: Boolean = isPinned): DiaryLog {
        val saveTime = System.currentTimeMillis()
        val bodyChanged = bodyText != lastHtmlDecodedText || currentContentHtml.isBlank()
        val finalHtml = if (bodyChanged) {
            val baseHtml = editableTextToHtml(bodyText)
            val preservedImgTags = extractInlineHtmlImgTags(currentContentHtml)
            if (preservedImgTags.isNotEmpty()) baseHtml + preservedImgTags.joinToString("") else baseHtml
        } else {
            currentContentHtml
        }
        val cleanPreview = bodyText
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank {
                HtmlCompat.fromHtml(finalHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .replace('\u00A0', ' ')
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .take(180)
        val modDate = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(saveTime))
        val modTime = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(saveTime))
        return DiaryLog(
            id = stableLogId,
            heading = heading.trim().ifBlank { "Untitled Reflection" },
            contentHtml = finalHtml,
            plainPreview = cleanPreview,
            pfpDataUrl = initialLog?.pfpDataUrl,
            createdAt = stableCreatedAt,
            updatedAt = saveTime,
            dateStamp = stableDateStamp,
            timeStamp = stableTimeStamp,
            updatedDateStamp = modDate,
            updatedTimeStamp = modTime,
            reminderAt = initialLog?.reminderAt,
            reminderFired = initialLog?.reminderFired ?: false,
            pinned = overridePinned,
            diaryLockPin = overridePin?.takeIf { it.isNotBlank() },
            canvasBgDataUrl = initialLog?.canvasBgDataUrl,
            canvasBgOpacity = initialLog?.canvasBgOpacity,
            canvasImages = canvasImages,
            audioAttachments = audioAttachments
        )
    }

    fun saveAndUpdateBaseline(exitAfter: Boolean) {
        if (heading.isBlank() && bodyText.isBlank() && canvasImages.isEmpty() && audioAttachments.isEmpty()) {
            if (exitAfter) onExitWorkspace()
            return
        }
        val built = buildCurrentDiaryLog()
        currentContentHtml = built.contentHtml
        lastHtmlDecodedText = bodyText
        heading = built.heading
        baselineHeading = built.heading
        baselineBodyText = bodyText
        baselineImageCount = canvasImages.size
        baselineAudioCount = audioAttachments.size
        lastUpdatedAt = built.updatedAt
        hasSavedOnce = true
        onSaveLog(built, exitAfter)
        if (!exitAfter) {
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onSystemPickerStateChanged(false)
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingPhoto = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { mediaEngine.importImageFromUri(uri) }
            }
            isImportingPhoto = false
            res.onSuccess { imp ->
                val imgW = imp.width.coerceIn(120, 320).toDouble()
                val aspect = imp.height.toDouble() / imp.width.coerceAtLeast(1).toDouble()
                val newItem = CanvasDraggableImage(
                    id = imp.id,
                    dataUrl = imp.assetUrl,
                    x = 24.0,
                    y = 40.0 + (canvasImages.size * 24.0),
                    width = imgW,
                    height = (imgW * aspect).coerceAtLeast(90.0),
                    opacity = 1.0,
                    rotation = 0.0,
                    layer = "foreground",
                    sizeBytes = imp.sizeBytes,
                    originalSizeBytes = imp.originalSizeBytes
                )
                canvasImages = canvasImages + newItem
            }.onFailure { err ->
                Toast.makeText(context, "Could not attach image: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun attemptExit() {
        when {
            viewingPhotoRef != null -> viewingPhotoRef = null
            showExportDialog -> showExportDialog = false
            showDeleteDialog -> showDeleteDialog = false
            showPinDialog -> showPinDialog = false
            isMenuOpen -> isMenuOpen = false
            isEditMode && hasUnsavedChanges -> showDiscardDialog = true
            else -> onExitWorkspace()
        }
    }

    BackHandler { attemptExit() }

    // Collect any legacy inline <img> data URLs/asset refs embedded directly in HTML plus canvasImages
    val galleryImageRefs = remember(canvasImages, currentContentHtml) {
        val inline = extractInlineHtmlImageRefs(currentContentHtml)
        (canvasImages.map { it.dataUrl } + inline)
            .filter { it.isNotBlank() }
            .distinct()
    }

    val readableAnnotatedBody = remember(currentContentHtml, bodyText, lastHtmlDecodedText, isEditMode) {
        val effectiveHtml = if (bodyText != lastHtmlDecodedText) {
            editableTextToHtml(bodyText)
        } else {
            currentContentHtml.ifBlank { editableTextToHtml(bodyText) }
        }
        val spanned = HtmlCompat.fromHtml(effectiveHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        spannedToAnnotatedString(spanned)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Editorial Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(colors.surface)
                    .padding(horizontal = 10.dp),
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
                            .size(38.dp)
                            .clickable { attemptExit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (isEditMode) "Write" else "Read",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.muted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Entry Lock PIN toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.bg)
                            .border(
                                LikkhoHairlineBorder,
                                if (!entryLockPin.isNullOrEmpty()) colors.crimson else colors.border,
                                RectangleShape
                            )
                            .clickable { showPinDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (!entryLockPin.isNullOrEmpty()) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "Entry lock PIN",
                            tint = if (!entryLockPin.isNullOrEmpty()) colors.crimson else colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Toggle Read / Edit
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable {
                                if (isEditMode && hasUnsavedChanges) {
                                    saveAndUpdateBaseline(exitAfter = false)
                                }
                                isEditMode = !isEditMode
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                            contentDescription = if (isEditMode) "Switch to Read mode" else "Edit entry",
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isEditMode) "Read" else "Edit",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.text
                        )
                    }

                    if (isEditMode) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .background(colors.accent)
                                .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                                .clickable { saveAndUpdateBaseline(exitAfter = false) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Save",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Save",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    if (hasSavedOnce) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(colors.bg)
                                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                    .clickable { isMenuOpen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Entry options",
                                    tint = colors.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = isMenuOpen,
                                onDismissRequest = { isMenuOpen = false },
                                shape = RectangleShape,
                                containerColor = colors.bg,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(LikkhoHairlineBorder, colors.border),
                                modifier = Modifier.widthIn(min = 172.dp)
                            ) {
                                DropdownMenuItem(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(40.dp),
                                    text = {
                                        Text(
                                            text = if (isPinned) "Unpin from Top" else "Pin to Top",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontSize = 12.sp,
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
                                    onClick = {
                                        isMenuOpen = false
                                        val nextPinned = !isPinned
                                        isPinned = nextPinned
                                        val built = buildCurrentDiaryLog(overridePinned = nextPinned)
                                        onSaveLog(built, false)
                                    }
                                )
                                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                                DropdownMenuItem(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(40.dp),
                                    text = {
                                        Text(
                                            text = "Share (.likkho)",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontSize = 12.sp,
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
                                    onClick = {
                                        isMenuOpen = false
                                        onShareLikkho(buildCurrentDiaryLog())
                                    }
                                )
                                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                                DropdownMenuItem(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(40.dp),
                                    text = {
                                        Text(
                                            text = "Export",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontSize = 12.sp,
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
                                    onClick = {
                                        isMenuOpen = false
                                        showExportDialog = true
                                    }
                                )
                                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                                DropdownMenuItem(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(40.dp),
                                    text = {
                                        Text(
                                            text = "Delete Entry",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontSize = 12.sp,
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
                                    onClick = {
                                        isMenuOpen = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.border)

            if (isEditMode) {
                // ==================== Distraction-Free Native Editor ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$stableDateStamp · $stableTimeStamp",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 11.sp,
                        color = colors.muted
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (heading.isEmpty()) {
                            Text(
                                text = "Title your reflection...",
                                fontFamily = LikkhoFontFamilies.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = colors.muted.copy(alpha = 0.65f)
                            )
                        }
                        BasicTextField(
                            value = heading,
                            onValueChange = { heading = it },
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                lineHeight = 30.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp)
                    ) {
                        if (bodyText.isEmpty()) {
                            Text(
                                text = "Write freely. Everything stays strictly private on your device...",
                                fontFamily = LikkhoFontFamilies.Prose,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.muted.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = bodyText,
                            onValueChange = { bodyText = it },
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Prose,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 260.dp)
                        )
                    }

                    // Optional Photos Section
                    if (canvasImages.isNotEmpty()) {
                        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                        Text(
                            text = "ATTACHED PHOTOS (${canvasImages.size})",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            canvasImages.forEach { img ->
                                SecretAttachmentThumbCard(
                                    mediaRef = img.dataUrl,
                                    mediaEngine = mediaEngine,
                                    onView = { viewingPhotoRef = img.dataUrl },
                                    onRemove = {
                                        canvasImages = canvasImages.filterNot { it.id == img.id }
                                    }
                                )
                            }
                        }
                    }

                    // Legacy Voice Notes (Preserved cleanly if present)
                    if (audioAttachments.isNotEmpty()) {
                        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                        SecretVoiceNotesSection(
                            audioAttachments = audioAttachments,
                            playingAudioId = playingAudioId,
                            isAudioPaused = isAudioPaused,
                            audioProgressMs = audioProgressMs,
                            audioDurationMs = audioDurationMs,
                            audioSpeed = audioSpeed,
                            onTogglePlay = { toggleAudioAttachment(it) },
                            onCycleSpeed = { cycleAudioSpeed() },
                            onRemove = { target ->
                                if (playingAudioId == target.id) stopLocalAudio()
                                audioAttachments = audioAttachments.filterNot { it.id == target.id }
                            }
                        )
                    }
                }

                // Bottom Minimal Toolbar (Add Photo + Line Helpers)
                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .alpha(if (isImportingPhoto) 0.5f else 1f)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable(enabled = !isImportingPhoto) {
                                onSystemPickerStateChanged(true)
                                photoPickerLauncher.launch("image/*")
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isImportingPhoto) "Adding..." else "Add Photo",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = colors.text
                        )
                    }

                    EditorQuickInsertChip(
                        label = "• Bullet",
                        onClick = {
                            val prefix = if (bodyText.isNotEmpty() && !bodyText.endsWith("\n")) "\n• " else "• "
                            bodyText += prefix
                        }
                    )

                    EditorQuickInsertChip(
                        label = "— Break",
                        onClick = {
                            val sep = if (bodyText.isNotEmpty() && !bodyText.endsWith("\n")) "\n\n———\n\n" else "———\n\n"
                            bodyText += sep
                        }
                    )
                }
            } else {
                // ==================== Editorial Read Mode ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPinned) {
                            Text(
                                text = "PINNED",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.accent,
                                modifier = Modifier
                                    .background(colors.accent.copy(alpha = 0.08f))
                                    .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.5f), RectangleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!entryLockPin.isNullOrEmpty()) {
                            Text(
                                text = "PIN LOCKED",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.crimson,
                                modifier = Modifier
                                    .background(colors.crimson.copy(alpha = 0.08f))
                                    .border(LikkhoHairlineBorder, colors.crimson.copy(alpha = 0.5f), RectangleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = heading.ifBlank { "Untitled Reflection" },
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 27.sp,
                        lineHeight = 33.sp,
                        color = colors.text
                    )

                    val modDateStr = SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.ENGLISH).format(Date(lastUpdatedAt))
                    Text(
                        text = "Created $stableDateStamp · $stableTimeStamp\nUpdated $modDateStr",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = colors.muted
                    )

                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

                    val displayText = if (readableAnnotatedBody.text.isBlank()) {
                        AnnotatedString("No text recorded yet.")
                    } else {
                        readableAnnotatedBody
                    }

                    Text(
                        text = displayText,
                        fontFamily = LikkhoFontFamilies.Prose,
                        fontSize = 15.sp,
                        lineHeight = 25.sp,
                        color = if (readableAnnotatedBody.text.isBlank()) colors.muted else colors.text,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (galleryImageRefs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                        Text(
                            text = "PHOTOS (${galleryImageRefs.size})",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            galleryImageRefs.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { ref ->
                                        SecretReadPhotoTile(
                                            mediaRef = ref,
                                            mediaEngine = mediaEngine,
                                            onClick = { viewingPhotoRef = ref },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (audioAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                        SecretVoiceNotesSection(
                            audioAttachments = audioAttachments,
                            playingAudioId = playingAudioId,
                            isAudioPaused = isAudioPaused,
                            audioProgressMs = audioProgressMs,
                            audioDurationMs = audioDurationMs,
                            audioSpeed = audioSpeed,
                            onTogglePlay = { toggleAudioAttachment(it) },
                            onCycleSpeed = { cycleAudioSpeed() },
                            onRemove = null
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Fullscreen Photo Lightbox
        viewingPhotoRef?.let { photoRef ->
            SecretFullscreenPhotoLightbox(
                mediaRef = photoRef,
                mediaEngine = mediaEngine,
                onDismiss = { viewingPhotoRef = null }
            )
        }
    }

    if (showDiscardDialog) {
        LikkhoHairlineDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = "Save Changes?",
            leadingIcon = Icons.Outlined.WarningAmber,
            maxWidth = 390.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Discard",
                    textColor = colors.crimson,
                    borderColor = colors.crimson.copy(alpha = 0.6f),
                    onClick = {
                        showDiscardDialog = false
                        onExitWorkspace()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Save & Close",
                    onClick = {
                        showDiscardDialog = false
                        saveAndUpdateBaseline(exitAfter = true)
                    }
                )
            }
        ) {
            Text(
                text = "You have unsaved changes in this reflection. Save before returning?",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
        }
    }

    if (showPinDialog) {
        EntryLockPinDialog(
            currentPin = entryLockPin,
            onSavePin = { nextPin ->
                entryLockPin = nextPin
                showPinDialog = false
                if (hasSavedOnce) {
                    onSaveLog(buildCurrentDiaryLog(overridePin = nextPin), false)
                }
                Toast.makeText(
                    context,
                    if (nextPin.isNullOrBlank()) "Entry lock removed." else "Entry lock PIN saved.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showDeleteDialog) {
        LikkhoHairlineDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "Delete Entry?",
            leadingIcon = Icons.Outlined.WarningAmber,
            leadingIconTint = colors.crimson,
            borderColor = colors.crimson,
            titleColor = colors.crimson,
            maxWidth = 380.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    onClick = { showDeleteDialog = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Delete",
                    bgColor = colors.crimson,
                    onClick = {
                        showDeleteDialog = false
                        stopLocalAudio()
                        onDeleteLog(stableLogId)
                    }
                )
            }
        ) {
            Text(
                text = "Permanently delete \"${heading.ifBlank { "Untitled Reflection" }}\"? This cannot be undone.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
        }
    }

    if (showExportDialog) {
        val currentSnapshot = buildCurrentDiaryLog()
        SecretExportFormatDialog(
            log = currentSnapshot,
            onDismiss = { showExportDialog = false },
            onSelectFormat = { fmt ->
                showExportDialog = false
                onExportEntry(currentSnapshot, fmt)
            }
        )
    }
}

@Composable
private fun EditorQuickInsertChip(
    label: String,
    onClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(colors.bg)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = colors.text
        )
    }
}

@Composable
private fun SecretAttachmentThumbCard(
    mediaRef: String,
    mediaEngine: NativeMediaEngine,
    onView: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = mediaRef) {
        value = withContext(Dispatchers.IO) {
            mediaEngine.decodeBoundedBitmapForExport(mediaRef, 360)
        }
    }
    Box(
        modifier = Modifier
            .size(92.dp)
            .background(colors.surface)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
            .clickable { onView() }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = "Attached photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun SecretReadPhotoTile(
    mediaRef: String,
    mediaEngine: NativeMediaEngine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = mediaRef) {
        value = withContext(Dispatchers.IO) {
            mediaEngine.decodeBoundedBitmapForExport(mediaRef, 720)
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(1.25f)
            .background(colors.surface)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = "Entry photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Loading...",
                fontFamily = LikkhoFontFamilies.Mono,
                fontSize = 10.sp,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun SecretFullscreenPhotoLightbox(
    mediaRef: String,
    mediaEngine: NativeMediaEngine,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val fullBmp by produceState<Bitmap?>(initialValue = null, key1 = mediaRef) {
        value = withContext(Dispatchers.IO) {
            mediaEngine.decodeBoundedBitmapForExport(mediaRef, 1600)
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f)
            .background(Color.Black.copy(alpha = 0.94f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    if (nextScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (fullBmp != null) {
            Image(
                bitmap = fullBmp!!.asImageBitmap(),
                contentDescription = "Full photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .border(LikkhoHairlineBorder, Color.White.copy(alpha = 0.35f), RectangleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close viewer",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SecretVoiceNotesSection(
    audioAttachments: List<CanvasAudioAttachment>,
    playingAudioId: String?,
    isAudioPaused: Boolean,
    audioProgressMs: Int,
    audioDurationMs: Int,
    audioSpeed: Float,
    onTogglePlay: (CanvasAudioAttachment) -> Unit,
    onCycleSpeed: () -> Unit,
    onRemove: ((CanvasAudioAttachment) -> Unit)?
) {
    val colors = LikkhoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "VOICE NOTES (${audioAttachments.size})",
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = colors.muted
        )
        audioAttachments.forEach { att ->
            val isThisActive = playingAudioId == att.id
            val isPlayingNow = isThisActive && !isAudioPaused
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(
                        LikkhoHairlineBorder,
                        if (isThisActive) colors.accent else colors.border,
                        RectangleShape
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (isPlayingNow) colors.accent else colors.bg)
                            .border(
                                LikkhoHairlineBorder,
                                if (isPlayingNow) colors.accent else colors.border,
                                RectangleShape
                            )
                            .clickable { onTogglePlay(att) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlayingNow) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlayingNow) "Pause" else "Play",
                            tint = if (isPlayingNow) Color.White else colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = att.name.ifBlank { "Voice Note" },
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val durLabel = if (isThisActive && audioDurationMs > 0) {
                            "${formatSecretMs(audioProgressMs)} / ${formatSecretMs(audioDurationMs)}"
                        } else {
                            att.durationSec?.let { formatSecretSeconds(it) } ?: att.format.uppercase(Locale.US)
                        }
                        Text(
                            text = "${att.format.uppercase(Locale.US)} · $durLabel",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            color = colors.muted
                        )
                    }

                    if (isThisActive) {
                        Box(
                            modifier = Modifier
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                                .clickable { onCycleSpeed() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${audioSpeed}x",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent
                            )
                        }
                    }

                    if (onRemove != null) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { onRemove(att) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove voice note",
                                tint = colors.crimson,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isThisActive && audioDurationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (audioProgressMs.toFloat() / audioDurationMs.toFloat()).coerceIn(0f, 1f) },
                        color = colors.accent,
                        trackColor = colors.hairline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryLockPinDialog(
    currentPin: String?,
    onSavePin: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LikkhoTheme.colors
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = if (currentPin.isNullOrBlank()) "Lock This Entry" else "Change Entry Lock",
        leadingIcon = Icons.Outlined.Lock,
        maxWidth = 380.dp,
        footer = {
            if (!currentPin.isNullOrBlank()) {
                LikkhoDialogOutlineButton(
                    text = "Remove Lock",
                    textColor = colors.crimson,
                    borderColor = colors.crimson.copy(alpha = 0.6f),
                    bgColor = colors.crimson.copy(alpha = 0.08f),
                    onClick = { onSavePin(null) }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            LikkhoDialogOutlineButton(
                text = "Cancel",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = "Save PIN",
                onClick = {
                    val clean = pin.trim()
                    when {
                        clean.length !in 4..6 || !clean.all { it.isDigit() } -> {
                            errorMsg = "PIN must be 4 to 6 digits."
                        }
                        clean != confirmPin.trim() -> {
                            errorMsg = "PIN entries do not match."
                        }
                        else -> onSavePin(clean)
                    }
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
                    .padding(8.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Enter 4–6 Digit Entry PIN",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.muted
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
                BasicTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; errorMsg = null } },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(fontFamily = LikkhoFontFamilies.Mono, fontSize = 15.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Confirm Entry PIN",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.muted
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
                BasicTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { confirmPin = it; errorMsg = null } },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(fontFamily = LikkhoFontFamilies.Mono, fontSize = 15.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun htmlToEditableText(html: String, plainFallback: String): String {
    if (html.isBlank()) return plainFallback
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .ifBlank { plainFallback }
}

private fun editableTextToHtml(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.split(Regex("\\r?\\n")).joinToString("") { line ->
        val escaped = line
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        "<p>${if (escaped.isNotEmpty()) escaped else "<br/>"}</p>"
    }
}

private fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString = buildAnnotatedString {
    val raw = spanned.toString().replace('\u00A0', ' ').trimEnd()
    append(raw)
    val len = raw.length
    val spans = spanned.getSpans(0, spanned.length, Any::class.java)
    for (span in spans) {
        val start = spanned.getSpanStart(span).coerceIn(0, len)
        val end = spanned.getSpanEnd(span).coerceIn(start, len)
        if (start >= end) continue
        when (span) {
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end
                )
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is RelativeSizeSpan -> {
                val factor = span.sizeChange.coerceIn(0.85f, 1.5f)
                addStyle(SpanStyle(fontSize = (15f * factor).sp), start, end)
            }
        }
    }
}

private val INLINE_IMG_SRC_REGEX = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val INLINE_IMG_TAG_REGEX = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)

private fun extractInlineHtmlImageRefs(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    return INLINE_IMG_SRC_REGEX.findAll(html)
        .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { s -> s.isNotBlank() } }
        .toList()
}

private fun extractInlineHtmlImgTags(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    return INLINE_IMG_TAG_REGEX.findAll(html)
        .map { it.value }
        .distinct()
        .toList()
}

private fun resolveSecretPlayableAudioFile(
    context: android.content.Context,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    attachment: CanvasAudioAttachment
): File? {
    repository.resolveExistingAudioFile(attachment.id, attachment.format)?.let { return it }
    attachment.audioUri?.takeIf { it.isNotBlank() }?.let { uri ->
        repository.resolveExistingAudioFile(repository.normalizeMediaId(uri), attachment.format)?.let { return it }
    }
    val rawUrl = attachment.dataUrl
    if (!rawUrl.isNullOrBlank() && rawUrl.startsWith("data:")) {
        val parsed = mediaEngine.parseDataUrl(rawUrl)
        if (parsed != null) {
            return runCatching {
                val ext = attachment.format.ifBlank { parsed.ext }
                val tmp = File(
                    context.cacheDir,
                    "tmp_secret_play_${attachment.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.$ext"
                )
                FileOutputStream(tmp).use { it.write(parsed.bytes) }
                tmp
            }.getOrNull()
        }
        val commaIdx = rawUrl.indexOf(',')
        if (commaIdx > 0) {
            runCatching {
                val bytes = Base64.decode(rawUrl.substring(commaIdx + 1), Base64.DEFAULT)
                val ext = attachment.format.ifBlank { "wav" }
                val tmp = File(
                    context.cacheDir,
                    "tmp_secret_play_${attachment.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.$ext"
                )
                FileOutputStream(tmp).use { it.write(bytes) }
                return tmp
            }
        }
    }
    return null
}

private fun formatSecretSeconds(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

private fun formatSecretMs(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

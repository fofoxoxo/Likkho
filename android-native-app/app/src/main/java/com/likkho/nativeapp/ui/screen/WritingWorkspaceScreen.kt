package com.likkho.nativeapp.ui.screen

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.likkho.nativeapp.data.model.CanvasAudioAttachment
import com.likkho.nativeapp.data.model.CanvasDraggableImage
import com.likkho.nativeapp.data.model.DiaryLog
import com.likkho.nativeapp.service.MicForegroundService
import com.likkho.nativeapp.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun WritingWorkspaceScreen(
    initialLog: DiaryLog?,
    vaultMode: String,
    onSaveAndExit: (DiaryLog) -> Unit,
    onBackWithoutSave: () -> Unit
) {
    val context = LocalContext.current

    var heading by remember { mutableStateOf(initialLog?.heading ?: "") }
    var contentText by remember { mutableStateOf(initialLog?.plainPreview ?: "") }
    var canvasImages by remember { mutableStateOf(initialLog?.canvasImages ?: emptyList()) }
    var audioAttachments by remember { mutableStateOf(initialLog?.audioAttachments ?: emptyList()) }
    var diaryLockPin by remember { mutableStateOf(initialLog?.diaryLockPin) }
    var showPinDialog by remember { mutableStateOf(false) }

    // Recording status
    var isRecording by remember { mutableStateOf(MicForegroundService.isRecordingActive) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mediaDir = File(context.filesDir, "likkho_media").apply { if (!exists()) mkdirs() }
            val imgId = "img_${UUID.randomUUID()}"
            val targetFile = File(mediaDir, "$imgId.jpg")

            context.contentResolver.openInputStream(it)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val newImg = CanvasDraggableImage(
                id = imgId,
                filePath = targetFile.absolutePath,
                x = 40f,
                y = 80f,
                width = 220f,
                height = 220f
            )
            canvasImages = canvasImages + newImg
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBackWithoutSave) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }

                    TextField(
                        value = heading,
                        onValueChange = { heading = it },
                        placeholder = { Text("Title of Diary...", fontFamily = WikiSerif, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showPinDialog = true }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Lock Diary",
                                tint = if (diaryLockPin != null) WikiAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val now = System.currentTimeMillis()
                                val updated = DiaryLog(
                                    id = initialLog?.id ?: UUID.randomUUID().toString(),
                                    heading = heading.ifBlank { "Untitled Entry" },
                                    contentHtml = "<p>${contentText.replace("\n", "<br/>")}</p>",
                                    plainPreview = contentText.take(160),
                                    createdAt = initialLog?.createdAt ?: now,
                                    updatedAt = now,
                                    pinned = initialLog?.pinned ?: false,
                                    diaryLockPin = diaryLockPin,
                                    canvasImages = canvasImages,
                                    audioAttachments = audioAttachments,
                                    vaultMode = vaultMode
                                )
                                onSaveAndExit(updated)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WikiBlue),
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Formatting & Media Quick Action Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(0.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Photo", tint = WikiBlue)
                    }

                    IconButton(onClick = {
                        if (isRecording) {
                            MicForegroundService.stop(context)
                            isRecording = false
                        } else {
                            MicForegroundService.start(context)
                            isRecording = true
                        }
                    }) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record Voice Note",
                            tint = if (isRecording) WikiRed else WikiGreen
                        )
                    }

                    IconButton(onClick = { contentText += "\n**Bold**" }) {
                        Icon(Icons.Default.FormatBold, contentDescription = "Bold", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(onClick = { contentText += "\n*Italic*" }) {
                        Icon(Icons.Default.FormatItalic, contentDescription = "Italic", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(onClick = { contentText += "\n## Heading" }) {
                        Icon(Icons.Default.Title, contentDescription = "Heading", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(onClick = { contentText += "\n> Quote" }) {
                        Icon(Icons.Default.FormatQuote, contentDescription = "Quote", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Freeform Movable Canvas Images
            canvasImages.forEach { img ->
                DraggableCanvasImage(
                    image = img,
                    onUpdate = { updated ->
                        canvasImages = canvasImages.map { if (it.id == updated.id) updated else it }
                    },
                    onDelete = {
                        canvasImages = canvasImages.filter { it.id != img.id }
                    }
                )
            }

            // Freeform Draggable Audio Player Cards
            audioAttachments.forEach { audio ->
                DraggableAudioCard(
                    audio = audio,
                    onDelete = {
                        audioAttachments = audioAttachments.filter { it.id != audio.id }
                    }
                )
            }

            // Main Text Writing Area
            TextField(
                value = contentText,
                onValueChange = { contentText = it },
                placeholder = { Text("Write your story, thoughts or research here...", style = MaterialTheme.typography.bodyLarge) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )

            // Active Voice Recording Indicator Banner
            if (isRecording) {
                Surface(
                    color = WikiRed.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Recording Audio Note in Background...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Diary Passcode Protection Dialog
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf(diaryLockPin ?: "") }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set Diary Lock PIN", fontFamily = WikiSerif) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a 4-digit PIN to lock this specific entry:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        placeholder = { Text("••••") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        diaryLockPin = pinInput.takeIf { it.isNotBlank() }
                        showPinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WikiBlue)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                if (diaryLockPin != null) {
                    TextButton(onClick = {
                        diaryLockPin = null
                        showPinDialog = false
                    }) {
                        Text("Remove Lock", color = WikiRed)
                    }
                }
            }
        )
    }
}

@Composable
fun DraggableCanvasImage(
    image: CanvasDraggableImage,
    onUpdate: (CanvasDraggableImage) -> Unit,
    onDelete: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset(image.x, image.y)) }
    var scale by remember { mutableStateOf(1f) }

    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 3f)
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size((image.width * scale).dp, (image.height * scale).dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset = Offset(offset.x + dragAmount.x, offset.y + dragAmount.y)
                    onUpdate(image.copy(x = offset.x, y = offset.y))
                }
            }
            .transformable(state = transformState)
            .border(1.dp, WikiBorderLight, RoundedCornerShape(2.dp))
            .background(Color.White)
    ) {
        AsyncImage(
            model = File(image.filePath),
            contentDescription = "Canvas Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Close/Delete button on top corner
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Delete Photo", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun DraggableAudioCard(
    audio: CanvasAudioAttachment,
    onDelete: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audio.filePath) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Surface(
        modifier = Modifier
            .offset(audio.x.dp, audio.y.dp)
            .width(260.dp)
            .border(1.dp, WikiGreen, RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            try {
                                if (mediaPlayer == null) {
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(audio.filePath)
                                        prepare()
                                        setOnCompletionListener { isPlaying = false }
                                    }
                                }
                                mediaPlayer?.start()
                                isPlaying = true
                            } catch (e: Exception) {
                                isPlaying = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(WikiGreen, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(audio.name, style = MaterialTheme.typography.titleMedium, fontSize = 13.sp, maxLines = 1)
                    Text(".${audio.format} • ${audio.durationSec.toInt()}s", style = MaterialTheme.typography.labelSmall)
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = WikiRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}

package com.likkho.nativeapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.likkho.nativeapp.data.local.RoomConverters

@Entity(tableName = "diary_logs")
data class DiaryLog(
    @PrimaryKey
    val id: String,
    val heading: String,
    val contentHtml: String,
    val plainPreview: String,
    val pfpDataUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dateStamp: String = "",
    val timeStamp: String = "",
    val updatedDateStamp: String? = null,
    val updatedTimeStamp: String? = null,
    val reminderAt: Long? = null,
    val reminderFired: Boolean = false,
    val pinned: Boolean = false,
    val diaryLockPin: String? = null,
    val canvasBgDataUrl: String? = null,
    val canvasBgOpacity: Float = 1.0f,
    val canvasImages: List<CanvasDraggableImage> = emptyList(),
    val audioAttachments: List<CanvasAudioAttachment> = emptyList(),
    val vaultMode: String = "primary" // "primary" vs "decoy" isolation
)

data class CanvasDraggableImage(
    val id: String,
    val filePath: String = "", // Direct local disk path (low RAM, zero base64 in memory)
    val dataUrl: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 200f,
    val height: Float = 200f,
    val opacity: Float = 1f,
    val rotation: Float = 0f,
    val layer: String = "foreground"
)

data class CanvasAudioAttachment(
    val id: String,
    val name: String,
    val format: String = "wav",
    val filePath: String = "", // Native audio file path (direct streaming playback)
    val durationSec: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 260f,
    val height: Float = 60f,
    val rotation: Float = 0f,
    val playbackRate: Float = 1f,
    val layer: String = "foreground"
)

@Entity(tableName = "custom_fonts")
data class CustomFontItem(
    @PrimaryKey
    val id: String,
    val name: String,
    val lang: String = "custom",
    val fontFamily: String,
    val filePath: String = "",
    val vaultMode: String = "primary"
)

data class MicRecordingSettings(
    val format: String = "wav",
    val sampleRate: Int = 44100,
    val bitRate: Int = 128000
)

data class VaultBackupBundle(
    val logs: List<DiaryLog>,
    val customFonts: List<CustomFontItem> = emptyList(),
    val micSettings: MicRecordingSettings = MicRecordingSettings()
)

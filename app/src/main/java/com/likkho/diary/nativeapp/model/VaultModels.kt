package com.likkho.diary.nativeapp.model

import org.json.JSONArray
import org.json.JSONObject

enum class VaultMode(val wireValue: String) {
    PRIMARY("primary"),
    DECOY("decoy");

    companion object {
        fun fromWire(value: String?): VaultMode =
            if (value == "decoy") DECOY else PRIMARY
    }
}

enum class AudioFormatOption(val wireValue: String) {
    WAV("wav"),
    FLAC("flac"),
    M4A("m4a"),
    AAC("aac"),
    MP3("mp3"),
    OGG("ogg"),
    WEBM("webm");

    companion object {
        fun fromWire(value: String?): AudioFormatOption =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: WAV
    }
}

data class MicRecordingSettings(
    val format: AudioFormatOption = AudioFormatOption.WAV,
    val sampleRate: Int = 44100,
    val bitRate: Int = 128000
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", format.wireValue)
        put("sampleRate", sampleRate)
        put("bitRate", bitRate)
    }

    companion object {
        fun fromJson(obj: JSONObject?): MicRecordingSettings {
            if (obj == null) return MicRecordingSettings()
            return MicRecordingSettings(
                format = AudioFormatOption.fromWire(obj.optString("format", "wav")),
                sampleRate = obj.optInt("sampleRate", 44100),
                bitRate = obj.optInt("bitRate", 128000)
            )
        }
    }
}

data class CanvasDraggableImage(
    val id: String,
    val dataUrl: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val opacity: Double = 1.0,
    val rotation: Double = 0.0,
    val layer: String = "foreground",
    val sizeBytes: Long? = null,
    val originalSizeBytes: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("dataUrl", dataUrl)
        put("x", x)
        put("y", y)
        put("width", width)
        put("height", height)
        put("opacity", opacity)
        put("rotation", rotation)
        put("layer", layer)
        if (sizeBytes != null) put("sizeBytes", sizeBytes)
        if (originalSizeBytes != null) put("originalSizeBytes", originalSizeBytes)
    }

    companion object {
        fun fromJson(obj: JSONObject): CanvasDraggableImage = CanvasDraggableImage(
            id = obj.optString("id"),
            dataUrl = obj.optString("dataUrl"),
            x = obj.optDouble("x", 24.0),
            y = obj.optDouble("y", 40.0),
            width = obj.optDouble("width", 220.0),
            height = obj.optDouble("height", 165.0),
            opacity = obj.optDouble("opacity", 1.0),
            rotation = obj.optDouble("rotation", 0.0),
            layer = obj.optString("layer", "foreground"),
            sizeBytes = if (obj.has("sizeBytes") && !obj.isNull("sizeBytes")) obj.optLong("sizeBytes") else null,
            originalSizeBytes = if (obj.has("originalSizeBytes") && !obj.isNull("originalSizeBytes")) obj.optLong("originalSizeBytes") else null
        )
    }
}

data class CanvasAudioAttachment(
    val id: String,
    val name: String,
    val format: String,
    val dataUrl: String? = null,
    val audioUri: String? = null,
    val durationSec: Double? = null,
    val createdAt: Long = 0L,
    val x: Double = 24.0,
    val y: Double = 120.0,
    val width: Double = 270.0,
    val height: Double = 56.0,
    val rotation: Double = 0.0,
    val playbackRate: Double = 1.0,
    val layer: String = "foreground"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("format", format)
        if (dataUrl != null) put("dataUrl", dataUrl)
        if (audioUri != null) put("audioUri", audioUri)
        if (durationSec != null) put("durationSec", durationSec)
        put("createdAt", createdAt)
        put("x", x)
        put("y", y)
        put("width", width)
        put("height", height)
        put("rotation", rotation)
        put("playbackRate", playbackRate)
        put("layer", layer)
    }

    companion object {
        fun fromJson(obj: JSONObject): CanvasAudioAttachment = CanvasAudioAttachment(
            id = obj.optString("id"),
            name = obj.optString("name", "Voice Note"),
            format = obj.optString("format", "wav"),
            dataUrl = obj.optString("dataUrl").takeIf { it.isNotEmpty() && !obj.isNull("dataUrl") },
            audioUri = obj.optString("audioUri").takeIf { it.isNotEmpty() && !obj.isNull("audioUri") },
            durationSec = if (obj.has("durationSec") && !obj.isNull("durationSec")) obj.optDouble("durationSec") else null,
            createdAt = obj.optLong("createdAt", 0L),
            x = obj.optDouble("x", 24.0),
            y = obj.optDouble("y", 120.0),
            width = obj.optDouble("width", 270.0),
            height = obj.optDouble("height", 56.0),
            rotation = obj.optDouble("rotation", 0.0),
            playbackRate = obj.optDouble("playbackRate", 1.0),
            layer = obj.optString("layer", "foreground")
        )
    }
}

data class CustomFontItem(
    val id: String,
    val name: String,
    val lang: String,
    val fontFamily: String,
    val dataUrl: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("lang", lang)
        put("fontFamily", fontFamily)
        put("dataUrl", dataUrl)
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomFontItem = CustomFontItem(
            id = obj.optString("id"),
            name = obj.optString("name"),
            lang = obj.optString("lang", "custom"),
            fontFamily = obj.optString("fontFamily"),
            dataUrl = obj.optString("dataUrl")
        )
    }
}

data class DiaryLog(
    val id: String,
    val heading: String,
    val contentHtml: String,
    val plainPreview: String,
    val pfpDataUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val dateStamp: String,
    val timeStamp: String,
    val updatedDateStamp: String? = null,
    val updatedTimeStamp: String? = null,
    val reminderAt: Long? = null,
    val reminderFired: Boolean = false,
    val pinned: Boolean = false,
    val diaryLockPin: String? = null,
    val canvasBgDataUrl: String? = null,
    val canvasBgOpacity: Double? = null,
    val canvasImages: List<CanvasDraggableImage> = emptyList(),
    val audioAttachments: List<CanvasAudioAttachment> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("heading", heading)
        put("contentHtml", contentHtml)
        put("plainPreview", plainPreview)
        put("pfpDataUrl", pfpDataUrl ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("dateStamp", dateStamp)
        put("timeStamp", timeStamp)
        if (updatedDateStamp != null) put("updatedDateStamp", updatedDateStamp)
        if (updatedTimeStamp != null) put("updatedTimeStamp", updatedTimeStamp)
        put("reminderAt", reminderAt ?: JSONObject.NULL)
        put("reminderFired", reminderFired)
        put("pinned", pinned)
        put("diaryLockPin", diaryLockPin ?: JSONObject.NULL)
        put("canvasBgDataUrl", canvasBgDataUrl ?: JSONObject.NULL)
        if (canvasBgOpacity != null) put("canvasBgOpacity", canvasBgOpacity)
        put("canvasImages", JSONArray().apply { canvasImages.forEach { put(it.toJson()) } })
        put("audioAttachments", JSONArray().apply { audioAttachments.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): DiaryLog {
            val imagesArr = obj.optJSONArray("canvasImages")
            val images = buildList {
                if (imagesArr != null) {
                    for (i in 0 until imagesArr.length()) {
                        imagesArr.optJSONObject(i)?.let { add(CanvasDraggableImage.fromJson(it)) }
                    }
                }
            }
            val audiosArr = obj.optJSONArray("audioAttachments")
            val audios = buildList {
                if (audiosArr != null) {
                    for (i in 0 until audiosArr.length()) {
                        audiosArr.optJSONObject(i)?.let { add(CanvasAudioAttachment.fromJson(it)) }
                    }
                }
            }
            return DiaryLog(
                id = obj.optString("id"),
                heading = obj.optString("heading", ""),
                contentHtml = obj.optString("contentHtml", ""),
                plainPreview = obj.optString("plainPreview", ""),
                pfpDataUrl = obj.optString("pfpDataUrl").takeIf { it.isNotEmpty() && !obj.isNull("pfpDataUrl") },
                createdAt = obj.optLong("createdAt", 0L),
                updatedAt = obj.optLong("updatedAt", 0L),
                dateStamp = obj.optString("dateStamp", ""),
                timeStamp = obj.optString("timeStamp", ""),
                updatedDateStamp = obj.optString("updatedDateStamp").takeIf { it.isNotEmpty() && !obj.isNull("updatedDateStamp") },
                updatedTimeStamp = obj.optString("updatedTimeStamp").takeIf { it.isNotEmpty() && !obj.isNull("updatedTimeStamp") },
                reminderAt = if (obj.has("reminderAt") && !obj.isNull("reminderAt")) obj.optLong("reminderAt") else null,
                reminderFired = obj.optBoolean("reminderFired", false),
                pinned = obj.optBoolean("pinned", false),
                diaryLockPin = obj.optString("diaryLockPin").takeIf { it.isNotEmpty() && !obj.isNull("diaryLockPin") },
                canvasBgDataUrl = obj.optString("canvasBgDataUrl").takeIf { it.isNotEmpty() && !obj.isNull("canvasBgDataUrl") },
                canvasBgOpacity = if (obj.has("canvasBgOpacity") && !obj.isNull("canvasBgOpacity")) obj.optDouble("canvasBgOpacity") else null,
                canvasImages = images,
                audioAttachments = audios
            )
        }
    }
}

data class SavedPasswordItem(
    val id: String,
    val name: String,
    val password: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("password", password)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): SavedPasswordItem = SavedPasswordItem(
            id = obj.optString("id"),
            name = obj.optString("name"),
            password = obj.optString("password"),
            createdAt = obj.optLong("createdAt", 0L)
        )
    }
}

data class QuickNoteChecklistItem(
    val id: String,
    val text: String,
    val checked: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("checked", checked)
    }

    companion object {
        fun fromJson(obj: JSONObject): QuickNoteChecklistItem = QuickNoteChecklistItem(
            id = obj.optString("id"),
            text = obj.optString("text"),
            checked = obj.optBoolean("checked", false)
        )
    }
}

data class QuickNote(
    val id: String,
    val title: String,
    val items: List<QuickNoteChecklistItem> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDone: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isDone", isDone)
    }

    companion object {
        fun fromJson(obj: JSONObject): QuickNote {
            val arr = obj.optJSONArray("items")
            val parsedItems = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(QuickNoteChecklistItem.fromJson(it)) }
                    }
                }
            }
            return QuickNote(
                id = obj.optString("id"),
                title = obj.optString("title", ""),
                items = parsedItems,
                createdAt = obj.optLong("createdAt", 0L),
                updatedAt = obj.optLong("updatedAt", 0L),
                isDone = obj.optBoolean("isDone", false)
            )
        }
    }
}

data class VaultBackupBundle(
    val logs: List<DiaryLog>,
    val customFonts: List<CustomFontItem> = emptyList(),
    val micSettings: MicRecordingSettings = MicRecordingSettings(),
    val savedPasswords: List<SavedPasswordItem> = emptyList(),
    val quickNotes: List<QuickNote> = emptyList(),
    val skippedMediaCount: Int = 0,
    val skippedMediaIds: List<String> = emptyList()
)

data class EncryptedVaultMetadata(
    val exists: Boolean,
    val lastBackupAt: Long?,
    val entryCount: Int,
    val vaultPath: String,
    val sizeBytes: Long,
    val keyHintHash: String?,
    val failedMediaCount: Int = 0,
    val failedMediaIds: List<String> = emptyList()
)

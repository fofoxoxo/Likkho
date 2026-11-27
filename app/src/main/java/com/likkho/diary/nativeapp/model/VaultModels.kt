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
    val createdAt: Long,
    val username: String = "",
    val url: String = "",
    val notes: String = "",
    val updatedAt: Long = createdAt
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("password", password)
        put("createdAt", createdAt)
        if (username.isNotEmpty()) put("username", username)
        if (url.isNotEmpty()) put("url", url)
        if (notes.isNotEmpty()) put("notes", notes)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): SavedPasswordItem {
            val created = obj.optLong("createdAt", 0L)
            return SavedPasswordItem(
                id = obj.optString("id"),
                name = obj.optString("name"),
                password = obj.optString("password"),
                createdAt = created,
                username = obj.optString("username", ""),
                url = obj.optString("url", ""),
                notes = obj.optString("notes", ""),
                updatedAt = obj.optLong("updatedAt", created)
            )
        }
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
        put("done", checked)
    }

    companion object {
        fun fromJson(obj: JSONObject): QuickNoteChecklistItem = QuickNoteChecklistItem(
            id = obj.optString("id"),
            text = obj.optString("text"),
            checked = if (obj.has("checked")) obj.optBoolean("checked", false) else obj.optBoolean("done", false)
        )
    }
}

data class QuickNote(
    val id: String,
    val title: String,
    val items: List<QuickNoteChecklistItem> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDone: Boolean = false,
    val content: String = "",
    val isChecklist: Boolean = false,
    val isPinnedToNotification: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("isChecklist", isChecklist)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isDone", isDone)
        put("isPinnedToNotification", isPinnedToNotification)
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
                isDone = obj.optBoolean("isDone", false),
                content = obj.optString("content", ""),
                isChecklist = obj.optBoolean("isChecklist", false),
                isPinnedToNotification = obj.optBoolean("isPinnedToNotification", true)
            )
        }
    }
}

enum class TrackerEntryStatus(val wireValue: String) {
    PRESENT("present"),
    ABSENT("absent"),
    SKIPPED("skipped");

    companion object {
        fun fromWire(value: String?): TrackerEntryStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: PRESENT
    }
}

data class TrackerDayEntry(
    val dateKey: String,
    val status: TrackerEntryStatus = TrackerEntryStatus.PRESENT,
    val quantity: Double? = null,
    val note: String = "",
    val updatedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("dateKey", dateKey)
        put("status", status.wireValue)
        if (quantity != null) put("quantity", quantity)
        if (note.isNotEmpty()) put("note", note)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject, fallbackDateKey: String = ""): TrackerDayEntry = TrackerDayEntry(
            dateKey = obj.optString("dateKey", fallbackDateKey).ifBlank { fallbackDateKey },
            status = TrackerEntryStatus.fromWire(obj.optString("status", "present")),
            quantity = if (obj.has("quantity") && !obj.isNull("quantity")) obj.optDouble("quantity") else null,
            note = obj.optString("note", ""),
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }
}

data class RecurringTracker(
    val id: String,
    val name: String,
    val unitLabel: String = "",
    val defaultQuantity: Double = 1.0,
    val ratePerUnit: Double = 0.0,
    val currencySymbol: String = "₹",
    val accentHex: String = "#3366CC",
    val notes: String = "",
    val archived: Boolean = false,
    val entries: Map<String, TrackerDayEntry> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("unitLabel", unitLabel)
        put("defaultQuantity", defaultQuantity)
        put("ratePerUnit", ratePerUnit)
        put("currencySymbol", currencySymbol)
        put("accentHex", accentHex)
        put("notes", notes)
        put("archived", archived)
        put("entries", JSONObject().apply {
            entries.forEach { (key, entry) ->
                put(key, entry.toJson())
            }
        })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): RecurringTracker {
            val entriesObj = obj.optJSONObject("entries")
            val parsedEntries = linkedMapOf<String, TrackerDayEntry>()
            if (entriesObj != null) {
                val keys = entriesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    entriesObj.optJSONObject(key)?.let { entryObj ->
                        parsedEntries[key] = TrackerDayEntry.fromJson(entryObj, fallbackDateKey = key)
                    }
                }
            }
            return RecurringTracker(
                id = obj.optString("id"),
                name = obj.optString("name", ""),
                unitLabel = obj.optString("unitLabel", ""),
                defaultQuantity = obj.optDouble("defaultQuantity", 1.0),
                ratePerUnit = obj.optDouble("ratePerUnit", 0.0),
                currencySymbol = obj.optString("currencySymbol", "₹").ifBlank { "₹" },
                accentHex = obj.optString("accentHex", "#3366CC").ifBlank { "#3366CC" },
                notes = obj.optString("notes", ""),
                archived = obj.optBoolean("archived", false),
                entries = parsedEntries,
                createdAt = obj.optLong("createdAt", 0L),
                updatedAt = obj.optLong("updatedAt", 0L)
            )
        }
    }
}

enum class ReminderRepeat(val wireValue: String, val displayLabel: String) {
    NONE("none", "Once"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly");

    companion object {
        fun fromWire(value: String?): ReminderRepeat =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: NONE
    }
}

data class StandaloneReminder(
    val id: String,
    val title: String,
    val note: String = "",
    val triggerAtMs: Long,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("note", note)
        put("triggerAtMs", triggerAtMs)
        put("repeat", repeat.wireValue)
        put("isCompleted", isCompleted)
        put("completedAt", completedAt ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): StandaloneReminder = StandaloneReminder(
            id = obj.optString("id"),
            title = obj.optString("title", ""),
            note = obj.optString("note", ""),
            triggerAtMs = obj.optLong("triggerAtMs", 0L),
            repeat = ReminderRepeat.fromWire(obj.optString("repeat", "none")),
            isCompleted = obj.optBoolean("isCompleted", false),
            completedAt = if (obj.has("completedAt") && !obj.isNull("completedAt")) obj.optLong("completedAt") else null,
            createdAt = obj.optLong("createdAt", 0L),
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }
}

enum class DocumentCategory(val wireValue: String, val displayLabel: String) {
    ID_CARD("id_card", "ID Card"),
    FINANCE("finance", "Finance"),
    TRAVEL("travel", "Travel"),
    EDUCATION("education", "Education"),
    MEDICAL("medical", "Medical"),
    OTHER("other", "Other");

    companion object {
        fun fromWire(value: String?): DocumentCategory =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: OTHER
    }
}

data class PersonalDocument(
    val id: String,
    val title: String,
    val category: DocumentCategory = DocumentCategory.OTHER,
    val idNumber: String = "",
    val fileId: String? = null,
    val fileName: String = "",
    val mimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val notes: String = "",
    val issuedDate: String = "",
    val expiryDate: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("category", category.wireValue)
        put("idNumber", idNumber)
        put("fileId", fileId ?: JSONObject.NULL)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("fileSizeBytes", fileSizeBytes)
        put("notes", notes)
        put("issuedDate", issuedDate)
        put("expiryDate", expiryDate)
        put("pinned", pinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): PersonalDocument = PersonalDocument(
            id = obj.optString("id"),
            title = obj.optString("title", ""),
            category = DocumentCategory.fromWire(obj.optString("category", "other")),
            idNumber = obj.optString("idNumber", ""),
            fileId = obj.optString("fileId").takeIf { it.isNotEmpty() && !obj.isNull("fileId") },
            fileName = obj.optString("fileName", ""),
            mimeType = obj.optString("mimeType", ""),
            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
            notes = obj.optString("notes", ""),
            issuedDate = obj.optString("issuedDate", ""),
            expiryDate = obj.optString("expiryDate", ""),
            pinned = obj.optBoolean("pinned", false),
            createdAt = obj.optLong("createdAt", 0L),
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }
}

data class VaultBackupBundle(
    val logs: List<DiaryLog>,
    val customFonts: List<CustomFontItem> = emptyList(),
    val micSettings: MicRecordingSettings = MicRecordingSettings(),
    val savedPasswords: List<SavedPasswordItem> = emptyList(),
    val quickNotes: List<QuickNote> = emptyList(),
    val trackers: List<RecurringTracker> = emptyList(),
    val reminders: List<StandaloneReminder> = emptyList(),
    val documents: List<PersonalDocument> = emptyList(),
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

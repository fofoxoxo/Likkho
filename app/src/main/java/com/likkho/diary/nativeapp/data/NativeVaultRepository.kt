package com.likkho.diary.nativeapp.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.MimeTypeMap
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.CustomFontItem
import com.likkho.diary.nativeapp.model.EncryptedVaultMetadata
import com.likkho.diary.nativeapp.model.MicRecordingSettings
import com.likkho.diary.nativeapp.model.PersonalDocument
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.RecurringTracker
import com.likkho.diary.nativeapp.model.SavedPasswordItem
import com.likkho.diary.nativeapp.model.StandaloneReminder
import com.likkho.diary.nativeapp.model.TrackerDayEntry
import com.likkho.diary.nativeapp.model.VaultConfig
import com.likkho.diary.nativeapp.model.VaultMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PasscodeGuardState(
    val failedAttempts: Int = 0,
    val cooldownUntilMs: Long = 0L
)

data class PasscodeFailureResult(
    val failedAttempts: Int,
    val cooldownSeconds: Int,
    val cooldownUntilMs: Long
)

data class ImportedDocumentFile(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

/**
 * Isolated Dual-Vault Local Repository (Primary vs Covert Decoy Vault)
 *
 * Security & Storage Guarantees:
 * - Zero cross-vault leakage: `likkho_vaults/primary/` vs `likkho_vaults/decoy/`.
 * - All sensitive vault JSON files & security config are encrypted at rest using an
 *   AndroidKeyStore AES-256-GCM key (`likkho_native_vault_master_v1`).
 * - App Passcodes (Primary & Decoy) are stored strictly as salted PBKDF2-HMAC-SHA256
 *   hashes verified via constant-time `MessageDigest.isEqual`.
 * - Atomic file writes (`.tmp` -> rename) prevent partial corruption on crash.
 * - Strict path-confinement helper for `likkho_media/` and `likkho_audio/`.
 */
class NativeVaultRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val safPrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private val securityLock = Any()

    @Volatile
    private var cachedSecurityJson: JSONObject? = null

    val mediaImagesDir: File
        get() = File(appContext.filesDir, MEDIA_IMAGES_DIR).apply { if (!exists()) mkdirs() }

    val mediaAudioDir: File
        get() = File(appContext.filesDir, MEDIA_AUDIO_DIR).apply { if (!exists()) mkdirs() }

    val mediaDocsDir: File
        get() = File(appContext.filesDir, MEDIA_DOCS_DIR).apply {
            if (!exists()) mkdirs()
            val nomedia = File(this, ".nomedia")
            if (!nomedia.exists()) {
                runCatching { nomedia.createNewFile() }
            }
        }

    val previewCacheDir: File
        get() = File(appContext.cacheDir, PREVIEW_CACHE_DIR).apply { if (!exists()) mkdirs() }

    fun vaultDir(mode: VaultMode): File {
        val cfg = VaultConfig.forMode(mode)
        return File(appContext.filesDir, "$VAULTS_ROOT_DIR/${cfg.internalFolderName}").apply {
            if (!exists()) mkdirs()
        }
    }

    // ==================== Path-Confined Media Resolution ====================

    fun normalizeMediaId(rawRef: String): String {
        val trimmed = rawRef.trim()
        return when {
            trimmed.startsWith(ASSET_MEDIA_PREFIX) -> trimmed.removePrefix(ASSET_MEDIA_PREFIX)
            trimmed.startsWith(ASSET_AUDIO_PREFIX) -> trimmed.removePrefix(ASSET_AUDIO_PREFIX)
            else -> trimmed
        }
    }

    fun sanitizeFileId(rawId: String): String {
        val normalized = normalizeMediaId(rawId)
        val cleaned = normalized.replace(SAFE_ID_REGEX, "_").trim('.')
        require(cleaned.isNotEmpty() && cleaned != "." && cleaned != "..") {
            "Invalid media identifier"
        }
        return cleaned.take(180)
    }

    fun resolveConfinedFile(baseDir: File, filename: String): File {
        if (!baseDir.exists()) baseDir.mkdirs()
        val safeName = sanitizeFileId(filename)
        val candidate = File(baseDir, safeName)
        val baseCanonical = baseDir.canonicalPath
        val targetCanonical = candidate.canonicalPath
        if (!targetCanonical.startsWith(baseCanonical + File.separator)) {
            throw SecurityException("Path traversal blocked for media file: $filename")
        }
        return candidate
    }

    fun resolveExistingImageFile(id: String): File? {
        return try {
            val safeName = sanitizeFileId(id)
            val direct = resolveConfinedFile(mediaImagesDir, safeName)
            if (direct.exists() && direct.isFile && direct.length() > 0L) return direct
            for (ext in IMAGE_EXTENSIONS) {
                val withExt = resolveConfinedFile(mediaImagesDir, "$safeName.$ext")
                if (withExt.exists() && withExt.isFile && withExt.length() > 0L) return withExt
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveExistingAudioFile(id: String, preferredFormat: String? = null): File? {
        return try {
            val safeName = sanitizeFileId(id)
            val direct = resolveConfinedFile(mediaAudioDir, safeName)
            if (direct.exists() && direct.isFile && direct.length() > 0L) return direct

            if (!preferredFormat.isNullOrBlank()) {
                val cleanFmt = preferredFormat.lowercase().trim().removePrefix(".")
                val byFmt = resolveConfinedFile(mediaAudioDir, "$safeName.$cleanFmt")
                if (byFmt.exists() && byFmt.isFile && byFmt.length() > 0L) return byFmt
            }
            for (ext in AUDIO_EXTENSIONS) {
                val withExt = resolveConfinedFile(mediaAudioDir, "$safeName.$ext")
                if (withExt.exists() && withExt.isFile && withExt.length() > 0L) return withExt
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveExistingDocumentFile(id: String): File? {
        return try {
            val safeName = sanitizeFileId(id)
            val direct = resolveConfinedFile(mediaDocsDir, safeName)
            if (direct.exists() && direct.isFile && direct.length() > 0L) return direct
            for (ext in DOCUMENT_EXTENSIONS) {
                val withExt = resolveConfinedFile(mediaDocsDir, "$safeName.$ext")
                if (withExt.exists() && withExt.isFile && withExt.length() > 0L) return withExt
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun importDocumentFromUri(uri: Uri): ImportedDocumentFile? {
        return try {
            val resolver = appContext.contentResolver
            var displayName = ""
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    displayName = c.getString(0) ?: ""
                }
            }
            val mime = resolver.getType(uri)?.lowercase()?.trim()
                ?: MimeTypeMap.getFileExtensionFromUrl(displayName)
                    ?.lowercase()
                    ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                ?: "application/octet-stream"

            val rawExt = displayName.substringAfterLast('.', "").lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                ?: when {
                    mime == "application/pdf" -> "pdf"
                    mime == "image/png" -> "png"
                    mime == "image/webp" -> "webp"
                    mime.startsWith("image/") -> "jpg"
                    else -> "bin"
                }

            val fileId = "${DOCUMENT_ID_PREFIX}${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.$rawExt"
            val destFile = resolveConfinedFile(mediaDocsDir, fileId)
            var totalBytes = 0L
            val input = resolver.openInputStream(uri) ?: return null
            BufferedInputStream(input, 32_768).use { bin ->
                BufferedOutputStream(FileOutputStream(destFile), 32_768).use { bout ->
                    val buf = ByteArray(32_768)
                    while (true) {
                        val read = bin.read(buf)
                        if (read == -1) break
                        bout.write(buf, 0, read)
                        totalBytes += read
                    }
                    bout.flush()
                }
            }
            if (totalBytes <= 0L) {
                destFile.delete()
                return null
            }
            ImportedDocumentFile(
                fileId = fileId,
                fileName = displayName.ifBlank { "Document.$rawExt" },
                mimeType = mime,
                sizeBytes = totalBytes
            )
        } catch (_: Exception) {
            null
        }
    }

    fun hasLegacyPrimaryLogs(): Boolean = loadLogs(VaultMode.PRIMARY).isNotEmpty()

    fun getLegacyPrimaryLogsCount(): Int = loadLogs(VaultMode.PRIMARY).size

    /**
     * Safely merges legacy Real Vault (`PRIMARY`) diaries into Secret Writing (`DECOY`)
     * without overwriting newer entries or losing any media references.
     */
    fun migrateLegacyPrimaryLogsToSecretVault(removeFromPrimary: Boolean = true): Int {
        val legacyLogs = loadLogs(VaultMode.PRIMARY)
        if (legacyLogs.isEmpty()) return 0
        val decoyLogs = loadLogs(VaultMode.DECOY).toMutableList()
        val existingIds = decoyLogs.mapTo(HashSet()) { it.id }
        var addedCount = 0
        for (log in legacyLogs) {
            if (log.id !in existingIds) {
                decoyLogs.add(log)
                existingIds.add(log.id)
                addedCount++
            }
        }
        val sorted = decoyLogs.sortedWith(
            compareByDescending<DiaryLog> { it.pinned }.thenByDescending { it.updatedAt }
        )
        if (saveLogs(VaultMode.DECOY, sorted) && removeFromPrimary) {
            saveLogs(VaultMode.PRIMARY, emptyList())
        }
        return addedCount
    }

    // ==================== Diary Logs ====================

    fun hasEncryptedLogsFile(mode: VaultMode): Boolean {
        val f = File(vaultDir(mode), FILE_LOGS)
        return f.exists() && f.isFile && f.length() > 0L
    }

    fun loadLogs(mode: VaultMode): List<DiaryLog> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_LOGS)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(DiaryLog.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLogs(mode: VaultMode, logs: List<DiaryLog>): Boolean {
        val arr = JSONArray().apply { logs.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_LOGS), arr.toString())
    }

    fun upsertLog(mode: VaultMode, log: DiaryLog): List<DiaryLog> {
        val current = loadLogs(mode).toMutableList()
        val idx = current.indexOfFirst { it.id == log.id }
        if (idx >= 0) {
            current[idx] = log
        } else {
            current.add(0, log)
        }
        saveLogs(mode, current)
        return current
    }

    fun deleteLog(mode: VaultMode, logId: String, pruneUnreferencedMedia: Boolean = true): List<DiaryLog> {
        val current = loadLogs(mode)
        val removed = current.firstOrNull { it.id == logId }
        val remaining = current.filterNot { it.id == logId }
        saveLogs(mode, remaining)
        if (pruneUnreferencedMedia && removed != null) {
            cleanupUnreferencedMediaForLog(removed, otherMode = if (mode == VaultMode.PRIMARY) VaultMode.DECOY else VaultMode.PRIMARY, sameModeLogs = remaining)
        }
        return remaining
    }

    private fun collectReferencedMediaIds(logs: List<DiaryLog>): Set<String> {
        val refs = HashSet<String>()
        for (log in logs) {
            listOfNotNull(log.pfpDataUrl, log.canvasBgDataUrl).forEach {
                if (!it.startsWith("data:") && !it.startsWith("blob:") && !it.startsWith("http")) {
                    refs.add(normalizeMediaId(it))
                }
            }
            log.canvasImages.forEach { img ->
                if (!img.dataUrl.startsWith("data:") && !img.dataUrl.startsWith("blob:") && !img.dataUrl.startsWith("http")) {
                    refs.add(normalizeMediaId(img.dataUrl))
                }
            }
            log.audioAttachments.forEach { aud ->
                if (aud.id.isNotBlank()) refs.add(aud.id)
                val uriRef = aud.audioUri?.takeIf { it.isNotBlank() }?.let { normalizeMediaId(it) }
                if (!uriRef.isNullOrBlank()) refs.add(uriRef)
            }
        }
        return refs
    }

    private fun cleanupUnreferencedMediaForLog(
        removed: DiaryLog,
        otherMode: VaultMode,
        sameModeLogs: List<DiaryLog>
    ) {
        val stillReferenced = collectReferencedMediaIds(sameModeLogs) +
            collectReferencedMediaIds(loadLogs(otherMode))

        val candidateImages = buildList {
            removed.pfpDataUrl?.let { add(it) }
            removed.canvasBgDataUrl?.let { add(it) }
            removed.canvasImages.forEach { add(it.dataUrl) }
        }
        for (ref in candidateImages) {
            if (ref.startsWith("data:") || ref.startsWith("blob:") || ref.startsWith("http")) continue
            val clean = normalizeMediaId(ref)
            if (clean !in stillReferenced) {
                resolveExistingImageFile(clean)?.delete()
                try {
                    val prev = resolveConfinedFile(previewCacheDir, clean)
                    if (prev.exists()) prev.delete()
                } catch (_: Exception) {}
            }
        }
        for (aud in removed.audioAttachments) {
            val uriRef = aud.audioUri?.takeIf { it.isNotBlank() }?.let { normalizeMediaId(it) }
            if (aud.id.isNotBlank() && aud.id !in stillReferenced) {
                resolveExistingAudioFile(aud.id, aud.format)?.delete()
            }
            if (!uriRef.isNullOrBlank() && uriRef !in stillReferenced) {
                resolveExistingAudioFile(uriRef, aud.format)?.delete()
            }
        }
    }

    // ==================== Custom Fonts ====================

    fun loadCustomFonts(mode: VaultMode): List<CustomFontItem> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_CUSTOM_FONTS)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(CustomFontItem.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomFonts(mode: VaultMode, fonts: List<CustomFontItem>): Boolean {
        val arr = JSONArray().apply { fonts.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_CUSTOM_FONTS), arr.toString())
    }

    // ==================== Mic Settings ====================

    fun loadMicSettings(mode: VaultMode): MicRecordingSettings {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_MIC_SETTINGS)) ?: return MicRecordingSettings()
        return try {
            MicRecordingSettings.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            MicRecordingSettings()
        }
    }

    fun saveMicSettings(mode: VaultMode, settings: MicRecordingSettings): Boolean {
        return writeEncryptedText(File(vaultDir(mode), FILE_MIC_SETTINGS), settings.toJson().toString())
    }

    // ==================== Quick Notes ====================

    fun loadQuickNotes(mode: VaultMode): List<QuickNote> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_QUICK_NOTES)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(QuickNote.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveQuickNotes(mode: VaultMode, notes: List<QuickNote>): Boolean {
        val arr = JSONArray().apply { notes.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_QUICK_NOTES), arr.toString())
    }

    // ==================== Saved Passwords ====================

    fun loadSavedPasswords(mode: VaultMode): List<SavedPasswordItem> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_SAVED_PASSWORDS)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(SavedPasswordItem.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSavedPasswords(mode: VaultMode, items: List<SavedPasswordItem>): Boolean {
        val arr = JSONArray().apply { items.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_SAVED_PASSWORDS), arr.toString())
    }

    // ==================== Calendar Recurring Trackers (v3.0) ====================

    fun loadTrackers(mode: VaultMode): List<RecurringTracker> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_TRACKERS)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(RecurringTracker.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTrackers(mode: VaultMode, trackers: List<RecurringTracker>): Boolean {
        val arr = JSONArray().apply { trackers.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_TRACKERS), arr.toString())
    }

    fun upsertTracker(mode: VaultMode, tracker: RecurringTracker): List<RecurringTracker> {
        val current = loadTrackers(mode).toMutableList()
        val idx = current.indexOfFirst { it.id == tracker.id }
        if (idx >= 0) current[idx] = tracker else current.add(0, tracker)
        saveTrackers(mode, current)
        return current
    }

    fun setTrackerDayEntry(
        mode: VaultMode,
        trackerId: String,
        dateKey: String,
        entry: TrackerDayEntry?
    ): List<RecurringTracker> {
        val current = loadTrackers(mode).toMutableList()
        val idx = current.indexOfFirst { it.id == trackerId }
        if (idx < 0) return current
        val target = current[idx]
        val nextEntries = target.entries.toMutableMap()
        if (entry == null) {
            nextEntries.remove(dateKey)
        } else {
            nextEntries[dateKey] = entry
        }
        current[idx] = target.copy(
            entries = nextEntries,
            updatedAt = System.currentTimeMillis()
        )
        saveTrackers(mode, current)
        return current
    }

    fun deleteTracker(mode: VaultMode, trackerId: String): List<RecurringTracker> {
        val remaining = loadTrackers(mode).filterNot { it.id == trackerId }
        saveTrackers(mode, remaining)
        return remaining
    }

    // ==================== Standalone Reminders (v3.0) ====================

    fun loadReminders(mode: VaultMode): List<StandaloneReminder> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_REMINDERS)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(StandaloneReminder.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveReminders(mode: VaultMode, reminders: List<StandaloneReminder>): Boolean {
        val arr = JSONArray().apply { reminders.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_REMINDERS), arr.toString())
    }

    fun upsertReminder(mode: VaultMode, reminder: StandaloneReminder): List<StandaloneReminder> {
        val current = loadReminders(mode).toMutableList()
        val idx = current.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) current[idx] = reminder else current.add(0, reminder)
        val sorted = current.sortedWith(
            compareBy<StandaloneReminder> { it.isCompleted }.thenBy { it.triggerAtMs }
        )
        saveReminders(mode, sorted)
        return sorted
    }

    fun deleteReminder(mode: VaultMode, reminderId: String): List<StandaloneReminder> {
        val remaining = loadReminders(mode).filterNot { it.id == reminderId }
        saveReminders(mode, remaining)
        return remaining
    }

    // ==================== Personal Documents Vault (v3.0) ====================

    fun loadDocuments(mode: VaultMode): List<PersonalDocument> {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_DOCUMENTS)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(PersonalDocument.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDocuments(mode: VaultMode, documents: List<PersonalDocument>): Boolean {
        val arr = JSONArray().apply { documents.forEach { put(it.toJson()) } }
        return writeEncryptedText(File(vaultDir(mode), FILE_DOCUMENTS), arr.toString())
    }

    fun upsertDocument(mode: VaultMode, document: PersonalDocument): List<PersonalDocument> {
        val current = loadDocuments(mode).toMutableList()
        val idx = current.indexOfFirst { it.id == document.id }
        if (idx >= 0) current[idx] = document else current.add(0, document)
        val sorted = current.sortedWith(
            compareByDescending<PersonalDocument> { it.pinned }.thenByDescending { it.updatedAt }
        )
        saveDocuments(mode, sorted)
        return sorted
    }

    fun deleteDocument(mode: VaultMode, documentId: String, pruneFile: Boolean = true): List<PersonalDocument> {
        val current = loadDocuments(mode)
        val removed = current.firstOrNull { it.id == documentId }
        val remaining = current.filterNot { it.id == documentId }
        saveDocuments(mode, remaining)
        val removedFileId = removed?.fileId
        if (pruneFile && !removedFileId.isNullOrBlank()) {
            val otherMode = if (mode == VaultMode.PRIMARY) VaultMode.DECOY else VaultMode.PRIMARY
            val stillUsed = remaining.any { it.fileId == removedFileId } ||
                loadDocuments(otherMode).any { it.fileId == removedFileId }
            if (!stillUsed) {
                resolveExistingDocumentFile(removedFileId)?.delete()
            }
        }
        return remaining
    }

    // ==================== Saved Word Analyses (JSON preserved 1:1) ====================

    fun loadSavedWordAnalysesJson(mode: VaultMode): String {
        return readEncryptedText(File(vaultDir(mode), FILE_WORD_ANALYSES)) ?: "[]"
    }

    fun saveSavedWordAnalysesJson(mode: VaultMode, jsonArrayStr: String): Boolean {
        return writeEncryptedText(File(vaultDir(mode), FILE_WORD_ANALYSES), jsonArrayStr)
    }

    // ==================== Vault Backup Metadata & Encryption Key Hash ====================

    fun loadBackupMetadata(mode: VaultMode): EncryptedVaultMetadata {
        val cfg = VaultConfig.forMode(mode)
        val defaultPath = "SAF/${cfg.safFolderName}/${cfg.dbFilename}"
        val raw = readEncryptedText(File(vaultDir(mode), FILE_BACKUP_META))
            ?: return EncryptedVaultMetadata(
                exists = false,
                lastBackupAt = null,
                entryCount = 0,
                vaultPath = defaultPath,
                sizeBytes = 0L,
                keyHintHash = getEncryptionKeyHintHash(mode)
            )
        return try {
            val obj = JSONObject(raw)
            val failedArr = obj.optJSONArray("failedMediaIds")
            val failedIds = buildList {
                if (failedArr != null) {
                    for (i in 0 until failedArr.length()) {
                        failedArr.optString(i).takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
            EncryptedVaultMetadata(
                exists = obj.optBoolean("exists", false),
                lastBackupAt = if (obj.has("lastBackupAt") && !obj.isNull("lastBackupAt")) obj.optLong("lastBackupAt") else null,
                entryCount = obj.optInt("entryCount", 0),
                vaultPath = obj.optString("vaultPath", defaultPath),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                keyHintHash = obj.optString("keyHintHash").takeIf { it.isNotEmpty() && !obj.isNull("keyHintHash") }
                    ?: getEncryptionKeyHintHash(mode),
                failedMediaCount = obj.optInt("failedMediaCount", 0),
                failedMediaIds = failedIds
            )
        } catch (_: Exception) {
            EncryptedVaultMetadata(
                exists = false,
                lastBackupAt = null,
                entryCount = 0,
                vaultPath = defaultPath,
                sizeBytes = 0L,
                keyHintHash = getEncryptionKeyHintHash(mode)
            )
        }
    }

    fun saveBackupMetadata(mode: VaultMode, meta: EncryptedVaultMetadata): Boolean {
        val obj = JSONObject().apply {
            put("exists", meta.exists)
            put("lastBackupAt", meta.lastBackupAt ?: JSONObject.NULL)
            put("entryCount", meta.entryCount)
            put("vaultPath", meta.vaultPath)
            put("sizeBytes", meta.sizeBytes)
            put("keyHintHash", meta.keyHintHash ?: JSONObject.NULL)
            put("failedMediaCount", meta.failedMediaCount)
            put("failedMediaIds", JSONArray().apply { meta.failedMediaIds.forEach { put(it) } })
        }
        return writeEncryptedText(File(vaultDir(mode), FILE_BACKUP_META), obj.toString())
    }

    fun getEncryptionKeyHintHash(mode: VaultMode): String? {
        val raw = readEncryptedText(File(vaultDir(mode), FILE_KEY_HASH))?.trim()
        return raw?.takeIf { it.isNotEmpty() }
    }

  fun setEncryptionKeyPassphrase(mode: VaultMode, passphrase: String): String {
        val hash = hashPassphrase(passphrase.trim(), mode)
        writeEncryptedText(File(vaultDir(mode), FILE_KEY_HASH), hash)
        return hash
    }

    fun restorePreHashedEncryptionKeyHint(mode: VaultMode, existingHash: String) {
        val clean = existingHash.trim()
        if (clean.isNotEmpty()) {
            writeEncryptedText(File(vaultDir(mode), FILE_KEY_HASH), clean)
        }
    }

    fun verifyEncryptionKeyPassphrase(mode: VaultMode, passphrase: String): Boolean {
        val storedHash = getEncryptionKeyHintHash(mode) ?: return false
        val candidate = hashPassphrase(passphrase.trim(), mode)
        return MessageDigest.isEqual(
            storedHash.toByteArray(StandardCharsets.UTF_8),
            candidate.toByteArray(StandardCharsets.UTF_8)
        )
    }

    // ==================== App Passcode & Covert Decoy Passcode ====================

    private fun securityConfigFile(): File = File(appContext.filesDir, "$VAULTS_ROOT_DIR/$FILE_SECURITY")

    fun hasSecurityConfigFile(): Boolean {
        val f = securityConfigFile()
        return f.exists() && f.isFile && f.length() > 0L
    }

    /**
     * Fast startup hint backed by SharedPreferences + security file existence.
     * Returns false immediately when no passcode has ever been written.
     */
    fun isPasscodeProtectionFastHint(): Boolean {
        if (!hasSecurityConfigFile()) return false
        return if (safPrefs.contains(KEY_PASSCODE_PROTECTED)) {
            safPrefs.getBoolean(KEY_PASSCODE_PROTECTED, false)
        } else {
            isAppPasscodeProtectionEnabled()
        }
    }

    private fun readSecurityJson(): JSONObject {
        synchronized(securityLock) {
            cachedSecurityJson?.let { cached ->
                return JSONObject(cached.toString())
            }
            val file = securityConfigFile()
            if (!file.exists() || !file.isFile || file.length() == 0L) {
                val empty = JSONObject()
                cachedSecurityJson = empty
                return JSONObject()
            }
            val raw = readEncryptedText(file)
            val parsed = try {
                if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
            } catch (_: Exception) {
                JSONObject()
            }
            cachedSecurityJson = parsed
            return JSONObject(parsed.toString())
        }
    }

    private fun writeSecurityJson(obj: JSONObject): Boolean {
        synchronized(securityLock) {
            val root = File(appContext.filesDir, VAULTS_ROOT_DIR).apply { if (!exists()) mkdirs() }
            val ok = writeEncryptedText(File(root, FILE_SECURITY), obj.toString())
            cachedSecurityJson = JSONObject(obj.toString())
            val hasAnyPin = obj.optString("primaryPinHash").isNotEmpty() || obj.optString("decoyPinHash").isNotEmpty()
            safPrefs.edit().putBoolean(KEY_PASSCODE_PROTECTED, hasAnyPin).apply()
            return ok
        }
    }

    fun isAppPasscodeProtectionEnabled(): Boolean {
        val obj = readSecurityJson()
        return obj.optString("primaryPinHash").isNotEmpty() || obj.optString("decoyPinHash").isNotEmpty()
    }

    fun hasPrimaryPasscode(): Boolean = readSecurityJson().optString("primaryPinHash").isNotEmpty()

    fun hasDecoyPasscode(): Boolean = readSecurityJson().optString("decoyPinHash").isNotEmpty()

    fun setPrimaryPasscode(pin: String) {
        val clean = pin.trim()
        require(clean.length in 4..8) { "Passcode must be 4-8 digits" }
        val obj = readSecurityJson()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hashB64 = Base64.encodeToString(
            pbkdf2Hmac(clean.toByteArray(StandardCharsets.UTF_8), salt, PIN_KDF_ITERATIONS, 32, "HmacSHA256"),
            Base64.NO_WRAP
        )
        obj.put("primaryPinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
        obj.put("primaryPinHash", hashB64)
        writeSecurityJson(obj)
    }

    fun setDecoyPasscode(pin: String) {
        val clean = pin.trim()
        require(clean.length in 4..8) { "Decoy passcode must be 4-8 digits" }
        val obj = readSecurityJson()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hashB64 = Base64.encodeToString(
            pbkdf2Hmac(clean.toByteArray(StandardCharsets.UTF_8), salt, PIN_KDF_ITERATIONS, 32, "HmacSHA256"),
            Base64.NO_WRAP
        )
        obj.put("decoyPinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
        obj.put("decoyPinHash", hashB64)
        writeSecurityJson(obj)
    }

    fun clearPrimaryPasscode() {
        val obj = readSecurityJson()
        obj.remove("primaryPinSalt")
        obj.remove("primaryPinHash")
        writeSecurityJson(obj)
    }

    fun clearDecoyPasscode() {
        val obj = readSecurityJson()
        obj.remove("decoyPinSalt")
        obj.remove("decoyPinHash")
        writeSecurityJson(obj)
    }

    fun verifyPrimaryPasscode(pin: String): Boolean {
        val obj = readSecurityJson()
        val saltB64 = obj.optString("primaryPinSalt")
        val hashB64 = obj.optString("primaryPinHash")
        if (saltB64.isEmpty() || hashB64.isEmpty()) return false
        return verifyPinHash(pin.trim(), saltB64, hashB64)
    }

    fun verifyDecoyPasscode(pin: String): Boolean {
        val obj = readSecurityJson()
        val saltB64 = obj.optString("decoyPinSalt")
        val hashB64 = obj.optString("decoyPinHash")
        if (saltB64.isEmpty() || hashB64.isEmpty()) return false
        return verifyPinHash(pin.trim(), saltB64, hashB64)
    }

    /**
     * Evaluates an entered PIN and routes cleanly to Primary or Covert Decoy Vault.
     * Returns null if PIN does not match any configured passcode.
     */
    fun evaluatePasscode(pin: String): VaultMode? {
        if (verifyPrimaryPasscode(pin)) return VaultMode.PRIMARY
        if (verifyDecoyPasscode(pin)) return VaultMode.DECOY
        return null
    }

    private fun verifyPinHash(pin: String, saltB64: String, expectedHashB64: String): Boolean {
        return try {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(expectedHashB64, Base64.NO_WRAP)
            val actual = pbkdf2Hmac(pin.toByteArray(StandardCharsets.UTF_8), salt, PIN_KDF_ITERATIONS, 32, "HmacSHA256")
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    // ==================== Progressive Brute-Force Cooldown Manager ====================

    fun getPasscodeGuardState(): PasscodeGuardState {
        val obj = readSecurityJson()
        return PasscodeGuardState(
            failedAttempts = obj.optInt("guardFailedAttempts", 0),
            cooldownUntilMs = obj.optLong("guardCooldownUntilMs", 0L)
        )
    }

    fun getRemainingPasscodeCooldownSeconds(): Int {
        val diff = getPasscodeGuardState().cooldownUntilMs - System.currentTimeMillis()
        return if (diff > 0L) kotlin.math.ceil(diff / 1000.0).toInt() else 0
    }

    fun recordPasscodeFailure(): PasscodeFailureResult {
        val obj = readSecurityJson()
        val nextAttempts = obj.optInt("guardFailedAttempts", 0) + 1
        val cooldownSeconds = when {
            nextAttempts == 5 -> 30
            nextAttempts > 5 -> 30 * nextAttempts
            else -> 0
        }
        val cooldownUntilMs = if (cooldownSeconds > 0) {
            System.currentTimeMillis() + cooldownSeconds * 1000L
        } else {
            0L
        }
        obj.put("guardFailedAttempts", nextAttempts)
        obj.put("guardCooldownUntilMs", cooldownUntilMs)
        writeSecurityJson(obj)
        return PasscodeFailureResult(
            failedAttempts = nextAttempts,
            cooldownSeconds = cooldownSeconds,
            cooldownUntilMs = cooldownUntilMs
        )
    }

    fun resetPasscodeFailures() {
        val obj = readSecurityJson()
        if (obj.has("guardFailedAttempts") || obj.has("guardCooldownUntilMs")) {
            obj.remove("guardFailedAttempts")
            obj.remove("guardCooldownUntilMs")
            writeSecurityJson(obj)
        }
    }

    // ==================== Preferences (Biometrics, Theme, Fullscreen, SAF URIs) ====================

    fun isBiometricEnabled(): Boolean = safPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        safPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isDarkTheme(): Boolean = safPrefs.getBoolean(KEY_DARK_THEME, false)

    fun setDarkTheme(isDark: Boolean) {
        safPrefs.edit()
            .putBoolean(KEY_DARK_THEME, isDark)
            .putBoolean(KEY_DARK_ICONS_ON_LIGHT, !isDark)
            .apply()
    }

    fun isFullScreenEnabled(): Boolean = safPrefs.getBoolean(KEY_FULLSCREEN_ENABLED, false)

    fun setFullScreenEnabled(enabled: Boolean) {
        safPrefs.edit().putBoolean(KEY_FULLSCREEN_ENABLED, enabled).apply()
    }

    fun getSafTreeUri(mode: VaultMode): Uri? {
        val primaryKey = KEY_SAF_TREE_URI
        val decoyKey = KEY_SAF_DECOY_TREE_URI
        val uriStr = if (mode == VaultMode.DECOY) {
            safPrefs.getString(decoyKey, null) ?: safPrefs.getString(primaryKey, null)
        } else {
            safPrefs.getString(primaryKey, null)
        }
        return uriStr?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun persistSafTreeUri(mode: VaultMode, treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) {
            // Permission may already be persisted
        }
        val key = if (mode == VaultMode.DECOY) KEY_SAF_DECOY_TREE_URI else KEY_SAF_TREE_URI
        safPrefs.edit().putString(key, treeUri.toString()).apply()
    }

    fun getSafFolderDisplayName(mode: VaultMode): String {
        val treeUri = getSafTreeUri(mode) ?: return ""
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val colon = docId.indexOf(':')
            if (colon >= 0 && colon + 1 < docId.length) {
                val tail = docId.substring(colon + 1).trim('/')
                if (tail.isNotEmpty()) tail else "Internal Storage"
            } else {
                docId
            }
        } catch (_: Exception) {
            treeUri.lastPathSegment ?: "Selected Folder"
        }
    }

    // ==================== Clear All Data ====================

    fun clearAllVaultData() {
        synchronized(securityLock) {
            cachedSecurityJson = null
        }
        File(appContext.filesDir, VAULTS_ROOT_DIR).deleteRecursively()
        mediaImagesDir.deleteRecursively()
        mediaAudioDir.deleteRecursively()
        mediaDocsDir.deleteRecursively()
        previewCacheDir.deleteRecursively()
        File(appContext.filesDir, "likkho_internal_data").deleteRecursively()
        safPrefs.edit().clear().apply()
        appContext.getSharedPreferences("likkho_recording_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ==================== At-Rest Encryption Helpers (AndroidKeyStore AES-GCM) ====================

    private fun getOrCreateMasterKey(): SecretKey {
        cachedMasterKey?.let { return it }
        synchronized(masterKeyLock) {
            cachedMasterKey?.let { return it }
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) {
                cachedMasterKey = existing
                return existing
            }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            val created = generator.generateKey()
            cachedMasterKey = created
            return created
        }
    }

    private fun writeEncryptedText(targetFile: File, plaintext: String): Boolean {
        return try {
            targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val payload = "v1:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"

            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.writeText(payload, StandardCharsets.UTF_8)
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.writeText(payload, StandardCharsets.UTF_8)
                tmpFile.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readEncryptedText(targetFile: File): String? {
        if (!targetFile.exists() || !targetFile.isFile) return null
        return try {
            val raw = targetFile.readText(StandardCharsets.UTF_8).trim()
            if (raw.isEmpty()) return null
            if (!raw.startsWith("v1:")) {
                return raw
            }
            val parts = raw.split(':', limit = 3)
            if (parts.size != 3) return null
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ct = Base64.decode(parts[2], Base64.NO_WRAP)
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "likkho_saf_prefs"
        private const val KEY_SAF_TREE_URI = "saf_base_tree_uri"
        private const val KEY_SAF_DECOY_TREE_URI = "saf_decoy_base_tree_uri"
        private const val KEY_PASSCODE_PROTECTED = "app_passcode_protection_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "native_biometric_unlock_enabled"
        private const val KEY_DARK_THEME = "native_dark_theme_enabled"
        private const val KEY_DARK_ICONS_ON_LIGHT = "saved_dark_icons_on_light"
        private const val KEY_FULLSCREEN_ENABLED = "saved_fullscreen_enabled"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "likkho_native_vault_master_v1"
        private const val VAULTS_ROOT_DIR = "likkho_vaults"
        private const val MEDIA_IMAGES_DIR = "likkho_media"
        private const val MEDIA_AUDIO_DIR = "likkho_audio"
        private const val MEDIA_DOCS_DIR = "likkho_docs"
        private const val PREVIEW_CACHE_DIR = "likkho_previews"
        const val DOCUMENT_ID_PREFIX = "lkdoc_"

        private const val FILE_LOGS = "logs.enc"
        private const val FILE_CUSTOM_FONTS = "custom_fonts.enc"
        private const val FILE_MIC_SETTINGS = "mic_settings.enc"
        private const val FILE_QUICK_NOTES = "quick_notes.enc"
        private const val FILE_SAVED_PASSWORDS = "saved_passwords.enc"
        private const val FILE_TRACKERS = "trackers.enc"
        private const val FILE_REMINDERS = "reminders.enc"
        private const val FILE_DOCUMENTS = "documents.enc"
        private const val FILE_WORD_ANALYSES = "word_analyses.enc"
        private const val FILE_BACKUP_META = "backup_meta.enc"
        private const val FILE_KEY_HASH = "encryption_key_hash.enc"
        private const val FILE_SECURITY = "app_security.enc"

        private val masterKeyLock = Any()
        @Volatile
        private var cachedMasterKey: SecretKey? = null

        private const val PIN_KDF_ITERATIONS = 50_000
        private const val ASSET_MEDIA_PREFIX = "https://appassets.androidplatform.net/likkho-media/"
        private const val ASSET_AUDIO_PREFIX = "https://appassets.androidplatform.net/likkho-audio/"
        private val SAFE_ID_REGEX = Regex("[^A-Za-z0-9._-]")
        private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "gif")
        private val AUDIO_EXTENSIONS = listOf("wav", "m4a", "mp3", "aac", "flac", "ogg", "webm")
        private val DOCUMENT_EXTENSIONS = listOf("pdf", "jpg", "jpeg", "png", "webp", "bin")

        /**
         * Exact 1:1 parity with `hashPassphrase(passphrase, vaultMode)` in `src/utils/cryptoVault.ts`.
         */
        fun hashPassphrase(passphrase: String, vaultMode: VaultMode = VaultMode.PRIMARY): String {
            val cfg = VaultConfig.forMode(vaultMode)
            val digest = MessageDigest.getInstance(cfg.kdfDigestName)
            val input = "${cfg.legacyEnvelopeContextSalt}HASH_SALT_$passphrase"
                .toByteArray(StandardCharsets.UTF_8)
            return Base64.encodeToString(digest.digest(input), Base64.NO_WRAP)
        }

        /**
         * RFC 2898 / PKCS#5 PBKDF2-HMAC using exact UTF-8 byte key material and `javax.crypto.Mac`.
         * Compatible across all Android versions from API 24+ (no API 26 PBKDF2WithHmacSHA256 restriction).
         */
        fun pbkdf2Hmac(
            passwordBytes: ByteArray,
            salt: ByteArray,
            iterations: Int,
            keyLengthBytes: Int,
            hmacAlgorithm: String
        ): ByteArray {
            val mac = Mac.getInstance(hmacAlgorithm)
            val safeKey = if (passwordBytes.isEmpty()) ByteArray(1) else passwordBytes
            mac.init(SecretKeySpec(safeKey, hmacAlgorithm))

            val hashLen = mac.macLength
            val blockCount = (keyLengthBytes + hashLen - 1) / hashLen
            val output = ByteArray(blockCount * hashLen)
            val intBlock = ByteArray(4)

            for (blockIndex in 1..blockCount) {
                intBlock[0] = ((blockIndex ushr 24) and 0xFF).toByte()
                intBlock[1] = ((blockIndex ushr 16) and 0xFF).toByte()
                intBlock[2] = ((blockIndex ushr 8) and 0xFF).toByte()
                intBlock[3] = (blockIndex and 0xFF).toByte()

                mac.reset()
                mac.update(salt)
                var u = mac.doFinal(intBlock)
                val t = u.copyOf()

                for (iter in 2..iterations) {
                    mac.reset()
                    u = mac.doFinal(u)
                    for (j in t.indices) {
                        t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                    }
                }
                System.arraycopy(t, 0, output, (blockIndex - 1) * hashLen, hashLen)
            }
            return if (output.size == keyLengthBytes) output else output.copyOf(keyLengthBytes)
        }
    }
}

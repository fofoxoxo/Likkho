package com.likkho.diary.nativeapp.crypto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Base64
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.CanvasAudioAttachment
import com.likkho.diary.nativeapp.model.CustomFontItem
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.EncryptedVaultMetadata
import com.likkho.diary.nativeapp.model.MicRecordingSettings
import com.likkho.diary.nativeapp.model.PersonalDocument
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.RecurringTracker
import com.likkho.diary.nativeapp.model.SavedPasswordItem
import com.likkho.diary.nativeapp.model.StandaloneReminder
import com.likkho.diary.nativeapp.model.VaultBackupBundle
import com.likkho.diary.nativeapp.model.VaultConfig
import com.likkho.diary.nativeapp.model.VaultMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class StreamBackupResult(
    val totalBytes: Long,
    val entryCount: Int,
    val failedMediaCount: Int,
    val failedMediaIds: List<String>,
    val vaultPath: String
)

private data class PendingMediaItem(
    val id: String,
    val isAudio: Boolean,
    val audioAttachment: CanvasAudioAttachment? = null,
    val isDocument: Boolean = false,
    val docMimeType: String? = null
)

private data class MultiChunkTracker(
    val totalChunks: Int,
    val mimeType: String,
    val receivedIndices: MutableSet<Int> = HashSet()
)

class InvalidEncryptionKeyException(message: String = "Incorrect Encryption Key.") : Exception(message)

class IncompleteBackupException(message: String) : Exception(message)

/**
 * Native Streaming Chunked Binary Encryption & Storage Engine (`LKSTRM02` / `LKSTRM01` + Legacy `WIKILOG_VAULT_V1`)
 *
 * Byte-for-byte compatible with `src/utils/chunkedVaultStream.ts` and `src/utils/cryptoVault.ts`:
 * - Streams 1.5 MB AES-256-GCM chunks directly to/from disk and Android SAF `Uri`s (~2-4 MB peak RAM).
 * - Preserves Primary (`PBKDF2-SHA256`, 100,000 iters, `real_vault.db`) and Covert Decoy
 *   (`PBKDF2-SHA512`, 210,000 iters, `decoy_vault.db`) domain separation.
 * - Verifies per-chunk IV (`baseIv[0..7] + uint32BE(counter)`), AAD (`recordType + chunkIndex + totalChunks + id`),
 *   128-bit GCM auth tag, and `0xFF` EOF record integrity.
 */
class StreamCryptoEngine(
    context: Context,
    private val repository: NativeVaultRepository
) {
    private val appContext: Context = context.applicationContext
    private val secureRandom = SecureRandom()

    // ==================== Key Derivation (1:1 WebCrypto Match) ====================

    private fun deriveAesKey(
        fullPassword: String,
        salt: ByteArray,
        iterations: Int,
        hashDigestName: String
    ): SecretKey {
        val isSha512 = hashDigestName.equals("SHA-512", ignoreCase = true)
        val rawKeyBytes: ByteArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && fullPassword.all { it.code < 128 }) {
            val kdfAlg = if (isSha512) "PBKDF2WithHmacSHA512" else "PBKDF2WithHmacSHA256"
            val chars = fullPassword.toCharArray()
            val spec = PBEKeySpec(chars, salt, iterations, 256)
            try {
                SecretKeyFactory.getInstance(kdfAlg).generateSecret(spec).encoded
            } catch (_: Exception) {
                NativeVaultRepository.pbkdf2Hmac(
                    fullPassword.toByteArray(StandardCharsets.UTF_8),
                    salt,
                    iterations,
                    32,
                    if (isSha512) "HmacSHA512" else "HmacSHA256"
                )
            } finally {
                spec.clearPassword()
                Arrays.fill(chars, '\u0000')
            }
        } else {
            NativeVaultRepository.pbkdf2Hmac(
                fullPassword.toByteArray(StandardCharsets.UTF_8),
                salt,
                iterations,
                32,
                if (isSha512) "HmacSHA512" else "HmacSHA256"
            )
        }
        return try {
            SecretKeySpec(rawKeyBytes, "AES")
        } finally {
            Arrays.fill(rawKeyBytes, 0.toByte())
        }
    }

    private fun deriveStreamAesKey(
        passphrase: String,
        salt: ByteArray,
        vaultMode: VaultMode,
        customIterations: Int? = null,
        customDigestName: String? = null
    ): SecretKey {
        val cfg = VaultConfig.forMode(vaultMode)
        val iterations = (customIterations ?: cfg.kdfIterations).coerceAtLeast(1)
        val digest = customDigestName ?: cfg.kdfDigestName
        return deriveAesKey("${cfg.streamKdfContextPrefix}$passphrase", salt, iterations, digest)
    }

    private fun deriveLegacyEnvelopeAesKey(
        passphrase: String,
        salt: ByteArray,
        vaultMode: VaultMode
    ): SecretKey {
        val cfg = VaultConfig.forMode(vaultMode)
        return deriveAesKey("${cfg.legacyEnvelopeContextSalt}$passphrase", salt, cfg.kdfIterations, cfg.kdfDigestName)
    }

    private fun deriveLegacyRawPrimaryAesKey(
        passphrase: String,
        salt: ByteArray
    ): SecretKey {
        return deriveAesKey(passphrase, salt, 100_000, "SHA-256")
    }

    // ==================== IV, AAD & Frame Helpers ====================

    private fun deriveChunkIv(baseIv: ByteArray, counter: Int): ByteArray {
        val iv = ByteArray(12)
        System.arraycopy(baseIv, 0, iv, 0, 8)
        iv[8] = ((counter ushr 24) and 0xFF).toByte()
        iv[9] = ((counter ushr 16) and 0xFF).toByte()
        iv[10] = ((counter ushr 8) and 0xFF).toByte()
        iv[11] = (counter and 0xFF).toByte()
        return iv
    }

    private fun buildChunkAad(
        recordType: Byte,
        chunkIndex: Int,
        totalChunks: Int,
        idBytes: ByteArray
    ): ByteArray {
        val buf = ByteBuffer.allocate(1 + 4 + 4 + idBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(recordType)
        buf.putInt(chunkIndex)
        buf.putInt(totalChunks)
        buf.put(idBytes)
        return buf.array()
    }

    private fun encryptGcmFrameData(
        aesKey: SecretKey,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        plaintextLength: Int = plaintext.size
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext, 0, plaintextLength)
    }

    private fun decryptGcmFrameData(
        aesKey: SecretKey,
        iv: ByteArray,
        aad: ByteArray?,
        cipherBytes: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        return try {
            cipher.doFinal(cipherBytes)
        } catch (badTag: AEADBadTagException) {
            throw InvalidEncryptionKeyException()
        }
    }

    private fun writeRecordFrame(
        out: OutputStream,
        recordType: Byte,
        id: String,
        chunkIndex: Int,
        totalChunks: Int,
        mimeType: String,
        iv: ByteArray,
        cipherData: ByteArray
    ): Long {
        val idBytes = id.toByteArray(StandardCharsets.UTF_8)
        val mimeBytes = mimeType.toByteArray(StandardCharsets.UTF_8)
        require(idBytes.size <= 0xFFFF) { "Record id too long" }
        require(mimeBytes.size <= 0xFFFF) { "MIME type too long" }

        val headerLen = 1 + 2 + idBytes.size + 4 + 4 + 2 + mimeBytes.size + 12 + 4
        val buf = ByteBuffer.allocate(headerLen).order(ByteOrder.BIG_ENDIAN)
        buf.put(recordType)
        buf.putShort(idBytes.size.toShort())
        buf.put(idBytes)
        buf.putInt(chunkIndex)
        buf.putInt(totalChunks)
        buf.putShort(mimeBytes.size.toShort())
        buf.put(mimeBytes)
        buf.put(iv, 0, 12)
        buf.putInt(cipherData.size)

        out.write(buf.array())
        out.write(cipherData)
        return headerLen.toLong() + cipherData.size.toLong()
    }

    // ==================== Streaming Backup ====================

    fun backupVaultToSafTree(
        bundle: VaultBackupBundle,
        passphrase: String,
        vaultMode: VaultMode,
        treeUri: Uri? = repository.getSafTreeUri(vaultMode),
        onProgress: ((currentItem: Int, totalItems: Int, bytesWritten: Long) -> Unit)? = null
    ): StreamBackupResult {
        require(treeUri != null) {
            "Backup folder is not selected. Please choose a backup folder first."
        }
        val cfg = VaultConfig.forMode(vaultMode)
        val resolver = appContext.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val backupDirDocId = getOrCreateChildDirectory(resolver, treeUri, rootDocId, cfg.safFolderName)
        ensureNomediaInSafFolder(resolver, treeUri, backupDirDocId)

        val tmpFilename = "${cfg.dbFilename}.tmp"
        findChildDocumentId(resolver, treeUri, backupDirDocId, tmpFilename)?.let { oldTmpId ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, oldTmpId))
            }
        }

        val backupDirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, backupDirDocId)
        val tmpUri = DocumentsContract.createDocument(
            resolver,
            backupDirUri,
            "application/octet-stream",
            tmpFilename
        ) ?: throw IOException("Unable to create temporary backup file in SAF folder.")

        val streamResult: StreamBackupResult
        try {
            val rawOut = resolver.openOutputStream(tmpUri, "wt")
                ?: throw IOException("Failed to open SAF output stream for backup.")
            streamResult = BufferedOutputStream(rawOut, IO_BUFFER_SIZE).use { bufferedOut ->
                backupVaultToOutputStream(
                    bundle = bundle,
                    passphrase = passphrase,
                    vaultMode = vaultMode,
                    outputStream = bufferedOut,
                    vaultPath = "SAF/${cfg.safFolderName}/${cfg.dbFilename}",
                    onProgress = onProgress
                )
            }
        } catch (err: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, tmpUri) }
            throw err
        }

        // Atomic swap: existing -> .bak, .tmp -> final, delete .bak
        val bakFilename = "${cfg.dbFilename}.bak"
        findChildDocumentId(resolver, treeUri, backupDirDocId, bakFilename)?.let { oldBakId ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, oldBakId))
            }
        }

        var bakUri: Uri? = null
        try {
            findChildDocumentId(resolver, treeUri, backupDirDocId, cfg.dbFilename)?.let { existingId ->
                val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
                bakUri = DocumentsContract.renameDocument(resolver, existingUri, bakFilename)
            }
            val finalUri = DocumentsContract.renameDocument(resolver, tmpUri, cfg.dbFilename)
                ?: throw IOException("Failed to rename temporary backup to ${cfg.dbFilename}.")
            if (bakUri != null) {
                runCatching { DocumentsContract.deleteDocument(resolver, bakUri!!) }
            }
            val keyHintHash = NativeVaultRepository.hashPassphrase(passphrase.trim(), vaultMode)
            repository.saveBackupMetadata(
                vaultMode,
                EncryptedVaultMetadata(
                    exists = true,
                    lastBackupAt = System.currentTimeMillis(),
                    entryCount = streamResult.entryCount,
                    vaultPath = streamResult.vaultPath,
                    sizeBytes = streamResult.totalBytes,
                    keyHintHash = keyHintHash,
                    failedMediaCount = streamResult.failedMediaCount,
                    failedMediaIds = streamResult.failedMediaIds
                )
            )
            // Keep unused variable lint clean on finalUri
            finalUri.toString()
            return streamResult
        } catch (swapErr: Throwable) {
            if (bakUri != null) {
                runCatching { DocumentsContract.renameDocument(resolver, bakUri!!, cfg.dbFilename) }
            }
            runCatching { DocumentsContract.deleteDocument(resolver, tmpUri) }
            throw swapErr
        }
    }

    fun backupVaultToOutputStream(
        bundle: VaultBackupBundle,
        passphrase: String,
        vaultMode: VaultMode,
        outputStream: OutputStream,
        vaultPath: String = "SAF/${VaultConfig.forMode(vaultMode).safFolderName}/${VaultConfig.forMode(vaultMode).dbFilename}",
        onProgress: ((currentItem: Int, totalItems: Int, bytesWritten: Long) -> Unit)? = null
    ): StreamBackupResult {
        val cfg = VaultConfig.forMode(vaultMode)
        val trimmedKey = passphrase.trim()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val baseIv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val aesKey = deriveStreamAesKey(trimmedKey, salt, vaultMode)

        var totalBytesWritten = 0L
        var chunkCounter = 0
        val failedMediaIds = ArrayList<String>()

        // 1. Write 44-byte LKSTRM02 header
        val header = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
        header.put(MAGIC_V2) // 0..7
        header.putShort(2.toShort()) // 8..9 version = 2
        header.put(salt) // 10..25
        header.putInt(cfg.kdfIterations) // 26..29
        header.put(if (cfg.kdfDigestName.equals("SHA-512", ignoreCase = true)) 2.toByte() else 1.toByte()) // 30
        header.put(0.toByte()) // 31 reserved
        header.put(baseIv) // 32..43
        val headerBytes = header.array()
        outputStream.write(headerBytes)
        totalBytesWritten += headerBytes.size

        // 2. Write Manifest Record (0x01)
        val effectiveSavedPasswords = bundle.savedPasswords.ifEmpty { repository.loadSavedPasswords(vaultMode) }
        val effectiveQuickNotes = bundle.quickNotes.ifEmpty { repository.loadQuickNotes(vaultMode) }
        val effectiveTrackers = bundle.trackers.ifEmpty { repository.loadTrackers(vaultMode) }
        val effectiveReminders = bundle.reminders.ifEmpty { repository.loadReminders(vaultMode) }
        val effectiveDocuments = bundle.documents.ifEmpty { repository.loadDocuments(vaultMode) }
        val manifestObj = JSONObject().apply {
            put("magic", "WIKILOG_STREAM_V1")
            put("dbTarget", cfg.dbFilename)
            put("exportedAt", System.currentTimeMillis())
            put("logs", JSONArray().apply { bundle.logs.forEach { put(it.toJson()) } })
            put("customFonts", JSONArray().apply { bundle.customFonts.forEach { put(it.toJson()) } })
            put("micSettings", bundle.micSettings.toJson())
            put("savedPasswords", JSONArray().apply { effectiveSavedPasswords.forEach { put(it.toJson()) } })
            put("quickNotes", JSONArray().apply { effectiveQuickNotes.forEach { put(it.toJson()) } })
            put("trackers", JSONArray().apply { effectiveTrackers.forEach { put(it.toJson()) } })
            put("reminders", JSONArray().apply { effectiveReminders.forEach { put(it.toJson()) } })
            put("documents", JSONArray().apply { effectiveDocuments.forEach { put(it.toJson()) } })
        }
        val manifestPlainBytes = manifestObj.toString().toByteArray(StandardCharsets.UTF_8)
        val manifestId = "manifest"
        val manifestIv = deriveChunkIv(baseIv, chunkCounter++)
        val manifestAad = buildChunkAad(RECORD_TYPE_MANIFEST, 0, 1, manifestId.toByteArray(StandardCharsets.UTF_8))
        val encryptedManifest = encryptGcmFrameData(aesKey, manifestIv, manifestAad, manifestPlainBytes)
        totalBytesWritten += writeRecordFrame(
            out = outputStream,
            recordType = RECORD_TYPE_MANIFEST,
            id = manifestId,
            chunkIndex = 0,
            totalChunks = 1,
            mimeType = "application/json",
            iv = manifestIv,
            cipherData = encryptedManifest
        )

        // 3. Collect unique media & personal document references
        val mediaQueue = ArrayList<PendingMediaItem>()
        val seenIds = HashSet<String>()
        for (log in bundle.logs) {
            val imageRefs = buildList {
                log.pfpDataUrl?.let { add(it) }
                log.canvasBgDataUrl?.let { add(it) }
                log.canvasImages.forEach { add(it.dataUrl) }
            }
            for (rawRef in imageRefs) {
                val clean = repository.normalizeMediaId(rawRef)
                if (
                    clean.isNotEmpty() &&
                    !clean.startsWith("data:") &&
                    !clean.startsWith("blob:") &&
                    !clean.startsWith("http") &&
                    seenIds.add(clean)
                ) {
                    mediaQueue.add(PendingMediaItem(id = clean, isAudio = false))
                }
            }
            for (att in log.audioAttachments) {
                if (att.id.isNotBlank() && seenIds.add(att.id)) {
                    mediaQueue.add(PendingMediaItem(id = att.id, isAudio = true, audioAttachment = att))
                }
            }
        }
        for (doc in effectiveDocuments) {
            val fileId = doc.fileId?.trim().orEmpty()
            if (fileId.isNotEmpty() && seenIds.add(fileId)) {
                mediaQueue.add(
                    PendingMediaItem(
                        id = fileId,
                        isAudio = false,
                        isDocument = true,
                        docMimeType = doc.mimeType
                    )
                )
            }
        }

        // 4. Stream each media file chunk-by-chunk (1.5 MB slices)
        val readBuf = ByteArray(DEFAULT_CHUNK_SIZE)
        mediaQueue.forEachIndexed { idx, item ->
            onProgress?.invoke(idx + 1, mediaQueue.size, totalBytesWritten)
            try {
                val resolved = openMediaInputAndSize(item)
                if (resolved == null || resolved.second <= 0L) {
                    failedMediaIds.add(item.id)
                    resolved?.first?.close()
                    return@forEachIndexed
                }
                val (inputStream, fileSizeBytes) = resolved
                val mimeType = resolveMimeTypeForMedia(item)
                val totalChunks = maxOf(1, ((fileSizeBytes + DEFAULT_CHUNK_SIZE - 1L) / DEFAULT_CHUNK_SIZE).toInt())
                val idBytes = item.id.toByteArray(StandardCharsets.UTF_8)

                inputStream.use { stream ->
                    for (cIdx in 0 until totalChunks) {
                        val bytesRead = readExactUpTo(stream, readBuf)
                        if (bytesRead <= 0) {
                            throw IOException("Unexpected EOF while reading media ${item.id} at chunk $cIdx")
                        }
                        val chunkIv = deriveChunkIv(baseIv, chunkCounter++)
                        val aad = buildChunkAad(RECORD_TYPE_MEDIA_CHUNK, cIdx, totalChunks, idBytes)
                        val encryptedChunk = encryptGcmFrameData(aesKey, chunkIv, aad, readBuf, bytesRead)
                        totalBytesWritten += writeRecordFrame(
                            out = outputStream,
                            recordType = RECORD_TYPE_MEDIA_CHUNK,
                            id = item.id,
                            chunkIndex = cIdx,
                            totalChunks = totalChunks,
                            mimeType = mimeType,
                            iv = chunkIv,
                            cipherData = encryptedChunk
                        )
                    }
                }
            } catch (err: Exception) {
                if (item.id !in failedMediaIds) {
                    failedMediaIds.add(item.id)
                }
            }
        }

        // 5. Write EOF Record (0xFF)
        val eofId = "eof"
        val eofIv = deriveChunkIv(baseIv, chunkCounter++)
        val eofAad = buildChunkAad(RECORD_TYPE_EOF, 0, 1, eofId.toByteArray(StandardCharsets.UTF_8))
        val encryptedEof = encryptGcmFrameData(aesKey, eofIv, eofAad, "EOF".toByteArray(StandardCharsets.UTF_8))
        totalBytesWritten += writeRecordFrame(
            out = outputStream,
            recordType = RECORD_TYPE_EOF,
            id = eofId,
            chunkIndex = 0,
            totalChunks = 1,
            mimeType = "text/plain",
            iv = eofIv,
            cipherData = encryptedEof
        )

        outputStream.flush()
        val computedEntryCount = if (bundle.logs.isNotEmpty()) {
            bundle.logs.size
        } else {
            effectiveTrackers.size +
                effectiveReminders.size +
                effectiveDocuments.size +
                effectiveQuickNotes.size +
                effectiveSavedPasswords.size
        }
        return StreamBackupResult(
            totalBytes = totalBytesWritten,
            entryCount = computedEntryCount,
            failedMediaCount = failedMediaIds.size,
            failedMediaIds = failedMediaIds,
            vaultPath = vaultPath
        )
    }

    private fun openMediaInputAndSize(item: PendingMediaItem): Pair<InputStream, Long>? {
        if (item.isDocument) {
            val docFile = repository.resolveExistingDocumentFile(item.id)
            if (docFile != null && docFile.exists() && docFile.length() > 0L) {
                return BufferedInputStream(FileInputStream(docFile), IO_BUFFER_SIZE) to docFile.length()
            }
            return null
        }
        if (!item.isAudio) {
            val imgFile = repository.resolveExistingImageFile(item.id)
            if (imgFile != null && imgFile.exists() && imgFile.length() > 0L) {
                return BufferedInputStream(FileInputStream(imgFile), IO_BUFFER_SIZE) to imgFile.length()
            }
            return null
        }

        val att = item.audioAttachment
        val audioFile = repository.resolveExistingAudioFile(item.id, att?.format)
            ?: att?.audioUri?.takeIf { it.isNotBlank() }?.let {
                repository.resolveExistingAudioFile(repository.normalizeMediaId(it), att.format)
            }
        if (audioFile != null && audioFile.exists() && audioFile.length() > 0L) {
            return BufferedInputStream(FileInputStream(audioFile), IO_BUFFER_SIZE) to audioFile.length()
        }

        val rawUri = att?.audioUri?.takeIf { it.isNotBlank() }
        if (rawUri != null) {
            if (rawUri.startsWith("/") || rawUri.startsWith("file://")) {
                val localPath = if (rawUri.startsWith("file://")) Uri.parse(rawUri).path else rawUri
                if (!localPath.isNullOrBlank()) {
                    val f = File(localPath)
                    if (f.exists() && f.isFile && f.length() > 0L) {
                        return BufferedInputStream(FileInputStream(f), IO_BUFFER_SIZE) to f.length()
                    }
                }
            } else if (rawUri.startsWith("content://")) {
                val uri = Uri.parse(rawUri)
                val size = queryUriSize(appContext.contentResolver, uri)
                val stream = appContext.contentResolver.openInputStream(uri)
                if (stream != null && size > 0L) {
                    return BufferedInputStream(stream, IO_BUFFER_SIZE) to size
                }
                stream?.close()
            }
        }
        return null
    }

    private fun resolveMimeTypeForMedia(item: PendingMediaItem): String {
        if (item.isDocument) {
            val explicit = item.docMimeType?.trim().orEmpty()
            if (explicit.isNotEmpty()) return explicit
            val file = repository.resolveExistingDocumentFile(item.id)
            return when (file?.extension?.lowercase()) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }
        }
        if (!item.isAudio) {
            val file = repository.resolveExistingImageFile(item.id)
            return when (file?.extension?.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
        }
        val fmt = (item.audioAttachment?.format ?: "").lowercase().trim()
        return when (fmt) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "webm" -> "audio/webm"
            else -> if (fmt.isNotEmpty()) "audio/$fmt" else "audio/wav"
        }
    }

    private fun readExactUpTo(stream: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    // ==================== Streaming Restore (LKSTRM02 / LKSTRM01 / Legacy JSON) ====================

    fun restoreFromSafTree(
        passphrase: String,
        vaultMode: VaultMode,
        treeUri: Uri? = repository.getSafTreeUri(vaultMode),
        writeMediaToDisk: Boolean = true,
        targetImageDir: File = repository.mediaImagesDir,
        targetAudioDir: File = repository.mediaAudioDir,
        targetDocsDir: File = repository.mediaDocsDir
    ): VaultBackupBundle {
        require(treeUri != null) { "Backup folder is not selected." }
        val fileUri = resolveSafBackupFileUri(appContext.contentResolver, treeUri, vaultMode)
            ?: throw IncompleteBackupException("No backup found to restore.")
        return restoreFromUri(
            uri = fileUri,
            passphrase = passphrase,
            vaultMode = vaultMode,
            writeMediaToDisk = writeMediaToDisk,
            targetImageDir = targetImageDir,
            targetAudioDir = targetAudioDir,
            targetDocsDir = targetDocsDir
        )
    }

    fun restoreFromUri(
        uri: Uri,
        passphrase: String,
        vaultMode: VaultMode,
        writeMediaToDisk: Boolean = true,
        targetImageDir: File = repository.mediaImagesDir,
        targetAudioDir: File = repository.mediaAudioDir,
        targetDocsDir: File = repository.mediaDocsDir
    ): VaultBackupBundle {
        val rawStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup file.")
        return rawStream.use { stream ->
            restoreFromInputStream(
                inputStream = stream,
                passphrase = passphrase,
                vaultMode = vaultMode,
                writeMediaToDisk = writeMediaToDisk,
                targetImageDir = targetImageDir,
                targetAudioDir = targetAudioDir,
                targetDocsDir = targetDocsDir
            )
        }
    }

    fun restoreFromInputStream(
        inputStream: InputStream,
        passphrase: String,
        vaultMode: VaultMode,
        writeMediaToDisk: Boolean = true,
        targetImageDir: File = repository.mediaImagesDir,
        targetAudioDir: File = repository.mediaAudioDir,
        targetDocsDir: File = repository.mediaDocsDir
    ): VaultBackupBundle {
        val buffered = BufferedInputStream(inputStream, IO_BUFFER_SIZE)
        val pushback = PushbackInputStream(buffered, 64)

        val probe = ByteArray(44)
        val probeLen = readExactUpTo(pushback, probe)
        if (probeLen <= 0) {
            throw IncompleteBackupException("Backup file is empty.")
        }
        pushback.unread(probe, 0, probeLen)

        if (isChunkedStreamMagic(probe, probeLen)) {
            return restoreChunkedStream(
                stream = pushback,
                passphrase = passphrase.trim(),
                vaultMode = vaultMode,
                writeMediaToDisk = writeMediaToDisk,
                targetImageDir = targetImageDir,
                targetAudioDir = targetAudioDir,
                targetDocsDir = targetDocsDir
            )
        }

        // Legacy single-envelope JSON format (`WIKILOG_VAULT_V1`)
        val allBytes = pushback.readBytes()
        val rawJson = String(allBytes, StandardCharsets.UTF_8)
        return decryptLegacyEnvelopeBundle(
            rawEnvelope = rawJson,
            passphrase = passphrase.trim(),
            vaultMode = vaultMode,
            writeMediaToDisk = writeMediaToDisk,
            targetImageDir = targetImageDir,
            targetAudioDir = targetAudioDir
        )
    }

    private fun restoreChunkedStream(
        stream: InputStream,
        passphrase: String,
        vaultMode: VaultMode,
        writeMediaToDisk: Boolean,
        targetImageDir: File,
        targetAudioDir: File,
        targetDocsDir: File
    ): VaultBackupBundle {
        val dataIn = DataInputStream(stream)
        val first42 = ByteArray(42)
        try {
            dataIn.readFully(first42)
        } catch (_: EOFException) {
            throw IncompleteBackupException("Backup file adhoori hai: truncated stream header.")
        }

        val version = ((first42[8].toInt() and 0xFF) shl 8) or (first42[9].toInt() and 0xFF)
        val isV1 = version == 1 || first42[7] == 0x31.toByte()

        val salt = first42.copyOfRange(10, 26)
        val iterationsRaw = ByteBuffer.wrap(first42, 26, 4).order(ByteOrder.BIG_ENDIAN).int
        val iterations = if (iterationsRaw > 0) iterationsRaw else 100_000
        val digestName: String

        if (isV1) {
            digestName = "SHA-256"
        } else {
            val extra2 = ByteArray(2)
            try {
                dataIn.readFully(extra2)
            } catch (_: EOFException) {
                throw IncompleteBackupException("Backup file adhoori hai: truncated v2 header.")
            }
            val hashId = first42[30].toInt() and 0xFF
            digestName = if (hashId == 2) "SHA-512" else "SHA-256"
        }

        val aesKey = deriveStreamAesKey(
            passphrase = passphrase,
            salt = salt,
            vaultMode = vaultMode,
            customIterations = iterations,
            customDigestName = digestName
        )

        val tempDir = File(appContext.cacheDir, "likkho_restore_tmp_${System.nanoTime()}").apply {
            if (!exists()) mkdirs()
        }
        val multiChunkTrackers = HashMap<String, MultiChunkTracker>()
        val savedAudioIds = HashSet<String>()

        var manifestObj: JSONObject? = null
        var eofSeen = false

        try {
            while (true) {
                val firstByte = dataIn.read()
                if (firstByte == -1) break
                val recordType = firstByte.toByte()

                val idLen = dataIn.readUnsignedShort()
                if (idLen > MAX_HEADER_STR_BYTES) {
                    throw IncompleteBackupException("Backup frame header corrupted: idLen=$idLen")
                }
                val idBytes = ByteArray(idLen).also { dataIn.readFully(it) }
                val id = String(idBytes, StandardCharsets.UTF_8)

                val chunkIndex = dataIn.readInt()
                val totalChunks = dataIn.readInt()

                val mimeLen = dataIn.readUnsignedShort()
                if (mimeLen > MAX_HEADER_STR_BYTES) {
                    throw IncompleteBackupException("Backup frame header corrupted: mimeLen=$mimeLen")
                }
                val mimeBytes = ByteArray(mimeLen).also { dataIn.readFully(it) }
                val mimeType = String(mimeBytes, StandardCharsets.UTF_8)

                val chunkIv = ByteArray(12).also { dataIn.readFully(it) }
                val cipherLen = dataIn.readInt()
                if (cipherLen < 16 || cipherLen > MAX_FRAME_CIPHER_BYTES) {
                    throw IncompleteBackupException("Backup frame corrupted: cipherLen=$cipherLen")
                }

                val cipherBytes = ByteArray(cipherLen).also { dataIn.readFully(it) }
                val aad = buildChunkAad(recordType, chunkIndex, totalChunks, idBytes)
                val decrypted = decryptGcmFrameData(aesKey, chunkIv, aad, cipherBytes)

                when (recordType) {
                    RECORD_TYPE_MANIFEST -> {
                        val jsonStr = String(decrypted, StandardCharsets.UTF_8)
                        manifestObj = JSONObject(jsonStr)
                    }

                    RECORD_TYPE_MEDIA_CHUNK -> {
                        if (writeMediaToDisk) {
                            val safeId = repository.sanitizeFileId(id)
                            val isDoc = safeId.startsWith(NativeVaultRepository.DOCUMENT_ID_PREFIX)
                            val isAudio = !isDoc && mimeType.startsWith("audio/")
                            val destDir = when {
                                isDoc -> targetDocsDir
                                isAudio -> targetAudioDir
                                else -> targetImageDir
                            }

                            if (totalChunks <= 1) {
                                val outFile = repository.resolveConfinedFile(destDir, safeId)
                                FileOutputStream(outFile).use { fos ->
                                    fos.write(decrypted)
                                    fos.flush()
                                }
                                if (isAudio) savedAudioIds.add(id)
                            } else {
                                if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                                    throw IncompleteBackupException(
                                        "Backup frame chunk index invalid: $chunkIndex/$totalChunks"
                                    )
                                }
                                val tracker = multiChunkTrackers.getOrPut(id) {
                                    MultiChunkTracker(totalChunks = totalChunks, mimeType = mimeType)
                                }
                                if (tracker.totalChunks != totalChunks) {
                                    throw IncompleteBackupException(
                                        "Backup frame chunk count mismatch for $id: ${tracker.totalChunks} vs $totalChunks"
                                    )
                                }
                                val chunkFile = repository.resolveConfinedFile(tempDir, "${safeId}_chunk_$chunkIndex")
                                FileOutputStream(chunkFile).use { fos ->
                                    fos.write(decrypted)
                                }
                                tracker.receivedIndices.add(chunkIndex)

                                if (tracker.receivedIndices.size == totalChunks) {
                                    val finalFile = repository.resolveConfinedFile(destDir, safeId)
                                    assembleChunksOnDisk(tempDir, safeId, totalChunks, finalFile)
                                    deleteTempChunks(tempDir, safeId, totalChunks)
                                    multiChunkTrackers.remove(id)
                                    if (!isDoc && tracker.mimeType.startsWith("audio/")) {
                                        savedAudioIds.add(id)
                                    }
                                }
                            }
                        }
                    }

                    RECORD_TYPE_EOF -> {
                        eofSeen = true
                        break
                    }
                }
            }

            if (!eofSeen) {
                throw IncompleteBackupException("Backup file adhoori hai: missing EOF record marker.")
            }

            val skippedMediaIds = multiChunkTrackers.keys.toList()
            val manifest = manifestObj
                ?: throw IncompleteBackupException("Backup file adhoori hai: missing manifest.")
            val logsArr = manifest.optJSONArray("logs")
                ?: throw IncompleteBackupException("Backup file adhoori hai: invalid or missing logs manifest.")

            val logs = parseAndCleanLogs(logsArr, savedAudioIds)
            val customFonts = parseCustomFonts(manifest.optJSONArray("customFonts"))
            val micSettings = MicRecordingSettings.fromJson(manifest.optJSONObject("micSettings"))
            val savedPasswords = parseSavedPasswords(manifest.optJSONArray("savedPasswords"))
            val quickNotes = parseQuickNotes(manifest.optJSONArray("quickNotes"))
            val trackers = parseTrackers(manifest.optJSONArray("trackers"))
            val reminders = parseReminders(manifest.optJSONArray("reminders"))
            val documents = parseDocuments(manifest.optJSONArray("documents"))

            return VaultBackupBundle(
                logs = logs,
                customFonts = customFonts,
                micSettings = micSettings,
                savedPasswords = savedPasswords,
                quickNotes = quickNotes,
                trackers = trackers,
                reminders = reminders,
                documents = documents,
                skippedMediaCount = skippedMediaIds.size,
                skippedMediaIds = skippedMediaIds
            )
        } catch (eofErr: EOFException) {
            throw IncompleteBackupException("Backup file adhoori hai: corrupted or truncated stream frame.")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun decryptLegacyEnvelopeBundle(
        rawEnvelope: String,
        passphrase: String,
        vaultMode: VaultMode,
        writeMediaToDisk: Boolean,
        targetImageDir: File,
        targetAudioDir: File
    ): VaultBackupBundle {
        val envelope = try {
            JSONObject(rawEnvelope)
        } catch (_: Exception) {
            throw IncompleteBackupException("Corrupted backup format.")
        }
        val saltStr = envelope.optString("salt")
        val ivStr = envelope.optString("iv")
        val ctStr = envelope.optString("ct")
        if (saltStr.isEmpty() || ivStr.isEmpty() || ctStr.isEmpty()) {
            throw IncompleteBackupException("Invalid backup file.")
        }

        val salt = Base64.decode(saltStr, Base64.DEFAULT)
        val iv = Base64.decode(ivStr, Base64.DEFAULT)
        val ctBytes = Base64.decode(ctStr, Base64.DEFAULT)
        val tagStr = envelope.optString("tag")
        val combined = if (tagStr.isNotEmpty()) {
            val tagBytes = Base64.decode(tagStr, Base64.DEFAULT)
            ByteArray(ctBytes.size + tagBytes.size).also {
                System.arraycopy(ctBytes, 0, it, 0, ctBytes.size)
                System.arraycopy(tagBytes, 0, it, ctBytes.size, tagBytes.size)
            }
        } else {
            ctBytes
        }

        val candidates = buildList {
            add(deriveLegacyEnvelopeAesKey(passphrase, salt, vaultMode))
            if (vaultMode == VaultMode.PRIMARY) {
                add(deriveLegacyRawPrimaryAesKey(passphrase, salt))
            }
        }

        for (candidateKey in candidates) {
            try {
                val plainBytes = decryptGcmFrameData(candidateKey, iv, null, combined)
                val parsed = JSONObject(String(plainBytes, StandardCharsets.UTF_8))
                if (parsed.optString("magic") != "WIKILOG_VAULT_V1") continue
                val logsArr = parsed.optJSONArray("logs") ?: continue

                val savedAudioIds = HashSet<String>()
                val mediaArr = parsed.optJSONArray("mediaArchive")
                if (writeMediaToDisk && mediaArr != null) {
                    for (i in 0 until mediaArr.length()) {
                        val item = mediaArr.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val type = item.optString("type")
                        val b64 = item.optString("base64")
                        if (id.isEmpty() || b64.isEmpty()) continue
                        try {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            val isAudio = type == "audio"
                            val outFile = repository.resolveConfinedFile(
                                if (isAudio) targetAudioDir else targetImageDir,
                                id
                            )
                            FileOutputStream(outFile).use { fos ->
                                fos.write(bytes)
                            }
                            if (isAudio) savedAudioIds.add(id)
                        } catch (_: Exception) {
                            // Continue extracting remaining items
                        }
                    }
                }

                return VaultBackupBundle(
                    logs = parseAndCleanLogs(logsArr, savedAudioIds),
                    customFonts = parseCustomFonts(parsed.optJSONArray("customFonts")),
                    micSettings = MicRecordingSettings.fromJson(parsed.optJSONObject("micSettings")),
                    savedPasswords = parseSavedPasswords(parsed.optJSONArray("savedPasswords")),
                    quickNotes = parseQuickNotes(parsed.optJSONArray("quickNotes"))
                )
            } catch (_: InvalidEncryptionKeyException) {
                // Try next key candidate
            } catch (_: Exception) {
                // Try next key candidate
            }
        }
        throw InvalidEncryptionKeyException()
    }

    private fun assembleChunksOnDisk(tempDir: File, safeId: String, totalChunks: Int, finalFile: File) {
        val copyBuf = ByteArray(IO_BUFFER_SIZE)
        BufferedOutputStream(FileOutputStream(finalFile), IO_BUFFER_SIZE).use { out ->
            for (i in 0 until totalChunks) {
                val part = repository.resolveConfinedFile(tempDir, "${safeId}_chunk_$i")
                if (!part.exists()) {
                    throw IncompleteBackupException("Missing chunk $i for media $safeId.")
                }
                FileInputStream(part).use { inp ->
                    while (true) {
                        val r = inp.read(copyBuf)
                        if (r == -1) break
                        out.write(copyBuf, 0, r)
                    }
                }
            }
            out.flush()
        }
    }

    private fun deleteTempChunks(tempDir: File, safeId: String, totalChunks: Int) {
        for (i in 0 until totalChunks) {
            runCatching {
                repository.resolveConfinedFile(tempDir, "${safeId}_chunk_$i").delete()
            }
        }
    }

    private fun parseAndCleanLogs(arr: JSONArray, savedAudioIds: Set<String>): List<DiaryLog> {
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val parsed = DiaryLog.fromJson(obj)
                val cleanedAudio = parsed.audioAttachments.map { att ->
                    if (att.id in savedAudioIds) att.copy(audioUri = null) else att
                }
                add(parsed.copy(audioAttachments = cleanedAudio))
            }
        }
    }

    private fun parseCustomFonts(arr: JSONArray?): List<CustomFontItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(CustomFontItem.fromJson(it)) }
            }
        }
    }

    private fun parseSavedPasswords(arr: JSONArray?): List<SavedPasswordItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(SavedPasswordItem.fromJson(it)) }
            }
        }
    }

    private fun parseQuickNotes(arr: JSONArray?): List<QuickNote> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(QuickNote.fromJson(it)) }
            }
        }
    }

    private fun parseTrackers(arr: JSONArray?): List<RecurringTracker> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(RecurringTracker.fromJson(it)) }
            }
        }
    }

    private fun parseReminders(arr: JSONArray?): List<StandaloneReminder> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(StandaloneReminder.fromJson(it)) }
            }
        }
    }

    private fun parseDocuments(arr: JSONArray?): List<PersonalDocument> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(PersonalDocument.fromJson(it)) }
            }
        }
    }

    // ==================== SAF Document Helpers ====================

    fun hasSafBackupFile(vaultMode: VaultMode, treeUri: Uri? = repository.getSafTreeUri(vaultMode)): Boolean {
        treeUri ?: return false
        return resolveSafBackupFileUri(appContext.contentResolver, treeUri, vaultMode) != null
    }

    fun getSafBackupFileSize(vaultMode: VaultMode, treeUri: Uri? = repository.getSafTreeUri(vaultMode)): Long {
        treeUri ?: return -1L
        val fileUri = resolveSafBackupFileUri(appContext.contentResolver, treeUri, vaultMode) ?: return -1L
        return queryUriSize(appContext.contentResolver, fileUri)
    }

    private fun resolveSafBackupFileUri(
        resolver: ContentResolver,
        treeUri: Uri,
        vaultMode: VaultMode
    ): Uri? {
        val cfg = VaultConfig.forMode(vaultMode)
        val candidates = if (vaultMode == VaultMode.DECOY) {
            listOf(cfg.dbFilename)
        } else {
            listOf(cfg.dbFilename, "likkho_encrypted_vault.json", "likkho_encrypted_vault.bak")
        }
        return try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val backupDirDocId = findChildDocumentId(resolver, treeUri, rootDocId, cfg.safFolderName)
            val searchDirs = listOfNotNull(backupDirDocId, rootDocId)
            for (dirId in searchDirs) {
                for (name in candidates) {
                    val fileDocId = findChildDocumentId(resolver, treeUri, dirId, name)
                    if (fileDocId != null) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun findChildDocumentId(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        targetName: String
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)
                if (targetName == name) {
                    return docId
                }
            }
        }
        return null
    }

    private fun getOrCreateChildDirectory(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        folderName: String
    ): String {
        findChildDocumentId(resolver, treeUri, parentDocId, folderName)?.let { return it }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val created = DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            folderName
        ) ?: throw IOException("Could not create folder $folderName in SAF storage.")
        return DocumentsContract.getDocumentId(created)
    }

    private fun ensureNomediaInSafFolder(
        resolver: ContentResolver,
        treeUri: Uri,
        folderDocId: String
    ) {
        if (findChildDocumentId(resolver, treeUri, folderDocId, ".nomedia") != null) return
        val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
        runCatching {
            val nomediaUri = DocumentsContract.createDocument(
                resolver,
                folderUri,
                "application/octet-stream",
                ".nomedia"
            )
            nomediaUri?.let { uri ->
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(ByteArray(0))
                }
            }
        }
    }

    private fun queryUriSize(resolver: ContentResolver, uri: Uri): Long {
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) return afd.length
            }
        }
        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 0L) return pfd.statSize
            }
        }
        runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) {
                    val s = c.getLong(0)
                    if (s > 0L) return s
                }
            }
        }
        return -1L
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE: Int = 1_572_864 // 1.5 MB exact match with chunkedVaultStream.ts
        private const val IO_BUFFER_SIZE: Int = 65_536
        private const val MAX_HEADER_STR_BYTES: Int = 4096
        private const val MAX_FRAME_CIPHER_BYTES: Int = 32 * 1024 * 1024

        private const val RECORD_TYPE_MANIFEST: Byte = 0x01
        private const val RECORD_TYPE_MEDIA_CHUNK: Byte = 0x02
        private val RECORD_TYPE_EOF: Byte = 0xFF.toByte()

        private val MAGIC_V1 = "LKSTRM01".toByteArray(StandardCharsets.US_ASCII)
        private val MAGIC_V2 = "LKSTRM02".toByteArray(StandardCharsets.US_ASCII)

        fun isChunkedStreamMagic(bytes: ByteArray, length: Int = bytes.size): Boolean {
            if (length < 8 || bytes.size < 8) return false
            var matchV1 = true
            var matchV2 = true
            for (i in 0 until 8) {
                if (bytes[i] != MAGIC_V1[i]) matchV1 = false
                if (bytes[i] != MAGIC_V2[i]) matchV2 = false
            }
            return matchV1 || matchV2
        }
    }
}

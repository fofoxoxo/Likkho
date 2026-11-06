package com.likkho.nativeapp.data.security

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.likkho.nativeapp.data.model.CanvasAudioAttachment
import com.likkho.nativeapp.data.model.CanvasDraggableImage
import com.likkho.nativeapp.data.model.DiaryLog
import com.likkho.nativeapp.data.model.VaultBackupBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native Streaming Chunked Binary Encryption & Storage Engine for Likkho Native Android.
 *
 * Implements Direct-IO streaming using Linux Direct Memory Access (DMA), FileChannel,
 * and ParcelFileDescriptor:
 * 1. ZERO 200MB file heap accumulation (memory usage capped at 1.5MB per chunk).
 * 2. Binary LKSTRM02 format (compatible with existing Likkho backups).
 * 3. Safe Atomic replace (.tmp -> target, rollback on failure).
 * 4. EOF verification and partial media resilience.
 */
class NativeStreamVaultEngine(private val context: Context) {

    companion object {
        // LKSTRM02 magic bytes (8 bytes) + version 2 (2 bytes)
        val MAGIC_BYTES_V2 = "LKSTRM02".toByteArray(StandardCharsets.US_ASCII)
        val MAGIC_BYTES_V1 = "LKSTRM01".toByteArray(StandardCharsets.US_ASCII)

        const val CHUNK_SIZE = 1536 * 1024 // 1.5MB per chunk
        const val RECORD_TYPE_MANIFEST: Byte = 0x01
        const val RECORD_TYPE_MEDIA_CHUNK: Byte = 0x02
        const val RECORD_TYPE_EOF: Byte = 0xFF.toByte()

        const val FOLDER_PRIMARY = ".likkho_backups"
        const val FOLDER_DECOY = ".likkho_decoy_backups"
        const val FILE_PRIMARY = "real_vault.db"
        const val FILE_DECOY = "decoy_vault.db"
    }

    data class StreamBackupResult(
        val success: Boolean,
        val totalBytes: Long,
        val entryCount: Int,
        val failedMediaCount: Int,
        val message: String
    )

    data class StreamRestoreResult(
        val bundle: VaultBackupBundle,
        val skippedMediaCount: Int
    )

    // ==================== STREAMING BACKUP ====================
    suspend fun performStreamingBackup(
        treeUri: Uri,
        bundle: VaultBackupBundle,
        passphrase: String,
        vaultMode: String
    ): StreamBackupResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val subfolder = if (vaultMode == "decoy") FOLDER_DECOY else FOLDER_PRIMARY
        val targetFilename = if (vaultMode == "decoy") FILE_DECOY else FILE_PRIMARY
        val tmpFilename = "$targetFilename.tmp"
        val bakFilename = "$targetFilename.bak"

        var failedMediaCount = 0
        var totalBytesWritten = 0L

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val subfolderDocId = getOrCreateChildDirectory(resolver, treeUri, rootDocId, subfolder)
            ensureNomedia(resolver, treeUri, subfolderDocId)

            // 1. Delete previous crashed .tmp file if any
            findChildDocId(resolver, treeUri, subfolderDocId, tmpFilename)?.let {
                try { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, it)) } catch (ignored: Exception) {}
            }

            // 2. Create .tmp file
            val subfolderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, subfolderDocId)
            val tmpFileUri = DocumentsContract.createDocument(resolver, subfolderUri, "application/octet-stream", tmpFilename)
                ?: return@withContext StreamBackupResult(false, 0, 0, 0, "Failed to create temporary backup file via SAF.")

            val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(tmpFileUri, "wt")
                ?: return@withContext StreamBackupResult(false, 0, 0, 0, "Cannot open output descriptor for backup.")

            pfd.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
                    // KDF Parameters: Primary (100k, SHA-256) vs Decoy (250k, SHA-512)
                    val isDecoy = (vaultMode == "decoy")
                    val iterations = if (isDecoy) 250000 else 100000
                    val kdfHash = if (isDecoy) "SHA-512" else "SHA-256"
                    val kdfHashId: Byte = if (isDecoy) 0x02 else 0x01

                    val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
                    val baseIv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

                    // Derive AES-256 key
                    val aesKey = deriveKey(passphrase, salt, iterations, kdfHash)

                    // Write 44-byte LKSTRM02 Header
                    val headerBuf = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
                    headerBuf.put(MAGIC_BYTES_V2) // 8 bytes
                    headerBuf.putShort(2.toShort()) // Version = 2
                    headerBuf.put(salt) // 16 bytes
                    headerBuf.putInt(iterations) // 4 bytes
                    headerBuf.put(kdfHashId) // 1 byte
                    headerBuf.put(baseIv) // 12 bytes
                    headerBuf.put(0x00.toByte()) // 1 byte reserved
                    headerBuf.flip()

                    channel.write(headerBuf)
                    totalBytesWritten += 44

                    var chunkCounter = 0

                    // 1. Write Manifest Record (Type 0x01)
                    val manifestJson = buildManifestJson(bundle)
                    val manifestBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
                    val manifestIv = deriveChunkIv(baseIv, chunkCounter++)
                    val encryptedManifest = encryptChunk(manifestBytes, aesKey, manifestIv, RECORD_TYPE_MANIFEST, 0, 1, "manifest")

                    val manifestFrame = buildRecordFrame(
                        type = RECORD_TYPE_MANIFEST,
                        id = "manifest",
                        chunkIndex = 0,
                        totalChunks = 1,
                        mimeType = "application/json",
                        iv = manifestIv,
                        ciphertext = encryptedManifest
                    )
                    channel.write(ByteBuffer.wrap(manifestFrame))
                    totalBytesWritten += manifestFrame.size

                    // 2. Stream Attached Media Files chunk-by-chunk (1.5MB per chunk)
                    val allMedia = collectMediaItems(bundle)
                    val buffer = ByteArray(CHUNK_SIZE)

                    for (item in allMedia) {
                        val mediaFile = File(item.filePath)
                        if (!mediaFile.exists() || mediaFile.length() == 0L) {
                            failedMediaCount++
                            continue
                        }

                        val fileSize = mediaFile.length()
                        val totalChunks = Math.max(1, Math.ceil(fileSize.toDouble() / CHUNK_SIZE).toInt())
                        val mimeType = item.mimeType

                        try {
                            FileInputStream(mediaFile).use { fis ->
                                var cIdx = 0
                                var bytesRead: Int
                                while (fis.read(buffer).also { bytesRead = it } != -1) {
                                    val slice = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                                    val chunkIv = deriveChunkIv(baseIv, chunkCounter++)
                                    val encryptedSlice = encryptChunk(slice, aesKey, chunkIv, RECORD_TYPE_MEDIA_CHUNK, cIdx, totalChunks, item.id)

                                    val frame = buildRecordFrame(
                                        type = RECORD_TYPE_MEDIA_CHUNK,
                                        id = item.id,
                                        chunkIndex = cIdx,
                                        totalChunks = totalChunks,
                                        mimeType = mimeType,
                                        iv = chunkIv,
                                        ciphertext = encryptedSlice
                                    )
                                    channel.write(ByteBuffer.wrap(frame))
                                    totalBytesWritten += frame.size
                                    cIdx++
                                }
                            }
                        } catch (e: Exception) {
                            failedMediaCount++
                        }
                    }

                    // 3. Write EOF Record (Type 0xFF)
                    val eofIv = deriveChunkIv(baseIv, chunkCounter++)
                    val encryptedEof = encryptChunk("EOF".toByteArray(StandardCharsets.UTF_8), aesKey, eofIv, RECORD_TYPE_EOF, 0, 1, "eof")
                    val eofFrame = buildRecordFrame(
                        type = RECORD_TYPE_EOF,
                        id = "eof",
                        chunkIndex = 0,
                        totalChunks = 1,
                        mimeType = "text/plain",
                        iv = eofIv,
                        ciphertext = encryptedEof
                    )
                    channel.write(ByteBuffer.wrap(eofFrame))
                    totalBytesWritten += eofFrame.size
                }
            }

            // 3. Atomic replace: rename old to .bak, rename .tmp to target, then delete .bak
            var bakUri: Uri? = null
            try {
                // Delete existing old .bak if any
                findChildDocId(resolver, treeUri, subfolderDocId, bakFilename)?.let {
                    try { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, it)) } catch (ignored: Exception) {}
                }

                // If target file exists, rename to .bak
                findChildDocId(resolver, treeUri, subfolderDocId, targetFilename)?.let { existingId ->
                    val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
                    bakUri = DocumentsContract.renameDocument(resolver, existingUri, bakFilename)
                }

                // Rename .tmp to target filename
                val finalUri = DocumentsContract.renameDocument(resolver, tmpFileUri, targetFilename)
                if (finalUri != null) {
                    // Success: delete backup .bak file
                    bakUri?.let {
                        try { DocumentsContract.deleteDocument(resolver, it) } catch (ignored: Exception) {}
                    }
                    return@withContext StreamBackupResult(
                        success = true,
                        totalBytes = totalBytesWritten,
                        entryCount = bundle.logs.size,
                        failedMediaCount = failedMediaCount,
                        message = "Backup saved successfully."
                    )
                } else {
                    throw Exception("Failed to rename temporary backup file.")
                }
            } catch (renameEx: Exception) {
                // Rollback: restore .bak if needed
                bakUri?.let {
                    try { DocumentsContract.renameDocument(resolver, it, targetFilename) } catch (ignored: Exception) {}
                }
                try { DocumentsContract.deleteDocument(resolver, tmpFileUri) } catch (ignored: Exception) {}
                return@withContext StreamBackupResult(false, 0, 0, 0, "Atomic rename failed: ${renameEx.message}")
            }
        } catch (e: Exception) {
            return@withContext StreamBackupResult(false, 0, 0, 0, e.message ?: "Backup failed.")
        }
    }

    // ==================== STREAMING RESTORE ====================
    suspend fun performStreamingRestore(
        treeUri: Uri,
        passphrase: String,
        vaultMode: String
    ): StreamRestoreResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val subfolder = if (vaultMode == "decoy") FOLDER_DECOY else FOLDER_PRIMARY
        val targetFilename = if (vaultMode == "decoy") FILE_DECOY else FILE_PRIMARY

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val subfolderDocId = findChildDocId(resolver, treeUri, rootDocId, subfolder)
            ?: throw Exception("No backup folder found.")

        val fileDocId = findChildDocId(resolver, treeUri, subfolderDocId, targetFilename)
            ?: throw Exception("No backup file ($targetFilename) found.")

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
        val pfd = resolver.openFileDescriptor(fileUri, "r")
            ?: throw Exception("Cannot open backup file for reading.")

        var skippedMediaCount = 0

        pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                val fileSize = channel.size()
                if (fileSize < 44) {
                    throw Exception("Backup file is too small or corrupted.")
                }

                // 1. Read LKSTRM Header (44 bytes for V2, 42 bytes for V1)
                val headerBuf = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
                channel.read(headerBuf)
                headerBuf.flip()

                val magic = ByteArray(8)
                headerBuf.get(magic)

                val isV2 = magic.contentEquals(MAGIC_BYTES_V2)
                val isV1 = magic.contentEquals(MAGIC_BYTES_V1)

                if (!isV2 && !isV1) {
                    throw Exception("Invalid backup file header (not a Likkho vault stream).")
                }

                val version = headerBuf.short.toInt()
                val salt = ByteArray(16)
                headerBuf.get(salt)
                val iterations = headerBuf.int
                val kdfHash = if (isV2) {
                    val hashId = headerBuf.get()
                    if (hashId == 0x02.toByte()) "SHA-512" else "SHA-256"
                } else "SHA-256"

                val baseIv = ByteArray(12)
                headerBuf.get(baseIv)

                // Rewind channel to post-header
                val postHeaderOffset = if (isV2) 44L else 42L
                channel.position(postHeaderOffset)

                // Derive decryption key
                val aesKey = deriveKey(passphrase, salt, iterations, kdfHash)

                var restoredBundle: VaultBackupBundle? = null
                var eofSeen = false
                val multiChunkAssemblers = mutableMapOf<String, FileOutputStream>()

                val internalMediaDir = File(context.filesDir, "likkho_media").apply { if (!exists()) mkdirs() }
                val internalAudioDir = File(context.filesDir, "likkho_audio").apply { if (!exists()) mkdirs() }

                // 2. Read frame-by-frame
                val headerLenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)

                while (channel.position() < fileSize) {
                    headerLenBuf.clear()
                    val readLen = channel.read(headerLenBuf)
                    if (readLen < 4) break
                    headerLenBuf.flip()

                    val frameLen = headerLenBuf.int
                    if (frameLen <= 0 || frameLen > 50 * 1024 * 1024) break // Safety check (max 50MB per frame)

                    val frameBuf = ByteBuffer.allocate(frameLen).order(ByteOrder.BIG_ENDIAN)
                    var readTotal = 0
                    while (readTotal < frameLen) {
                        val r = channel.read(frameBuf)
                        if (r <= 0) break
                        readTotal += r
                    }
                    frameBuf.flip()

                    // Parse Frame
                    val recordType = frameBuf.get()
                    val idLen = frameBuf.short.toInt()
                    val idBytes = ByteArray(idLen)
                    frameBuf.get(idBytes)
                    val id = String(idBytes, StandardCharsets.UTF_8)

                    val chunkIndex = frameBuf.int
                    val totalChunks = frameBuf.int

                    val mimeLen = frameBuf.short.toInt()
                    val mimeBytes = ByteArray(mimeLen)
                    frameBuf.get(mimeBytes)
                    val mimeType = String(mimeBytes, StandardCharsets.UTF_8)

                    val iv = ByteArray(12)
                    frameBuf.get(iv)

                    val cipherLen = frameBuf.int
                    val cipherBytes = ByteArray(cipherLen)
                    frameBuf.get(cipherBytes)

                    // Decrypt chunk
                    val decryptedBytes = try {
                        decryptChunk(cipherBytes, aesKey, iv, recordType, chunkIndex, totalChunks, id)
                    } catch (e: Exception) {
                        throw Exception("Incorrect Encryption Key.")
                    }

                    when (recordType) {
                        RECORD_TYPE_MANIFEST -> {
                            val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
                            restoredBundle = parseManifestJson(jsonStr)
                        }
                        RECORD_TYPE_MEDIA_CHUNK -> {
                            val isAudio = mimeType.startsWith("audio/")
                            val targetDir = if (isAudio) internalAudioDir else internalMediaDir
                            val ext = if (isAudio) ".wav" else ".jpg"
                            val outFile = File(targetDir, "${id}$ext")

                            if (totalChunks == 1) {
                                FileOutputStream(outFile).use { it.write(decryptedBytes) }
                            } else {
                                if (chunkIndex == 0) {
                                    multiChunkAssemblers[id] = FileOutputStream(outFile)
                                }
                                multiChunkAssemblers[id]?.write(decryptedBytes)
                                if (chunkIndex == totalChunks - 1) {
                                    multiChunkAssemblers[id]?.close()
                                    multiChunkAssemblers.remove(id)
                                }
                            }
                        }
                        RECORD_TYPE_EOF -> {
                            eofSeen = true
                            break
                        }
                    }
                }

                // Cleanup any open assemblers if incomplete
                multiChunkAssemblers.values.forEach { try { it.close() } catch (ignored: Exception) {} }
                if (multiChunkAssemblers.isNotEmpty()) {
                    skippedMediaCount += multiChunkAssemblers.size
                }

                if (!eofSeen) {
                    throw Exception("Backup file adhoori hai: missing EOF record marker.")
                }

                val finalBundle = restoredBundle ?: throw Exception("Backup file contains no diary logs.")
                return@withContext StreamRestoreResult(finalBundle, skippedMediaCount)
            }
        }
    }

    // ==================== CRYPTOGRAPHIC HELPERS ====================
    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int, hash: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(
            if (hash == "SHA-512") "PBKDF2WithHmacSHA512" else "PBKDF2WithHmacSHA256"
        )
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun deriveChunkIv(baseIv: ByteArray, counter: Int): ByteArray {
        val iv = baseIv.copyOf()
        val c0 = (counter and 0xFF).toByte()
        val c1 = ((counter shr 8) and 0xFF).toByte()
        val c2 = ((counter shr 16) and 0xFF).toByte()
        val c3 = ((counter shr 24) and 0xFF).toByte()
        iv[8] = (iv[8].toInt() xor c3.toInt()).toByte()
        iv[9] = (iv[9].toInt() xor c2.toInt()).toByte()
        iv[10] = (iv[10].toInt() xor c1.toInt()).toByte()
        iv[11] = (iv[11].toInt() xor c0.toInt()).toByte()
        return iv
    }

    private fun encryptChunk(
        plaintext: ByteArray,
        key: SecretKeySpec,
        iv: ByteArray,
        recordType: Byte,
        chunkIndex: Int,
        totalChunks: Int,
        id: String
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        cipher.updateAAD(buildAAD(recordType, chunkIndex, totalChunks, id))
        return cipher.doFinal(plaintext)
    }

    private fun decryptChunk(
        ciphertext: ByteArray,
        key: SecretKeySpec,
        iv: ByteArray,
        recordType: Byte,
        chunkIndex: Int,
        totalChunks: Int,
        id: String
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.updateAAD(buildAAD(recordType, chunkIndex, totalChunks, id))
        return cipher.doFinal(ciphertext)
    }

    private fun buildAAD(recordType: Byte, chunkIndex: Int, totalChunks: Int, id: String): ByteArray {
        val idBytes = id.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(1 + 4 + 4 + idBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(recordType)
        buf.putInt(chunkIndex)
        buf.putInt(totalChunks)
        buf.put(idBytes)
        return buf.array()
    }

    private fun buildRecordFrame(
        type: Byte,
        id: String,
        chunkIndex: Int,
        totalChunks: Int,
        mimeType: String,
        iv: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val idBytes = id.toByteArray(StandardCharsets.UTF_8)
        val mimeBytes = mimeType.toByteArray(StandardCharsets.UTF_8)

        val payloadSize = 1 + 2 + idBytes.size + 4 + 4 + 2 + mimeBytes.size + 12 + 4 + ciphertext.size
        val buf = ByteBuffer.allocate(4 + payloadSize).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(payloadSize)
        buf.put(type)
        buf.putShort(idBytes.size.toShort())
        buf.put(idBytes)
        buf.putInt(chunkIndex)
        buf.putInt(totalChunks)
        buf.putShort(mimeBytes.size.toShort())
        buf.put(mimeBytes)
        buf.put(iv)
        buf.putInt(ciphertext.size)
        buf.put(ciphertext)
        return buf.array()
    }

    // ==================== SAF UTILITIES ====================
    private fun getOrCreateChildDirectory(resolver: ContentResolver, treeUri: Uri, parentDocId: String, name: String): String {
        findChildDocId(resolver, treeUri, parentDocId, name)?.let { return it }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val newUri = DocumentsContract.createDocument(resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw Exception("Failed to create SAF directory: $name")
        return DocumentsContract.getDocumentId(newUri)
    }

    private fun findChildDocId(resolver: ContentResolver, treeUri: Uri, parentDocId: String, name: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == name) {
                    return cursor.getString(idIdx)
                }
            }
        }
        return null
    }

    private fun ensureNomedia(resolver: ContentResolver, treeUri: Uri, dirDocId: String) {
        if (findChildDocId(resolver, treeUri, dirDocId, ".nomedia") == null) {
            val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, dirDocId)
            try {
                DocumentsContract.createDocument(resolver, dirUri, "application/octet-stream", ".nomedia")
            } catch (ignored: Exception) {}
        }
    }

    private data class MediaItemRef(val id: String, val filePath: String, val mimeType: String)

    private fun collectMediaItems(bundle: VaultBackupBundle): List<MediaItemRef> {
        val list = mutableListOf<MediaItemRef>()
        for (log in bundle.logs) {
            log.canvasImages.forEach { img ->
                if (img.filePath.isNotBlank()) {
                    list.add(MediaItemRef(img.id, img.filePath, "image/jpeg"))
                }
            }
            log.audioAttachments.forEach { audio ->
                if (audio.filePath.isNotBlank()) {
                    val mime = if (audio.format.lowercase() == "mp3") "audio/mpeg" else "audio/wav"
                    list.add(MediaItemRef(audio.id, audio.filePath, mime))
                }
            }
        }
        return list
    }

    private fun buildManifestJson(bundle: VaultBackupBundle): String {
        val obj = JSONObject()
        val logsArr = JSONArray()
        for (log in bundle.logs) {
            val lObj = JSONObject().apply {
                put("id", log.id)
                put("heading", log.heading)
                put("contentHtml", log.contentHtml)
                put("plainPreview", log.plainPreview)
                put("createdAt", log.createdAt)
                put("updatedAt", log.updatedAt)
                put("dateStamp", log.dateStamp)
                put("timeStamp", log.timeStamp)
                put("pinned", log.pinned)
                put("diaryLockPin", log.diaryLockPin ?: "")
                put("canvasBgOpacity", log.canvasBgOpacity.toDouble())

                val imgArr = JSONArray()
                log.canvasImages.forEach { img ->
                    imgArr.put(JSONObject().apply {
                        put("id", img.id)
                        put("x", img.x.toDouble())
                        put("y", img.y.toDouble())
                        put("width", img.width.toDouble())
                        put("height", img.height.toDouble())
                        put("opacity", img.opacity.toDouble())
                        put("rotation", img.rotation.toDouble())
                    })
                }
                put("canvasImages", imgArr)

                val audArr = JSONArray()
                log.audioAttachments.forEach { aud ->
                    audArr.put(JSONObject().apply {
                        put("id", aud.id)
                        put("name", aud.name)
                        put("format", aud.format)
                        put("durationSec", aud.durationSec.toDouble())
                    })
                }
                put("audioAttachments", audArr)
            }
            logsArr.put(lObj)
        }
        obj.put("logs", logsArr)
        return obj.toString()
    }

    private fun parseManifestJson(jsonStr: String): VaultBackupBundle {
        val root = JSONObject(jsonStr)
        val logsArr = root.getJSONArray("logs")
        val logs = mutableListOf<DiaryLog>()

        val internalMediaDir = File(context.filesDir, "likkho_media")
        val internalAudioDir = File(context.filesDir, "likkho_audio")

        for (i in 0 until logsArr.length()) {
            val o = logsArr.getJSONObject(i)
            val logId = o.getString("id")

            val images = mutableListOf<CanvasDraggableImage>()
            val imgArr = o.optJSONArray("canvasImages")
            if (imgArr != null) {
                for (j in 0 until imgArr.length()) {
                    val io = imgArr.getJSONObject(j)
                    val imgId = io.getString("id")
                    val localImgPath = File(internalMediaDir, "$imgId.jpg").absolutePath
                    images.add(
                        CanvasDraggableImage(
                            id = imgId,
                            filePath = localImgPath,
                            x = io.optDouble("x", 0.0).toFloat(),
                            y = io.optDouble("y", 0.0).toFloat(),
                            width = io.optDouble("width", 200.0).toFloat(),
                            height = io.optDouble("height", 200.0).toFloat(),
                            opacity = io.optDouble("opacity", 1.0).toFloat(),
                            rotation = io.optDouble("rotation", 0.0).toFloat()
                        )
                    )
                }
            }

            val audios = mutableListOf<CanvasAudioAttachment>()
            val audArr = o.optJSONArray("audioAttachments")
            if (audArr != null) {
                for (j in 0 until audArr.length()) {
                    val ao = audArr.getJSONObject(j)
                    val audId = ao.getString("id")
                    val fmt = ao.optString("format", "wav")
                    val localAudioPath = File(internalAudioDir, "$audId.wav").absolutePath
                    audios.add(
                        CanvasAudioAttachment(
                            id = audId,
                            name = ao.optString("name", "Voice Note"),
                            format = fmt,
                            filePath = localAudioPath,
                            durationSec = ao.optDouble("durationSec", 0.0).toFloat()
                        )
                    )
                }
            }

            logs.add(
                DiaryLog(
                    id = logId,
                    heading = o.optString("heading", "Untitled Diary"),
                    contentHtml = o.optString("contentHtml", ""),
                    plainPreview = o.optString("plainPreview", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    dateStamp = o.optString("dateStamp", ""),
                    timeStamp = o.optString("timeStamp", ""),
                    pinned = o.optBoolean("pinned", false),
                    diaryLockPin = o.optString("diaryLockPin", null).takeIf { !it.isNullOrBlank() },
                    canvasBgOpacity = o.optDouble("canvasBgOpacity", 1.0).toFloat(),
                    canvasImages = images,
                    audioAttachments = audios
                )
            )
        }
        return VaultBackupBundle(logs = logs)
    }
}

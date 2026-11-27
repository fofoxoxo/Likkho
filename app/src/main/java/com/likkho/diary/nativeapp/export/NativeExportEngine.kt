package com.likkho.diary.nativeapp.export

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CanvasAudioAttachment
import com.likkho.diary.nativeapp.model.CanvasDraggableImage
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.VaultMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class NativeExportFormat(val ext: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MD("md", "text/markdown"),
    PDF("pdf", "application/pdf"),
    RTF("rtf", "application/rtf"),
    HTML("html", "text/html"),
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    TSV("tsv", "text/tab-separated-values"),
    XML("xml", "application/xml");

    companion object {
        fun fromExt(ext: String): NativeExportFormat =
            entries.firstOrNull { it.ext.equals(ext.trim().removePrefix("."), ignoreCase = true) } ?: TXT
    }
}

data class ExportedDestinationResult(
    val filename: String,
    val displayLocation: String,
    val sizeBytes: Long,
    val uri: Uri? = null,
    val localFile: File? = null
)

/**
 * Native Streaming Exporter & `.likkho` Share Archive Engine.
 *
 * Implements:
 * - `.likkho` encrypted shareable archive pack & unpack (`LKSHRE01` AES-256-GCM + `LIKKHO_SHARED_DIARY_ARCHIVE_V1`,
 *   100% compatible with `src/utils/likkhoShareArchive.ts`).
 * - Multi-page A4 `.pdf` via `android.graphics.pdf.PdfDocument` with per-page canvas background,
 *   line-safe pagination, inline/canvas images, and opaque red audio cards.
 * - `.html`, `.md`, `.txt`, `.json`, `.rtf`, `.csv`, `.tsv`, `.xml` single-diary exports.
 * - Full-vault `.zip` export (`ZipOutputStream` streaming media directly from disk, matching `src/utils/zipDataExporter.ts`).
 * - Direct export to SAF `Uri` or `Downloads/Likkho/`.
 */
class NativeExportEngine(
    context: Context,
    private val repository: NativeVaultRepository,
    private val mediaEngine: NativeMediaEngine = NativeMediaEngine(context, repository)
) {
    private val appContext: Context = context.applicationContext
    private val secureRandom = SecureRandom()

    // ==================== 1. `.likkho` Share Archive Pack & Unpack ====================

    fun packDiaryToLikkhoFile(
        log: DiaryLog,
        outputFile: File? = null
    ): File {
        val slug = sanitizeArchiveSlug(log.heading, "Likkho_Diary")
        val filename = "$slug.likkho"
        val targetFile = outputFile ?: File(
            File(appContext.cacheDir, "shared_likkho").apply { if (!exists()) mkdirs() },
            filename
        )
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val tempZipFile = File(appContext.cacheDir, "likkho_pack_${System.nanoTime()}.zip")
        try {
            writeDiaryZipArchive(log, tempZipFile)

            val plainZipBytes = tempZipFile.readBytes()
            val encryptedBytes = encryptWithUniversalLikkhoShareKey(plainZipBytes)
            writeBytesAtomic(targetFile, encryptedBytes)
            return targetFile
        } finally {
            tempZipFile.delete()
        }
    }

    fun exportLikkhoArchiveToDownloads(log: DiaryLog): ExportedDestinationResult {
        val tempLikkho = packDiaryToLikkhoFile(log)
        return try {
            saveFileToDownloads(tempLikkho, tempLikkho.name, LIKKHO_MIME_TYPE)
        } finally {
            if (tempLikkho.parentFile?.name == "shared_likkho") {
                // Keep cached copy or let caller clean up; Downloads now has its own copy
            }
        }
    }

    fun buildShareLikkhoIntent(
        likkhoFile: File,
        diaryHeading: String,
        githubUrl: String = LIKKHO_GITHUB_DOWNLOAD_URL
    ): Intent {
        val heading = diaryHeading.ifBlank { "Diary Entry" }
        val shareMessage = "📖 Shared a diary entry \"$heading\" from Likkho (लिक्खो).\n\n" +
            "Open the attached .likkho file in the Likkho app to read the text, images, and audio recordings in their exact order.\n\n" +
            "Download Likkho APK from GitHub:\n$githubUrl"

        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            likkhoFile,
            likkhoFile.name
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = LIKKHO_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            putExtra("sms_body", shareMessage)
            putExtra(Intent.EXTRA_TITLE, likkhoFile.name)
            putExtra(Intent.EXTRA_SUBJECT, likkhoFile.name)
            clipData = ClipData(
                likkhoFile.name,
                arrayOf(LIKKHO_MIME_TYPE, "text/plain"),
                ClipData.Item(shareMessage, null, contentUri)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Likkho Diary (.likkho)").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun unpackLikkhoFromUri(uri: Uri): DiaryLog {
        val inp = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open .likkho archive.")
        return inp.use { unpackLikkhoFromStream(it) }
    }

    fun unpackLikkhoFromStream(inputStream: InputStream): DiaryLog {
        val incomingBytes = inputStream.readBytes()
        val containerBytes = if (hasUniversalLikkhoShareMagic(incomingBytes)) {
            decryptWithUniversalLikkhoShareKey(incomingBytes)
        } else {
            incomingBytes
        }

        val extractDir = File(appContext.cacheDir, "likkho_unpack_${System.nanoTime()}").apply {
            if (!exists()) mkdirs()
        }
        val extractedPaths = HashMap<String, File>()

        try {
            val isZip = containerBytes.size >= 4 &&
                containerBytes[0] == 0x50.toByte() &&
                containerBytes[1] == 0x4B.toByte() &&
                containerBytes[2] == 0x03.toByte() &&
                containerBytes[3] == 0x04.toByte()

            if (isZip) {
                ZipInputStream(BufferedInputStream(ByteArrayInputStream(containerBytes), IO_BUFFER_SIZE)).use { zis ->
                    val buf = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (entry.isDirectory) {
                            zis.closeEntry()
                            continue
                        }
                        val cleanRelPath = sanitizeZipEntryPath(entry.name)
                        val destFile = resolveConfinedZipTarget(extractDir, cleanRelPath)
                        destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        BufferedOutputStream(FileOutputStream(destFile), IO_BUFFER_SIZE).use { fos ->
                            while (true) {
                                val r = zis.read(buf)
                                if (r == -1) break
                                fos.write(buf, 0, r)
                            }
                            fos.flush()
                        }
                        extractedPaths[cleanRelPath] = destFile
                        zis.closeEntry()
                    }
                }
            } else {
                // Fallback: plain JSON or gzip JSON
                val jsonBytes = runCatching {
                    GZIPInputStream(ByteArrayInputStream(containerBytes)).use { it.readBytes() }
                }.getOrDefault(containerBytes)
                val fallbackFile = File(extractDir, "manifest.json")
                fallbackFile.writeBytes(jsonBytes)
                extractedPaths["manifest.json"] = fallbackFile
            }

            val manifestStr = when {
                extractedPaths.containsKey("manifest.json") ->
                    extractedPaths.getValue("manifest.json").readText(StandardCharsets.UTF_8)
                extractedPaths.containsKey("manifest.json.gz") ->
                    GZIPInputStream(FileInputStream(extractedPaths.getValue("manifest.json.gz"))).use {
                        String(it.readBytes(), StandardCharsets.UTF_8)
                    }
                else -> throw IOException("Invalid or corrupted .likkho file.")
            }

            val manifest = JSONObject(manifestStr)
            if (manifest.optString("magic") != "LIKKHO_SHARED_DIARY_ARCHIVE_V1" || !manifest.has("diary")) {
                throw IOException("Unrecognized .likkho diary archive.")
            }
            val d = manifest.getJSONObject("diary")

            // 1. PFP
            var pfpRef: String? = d.optString("pfpDataUrlFallback").takeIf { it.isNotEmpty() && !d.isNull("pfpDataUrlFallback") }
            val pfpPath = d.optString("pfpPath").takeIf { it.isNotEmpty() && !d.isNull("pfpPath") }
            if (pfpPath != null) {
                extractedPaths[pfpPath]?.let { f ->
                    pfpRef = persistExtractedImageFile(f, "img_blob_pfp")
                }
            }

            // 2. Canvas Background
            var canvasBgRef: String? = d.optString("canvasBgDataUrlFallback").takeIf { it.isNotEmpty() && !d.isNull("canvasBgDataUrlFallback") }
            val canvasBgPath = d.optString("canvasBgPath").takeIf { it.isNotEmpty() && !d.isNull("canvasBgPath") }
            if (canvasBgPath != null) {
                extractedPaths[canvasBgPath]?.let { f ->
                    canvasBgRef = persistExtractedImageFile(f, "img_blob_bg")
                }
            }

            // 3. Inline Images inside contentHtml
            var restoredHtml = d.optString("contentHtml", "")
            val inlineArr = d.optJSONArray("inlineImages")
            if (inlineArr != null) {
                for (i in 0 until inlineArr.length()) {
                    val inl = inlineArr.optJSONObject(i) ?: continue
                    val placeholderId = inl.optString("placeholderId")
                    val archivePath = inl.optString("archivePath")
                    val f = extractedPaths[archivePath]
                    if (placeholderId.isNotEmpty() && f != null) {
                        val inlId = persistExtractedImageFile(f, "img_blob_inl")
                        val assetUrl = "$ASSET_MEDIA_PREFIX$inlId"
                        restoredHtml = replaceInlinePlaceholderInHtml(restoredHtml, placeholderId, assetUrl)
                    }
                }
            }

            // 4. Canvas Images
            val canvasImages = ArrayList<CanvasDraggableImage>()
            val cImgArr = d.optJSONArray("canvasImages")
            if (cImgArr != null) {
                for (i in 0 until cImgArr.length()) {
                    val cImg = cImgArr.optJSONObject(i) ?: continue
                    val archivePath = cImg.optString("archivePath")
                    var imgRef = cImg.optString("dataUrlFallback")
                    val f = extractedPaths[archivePath]
                    if (f != null) {
                        imgRef = persistExtractedImageFile(f, "img_blob_cnv")
                    }
                    if (imgRef.isNotEmpty()) {
                        canvasImages.add(
                            CanvasDraggableImage(
                                id = cImg.optString("id").ifEmpty { "img_${System.currentTimeMillis()}_$i" },
                                dataUrl = imgRef,
                                x = cImg.optDouble("x", 24.0),
                                y = cImg.optDouble("y", 80.0),
                                width = cImg.optDouble("width", 220.0),
                                height = cImg.optDouble("height", 165.0),
                                opacity = cImg.optDouble("opacity", 1.0),
                                rotation = cImg.optDouble("rotation", 0.0),
                                layer = cImg.optString("layer", "foreground")
                            )
                        )
                    }
                }
            }

            // 5. Audio Attachments
            val audioAttachments = ArrayList<CanvasAudioAttachment>()
            val audArr = d.optJSONArray("audioAttachments")
            if (audArr != null) {
                for (i in 0 until audArr.length()) {
                    val aud = audArr.optJSONObject(i) ?: continue
                    val audId = aud.optString("id").ifEmpty { "aud_${System.currentTimeMillis()}_$i" }
                    val format = aud.optString("format", "wav").lowercase().trim().removePrefix(".").ifEmpty { "wav" }
                    val archivePath = aud.optString("archivePath")
                    val extractedAud = extractedPaths[archivePath]
                    var dataUrlFallback = aud.optString("dataUrlFallback")

                    if (extractedAud != null) {
                        val destAudio = mediaEngine.createRecordingTargetFile(audId, format)
                        copyFileAtomic(extractedAud, destAudio)
                        dataUrlFallback = ""
                    } else if (dataUrlFallback.startsWith("data:")) {
                        mediaEngine.parseDataUrl(dataUrlFallback)?.let { parsed ->
                            mediaEngine.importAudioFromBytes(parsed.bytes, format, aud.optString("name", "Voice Note"), audId)
                            dataUrlFallback = ""
                        }
                    }

                    audioAttachments.add(
                        CanvasAudioAttachment(
                            id = audId,
                            name = aud.optString("name", "Voice Note"),
                            format = format,
                            dataUrl = dataUrlFallback.ifEmpty { "" },
                            audioUri = null,
                            durationSec = if (aud.has("durationSec") && !aud.isNull("durationSec")) aud.optDouble("durationSec") else null,
                            createdAt = aud.optLong("createdAt", System.currentTimeMillis()),
                            x = aud.optDouble("x", 24.0),
                            y = aud.optDouble("y", 120.0),
                            width = aud.optDouble("width", 270.0),
                            height = aud.optDouble("height", 56.0),
                            rotation = aud.optDouble("rotation", 0.0),
                            playbackRate = aud.optDouble("playbackRate", 1.0),
                            layer = aud.optString("layer", "foreground")
                        )
                    )
                }
            }

            val rand = UUID.randomUUID().toString().replace("-", "").take(5)
            val now = System.currentTimeMillis()
            return DiaryLog(
                id = "log_shared_${now}_$rand",
                heading = d.optString("heading", "Shared Diary"),
                contentHtml = restoredHtml,
                plainPreview = d.optString("plainPreview", ""),
                pfpDataUrl = pfpRef,
                createdAt = d.optLong("createdAt", now),
                updatedAt = d.optLong("updatedAt", now),
                dateStamp = d.optString("dateStamp", ""),
                timeStamp = d.optString("timeStamp", ""),
                updatedDateStamp = d.optString("updatedDateStamp").takeIf { it.isNotEmpty() && !d.isNull("updatedDateStamp") },
                updatedTimeStamp = d.optString("updatedTimeStamp").takeIf { it.isNotEmpty() && !d.isNull("updatedTimeStamp") },
                reminderAt = null,
                reminderFired = false,
                pinned = false,
                diaryLockPin = d.optString("diaryLockPin").takeIf { it.isNotEmpty() && !d.isNull("diaryLockPin") },
                canvasBgDataUrl = canvasBgRef,
                canvasBgOpacity = if (d.has("canvasBgOpacity") && !d.isNull("canvasBgOpacity")) d.optDouble("canvasBgOpacity") else 0.25,
                canvasImages = canvasImages,
                audioAttachments = audioAttachments
            )
        } finally {
            extractDir.deleteRecursively()
        }
    }

    private fun writeDiaryZipArchive(log: DiaryLog, targetZipFile: File): JSONObject {
        val inlineImagesMeta = JSONArray()
        val canvasImagesMeta = JSONArray()
        val audioMeta = JSONArray()
        var pfpPath: String? = null
        var canvasBgPath: String? = null

        ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZipFile), IO_BUFFER_SIZE)).use { zos ->
            // 1. PFP
            if (!log.pfpDataUrl.isNullOrBlank()) {
                resolveImageSourceForZip(log.pfpDataUrl, "png")?.let { src ->
                    pfpPath = "images/pfp.${src.ext}"
                    writeZipEntryFromSource(zos, pfpPath!!, src)
                }
            }

            // 2. Canvas Background
            if (!log.canvasBgDataUrl.isNullOrBlank()) {
                resolveImageSourceForZip(log.canvasBgDataUrl, "png")?.let { src ->
                    canvasBgPath = "images/canvas_bg.${src.ext}"
                    writeZipEntryFromSource(zos, canvasBgPath!!, src)
                }
            }

            // 3. Inline Images inside contentHtml
            val tokenizedHtml = extractAndTokenizeInlineImages(log.contentHtml) { index, rawSrc, alt ->
                val src = resolveImageSourceForZip(rawSrc, "png") ?: return@extractAndTokenizeInlineImages null
                val placeholderId = "__LIKKHO_INLINE_IMG_${index + 1}__"
                val archivePath = "images/inline_${index + 1}.${src.ext}"
                writeZipEntryFromSource(zos, archivePath, src)
                inlineImagesMeta.put(
                    JSONObject().apply {
                        put("placeholderId", placeholderId)
                        put("archivePath", archivePath)
                        put("mimeType", src.mimeType)
                        put("alt", alt.ifEmpty { "inline_${index + 1}" })
                    }
                )
                placeholderId
            }

            // 4. Floating / Layered Canvas Images
            log.canvasImages.forEachIndexed { idx, img ->
                val src = resolveImageSourceForZip(img.dataUrl, "png")
                if (src != null) {
                    val archivePath = "images/canvas_img_${idx + 1}.${src.ext}"
                    writeZipEntryFromSource(zos, archivePath, src)
                    canvasImagesMeta.put(
                        JSONObject().apply {
                            put("id", img.id)
                            put("archivePath", archivePath)
                            put("mimeType", src.mimeType)
                            put("x", img.x)
                            put("y", img.y)
                            put("width", img.width)
                            put("height", img.height)
                            put("opacity", img.opacity)
                            put("rotation", img.rotation)
                            put("layer", img.layer)
                        }
                    )
                } else {
                    canvasImagesMeta.put(
                        JSONObject().apply {
                            put("id", img.id)
                            put("archivePath", "")
                            put("mimeType", "image/png")
                            put("dataUrlFallback", img.dataUrl)
                            put("x", img.x)
                            put("y", img.y)
                            put("width", img.width)
                            put("height", img.height)
                            put("opacity", img.opacity)
                            put("rotation", img.rotation)
                            put("layer", img.layer)
                        }
                    )
                }
            }

            // 5. Audio Attachments
            log.audioAttachments.forEachIndexed { idx, aud ->
                val preferredExt = aud.format.lowercase().trim().removePrefix(".").ifEmpty { "wav" }
                val audSrc = resolveAudioSourceForZip(aud, preferredExt)
                if (audSrc != null) {
                    val archivePath = "audio/audio_${idx + 1}.${audSrc.ext}"
                    writeZipEntryFromSource(zos, archivePath, audSrc)
                    audioMeta.put(
                        JSONObject().apply {
                            put("id", aud.id)
                            put("name", aud.name)
                            put("format", aud.format)
                            put("archivePath", archivePath)
                            put("mimeType", audSrc.mimeType)
                            if (aud.durationSec != null) put("durationSec", aud.durationSec)
                            put("createdAt", aud.createdAt)
                            put("x", aud.x)
                            put("y", aud.y)
                            put("width", aud.width)
                            put("height", aud.height)
                            put("rotation", aud.rotation)
                            put("playbackRate", aud.playbackRate)
                            put("layer", aud.layer)
                        }
                    )
                } else {
                    audioMeta.put(
                        JSONObject().apply {
                            put("id", aud.id)
                            put("name", aud.name)
                            put("format", aud.format)
                            put("archivePath", "")
                            put("mimeType", "audio/wav")
                            put("dataUrlFallback", aud.dataUrl ?: "")
                            if (aud.durationSec != null) put("durationSec", aud.durationSec)
                            put("createdAt", aud.createdAt)
                            put("x", aud.x)
                            put("y", aud.y)
                            put("width", aud.width)
                            put("height", aud.height)
                            put("rotation", aud.rotation)
                            put("playbackRate", aud.playbackRate)
                            put("layer", aud.layer)
                        }
                    )
                }
            }

            // 6. diary.md
            val markdownText = convertHtmlToShareMarkdown(log)
            writeZipEntryBytes(zos, "diary.md", markdownText.toByteArray(StandardCharsets.UTF_8))

            // 7. manifest.json & manifest.json.gz
            val diaryObj = JSONObject().apply {
                put("id", log.id)
                put("heading", log.heading)
                put("contentHtml", tokenizedHtml)
                put("plainPreview", log.plainPreview)
                put("pfpPath", pfpPath ?: JSONObject.NULL)
                put("pfpDataUrlFallback", if (pfpPath != null) JSONObject.NULL else (log.pfpDataUrl ?: JSONObject.NULL))
                put("createdAt", log.createdAt)
                put("updatedAt", log.updatedAt)
                put("dateStamp", log.dateStamp)
                put("timeStamp", log.timeStamp)
                if (log.updatedDateStamp != null) put("updatedDateStamp", log.updatedDateStamp)
                if (log.updatedTimeStamp != null) put("updatedTimeStamp", log.updatedTimeStamp)
                put("pinned", log.pinned)
                put("diaryLockPin", log.diaryLockPin ?: JSONObject.NULL)
                put("canvasBgPath", canvasBgPath ?: JSONObject.NULL)
                put("canvasBgDataUrlFallback", if (canvasBgPath != null) JSONObject.NULL else (log.canvasBgDataUrl ?: JSONObject.NULL))
                put("canvasBgOpacity", log.canvasBgOpacity ?: 0.25)
                put("inlineImages", inlineImagesMeta)
                put("canvasImages", canvasImagesMeta)
                put("audioAttachments", audioMeta)
            }
            val manifestObj = JSONObject().apply {
                put("magic", "LIKKHO_SHARED_DIARY_ARCHIVE_V1")
                put("formatVersion", 1)
                put("sharedAt", System.currentTimeMillis())
                put("isLocked", !log.diaryLockPin.isNullOrEmpty())
                put("diary", diaryObj)
            }
            val manifestBytes = manifestObj.toString().toByteArray(StandardCharsets.UTF_8)
            val gzBytes = ByteArrayOutputStream().use { baos ->
                GZIPOutputStream(baos).use { it.write(manifestBytes) }
                baos.toByteArray()
            }
            writeZipEntryBytes(zos, "manifest.json.gz", gzBytes)
            writeZipEntryBytes(zos, "manifest.json", manifestBytes)
            zos.flush()
            return manifestObj
        }
    }

    // ==================== 2. Single-Diary Exports (PDF, HTML, MD, TXT, JSON, etc.) ====================

    fun exportDiaryToDownloads(log: DiaryLog, format: NativeExportFormat): ExportedDestinationResult {
        val baseSlug = sanitizeFileSlug(log.heading, "likkho_log")
        val filename = "$baseSlug.${format.ext}"
        val tempFile = File(appContext.cacheDir, "export_tmp_${System.nanoTime()}_$filename")
        try {
            BufferedOutputStream(FileOutputStream(tempFile), IO_BUFFER_SIZE).use { out ->
                writeDiaryExportToStream(log, format, out)
                out.flush()
            }
            return saveFileToDownloads(tempFile, filename, format.mimeType)
        } finally {
            tempFile.delete()
        }
    }

    fun exportDiaryToSafUri(log: DiaryLog, format: NativeExportFormat, destUri: Uri): ExportedDestinationResult {
        val baseSlug = sanitizeFileSlug(log.heading, "likkho_log")
        val filename = "$baseSlug.${format.ext}"
        val out = appContext.contentResolver.openOutputStream(destUri, "wt")
            ?: throw IOException("Unable to open SAF export destination.")
        BufferedOutputStream(out, IO_BUFFER_SIZE).use { buffered ->
            writeDiaryExportToStream(log, format, buffered)
            buffered.flush()
        }
        return ExportedDestinationResult(
            filename = filename,
            displayLocation = destUri.lastPathSegment ?: filename,
            sizeBytes = queryUriSize(appContext.contentResolver, destUri),
            uri = destUri
        )
    }

    fun writeDiaryExportToStream(log: DiaryLog, format: NativeExportFormat, out: OutputStream) {
        val plainText = htmlToPlainText(log.contentHtml)
        val dateLine = "${log.dateStamp} · ${log.timeStamp}"

        when (format) {
            NativeExportFormat.PDF -> writeMultiPageA4Pdf(log, out)
            NativeExportFormat.TXT -> {
                val text = "${log.heading}\n$dateLine\n${"=".repeat(40)}\n\n$plainText\n"
                out.write(text.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.MD -> {
                val md = htmlToSimpleMarkdown(log.heading, log.dateStamp, log.timeStamp, log.contentHtml)
                out.write(md.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.HTML -> {
                val html = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<title>${escapeXml(log.heading)}</title>
<style>
  body { font-family: Georgia, 'Times New Roman', serif; max-width: 720px; margin: 40px auto; padding: 0 20px; color: #202122; line-height: 1.7; }
  h1.title { border-bottom: 1px solid #a2a9b1; padding-bottom: 8px; margin-bottom: 4px; }
  .meta { color: #54595d; font-size: 0.85rem; margin-bottom: 24px; font-family: monospace; }
  blockquote { border-left: 3px solid #a2a9b1; padding: 8px 16px; background: #f8f9fa; font-style: italic; }
  a { color: #3366cc; }
</style>
</head>
<body>
  <h1 class="title">${escapeXml(log.heading)}</h1>
  <div class="meta">${escapeXml(dateLine)}</div>
  <div class="content">${log.contentHtml}</div>
</body>
</html>"""
                out.write(html.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.JSON -> {
                val json = JSONObject().apply {
                    put("app", "Likkho")
                    put("id", log.id)
                    put("heading", log.heading)
                    put("dateStamp", log.dateStamp)
                    put("timeStamp", log.timeStamp)
                    put("createdAt", log.createdAt)
                    put("updatedAt", log.updatedAt)
                    put("plainText", plainText)
                    put("contentHtml", log.contentHtml)
                }.toString(2)
                out.write(json.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.RTF -> {
                val rtf = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs32\\b ${escapeRtf(log.heading)}\\b0\\par\\fs20 ${escapeRtf(dateLine)}\\par\\par\\fs24 ${escapeRtf(plainText)}\\par}"
                out.write(rtf.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.CSV -> {
                fun escCsv(v: String) = "\"${v.replace("\"", "\"\"")}\""
                val csv = "id,heading,date,time,content\n" +
                    listOf(escCsv(log.id), escCsv(log.heading), escCsv(log.dateStamp), escCsv(log.timeStamp), escCsv(plainText)).joinToString(",") + "\n"
                out.write(csv.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.TSV -> {
                fun cleanTab(v: String) = v.replace(Regex("[\t\r\n]+"), " ")
                val tsv = "id\theading\tdate\ttime\tcontent\n" +
                    listOf(cleanTab(log.id), cleanTab(log.heading), cleanTab(log.dateStamp), cleanTab(log.timeStamp), cleanTab(plainText)).joinToString("\t") + "\n"
                out.write(tsv.toByteArray(StandardCharsets.UTF_8))
            }
            NativeExportFormat.XML -> {
                val xml = """<?xml version="1.0" encoding="UTF-8"?>
<likkhoEntry>
  <id>${escapeXml(log.id)}</id>
  <heading>${escapeXml(log.heading)}</heading>
  <dateStamp>${escapeXml(log.dateStamp)}</dateStamp>
  <timeStamp>${escapeXml(log.timeStamp)}</timeStamp>
  <plainText>${escapeXml(plainText)}</plainText>
  <contentHtml><![CDATA[${log.contentHtml}]]></contentHtml>
</likkhoEntry>"""
                out.write(xml.toByteArray(StandardCharsets.UTF_8))
            }
        }
        out.flush()
    }

    // ==================== 3. Multi-Page A4 PDF via Android PdfDocument ====================

    private sealed class PdfRenderBlock {
        data class TextBlock(
            val text: CharSequence,
            val textSizePt: Float,
            val typeface: Typeface,
            val color: Int,
            val indentLeft: Float = 0f,
            val isQuote: Boolean = false,
            val spacingAfter: Float = 8f,
            val drawUnderlineRule: Boolean = false
        ) : PdfRenderBlock()

        data class DividerBlock(val spacing: Float = 12f) : PdfRenderBlock()

        data class InlineImageBlock(
            val mediaRef: String,
            val caption: String? = null
        ) : PdfRenderBlock()
    }

    fun writeMultiPageA4Pdf(log: DiaryLog, out: OutputStream) {
        val pageWidth = A4_WIDTH_PT
        val pageHeight = A4_HEIGHT_PT
        val marginSide = 32f
        val contentWidth = (pageWidth - marginSide * 2f).roundToInt()
        val headerHeight = 84f
        val footerReserve = 26f
        val topContinuationPad = 28f

        val pdf = PdfDocument()
        val bgBitmap = log.canvasBgDataUrl?.let { mediaEngine.decodeBoundedBitmapForExport(it, 1024) }
        val pfpBitmap = log.pfpDataUrl?.let { mediaEngine.decodeBoundedBitmapForExport(it, 256) }

        val bgAlpha = ((log.canvasBgOpacity ?: 0.25).coerceIn(0.05, 1.0) * 255.0).roundToInt()
        val blocks = parseHtmlIntoPdfBlocks(log.contentHtml)

        var pageIndex = 1
        var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
        var foregroundDrawnOnFirstPage = false

        fun drawUnifiedPageBackground(canvas: Canvas, topY: Float, height: Float) {
            if (bgBitmap == null) return
            canvas.save()
            canvas.clipRect(0f, topY, pageWidth.toFloat(), topY + height)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = bgAlpha }
            val imgRatio = bgBitmap.width.toFloat() / max(1, bgBitmap.height).toFloat()
            val areaRatio = pageWidth.toFloat() / max(1f, height)
            val drawW: Float
            val drawH: Float
            val drawX: Float
            val drawY: Float
            if (imgRatio > areaRatio) {
                drawH = height
                drawW = drawH * imgRatio
                drawX = (pageWidth - drawW) / 2f
                drawY = topY
            } else {
                drawW = pageWidth.toFloat()
                drawH = drawW / imgRatio
                drawX = 0f
                drawY = topY + (height - drawH) / 2f
            }
            canvas.drawBitmap(bgBitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), paint)
            canvas.restore()
        }

        fun drawFirstPageHeader(canvas: Canvas) {
            val headerPaint = Paint().apply { color = Color.parseColor("#F8F9FA") }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, headerPaint)
            val borderPaint = Paint().apply {
                color = Color.parseColor("#A2A9B1")
                strokeWidth = 1f
            }
            canvas.drawLine(0f, headerHeight, pageWidth.toFloat(), headerHeight, borderPaint)

            var titleX = marginSide
            if (pfpBitmap != null) {
                val pfpRect = RectF(marginSide, 16f, marginSide + 52f, 68f)
                canvas.drawBitmap(pfpBitmap, null, pfpRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                val framePaint = Paint().apply {
                    color = Color.parseColor("#A2A9B1")
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                canvas.drawRect(pfpRect, framePaint)
                titleX = marginSide + 64f
            }

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#202122")
                textSize = 19f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val titleAvailW = (pageWidth - marginSide - titleX).roundToInt().coerceAtLeast(100)
            val titleLayout = buildStaticLayout(log.heading.ifBlank { "Untitled Entry" }, titlePaint, titleAvailW)
            canvas.save()
            canvas.translate(titleX, 18f)
            titleLayout.draw(canvas)
            canvas.restore()

            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#54595D")
                textSize = 9.5f
                typeface = Typeface.MONOSPACE
            }
            val createdLine = "Created: ${log.dateStamp} · ${log.timeStamp}"
            val modifiedLine = if (!log.updatedDateStamp.isNullOrBlank() && !log.updatedTimeStamp.isNullOrBlank()) {
                "   |   Modified: ${log.updatedDateStamp} · ${log.updatedTimeStamp}"
            } else {
                ""
            }
            canvas.drawText(createdLine + modifiedLine, titleX, 64f, metaPaint)
        }

        fun drawPageFooter(canvas: Canvas, pageNum: Int) {
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#54595D")
                textSize = 9f
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("Page $pageNum", pageWidth - marginSide, pageHeight - 10f, footerPaint)
        }

        fun startNextPage(): Float {
            if (!foregroundDrawnOnFirstPage && pageIndex == 1) {
                drawPositionedMediaForPdf(
                    canvas = currentPage.canvas,
                    log = log,
                    originY = headerHeight,
                    maxWidth = pageWidth.toFloat(),
                    layerFilter = "foreground"
                )
                foregroundDrawnOnFirstPage = true
            }
            drawPageFooter(currentPage.canvas, pageIndex)
            pdf.finishPage(currentPage)
            pageIndex++
            currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
            val c = currentPage.canvas
            c.drawColor(Color.WHITE)
            drawUnifiedPageBackground(c, 0f, pageHeight.toFloat())
            return topContinuationPad
        }

        try {
            // Initialize Page 1
            currentPage.canvas.drawColor(Color.WHITE)
            drawUnifiedPageBackground(currentPage.canvas, headerHeight, pageHeight - headerHeight)
            drawFirstPageHeader(currentPage.canvas)

            var cursorY = headerHeight + 20f
            val maxPageBottom = pageHeight - footerReserve

            // Draw background-layer canvas images & audio cards at scaled coordinates on Page 1
            drawPositionedMediaForPdf(
                canvas = currentPage.canvas,
                log = log,
                originY = headerHeight,
                maxWidth = pageWidth.toFloat(),
                layerFilter = "background"
            )

            for (block in blocks) {
                when (block) {
                    is PdfRenderBlock.DividerBlock -> {
                        if (cursorY + 14f > maxPageBottom) {
                            cursorY = startNextPage()
                        }
                        cursorY += 4f
                        val hrPaint = Paint().apply {
                            color = Color.parseColor("#A2A9B1")
                            strokeWidth = 0.8f
                        }
                        currentPage.canvas.drawLine(marginSide, cursorY, pageWidth - marginSide, cursorY, hrPaint)
                        cursorY += block.spacing
                    }

                    is PdfRenderBlock.InlineImageBlock -> {
                        val bmp = mediaEngine.decodeBoundedBitmapForExport(block.mediaRef, 900)
                        if (bmp != null) {
                            try {
                                val targetW = min(contentWidth.toFloat(), 320f)
                                val aspect = bmp.height.toFloat() / max(1, bmp.width).toFloat()
                                val targetH = (targetW * aspect).coerceAtMost(maxPageBottom - topContinuationPad - 24f)
                                if (cursorY + targetH > maxPageBottom) {
                                    cursorY = startNextPage()
                                }
                                val dest = RectF(marginSide, cursorY, marginSide + targetW, cursorY + targetH)
                                currentPage.canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                cursorY += targetH + 8f
                            } finally {
                                bmp.recycle()
                            }
                        }
                        if (!block.caption.isNullOrBlank()) {
                            val capPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#54595D")
                                textSize = 10f
                                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                            }
                            val capLayout = buildStaticLayout(block.caption, capPaint, contentWidth)
                            if (cursorY + capLayout.height > maxPageBottom) {
                                cursorY = startNextPage()
                            }
                            currentPage.canvas.save()
                            currentPage.canvas.translate(marginSide, cursorY)
                            capLayout.draw(currentPage.canvas)
                            currentPage.canvas.restore()
                            cursorY += capLayout.height + 8f
                        }
                    }

                    is PdfRenderBlock.TextBlock -> {
                        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = block.color
                            textSize = block.textSizePt
                            typeface = block.typeface
                        }
                        val availWidth = (contentWidth - block.indentLeft).roundToInt().coerceAtLeast(100)
                        val layout = buildStaticLayout(block.text, tp, availWidth)

                        // Line-by-line pagination so NO line is ever cut in half across A4 page borders!
                        var startLine = 0
                        while (startLine < layout.lineCount) {
                            val availHeight = maxPageBottom - cursorY
                            var endLine = startLine
                            while (endLine < layout.lineCount) {
                                val sliceBottom = layout.getLineBottom(endLine) - layout.getLineTop(startLine)
                                if (sliceBottom <= availHeight) {
                                    endLine++
                                } else {
                                    break
                                }
                            }
                            if (endLine == startLine) {
                                cursorY = startNextPage()
                                continue
                            }

                            val topOffset = layout.getLineTop(startLine).toFloat()
                            val bottomOffset = layout.getLineBottom(endLine - 1).toFloat()
                            val sliceHeight = bottomOffset - topOffset

                            val c = currentPage.canvas
                            if (block.isQuote) {
                                val qBg = Paint().apply { color = Color.parseColor("#F8F9FA") }
                                c.drawRect(marginSide + 6f, cursorY - 2f, pageWidth - marginSide, cursorY + sliceHeight + 2f, qBg)
                                val qBar = Paint().apply {
                                    color = Color.parseColor("#A2A9B1")
                                    strokeWidth = 2.5f
                                }
                                c.drawLine(marginSide + 6f, cursorY - 2f, marginSide + 6f, cursorY + sliceHeight + 2f, qBar)
                            }

                            c.save()
                            c.clipRect(
                                marginSide + block.indentLeft,
                                cursorY,
                                pageWidth - marginSide,
                                cursorY + sliceHeight
                            )
                            c.translate(marginSide + block.indentLeft, cursorY - topOffset)
                            layout.draw(c)
                            c.restore()

                            cursorY += sliceHeight
                            startLine = endLine

                            if (startLine < layout.lineCount) {
                                cursorY = startNextPage()
                            }
                        }

                        if (block.drawUnderlineRule) {
                            val rulePaint = Paint().apply {
                                color = Color.parseColor("#EAECF0")
                                strokeWidth = 0.8f
                            }
                            currentPage.canvas.drawLine(marginSide, cursorY + 2f, pageWidth - marginSide, cursorY + 2f, rulePaint)
                            cursorY += 4f
                        }
                        cursorY += block.spacingAfter
                    }
                }
            }

            // Draw foreground-layer canvas images & audio cards if still on Page 1
            if (!foregroundDrawnOnFirstPage && pageIndex == 1) {
                drawPositionedMediaForPdf(
                    canvas = currentPage.canvas,
                    log = log,
                    originY = headerHeight,
                    maxWidth = pageWidth.toFloat(),
                    layerFilter = "foreground"
                )
                foregroundDrawnOnFirstPage = true
            }

            if (pageIndex > 1) {
                drawPageFooter(currentPage.canvas, pageIndex)
            }

            pdf.finishPage(currentPage)
            pdf.writeTo(out)
            out.flush()
        } finally {
            pdf.close()
            bgBitmap?.recycle()
            pfpBitmap?.recycle()
        }
    }

    private fun drawPositionedMediaForPdf(
        canvas: Canvas,
        log: DiaryLog,
        originY: Float,
        maxWidth: Float,
        layerFilter: String
    ) {
        val scaleFactor = maxWidth / 768f

        for (im in log.canvasImages) {
            val isBg = im.layer == "background"
            if ((layerFilter == "background") != isBg) continue
            val bmp = mediaEngine.decodeBoundedBitmapForExport(im.dataUrl, 900) ?: continue
            try {
                val w = (im.width.toFloat().coerceAtLeast(40f)) * scaleFactor
                val aspect = bmp.height.toFloat() / max(1, bmp.width).toFloat()
                val h = w * aspect
                val x = (im.x.toFloat()) * scaleFactor
                val y = originY + (im.y.toFloat()) * scaleFactor

                canvas.save()
                canvas.translate(x + w / 2f, y + h / 2f)
                canvas.rotate(im.rotation.toFloat())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (im.opacity.toFloat().coerceIn(0.05f, 1f) * 255f).roundToInt()
                }
                canvas.drawBitmap(bmp, null, RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), paint)
                canvas.restore()
            } finally {
                bmp.recycle()
            }
        }

        log.audioAttachments.forEachIndexed { idx, aud ->
            val isBg = aud.layer == "background"
            if ((layerFilter == "background") != isBg) return@forEachIndexed
            val w = (aud.width.toFloat().coerceAtLeast(140f)) * scaleFactor
            val h = (aud.height.toFloat().coerceAtLeast(44f)) * scaleFactor
            val x = (aud.x.toFloat()) * scaleFactor
            val y = originY + (aud.y.toFloat()) * scaleFactor
            if (y > A4_HEIGHT_PT - 20f) return@forEachIndexed

            canvas.save()
            canvas.translate(x + w / 2f, y + h / 2f)
            canvas.rotate(aud.rotation.toFloat())

            val bodyPaint = Paint().apply { color = Color.parseColor("#B32424") }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#7A1616")
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            val cardRect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
            canvas.drawRect(cardRect, bodyPaint)
            canvas.drawRect(cardRect, borderPaint)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 255, 255, 255)
                textSize = 7.5f
                typeface = Typeface.MONOSPACE
            }
            val durStr = formatAudioSeconds(aud.durationSec)
            val stamp = formatAudioStamp(aud.createdAt)
            canvas.drawText("▶ ${aud.name.take(24)} ($durStr)", -w / 2f + 10f, -2f, titlePaint)
            canvas.drawText(stamp, -w / 2f + 10f, 12f, subPaint)
            canvas.restore()
        }
    }

    // ==================== 4. Full-Vault ZIP Exporter (`zipDataExporter.ts` Parity) ====================

    fun exportAllVaultDataAsZipToDownloads(vaultMode: VaultMode): ExportedDestinationResult {
        val dateTag = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val filename = "likkho_all_data_$dateTag.zip"
        val tempZip = File(appContext.cacheDir, "zip_export_${System.nanoTime()}.zip")
        try {
            BufferedOutputStream(FileOutputStream(tempZip), IO_BUFFER_SIZE).use { out ->
                writeFullVaultZipToStream(vaultMode, out)
                out.flush()
            }
            return saveFileToDownloads(tempZip, filename, "application/zip")
        } finally {
            tempZip.delete()
        }
    }

    fun exportAllVaultDataAsZipToSafUri(vaultMode: VaultMode, destUri: Uri): ExportedDestinationResult {
        val dateTag = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val filename = "likkho_all_data_$dateTag.zip"
        val out = appContext.contentResolver.openOutputStream(destUri, "wt")
            ?: throw IOException("Unable to open SAF ZIP destination.")
        BufferedOutputStream(out, IO_BUFFER_SIZE).use { buffered ->
            writeFullVaultZipToStream(vaultMode, buffered)
            buffered.flush()
        }
        return ExportedDestinationResult(
            filename = filename,
            displayLocation = destUri.lastPathSegment ?: filename,
            sizeBytes = queryUriSize(appContext.contentResolver, destUri),
            uri = destUri
        )
    }

    fun writeFullVaultZipToStream(vaultMode: VaultMode, out: OutputStream) {
        val logs = repository.loadLogs(vaultMode)
        val quickNotes = repository.loadQuickNotes(vaultMode)
        val trackers = repository.loadTrackers(vaultMode)
        val reminders = repository.loadReminders(vaultMode)
        val documents = repository.loadDocuments(vaultMode)
        val savedPasswords = repository.loadSavedPasswords(vaultMode)
        var exportedDocFiles = 0

        ZipOutputStream(BufferedOutputStream(out, IO_BUFFER_SIZE)).use { zos ->
            // 1. Entries (when present, e.g., Secret Writing vault)
            logs.forEachIndexed { idx, log ->
                val numPrefix = (idx + 1).toString().padStart(2, '0')
                val slug = "${numPrefix}_${sanitizeFileSlug(log.heading, "entry_${idx + 1}")}"
                writeDiaryMarkdownAndMediaToZip(zos, log, slug)
            }

            // 2. Quick Notes & Checklists
            if (quickNotes.isNotEmpty()) {
                val sb = StringBuilder("# Quick Notes & Checklists\n\n")
                quickNotes.forEachIndexed { idx, note ->
                    val stamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                        .format(Date(max(note.updatedAt, note.createdAt)))
                    sb.append("## ").append(idx + 1).append(". ").append(note.title.ifBlank { "Untitled Note" })
                    if (note.isDone) sb.append(" [Done]")
                    sb.append("\n")
                    sb.append("- **Updated:** ").append(stamp).append("\n")
                    if (note.isChecklist && note.items.isNotEmpty()) {
                        note.items.forEach { item ->
                            val mark = if (item.checked) "x" else " "
                            sb.append("  - [").append(mark).append("] ").append(item.text).append("\n")
                        }
                    } else if (note.content.isNotBlank()) {
                        sb.append("\n").append(note.content.trim()).append("\n")
                    }
                    sb.append("\n")
                }
                writeZipEntryBytes(zos, "notes/quick_notes.md", sb.toString().toByteArray(StandardCharsets.UTF_8))
            }

            // 3. Recurring Trackers (Calendar Hisaab)
            if (trackers.isNotEmpty()) {
                val sb = StringBuilder("# Recurring Trackers\n\n")
                val jsonArr = JSONArray()
                trackers.forEachIndexed { idx, tracker ->
                    jsonArr.put(tracker.toJson())
                    sb.append("## ").append(idx + 1).append(". ").append(tracker.name.ifBlank { "Tracker" })
                    if (tracker.archived) sb.append(" (Archived)")
                    sb.append("\n")
                    if (tracker.unitLabel.isNotBlank()) {
                        sb.append("- **Unit:** ").append(tracker.defaultQuantity).append(" ").append(tracker.unitLabel).append("\n")
                    }
                    if (tracker.ratePerUnit > 0.0) {
                        sb.append("- **Rate:** ").append(tracker.currencySymbol).append(tracker.ratePerUnit).append("\n")
                    }
                    if (tracker.notes.isNotBlank()) {
                        sb.append("- **Notes:** ").append(tracker.notes).append("\n")
                    }
                    if (tracker.entries.isNotEmpty()) {
                        sb.append("- **Logged Days:** ").append(tracker.entries.size).append("\n")
                        tracker.entries.entries.sortedBy { it.key }.forEach { (dateKey, entry) ->
                            val qtyPart = entry.quantity?.let { " · qty $it" }.orEmpty()
                            val notePart = if (entry.note.isNotBlank()) " · ${entry.note}" else ""
                            sb.append("  - `").append(dateKey).append("`: ").append(entry.status.wireValue)
                                .append(qtyPart).append(notePart).append("\n")
                        }
                    }
                    sb.append("\n")
                }
                writeZipEntryBytes(zos, "trackers/trackers.md", sb.toString().toByteArray(StandardCharsets.UTF_8))
                writeZipEntryBytes(zos, "trackers/trackers.json", jsonArr.toString(2).toByteArray(StandardCharsets.UTF_8))
            }

            // 4. Reminders
            if (reminders.isNotEmpty()) {
                val sb = StringBuilder("# Reminders\n\n")
                reminders.forEachIndexed { idx, rem ->
                    val dueStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(rem.triggerAtMs))
                    val statusTag = if (rem.isCompleted) "Completed" else "Upcoming"
                    sb.append("## ").append(idx + 1).append(". ").append(rem.title.ifBlank { "Reminder" }).append("\n")
                    sb.append("- **Status:** ").append(statusTag).append("\n")
                    sb.append("- **Scheduled:** ").append(dueStr).append("\n")
                    sb.append("- **Repeat:** ").append(rem.repeat.displayLabel).append("\n")
                    if (rem.note.isNotBlank()) {
                        sb.append("- **Note:** ").append(rem.note).append("\n")
                    }
                    sb.append("\n")
                }
                writeZipEntryBytes(zos, "reminders/reminders.md", sb.toString().toByteArray(StandardCharsets.UTF_8))
            }

            // 5. Personal Documents & Attached Files
            if (documents.isNotEmpty()) {
                val sb = StringBuilder("# Personal Documents\n\n")
                val usedFileNames = HashSet<String>()
                documents.forEachIndexed { idx, doc ->
                    sb.append("## ").append(idx + 1).append(". ").append(doc.title.ifBlank { "Document" }).append("\n")
                    sb.append("- **Category:** ").append(doc.category.displayLabel).append("\n")
                    if (doc.idNumber.isNotBlank()) sb.append("- **ID / Number:** ").append(doc.idNumber).append("\n")
                    if (doc.issuedDate.isNotBlank()) sb.append("- **Issued Date:** ").append(doc.issuedDate).append("\n")
                    if (doc.expiryDate.isNotBlank()) sb.append("- **Expiry Date:** ").append(doc.expiryDate).append("\n")
                    if (doc.notes.isNotBlank()) sb.append("- **Notes:** ").append(doc.notes).append("\n")

                    val docFileId = doc.fileId
                    val docFile = if (!docFileId.isNullOrBlank()) repository.resolveExistingDocumentFile(docFileId) else null
                    if (docFile != null && docFile.exists() && docFile.length() > 0L) {
                        val fallbackExt = when {
                            doc.mimeType.contains("pdf", ignoreCase = true) -> "pdf"
                            doc.mimeType.contains("png", ignoreCase = true) -> "png"
                            doc.mimeType.contains("webp", ignoreCase = true) -> "webp"
                            else -> "jpg"
                        }
                        val rawBase = doc.fileName.substringBeforeLast('.', doc.title.ifBlank { "document_${idx + 1}" })
                        val rawExt = doc.fileName.substringAfterLast('.', fallbackExt).lowercase(Locale.US)
                            .replace(Regex("[^a-z0-9]"), "").ifBlank { fallbackExt }
                        var candidateName = "${idx + 1}_${sanitizeFileSlug(rawBase, "doc_${idx + 1}")}.$rawExt"
                        while (!usedFileNames.add(candidateName)) {
                            candidateName = "${idx + 1}_${System.nanoTime()}.$rawExt"
                        }
                        val archivePath = "documents/files/$candidateName"
                        FileInputStream(docFile).use { inp ->
                            zos.putNextEntry(ZipEntry(archivePath))
                            copyStreamBuffered(inp, zos)
                            zos.closeEntry()
                        }
                        exportedDocFiles++
                        sb.append("- **File:** `").append(archivePath).append("`\n")
                    }
                    sb.append("\n")
                }
                writeZipEntryBytes(zos, "documents/documents_index.md", sb.toString().toByteArray(StandardCharsets.UTF_8))
            }

            // 6. Saved Passwords
            if (savedPasswords.isNotEmpty()) {
                val sb = StringBuilder("# Saved Passwords\n\n")
                savedPasswords.forEachIndexed { idx, p ->
                    val stamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                        .format(Date(max(p.updatedAt, p.createdAt)))
                    sb.append("### ").append(idx + 1).append(". ").append(p.name.ifBlank { "Untitled" }).append("\n")
                    if (p.username.isNotBlank()) sb.append("- **Username / Email:** ").append(p.username).append("\n")
                    sb.append("- **Password:** ").append(p.password).append("\n")
                    if (p.url.isNotBlank()) sb.append("- **URL:** ").append(p.url).append("\n")
                    if (p.notes.isNotBlank()) sb.append("- **Notes:** ").append(p.notes).append("\n")
                    sb.append("- **Updated:** ").append(stamp).append("\n\n")
                }
                writeZipEntryBytes(
                    zos,
                    "passwords/saved_passwords.md",
                    sb.toString().toByteArray(StandardCharsets.UTF_8)
                )
            }

            // 7. README.md
            val exportedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
            val readme = buildString {
                append("# Likkho Data Export Archive\n\n")
                append("- **Exported At:** ").append(exportedDate).append("\n")
                if (vaultMode == VaultMode.DECOY || logs.isNotEmpty()) {
                    append("- **Journal Entries:** ").append(logs.size).append("\n")
                }
                if (quickNotes.isNotEmpty()) append("- **Notes & Checklists:** ").append(quickNotes.size).append("\n")
                if (trackers.isNotEmpty()) append("- **Recurring Trackers:** ").append(trackers.size).append("\n")
                if (reminders.isNotEmpty()) append("- **Reminders:** ").append(reminders.size).append("\n")
                if (documents.isNotEmpty()) append("- **Documents:** ").append(documents.size).append(" (").append(exportedDocFiles).append(" files)\n")
                if (savedPasswords.isNotEmpty()) append("- **Saved Passwords:** ").append(savedPasswords.size).append("\n")
                append("\n## Folder Structure\n")
                if (logs.isNotEmpty()) {
                    append("- `diaries/` — Journal entries in Markdown (`.md`) format.\n")
                    append("- `images/` — Attached photos & backgrounds.\n")
                    append("- `audio/` — Attached voice notes.\n")
                }
                if (quickNotes.isNotEmpty()) append("- `notes/` — Quick Notes & Checklists.\n")
                if (trackers.isNotEmpty()) append("- `trackers/` — Recurring Trackers & history.\n")
                if (reminders.isNotEmpty()) append("- `reminders/` — Scheduled & completed reminders.\n")
                if (documents.isNotEmpty()) append("- `documents/` — Document metadata & attached files.\n")
                if (savedPasswords.isNotEmpty()) append("- `passwords/` — Saved credentials.\n")
            }
            writeZipEntryBytes(zos, "README.md", readme.toByteArray(StandardCharsets.UTF_8))
            zos.flush()
        }
    }

    private fun writeDiaryMarkdownAndMediaToZip(
        zos: ZipOutputStream,
        log: DiaryLog,
        entrySlug: String
    ) {
        val md = StringBuilder()
        md.append("# ").append(log.heading.ifBlank { "Untitled Entry" }).append("\n\n")
        md.append("- **Created:** ").append(log.dateStamp).append(" · ").append(log.timeStamp).append("\n")
        if (!log.updatedDateStamp.isNullOrBlank() && !log.updatedTimeStamp.isNullOrBlank()) {
            md.append("- **Modified:** ").append(log.updatedDateStamp).append(" · ").append(log.updatedTimeStamp).append("\n")
        }
        if (log.pinned) {
            md.append("- **Pinned:** Yes\n")
        }
        md.append("\n---\n\n")

        if (!log.pfpDataUrl.isNullOrBlank()) {
            resolveImageSourceForZip(log.pfpDataUrl, "png")?.let { src ->
                val pfpPath = "images/${entrySlug}_pfp.${src.ext}"
                writeZipEntryFromSource(zos, pfpPath, src)
                md.append("![Entry Profile Picture](../$pfpPath)\n\n")
            }
        }

        if (!log.canvasBgDataUrl.isNullOrBlank()) {
            resolveImageSourceForZip(log.canvasBgDataUrl, "png")?.let { src ->
                val bgPath = "images/${entrySlug}_canvas_bg.${src.ext}"
                writeZipEntryFromSource(zos, bgPath, src)
                val opacityPct = ((log.canvasBgOpacity ?: 0.25) * 100.0).roundToInt()
                md.append("> **Canvas Background Image:** [../$bgPath](../$bgPath) (Opacity: $opacityPct%)\n\n")
            }
        }

        // Inline images inside HTML
        val mdHtml = extractAndTokenizeInlineImagesWithMarkdown(log.contentHtml) { idx, rawSrc, alt ->
            val src = resolveImageSourceForZip(rawSrc, "png")
            if (src != null) {
                val inlinePath = "images/${entrySlug}_inline_${idx + 1}.${src.ext}"
                writeZipEntryFromSource(zos, inlinePath, src)
                "\n\n![$alt](../$inlinePath)\n\n"
            } else if (rawSrc.isNotBlank()) {
                "\n\n![$alt]($rawSrc)\n\n"
            } else {
                ""
            }
        }
        md.append(htmlBodyToMarkdownText(mdHtml)).append("\n")

        if (log.canvasImages.isNotEmpty()) {
            md.append("\n\n## Attached Canvas Images\n\n")
            log.canvasImages.forEachIndexed { idx, cImg ->
                resolveImageSourceForZip(cImg.dataUrl, "png")?.let { src ->
                    val imgPath = "images/${entrySlug}_image_${idx + 1}.${src.ext}"
                    writeZipEntryFromSource(zos, imgPath, src)
                    md.append("- ![Canvas Image ${idx + 1}](../$imgPath) — `$imgPath` (Layer: ${cImg.layer}, Width: ${cImg.width.roundToInt()}px, Rotation: ${cImg.rotation.roundToInt()}°)\n")
                }
            }
        }

        if (log.audioAttachments.isNotEmpty()) {
            md.append("\n\n## Attached Audio Recordings\n\n")
            log.audioAttachments.forEachIndexed { idx, aud ->
                val preferredExt = aud.format.lowercase().trim().removePrefix(".").ifEmpty { "wav" }
                resolveAudioSourceForZip(aud, preferredExt)?.let { src ->
                    val audSlug = sanitizeFileSlug(aud.name.substringBeforeLast('.'), "audio_${idx + 1}")
                    val audPath = "audio/${entrySlug}_${idx + 1}_$audSlug.${src.ext}"
                    writeZipEntryFromSource(zos, audPath, src)
                    md.append("- **${aud.name}** — [../$audPath](../$audPath)\n")
                }
            }
        }

        writeZipEntryBytes(zos, "diaries/$entrySlug.md", md.toString().toByteArray(StandardCharsets.UTF_8))
    }

    // ==================== 5. Downloads / SAF File Saving ====================

    fun saveFileToDownloads(
        sourceFile: File,
        filename: String,
        mimeType: String
    ): ExportedDestinationResult {
        val safeName = filename.replace(Regex("[\\\\/:*?\"<>|]+"), "_").ifBlank { "likkho_export" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Likkho")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create export file in Downloads/Likkho.")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { inp ->
                        copyStreamBuffered(inp, out)
                    }
                    out.flush()
                } ?: throw IOException("Unable to write to Downloads/Likkho/$safeName")

                val publish = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
                return ExportedDestinationResult(
                    filename = safeName,
                    displayLocation = "Downloads/Likkho/$safeName",
                    sizeBytes = sourceFile.length(),
                    uri = uri
                )
            } catch (err: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw err
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val likkhoDir = File(downloadsDir, "Likkho").apply { if (!exists()) mkdirs() }
            val destFile = File(likkhoDir, safeName)
            copyFileAtomic(sourceFile, destFile)
            return ExportedDestinationResult(
                filename = safeName,
                displayLocation = "Downloads/Likkho/$safeName",
                sizeBytes = destFile.length(),
                localFile = destFile
            )
        }
    }

    // ==================== Universal `.likkho` Crypto Helpers (`LKSHRE01`) ====================

    private fun deriveUniversalShareAesKey(salt: ByteArray): SecretKey {
        val keyInput = "$LIKKHO_UNIVERSAL_SHARE_KDF_CONTEXT$LIKKHO_UNIVERSAL_SHARE_KEY_SECRET"
            .toByteArray(StandardCharsets.UTF_8)
        val rawKey = NativeVaultRepository.pbkdf2Hmac(
            passwordBytes = keyInput,
            salt = salt,
            iterations = LIKKHO_SHARE_KDF_ITERATIONS,
            keyLengthBytes = 32,
            hmacAlgorithm = "HmacSHA256"
        )
        return try {
            SecretKeySpec(rawKey, "AES")
        } finally {
            Arrays.fill(rawKey, 0.toByte())
        }
    }

    private fun encryptWithUniversalLikkhoShareKey(plainArchiveBytes: ByteArray): ByteArray {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val aesKey = deriveUniversalShareAesKey(salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainArchiveBytes)

        val out = ByteArray(LIKKHO_UNIVERSAL_SHARE_MAGIC.size + salt.size + iv.size + cipherBytes.size)
        var offset = 0
        System.arraycopy(LIKKHO_UNIVERSAL_SHARE_MAGIC, 0, out, offset, LIKKHO_UNIVERSAL_SHARE_MAGIC.size)
        offset += LIKKHO_UNIVERSAL_SHARE_MAGIC.size
        System.arraycopy(salt, 0, out, offset, salt.size)
        offset += salt.size
        System.arraycopy(iv, 0, out, offset, iv.size)
        offset += iv.size
        System.arraycopy(cipherBytes, 0, out, offset, cipherBytes.size)
        return out
    }

    private fun hasUniversalLikkhoShareMagic(bytes: ByteArray): Boolean {
        if (bytes.size < LIKKHO_UNIVERSAL_SHARE_MAGIC.size + 16 + 12 + 16) return false
        for (i in LIKKHO_UNIVERSAL_SHARE_MAGIC.indices) {
            if (bytes[i] != LIKKHO_UNIVERSAL_SHARE_MAGIC[i]) return false
        }
        return true
    }

    private fun decryptWithUniversalLikkhoShareKey(encryptedBytes: ByteArray): ByteArray {
        val magicLen = LIKKHO_UNIVERSAL_SHARE_MAGIC.size
        val salt = encryptedBytes.copyOfRange(magicLen, magicLen + 16)
        val iv = encryptedBytes.copyOfRange(magicLen + 16, magicLen + 28)
        val cipherBytes = encryptedBytes.copyOfRange(magicLen + 28, encryptedBytes.size)

        val aesKey = deriveUniversalShareAesKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(cipherBytes)
        } catch (_: AEADBadTagException) {
            throw IOException("This .likkho file could not be decrypted by Likkho.")
        }
    }

    // ==================== Media & ZIP Streaming Helpers ====================

    private sealed class ZipBinarySource(val ext: String, val mimeType: String) {
        class FileSource(val file: File, ext: String, mimeType: String) : ZipBinarySource(ext, mimeType)
        class ByteSource(val bytes: ByteArray, ext: String, mimeType: String) : ZipBinarySource(ext, mimeType)
        class UriSource(val uri: Uri, ext: String, mimeType: String) : ZipBinarySource(ext, mimeType)
    }

    private fun resolveImageSourceForZip(refOrDataUrl: String, fallbackExt: String): ZipBinarySource? {
        if (refOrDataUrl.isBlank()) return null
        if (refOrDataUrl.startsWith("data:")) {
            val parsed = mediaEngine.parseDataUrl(refOrDataUrl) ?: return null
            return ZipBinarySource.ByteSource(parsed.bytes, parsed.ext, parsed.mimeType)
        }
        val file = repository.resolveExistingImageFile(refOrDataUrl) ?: return null
        val detected = detectImageExtAndMime(file, fallbackExt)
        return ZipBinarySource.FileSource(file, detected.first, detected.second)
    }

    private fun resolveAudioSourceForZip(aud: CanvasAudioAttachment, preferredExt: String): ZipBinarySource? {
        val audioFile = repository.resolveExistingAudioFile(aud.id, preferredExt)
            ?: aud.audioUri?.takeIf { it.isNotBlank() }?.let {
                repository.resolveExistingAudioFile(repository.normalizeMediaId(it), preferredExt)
            }
        if (audioFile != null && audioFile.exists() && audioFile.length() > 0L) {
            val actualExt = audioFile.extension.lowercase().ifBlank { preferredExt }
            return ZipBinarySource.FileSource(audioFile, actualExt, mimeForAudioExt(actualExt))
        }
        val rawUri = aud.audioUri?.takeIf { it.isNotBlank() }
        if (rawUri != null) {
            if (rawUri.startsWith("/") || rawUri.startsWith("file://")) {
                val path = if (rawUri.startsWith("file://")) Uri.parse(rawUri).path else rawUri
                if (!path.isNullOrBlank()) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0L) {
                        return ZipBinarySource.FileSource(f, preferredExt, mimeForAudioExt(preferredExt))
                    }
                }
            } else if (rawUri.startsWith("content://")) {
                return ZipBinarySource.UriSource(Uri.parse(rawUri), preferredExt, mimeForAudioExt(preferredExt))
            }
        }
        val dataUrl = aud.dataUrl?.takeIf { it.startsWith("data:") }
        if (dataUrl != null) {
            val parsed = mediaEngine.parseDataUrl(dataUrl)
            if (parsed != null) {
                return ZipBinarySource.ByteSource(parsed.bytes, parsed.ext, parsed.mimeType)
            }
        }
        return null
    }

    private fun writeZipEntryFromSource(zos: ZipOutputStream, path: String, source: ZipBinarySource) {
        val cleanPath = path.trimStart('/')
        zos.putNextEntry(ZipEntry(cleanPath))
        when (source) {
            is ZipBinarySource.ByteSource -> zos.write(source.bytes)
            is ZipBinarySource.FileSource -> FileInputStream(source.file).use { copyStreamBuffered(it, zos) }
            is ZipBinarySource.UriSource -> appContext.contentResolver.openInputStream(source.uri)?.use {
                copyStreamBuffered(it, zos)
            }
        }
        zos.closeEntry()
    }

    private fun writeZipEntryBytes(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path.trimStart('/')))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun persistExtractedImageFile(srcFile: File, prefix: String): String {
        val id = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(4)}"
        val destFile = repository.resolveConfinedFile(repository.mediaImagesDir, id)
        copyFileAtomic(srcFile, destFile)
        return id
    }

    private fun sanitizeZipEntryPath(rawName: String): String {
        val normalized = rawName.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty() || normalized.split('/').any { it == ".." || it.isEmpty() }) {
            throw SecurityException("Unsafe ZIP entry path blocked: $rawName")
        }
        return normalized
    }

    private fun resolveConfinedZipTarget(baseDir: File, relPath: String): File {
        val target = File(baseDir, relPath)
        val baseCanonical = baseDir.canonicalPath
        val targetCanonical = target.canonicalPath
        if (!targetCanonical.startsWith(baseCanonical + File.separator)) {
            throw SecurityException("ZIP Slip path traversal blocked: $relPath")
        }
        return target
    }

    private fun detectImageExtAndMime(file: File, fallbackExt: String): Pair<String, String> {
        val header = ByteArray(16)
        val read = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(0)
        if (read >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
            return "png" to "image/png"
        }
        if (read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
            return "jpg" to "image/jpeg"
        }
        if (read >= 12 && String(header, 0, 4, StandardCharsets.US_ASCII) == "RIFF" && String(header, 8, 4, StandardCharsets.US_ASCII) == "WEBP") {
            return "webp" to "image/webp"
        }
        if (read >= 6 && String(header, 0, 3, StandardCharsets.US_ASCII) == "GIF") {
            return "gif" to "image/gif"
        }
        val textProbe = String(header, 0, max(0, read), StandardCharsets.UTF_8).trimStart()
        if (textProbe.startsWith("<svg", ignoreCase = true) || textProbe.startsWith("<?xml", ignoreCase = true)) {
            return "svg" to "image/svg+xml"
        }
        val ext = file.extension.lowercase().ifEmpty { fallbackExt }
        val mime = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            else -> "image/jpeg"
        }
        return ext to mime
    }

    private fun mimeForAudioExt(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        else -> "audio/wav"
    }

    // ==================== HTML / Markdown / PDF Parsing Helpers ====================

    private fun extractAndTokenizeInlineImages(
        html: String,
        onImage: (index: Int, src: String, alt: String) -> String?
    ): String {
        if (html.isEmpty()) return html
        var idx = 0
        return IMG_TAG_REGEX.replace(html) { match ->
            val tag = match.value
            val src = SRC_ATTR_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: ""
            val alt = ALT_ATTR_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: ""
            val currentIdx = idx++
            val placeholderId = onImage(currentIdx, src, alt)
            if (placeholderId != null) {
                val withoutSrc = SRC_ATTR_REGEX.replace(tag, "")
                if (withoutSrc.endsWith("/>")) {
                    withoutSrc.dropLast(2) + " data-likkho-inline-id=\"$placeholderId\" />"
                } else {
                    withoutSrc.dropLast(1) + " data-likkho-inline-id=\"$placeholderId\">"
                }
            } else {
                tag
            }
        }
    }

    private fun extractAndTokenizeInlineImagesWithMarkdown(
        html: String,
        onImage: (index: Int, src: String, alt: String) -> String
    ): String {
        if (html.isEmpty()) return html
        var idx = 0
        return IMG_TAG_REGEX.replace(html) { match ->
            val tag = match.value
            val src = SRC_ATTR_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: ""
            val alt = ALT_ATTR_REGEX.find(tag)?.groupValues?.getOrNull(1) ?: "inline_image_${idx + 1}"
            val currentIdx = idx++
            onImage(currentIdx, src, alt)
        }
    }

    private fun replaceInlinePlaceholderInHtml(html: String, placeholderId: String, assetUrl: String): String {
        val attrRegex = Regex("""data-likkho-inline-id\s*=\s*["']${Regex.escape(placeholderId)}["']""")
        return attrRegex.replace(html, """src="$assetUrl"""")
    }

    private fun convertHtmlToShareMarkdown(log: DiaryLog): String {
        val sb = StringBuilder()
        sb.append("# ").append(log.heading.ifBlank { "Untitled Diary" }).append("\n\n")
        sb.append("- **Created:** ").append(log.dateStamp).append(" · ").append(log.timeStamp).append("\n")
        if (!log.updatedDateStamp.isNullOrBlank() && !log.updatedTimeStamp.isNullOrBlank()) {
            sb.append("- **Modified:** ").append(log.updatedDateStamp).append(" · ").append(log.updatedTimeStamp).append("\n")
        }
        if (!log.diaryLockPin.isNullOrBlank()) {
            sb.append("- **Locked Diary:** Yes (Passcode Protected)\n")
        }
        sb.append("\n---\n\n")
        sb.append(htmlBodyToMarkdownText(log.contentHtml)).append("\n")
        return sb.toString()
    }

    private fun htmlToSimpleMarkdown(heading: String, dateStamp: String, timeStamp: String, html: String): String {
        return "# $heading\n*$dateStamp · $timeStamp*\n\n${htmlBodyToMarkdownText(html)}"
    }

    private fun htmlBodyToMarkdownText(rawHtml: String): String {
        var s = rawHtml
            .replace(Regex("""<span[^>]*data-wiki-latex="true"[^>]*data-latex-src="([^"]*)"[^>]*>.*?</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { m ->
                "$${m.groupValues[1]}$"
            }
            .replace(Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "\n# $1\n\n")
            .replace(Regex("""<h2[^>]*>(.*?)</h2>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "\n## $1\n\n")
            .replace(Regex("""<h3[^>]*>(.*?)</h3>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "\n### $1\n\n")
            .replace(Regex("""<(?:strong|b)[^>]*>(.*?)</(?:strong|b)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "**$1**")
            .replace(Regex("""<(?:em|i)[^>]*>(.*?)</(?:em|i)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "*$1*")
            .replace(Regex("""<u[^>]*>(.*?)</u>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "_$1_")
            .replace(Regex("""<(?:strike|s|del)[^>]*>(.*?)</(?:strike|s|del)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "~~$1~~")
            .replace(Regex("""<a[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "[$2]($1)")
            .replace(Regex("""<blockquote[^>]*>(.*?)</blockquote>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "\n> $1\n\n")
            .replace(Regex("""<li[^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "- $1\n")
            .replace(Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE), "\n---\n\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        s = stripHtmlTags(s)
        return s.replace("\u200B", "").trim()
    }

    private fun htmlToPlainText(rawHtml: String): String {
        val preformatted = rawHtml
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "\n")
        return stripHtmlTags(preformatted).replace("\u200B", "").trim()
    }

    private fun stripHtmlTags(html: String): String {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun parseHtmlIntoPdfBlocks(rawHtml: String): List<PdfRenderBlock> {
        if (rawHtml.isBlank()) {
            return listOf(
                PdfRenderBlock.TextBlock(
                    text = "",
                    textSizePt = 11f,
                    typeface = Typeface.SERIF,
                    color = Color.parseColor("#202122")
                )
            )
        }

        val blocks = ArrayList<PdfRenderBlock>()
        val tokenRegex = Regex(
            """(<figure\b[^>]*>.*?</figure>|<img\b[^>]*/?>|<hr\b[^>]*/?>|<h[1-3]\b[^>]*>.*?</h[1-3]>|<blockquote\b[^>]*>.*?</blockquote>|<ul\b[^>]*>.*?</ul>|<ol\b[^>]*>.*?</ol>|<p\b[^>]*>.*?</p>|<div\b[^>]*>.*?</div>)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        var lastPos = 0
        for (m in tokenRegex.findAll(rawHtml)) {
            if (m.range.first > lastPos) {
                val plainGap = stripHtmlTags(rawHtml.substring(lastPos, m.range.first)).replace("\u200B", "").trim()
                if (plainGap.isNotEmpty()) {
                    blocks.add(
                        PdfRenderBlock.TextBlock(
                            text = plainGap,
                            textSizePt = 11f,
                            typeface = Typeface.SERIF,
                            color = Color.parseColor("#202122")
                        )
                    )
                }
            }
            val chunk = m.value
            val lower = chunk.lowercase()
            when {
                lower.startsWith("<hr") -> blocks.add(PdfRenderBlock.DividerBlock())
                lower.startsWith("<figure") -> {
                    val imgSrc = SRC_ATTR_REGEX.find(chunk)?.groupValues?.getOrNull(1)
                    val cap = Regex("""<figcaption\b[^>]*>(.*?)</figcaption>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(chunk)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it).trim() }
                    if (!imgSrc.isNullOrBlank()) {
                        blocks.add(PdfRenderBlock.InlineImageBlock(imgSrc, cap))
                    }
                }
                lower.startsWith("<img") -> {
                    val imgSrc = SRC_ATTR_REGEX.find(chunk)?.groupValues?.getOrNull(1)
                    if (!imgSrc.isNullOrBlank()) {
                        blocks.add(PdfRenderBlock.InlineImageBlock(imgSrc))
                    }
                }
                lower.startsWith("<h1") -> {
                    val spanned = Html.fromHtml(chunk, Html.FROM_HTML_MODE_LEGACY)
                    blocks.add(
                        PdfRenderBlock.TextBlock(
                            text = spanned.toString().trim(),
                            textSizePt = 17f,
                            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
                            color = Color.parseColor("#202122"),
                            spacingAfter = 10f,
                            drawUnderlineRule = true
                        )
                    )
                }
                lower.startsWith("<h2") -> {
                    val spanned = Html.fromHtml(chunk, Html.FROM_HTML_MODE_LEGACY)
                    blocks.add(
                        PdfRenderBlock.TextBlock(
                            text = spanned.toString().trim(),
                            textSizePt = 14.5f,
                            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD),
                            color = Color.parseColor("#202122"),
                            spacingAfter = 8f,
                            drawUnderlineRule = true
                        )
                    )
                }
                lower.startsWith("<h3") -> {
                    val spanned = Html.fromHtml(chunk, Html.FROM_HTML_MODE_LEGACY)
                    blocks.add(
                        PdfRenderBlock.TextBlock(
                            text = spanned.toString().trim(),
                            textSizePt = 12.5f,
                            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                            color = Color.parseColor("#202122"),
                            spacingAfter = 6f
                        )
                    )
                }
                lower.startsWith("<blockquote") -> {
                    val spanned = Html.fromHtml(chunk, Html.FROM_HTML_MODE_LEGACY).toString().replace("\u200B", "").trim()
                    if (spanned.isNotEmpty()) {
                        blocks.add(
                            PdfRenderBlock.TextBlock(
                                text = spanned,
                                textSizePt = 10.8f,
                                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC),
                                color = Color.parseColor("#54595D"),
                                indentLeft = 14f,
                                isQuote = true,
                                spacingAfter = 10f
                            )
                        )
                    }
                }
                lower.startsWith("<ul") || lower.startsWith("<ol") -> {
                    val isOrdered = lower.startsWith("<ol")
                    val liRegex = Regex("""<li\b[^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    var itemNum = 1
                    for (li in liRegex.findAll(chunk)) {
                        val prefix = if (isOrdered) "${itemNum++}. " else "• "
                        val txt = prefix + stripHtmlTags(li.groupValues[1]).replace("\u200B", "").trim()
                        blocks.add(
                            PdfRenderBlock.TextBlock(
                                text = txt,
                                textSizePt = 11f,
                                typeface = Typeface.SERIF,
                                color = Color.parseColor("#202122"),
                                indentLeft = 12f,
                                spacingAfter = 4f
                            )
                        )
                    }
                }
                else -> {
                    // Extract any nested images first, then render styled inline spans
                    for (imgMatch in IMG_TAG_REGEX.findAll(chunk)) {
                        val imgSrc = SRC_ATTR_REGEX.find(imgMatch.value)?.groupValues?.getOrNull(1)
                        if (!imgSrc.isNullOrBlank()) {
                            blocks.add(PdfRenderBlock.InlineImageBlock(imgSrc))
                        }
                    }
                    val withoutImgs = IMG_TAG_REGEX.replace(chunk, "")
                    val spanned = SpannableStringBuilder(Html.fromHtml(withoutImgs, Html.FROM_HTML_MODE_LEGACY))
                    val trimmed = trimSpannable(spanned)
                    if (trimmed.isNotEmpty()) {
                        blocks.add(
                            PdfRenderBlock.TextBlock(
                                text = trimmed,
                                textSizePt = 11f,
                                typeface = Typeface.SERIF,
                                color = Color.parseColor("#202122"),
                                spacingAfter = 8f
                            )
                        )
                    }
                }
            }
            lastPos = m.range.last + 1
        }

        if (lastPos < rawHtml.length) {
            val tail = stripHtmlTags(rawHtml.substring(lastPos)).replace("\u200B", "").trim()
            if (tail.isNotEmpty()) {
                blocks.add(
                    PdfRenderBlock.TextBlock(
                        text = tail,
                        textSizePt = 11f,
                        typeface = Typeface.SERIF,
                        color = Color.parseColor("#202122")
                    )
                )
            }
        }
        return blocks
    }

    private fun trimSpannable(sb: SpannableStringBuilder): CharSequence {
        var start = 0
        var end = sb.length
        while (start < end && (sb[start].isWhitespace() || sb[start] == '\u200B')) start++
        while (end > start && (sb[end - 1].isWhitespace() || sb[end - 1] == '\u200B')) end--
        return if (start < end) sb.subSequence(start, end) else ""
    }

    private fun buildStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.35f)
            .setIncludePad(false)
            .build()
    }

    private fun formatAudioSeconds(sec: Double?): String {
        if (sec == null || !sec.isFinite() || sec < 0.0) return "00:01"
        val total = sec.toInt()
        val mins = total / 60
        val rem = total % 60
        return "${mins.toString().padStart(2, '0')}:${rem.toString().padStart(2, '0')}"
    }

    private fun formatAudioStamp(ms: Long): String {
        val ts = if (ms > 0L) ms else System.currentTimeMillis()
        return SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.US).format(Date(ts))
    }

    private fun escapeXml(unsafe: String): String = unsafe
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun escapeRtf(str: String): String {
        val sb = StringBuilder(str.length)
        for (ch in str) {
            val code = ch.code
            when {
                code == 10 -> sb.append("\\par\n")
                code == 92 || code == 123 || code == 125 -> sb.append('\\').append(ch)
                code > 127 -> sb.append("\\u").append(code).append('?')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun sanitizeArchiveSlug(input: String, fallback: String): String {
        val cleaned = input.trim()
            .replace(Regex("""[\\/:*?"<>|]+"""), "_")
            .replace(Regex("""\s+"""), "_")
            .trim('_')
            .take(48)
        return cleaned.ifEmpty { fallback }
    }

    private fun sanitizeFileSlug(input: String, fallback: String): String {
        val cleaned = input.lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9_-]+"""), "_")
            .trim('_')
            .take(42)
        return cleaned.ifEmpty { fallback }
    }

    private fun copyStreamBuffered(inp: InputStream, out: OutputStream) {
        val buf = ByteArray(IO_BUFFER_SIZE)
        while (true) {
            val r = inp.read(buf)
            if (r == -1) break
            out.write(buf, 0, r)
        }
    }

    private fun copyFileAtomic(src: File, dst: File) {
        dst.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(dst.parentFile, "${dst.name}.tmp")
        try {
            FileInputStream(src).use { inp ->
                BufferedOutputStream(FileOutputStream(tmp), IO_BUFFER_SIZE).use { out ->
                    copyStreamBuffered(inp, out)
                    out.flush()
                }
            }
            if (!tmp.renameTo(dst)) {
                tmp.copyTo(dst, overwrite = true)
                tmp.delete()
            }
        } catch (err: Throwable) {
            tmp.delete()
            throw err
        }
    }

    private fun writeBytesAtomic(dst: File, bytes: ByteArray) {
        dst.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(dst.parentFile, "${dst.name}.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            if (!tmp.renameTo(dst)) {
                tmp.copyTo(dst, overwrite = true)
                tmp.delete()
            }
        } catch (err: Throwable) {
            tmp.delete()
            throw err
        }
    }

    private fun queryUriSize(resolver: ContentResolver, uri: Uri): Long {
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) return afd.length
            }
        }
        return 0L
    }

    companion object {
        const val LIKKHO_MIME_TYPE = "application/x-likkho"
        const val LIKKHO_GITHUB_DOWNLOAD_URL = "https://github.com/Likkho/Likkho/releases"

        private const val A4_WIDTH_PT = 595
        private const val A4_HEIGHT_PT = 842
        private const val IO_BUFFER_SIZE = 65_536

        private const val ASSET_MEDIA_PREFIX = "https://appassets.androidplatform.net/likkho-media/"

        private val LIKKHO_UNIVERSAL_SHARE_MAGIC = "LKSHRE01".toByteArray(StandardCharsets.US_ASCII)
        private const val LIKKHO_UNIVERSAL_SHARE_KEY_SECRET =
            "LIKKHO_UNIVERSAL_SHARED_DIARY_CONTAINER_KEY_2026_V1::9f4b2c8e7a1d6f305e8c2a4b7d9e1f6a"
        private const val LIKKHO_UNIVERSAL_SHARE_KDF_CONTEXT =
            "LIKKHO_SHARED_ARCHIVE_UNIVERSAL_DOMAIN_ISOLATION_SALT_V1::"
        private const val LIKKHO_SHARE_KDF_ITERATIONS = 64_000

        private val IMG_TAG_REGEX = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val SRC_ATTR_REGEX = Regex("""src\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val ALT_ATTR_REGEX = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    }
}

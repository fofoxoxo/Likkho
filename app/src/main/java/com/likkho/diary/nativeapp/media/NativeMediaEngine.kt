package com.likkho.diary.nativeapp.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImportedMediaResult(
    val id: String,
    val file: File,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val originalSizeBytes: Long,
    val mimeType: String,
    val assetUrl: String
)

data class ImportedAudioResult(
    val id: String,
    val name: String,
    val format: String,
    val file: File,
    val durationSec: Double,
    val sizeBytes: Long,
    val assetUrl: String
)

data class ImageAdjustmentParams(
    val brightness: Float = 0f,       // -100f..100f
    val contrast: Float = 0f,         // -100f..100f
    val saturation: Float = 0f,       // -100f..100f
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val invert: Boolean = false,
    val rotationDegrees: Float = 0f,  // any angle, typically 0/90/180/270
    val opacity: Float = 1f,          // 0.05f..1f (baked only if < 0.999f and PNG/WebP, or returned for canvas)
    val outputFormat: String = "image/jpeg",
    val qualityPercent: Int = 88,
    val maxDimension: Int = NativeMediaEngine.DEFAULT_MAX_IMAGE_DIM
)

/**
 * Native Zero-OOM Heavy Media Engine for Likkho.
 *
 * Replaces WebView/IndexedDB base64 image & audio handling with streaming native storage:
 * - Downscales picked images using `BitmapFactory.Options` (`inJustDecodeBounds` + `inSampleSize`)
 *   with automatic `ExifInterface` orientation correction, capped at `2560px` max dimension.
 * - Saves processed images directly to `filesDir/likkho_media/<id>` with zero base64 strings in JS/RAM.
 * - Provides 1:1 PFP square cropping, Emoji/Text square PFP rendering, and `ColorMatrix` adjustments.
 * - Generates & caches lightweight thumbnails (`192px` PFP / `720px` canvas preview) in `cacheDir/likkho_previews/`.
 * - Streams picked/recorded audio files directly to `filesDir/likkho_audio/<id>.<ext>`.
 */
class NativeMediaEngine(
    context: Context,
    private val repository: NativeVaultRepository
) {
    private val appContext: Context = context.applicationContext

    // ==================== Image Import (Zero-OOM) ====================

    fun importImageFromUri(
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_IMAGE_DIM,
        preferredId: String? = null
    ): ImportedMediaResult {
        val resolver = appContext.contentResolver
        val rawMime = (resolver.getType(uri) ?: "").lowercase()
        val originalSizeBytes = queryUriSize(resolver, uri)
        val stagedFile = stageUriToTempCacheFile(uri, "img_stage")
        try {
            val decoded = decodeBitmapFromFileWithFallback(stagedFile, maxDimension)
            val resolvedOriginalSize = when {
                originalSizeBytes > 0L -> originalSizeBytes
                stagedFile.length() > 0L -> stagedFile.length()
                else -> 0L
            }
            return processAndPersistBitmap(
                decodedBitmap = decoded.bitmap,
                exifOrientation = decoded.pendingExifOrientation,
                maxDimension = maxDimension,
                preferPng = rawMime == "image/png" || decoded.bitmap.hasAlpha(),
                quality = 88,
                originalSizeBytes = resolvedOriginalSize,
                preferredId = preferredId,
                idPrefix = "img_blob"
            )
        } finally {
            runCatching { stagedFile.delete() }
        }
    }

    fun importImageFromBytes(
        bytes: ByteArray,
        mimeTypeHint: String = "image/jpeg",
        maxDimension: Int = DEFAULT_MAX_IMAGE_DIM,
        preferredId: String? = null
    ): ImportedMediaResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            throw IOException("Invalid image byte payload.")
        }

        val exifOrientation = readExifOrientationFromStream(ByteArrayInputStream(bytes))
        val sampleSize = calculateInSampleSize(srcW, srcH, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: throw IOException("Failed to decode image bytes.")

        return processAndPersistBitmap(
            decodedBitmap = sampledBitmap,
            exifOrientation = exifOrientation,
            maxDimension = maxDimension,
            preferPng = mimeTypeHint.equals("image/png", ignoreCase = true) || sampledBitmap.hasAlpha(),
            quality = 88,
            originalSizeBytes = bytes.size.toLong(),
            preferredId = preferredId,
            idPrefix = "img_blob"
        )
    }

    fun importImageFromDataUrl(
        dataUrl: String,
        maxDimension: Int = DEFAULT_MAX_IMAGE_DIM,
        preferredId: String? = null
    ): ImportedMediaResult? {
        val parsed = parseDataUrl(dataUrl) ?: return null
        return if (parsed.mimeType.contains("svg")) {
            val id = preferredId?.let { repository.sanitizeFileId(it) } ?: generateMediaId("img_blob")
            val outFile = repository.resolveConfinedFile(repository.mediaImagesDir, id)
            writeBytesAtomic(outFile, parsed.bytes)
            ImportedMediaResult(
                id = id,
                file = outFile,
                width = 240,
                height = 240,
                sizeBytes = outFile.length(),
                originalSizeBytes = parsed.bytes.size.toLong(),
                mimeType = "image/svg+xml",
                assetUrl = "$ASSET_MEDIA_PREFIX$id"
            )
        } else {
            importImageFromBytes(parsed.bytes, parsed.mimeType, maxDimension, preferredId)
        }
    }

    // ==================== 1:1 PFP Cropper & Emoji/Text Renderer ====================

    fun cropSquarePfpFromUri(
        uri: Uri,
        zoom: Float = 1f,
        panXFraction: Float = 0f,
        panYFraction: Float = 0f,
        outputSizePx: Int = DEFAULT_PFP_OUTPUT_PX
    ): ImportedMediaResult {
        val resolver = appContext.contentResolver
        val originalSize = queryUriSize(resolver, uri)
        val stagedFile = stageUriToTempCacheFile(uri, "pfp_stage")
        val decodeTarget = max(outputSizePx * 2, 1024)
        var sampled: Bitmap? = null
        var oriented: Bitmap? = null
        return try {
            val decoded = decodeBitmapFromFileWithFallback(stagedFile, decodeTarget)
            sampled = decoded.bitmap
            oriented = applyExifOrientation(decoded.bitmap, decoded.pendingExifOrientation)
            val resolvedOriginalSize = if (originalSize > 0L) originalSize else stagedFile.length()
            renderSquareCroppedPfp(
                source = oriented,
                zoom = zoom,
                panXFraction = panXFraction,
                panYFraction = panYFraction,
                outputSizePx = outputSizePx,
                originalSizeBytes = resolvedOriginalSize
            )
        } finally {
            if (oriented != null && oriented !== sampled) oriented.recycle()
            sampled?.recycle()
            runCatching { stagedFile.delete() }
        }
    }

    fun decodePreviewBitmapFromUri(uri: Uri, maxDim: Int = DEFAULT_CANVAS_PREVIEW_PX): Bitmap? {
        val stagedFile = runCatching { stageUriToTempCacheFile(uri, "prev_stage") }.getOrNull() ?: return null
        return try {
            decodePreviewBitmapFromFile(stagedFile, maxDim)
        } finally {
            runCatching { stagedFile.delete() }
        }
    }

    fun decodePreviewBitmapFromFile(file: File, maxDim: Int = DEFAULT_CANVAS_PREVIEW_PX): Bitmap? {
        return runCatching {
            val decoded = decodeBitmapFromFileWithFallback(file, maxDim)
            val oriented = applyExifOrientation(decoded.bitmap, decoded.pendingExifOrientation)
            if (oriented !== decoded.bitmap) {
                decoded.bitmap.recycle()
            }
            oriented
        }.getOrNull()
    }

    fun cropSquarePfpFromMediaId(
        mediaIdOrRef: String,
        zoom: Float = 1f,
        panXFraction: Float = 0f,
        panYFraction: Float = 0f,
        outputSizePx: Int = DEFAULT_PFP_OUTPUT_PX
    ): ImportedMediaResult {
        val file = repository.resolveExistingImageFile(mediaIdOrRef)
            ?: throw IOException("Source image not found for PFP crop.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val decodeTarget = max(outputSizePx * 2, 1024)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, decodeTarget)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: throw IOException("Failed to decode source image for PFP crop.")

        return try {
            renderSquareCroppedPfp(
                source = decoded,
                zoom = zoom,
                panXFraction = panXFraction,
                panYFraction = panYFraction,
                outputSizePx = outputSizePx,
                originalSizeBytes = file.length()
            )
        } finally {
            decoded.recycle()
        }
    }

    private fun renderSquareCroppedPfp(
        source: Bitmap,
        zoom: Float,
        panXFraction: Float,
        panYFraction: Float,
        outputSizePx: Int,
        originalSizeBytes: Long
    ): ImportedMediaResult {
        val size = outputSizePx.coerceIn(128, 1024)
        val safeZoom = zoom.coerceIn(0.5f, 6f)
        val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(outBmp)
            canvas.drawColor(Color.parseColor("#101418"))

            val minDim = max(1, min(source.width, source.height)).toFloat()
            val finalScale = (size.toFloat() / minDim) * safeZoom
            val drawW = source.width * finalScale
            val drawH = source.height * finalScale
            val drawX = (size - drawW) / 2f + (panXFraction * size)
            val drawY = (size - drawH) / 2f + (panYFraction * size)

            val matrix = Matrix().apply {
                postScale(finalScale, finalScale)
                postTranslate(drawX, drawY)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, matrix, paint)

            val id = generateMediaId("img_blob_pfp")
            val outFile = repository.resolveConfinedFile(repository.mediaImagesDir, id)
            val usePng = source.hasAlpha()
            val format = if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (usePng) 100 else 90
            saveBitmapAtomic(outBmp, outFile, format, quality)
            generatePreviewThumbnail(outBmp, id, DEFAULT_PFP_PREVIEW_PX, isPfp = true)

            return ImportedMediaResult(
                id = id,
                file = outFile,
                width = size,
                height = size,
                sizeBytes = outFile.length(),
                originalSizeBytes = originalSizeBytes.coerceAtLeast(outFile.length()),
                mimeType = if (usePng) "image/png" else "image/jpeg",
                assetUrl = "$ASSET_MEDIA_PREFIX$id"
            )
        } finally {
            outBmp.recycle()
        }
    }

    fun renderEmojiTextPfp(
        rawText: String,
        bgHex: String = "#3366cc",
        fgHex: String = "#ffffff",
        fontScale: Float = 1f,
        outputSizePx: Int = DEFAULT_PFP_OUTPUT_PX
    ): ImportedMediaResult {
        val size = outputSizePx.coerceIn(128, 1024)
        val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(outBmp)
            val bgColor = runCatching { Color.parseColor(bgHex) }.getOrDefault(Color.parseColor("#3366cc"))
            val fgColor = runCatching { Color.parseColor(fgHex) }.getOrDefault(Color.WHITE)
            canvas.drawColor(bgColor)

            val displayStr = rawText.trim().take(8).ifEmpty { "📔" }
            val codePointCount = displayStr.codePointCount(0, displayStr.length)
            val baseFontPx = when {
                codePointCount <= 1 -> size * 0.54f
                codePointCount == 2 -> size * 0.44f
                codePointCount == 3 -> size * 0.34f
                else -> size * 0.26f
            }
            val finalFontPx = (baseFontPx * fontScale.coerceIn(0.4f, 1.8f)).coerceAtLeast(16f)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fgColor
                textSize = finalFontPx
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val fm = textPaint.fontMetrics
            val centerY = (size / 2f) - ((fm.ascent + fm.descent) / 2f)
            canvas.drawText(displayStr, size / 2f, centerY, textPaint)

            val id = generateMediaId("img_blob_pfp")
            val outFile = repository.resolveConfinedFile(repository.mediaImagesDir, id)
            saveBitmapAtomic(outBmp, outFile, Bitmap.CompressFormat.PNG, 100)
            generatePreviewThumbnail(outBmp, id, DEFAULT_PFP_PREVIEW_PX, isPfp = true)

            return ImportedMediaResult(
                id = id,
                file = outFile,
                width = size,
                height = size,
                sizeBytes = outFile.length(),
                originalSizeBytes = outFile.length(),
                mimeType = "image/png",
                assetUrl = "$ASSET_MEDIA_PREFIX$id"
            )
        } finally {
            outBmp.recycle()
        }
    }

    // ==================== Native Bitmap Adjustments & Filters ====================

    fun applyImageAdjustments(
        sourceMediaIdOrRef: String,
        params: ImageAdjustmentParams,
        targetId: String? = null
    ): ImportedMediaResult {
        val sourceFile = repository.resolveExistingImageFile(sourceMediaIdOrRef)
            ?: throw IOException("Image file not found: $sourceMediaIdOrRef")

        val maxDim = params.maxDimension.coerceIn(128, 4096)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)

        val decoded = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: throw IOException("Failed to decode image for adjustments.")

        var rotated: Bitmap? = null
        var edited: Bitmap? = null
        try {
            val rotDeg = params.rotationDegrees % 360f
            rotated = if (rotDeg != 0f) {
                val m = Matrix().apply { postRotate(rotDeg) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            } else {
                decoded
            }

            val scale = computeDownscaleFactor(rotated.width, rotated.height, maxDim)
            val targetW = max(1, (rotated.width * scale).roundToInt())
            val targetH = max(1, (rotated.height * scale).roundToInt())

            edited = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(edited)

            val cm = buildAdjustmentColorMatrix(params)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
                if (params.opacity < 0.999f) {
                    alpha = (params.opacity.coerceIn(0.05f, 1f) * 255f).roundToInt()
                }
            }

            val drawMatrix = Matrix().apply {
                if (scale != 1f) postScale(scale, scale)
            }
            canvas.drawBitmap(rotated, drawMatrix, paint)

            val outId = targetId?.let { repository.sanitizeFileId(it) } ?: generateMediaId("img_blob_edit")
            val outFile = repository.resolveConfinedFile(repository.mediaImagesDir, outId)
            val fmt = when {
                params.outputFormat.equals("image/png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
                params.outputFormat.equals("image/webp", ignoreCase = true) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                else -> Bitmap.CompressFormat.JPEG
            }
            val mime = when (fmt) {
                Bitmap.CompressFormat.PNG -> "image/png"
                Bitmap.CompressFormat.JPEG -> "image/jpeg"
                else -> "image/webp"
            }
            val quality = if (fmt == Bitmap.CompressFormat.PNG) 100 else params.qualityPercent.coerceIn(20, 100)
            saveBitmapAtomic(edited, outFile, fmt, quality)
            generatePreviewThumbnail(edited, outId, DEFAULT_CANVAS_PREVIEW_PX, isPfp = false)

            return ImportedMediaResult(
                id = outId,
                file = outFile,
                width = targetW,
                height = targetH,
                sizeBytes = outFile.length(),
                originalSizeBytes = sourceFile.length(),
                mimeType = mime,
                assetUrl = "$ASSET_MEDIA_PREFIX$outId"
            )
        } finally {
            if (rotated != null && rotated !== decoded) rotated.recycle()
            decoded.recycle()
            edited?.recycle()
        }
    }

    private fun buildAdjustmentColorMatrix(params: ImageAdjustmentParams): ColorMatrix {
        val result = ColorMatrix()

        // 1. Saturation / Grayscale
        if (params.grayscale) {
            val gray = ColorMatrix().apply { setSaturation(0f) }
            result.postConcat(gray)
        } else if (params.saturation != 0f) {
            val satVal = (1f + params.saturation.coerceIn(-100f, 100f) / 100f).coerceIn(0f, 2.5f)
            val sat = ColorMatrix().apply { setSaturation(satVal) }
            result.postConcat(sat)
        }

        // 2. Contrast & Brightness
        if (params.contrast != 0f || params.brightness != 0f) {
            val c = (1f + params.contrast.coerceIn(-100f, 100f) / 100f).coerceIn(0.1f, 3f)
            val b = (params.brightness.coerceIn(-100f, 100f) / 100f) * 255f
            val translate = 128f * (1f - c) + b
            val cbMatrix = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(cbMatrix)
        }

        // 3. Sepia
        if (params.sepia) {
            val sepiaMatrix = ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(sepiaMatrix)
        }

        // 4. Invert
        if (params.invert) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(invertMatrix)
        }

        return result
    }

    // ==================== Lightweight Thumbnail Cache (192px PFP / 720px Preview) ====================

    fun getOrGeneratePreviewFile(
        mediaIdOrRef: String,
        maxPreviewPx: Int = DEFAULT_CANVAS_PREVIEW_PX,
        isPfp: Boolean = false
    ): File? {
        val cleanId = repository.normalizeMediaId(mediaIdOrRef)
        if (cleanId.isBlank() || cleanId.startsWith("data:") || cleanId.startsWith("http")) return null

        val safeId = runCatching { repository.sanitizeFileId(cleanId) }.getOrNull() ?: return null
        val cacheKey = if (isPfp) "${safeId}_pfp_${maxPreviewPx}" else "${safeId}_prev_${maxPreviewPx}"
        val cachedFile = repository.resolveConfinedFile(repository.previewCacheDir, cacheKey)
        if (cachedFile.exists() && cachedFile.isFile && cachedFile.length() > 0L) {
            return cachedFile
        }

        val fullFile = repository.resolveExistingImageFile(safeId) ?: return null
        if (fullFile.length() <= SMALL_IMAGE_PASSTHROUGH_BYTES) {
            // Small enough to serve directly without generating a duplicate preview
            return fullFile
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(fullFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return fullFile

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxPreviewPx)
            val decoded = BitmapFactory.decodeFile(
                fullFile.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: return fullFile

            try {
                generatePreviewThumbnail(decoded, safeId, maxPreviewPx, isPfp) ?: fullFile
            } finally {
                decoded.recycle()
            }
        } catch (_: Exception) {
            fullFile
        }
    }

    private fun generatePreviewThumbnail(
        sourceBitmap: Bitmap,
        safeId: String,
        maxPreviewPx: Int,
        isPfp: Boolean
    ): File? {
        return try {
            val cacheKey = if (isPfp) "${safeId}_pfp_${maxPreviewPx}" else "${safeId}_prev_${maxPreviewPx}"
            val outFile = repository.resolveConfinedFile(repository.previewCacheDir, cacheKey)
            val scale = computeDownscaleFactor(sourceBitmap.width, sourceBitmap.height, maxPreviewPx)
            val targetW = max(1, (sourceBitmap.width * scale).roundToInt())
            val targetH = max(1, (sourceBitmap.height * scale).roundToInt())

            val scaled = if (scale < 0.999f) {
                Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
            } else {
                sourceBitmap
            }
            try {
                val fmt = if (sourceBitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val q = if (fmt == Bitmap.CompressFormat.PNG) 100 else 82
                saveBitmapAtomic(scaled, outFile, fmt, q)
                outFile
            } finally {
                if (scaled !== sourceBitmap) scaled.recycle()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBoundedBitmapForExport(
        mediaRefOrId: String,
        maxDimension: Int = 1024
    ): Bitmap? {
        if (mediaRefOrId.isBlank()) return null
        if (mediaRefOrId.startsWith("data:image/")) {
            val parsed = parseDataUrl(mediaRefOrId) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(parsed.bytes, 0, parsed.bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            return BitmapFactory.decodeByteArray(
                parsed.bytes,
                0,
                parsed.bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }
        val file = repository.resolveExistingImageFile(mediaRefOrId) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }

    // ==================== Native Audio Import & Recording Storage ====================

    fun createRecordingTargetFile(audioId: String, format: String = "wav"): File {
        val cleanExt = format.lowercase().trim().removePrefix(".").ifEmpty { "wav" }
        val safeId = repository.sanitizeFileId(audioId)
        val fileName = if (safeId.endsWith(".$cleanExt", ignoreCase = true)) safeId else "$safeId.$cleanExt"
        return repository.resolveConfinedFile(repository.mediaAudioDir, fileName)
    }

    fun importAudioFromUri(uri: Uri, preferredId: String? = null): ImportedAudioResult {
        val resolver = appContext.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "Audio_${System.currentTimeMillis()}"
        val mime = (resolver.getType(uri) ?: "").lowercase()
        val ext = inferAudioExtension(displayName, mime)

        val id = preferredId?.let { repository.sanitizeFileId(it) } ?: generateMediaId("aud_native")
        val destFile = createRecordingTargetFile(id, ext)
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")

        try {
            copyUriToFile(uri, tmpFile, "Unable to open selected audio file.")
            if (!tmpFile.exists() || tmpFile.length() <= 0L) {
                throw IOException("Selected audio file is empty.")
            }
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (err: Throwable) {
            tmpFile.delete()
            throw err
        }

        val durationSec = extractAudioDurationSeconds(destFile)
        return ImportedAudioResult(
            id = id,
            name = displayName,
            format = ext,
            file = destFile,
            durationSec = durationSec,
            sizeBytes = destFile.length(),
            assetUrl = "$ASSET_AUDIO_PREFIX${destFile.name}"
        )
    }

    fun importAudioFromBytes(
        bytes: ByteArray,
        format: String = "wav",
        displayName: String = "Voice Note",
        preferredId: String? = null
    ): ImportedAudioResult {
        val cleanExt = format.lowercase().trim().removePrefix(".").ifEmpty { "wav" }
        val id = preferredId?.let { repository.sanitizeFileId(it) } ?: generateMediaId("aud_native")
        val destFile = createRecordingTargetFile(id, cleanExt)
        writeBytesAtomic(destFile, bytes)
        val durationSec = extractAudioDurationSeconds(destFile)
        return ImportedAudioResult(
            id = id,
            name = displayName,
            format = cleanExt,
            file = destFile,
            durationSec = durationSec,
            sizeBytes = destFile.length(),
            assetUrl = "$ASSET_AUDIO_PREFIX${destFile.name}"
        )
    }

    fun extractAudioDurationSeconds(file: File): Double {
        if (!file.exists() || file.length() <= 0L) return 1.0
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durMs > 0L) (durMs / 1000.0).coerceAtLeast(1.0) else 1.0
        } catch (_: Exception) {
            1.0
        } finally {
            runCatching { mmr.release() }
        }
    }

    // ==================== Internal Bitmap & Stream Helpers ====================

    private fun processAndPersistBitmap(
        decodedBitmap: Bitmap,
        exifOrientation: Int,
        maxDimension: Int,
        preferPng: Boolean,
        quality: Int,
        originalSizeBytes: Long,
        preferredId: String?,
        idPrefix: String
    ): ImportedMediaResult {
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            oriented = applyExifOrientation(decodedBitmap, exifOrientation)
            val safeMax = maxDimension.coerceIn(128, 4096)
            val scale = computeDownscaleFactor(oriented.width, oriented.height, safeMax)
            val targetW = max(1, (oriented.width * scale).roundToInt())
            val targetH = max(1, (oriented.height * scale).roundToInt())

            scaled = if (scale < 0.999f) {
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            } else {
                oriented
            }

            val id = preferredId?.let { repository.sanitizeFileId(it) } ?: generateMediaId(idPrefix)
            val outFile = repository.resolveConfinedFile(repository.mediaImagesDir, id)
            val fmt = if (preferPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val finalQuality = if (preferPng) 100 else quality.coerceIn(20, 100)
            saveBitmapAtomic(scaled, outFile, fmt, finalQuality)
            generatePreviewThumbnail(scaled, id, DEFAULT_CANVAS_PREVIEW_PX, isPfp = false)

            return ImportedMediaResult(
                id = id,
                file = outFile,
                width = targetW,
                height = targetH,
                sizeBytes = outFile.length(),
                originalSizeBytes = if (originalSizeBytes > 0L) originalSizeBytes else outFile.length(),
                mimeType = if (preferPng) "image/png" else "image/jpeg",
                assetUrl = "$ASSET_MEDIA_PREFIX$id"
            )
        } finally {
            if (scaled != null && scaled !== oriented && scaled !== decodedBitmap) scaled.recycle()
            if (oriented != null && oriented !== decodedBitmap) oriented.recycle()
            decodedBitmap.recycle()
        }
    }

    private fun saveBitmapAtomic(
        bitmap: Bitmap,
        targetFile: File,
        format: Bitmap.CompressFormat,
        quality: Int
    ) {
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            BufferedOutputStream(FileOutputStream(tmp), IO_BUFFER_SIZE).use { out ->
                if (!bitmap.compress(format, quality, out)) {
                    throw IOException("Failed to compress bitmap to ${targetFile.name}")
                }
                out.flush()
            }
            if (!tmp.renameTo(targetFile)) {
                tmp.copyTo(targetFile, overwrite = true)
                tmp.delete()
            }
        } catch (err: Throwable) {
            tmp.delete()
            throw err
        }
    }

    private fun writeBytesAtomic(targetFile: File, bytes: ByteArray) {
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            if (!tmp.renameTo(targetFile)) {
                tmp.copyTo(targetFile, overwrite = true)
                tmp.delete()
            }
        } catch (err: Throwable) {
            tmp.delete()
            throw err
        }
    }

    private data class DecodedBitmapWithExif(
        val bitmap: Bitmap,
        val pendingExifOrientation: Int
    )

    private fun stageUriToTempCacheFile(uri: Uri, prefix: String): File {
        val stageDir = File(appContext.cacheDir, "likkho_media_stage").apply { if (!exists()) mkdirs() }
        val tmp = File(stageDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.tmp")
        return try {
            copyUriToFile(uri, tmp, "Unable to read selected file.")
            if (!tmp.exists() || tmp.length() <= 0L) {
                throw IOException("Selected file is empty.")
            }
            tmp
        } catch (err: Throwable) {
            runCatching { tmp.delete() }
            throw err
        }
    }

    private fun copyUriToFile(uri: Uri, targetFile: File, emptyErrorMessage: String) {
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val resolver = appContext.contentResolver
        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull()
        if (stream != null) {
            stream.use { input ->
                BufferedOutputStream(FileOutputStream(targetFile), IO_BUFFER_SIZE).use { out ->
                    val buf = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                    }
                    out.flush()
                }
            }
            return
        }

        val pfd = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
            ?: throw IOException(emptyErrorMessage)
        pfd.use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                BufferedOutputStream(FileOutputStream(targetFile), IO_BUFFER_SIZE).use { out ->
                    val buf = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                    }
                    out.flush()
                }
            }
        }
    }

    private fun decodeBitmapFromFileWithFallback(file: File, maxDimension: Int): DecodedBitmapWithExif {
        val safeMax = maxDimension.coerceIn(128, 4096)
        val exifOrientation = readExifOrientationFromFile(file)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, safeMax)
            for (attempt in 0..2) {
                try {
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = false
                    }
                    val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                    if (bmp != null) {
                        return DecodedBitmapWithExif(bmp, exifOrientation)
                    }
                    break
                } catch (_: OutOfMemoryError) {
                    sampleSize *= 2
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(file)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val srcW = info.size.width
                val srcH = info.size.height
                if (srcW > 0 && srcH > 0) {
                    val scale = computeDownscaleFactor(srcW, srcH, safeMax)
                    if (scale < 0.999f) {
                        decoder.setTargetSize(
                            max(1, (srcW * scale).roundToInt()),
                            max(1, (srcH * scale).roundToInt())
                        )
                    }
                }
            }
            // ImageDecoder applies EXIF rotation automatically on API 28+
            return DecodedBitmapWithExif(decoded, ExifInterface.ORIENTATION_NORMAL)
        }

        throw IOException("Unsupported or corrupted image file.")
    }

    private fun readExifOrientationFromFile(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun readExifOrientationFromStream(stream: InputStream): Int {
        return try {
            val exif = ExifInterface(stream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqMaxDim: Int): Int {
        var inSampleSize = 1
        val maxSide = max(srcWidth, srcHeight)
        if (maxSide > reqMaxDim) {
            val halfMax = maxSide / 2
            while ((halfMax / inSampleSize) >= reqMaxDim) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun computeDownscaleFactor(width: Int, height: Int, maxDimension: Int): Float {
        val maxSide = max(width, height)
        return if (maxSide > maxDimension && maxSide > 0) {
            maxDimension.toFloat() / maxSide.toFloat()
        } else {
            1f
        }
    }

    private fun queryUriSize(resolver: ContentResolver, uri: Uri): Long {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) {
                    val s = c.getLong(0)
                    if (s > 0L) return s
                }
            }
        }
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) return afd.length
            }
        }
        return 0L
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun inferAudioExtension(displayName: String, mime: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase().trim()
        if (fromName in SUPPORTED_AUDIO_EXTENSIONS) return fromName
        return when {
            mime.contains("flac") -> "flac"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("mp4") || mime.contains("m4a") -> "m4a"
            mime.contains("aac") -> "aac"
            mime.contains("ogg") -> "ogg"
            mime.contains("webm") -> "webm"
            else -> "wav"
        }
    }

    data class ParsedDataUrl(val mimeType: String, val ext: String, val bytes: ByteArray)

    fun parseDataUrl(dataUrl: String): ParsedDataUrl? {
        if (!dataUrl.startsWith("data:")) return null
        val commaIdx = dataUrl.indexOf(',')
        if (commaIdx < 0) return null
        val header = dataUrl.substring(5, commaIdx)
        val body = dataUrl.substring(commaIdx + 1)
        val isBase64 = header.contains(";base64", ignoreCase = true)
        val mime = header.substringBefore(';').lowercase().ifEmpty { "application/octet-stream" }
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("svg") -> "svg"
            mime.contains("wav") -> "wav"
            mime.contains("flac") -> "flac"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("mp4") || mime.contains("m4a") -> "m4a"
            mime.contains("aac") -> "aac"
            mime.contains("ogg") -> "ogg"
            mime.contains("webm") -> "webm"
            mime.contains("ttf") || mime.contains("font") -> "ttf"
            else -> "bin"
        }
        return try {
            val bytes = if (isBase64) {
                Base64.decode(body, Base64.DEFAULT)
            } else {
                java.net.URLDecoder.decode(body, "UTF-8").toByteArray(StandardCharsets.UTF_8)
            }
            ParsedDataUrl(mime, ext, bytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun generateMediaId(prefix: String): String {
        val rand = UUID.randomUUID().toString().replace("-", "").take(6)
        return "${prefix}_${System.currentTimeMillis()}_$rand"
    }

    companion object {
        const val DEFAULT_MAX_IMAGE_DIM = 2560
        const val DEFAULT_PFP_OUTPUT_PX = 512
        const val DEFAULT_PFP_PREVIEW_PX = 192
        const val DEFAULT_CANVAS_PREVIEW_PX = 720
        private const val SMALL_IMAGE_PASSTHROUGH_BYTES = 65L * 1024L
        private const val IO_BUFFER_SIZE = 65_536

        private const val ASSET_MEDIA_PREFIX = "https://appassets.androidplatform.net/likkho-media/"
        private const val ASSET_AUDIO_PREFIX = "https://appassets.androidplatform.net/likkho-audio/"

        private val SUPPORTED_AUDIO_EXTENSIONS = setOf("wav", "flac", "m4a", "aac", "mp3", "ogg", "webm")
    }
}

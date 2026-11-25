package com.likkho.diary.nativeapp.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CustomFontItem
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.MicRecordingSettings
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.SavedPasswordItem
import com.likkho.diary.nativeapp.model.VaultMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-time automatic migration helper from legacy Capacitor `https://localhost` WebView
 * storage (`localStorage`, `IndexedDB`, and `filesDir/likkho_internal_data/`) into
 * encrypted `NativeVaultRepository` + `filesDir/likkho_media/` + `filesDir/likkho_audio/`.
 */
class LegacyWebViewMigrationHelper(
    private val context: Context,
    private val repository: NativeVaultRepository,
    private val mediaEngine: NativeMediaEngine
) {
    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun needsMigration(): Boolean {
        if (prefs.getBoolean(KEY_MIGRATED_V1, false)) return false
        val hasPrimaryLogs = repository.loadLogs(VaultMode.PRIMARY).isNotEmpty()
        val hasPrimaryPin = repository.hasPrimaryPasscode()
        if (hasPrimaryLogs || hasPrimaryPin) {
            prefs.edit().putBoolean(KEY_MIGRATED_V1, true).apply()
            return false
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun runMigrationIfNeeded(onComplete: (didMigrateData: Boolean) -> Unit) {
        if (!needsMigration()) {
            onComplete(false)
            return
        }

        val finished = AtomicBoolean(false)
        var offscreenWebView: WebView? = null
        val activeStreams = HashMap<String, FileOutputStream>()

        fun cleanupAndFinish(didMigrate: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            prefs.edit().putBoolean(KEY_MIGRATED_V1, true).apply()
            synchronized(activeStreams) {
                activeStreams.values.forEach { runCatching { it.close() } }
                activeStreams.clear()
            }
            mainHandler.post {
                runCatching {
                    offscreenWebView?.apply {
                        stopLoading()
                        removeJavascriptInterface("LikkhoLegacyMigrationBridge")
                        removeJavascriptInterface("LikkhoNative")
                        destroy()
                    }
                    offscreenWebView = null
                }
                onComplete(didMigrate)
            }
        }

        val timeoutRunnable = Runnable {
            Log.w(TAG, "Legacy WebView migration timed out after ${MIGRATION_TIMEOUT_MS}ms")
            cleanupAndFinish(false)
        }
        mainHandler.postDelayed(timeoutRunnable, MIGRATION_TIMEOUT_MS)

        mainHandler.post {
            try {
                val webView = WebView(appContext).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.allowFileAccess = false
                }
                offscreenWebView = webView

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun readInternalAppFile(filename: String): String {
                            return readLegacyInternalFile(filename)
                        }

                        @JavascriptInterface
                        fun hasInternalAppFile(filename: String): Boolean {
                            return hasLegacyInternalFile(filename)
                        }
                    },
                    "LikkhoNative"
                )

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun writeMediaImageChunk(
                            id: String,
                            mimeType: String,
                            base64Chunk: String,
                            isFirst: Boolean,
                            isLast: Boolean
                        ): Boolean {
                            return writeStreamedMediaChunk(
                                baseDir = repository.mediaImagesDir,
                                id = id,
                                finalFilename = repository.sanitizeFileId(id),
                                base64Chunk = base64Chunk,
                                isFirst = isFirst,
                                isLast = isLast,
                                activeStreams = activeStreams
                            )
                        }

                        @JavascriptInterface
                        fun writeMediaAudioChunk(
                            id: String,
                            mimeType: String,
                            base64Chunk: String,
                            isFirst: Boolean,
                            isLast: Boolean
                        ): Boolean {
                            val ext = inferAudioExtFromMime(mimeType)
                            val safeId = repository.sanitizeFileId(id)
                            val targetName = if (safeId.contains('.')) safeId else "$safeId.$ext"
                            return writeStreamedMediaChunk(
                                baseDir = repository.mediaAudioDir,
                                id = id,
                                finalFilename = targetName,
                                base64Chunk = base64Chunk,
                                isFirst = isFirst,
                                isLast = isLast,
                                activeStreams = activeStreams
                            )
                        }

                        @JavascriptInterface
                        fun commitVaultPayload(payloadJson: String) {
                            runCatching {
                                commitParsedPayload(payloadJson)
                            }.onFailure { err ->
                                Log.e(TAG, "Error committing legacy migration payload", err)
                            }
                        }

                        @JavascriptInterface
                        fun markMigrationDone(summaryJson: String) {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            val obj = runCatching { JSONObject(summaryJson) }.getOrNull()
                            val totalMigrated = (obj?.optInt("primaryLogs", 0) ?: 0) +
                                (obj?.optInt("decoyLogs", 0) ?: 0)
                            workerScope.launch {
                                cleanupAndFinish(totalMigrated > 0)
                            }
                        }

                        @JavascriptInterface
                        fun markMigrationError(errorMessage: String) {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            Log.w(TAG, "Legacy JS migration error: $errorMessage")
                            cleanupAndFinish(false)
                        }
                    },
                    "LikkhoLegacyMigrationBridge"
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        if (!url.host.equals("localhost", ignoreCase = true)) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                403,
                                "Forbidden",
                                emptyMap(),
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        val rawPath = (url.encodedPath ?: "").trimStart('/')
                        val assetPath = if (rawPath.isEmpty()) "index.html" else rawPath
                        if (assetPath.contains("..")) return null
                        return openAssetResponse(assetPath)
                    }
                }

                webView.loadUrl("https://localhost/index.html?mode=migrate")
            } catch (err: Throwable) {
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.e(TAG, "Failed to start legacy WebView migration", err)
                cleanupAndFinish(false)
            }
        }
    }

    private fun writeStreamedMediaChunk(
        baseDir: File,
        id: String,
        finalFilename: String,
        base64Chunk: String,
        isFirst: Boolean,
        isLast: Boolean,
        activeStreams: MutableMap<String, FileOutputStream>
    ): Boolean {
        return try {
            val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
            val tmpFile = repository.resolveConfinedFile(baseDir, "${repository.sanitizeFileId(id)}.migtmp")
            val finalFile = repository.resolveConfinedFile(baseDir, finalFilename)
            val streamKey = "${baseDir.name}:$id"

            synchronized(activeStreams) {
                if (isFirst) {
                    activeStreams.remove(streamKey)?.let { runCatching { it.close() } }
                    if (tmpFile.exists()) tmpFile.delete()
                    activeStreams[streamKey] = FileOutputStream(tmpFile, false)
                }
                val fos = activeStreams[streamKey] ?: FileOutputStream(tmpFile, true).also {
                    activeStreams[streamKey] = it
                }
                fos.write(bytes)
                if (isLast) {
                    fos.flush()
                    fos.close()
                    activeStreams.remove(streamKey)
                    if (!tmpFile.renameTo(finalFile)) {
                        tmpFile.copyTo(finalFile, overwrite = true)
                        tmpFile.delete()
                    }
                }
            }
            true
        } catch (err: Throwable) {
            Log.w(TAG, "Failed to write migration media chunk for $id: ${err.message}")
            false
        }
    }

    private fun commitParsedPayload(payloadJson: String) {
        val root = JSONObject(payloadJson)

        if (root.optBoolean("isDarkTheme", false)) {
            repository.setDarkTheme(true)
        }
        if (root.optBoolean("isFullScreen", false)) {
            repository.setFullScreenEnabled(true)
        }
        if (root.optBoolean("isBiometricEnabled", false)) {
            repository.setBiometricEnabled(true)
        }

        val primaryPin = root.optString("primaryPin", "").trim()
        if (primaryPin.length in 4..8) {
            repository.setPrimaryPasscode(primaryPin)
            val decoyPin = root.optString("decoyPin", "").trim()
            if (decoyPin.length in 4..8 && decoyPin != primaryPin) {
                repository.setDecoyPasscode(decoyPin)
            }
        }

        commitSingleVaultSection(VaultMode.PRIMARY, root.optJSONObject("primary"))
        commitSingleVaultSection(VaultMode.DECOY, root.optJSONObject("decoy"))
    }

    private fun commitSingleVaultSection(mode: VaultMode, section: JSONObject?) {
        if (section == null) return

        val logsArr = section.optJSONArray("logs")
        if (logsArr != null && logsArr.length() > 0) {
            val migratedLogs = buildList {
                for (i in 0 until logsArr.length()) {
                    val item = logsArr.optJSONObject(i) ?: continue
                    val parsed = DiaryLog.fromJson(item)
                    add(rewriteLegacyMediaRefsInLog(parsed))
                }
            }
            if (migratedLogs.isNotEmpty()) {
                repository.saveLogs(mode, migratedLogs)
            }
        }

        val fontsArr = section.optJSONArray("customFonts")
        if (fontsArr != null && fontsArr.length() > 0) {
            val fonts = buildList {
                for (i in 0 until fontsArr.length()) {
                    fontsArr.optJSONObject(i)?.let { add(CustomFontItem.fromJson(it)) }
                }
            }
            if (fonts.isNotEmpty()) repository.saveCustomFonts(mode, fonts)
        }

        section.optJSONObject("micSettings")?.let {
            repository.saveMicSettings(mode, MicRecordingSettings.fromJson(it))
        }

        val notesArr = section.optJSONArray("quickNotes")
        if (notesArr != null && notesArr.length() > 0) {
            val notes = buildList {
                for (i in 0 until notesArr.length()) {
                    notesArr.optJSONObject(i)?.let { add(QuickNote.fromJson(it)) }
                }
            }
            if (notes.isNotEmpty()) repository.saveQuickNotes(mode, notes)
        }

        val pwArr = section.optJSONArray("savedPasswords")
        if (pwArr != null && pwArr.length() > 0) {
            val passwords = buildList {
                for (i in 0 until pwArr.length()) {
                    pwArr.optJSONObject(i)?.let { add(SavedPasswordItem.fromJson(it)) }
                }
            }
            if (passwords.isNotEmpty()) repository.saveSavedPasswords(mode, passwords)
        }

        val wordJson = section.optString("savedWordAnalysesJson", "")
        if (wordJson.isNotBlank() && wordJson != "[]") {
            val parsedArr = runCatching { JSONArray(wordJson) }.getOrNull()
            if (parsedArr != null && parsedArr.length() > 0) {
                repository.saveSavedWordAnalysesJson(mode, parsedArr.toString())
            }
        }

        val encKeyHash = section.optString("encryptionKeyHash", "").trim()
        if (encKeyHash.isNotEmpty()) {
            repository.restorePreHashedEncryptionKeyHint(mode, encKeyHash)
        }
    }

    private fun rewriteLegacyMediaRefsInLog(log: DiaryLog): DiaryLog {
        val nextPfp = rewriteImageRef(log.pfpDataUrl)
        val nextBg = rewriteImageRef(log.canvasBgDataUrl)
        val nextImages = log.canvasImages.map { img ->
            val nextUrl = rewriteImageRef(img.dataUrl) ?: img.dataUrl
            img.copy(dataUrl = nextUrl)
        }
        val nextAudios = log.audioAttachments.map { aud ->
            val diskAudio = repository.resolveExistingAudioFile(aud.id, aud.format)
            val nextUri = when {
                diskAudio != null -> "$ASSET_AUDIO_PREFIX${diskAudio.name}"
                !aud.dataUrl.isNullOrBlank() && aud.dataUrl.startsWith("data:") -> {
                    runCatching {
                        mediaEngine.importAudioDataUrl(aud.dataUrl, aud.id, aud.name, aud.format).assetUrl
                    }.getOrNull() ?: aud.audioUri
                }
                else -> aud.audioUri
            }
            aud.copy(
                audioUri = nextUri,
                dataUrl = if (nextUri != null) "" else aud.dataUrl
            )
        }
        return log.copy(
            pfpDataUrl = nextPfp,
            canvasBgDataUrl = nextBg,
            canvasImages = nextImages,
            audioAttachments = nextAudios
        )
    }

    private fun rewriteImageRef(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        if (ref.startsWith(ASSET_MEDIA_PREFIX) || ref.startsWith("http://") || ref.startsWith("https://")) {
            return ref
        }
        if (ref.startsWith("data:")) {
            return runCatching {
                mediaEngine.importImageFromDataUrl(ref)?.assetUrl
            }.getOrNull() ?: ref
        }
        val existingFile = repository.resolveExistingImageFile(ref)
        return if (existingFile != null) {
            "$ASSET_MEDIA_PREFIX${existingFile.name}"
        } else {
            ref
        }
    }

    private fun openAssetResponse(cleanPath: String): WebResourceResponse {
        val candidates = listOf("public/$cleanPath", cleanPath)
        for (candidate in candidates) {
            val stream = runCatching { appContext.assets.open(candidate) }.getOrNull() ?: continue
            val mime = guessMimeType(cleanPath)
            val encoding = if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml")) {
                "UTF-8"
            } else {
                null
            }
            return WebResourceResponse(mime, encoding, BufferedInputStream(stream))
        }
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            404,
            "Not Found",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun readLegacyInternalFile(filename: String): String {
        return try {
            val dir = File(appContext.filesDir, "likkho_internal_data")
            val safe = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, safe)
            if (file.exists() && file.isFile) file.readText(StandardCharsets.UTF_8) else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun hasLegacyInternalFile(filename: String): Boolean {
        return try {
            val dir = File(appContext.filesDir, "likkho_internal_data")
            val safe = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, safe)
            file.exists() && file.isFile && file.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun inferAudioExtFromMime(mimeType: String): String {
        val mime = mimeType.lowercase()
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

    private fun guessMimeType(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private const val TAG = "LegacyMigrationHelper"
        private const val PREFS_NAME = "likkho_saf_prefs"
        private const val KEY_MIGRATED_V1 = "legacy_webview_migrated_v1"
        private const val MIGRATION_TIMEOUT_MS = 20_000L
        private const val ASSET_MEDIA_PREFIX = "https://appassets.androidplatform.net/likkho-media/"
        private const val ASSET_AUDIO_PREFIX = "https://appassets.androidplatform.net/likkho-audio/"
    }
}

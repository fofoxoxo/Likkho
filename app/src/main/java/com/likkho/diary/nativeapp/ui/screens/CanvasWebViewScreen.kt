package com.likkho.diary.nativeapp.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.media.ImageAdjustmentParams
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CanvasAudioAttachment
import com.likkho.diary.nativeapp.model.CustomFontItem
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.MicRecordingSettings
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import kotlin.math.roundToInt

/**
 * Callback surface provided by [NativeMainActivity] so the embedded React Canvas (`LikkhoNative`)
 * can trigger native microphone foreground recording, MediaPlayer audio streaming, OS reminders,
 * haptic feedback, and permission prompts directly from WebView without Capacitor overhead.
 */
interface NativeCanvasActivityHost {
    fun registerCanvasWebView(webView: WebView?)
    fun onSystemPickerStateChanged(isOpen: Boolean)
    fun triggerHapticFeedback(kind: String)
    fun clearNativeSelection()
    fun requestFilesAndMediaPermission()
    fun requestMicrophonePermission(pendingWebRequest: PermissionRequest? = null)
    fun startNativeMicRecording(sampleRate: Int, bitRate: Int, logId: String)
    fun stopNativeMicRecording()
    fun isNativeMicRecordingActive(): Boolean
    fun getNativeMicRecordingSeconds(): Int
    fun consumePendingFinishedNativeRecordingJson(): String
    fun clearPendingFinishedNativeRecording()
    fun playNativeAudio(audioId: String, title: String, filePathOrUrl: String, playbackRate: Float, startMs: Int): Boolean
    fun pauseNativeAudio()
    fun resumeNativeAudio()
    fun seekNativeAudio(posMs: Int)
    fun setNativeAudioSpeed(rate: Float)
    fun stopNativeAudio(audioId: String?)
    fun scheduleNativeReminder(logId: String, title: String, body: String, triggerAtMs: Long)
    fun cancelNativeReminder(logId: String)
    fun openExternalUrl(url: String)
}

private data class PfpDialogRequest(val currentPfpUrl: String)

private data class ImageStudioDialogRequest(
    val sourceRef: String,
    val targetKind: String,
    val targetItemId: String,
    val initialOpacity: Float
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CanvasWebViewScreen(
    vaultMode: VaultMode,
    initialLog: DiaryLog?,
    startInEditMode: Boolean,
    isDarkTheme: Boolean,
    customFonts: List<CustomFontItem>,
    micSettings: MicRecordingSettings,
    savedWordAnalysesJson: String,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    activityHost: NativeCanvasActivityHost,
    onSaveLog: (DiaryLog, exitAfterSave: Boolean) -> Unit,
    onDeleteLog: (String) -> Unit,
    onAddCustomFont: (CustomFontItem) -> Unit,
    onWordAnalysesUpdated: (jsonArrayStr: String, mode: VaultMode) -> Unit,
    onExitWorkspace: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val colors = LikkhoTheme.colors

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isCanvasReady by remember { mutableStateOf(false) }

    var pendingPfpRequest by remember { mutableStateOf<PfpDialogRequest?>(null) }
    var pendingStudioRequest by remember { mutableStateOf<ImageStudioDialogRequest?>(null) }
    var isProcessingNativeMedia by remember { mutableStateOf(false) }

    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val initialPayloadJson = remember(vaultMode, initialLog?.id, startInEditMode) {
        JSONObject().apply {
            put("vaultMode", vaultMode.wireValue)
            put("isDarkTheme", isDarkTheme)
            put("startInEditMode", startInEditMode)
            put("log", initialLog?.toJson() ?: JSONObject.NULL)
            put("customFonts", JSONArray().apply { customFonts.forEach { put(it.toJson()) } })
            put("micSettings", micSettings.toJson())
            put("wordAnalysesJson", savedWordAnalysesJson.ifBlank { "[]" })
        }.toString()
    }
    var pendingInitialConsume by remember(initialPayloadJson) { mutableStateOf<String?>(initialPayloadJson) }

    fun evaluateJs(script: String) {
        mainHandler.post {
            webViewRef?.evaluateJavascript(script, null)
        }
    }

    // Propagate Dark Mode changes live to active WebView Canvas
    LaunchedEffect(isDarkTheme, isCanvasReady) {
        if (isCanvasReady) {
            evaluateJs("if(window.__likkhoCanvasSetTheme){window.__likkhoCanvasSetTheme($isDarkTheme);}")
        }
    }

    // Native File Chooser fallback for WebView inputs (.ttf custom font, document import)
    val webFileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        activityHost.onSystemPickerStateChanged(false)
        val callback = pendingFileChooserCallback
        pendingFileChooserCallback = null
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback?.onReceiveValue(uris)
    }

    // Native Gallery Picker for Canvas Images & Canvas Background Image
    var pendingImageTargetKind by remember { mutableStateOf("canvas_new") }
    val galleryImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        activityHost.onSystemPickerStateChanged(false)
        if (uri == null) return@rememberLauncherForActivityResult
        val targetKind = pendingImageTargetKind
        isProcessingNativeMedia = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    mediaEngine.importImageFromUri(uri)
                }
            }.onSuccess { imported ->
                val defaultOpacity = if (targetKind == "canvas_bg") 0.25f else 1f
                evaluateJs(
                    "if(window.__onLikkhoNativeImageResult){window.__onLikkhoNativeImageResult(${
                        JSONObject.quote(targetKind)
                    },'',${JSONObject.quote(imported.assetUrl)},${imported.width},${imported.height},${imported.sizeBytes},${imported.originalSizeBytes},$defaultOpacity);}"
                )
            }
            isProcessingNativeMedia = false
        }
    }

    // Native Document Picker for Audio Attachments
    val audioAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        activityHost.onSystemPickerStateChanged(false)
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessingNativeMedia = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    mediaEngine.importAudioFromUri(uri)
                }
            }.onSuccess { imported ->
                val audioObj = CanvasAudioAttachment(
                    id = imported.id,
                    name = imported.name,
                    format = imported.format,
                    dataUrl = "",
                    audioUri = imported.assetUrl,
                    durationSec = imported.durationSec,
                    createdAt = System.currentTimeMillis()
                )
                evaluateJs(
                    "if(window.__onLikkhoNativeAudioPicked){window.__onLikkhoNativeAudioPicked(${
                        JSONObject.quote(audioObj.toJson().toString())
                    });}"
                )
            }
            isProcessingNativeMedia = false
        }
    }

    // Hardware Back Press delegates to Canvas `handleExitWorkspace` (saves changes / exits cleanly)
    BackHandler {
        val view = webViewRef
        if (view != null && isCanvasReady) {
            mainHandler.post {
                view.evaluateJavascript(
                    "(function(){ if(window.__handleLikkhoAndroidBack){ return window.__handleLikkhoAndroidBack(); } return 'fallback'; })()"
                ) { res ->
                    if (res == null || res == "null" || res.contains("fallback")) {
                        onExitWorkspace()
                    }
                }
            }
        } else {
            onExitWorkspace()
        }
    }

    val assetLoader = remember(repository) {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler("/likkho-media/", WebViewAssetLoader.PathHandler { path ->
                val clean = path.trimStart('/')
                if (clean.isEmpty() || clean.contains("..")) return@PathHandler null
                val file = repository.resolveExistingImageFile(clean) ?: return@PathHandler null
                val mime = guessImageMimeFromHeaderOrExt(clean, file)
                runCatching {
                    WebResourceResponse(mime, null, BufferedInputStream(FileInputStream(file)))
                }.getOrNull()
            })
            .addPathHandler("/likkho-audio/", WebViewAssetLoader.PathHandler { path ->
                val clean = path.trimStart('/')
                if (clean.isEmpty() || clean.contains("..")) return@PathHandler null
                val file = runCatching {
                    repository.resolveConfinedFile(repository.mediaAudioDir, clean)
                        .takeIf { it.exists() && it.isFile }
                }.getOrNull()
                    ?: repository.resolveExistingAudioFile(clean)
                    ?: return@PathHandler null
                val mime = guessAudioMimeFromExt(file.name)
                runCatching {
                    WebResourceResponse(mime, null, BufferedInputStream(FileInputStream(file)))
                }.getOrNull()
            })
            .addPathHandler("/", WebViewAssetLoader.PathHandler { path ->
                val trimmed = path.trimStart('/')
                val target = if (trimmed.isEmpty()) "index.html" else trimmed
                if (target.contains("..")) return@PathHandler null
                val candidates = listOf("public/$target", target)
                for (candidate in candidates) {
                    val stream = runCatching { context.assets.open(candidate) }.getOrNull() ?: continue
                    val mime = guessWebAssetMime(target)
                    val encoding = if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml")) {
                        "UTF-8"
                    } else {
                        null
                    }
                    return@PathHandler WebResourceResponse(mime, encoding, BufferedInputStream(stream))
                }
                null
            })
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            activityHost.registerCanvasWebView(null)
            mainHandler.post {
                webViewRef?.apply {
                    stopLoading()
                    removeJavascriptInterface("LikkhoCanvasBridge")
                    removeJavascriptInterface("LikkhoNative")
                    destroy()
                }
                webViewRef = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun consumeInitialCanvasPayload(): String {
                                val next = pendingInitialConsume ?: ""
                                pendingInitialConsume = null
                                return next
                            }

                            @JavascriptInterface
                            fun onCanvasReady() {
                                mainHandler.post {
                                    isCanvasReady = true
                                }
                            }

                            @JavascriptInterface
                            fun saveLogJson(logJson: String, exitAfterSave: Boolean) {
                                runCatching {
                                    val parsed = DiaryLog.fromJson(JSONObject(logJson))
                                    mainHandler.post {
                                        onSaveLog(parsed, exitAfterSave)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun deleteLog(logId: String) {
                                mainHandler.post { onDeleteLog(logId) }
                            }

                            @JavascriptInterface
                            fun exitWithoutSave() {
                                mainHandler.post { onExitWorkspace() }
                            }

                            @JavascriptInterface
                            fun openNativePfpDialog(currentPfpUrl: String) {
                                mainHandler.post {
                                    pendingPfpRequest = PfpDialogRequest(currentPfpUrl)
                                }
                            }

                            @JavascriptInterface
                            fun pickCanvasImage() {
                                mainHandler.post {
                                    pendingImageTargetKind = "canvas_new"
                                    activityHost.onSystemPickerStateChanged(true)
                                    galleryImageLauncher.launch("image/*")
                                }
                            }

                            @JavascriptInterface
                            fun pickCanvasBgImage() {
                                mainHandler.post {
                                    pendingImageTargetKind = "canvas_bg"
                                    activityHost.onSystemPickerStateChanged(true)
                                    galleryImageLauncher.launch("image/*")
                                }
                            }

                            @JavascriptInterface
                            fun openNativeImageStudio(
                                mediaUrlOrId: String,
                                targetKind: String,
                                targetItemId: String,
                                currentOpacity: Double
                            ) {
                                mainHandler.post {
                                    pendingStudioRequest = ImageStudioDialogRequest(
                                        sourceRef = mediaUrlOrId,
                                        targetKind = targetKind,
                                        targetItemId = targetItemId,
                                        initialOpacity = currentOpacity.toFloat().coerceIn(0.05f, 1f)
                                    )
                                }
                            }

                            @JavascriptInterface
                            fun pickAudioAttachment() {
                                mainHandler.post {
                                    activityHost.onSystemPickerStateChanged(true)
                                    audioAttachmentLauncher.launch(arrayOf("audio/*"))
                                }
                            }

                            @JavascriptInterface
                            fun onAddCustomFontJson(fontJson: String) {
                                runCatching {
                                    val item = CustomFontItem.fromJson(JSONObject(fontJson))
                                    mainHandler.post { onAddCustomFont(item) }
                                }
                            }

                            @JavascriptInterface
                            fun onWordAnalysesUpdated(jsonArrayStr: String, modeWire: String) {
                                mainHandler.post {
                                    onWordAnalysesUpdated(jsonArrayStr, VaultMode.fromWire(modeWire))
                                }
                            }

                            @JavascriptInterface
                            fun openExternalUrl(url: String) {
                                mainHandler.post { activityHost.openExternalUrl(url) }
                            }
                        },
                        "LikkhoCanvasBridge"
                    )

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun isSystemFilePickerActive(): Boolean = false

                            @JavascriptInterface
                            fun triggerHaptic(kind: String) {
                                activityHost.triggerHapticFeedback(kind)
                            }

                            @JavascriptInterface
                            fun clearNativeSelectionAndActionMode() {
                                mainHandler.post { activityHost.clearNativeSelection() }
                            }

                            @JavascriptInterface
                            fun requestFilesAndMediaPermission() {
                                mainHandler.post { activityHost.requestFilesAndMediaPermission() }
                            }

                            @JavascriptInterface
                            fun requestMicPermission() {
                                mainHandler.post { activityHost.requestMicrophonePermission(null) }
                            }

                            @JavascriptInterface
                            fun startNativeMicRecording(sampleRate: Int, bitRate: Int) {
                                mainHandler.post {
                                    activityHost.startNativeMicRecording(sampleRate, bitRate, initialLog?.id ?: "")
                                }
                            }

                            @JavascriptInterface
                            fun startNativeMicRecordingWithLogId(sampleRate: Int, bitRate: Int, logId: String) {
                                mainHandler.post {
                                    activityHost.startNativeMicRecording(sampleRate, bitRate, logId)
                                }
                            }

                            @JavascriptInterface
                            fun stopNativeMicRecording() {
                                mainHandler.post { activityHost.stopNativeMicRecording() }
                            }

                            @JavascriptInterface
                            fun isNativeMicRecordingActive(): Boolean {
                                return activityHost.isNativeMicRecordingActive()
                            }

                            @JavascriptInterface
                            fun getNativeMicRecordingSeconds(): Int {
                                return activityHost.getNativeMicRecordingSeconds()
                            }

                            @JavascriptInterface
                            fun getPendingFinishedNativeRecording(): String {
                                return activityHost.consumePendingFinishedNativeRecordingJson()
                            }

                            @JavascriptInterface
                            fun clearPendingFinishedNativeRecording() {
                                activityHost.clearPendingFinishedNativeRecording()
                            }

                            @JavascriptInterface
                            fun playNativeAudio(
                                audioId: String,
                                title: String,
                                filePathOrUrl: String,
                                playbackRate: Double,
                                startMs: Int
                            ): Boolean {
                                return activityHost.playNativeAudio(
                                    audioId,
                                    title,
                                    filePathOrUrl,
                                    playbackRate.toFloat(),
                                    startMs
                                )
                            }

                            @JavascriptInterface
                            fun pauseNativeAudio() {
                                activityHost.pauseNativeAudio()
                            }

                            @JavascriptInterface
                            fun resumeNativeAudio() {
                                activityHost.resumeNativeAudio()
                            }

                            @JavascriptInterface
                            fun seekNativeAudio(posMs: Int) {
                                activityHost.seekNativeAudio(posMs)
                            }

                            @JavascriptInterface
                            fun setNativeAudioSpeed(rate: Double) {
                                activityHost.setNativeAudioSpeed(rate.toFloat())
                            }

                            @JavascriptInterface
                            fun stopNativeAudio(audioId: String?) {
                                activityHost.stopNativeAudio(audioId)
                            }

                            @JavascriptInterface
                            fun deleteNativeAudioFile(filePathOrUrl: String) {
                                val clean = repository.normalizeMediaId(filePathOrUrl)
                                runCatching {
                                    repository.resolveExistingAudioFile(clean)?.delete()
                                }
                            }

                            @JavascriptInterface
                            fun scheduleNativeReminder(logId: String, title: String, body: String, triggerAtMs: Long) {
                                activityHost.scheduleNativeReminder(logId, title, body, triggerAtMs)
                            }

                            @JavascriptInterface
                            fun cancelNativeReminder(logId: String) {
                                activityHost.cancelNativeReminder(logId)
                            }

                            @JavascriptInterface
                            fun notifyAppReady() {
                                mainHandler.post { isCanvasReady = true }
                            }
                        },
                        "LikkhoNative"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url ?: return null
                            if (url.host.equals(ASSET_DOMAIN, ignoreCase = true)) {
                                return assetLoader.shouldInterceptRequest(url)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return false
                            val str = url.toString()
                            if (url.host.equals(ASSET_DOMAIN, ignoreCase = true)) {
                                return false
                            }
                            if (str.startsWith("http://") || str.startsWith("https://") || str.startsWith("mailto:")) {
                                activityHost.openExternalUrl(str)
                                return true
                            }
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            if (request == null) return
                            activityHost.requestMicrophonePermission(request)
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            pendingFileChooserCallback?.onReceiveValue(null)
                            pendingFileChooserCallback = filePathCallback
                            val intent = runCatching {
                                fileChooserParams?.createIntent()
                            }.getOrNull() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            activityHost.onSystemPickerStateChanged(true)
                            return try {
                                webFileChooserLauncher.launch(intent)
                                true
                            } catch (_: Exception) {
                                activityHost.onSystemPickerStateChanged(false)
                                pendingFileChooserCallback = null
                                false
                            }
                        }
                    }

                    webViewRef = this
                    activityHost.registerCanvasWebView(this)
                    val themeQuery = if (isDarkTheme) "1" else "0"
                    loadUrl("https://$ASSET_DOMAIN/index.html?mode=canvas&dark=$themeQuery")
                }
            }
        )

        if (!isCanvasReady || isProcessingNativeMedia) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg.copy(alpha = if (!isCanvasReady) 0.96f else 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = colors.accent,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }

    // Native 1:1 PFP Cropper & Emoji/Text Square PFP Dialog
    pendingPfpRequest?.let { req ->
        NativePfpStudioDialog(
            currentPfpUrl = req.currentPfpUrl,
            mediaEngine = mediaEngine,
            repository = repository,
            onSystemPickerStateChanged = activityHost::onSystemPickerStateChanged,
            onDismiss = { pendingPfpRequest = null },
            onResult = { assetUrl ->
                pendingPfpRequest = null
                evaluateJs(
                    "if(window.__onLikkhoNativePfpResult){window.__onLikkhoNativePfpResult(${
                        JSONObject.quote(assetUrl)
                    });}"
                )
            }
        )
    }

    // Native Image Studio Dialog (Format, Compression Quality, Max Resolution, Filters, Rotation & Opacity)
    pendingStudioRequest?.let { req ->
        NativeImageStudioDialog(
            request = req,
            mediaEngine = mediaEngine,
            repository = repository,
            onDismiss = { pendingStudioRequest = null },
            onResult = { assetUrl, w, h, sizeBytes, origBytes, opacity ->
                pendingStudioRequest = null
                evaluateJs(
                    "if(window.__onLikkhoNativeImageResult){window.__onLikkhoNativeImageResult(${
                        JSONObject.quote(req.targetKind)
                    },${JSONObject.quote(req.targetItemId)},${JSONObject.quote(assetUrl)},$w,$h,$sizeBytes,$origBytes,$opacity);}"
                )
            }
        )
    }
}

@Composable
private fun NativePfpStudioDialog(
    currentPfpUrl: String,
    mediaEngine: NativeMediaEngine,
    repository: NativeVaultRepository,
    onSystemPickerStateChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LikkhoTheme.colors

    var tabIndex by remember { mutableIntStateOf(0) } // 0 = Photo 1:1, 1 = Emoji / Text
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    var emojiText by remember { mutableStateOf("📔") }
    var emojiScale by remember { mutableFloatStateOf(1f) }
    var selectedBgHex by remember { mutableStateOf("#3366cc") }
    var selectedFgHex by remember { mutableStateOf("#ffffff") }
    var isSaving by remember { mutableStateOf(false) }

    val pfpBgSwatches = remember {
        listOf("#3366cc", "#14866d", "#b32424", "#202122", "#6b4ba1", "#ac6600", "#f8f9fa", "#ffffff")
    }

    LaunchedEffect(pickedPhotoUri, currentPfpUrl) {
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    pickedPhotoUri != null -> context.contentResolver.openInputStream(pickedPhotoUri!!)?.use { s ->
                        decodeSampledBitmapStream(s, 720)
                    }
                    currentPfpUrl.isNotBlank() -> repository.resolveExistingImageFile(currentPfpUrl)?.let { file ->
                        decodeSampledBitmapFile(file.absolutePath, 720)
                    }
                    else -> null
                }
            }.getOrNull()
        }
        previewBitmap = bmp?.asImageBitmap()
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onSystemPickerStateChanged(false)
        if (uri != null) {
            pickedPhotoUri = uri
            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, colors.border, RectangleShape)
                .background(colors.bg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Diary PFP (1:1 Studio)",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(enabled = !isSaving) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.muted, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Photo (1:1)", "Emoji / Text").forEachIndexed { idx, label ->
                    val active = tabIndex == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .border(1.dp, if (active) colors.accent else colors.border, RectangleShape)
                            .background(if (active) colors.accent else colors.surface)
                            .clickable { tabIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) Color.White else colors.text
                        )
                    }
                }
            }

            if (tabIndex == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .border(1.5.dp, colors.accent, RectangleShape)
                            .background(colors.surface)
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dim = 196f * density
                                    panX = (panX + dragAmount.x / dim).coerceIn(-0.8f, 0.8f)
                                    panY = (panY + dragAmount.y / dim).coerceIn(-0.8f, 0.8f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = previewBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "PFP Crop Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = panX * size.width
                                        translationY = panY * size.height
                                    }
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Zoom ${String.format(java.util.Locale.US, "%.1f", zoom)}x (Drag photo to pan)",
                        fontSize = 11.sp,
                        color = colors.muted
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1f..4f,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .border(1.dp, colors.border, RectangleShape)
                                .background(colors.surface)
                                .clickable {
                                    onSystemPickerStateChanged(true)
                                    pickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Choose Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
                        }
                        if (currentPfpUrl.isNotBlank() || pickedPhotoUri != null) {
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .border(1.dp, colors.crimson.copy(alpha = 0.5f), RectangleShape)
                                    .background(colors.crimson.copy(alpha = 0.08f))
                                    .clickable { onResult("") }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.crimson)
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val previewBgColor = runCatching { Color(android.graphics.Color.parseColor(selectedBgHex)) }.getOrDefault(colors.accent)
                    val previewFgColor = if (selectedBgHex.equals("#f8f9fa", true) || selectedBgHex.equals("#ffffff", true)) {
                        Color(0xFF202122)
                    } else {
                        Color.White
                    }
                    LaunchedEffect(selectedBgHex) {
                        selectedFgHex = if (selectedBgHex.equals("#f8f9fa", true) || selectedBgHex.equals("#ffffff", true)) {
                            "#202122"
                        } else {
                            "#ffffff"
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .border(1.dp, colors.border, RectangleShape)
                            .background(previewBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emojiText.ifBlank { "📔" },
                            fontSize = (42f * emojiScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = previewFgColor
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    BasicTextField(
                        value = emojiText,
                        onValueChange = { if (it.length <= 8) emojiText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = colors.text, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(1.dp, colors.border, RectangleShape)
                            .background(colors.surface)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = emojiScale,
                        onValueChange = { emojiScale = it },
                        valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pfpBgSwatches.forEach { hex ->
                            val swatchColor = Color(android.graphics.Color.parseColor(hex))
                            val selected = selectedBgHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.border, RectangleShape)
                                    .background(swatchColor)
                                    .clickable { selectedBgHex = hex }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .border(1.dp, colors.border, RectangleShape)
                        .background(colors.bg)
                        .clickable(enabled = !isSaving) { onDismiss() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", fontSize = 12.sp, color = colors.text)
                }
                Spacer(modifier = Modifier.width(8.dp))
                val canSave = tabIndex == 1 || pickedPhotoUri != null || currentPfpUrl.isNotBlank()
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(if (canSave) colors.accent else colors.muted)
                        .clickable(enabled = canSave && !isSaving) {
                            isSaving = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (tabIndex == 1) {
                                            mediaEngine.renderEmojiTextPfp(
                                                rawText = emojiText,
                                                bgHex = selectedBgHex,
                                                fgHex = selectedFgHex,
                                                fontScale = emojiScale
                                            )
                                        } else if (pickedPhotoUri != null) {
                                            mediaEngine.cropSquarePfpFromUri(
                                                uri = pickedPhotoUri!!,
                                                zoom = zoom,
                                                panXFraction = panX,
                                                panYFraction = panY
                                            )
                                        } else {
                                            mediaEngine.cropSquarePfpFromMediaId(
                                                mediaIdOrRef = currentPfpUrl,
                                                zoom = zoom,
                                                panXFraction = panX,
                                                panYFraction = panY
                                            )
                                        }
                                    }.getOrNull()
                                }
                                isSaving = false
                                if (res != null) {
                                    onResult(res.assetUrl)
                                }
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isSaving) "Saving..." else "Set PFP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NativeImageStudioDialog(
    request: ImageStudioDialogRequest,
    mediaEngine: NativeMediaEngine,
    repository: NativeVaultRepository,
    onDismiss: () -> Unit,
    onResult: (assetUrl: String, width: Int, height: Int, sizeBytes: Long, origBytes: Long, opacity: Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val colors = LikkhoTheme.colors

    var sourceBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var format by remember { mutableStateOf("image/jpeg") }
    var quality by remember { mutableIntStateOf(88) }
    var maxRes by remember { mutableIntStateOf(2560) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var opacity by remember { mutableFloatStateOf(request.initialOpacity) }
    var grayscale by remember { mutableStateOf(false) }
    var sepia by remember { mutableStateOf(false) }
    var invert by remember { mutableStateOf(false) }
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(request.sourceRef) {
        val bmp = withContext(Dispatchers.IO) {
            repository.resolveExistingImageFile(request.sourceRef)?.let { file ->
                decodeSampledBitmapFile(file.absolutePath, 720)
            }
        }
        sourceBitmap = bmp?.asImageBitmap()
    }

    val previewColorFilter = remember(brightness, contrast, saturation, grayscale, sepia, invert) {
        val cm = ColorMatrix()
        if (grayscale) {
            cm.postConcat(ColorMatrix().apply { setSaturation(0f) })
        } else if (saturation != 0f) {
            cm.postConcat(ColorMatrix().apply { setSaturation((1f + saturation / 100f).coerceIn(0f, 3f)) })
        }
        if (sepia) {
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (invert) {
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (contrast != 0f || brightness != 0f) {
            val scale = (1f + contrast / 100f).coerceIn(0.2f, 2.5f)
            val translate = (brightness * 2.55f) + 128f * (1f - scale)
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(cm.array)
        )
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(1.dp, colors.border, RectangleShape)
                .background(colors.bg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Native Image Studio",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .border(1.dp, colors.border, RectangleShape)
                            .clickable {
                                brightness = 0f
                                contrast = 0f
                                saturation = 0f
                                grayscale = false
                                sepia = false
                                invert = false
                                rotationDeg = 0f
                                opacity = 1f
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reset", fontSize = 11.sp, color = colors.text)
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = !isProcessing) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .border(1.dp, colors.border, RectangleShape)
                        .background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Edited Preview",
                            contentScale = ContentScale.Fit,
                            colorFilter = previewColorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(opacity)
                                .graphicsLayer { rotationZ = rotationDeg }
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.accent, strokeWidth = 2.dp)
                    }
                }

                // Format & Rotate row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("image/jpeg" to "JPEG", "image/webp" to "WebP", "image/png" to "PNG").forEach { (mime, label) ->
                        val active = format == mime
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .border(1.dp, if (active) colors.accent else colors.border, RectangleShape)
                                .background(if (active) colors.accent else colors.surface)
                                .clickable { format = mime },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else colors.text)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .border(1.dp, colors.border, RectangleShape)
                            .background(colors.surface)
                            .clickable { rotationDeg = (rotationDeg + 90f) % 360f }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.RotateRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                            Text("${rotationDeg.toInt()}°", fontSize = 11.sp, color = colors.text)
                        }
                    }
                }

                // Resolution chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(640, 1280, 1920, 2560).forEach { dim ->
                        val active = maxRes == dim
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .border(1.dp, if (active) colors.accent else colors.border, RectangleShape)
                                .background(if (active) colors.accent.copy(alpha = 0.14f) else colors.surface)
                                .clickable { maxRes = dim },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${dim}px", fontSize = 11.sp, fontFamily = LikkhoFontFamilies.Mono, color = if (active) colors.accent else colors.text)
                        }
                    }
                }

                if (format != "image/png") {
                    StudioSliderRow("Quality", "${quality}%", quality.toFloat(), 25f..100f, colors.accent, colors.text) {
                        quality = it.roundToInt()
                    }
                }
                StudioSliderRow("Opacity", "${(opacity * 100).roundToInt()}%", opacity, 0.05f..1f, colors.accent, colors.text) {
                    opacity = it
                }
                StudioSliderRow("Brightness", "${brightness.roundToInt()}", brightness, -100f..100f, colors.accent, colors.text) {
                    brightness = it
                }
                StudioSliderRow("Contrast", "${contrast.roundToInt()}", contrast, -100f..100f, colors.accent, colors.text) {
                    contrast = it
                }
                StudioSliderRow("Saturation", "${saturation.roundToInt()}", saturation, -100f..100f, colors.accent, colors.text) {
                    saturation = it
                }

                // Filter toggles
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChipBox("Grayscale", grayscale, colors.accent, colors.border, colors.surface, colors.text, Modifier.weight(1f)) {
                        grayscale = !grayscale
                    }
                    FilterChipBox("Sepia", sepia, colors.accent, colors.border, colors.surface, colors.text, Modifier.weight(1f)) {
                        sepia = !sepia
                    }
                    FilterChipBox("Invert", invert, colors.accent, colors.border, colors.surface, colors.text, Modifier.weight(1f)) {
                        invert = !invert
                    }
                }

                errorMsg?.let { msg ->
                    Text(msg, fontSize = 11.sp, color = colors.crimson)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .border(1.dp, colors.border, RectangleShape)
                        .background(colors.bg)
                        .clickable(enabled = !isProcessing) { onDismiss() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", fontSize = 12.sp, color = colors.text)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(if (!isProcessing) colors.accent else colors.muted)
                        .clickable(enabled = !isProcessing) {
                            isProcessing = true
                            errorMsg = null
                            scope.launch {
                                val params = ImageAdjustmentParams(
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    grayscale = grayscale,
                                    sepia = sepia,
                                    invert = invert,
                                    rotationDegrees = rotationDeg,
                                    opacity = opacity,
                                    outputFormat = format,
                                    qualityPercent = quality,
                                    maxDimension = maxRes
                                )
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        mediaEngine.applyImageAdjustments(
                                            sourceMediaIdOrRef = request.sourceRef,
                                            params = params,
                                            targetId = null
                                        )
                                    }
                                }
                                isProcessing = false
                                result.onSuccess { res ->
                                    onResult(
                                        res.assetUrl,
                                        res.width,
                                        res.height,
                                        res.sizeBytes,
                                        res.originalSizeBytes,
                                        opacity
                                    )
                                }.onFailure { err ->
                                    errorMsg = err.message ?: "Could not process image."
                                }
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isProcessing) "Processing..." else "Apply Changes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    textColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text(valueText, fontSize = 11.sp, fontFamily = LikkhoFontFamilies.Mono, color = textColor)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }
}

@Composable
private fun FilterChipBox(
    label: String,
    active: Boolean,
    accent: Color,
    border: Color,
    surface: Color,
    text: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .border(1.dp, if (active) accent else border, RectangleShape)
            .background(if (active) accent else surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else text)
    }
}

private fun decodeSampledBitmapStream(stream: java.io.InputStream, maxDim: Int): Bitmap? {
    val bytes = stream.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxSide / (sample * 2) >= maxDim) sample *= 2
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )
}

private fun decodeSampledBitmapFile(path: String, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxSide / (sample * 2) >= maxDim) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun guessWebAssetMime(path: String): String {
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

private fun guessImageMimeFromHeaderOrExt(clean: String, file: java.io.File): String {
    val ext = clean.substringAfterLast('.', "").lowercase()
    if (ext == "png") return "image/png"
    if (ext == "webp") return "image/webp"
    if (ext == "gif") return "image/gif"
    if (ext == "svg") return "image/svg+xml"
    val header = ByteArray(12)
    runCatching {
        FileInputStream(file).use { it.read(header) }
    }
    return when {
        header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> "image/png"
        header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[8] == 'W'.code.toByte() -> "image/webp"
        header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() -> "image/gif"
        header[0] == '<'.code.toByte() -> "image/svg+xml"
        else -> "image/jpeg"
    }
}

private fun guessAudioMimeFromExt(name: String): String {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        else -> "audio/wav"
    }
}

private const val ASSET_DOMAIN = "appassets.androidplatform.net"

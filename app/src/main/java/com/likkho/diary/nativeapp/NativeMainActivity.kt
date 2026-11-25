package com.likkho.diary.nativeapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.PermissionRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.likkho.diary.nativeapp.crypto.StreamCryptoEngine
import com.likkho.diary.nativeapp.data.LegacyWebViewMigrationHelper
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.export.NativeExportEngine
import com.likkho.diary.nativeapp.export.NativeExportFormat
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CanvasAudioAttachment
import com.likkho.diary.nativeapp.model.CanvasDraggableImage
import com.likkho.diary.nativeapp.model.CustomFontItem
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.MicRecordingSettings
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.VaultBackupBundle
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.screens.CanvasWebViewScreen
import com.likkho.diary.nativeapp.ui.screens.HomeScreen
import com.likkho.diary.nativeapp.ui.screens.NativeCanvasActivityHost
import com.likkho.diary.nativeapp.ui.screens.PasscodeLockScreen
import com.likkho.diary.nativeapp.ui.screens.QuickNoteModal
import com.likkho.diary.nativeapp.ui.screens.QuickNoteSystemSync
import com.likkho.diary.nativeapp.ui.screens.SettingsScreen
import com.likkho.diary.nativeapp.ui.screens.SettingsSubPage
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private enum class NativeScreenRoute {
    HOME,
    CANVAS,
    SETTINGS
}

private data class PendingSharedContent(
    val title: String,
    val text: String,
    val mediaUri: Uri?
)

/**
 * Pure Native Android Entry Activity for Likkho (Option 1 Hybrid Native Cutover).
 *
 * - Owns App Shell, Theme / Dark Mode, Edge-to-Edge System Bars, Biometric / Dual-Vault Lockscreen,
 *   HomeScreen, SettingsScreen, QuickNoteModal, One-Time Legacy Migration Gate, MediaPlayer Audio Service,
 *   MicForegroundService Control, AlarmManager Reminders, and Intent Dispatch (`.likkho`, `ACTION_SEND`, Shortcuts/Tiles).
 * - Embeds the React `WritingWorkspace` exclusively inside [CanvasWebViewScreen] via `WebViewAssetLoader`.
 */
class NativeMainActivity : FragmentActivity(), NativeCanvasActivityHost {

    private lateinit var repository: NativeVaultRepository
    private lateinit var mediaEngine: NativeMediaEngine
    private lateinit var streamCryptoEngine: StreamCryptoEngine
    private lateinit var exportEngine: NativeExportEngine

    private val mainHandler = Handler(Looper.getMainLooper())

    // Core UI State (Compose-observed)
    private var isMigratingLegacyData by mutableStateOf(true)
    private var isDarkTheme by mutableStateOf(false)
    private var isFullScreen by mutableStateOf(false)
    private var isLocked by mutableStateOf(true)
    private var currentVaultMode by mutableStateOf(VaultMode.PRIMARY)
    private var currentRoute by mutableStateOf(NativeScreenRoute.HOME)
    private var settingsInitialSubPage by mutableStateOf(SettingsSubPage.MAIN)

    // Hydrated Active Vault State
    private var logs by mutableStateOf<List<DiaryLog>>(emptyList())
    private var customFonts by mutableStateOf<List<CustomFontItem>>(emptyList())
    private var micSettings by mutableStateOf(MicRecordingSettings())
    private var quickNotes by mutableStateOf<List<QuickNote>>(emptyList())
    private var savedWordAnalysesJson by mutableStateOf("[]")

    // Canvas Workspace State
    private var editingLog by mutableStateOf<DiaryLog?>(null)
    private var canvasStartInEditMode by mutableStateOf(false)

    // Standalone Quick Note Launch Overlay (Widget / QS Tile / Notification / Shortcut)
    private var showStandaloneQuickNoteModal by mutableStateOf(false)
    private var openedFromExternalQuickNoteIntent by mutableStateOf(false)

    // Status & Export Progress Banners
    private var statusBanner by mutableStateOf<String?>(null)
    private var processingBanner by mutableStateOf<String?>(null)

    // Deferred Intent Actions (executed right after migration & unlock)
    private var pendingNewDiaryIntent = false
    private var pendingIncomingArchiveUri: Uri? = null
    private var pendingSharedContent: PendingSharedContent? = null

    // Security & Picker Tracking
    private var isSystemPickerActive = false
    private var recentsPrivacyOverlayView: View? = null

    // WebView Canvas Bridge State
    private var activeCanvasWebView: WebView? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingMicSampleRate: Int = 44_100
    private var pendingMicLogId: String = ""

    // Native Audio Playback State
    private var nativeMediaPlayer: MediaPlayer? = null
    private var activeAudioId: String? = null
    private var activeAudioTitle: String = "Likkho Audio"
    private var activeAudioSpeed: Float = 1.0f

    private val audioProgressRunnable: Runnable = object : Runnable {
        override fun run() {
            val mp = nativeMediaPlayer
            val audioId = activeAudioId
            if (mp != null && audioId != null) {
                runCatching {
                    val playing = mp.isPlaying
                    val curSec = mp.currentPosition / 1000.0
                    val durSec = mp.duration.coerceAtLeast(0) / 1000.0
                    emitNativeAudioProgress(audioId, curSec, durSec, playing, false)
                    if (playing) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
        }
    }

    // Modern ActivityResult Permission Launchers
    private lateinit var micPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var notifPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var mediaPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent Android system dark mode from overriding Likkho's explicit Light/Dark bar styling
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        repository = NativeVaultRepository(applicationContext)
        mediaEngine = NativeMediaEngine(applicationContext, repository)
        streamCryptoEngine = StreamCryptoEngine(applicationContext, repository)
        exportEngine = NativeExportEngine(applicationContext, repository, mediaEngine)

        isDarkTheme = repository.isDarkTheme()
        isFullScreen = repository.isFullScreenEnabled()

        val initialBgHex = if (isDarkTheme) "#101418" else "#ffffff"
        runCatching {
            window?.setBackgroundDrawable(ColorDrawable(AndroidColor.parseColor(initialBgHex)))
        }

        super.onCreate(savedInstanceState)
        currentInstance = this

        initPermissionLaunchers()
        applySystemBarsAndFullScreen(isDarkTheme, isFullScreen)
        applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())

        parseIncomingIntent(intent)

        setContent {
            LikkhoTheme(darkTheme = isDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LikkhoTheme.colors.bg)
                ) {
                    when {
                        isMigratingLegacyData -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Likkho",
                                    fontFamily = LikkhoFontFamilies.Serif,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LikkhoTheme.colors.text
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Preparing encrypted vault...",
                                    fontSize = 12.sp,
                                    color = LikkhoTheme.colors.muted
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = LikkhoTheme.colors.accent,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        showStandaloneQuickNoteModal -> {
                            QuickNoteModal(
                                initialNote = null,
                                onDismiss = { dismissStandaloneQuickNoteModal() },
                                onSave = { savedNote ->
                                    saveStandaloneQuickNote(savedNote)
                                }
                            )
                        }

                        isLocked -> {
                            PasscodeLockScreen(
                                repository = repository,
                                biometricsEnabled = repository.isBiometricEnabled(),
                                onUnlocked = { unlockedMode ->
                                    onVaultUnlocked(unlockedMode)
                                }
                            )
                        }

                        else -> {
                            when (currentRoute) {
                                NativeScreenRoute.HOME -> {
                                    HomeScreen(
                                        logs = logs,
                                        repository = repository,
                                        mediaEngine = mediaEngine,
                                        statusBanner = statusBanner,
                                        processingBanner = processingBanner,
                                        onDismissStatusBanner = { statusBanner = null },
                                        onLockApp = { lockVault() },
                                        onOpenSettings = {
                                            settingsInitialSubPage = SettingsSubPage.MAIN
                                            currentRoute = NativeScreenRoute.SETTINGS
                                        },
                                        onCreateDiary = {
                                            editingLog = null
                                            canvasStartInEditMode = true
                                            currentRoute = NativeScreenRoute.CANVAS
                                        },
                                        onOpenDiary = { log ->
                                            editingLog = log
                                            canvasStartInEditMode = false
                                            currentRoute = NativeScreenRoute.CANVAS
                                        },
                                        onTogglePin = { log ->
                                            val updated = log.copy(pinned = !log.pinned)
                                            lifecycleScope.launch {
                                                val refreshed = withContext(Dispatchers.IO) {
                                                    repository.upsertLog(currentVaultMode, updated)
                                                }
                                                logs = refreshed
                                            }
                                        },
                                        onShareLikkho = { log ->
                                            shareDiaryAsLikkho(log)
                                        },
                                        onExportDiary = { log, format ->
                                            exportSingleDiary(log, format)
                                        },
                                        onDeleteDiary = { log ->
                                            cancelNativeReminder(log.id)
                                            lifecycleScope.launch {
                                                val remaining = withContext(Dispatchers.IO) {
                                                    repository.deleteLog(currentVaultMode, log.id)
                                                }
                                                logs = remaining
                                                statusBanner = "Deleted \"${log.heading.ifBlank { "Untitled" }}\"."
                                            }
                                        }
                                    )
                                }

                                NativeScreenRoute.CANVAS -> {
                                    CanvasWebViewScreen(
                                        vaultMode = currentVaultMode,
                                        initialLog = editingLog,
                                        startInEditMode = canvasStartInEditMode,
                                        isDarkTheme = isDarkTheme,
                                        customFonts = customFonts,
                                        micSettings = micSettings,
                                        savedWordAnalysesJson = savedWordAnalysesJson,
                                        repository = repository,
                                        mediaEngine = mediaEngine,
                                        activityHost = this@NativeMainActivity,
                                        onSaveLog = { savedLog, exitAfterSave ->
                                            handleCanvasSaveLog(savedLog, exitAfterSave)
                                        },
                                        onDeleteLog = { deletedId ->
                                            handleCanvasDeleteLog(deletedId)
                                        },
                                        onAddCustomFont = { newFont ->
                                            handleAddCustomFont(newFont)
                                        },
                                        onWordAnalysesUpdated = { jsonStr, targetMode ->
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                repository.saveSavedWordAnalysesJson(targetMode, jsonStr)
                                            }
                                            if (targetMode == currentVaultMode) {
                                                savedWordAnalysesJson = jsonStr
                                            }
                                        },
                                        onExitWorkspace = {
                                            editingLog = null
                                            currentRoute = NativeScreenRoute.HOME
                                        }
                                    )
                                }

                                NativeScreenRoute.SETTINGS -> {
                                    SettingsScreen(
                                        vaultMode = currentVaultMode,
                                        isDarkTheme = isDarkTheme,
                                        isFullScreen = isFullScreen,
                                        logs = logs,
                                        customFonts = customFonts,
                                        micSettings = micSettings,
                                        quickNotes = quickNotes,
                                        repository = repository,
                                        streamCryptoEngine = streamCryptoEngine,
                                        mediaEngine = mediaEngine,
                                        exportEngine = exportEngine,
                                        onToggleDarkTheme = { enabled ->
                                            isDarkTheme = enabled
                                            repository.setDarkTheme(enabled)
                                            applySystemBarsAndFullScreen(enabled, isFullScreen)
                                        },
                                        onToggleFullScreen = { enabled ->
                                            isFullScreen = enabled
                                            repository.setFullScreenEnabled(enabled)
                                            applySystemBarsAndFullScreen(isDarkTheme, enabled)
                                        },
                                        onMicSettingsChanged = { updatedMic ->
                                            micSettings = updatedMic
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                repository.saveMicSettings(currentVaultMode, updatedMic)
                                            }
                                        },
                                        onQuickNotesUpdated = { updatedNotes ->
                                            quickNotes = updatedNotes
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                repository.saveQuickNotes(currentVaultMode, updatedNotes)
                                                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(
                                                    this@NativeMainActivity,
                                                    updatedNotes
                                                )
                                            }
                                        },
                                        onBundleRestored = { bundle ->
                                            applyRestoredBackupBundle(bundle)
                                        },
                                        onRestoreSingleDiary = { restoredLog ->
                                            lifecycleScope.launch {
                                                val nextLogs = withContext(Dispatchers.IO) {
                                                    repository.upsertLog(currentVaultMode, restoredLog)
                                                }
                                                logs = nextLogs
                                                statusBanner = "Restored \"${restoredLog.heading.ifBlank { "Diary Entry" }}\"."
                                            }
                                        },
                                        onSwitchedToPrimaryVault = {
                                            onVaultUnlocked(VaultMode.PRIMARY)
                                        },
                                        onClearAllDataCompleted = {
                                            handleClearAllDataCompleted()
                                        },
                                        onBack = {
                                            applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())
                                            currentRoute = NativeScreenRoute.HOME
                                        },
                                        initialSubPage = settingsInitialSubPage
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Run one-time Legacy Capacitor WebView -> Native Encrypted Vault migration if applicable
        val migrationHelper = LegacyWebViewMigrationHelper(this, repository, mediaEngine)
        migrationHelper.runMigrationIfNeeded { _ ->
            finishStartupAfterMigration()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncomingIntent(intent)
        if (!isMigratingLegacyData) {
            processPendingActionsIfReady()
        }
    }

    override fun onResume() {
        super.onResume()
        currentInstance = this
        isSystemPickerActive = false
        hideRecentsPrivacyShield()
        applySystemBarsAndFullScreen(isDarkTheme, isFullScreen)
        applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())

        checkPendingQuickNoteDoneFromNotification()
        deliverFinishedNativeRecordingIfPossible(this)
        if (!isMigratingLegacyData && !isLocked) {
            attachOrDispatchPendingRecording()
            processPendingActionsIfReady()
        }
    }

    override fun onPause() {
        clearNativeSelection()
        if (repository.isAppPasscodeProtectionEnabled() && !isSystemPickerActive && isFinishing) {
            showRecentsPrivacyShield()
        }
        super.onPause()
    }

    override fun onStop() {
        clearNativeSelection()
        if (repository.isAppPasscodeProtectionEnabled() && !isSystemPickerActive) {
            showRecentsPrivacyShield()
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideRecentsPrivacyShield()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsAndFullScreen(isDarkTheme, isFullScreen)
    }

    override fun onDestroy() {
        stopNativeAudio(null)
        if (currentInstance === this) {
            currentInstance = null
        }
        super.onDestroy()
    }

    // ==================== Startup & Vault Hydration ====================

    private fun initPermissionLaunchers() {
        micPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val webReq = pendingWebPermissionRequest
            pendingWebPermissionRequest = null
            if (webReq != null) {
                runCatching {
                    if (granted) webReq.grant(webReq.resources) else webReq.deny()
                }
            } else if (granted) {
                startMicForegroundServiceInternal(pendingMicSampleRate, pendingMicLogId)
            } else {
                Toast.makeText(this, "Microphone permission is required for recording.", Toast.LENGTH_SHORT).show()
            }
        }

        notifPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ ->
            // Quick note notifications will publish cleanly next sync if granted
        }

        mediaPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }
    }

    private fun finishStartupAfterMigration() {
        isDarkTheme = repository.isDarkTheme()
        isFullScreen = repository.isFullScreenEnabled()
        applySystemBarsAndFullScreen(isDarkTheme, isFullScreen)
        applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())

        isMigratingLegacyData = false

        if (!repository.isAppPasscodeProtectionEnabled()) {
            onVaultUnlocked(VaultMode.PRIMARY)
        } else {
            isLocked = true
            lifecycleScope.launch(Dispatchers.IO) {
                val primaryNotes = repository.loadQuickNotes(VaultMode.PRIMARY)
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(this@NativeMainActivity, primaryNotes)
            }
        }

        checkPendingQuickNoteDoneFromNotification()
        processPendingActionsIfReady()

        if (!showStandaloneQuickNoteModal && Build.VERSION.SDK_INT >= 33) {
            mainHandler.postDelayed({
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    runCatching { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            }, 900L)
        }
    }

    private fun onVaultUnlocked(mode: VaultMode) {
        currentVaultMode = mode
        isLocked = false
        currentRoute = NativeScreenRoute.HOME
        editingLog = null
        statusBanner = null

        lifecycleScope.launch {
            val hydrated = withContext(Dispatchers.IO) {
                HydratedVaultData(
                    logs = repository.loadLogs(mode),
                    customFonts = repository.loadCustomFonts(mode),
                    micSettings = repository.loadMicSettings(mode),
                    quickNotes = repository.loadQuickNotes(mode),
                    wordAnalysesJson = repository.loadSavedWordAnalysesJson(mode)
                )
            }
            logs = hydrated.logs
            customFonts = hydrated.customFonts
            micSettings = hydrated.micSettings
            quickNotes = hydrated.quickNotes
            savedWordAnalysesJson = hydrated.wordAnalysesJson

            withContext(Dispatchers.IO) {
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(
                    this@NativeMainActivity,
                    hydrated.quickNotes
                )
            }

            applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())
            attachOrDispatchPendingRecording()
            processPendingActionsIfReady()
        }
    }

    private fun lockVault() {
        stopNativeAudio(null)
        editingLog = null
        activeCanvasWebView = null
        logs = emptyList()
        customFonts = emptyList()
        quickNotes = emptyList()
        savedWordAnalysesJson = "[]"
        statusBanner = null
        currentRoute = NativeScreenRoute.HOME
        isLocked = true
        applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())
    }

    private fun handleClearAllDataCompleted() {
        stopNativeAudio(null)
        cancelAllLoadedReminders()
        isDarkTheme = false
        isFullScreen = false
        logs = emptyList()
        customFonts = emptyList()
        micSettings = MicRecordingSettings()
        quickNotes = emptyList()
        savedWordAnalysesJson = "[]"
        editingLog = null
        currentVaultMode = VaultMode.PRIMARY
        currentRoute = NativeScreenRoute.HOME
        isLocked = false
        applySystemBarsAndFullScreen(false, false)
        applyPasscodeSecurityFlags(false)
        statusBanner = "All Likkho vault data cleared."
    }

    private fun cancelAllLoadedReminders() {
        for (l in logs) {
            if (l.reminderAt != null) {
                cancelNativeReminder(l.id)
            }
        }
    }

    private fun applyRestoredBackupBundle(bundle: VaultBackupBundle) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveLogs(currentVaultMode, bundle.logs)
                repository.saveCustomFonts(currentVaultMode, bundle.customFonts)
                repository.saveMicSettings(currentVaultMode, bundle.micSettings)
                repository.saveQuickNotes(currentVaultMode, bundle.quickNotes)
                repository.saveSavedPasswords(currentVaultMode, bundle.savedPasswords)
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(
                    this@NativeMainActivity,
                    bundle.quickNotes
                )
            }
            logs = bundle.logs
            customFonts = bundle.customFonts
            micSettings = bundle.micSettings
            quickNotes = bundle.quickNotes
            currentRoute = NativeScreenRoute.HOME
            statusBanner = if (bundle.skippedMediaCount > 0) {
                "Restored ${bundle.logs.size} diaries (${bundle.skippedMediaCount} media skipped)."
            } else {
                "Restored ${bundle.logs.size} encrypted diaries to vault."
            }
        }
    }

    // ==================== Canvas Workspace Callbacks ====================

    private fun handleCanvasSaveLog(savedLog: DiaryLog, exitAfterSave: Boolean) {
        lifecycleScope.launch {
            val nextLogs = withContext(Dispatchers.IO) {
                repository.upsertLog(currentVaultMode, savedLog)
            }
            logs = nextLogs
            editingLog = savedLog
            if (exitAfterSave) {
                editingLog = null
                currentRoute = NativeScreenRoute.HOME
            }
        }
    }

    private fun handleCanvasDeleteLog(deletedId: String) {
        cancelNativeReminder(deletedId)
        lifecycleScope.launch {
            val remaining = withContext(Dispatchers.IO) {
                repository.deleteLog(currentVaultMode, deletedId)
            }
            logs = remaining
            editingLog = null
            currentRoute = NativeScreenRoute.HOME
            statusBanner = "Diary entry deleted."
        }
    }

    private fun handleAddCustomFont(newFont: CustomFontItem) {
        val next = (customFonts.filterNot { it.id == newFont.id } + newFont)
        customFonts = next
        lifecycleScope.launch(Dispatchers.IO) {
            repository.saveCustomFonts(currentVaultMode, next)
        }
    }

    // ==================== HomeScreen Export & `.likkho` Share ====================

    private fun shareDiaryAsLikkho(log: DiaryLog) {
        processingBanner = "Packing \"${log.heading.ifBlank { "Diary" }}\" (.likkho)..."
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val file = exportEngine.packDiaryToLikkhoFile(log)
                    exportEngine.buildShareLikkhoIntent(file, log.heading)
                }
            }
            processingBanner = null
            res.onSuccess { chooserIntent ->
                isSystemPickerActive = true
                runCatching { startActivity(chooserIntent) }
                    .onFailure {
                        isSystemPickerActive = false
                        Toast.makeText(
                            this@NativeMainActivity,
                            "Unable to open share sheet: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }.onFailure { err ->
                Toast.makeText(
                    this@NativeMainActivity,
                    "Share error: ${err.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportSingleDiary(log: DiaryLog, format: NativeExportFormat) {
        processingBanner = "Exporting \"${log.heading.ifBlank { "Diary" }}\" (.${format.ext})..."
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    exportEngine.exportDiaryToDownloads(log, format)
                }
            }
            processingBanner = null
            res.onSuccess { exported ->
                statusBanner = "Saved to ${exported.displayLocation}"
                Toast.makeText(
                    this@NativeMainActivity,
                    "Saved to ${exported.displayLocation}",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { err ->
                Toast.makeText(
                    this@NativeMainActivity,
                    "Export failed: ${err.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ==================== Standalone Quick Note & Intent Handling ====================

    private fun parseIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        val action = incoming.action
        val extraAction = incoming.getStringExtra("action")
        val dataUri = incoming.data

        if ("quick_note" == extraAction ||
            "com.likkho.diary.ACTION_QUICK_NOTE" == action ||
            "quick_note" == dataUri?.getQueryParameter("action")
        ) {
            openedFromExternalQuickNoteIntent = true
            showStandaloneQuickNoteModal = true
            clearIntentAction(incoming)
            return
        }

        if ("new_diary" == extraAction ||
            "com.likkho.diary.ACTION_NEW_DIARY" == action ||
            "new_diary" == dataUri?.getQueryParameter("action")
        ) {
            pendingNewDiaryIntent = true
            clearIntentAction(incoming)
            return
        }

        if (Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action || Intent.ACTION_SEND == action) {
            @Suppress("DEPRECATION")
            val streamUri: Uri? = if (Intent.ACTION_SEND == action) {
                incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: incoming.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            } else {
                null
            }
            val candidateUri = dataUri ?: streamUri

            if (Intent.ACTION_SEND == action && !isLikelyLikkhoArchiveIntent(incoming, candidateUri)) {
                val subject = incoming.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: incoming.getStringExtra(Intent.EXTRA_TITLE)
                    ?: ""
                val text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: incoming.getStringExtra(Intent.EXTRA_TEXT)
                    ?: ""
                if (subject.isNotBlank() || text.isNotBlank() || candidateUri != null) {
                    pendingSharedContent = PendingSharedContent(subject.trim(), text.trim(), candidateUri)
                }
                clearIntentAction(incoming)
            } else if (candidateUri != null) {
                pendingIncomingArchiveUri = candidateUri
                clearIntentAction(incoming)
            }
        }
    }

    private fun clearIntentAction(intent: Intent) {
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra("action")
        intent.data = null
    }

    private fun isLikelyLikkhoArchiveIntent(intent: Intent, uri: Uri?): Boolean {
        val mime = intent.type?.lowercase(Locale.US).orEmpty()
        if (mime.contains("likkho")) return true
        if (uri != null) {
            if (uri.toString().lowercase(Locale.US).endsWith(".likkho")) return true
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0)
                        if (name != null && name.lowercase(Locale.US).endsWith(".likkho")) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun processPendingActionsIfReady() {
        if (isLocked) return

        val archiveUri = pendingIncomingArchiveUri
        if (archiveUri != null) {
            pendingIncomingArchiveUri = null
            processingBanner = "Opening shared .likkho archive..."
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        val unpacked = exportEngine.unpackLikkhoFromUri(archiveUri)
                        val updatedLogs = repository.upsertLog(currentVaultMode, unpacked)
                        unpacked to updatedLogs
                    }
                }
                processingBanner = null
                res.onSuccess { (unpacked, updatedLogs) ->
                    logs = updatedLogs
                    editingLog = unpacked
                    canvasStartInEditMode = false
                    currentRoute = NativeScreenRoute.CANVAS
                    statusBanner = "Opened \"${unpacked.heading.ifBlank { "Shared Diary" }}\"."
                }.onFailure { err ->
                    Toast.makeText(
                        this@NativeMainActivity,
                        "Could not open .likkho archive: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            return
        }

        val shared = pendingSharedContent
        if (shared != null) {
            pendingSharedContent = null
            processingBanner = "Creating shared diary draft..."
            lifecycleScope.launch {
                val created = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val dateStamp = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(now))
                    val timeStamp = SimpleDateFormat("hh:mm a", Locale.US).format(Date(now))
                    val canvasImgs = ArrayList<CanvasDraggableImage>()
                    if (shared.mediaUri != null) {
                        runCatching { mediaEngine.importImageFromUri(shared.mediaUri) }
                            .getOrNull()?.let { imp ->
                                val w = imp.width.coerceIn(120, 280).toDouble()
                                val aspect = imp.height.toDouble() / imp.width.coerceAtLeast(1).toDouble()
                                canvasImgs.add(
                                    CanvasDraggableImage(
                                        id = "img_share_${now}",
                                        dataUrl = imp.assetUrl,
                                        x = 24.0,
                                        y = 80.0,
                                        width = w,
                                        height = (w * aspect).coerceAtLeast(80.0),
                                        opacity = 1.0,
                                        rotation = 0.0,
                                        layer = "foreground"
                                    )
                                )
                            }
                    }
                    val html = convertPlainTextToParagraphHtml(shared.text)
                    val preview = shared.text.replace(Regex("\\s+"), " ").trim().take(180)
                    val draft = DiaryLog(
                        id = "log_share_${now}_${UUID.randomUUID().toString().take(4)}",
                        heading = shared.title.ifBlank { "Shared Note" },
                        contentHtml = html,
                        plainPreview = preview,
                        createdAt = now,
                        updatedAt = now,
                        dateStamp = dateStamp,
                        timeStamp = timeStamp,
                        canvasImages = canvasImgs
                    )
                    val nextLogs = repository.upsertLog(currentVaultMode, draft)
                    draft to nextLogs
                }
                processingBanner = null
                logs = created.second
                editingLog = created.first
                canvasStartInEditMode = true
                currentRoute = NativeScreenRoute.CANVAS
            }
            return
        }

        if (pendingNewDiaryIntent) {
            pendingNewDiaryIntent = false
            editingLog = null
            canvasStartInEditMode = true
            currentRoute = NativeScreenRoute.CANVAS
        }
    }

    private fun saveStandaloneQuickNote(note: QuickNote) {
        val targetMode = if (isLocked) VaultMode.PRIMARY else currentVaultMode
        val shouldMinimize = openedFromExternalQuickNoteIntent
        lifecycleScope.launch {
            val nextNotes = withContext(Dispatchers.IO) {
                val existing = repository.loadQuickNotes(targetMode)
                val updated = listOf(note) + existing.filterNot { it.id == note.id }
                repository.saveQuickNotes(targetMode, updated)
                if (note.isPinnedToNotification) {
                    QuickNoteSystemSync.syncQuickNoteNotification(this@NativeMainActivity, note)
                }
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(this@NativeMainActivity, updated)
                updated
            }
            if (!isLocked && targetMode == currentVaultMode) {
                quickNotes = nextNotes
            }
            showStandaloneQuickNoteModal = false
            openedFromExternalQuickNoteIntent = false
            Toast.makeText(
                this@NativeMainActivity,
                "Quick Note saved to notification",
                Toast.LENGTH_SHORT
            ).show()
            if (shouldMinimize) {
                minimizeQuickNoteHost()
            }
        }
    }

    private fun dismissStandaloneQuickNoteModal() {
        val shouldMinimize = openedFromExternalQuickNoteIntent
        showStandaloneQuickNoteModal = false
        openedFromExternalQuickNoteIntent = false
        if (shouldMinimize) {
            minimizeQuickNoteHost()
        }
    }

    private fun minimizeQuickNoteHost() {
        clearNativeSelection()
        hideSoftKeyboard()
        if (!moveTaskToBack(true)) {
            finish()
        }
    }

    fun onQuickNoteMarkedDone(noteId: String) {
        if (noteId.isBlank()) return
        mainHandler.post {
            lifecycleScope.launch {
                val updatedPrimary = withContext(Dispatchers.IO) {
                    val notes = repository.loadQuickNotes(VaultMode.PRIMARY)
                    val target = notes.firstOrNull { it.id == noteId } ?: return@withContext null
                    val nextList = notes.map {
                        if (it.id == noteId) it.copy(isDone = true, updatedAt = System.currentTimeMillis()) else it
                    }
                    repository.saveQuickNotes(VaultMode.PRIMARY, nextList)
                    QuickNoteSystemSync.syncQuickNoteNotification(
                        this@NativeMainActivity,
                        target.copy(isDone = true),
                        cancelNoteId = noteId
                    )
                    QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(this@NativeMainActivity, nextList)
                    nextList
                }
                if (!isLocked && currentVaultMode == VaultMode.PRIMARY && updatedPrimary != null) {
                    quickNotes = updatedPrimary
                } else if (!isLocked && currentVaultMode == VaultMode.DECOY) {
                    val updatedDecoy = withContext(Dispatchers.IO) {
                        val notes = repository.loadQuickNotes(VaultMode.DECOY)
                        val target = notes.firstOrNull { it.id == noteId } ?: return@withContext null
                        val nextList = notes.map {
                            if (it.id == noteId) it.copy(isDone = true, updatedAt = System.currentTimeMillis()) else it
                        }
                        repository.saveQuickNotes(VaultMode.DECOY, nextList)
                        QuickNoteSystemSync.syncQuickNoteNotification(
                            this@NativeMainActivity,
                            target.copy(isDone = true),
                            cancelNoteId = noteId
                        )
                        nextList
                    }
                    if (updatedDecoy != null) quickNotes = updatedDecoy
                }
            }
        }
    }

    private fun checkPendingQuickNoteDoneFromNotification() {
        val prefs = getSharedPreferences("likkho_saf_prefs", Context.MODE_PRIVATE)
        val pendingId = prefs.getString("pending_done_quick_note_id", null)
        if (!pendingId.isNullOrBlank()) {
            prefs.edit().remove("pending_done_quick_note_id").apply()
            onQuickNoteMarkedDone(pendingId)
        }
    }

    // ==================== NativeCanvasActivityHost Implementation ====================

    override fun registerCanvasWebView(webView: WebView?) {
        activeCanvasWebView = webView
        if (webView != null) {
            mainHandler.postDelayed({
                if (activeCanvasWebView === webView) {
                    deliverFinishedNativeRecordingIfPossible(this)
                }
            }, 600L)
        }
    }

    override fun onSystemPickerStateChanged(isOpen: Boolean) {
        isSystemPickerActive = isOpen
    }

    override fun triggerHapticFeedback(kind: String) {
        mainHandler.post {
            runCatching {
                val targetView = activeCanvasWebView ?: window?.decorView ?: return@runCatching
                val constant = when (kind) {
                    "confirm" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                    "reject" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                    "heavy" -> HapticFeedbackConstants.LONG_PRESS
                    "tap" -> HapticFeedbackConstants.CONTEXT_CLICK
                    else -> HapticFeedbackConstants.CLOCK_TICK
                }
                targetView.performHapticFeedback(constant)
            }
        }
    }

    override fun clearNativeSelection() {
        mainHandler.post {
            runCatching {
                activeCanvasWebView?.clearFocus()
            }
        }
    }

    override fun requestFilesAndMediaPermission() {
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            mediaPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    override fun requestMicrophonePermission(pendingWebRequest: PermissionRequest?) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            pendingWebRequest?.grant(pendingWebRequest.resources)
        } else {
            pendingWebPermissionRequest = pendingWebRequest
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun startNativeMicRecording(sampleRate: Int, bitRate: Int, logId: String) {
        pendingMicSampleRate = if (sampleRate > 0) sampleRate else 44_100
        pendingMicLogId = logId
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingWebPermissionRequest = null
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startMicForegroundServiceInternal(pendingMicSampleRate, pendingMicLogId)
    }

    private fun startMicForegroundServiceInternal(sampleRate: Int, logId: String) {
        val serviceIntent = Intent().apply {
            component = ComponentName(this@NativeMainActivity, "com.likkho.diary.MicForegroundService")
            action = ACTION_START_MIC
            putExtra("sampleRate", sampleRate)
            putExtra("logId", logId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun stopNativeMicRecording() {
        val stopIntent = Intent().apply {
            component = ComponentName(this@NativeMainActivity, "com.likkho.diary.MicForegroundService")
            action = ACTION_STOP_MIC
        }
        runCatching { startService(stopIntent) }
    }

    override fun isNativeMicRecordingActive(): Boolean {
        return runCatching {
            val cls = Class.forName("com.likkho.diary.MicForegroundService")
            cls.getField("isRecording").getBoolean(null)
        }.getOrDefault(false)
    }

    override fun getNativeMicRecordingSeconds(): Int {
        return runCatching {
            val cls = Class.forName("com.likkho.diary.MicForegroundService")
            val active = cls.getField("isRecording").getBoolean(null)
            val startedMs = cls.getField("recordingStartedAtMs").getLong(null)
            if (!active || startedMs <= 0L) 0
            else ((System.currentTimeMillis() - startedMs) / 1000L).toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    }

    override fun consumePendingFinishedNativeRecordingJson(): String {
        val prefs = getSharedPreferences("likkho_recording_prefs", Context.MODE_PRIVATE)
        val rawPath = prefs.getString("pending_recording_path", null) ?: return ""
        val file = File(rawPath)
        if (!file.exists() || file.length() <= 44L) return ""
        val duration = prefs.getInt("pending_recording_duration", 1).coerceAtLeast(1)
        val logId = prefs.getString("pending_recording_log_id", "").orEmpty()
        val assetUrl = "https://appassets.androidplatform.net/likkho-audio/${file.name}"
        return JSONObject().apply {
            put("audioUri", assetUrl)
            put("filePath", file.absolutePath)
            put("duration", duration)
            put("logId", logId)
        }.toString()
    }

    override fun clearPendingFinishedNativeRecording() {
        clearPendingRecordingStorage(this)
    }

    // ==================== Native MediaPlayer + Audio Notification Controls ====================

    override fun playNativeAudio(
        audioId: String,
        title: String,
        filePathOrUrl: String,
        playbackRate: Float,
        startMs: Int
    ): Boolean {
        if (audioId.isBlank() || filePathOrUrl.isBlank()) return false
        mainHandler.post {
            startNativeAudioPlaybackInternal(audioId, title, filePathOrUrl, playbackRate, startMs)
        }
        return true
    }

    private fun startNativeAudioPlaybackInternal(
        audioId: String,
        title: String,
        filePathOrUrl: String,
        rate: Float,
        startMs: Int
    ) {
        try {
            stopNativeAudio(null)
            activeAudioId = audioId
            activeAudioTitle = title.ifBlank { "Likkho Audio" }
            activeAudioSpeed = rate.coerceIn(0.5f, 2.5f)

            val resolvedSource: String = when {
                filePathOrUrl.startsWith("/") -> filePathOrUrl
                filePathOrUrl.startsWith("https://appassets.androidplatform.net/likkho-audio/") -> {
                    val fileId = repository.normalizeMediaId(filePathOrUrl)
                    repository.resolveExistingAudioFile(fileId)?.absolutePath ?: filePathOrUrl
                }
                else -> filePathOrUrl
            }

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            if (resolvedSource.startsWith("/")) {
                mp.setDataSource(resolvedSource)
            } else {
                mp.setDataSource(this, Uri.parse(resolvedSource))
            }
            mp.setOnPreparedListener { prepared ->
                runCatching {
                    if (startMs > 0 && startMs < prepared.duration) {
                        prepared.seekTo(startMs)
                    }
                    applySpeedToPlayer(prepared, activeAudioSpeed)
                    prepared.start()
                    mainHandler.removeCallbacks(audioProgressRunnable)
                    mainHandler.post(audioProgressRunnable)
                    updateNativeAudioNotification(activeAudioTitle, true)
                }
            }
            mp.setOnCompletionListener {
                stopNativeAudio(audioId)
            }
            mp.setOnErrorListener { _, _, _ ->
                stopNativeAudio(audioId)
                true
            }
            nativeMediaPlayer = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            stopNativeAudio(audioId)
        }
    }

    override fun pauseNativeAudio() {
        mainHandler.post {
            runCatching {
                val mp = nativeMediaPlayer ?: return@runCatching
                if (mp.isPlaying) {
                    mp.pause()
                    mainHandler.removeCallbacks(audioProgressRunnable)
                    val curSec = mp.currentPosition / 1000.0
                    val durSec = mp.duration.coerceAtLeast(0) / 1000.0
                    emitNativeAudioProgress(activeAudioId, curSec, durSec, false, false)
                    updateNativeAudioNotification(activeAudioTitle, false)
                }
            }
        }
    }

    override fun resumeNativeAudio() {
        mainHandler.post {
            runCatching {
                val mp = nativeMediaPlayer ?: return@runCatching
                if (!mp.isPlaying) {
                    applySpeedToPlayer(mp, activeAudioSpeed)
                    mp.start()
                    mainHandler.removeCallbacks(audioProgressRunnable)
                    mainHandler.post(audioProgressRunnable)
                    updateNativeAudioNotification(activeAudioTitle, true)
                }
            }
        }
    }

    override fun seekNativeAudio(posMs: Int) {
        mainHandler.post {
            runCatching {
                val mp = nativeMediaPlayer ?: return@runCatching
                mp.seekTo(posMs.coerceAtLeast(0))
                val durSec = mp.duration.coerceAtLeast(0) / 1000.0
                emitNativeAudioProgress(activeAudioId, posMs.coerceAtLeast(0) / 1000.0, durSec, mp.isPlaying, false)
            }
        }
    }

    override fun setNativeAudioSpeed(rate: Float) {
        mainHandler.post {
            activeAudioSpeed = rate.coerceIn(0.5f, 2.5f)
            nativeMediaPlayer?.let { applySpeedToPlayer(it, activeAudioSpeed) }
        }
    }

    override fun stopNativeAudio(audioId: String?) {
        stopNativeAudioPlayback(audioId)
    }

    fun toggleNativeAudioFromNotification() {
        mainHandler.post {
            val mp = nativeMediaPlayer ?: return@post
            if (activeAudioId == null) return@post
            if (mp.isPlaying) {
                pauseNativeAudio()
            } else {
                resumeNativeAudio()
            }
        }
    }

    fun stopNativeAudioPlayback(onlyIfAudioId: String?) {
        mainHandler.post {
            if (onlyIfAudioId != null && activeAudioId != null && onlyIfAudioId != activeAudioId) {
                return@post
            }
            mainHandler.removeCallbacks(audioProgressRunnable)
            val prevId = activeAudioId
            activeAudioId = null
            nativeMediaPlayer?.let { mp ->
                runCatching { if (mp.isPlaying) mp.stop() }
                runCatching { mp.release() }
            }
            nativeMediaPlayer = null
            cancelNativeAudioNotification()
            if (prevId != null) {
                emitNativeAudioProgress(prevId, 0.0, 0.0, false, true)
            }
        }
    }

    private fun applySpeedToPlayer(player: MediaPlayer, speed: Float) {
        runCatching {
            val wasPlaying = player.isPlaying
            val params: PlaybackParams = player.playbackParams
            params.speed = speed.coerceIn(0.5f, 2.5f)
            player.playbackParams = params
            if (!wasPlaying && player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun emitNativeAudioProgress(
        audioId: String?,
        curSec: Double,
        durSec: Double,
        playing: Boolean,
        ended: Boolean
    ) {
        if (audioId == null) return
        val wv = activeCanvasWebView ?: return
        val js = "(function(){ if(window.__onLikkhoNativeAudioProgress){ window.__onLikkhoNativeAudioProgress(${
            JSONObject.quote(audioId)
        },$curSec,$durSec,$playing,$ended); } })()"
        mainHandler.post {
            activeCanvasWebView?.takeIf { it === wv }?.evaluateJavascript(js, null)
        }
    }

    private fun updateNativeAudioNotification(title: String, isPlayingNow: Boolean) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    AUDIO_PLAYBACK_CHANNEL_ID,
                    "Likkho Audio Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                nm.createNotificationChannel(ch)
            }

            val openIntent = Intent(this, NativeMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                this,
                88950,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val receiverComp = ComponentName(this, "com.likkho.diary.AudioControlReceiver")
            val toggleIntent = Intent("com.likkho.diary.ACTION_AUDIO_TOGGLE").apply {
                component = receiverComp
            }
            val togglePi = PendingIntent.getBroadcast(
                this,
                88951,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent("com.likkho.diary.ACTION_AUDIO_STOP").apply {
                component = receiverComp
            }
            val stopPi = PendingIntent.getBroadcast(
                this,
                88952,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, AUDIO_PLAYBACK_CHANNEL_ID)
                .setSmallIcon(if (isPlayingNow) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
                .setContentTitle(title.ifBlank { "Likkho Audio" })
                .setContentText(if (isPlayingNow) "Playing diary audio" else "Paused")
                .setOngoing(isPlayingNow)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .addAction(
                    if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlayingNow) "Pause" else "Play",
                    togglePi
                )
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)

            nm.notify(88950, builder.build())
        }
    }

    private fun cancelNativeAudioNotification() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(88950)
        }
    }

    // ==================== AlarmManager OS Reminders ====================

    @SuppressLint("MissingPermission")
    override fun scheduleNativeReminder(logId: String, title: String, body: String, triggerAtMs: Long) {
        mainHandler.post {
            runCatching {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@post
                val reqCode = abs(logId.hashCode())
                val intent = Intent().apply {
                    component = ComponentName(this@NativeMainActivity, "com.likkho.diary.ReminderReceiver")
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("notifId", reqCode)
                }
                val pi = PendingIntent.getBroadcast(
                    this,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                    }
                } else {
                    try {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                    } catch (_: SecurityException) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                    }
                }
            }
        }
    }

    override fun cancelNativeReminder(logId: String) {
        mainHandler.post {
            runCatching {
                val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@post
                val reqCode = abs(logId.hashCode())
                val intent = Intent().apply {
                    component = ComponentName(this@NativeMainActivity, "com.likkho.diary.ReminderReceiver")
                }
                val pi = PendingIntent.getBroadcast(
                    this,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
        }
    }

    override fun openExternalUrl(url: String) {
        val clean = url.trim()
        if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("mailto:")) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(clean)))
            }
        }
    }

    // ==================== Background Recording Delivery ====================

    private fun attachOrDispatchPendingRecording() {
        val prefs = getSharedPreferences("likkho_recording_prefs", Context.MODE_PRIVATE)
        val rawPath = prefs.getString("pending_recording_path", null) ?: return
        val file = File(rawPath)
        if (!file.exists() || file.length() <= 44L) {
            clearPendingRecordingStorage(this)
            return
        }
        val duration = prefs.getInt("pending_recording_duration", 1).coerceAtLeast(1)
        val targetLogId = prefs.getString("pending_recording_log_id", "").orEmpty()
        val assetUrl = "https://appassets.androidplatform.net/likkho-audio/${file.name}"

        val wv = activeCanvasWebView
        if (wv != null && currentRoute == NativeScreenRoute.CANVAS) {
            wv.evaluateJavascript(
                "(function(){ if(window.__onLikkhoNativeMicFinished){ window.__onLikkhoNativeMicFinished(${
                    JSONObject.quote(assetUrl)
                }, $duration); return 'OK'; } return 'WAIT'; })()"
            ) { result ->
                if (result != null && result.contains("OK")) {
                    clearPendingRecordingStorage(this)
                }
            }
            return
        }

        if (targetLogId.isNotBlank() && !isLocked) {
            lifecycleScope.launch {
                val attachedLog = withContext(Dispatchers.IO) {
                    val current = repository.loadLogs(currentVaultMode)
                    val target = current.firstOrNull { it.id == targetLogId } ?: return@withContext null
                    val now = System.currentTimeMillis()
                    val stamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(now))
                    val fmt = micSettings.format.wireValue
                    val newAttachment = CanvasAudioAttachment(
                        id = "rec_${now}",
                        name = "Voice ($stamp).$fmt",
                        format = fmt,
                        dataUrl = "",
                        audioUri = assetUrl,
                        durationSec = duration.toDouble(),
                        createdAt = now
                    )
                    val updatedLog = target.copy(
                        updatedAt = now,
                        audioAttachments = target.audioAttachments + newAttachment
                    )
                    repository.upsertLog(currentVaultMode, updatedLog)
                    updatedLog
                }
                if (attachedLog != null) {
                    logs = withContext(Dispatchers.IO) { repository.loadLogs(currentVaultMode) }
                    clearPendingRecordingStorage(this@NativeMainActivity)
                    statusBanner = "Saved background voice recording (${duration}s) to \"${attachedLog.heading.ifBlank { "Diary" }}\"."
                }
            }
        }
    }

    // ==================== Window System Bars, Fullscreen & Privacy Shield ====================

    private fun applySystemBarsAndFullScreen(dark: Boolean, fullScreen: Boolean) {
        mainHandler.post {
            runCatching {
                val win = window ?: return@runCatching
                val decor = win.decorView
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    decor.isForceDarkAllowed = false
                }
                WindowCompat.setDecorFitsSystemWindows(win, false)

                val statusHex = if (dark) "#1a1f24" else "#f8f9fa"
                val navHex = if (dark) "#101418" else "#ffffff"
                val statusColor = AndroidColor.parseColor(statusHex)
                val navColor = AndroidColor.parseColor(navHex)

                @Suppress("DEPRECATION")
                win.statusBarColor = statusColor
                @Suppress("DEPRECATION")
                win.navigationBarColor = navColor
                win.setBackgroundDrawable(ColorDrawable(navColor))
                decor.setBackgroundColor(navColor)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.navigationBarDividerColor = navColor
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isStatusBarContrastEnforced = false
                    win.isNavigationBarContrastEnforced = false
                }

                val controller = WindowCompat.getInsetsController(win, decor)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark

                if (fullScreen) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    private fun applyPasscodeSecurityFlags(enabled: Boolean) {
        mainHandler.post {
            runCatching {
                val win = window ?: return@runCatching
                if (enabled) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    hideRecentsPrivacyShield()
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    setRecentsScreenshotEnabled(!enabled)
                }
            }
        }
    }

    private fun showRecentsPrivacyShield() {
        mainHandler.post {
            runCatching {
                val decor = window?.decorView as? ViewGroup ?: return@runCatching
                val overlay = recentsPrivacyOverlayView ?: View(this).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    recentsPrivacyOverlayView = this
                }
                if (overlay.parent == null) {
                    decor.addView(overlay)
                }
                overlay.visibility = View.VISIBLE
                overlay.bringToFront()
            }
        }
    }

    private fun hideRecentsPrivacyShield() {
        mainHandler.post {
            runCatching {
                recentsPrivacyOverlayView?.visibility = View.GONE
            }
        }
    }

    private fun hideSoftKeyboard() {
        runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
            val target = currentFocus ?: window?.decorView
            if (target != null) {
                imm.hideSoftInputFromWindow(target.windowToken, 0)
            }
        }
    }

    private fun convertPlainTextToParagraphHtml(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.split(Regex("\\r?\\n")).joinToString("") { line ->
            val escaped = line
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            "<p>${if (escaped.isNotEmpty()) escaped else "&nbsp;"}</p>"
        }
    }

    private data class HydratedVaultData(
        val logs: List<DiaryLog>,
        val customFonts: List<CustomFontItem>,
        val micSettings: MicRecordingSettings,
        val quickNotes: List<QuickNote>,
        val wordAnalysesJson: String
    )

    companion object {
        private const val ACTION_START_MIC = "com.likkho.diary.ACTION_START_MIC"
        private const val ACTION_STOP_MIC = "com.likkho.diary.ACTION_STOP_MIC"
        private const val AUDIO_PLAYBACK_CHANNEL_ID = "likkho_audio_playback_channel"

        @JvmField
        var currentInstance: NativeMainActivity? = null

        @JvmStatic
        fun deliverFinishedNativeRecordingIfPossible(ctx: Context?) {
            if (ctx == null) return
            val prefs = ctx.getSharedPreferences("likkho_recording_prefs", Context.MODE_PRIVATE)
            var path = prefs.getString("pending_recording_path", null)
            var duration = prefs.getInt("pending_recording_duration", 1)
            var logId = prefs.getString("pending_recording_log_id", "").orEmpty()

            if (path == null) {
                runCatching {
                    val cls = Class.forName("com.likkho.diary.MicForegroundService")
                    path = cls.getField("latestFinishedFilePath").get(null) as? String
                    duration = cls.getField("latestDurationSec").getInt(null)
                    logId = (cls.getField("currentRecordingLogId").get(null) as? String).orEmpty()
                }
            }
            val resolvedPath = path ?: return
            val file = File(resolvedPath)
            if (!file.exists() || file.length() <= 44L) return

            // Move into lihhko_audio directory so WebViewAssetLoader serves it under /likkho-audio/
            var permFile = file
            runCatching {
                val audioDir = File(ctx.filesDir, "likkho_audio").apply { if (!exists()) mkdirs() }
                if (file.parentFile?.canonicalPath != audioDir.canonicalPath) {
                    val dest = File(audioDir, "audio_${System.currentTimeMillis()}.wav")
                    if (file.renameTo(dest)) {
                        permFile = dest
                    }
                }
            }

            prefs.edit()
                .putString("pending_recording_path", permFile.absolutePath)
                .putInt("pending_recording_duration", duration.coerceAtLeast(1))
                .putString("pending_recording_log_id", logId)
                .apply()

            val host = currentInstance ?: return
            val assetUrl = "https://appassets.androidplatform.net/likkho-audio/${permFile.name}"
            host.mainHandler.post {
                val wv = host.activeCanvasWebView
                if (wv != null && host.currentRoute == NativeScreenRoute.CANVAS) {
                    wv.evaluateJavascript(
                        "(function(){ if(window.__onLikkhoNativeMicFinished){ window.__onLikkhoNativeMicFinished(${
                            JSONObject.quote(assetUrl)
                        }, ${duration.coerceAtLeast(1)}); return 'OK'; } return 'WAIT'; })()"
                    ) { result ->
                        if (result != null && result.contains("OK")) {
                            clearPendingRecordingStorage(ctx)
                        }
                    }
                } else {
                    host.attachOrDispatchPendingRecording()
                }
            }
        }

        @JvmStatic
        fun clearPendingRecordingStorage(ctx: Context?) {
            if (ctx != null) {
                runCatching {
                    ctx.getSharedPreferences("likkho_recording_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                }
            }
            runCatching {
                val cls = Class.forName("com.likkho.diary.MicForegroundService")
                cls.getField("latestFinishedFilePath").set(null, null)
            }
        }
    }
}

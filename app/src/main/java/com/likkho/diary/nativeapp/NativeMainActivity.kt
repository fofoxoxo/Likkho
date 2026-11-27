package com.likkho.diary.nativeapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.likkho.diary.nativeapp.crypto.StreamCryptoEngine
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.export.NativeExportEngine
import com.likkho.diary.nativeapp.export.NativeExportFormat
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CanvasDraggableImage
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.DocumentCategory
import com.likkho.diary.nativeapp.model.PersonalDocument
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.RecurringTracker
import com.likkho.diary.nativeapp.model.SavedPasswordItem
import com.likkho.diary.nativeapp.model.StandaloneReminder
import com.likkho.diary.nativeapp.model.VaultBackupBundle
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.notifications.ReminderAlarmScheduler
import com.likkho.diary.nativeapp.ui.screens.EverydayShellScreen
import com.likkho.diary.nativeapp.ui.screens.PasscodeLockScreen
import com.likkho.diary.nativeapp.ui.screens.QuickNoteModal
import com.likkho.diary.nativeapp.ui.screens.QuickNoteSystemSync
import com.likkho.diary.nativeapp.ui.screens.SecretWritingEditorScreen
import com.likkho.diary.nativeapp.ui.screens.SecretWritingScreen
import com.likkho.diary.nativeapp.ui.screens.SettingsScreen
import com.likkho.diary.nativeapp.ui.screens.SettingsSubPage
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class NativeScreenRoute {
    HOME,
    SECRET_EDITOR,
    SETTINGS
}

private data class PendingSharedContent(
    val title: String,
    val text: String,
    val mediaUri: Uri?
)

/**
 * 100% Pure Native Kotlin + Jetpack Compose Entry Activity for Likkho v3.0.
 *
 * - Owns App Shell, Theme / Dark Mode, Edge-to-Edge System Bars, Biometric / Dual-Vault Lockscreen,
 *   EverydayShellScreen (VaultMode.PRIMARY), SecretWritingScreen & SecretWritingEditorScreen (VaultMode.DECOY),
 *   SettingsScreen, QuickNoteModal, AlarmManager Reminders, and Intent Dispatch.
 */
class NativeMainActivity : FragmentActivity() {

    private lateinit var repository: NativeVaultRepository
    private lateinit var mediaEngine: NativeMediaEngine
    private lateinit var streamCryptoEngine: StreamCryptoEngine
    private lateinit var exportEngine: NativeExportEngine

    private val mainHandler = Handler(Looper.getMainLooper())

    // Core UI State (Compose-observed)
    private var isHydratingVault by mutableStateOf(false)
    private var isDarkTheme by mutableStateOf(false)
    private var isFullScreen by mutableStateOf(false)
    private var isLocked by mutableStateOf(true)
    private var currentVaultMode by mutableStateOf(VaultMode.PRIMARY)
    private var currentRoute by mutableStateOf(NativeScreenRoute.HOME)
    private var settingsInitialSubPage by mutableStateOf(SettingsSubPage.MAIN)

    // Hydrated Active Vault State
    private var logs by mutableStateOf<List<DiaryLog>>(emptyList())
    private var legacyPrimaryLogsCount by mutableIntStateOf(0)
    private var quickNotes by mutableStateOf<List<QuickNote>>(emptyList())
    private var trackers by mutableStateOf<List<RecurringTracker>>(emptyList())
    private var reminders by mutableStateOf<List<StandaloneReminder>>(emptyList())
    private var documents by mutableStateOf<List<PersonalDocument>>(emptyList())
    private var savedPasswords by mutableStateOf<List<SavedPasswordItem>>(emptyList())

    // Secret Writing Reader / Editor State (VaultMode.DECOY)
    private var editingLog by mutableStateOf<DiaryLog?>(null)
    private var secretEditorStartInEditMode by mutableStateOf(false)

    // Standalone Quick Note Launch Overlay (Widget / QS Tile / Notification / Shortcut)
    private var showStandaloneQuickNoteModal by mutableStateOf(false)
    private var openedFromExternalQuickNoteIntent by mutableStateOf(false)

    // Status & Export Progress Banners
    private var statusBanner by mutableStateOf<String?>(null)
    private var processingBanner by mutableStateOf<String?>(null)

    // Deferred Intent Actions (executed right after unlock)
    private var pendingNewDiaryIntent = false
    private var pendingIncomingArchiveUri: Uri? = null
    private var pendingSharedContent: PendingSharedContent? = null

    // Security & Picker Tracking
    private var isSystemPickerActive = false
    private var recentsPrivacyOverlayView: View? = null

    // Permission Launcher
    private lateinit var notifPermissionLauncher: ActivityResultLauncher<String>

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

        val passcodeProtected = repository.isPasscodeProtectionFastHint()

        initPermissionLaunchers()
        applySystemBarsAndFullScreen(isDarkTheme, isFullScreen)
        applyPasscodeSecurityFlags(passcodeProtected)

        parseIncomingIntent(intent)

        if (!passcodeProtected) {
            currentVaultMode = VaultMode.PRIMARY
            isHydratingVault = true
            isLocked = false
            currentRoute = NativeScreenRoute.HOME
        } else {
            isLocked = true
        }

        setContent {
            LikkhoTheme(darkTheme = isDarkTheme) {
                val rootColors = LikkhoTheme.colors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rootColors.bg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(rootColors.surface)
                            .align(Alignment.TopCenter)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(rootColors.bg)
                            .align(Alignment.BottomCenter)
                    )
                    when {
                        isLocked -> {
                            PasscodeLockScreen(
                                repository = repository,
                                biometricsEnabled = repository.isBiometricEnabled() && repository.hasPrimaryPasscode(),
                                onUnlocked = { unlockedMode ->
                                    onVaultUnlocked(unlockedMode)
                                }
                            )
                        }

                        else -> {
                            when (currentRoute) {
                                NativeScreenRoute.HOME -> {
                                    if (currentVaultMode == VaultMode.PRIMARY) {
                                        EverydayShellScreen(
                                            quickNotes = quickNotes,
                                            trackers = trackers,
                                            reminders = reminders,
                                            documents = documents,
                                            savedPasswords = savedPasswords,
                                            repository = repository,
                                            isLoadingVault = isHydratingVault,
                                            statusBanner = statusBanner,
                                            processingBanner = processingBanner,
                                            onDismissStatusBanner = { statusBanner = null },
                                            onLockApp = { lockVault() },
                                            onOpenSettings = {
                                                settingsInitialSubPage = SettingsSubPage.MAIN
                                                currentRoute = NativeScreenRoute.SETTINGS
                                            },
                                            onQuickNotesUpdated = { updatedNotes ->
                                                quickNotes = updatedNotes
                                            },
                                            onTrackersUpdated = { updatedTrackers ->
                                                trackers = updatedTrackers
                                            },
                                            onRemindersUpdated = { updatedReminders ->
                                                reminders = updatedReminders
                                            },
                                            onDocumentsUpdated = { updatedDocuments ->
                                                documents = updatedDocuments
                                            },
                                            onPasswordsUpdated = { updatedPasswords ->
                                                savedPasswords = updatedPasswords
                                            },
                                            onSystemPickerStateChanged = { isOpen ->
                                                isSystemPickerActive = isOpen
                                            }
                                        )
                                    } else {
                                        SecretWritingScreen(
                                            logs = logs,
                                            legacyPrimaryLogsCount = legacyPrimaryLogsCount,
                                            repository = repository,
                                            mediaEngine = mediaEngine,
                                            isLoadingLogs = isHydratingVault,
                                            statusBanner = statusBanner,
                                            processingBanner = processingBanner,
                                            onDismissStatusBanner = { statusBanner = null },
                                            onMigrateLegacyPrimaryLogs = {
                                                migrateLegacyPrimaryLogsIntoSecretVault()
                                            },
                                            onLockApp = { lockVault() },
                                            onOpenSettings = {
                                                settingsInitialSubPage = SettingsSubPage.MAIN
                                                currentRoute = NativeScreenRoute.SETTINGS
                                            },
                                            onCreateEntry = {
                                                editingLog = null
                                                secretEditorStartInEditMode = true
                                                currentRoute = NativeScreenRoute.SECRET_EDITOR
                                            },
                                            onOpenEntry = { log ->
                                                editingLog = log
                                                secretEditorStartInEditMode = false
                                                currentRoute = NativeScreenRoute.SECRET_EDITOR
                                            },
                                            onTogglePin = { log ->
                                                val updated = log.copy(pinned = !log.pinned)
                                                lifecycleScope.launch {
                                                    val refreshed = withContext(Dispatchers.IO) {
                                                        repository.upsertLog(VaultMode.DECOY, updated)
                                                    }
                                                    logs = refreshed
                                                }
                                            },
                                            onShareLikkho = { log ->
                                                shareDiaryAsLikkho(log)
                                            },
                                            onExportEntry = { log, format ->
                                                exportSingleDiary(log, format)
                                            },
                                            onDeleteEntry = { log ->
                                                handleSecretDeleteLog(log.id)
                                            }
                                        )
                                    }
                                }

                                NativeScreenRoute.SECRET_EDITOR -> {
                                    if (currentVaultMode == VaultMode.DECOY) {
                                        SecretWritingEditorScreen(
                                            initialLog = editingLog,
                                            startInEditMode = secretEditorStartInEditMode,
                                            repository = repository,
                                            mediaEngine = mediaEngine,
                                            onSaveLog = { savedLog, exitAfterSave ->
                                                handleSecretSaveLog(savedLog, exitAfterSave)
                                            },
                                            onDeleteLog = { deletedId ->
                                                handleSecretDeleteLog(deletedId)
                                            },
                                            onShareLikkho = { target ->
                                                shareDiaryAsLikkho(target)
                                            },
                                            onExportEntry = { target, format ->
                                                exportSingleDiary(target, format)
                                            },
                                            onSystemPickerStateChanged = { isOpen ->
                                                isSystemPickerActive = isOpen
                                            },
                                            onExitWorkspace = {
                                                editingLog = null
                                                currentRoute = NativeScreenRoute.HOME
                                            }
                                        )
                                    } else {
                                        currentRoute = NativeScreenRoute.HOME
                                    }
                                }

                                NativeScreenRoute.SETTINGS -> {
                                    SettingsScreen(
                                        vaultMode = currentVaultMode,
                                        isDarkTheme = isDarkTheme,
                                        isFullScreen = isFullScreen,
                                        logs = logs,
                                        legacyPrimaryLogsCount = if (currentVaultMode == VaultMode.DECOY) legacyPrimaryLogsCount else 0,
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
                                        onBundleRestored = { bundle ->
                                            applyRestoredBackupBundle(bundle)
                                        },
                                        onRestoreSingleDiary = { restoredLog ->
                                            lifecycleScope.launch {
                                                val nextLogs = withContext(Dispatchers.IO) {
                                                    repository.upsertLog(currentVaultMode, restoredLog)
                                                }
                                                logs = nextLogs
                                                statusBanner = "Restored \"${restoredLog.heading.ifBlank { "Entry" }}\"."
                                            }
                                        },
                                        onLegacyPrimaryLogsMigrated = { migratedLogs ->
                                            logs = migratedLogs
                                            legacyPrimaryLogsCount = 0
                                            statusBanner = "Moved legacy diaries into Secret Writing."
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
                                            lifecycleScope.launch {
                                                savedPasswords = withContext(Dispatchers.IO) {
                                                    repository.loadSavedPasswords(currentVaultMode)
                                                }
                                            }
                                        },
                                        initialSubPage = settingsInitialSubPage
                                    )
                                }
                            }
                        }
                    }

                    if (showStandaloneQuickNoteModal) {
                        QuickNoteModal(
                            initialNote = null,
                            onDismiss = { dismissStandaloneQuickNoteModal() },
                            onSave = { savedNote ->
                                saveStandaloneQuickNote(savedNote)
                            }
                        )
                    }
                }
            }
        }

        if (!passcodeProtected) {
            hydratePrimaryVaultAtStartup()
        } else {
            lifecycleScope.launch {
                val verifiedProtected = withContext(Dispatchers.IO) {
                    repository.isAppPasscodeProtectionEnabled()
                }
                if (!verifiedProtected) {
                    applyPasscodeSecurityFlags(false)
                    hydratePrimaryVaultAtStartup()
                } else {
                    withContext(Dispatchers.IO) {
                        val primaryNotes = repository.loadQuickNotes(VaultMode.PRIMARY)
                        QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(this@NativeMainActivity, primaryNotes)
                    }
                    checkPendingQuickNoteDoneFromNotification()
                    processPendingActionsIfReady()
                    scheduleNotificationPermissionRequestIfNeeded()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncomingIntent(intent)
        processPendingActionsIfReady()
    }

    override fun onResume() {
        super.onResume()
        currentInstance = this
        isSystemPickerActive = false
        hideRecentsPrivacyShield()
        applySystemBarsAndFullScreen(isDarkTheme, isFullScreen)
        applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())

        checkPendingQuickNoteDoneFromNotification()
        if (!isLocked) {
            processPendingActionsIfReady()
        }
    }

    override fun onPause() {
        clearNativeSelection()
        if (repository.isAppPasscodeProtectionEnabled() && !isSystemPickerActive) {
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
        if (currentInstance === this) {
            currentInstance = null
        }
        super.onDestroy()
    }

    // ==================== Startup & Vault Hydration ====================

    private fun initPermissionLaunchers() {
        notifPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ ->
            // Quick note / reminder notifications will publish cleanly next sync if granted
        }
    }

    private fun scheduleNotificationPermissionRequestIfNeeded() {
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

    private fun hydratePrimaryVaultAtStartup() {
        isHydratingVault = true
        lifecycleScope.launch {
            try {
                val hydrated = withContext(Dispatchers.IO) {
                    loadVaultDataParallel(VaultMode.PRIMARY)
                }
                currentVaultMode = VaultMode.PRIMARY
                logs = hydrated.logs
                legacyPrimaryLogsCount = 0
                quickNotes = hydrated.quickNotes
                trackers = hydrated.trackers
                reminders = hydrated.reminders
                documents = hydrated.documents
                savedPasswords = hydrated.savedPasswords
                isLocked = false
            } finally {
                isHydratingVault = false
            }

            withContext(Dispatchers.IO) {
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(
                    this@NativeMainActivity,
                    quickNotes
                )
            }
            val syncedReminders = withContext(Dispatchers.IO) {
                ReminderAlarmScheduler.rescheduleAllPendingAlarms(
                    this@NativeMainActivity,
                    VaultMode.PRIMARY
                )
            }
            reminders = syncedReminders

            checkPendingQuickNoteDoneFromNotification()
            processPendingActionsIfReady()
            scheduleNotificationPermissionRequestIfNeeded()
        }
    }

    private suspend fun loadVaultDataParallel(mode: VaultMode): HydratedVaultData = coroutineScope {
        val logsDeferred = async(Dispatchers.IO) { repository.loadLogs(mode) }
        val legacyCountDeferred = async(Dispatchers.IO) {
            if (mode == VaultMode.DECOY) repository.getLegacyPrimaryLogsCount() else 0
        }
        val notesDeferred = async(Dispatchers.IO) { repository.loadQuickNotes(mode) }
        val trackersDeferred = async(Dispatchers.IO) { repository.loadTrackers(mode) }
        val remindersDeferred = async(Dispatchers.IO) { repository.loadReminders(mode) }
        val docsDeferred = async(Dispatchers.IO) { repository.loadDocuments(mode) }
        val passwordsDeferred = async(Dispatchers.IO) { repository.loadSavedPasswords(mode) }
        HydratedVaultData(
            logs = logsDeferred.await(),
            legacyPrimaryLogsCount = legacyCountDeferred.await(),
            quickNotes = notesDeferred.await(),
            trackers = trackersDeferred.await(),
            reminders = remindersDeferred.await(),
            documents = docsDeferred.await(),
            savedPasswords = passwordsDeferred.await()
        )
    }

    private fun onVaultUnlocked(mode: VaultMode) {
        if (isHydratingVault) return
        isHydratingVault = true

        lifecycleScope.launch {
            try {
                val hydrated = withContext(Dispatchers.IO) {
                    loadVaultDataParallel(mode)
                }
                currentVaultMode = mode
                logs = hydrated.logs
                legacyPrimaryLogsCount = hydrated.legacyPrimaryLogsCount
                quickNotes = hydrated.quickNotes
                trackers = hydrated.trackers
                reminders = hydrated.reminders
                documents = hydrated.documents
                savedPasswords = hydrated.savedPasswords
                editingLog = null
                statusBanner = null
                currentRoute = NativeScreenRoute.HOME
                isLocked = false
            } finally {
                isHydratingVault = false
            }

            withContext(Dispatchers.IO) {
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(
                    this@NativeMainActivity,
                    quickNotes
                )
            }
            if (mode == VaultMode.PRIMARY) {
                val syncedReminders = withContext(Dispatchers.IO) {
                    ReminderAlarmScheduler.rescheduleAllPendingAlarms(
                        this@NativeMainActivity,
                        VaultMode.PRIMARY
                    )
                }
                reminders = syncedReminders
            }

            applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())
            processPendingActionsIfReady()
        }
    }

    private fun lockVault() {
        editingLog = null
        logs = emptyList()
        legacyPrimaryLogsCount = 0
        quickNotes = emptyList()
        trackers = emptyList()
        reminders = emptyList()
        documents = emptyList()
        savedPasswords = emptyList()
        statusBanner = null
        currentRoute = NativeScreenRoute.HOME
        isLocked = true
        applyPasscodeSecurityFlags(repository.isAppPasscodeProtectionEnabled())
    }

    private fun handleClearAllDataCompleted() {
        cancelAllLoadedReminders()
        isDarkTheme = false
        isFullScreen = false
        logs = emptyList()
        legacyPrimaryLogsCount = 0
        quickNotes = emptyList()
        trackers = emptyList()
        reminders = emptyList()
        documents = emptyList()
        savedPasswords = emptyList()
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
                ReminderAlarmScheduler.cancelDiaryReminder(this, l.id)
            }
        }
        for (r in reminders) {
            ReminderAlarmScheduler.cancelReminder(this, r.id)
        }
    }

    private fun applyRestoredBackupBundle(bundle: VaultBackupBundle) {
        lifecycleScope.launch {
            val (restoredReminders, nextLegacyCount) = withContext(Dispatchers.IO) {
                QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(
                    this@NativeMainActivity,
                    bundle.quickNotes
                )
                val syncedReminders = if (currentVaultMode == VaultMode.PRIMARY) {
                    ReminderAlarmScheduler.rescheduleAllPendingAlarms(
                        this@NativeMainActivity,
                        VaultMode.PRIMARY
                    )
                } else {
                    bundle.reminders
                }
                val legacyCount = if (currentVaultMode == VaultMode.DECOY) {
                    repository.getLegacyPrimaryLogsCount()
                } else {
                    0
                }
                syncedReminders to legacyCount
            }
            logs = bundle.logs
            legacyPrimaryLogsCount = nextLegacyCount
            quickNotes = bundle.quickNotes
            trackers = bundle.trackers
            reminders = restoredReminders
            documents = bundle.documents
            savedPasswords = bundle.savedPasswords
            currentRoute = NativeScreenRoute.HOME
            statusBanner = if (currentVaultMode == VaultMode.DECOY) {
                if (bundle.skippedMediaCount > 0) {
                    "Restored ${bundle.logs.size} entries (${bundle.skippedMediaCount} files skipped)."
                } else {
                    "Restored ${bundle.logs.size} encrypted entries."
                }
            } else {
                val restoredItems = bundle.quickNotes.size +
                    bundle.trackers.size +
                    bundle.reminders.size +
                    bundle.documents.size +
                    bundle.savedPasswords.size
                "Restored $restoredItems items to vault."
            }
        }
    }

    private fun migrateLegacyPrimaryLogsIntoSecretVault() {
        if (currentVaultMode != VaultMode.DECOY) return
        processingBanner = "Moving legacy diaries into Secret Writing..."
        lifecycleScope.launch {
            val (addedCount, migratedLogs) = withContext(Dispatchers.IO) {
                val added = repository.migrateLegacyPrimaryLogsToSecretVault(removeFromPrimary = true)
                added to repository.loadLogs(VaultMode.DECOY)
            }
            processingBanner = null
            logs = migratedLogs
            legacyPrimaryLogsCount = 0
            statusBanner = if (addedCount > 0) {
                "Moved $addedCount legacy ${if (addedCount == 1) "diary" else "diaries"} into Secret Writing."
            } else {
                "Legacy diaries synced into Secret Writing."
            }
        }
    }

    // ==================== Secret Writing Callbacks ====================

    private fun handleSecretSaveLog(savedLog: DiaryLog, exitAfterSave: Boolean) {
        lifecycleScope.launch {
            val nextLogs = withContext(Dispatchers.IO) {
                repository.upsertLog(VaultMode.DECOY, savedLog)
            }
            logs = nextLogs
            editingLog = savedLog
            if (exitAfterSave) {
                editingLog = null
                currentRoute = NativeScreenRoute.HOME
            }
        }
    }

    private fun handleSecretDeleteLog(deletedId: String) {
        ReminderAlarmScheduler.cancelDiaryReminder(this, deletedId)
        lifecycleScope.launch {
            val remaining = withContext(Dispatchers.IO) {
                repository.deleteLog(VaultMode.DECOY, deletedId)
            }
            logs = remaining
            editingLog = null
            currentRoute = NativeScreenRoute.HOME
            statusBanner = "Entry deleted."
        }
    }

    // ==================== Secret Writing Export & `.likkho` Share ====================

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
            if (currentVaultMode != VaultMode.DECOY) {
                Toast.makeText(
                    this@NativeMainActivity,
                    "Archive cannot be opened in Everyday mode.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            processingBanner = "Opening shared .likkho archive..."
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        val unpacked = exportEngine.unpackLikkhoFromUri(archiveUri)
                        val updatedLogs = repository.upsertLog(VaultMode.DECOY, unpacked)
                        unpacked to updatedLogs
                    }
                }
                processingBanner = null
                res.onSuccess { (unpacked, updatedLogs) ->
                    logs = updatedLogs
                    editingLog = unpacked
                    secretEditorStartInEditMode = false
                    currentRoute = NativeScreenRoute.SECRET_EDITOR
                    statusBanner = "Opened \"${unpacked.heading.ifBlank { "Shared Entry" }}\"."
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
            if (currentVaultMode == VaultMode.PRIMARY) {
                lifecycleScope.launch {
                    val now = System.currentTimeMillis()
                    if (shared.mediaUri != null) {
                        val importedResult = withContext(Dispatchers.IO) {
                            val imp = repository.importDocumentFromUri(shared.mediaUri)
                            if (imp != null) {
                                val fallbackTitle = imp.fileName.substringBeforeLast('.').ifBlank { "Shared Document" }
                                val doc = PersonalDocument(
                                    id = "doc_share_${now}_${UUID.randomUUID().toString().take(4)}",
                                    title = shared.title.ifBlank { fallbackTitle },
                                    category = DocumentCategory.OTHER,
                                    fileId = imp.fileId,
                                    fileName = imp.fileName,
                                    mimeType = imp.mimeType,
                                    fileSizeBytes = imp.sizeBytes,
                                    notes = shared.text,
                                    pinned = false,
                                    createdAt = now,
                                    updatedAt = now
                                )
                                doc to repository.upsertDocument(VaultMode.PRIMARY, doc)
                            } else {
                                null
                            }
                        }
                        if (importedResult != null) {
                            documents = importedResult.second
                            statusBanner = "Saved document \"${importedResult.first.title}\"."
                            return@launch
                        }
                    }

                    val sharedBody = shared.text.ifBlank { shared.title }
                    if (sharedBody.isBlank() && shared.title.isBlank()) {
                        statusBanner = "Could not import shared file."
                        return@launch
                    }
                    val newNote = QuickNote(
                        id = "qn_share_${now}_${UUID.randomUUID().toString().take(4)}",
                        title = shared.title.ifBlank { "Shared Note" },
                        content = sharedBody,
                        createdAt = now,
                        updatedAt = now,
                        isDone = false,
                        isChecklist = false,
                        isPinnedToNotification = true
                    )
                    val nextNotes = withContext(Dispatchers.IO) {
                        val updated = listOf(newNote) + repository.loadQuickNotes(VaultMode.PRIMARY)
                        repository.saveQuickNotes(VaultMode.PRIMARY, updated)
                        QuickNoteSystemSync.syncQuickNoteNotification(this@NativeMainActivity, newNote)
                        QuickNoteSystemSync.syncActiveNoteToHomeWidgetAndTile(this@NativeMainActivity, updated)
                        updated
                    }
                    quickNotes = nextNotes
                    statusBanner = "Saved shared note \"${newNote.title}\"."
                }
                return
            }
            processingBanner = "Creating shared draft..."
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
                    val nextLogs = repository.upsertLog(VaultMode.DECOY, draft)
                    draft to nextLogs
                }
                processingBanner = null
                logs = created.second
                editingLog = created.first
                secretEditorStartInEditMode = true
                currentRoute = NativeScreenRoute.SECRET_EDITOR
            }
            return
        }

        if (pendingNewDiaryIntent) {
            pendingNewDiaryIntent = false
            if (currentVaultMode == VaultMode.PRIMARY) {
                showStandaloneQuickNoteModal = true
            } else {
                editingLog = null
                secretEditorStartInEditMode = true
                currentRoute = NativeScreenRoute.SECRET_EDITOR
            }
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
        val pendingIds = LinkedHashSet<String>()
        prefs.getStringSet("pending_done_quick_note_ids", null)?.let { set ->
            set.filterTo(pendingIds) { it.isNotBlank() }
        }
        prefs.getString("pending_done_quick_note_id", null)?.takeIf { it.isNotBlank() }?.let {
            pendingIds.add(it)
        }
        if (pendingIds.isNotEmpty()) {
            prefs.edit()
                .remove("pending_done_quick_note_ids")
                .remove("pending_done_quick_note_id")
                .apply()
            pendingIds.forEach { onQuickNoteMarkedDone(it) }
        }
    }

    // ==================== AlarmManager Receiver Sync ====================

    fun refreshRemindersFromVault() {
        mainHandler.post {
            if (isLocked) return@post
            val targetMode = currentVaultMode
            lifecycleScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    repository.loadReminders(targetMode)
                }
                if (!isLocked && currentVaultMode == targetMode) {
                    reminders = loaded
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

                val navHex = if (dark) "#101418" else "#ffffff"
                val navColor = AndroidColor.parseColor(navHex)

                @Suppress("DEPRECATION")
                win.statusBarColor = AndroidColor.TRANSPARENT
                @Suppress("DEPRECATION")
                win.navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || dark) {
                    AndroidColor.TRANSPARENT
                } else {
                    AndroidColor.parseColor("#80000000")
                }
                win.setBackgroundDrawable(ColorDrawable(navColor))
                decor.setBackgroundColor(navColor)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.navigationBarDividerColor = AndroidColor.TRANSPARENT
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

    private fun clearNativeSelection() {
        mainHandler.post {
            runCatching { currentFocus?.clearFocus() }
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
        val legacyPrimaryLogsCount: Int,
        val quickNotes: List<QuickNote>,
        val trackers: List<RecurringTracker>,
        val reminders: List<StandaloneReminder>,
        val documents: List<PersonalDocument>,
        val savedPasswords: List<SavedPasswordItem>
    )

    companion object {
        @JvmField
        var currentInstance: NativeMainActivity? = null
    }
}

package com.likkho.diary.nativeapp.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.likkho.diary.nativeapp.crypto.StreamCryptoEngine
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.export.NativeExportEngine
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.VaultBackupBundle
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogOutlineButton
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogPrimaryButton
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineBorder
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineDialog
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.hypot

enum class SettingsSubPage {
    MAIN,
    BACKUP_RESTORE,
    BACKED_UP_DIARIES,
    LEGAL_ABOUT
}

private data class GithubUpdateCheckResult(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val error: String? = null
)

private const val APP_CURRENT_VERSION = "3.0.0"
private const val DEFAULT_GITHUB_REPO = "fofoxoxo/Likkho"
private const val AUTHOR_NAME = "Sayan Saha"
private const val AUTHOR_EMAIL = "sayan996969saha@gmail.com"
private const val GITHUB_REPO_URL = "https://github.com/fofoxoxo/Likkho"

/**
 * Native Compose Settings Screen & Sub-Pages (1:1 parity with `src/components/SettingsPage.tsx`).
 */
@Composable
fun SettingsScreen(
    vaultMode: VaultMode,
    isDarkTheme: Boolean,
    isFullScreen: Boolean,
    logs: List<DiaryLog>,
    legacyPrimaryLogsCount: Int = 0,
    repository: NativeVaultRepository,
    streamCryptoEngine: StreamCryptoEngine,
    mediaEngine: NativeMediaEngine,
    exportEngine: NativeExportEngine,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleFullScreen: (Boolean) -> Unit,
    onBundleRestored: (VaultBackupBundle) -> Unit,
    onRestoreSingleDiary: (DiaryLog) -> Unit,
    onLegacyPrimaryLogsMigrated: (List<DiaryLog>) -> Unit = {},
    onSwitchedToPrimaryVault: () -> Unit,
    onClearAllDataCompleted: () -> Unit,
    onBack: () -> Unit,
    initialSubPage: SettingsSubPage = SettingsSubPage.MAIN,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var currentSubPage by remember(initialSubPage) { mutableStateOf(initialSubPage) }
    var statusToast by remember { mutableStateOf<String?>(null) }

    var hasPrimaryPasscode by remember(vaultMode) { mutableStateOf(repository.hasPrimaryPasscode()) }
    var hasDecoyPasscode by remember(vaultMode) { mutableStateOf(repository.hasDecoyPasscode()) }
    var isBiometricEnabled by remember(vaultMode) { mutableStateOf(repository.isBiometricEnabled()) }

    // Passcode setup dialog state (supports both Everyday vault PIN and Secret Writing PIN / 10s-hold covert gesture)
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var isDecoyPinTarget by remember { mutableStateOf(false) }

    // Legacy Diaries migration confirmation (Secret Writing Mode only)
    var showLegacyMigrateDialog by remember { mutableStateOf(false) }
    var isMigratingLegacy by remember { mutableStateOf(false) }

    // Update checker state
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateModalResult by remember { mutableStateOf<GithubUpdateCheckResult?>(null) }

    LaunchedEffect(statusToast) {
        if (statusToast != null) {
            delay(3200L)
            statusToast = null
        }
    }

    fun openPasscodeDialog(covertDecoy: Boolean) {
        isDecoyPinTarget = covertDecoy || (vaultMode == VaultMode.DECOY)
        showPasscodeDialog = true
    }

    fun handleBiometricToggle(enable: Boolean) {
        if (vaultMode == VaultMode.DECOY) return
        if (!hasPrimaryPasscode) {
            statusToast = "Please set an App Passcode first."
            return
        }
        if (!enable) {
            repository.setBiometricEnabled(false)
            isBiometricEnabled = false
            statusToast = "Biometric Unlock disabled."
            return
        }
        val activity = context.findSettingsFragmentActivity()
        if (activity == null) {
            statusToast = "Biometric prompt unavailable in current host."
            return
        }
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            statusToast = "No enrolled biometrics found on this device."
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    repository.setBiometricEnabled(true)
                    isBiometricEnabled = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    statusToast = "Biometric Unlock enabled."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        statusToast = errString.toString()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Biometric Unlock")
            .setSubtitle("Verify your fingerprint or face to enable biometric unlock")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    fun handleCheckForUpdates() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchLatestGithubRelease() }
            isCheckingUpdate = false
            if (result.error != null) {
                statusToast = result.error
            } else if (!result.hasUpdate) {
                statusToast = "You are on the latest version (v$APP_CURRENT_VERSION)."
            } else {
                updateModalResult = result
            }
        }
    }

    when (currentSubPage) {
        SettingsSubPage.BACKUP_RESTORE -> {
            BackupRestoreScreen(
                vaultMode = vaultMode,
                logs = logs,
                repository = repository,
                streamCryptoEngine = streamCryptoEngine,
                onBundleRestored = onBundleRestored,
                onBack = { currentSubPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }

        SettingsSubPage.BACKED_UP_DIARIES -> {
            if (vaultMode == VaultMode.DECOY) {
                BackedUpDiariesScreen(
                    vaultMode = vaultMode,
                    repository = repository,
                    streamCryptoEngine = streamCryptoEngine,
                    mediaEngine = mediaEngine,
                    onRestoreSingleDiary = onRestoreSingleDiary,
                    onBack = { currentSubPage = SettingsSubPage.MAIN },
                    modifier = modifier
                )
                return
            } else {
                currentSubPage = SettingsSubPage.MAIN
            }
        }

        SettingsSubPage.LEGAL_ABOUT -> {
            LegalAndAboutSubScreen(
                vaultMode = vaultMode,
                repository = repository,
                exportEngine = exportEngine,
                onClearAllDataCompleted = onClearAllDataCompleted,
                onBack = { currentSubPage = SettingsSubPage.MAIN },
                modifier = modifier
            )
            return
        }

        SettingsSubPage.MAIN -> Unit
    }

    BackHandler {
        when {
            showPasscodeDialog -> showPasscodeDialog = false
            showLegacyMigrateDialog -> if (!isMigratingLegacy) showLegacyMigrateDialog = false
            updateModalResult != null -> updateModalResult = null
            else -> onBack()
        }
    }

    // ==================== Main Settings Layout ====================
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Settings",
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.text
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!statusToast.isNullOrBlank()) {
                Text(
                    text = statusToast!!,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.09f))
                        .border(1.dp, colors.accent)
                        .padding(12.dp)
                )
            }

            // Group 1: Appearance
            SettingsGroup(title = "APPEARANCE") {
                SettingsSwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Wikipedia editorial low-glare reading palette",
                    checked = isDarkTheme,
                    onCheckedChange = {
                        repository.setDarkTheme(it)
                        onToggleDarkTheme(it)
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                SettingsSwitchRow(
                    icon = Icons.Outlined.Fullscreen,
                    title = "Full Screen Mode",
                    subtitle = "Hide system bars for distraction-free writing",
                    checked = isFullScreen,
                    onCheckedChange = {
                        repository.setFullScreenEnabled(it)
                        onToggleFullScreen(it)
                    }
                )
            }

            // Group 2: Security & Privacy
            SettingsGroup(title = "SECURITY & PRIVACY") {
                val activePasscodeEnabled = if (vaultMode == VaultMode.DECOY) hasDecoyPasscode else hasPrimaryPasscode
                // Everyday PIN row preserves covert 10-second continuous hold gesture in Primary vault with zero UI hints
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(vaultMode, hasPrimaryPasscode) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                var holdTriggered = false
                                var cancelledByMove = false
                                val touchSlop = viewConfiguration.touchSlop

                                val holdJob = if (vaultMode == VaultMode.PRIMARY && hasPrimaryPasscode) {
                                    scope.launch {
                                        delay(10_000L)
                                        if (!cancelledByMove) {
                                            holdTriggered = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            openPasscodeDialog(covertDecoy = true)
                                        }
                                    }
                                } else {
                                    null
                                }

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        holdJob?.cancel()
                                        if (!holdTriggered && !cancelledByMove) {
                                            openPasscodeDialog(covertDecoy = false)
                                        }
                                        break
                                    }
                                    val dist = hypot(
                                        (change.position.x - downPos.x).toDouble(),
                                        (change.position.y - downPos.y).toDouble()
                                    ).toFloat()
                                    if (dist > touchSlop) {
                                        cancelledByMove = true
                                        holdJob?.cancel()
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (vaultMode == VaultMode.DECOY) "Secret Writing Passcode" else "Set Passcode",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colors.text
                        )
                        Text(
                            text = when {
                                vaultMode == VaultMode.DECOY && activePasscodeEnabled ->
                                    "Enabled (Manual PIN only · Biometrics disabled)"
                                vaultMode == VaultMode.DECOY ->
                                    "Off (Protect Secret Writing with a manual 4–6 digit PIN)"
                                activePasscodeEnabled ->
                                    "Enabled (Tap to Change or Remove)"
                                else ->
                                    "Off (Protect Everyday vault with a 4–6 digit PIN)"
                            },
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            color = colors.muted
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (vaultMode == VaultMode.PRIMARY) {
                    HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Biometric Unlock",
                        subtitle = if (hasPrimaryPasscode) {
                            "Unlock Everyday vault with fingerprint or face"
                        } else {
                            "Set an Everyday passcode first to enable"
                        },
                        checked = isBiometricEnabled,
                        enabled = hasPrimaryPasscode,
                        onCheckedChange = { handleBiometricToggle(it) }
                    )
                }
            }

            // Legacy Data Migration (Secret Writing Mode ONLY when older Primary diaries exist)
            if (vaultMode == VaultMode.DECOY && legacyPrimaryLogsCount > 0) {
                SettingsGroup(title = "LEGACY DATA MIGRATION") {
                    SettingsNavRow(
                        icon = Icons.Outlined.DriveFileMove,
                        title = "Move Legacy Diaries Here",
                        subtitle = "Import $legacyPrimaryLogsCount older ${if (legacyPrimaryLogsCount == 1) "diary" else "diaries"} safely into Secret Writing",
                        onClick = { showLegacyMigrateDialog = true }
                    )
                }
            }

            // Group 3: Backup (Strictly isolated by vault mode)
            if (vaultMode == VaultMode.PRIMARY) {
                SettingsGroup(title = "BACKUP") {
                    SettingsNavRow(
                        icon = Icons.Outlined.Restore,
                        title = "Backup & Restore",
                        subtitle = "Encrypted LKSTRM02 backup of notes, trackers, reminders, documents & passwords",
                        onClick = { currentSubPage = SettingsSubPage.BACKUP_RESTORE }
                    )
                }
            } else {
                SettingsGroup(title = "VAULT & BACKUP") {
                    SettingsNavRow(
                        icon = Icons.Outlined.Restore,
                        title = "Backup & Restore",
                        subtitle = "Encrypted LKSTRM02 streaming backup to device folder",
                        onClick = { currentSubPage = SettingsSubPage.BACKUP_RESTORE }
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                    SettingsNavRow(
                        icon = Icons.Outlined.Book,
                        title = "Backed-up Diaries",
                        subtitle = "Read or restore entries directly from your vault backup",
                        onClick = { currentSubPage = SettingsSubPage.BACKED_UP_DIARIES }
                    )
                }
            }

            // Group 4: About & Updates
            SettingsGroup(title = "ABOUT & UPDATES") {
                SettingsNavRow(
                    icon = Icons.Outlined.Info,
                    title = "Legal, About & Data Notice",
                    subtitle = "Privacy policy, security architecture, .zip export & reset",
                    onClick = { currentSubPage = SettingsSubPage.LEGAL_ABOUT }
                )
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCheckingUpdate) { handleCheckForUpdates() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCheckingUpdate) "Checking for Updates..." else "Check for Updates",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colors.text
                        )
                        Text(
                            text = "Compare against latest GitHub APK release",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            color = colors.muted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "v$APP_CURRENT_VERSION",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = colors.accent
                        )
                    }
                }
            }
        }
    }

    // ==================== Passcode Dialog (Everyday or Secret) ====================
    if (showPasscodeDialog) {
        PasscodeSetupDialog(
            isDecoyTarget = isDecoyPinTarget,
            hasExistingPin = if (isDecoyPinTarget) hasDecoyPasscode else hasPrimaryPasscode,
            repository = repository,
            onSaved = {
                hasPrimaryPasscode = repository.hasPrimaryPasscode()
                hasDecoyPasscode = repository.hasDecoyPasscode()
                showPasscodeDialog = false
                statusToast = "Passcode saved."
            },
            onRemoved = { removedDecoy ->
                hasPrimaryPasscode = repository.hasPrimaryPasscode()
                hasDecoyPasscode = repository.hasDecoyPasscode()
                isBiometricEnabled = repository.isBiometricEnabled()
                showPasscodeDialog = false
                if (removedDecoy && vaultMode == VaultMode.DECOY) {
                    onSwitchedToPrimaryVault()
                }
                statusToast = "Passcode removed."
            },
            onDismiss = { showPasscodeDialog = false }
        )
    }

    // ==================== Legacy Diaries Migration Confirm Dialog ====================
    if (showLegacyMigrateDialog) {
        LikkhoHairlineDialog(
            onDismissRequest = { if (!isMigratingLegacy) showLegacyMigrateDialog = false },
            title = "Move Legacy Diaries?",
            subtitle = "IMPORT TO SECRET WRITING",
            leadingIcon = Icons.Outlined.DriveFileMove,
            dismissEnabled = !isMigratingLegacy,
            maxWidth = 410.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    enabled = !isMigratingLegacy,
                    onClick = { showLegacyMigrateDialog = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = if (isMigratingLegacy) "Moving..." else "Move Here",
                    enabled = !isMigratingLegacy,
                    onClick = {
                        isMigratingLegacy = true
                        scope.launch {
                            val (addedCount, updatedLogs) = withContext(Dispatchers.IO) {
                                val added = repository.migrateLegacyPrimaryLogsToSecretVault(removeFromPrimary = true)
                                added to repository.loadLogs(VaultMode.DECOY)
                            }
                            isMigratingLegacy = false
                            showLegacyMigrateDialog = false
                            onLegacyPrimaryLogsMigrated(updatedLogs)
                            statusToast = if (addedCount > 0) {
                                "Moved $addedCount legacy ${if (addedCount == 1) "diary" else "diaries"} into Secret Writing."
                            } else {
                                "Legacy diaries synced into Secret Writing."
                            }
                        }
                    }
                )
            }
        ) {
            Text(
                text = "$legacyPrimaryLogsCount older ${if (legacyPrimaryLogsCount == 1) "diary was" else "diaries were"} found in standard storage from an earlier app version.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
            Text(
                text = "Moving them transfers all entries cleanly into Secret Writing while removing them from Everyday storage.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.muted
            )
        }
    }

    // ==================== App Update Dialog ====================
    val pendingUpdate = updateModalResult
    if (pendingUpdate != null) {
        AppUpdateDialog(
            result = pendingUpdate,
            onDismiss = { updateModalResult = null }
        )
    }
}

// ==================== Reusable Settings Rows ====================

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LikkhoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.7.sp,
            color = colors.muted
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .border(1.dp, colors.border)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.text
            )
            Text(
                text = subtitle,
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LikkhoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.text
            )
            Text(
                text = subtitle,
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.muted
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.surface,
                uncheckedBorderColor = colors.border
            )
        )
    }
}

// ==================== Passcode Setup Dialog ====================

@Composable
private fun PasscodeSetupDialog(
    isDecoyTarget: Boolean,
    hasExistingPin: Boolean,
    repository: NativeVaultRepository,
    onSaved: () -> Unit,
    onRemoved: (removedDecoy: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun handleSave() {
        val clean = pin.trim()
        if (clean.length !in 4..6 || !clean.all { it.isDigit() }) {
            errorMsg = "Passcode must be 4 to 6 digits."
            return
        }
        if (clean != confirmPin.trim()) {
            errorMsg = "Passcodes do not match."
            return
        }
        scope.launch {
            val conflict = withContext(Dispatchers.IO) {
                if (isDecoyTarget) {
                    repository.verifyPrimaryPasscode(clean)
                } else {
                    repository.verifyDecoyPasscode(clean)
                }
            }
            if (conflict) {
                errorMsg = "Please choose a different passcode."
                return@launch
            }
            withContext(Dispatchers.IO) {
                if (isDecoyTarget) {
                    repository.setDecoyPasscode(clean)
                } else {
                    repository.setPrimaryPasscode(clean)
                }
            }
            onSaved()
        }
    }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = if (isDecoyTarget) "Set Secret Passcode" else "Set Passcode",
        leadingIcon = Icons.Outlined.Lock,
        maxWidth = 400.dp,
        footer = {
            if (hasExistingPin) {
                LikkhoDialogOutlineButton(
                    text = "Remove",
                    textColor = colors.crimson,
                    borderColor = colors.crimson.copy(alpha = 0.6f),
                    bgColor = colors.crimson.copy(alpha = 0.08f),
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (isDecoyTarget) {
                                    repository.clearDecoyPasscode()
                                } else {
                                    repository.clearPrimaryPasscode()
                                    repository.clearDecoyPasscode()
                                    repository.setBiometricEnabled(false)
                                }
                            }
                            onRemoved(isDecoyTarget)
                        }
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            LikkhoDialogOutlineButton(
                text = "Cancel",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = "Save",
                onClick = { handleSave() }
            )
        }
    ) {
        if (!errorMsg.isNullOrBlank()) {
            Text(
                text = errorMsg!!,
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.crimson,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.crimson.copy(alpha = 0.08f))
                    .border(LikkhoHairlineBorder, colors.crimson, RectangleShape)
                    .padding(8.dp)
            )
        }

        PinDigitsInputField(
            label = "Enter 4–6 Digit PIN",
            value = pin,
            onValueChange = { next ->
                if (next.length <= 6 && next.all { it.isDigit() }) {
                    pin = next
                    errorMsg = null
                }
            }
        )

        PinDigitsInputField(
            label = "Confirm PIN",
            value = confirmPin,
            onValueChange = { next ->
                if (next.length <= 6 && next.all { it.isDigit() }) {
                    confirmPin = next
                    errorMsg = null
                }
            }
        )
    }
}

@Composable
private fun PinDigitsInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val colors = LikkhoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = colors.muted
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(colors.surface)
                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "••••",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 14.sp,
                    color = colors.muted
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = TextStyle(
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 16.sp,
                    letterSpacing = 4.sp,
                    color = colors.text
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


// ==================== Legal, About, Zip Export & Clear All Data Sub-Screen ====================

@Composable
private fun LegalAndAboutSubScreen(
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    exportEngine: NativeExportEngine,
    onClearAllDataCompleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var isExportingZip by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showClearConfirm -> showClearConfirm = false
            !isExportingZip -> onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(enabled = !isExportingZip) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Legal, About & Data Notice",
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage!!,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    color = colors.emerald,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.emerald.copy(alpha = 0.10f))
                        .border(1.dp, colors.emerald)
                        .padding(12.dp)
                )
            }

            LegalSectionCard(
                title = "1. Privacy Policy",
                paragraphs = listOf(
                    "Likkho operates on a strict local-first, zero-server-storage architecture. Your notes, recurring trackers, reminders, personal documents, passwords, and passcodes remain exclusively on your device.",
                    "On Android, vault JSON records are encrypted at rest inside app-private storage using AndroidKeyStore AES-256-GCM keys, and screenshots/app switcher previews are protected with FLAG_SECURE when locked or configured.",
                    "Network requests are only initiated manually by you when checking GitHub Releases for app updates."
                )
            )

            LegalSectionCard(
                title = "2. Terms of Service",
                paragraphs = listOf(
                    "Likkho is provided \"as is\" as a private personal organizer and workspace. Because no cloud servers hold your passcodes or encryption keys, lost PINs or forgotten Backup Encryption Keys cannot be recovered by the developer.",
                    "Please maintain regular encrypted LKSTRM02 backups to your selected Storage Access Framework folder or export `.zip` archives."
                )
            )

            LegalSectionCard(
                title = "3. Security & Cryptography Architecture",
                paragraphs = listOf(
                    if (vaultMode == VaultMode.DECOY) {
                        "Streaming Vault Backups (`decoy_vault.db`) use 1.5 MB chunked AES-256-GCM framing (`LKSTRM02`) with 210,000-iteration PBKDF2 SHA-512 key derivation, completely isolated from Everyday vault storage."
                    } else {
                        "Streaming Vault Backups (`real_vault.db`) use 1.5 MB chunked AES-256-GCM framing (`LKSTRM02`) with PBKDF2 key derivation and AndroidKeyStore hardware-backed local encryption."
                    }
                )
            )

            // 4. App Info & Links
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(1.dp, colors.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "4. App Info & Contact",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.text
                )
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Text(
                    text = "Likkho v$APP_CURRENT_VERSION · Author: $AUTHOR_NAME",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colors.text
                )
                Text(
                    text = GITHUB_REPO_URL,
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 11.sp,
                    color = colors.accent,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(GITHUB_REPO_URL) } }
                )
                Text(
                    text = AUTHOR_EMAIL,
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 11.sp,
                    color = colors.accent,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("mailto:$AUTHOR_EMAIL") } }
                )
            }

            // 5. Data Management (.zip Export + Clear All Data)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(1.dp, colors.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "5. Data Management & Deletion Notice",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.text
                )
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Text(
                    text = if (vaultMode == VaultMode.DECOY) {
                        "Export all Secret Writing entries, attached photos, and voice notes as a standard `.zip` archive, or permanently wipe local app data."
                    } else {
                        "Export all quick notes, trackers, reminders, documents, and saved passwords as a standard `.zip` archive, or permanently wipe all local app data."
                    },
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.muted
                )

                if (isExportingZip) {
                    LinearProgressIndicator(
                        color = colors.accent,
                        trackColor = colors.hairline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .alpha(if (isExportingZip) 0.5f else 1f)
                        .background(colors.accent)
                        .clickable(enabled = !isExportingZip) {
                            isExportingZip = true
                            scope.launch {
                                val res = runCatching {
                                    withContext(Dispatchers.IO) {
                                        exportEngine.exportAllVaultDataAsZipToDownloads(vaultMode)
                                    }
                                }
                                isExportingZip = false
                                res.onSuccess { exported ->
                                    statusMessage = "Exported ${exported.filename} to ${exported.displayLocation}"
                                }.onFailure { err ->
                                    statusMessage = err.message ?: "ZIP export failed."
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExportingZip) "Creating .zip Archive..." else "Export All Data (.zip)",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .alpha(if (isExportingZip) 0.5f else 1f)
                        .background(colors.crimson.copy(alpha = 0.08f))
                        .border(1.dp, colors.crimson)
                        .clickable(enabled = !isExportingZip) { showClearConfirm = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = colors.crimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear All Data",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.crimson
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        LikkhoHairlineDialog(
            onDismissRequest = { showClearConfirm = false },
            title = "Clear All Local Data?",
            leadingIcon = Icons.Outlined.WarningAmber,
            leadingIconTint = colors.crimson,
            borderColor = colors.crimson.copy(alpha = 0.7f),
            titleColor = colors.crimson,
            maxWidth = 400.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    onClick = { showClearConfirm = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Erase Everything",
                    bgColor = colors.crimson,
                    onClick = {
                        showClearConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.clearAllVaultData()
                            }
                            onClearAllDataCompleted()
                        }
                    }
                )
            }
        ) {
            Text(
                text = "This permanently deletes all local notes, trackers, reminders, documents, passwords, entries, media, and passcodes inside Likkho. External SAF backups are untouched.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
        }
    }
}

@Composable
private fun LegalSectionCard(
    title: String,
    paragraphs: List<String>
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .border(1.dp, colors.border)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontFamily = LikkhoFontFamilies.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colors.text
        )
        HorizontalDivider(thickness = 1.dp, color = colors.hairline)
        paragraphs.forEach { p ->
            Text(
                text = p,
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
        }
    }
}

// ==================== App Update Dialog ====================

@Composable
private fun AppUpdateDialog(
    result: GithubUpdateCheckResult,
    onDismiss: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val uriHandler = LocalUriHandler.current
    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = "Update Available",
        subtitle = "Installed v${result.currentVersion} → Latest v${result.latestVersion}",
        leadingIcon = Icons.Outlined.SystemUpdate,
        maxWidth = 440.dp,
        maxHeight = 560.dp,
        bodyPadding = 16.dp,
        footer = {
            LikkhoDialogOutlineButton(
                text = "Later",
                onClick = onDismiss
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = "Download Update",
                leadingIcon = Icons.Outlined.Download,
                onClick = {
                    runCatching { uriHandler.openUri(result.downloadUrl) }
                    onDismiss()
                }
            )
        }
    ) {
        Text(
            text = "${result.releaseName} (v${result.latestVersion})",
            fontFamily = LikkhoFontFamilies.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = colors.accent
        )
        HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
        Text(
            text = result.releaseNotes.ifBlank { "A new version of Likkho is available on GitHub." },
            fontFamily = LikkhoFontFamilies.Sans,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.text
        )
    }
}

// ==================== Helpers ====================

private tailrec fun Context.findSettingsFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findSettingsFragmentActivity()
    else -> null
}


private fun compareSemver(a: String, b: String): Int {
    val cleanA = a.trim().removePrefix("v").removePrefix("V").substringBefore('-')
    val cleanB = b.trim().removePrefix("v").removePrefix("V").substringBefore('-')
    val partsA = cleanA.split('.').map { it.toIntOrNull() ?: 0 }
    val partsB = cleanB.split('.').map { it.toIntOrNull() ?: 0 }
    val len = maxOf(partsA.size, partsB.size)
    for (i in 0 until len) {
        val pa = partsA.getOrElse(i) { 0 }
        val pb = partsB.getOrElse(i) { 0 }
        if (pa != pb) return pa.compareTo(pb)
    }
    return 0
}

private fun fetchGithubJson(endpointUrl: String): Pair<Int, String?> {
    var conn: HttpURLConnection? = null
    return try {
        val url = URL(endpointUrl)
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Likkho-Android/$APP_CURRENT_VERSION")
        }
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        code to body
    } finally {
        conn?.disconnect()
    }
}

private fun fetchLatestGithubRelease(): GithubUpdateCheckResult {
    return try {
        val (latestCode, latestBody) = fetchGithubJson(
            "https://api.github.com/repos/$DEFAULT_GITHUB_REPO/releases/latest"
        )

        val releaseJson: JSONObject? = when {
            latestCode in 200..299 && !latestBody.isNullOrBlank() -> JSONObject(latestBody)
            latestCode == 404 -> {
                val (listCode, listBody) = fetchGithubJson(
                    "https://api.github.com/repos/$DEFAULT_GITHUB_REPO/releases?per_page=5"
                )
                if (listCode !in 200..299 || listBody.isNullOrBlank()) {
                    return GithubUpdateCheckResult(
                        hasUpdate = false,
                        currentVersion = APP_CURRENT_VERSION,
                        latestVersion = APP_CURRENT_VERSION,
                        releaseName = "",
                        releaseNotes = "",
                        downloadUrl = GITHUB_REPO_URL,
                        error = "Unable to check updates right now (HTTP $listCode)."
                    )
                }
                val arr = JSONArray(listBody)
                var chosen: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (!item.optBoolean("draft", false)) {
                        chosen = item
                        break
                    }
                }
                chosen
            }
            else -> {
                return GithubUpdateCheckResult(
                    hasUpdate = false,
                    currentVersion = APP_CURRENT_VERSION,
                    latestVersion = APP_CURRENT_VERSION,
                    releaseName = "",
                    releaseNotes = "",
                    downloadUrl = GITHUB_REPO_URL,
                    error = "Unable to check updates right now (HTTP $latestCode)."
                )
            }
        }

        if (releaseJson == null) {
            return GithubUpdateCheckResult(
                hasUpdate = false,
                currentVersion = APP_CURRENT_VERSION,
                latestVersion = APP_CURRENT_VERSION,
                releaseName = "",
                releaseNotes = "",
                downloadUrl = GITHUB_REPO_URL,
                error = null
            )
        }

        val tag = releaseJson.optString("tag_name", APP_CURRENT_VERSION)
        val latestClean = tag.trim().removePrefix("v").removePrefix("V")
        val releaseName = releaseJson.optString("name", tag).ifBlank { tag }
        val releaseNotes = releaseJson.optString("body", "")
        val htmlUrl = releaseJson.optString("html_url", GITHUB_REPO_URL)

        var downloadUrl = htmlUrl
        val assets = releaseJson.optJSONArray("assets")
        if (assets != null) {
            var anyApk: String? = null
            var signedReleaseApk: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "").lowercase(Locale.US)
                val dl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk") && dl.isNotBlank()) {
                    if (assetName.contains("release")) {
                        signedReleaseApk = dl
                        break
                    } else if (anyApk == null) {
                        anyApk = dl
                    }
                }
            }
            downloadUrl = signedReleaseApk ?: anyApk ?: htmlUrl
        }

        GithubUpdateCheckResult(
            hasUpdate = compareSemver(latestClean, APP_CURRENT_VERSION) > 0,
            currentVersion = APP_CURRENT_VERSION,
            latestVersion = latestClean,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            error = null
        )
    } catch (_: Exception) {
        GithubUpdateCheckResult(
            hasUpdate = false,
            currentVersion = APP_CURRENT_VERSION,
            latestVersion = APP_CURRENT_VERSION,
            releaseName = "",
            releaseNotes = "",
            downloadUrl = GITHUB_REPO_URL,
            error = "Network unavailable. Could not check for updates."
        )
    }
}

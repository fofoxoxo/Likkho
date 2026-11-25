package com.likkho.diary.nativeapp.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.diary.nativeapp.crypto.InvalidEncryptionKeyException
import com.likkho.diary.nativeapp.crypto.StreamCryptoEngine
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.CustomFontItem
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.MicRecordingSettings
import com.likkho.diary.nativeapp.model.VaultBackupBundle
import com.likkho.diary.nativeapp.model.VaultConfig
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackupStatusType {
    SUCCESS,
    ERROR
}

private data class BackupStatusBanner(
    val type: BackupStatusType,
    val text: String
)

/**
 * Native Streaming Backup & Restore Screen powered by [StreamCryptoEngine].
 *
 * Parity with `src/components/BackupRestorePage.tsx`:
 * - SAF Backup Folder picker (`OpenDocumentTree` with persistent URI grant)
 * - Set Encryption Key (PBKDF2 / SHA verification hint)
 * - Streaming `LKSTRM02` AES-256-GCM Backup with per-item progress bar
 * - Streaming `LKSTRM02`/`LKSTRM01`/legacy `WIKILOG_VAULT_V1` Restore
 * - Shared progressive brute-force cooldown guard
 */
@Composable
fun BackupRestoreScreen(
    vaultMode: VaultMode,
    logs: List<DiaryLog>,
    customFonts: List<CustomFontItem>,
    micSettings: MicRecordingSettings,
    repository: NativeVaultRepository,
    streamCryptoEngine: StreamCryptoEngine,
    onBundleRestored: (VaultBackupBundle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val scope = rememberCoroutineScope()
    val vaultCfg = remember(vaultMode) { VaultConfig.forMode(vaultMode) }

    var safTreeUri by remember(vaultMode) { mutableStateOf(repository.getSafTreeUri(vaultMode)) }
    var safFolderLabel by remember(vaultMode) {
        mutableStateOf(repository.getSafFolderDisplayName(vaultMode))
    }
    var savedKeyHash by remember(vaultMode) {
        mutableStateOf(repository.getEncryptionKeyHintHash(vaultMode))
    }
    var backupMeta by remember(vaultMode) {
        mutableStateOf(repository.loadBackupMetadata(vaultMode))
    }

    var newKey by remember { mutableStateOf("") }
    var confirmKey by remember { mutableStateOf("") }
    var showKeyText by remember { mutableStateOf(false) }

    var backupPassphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }

    var statusBanner by remember { mutableStateOf<BackupStatusBanner?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    val isBusy = isBackingUp || isRestoring

    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var progressBytes by remember { mutableLongStateOf(0L) }

    var cooldownSec by remember {
        mutableIntStateOf(repository.getRemainingPasscodeCooldownSeconds())
    }
    val isCoolingDown = cooldownSec > 0

    BackHandler {
        if (!isBusy) {
            onBack()
        }
    }

    LaunchedEffect(cooldownSec) {
        while (true) {
            val rem = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            cooldownSec = rem
            if (rem <= 0) break
            delay(500L)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            repository.persistSafTreeUri(vaultMode, pickedUri)
            safTreeUri = repository.getSafTreeUri(vaultMode)
            safFolderLabel = repository.getSafFolderDisplayName(vaultMode)
            statusBanner = BackupStatusBanner(
                type = BackupStatusType.SUCCESS,
                text = "Backup folder selected: ${safFolderLabel.ifEmpty { "Selected Folder" }}/${vaultCfg.safFolderName}"
            )
        }
    }

    fun handleSaveEncryptionKey() {
        val trimmed = newKey.trim()
        if (trimmed.length < 4) {
            statusBanner = BackupStatusBanner(
                BackupStatusType.ERROR,
                "Encryption Key must be at least 4 characters."
            )
            return
        }
        if (newKey != confirmKey) {
            statusBanner = BackupStatusBanner(
                BackupStatusType.ERROR,
                "Encryption Keys do not match."
            )
            return
        }
        scope.launch {
            val hash = withContext(Dispatchers.IO) {
                repository.setEncryptionKeyPassphrase(vaultMode, trimmed)
            }
            savedKeyHash = hash
            backupPassphrase = trimmed
            newKey = ""
            confirmKey = ""
            statusBanner = BackupStatusBanner(
                BackupStatusType.SUCCESS,
                "Encryption Key saved."
            )
        }
    }

    fun handleStartBackup() {
        scope.launch {
            val rem = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            if (rem > 0) {
                cooldownSec = rem
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    "Too many wrong attempts. Please wait ${rem}s."
                )
                return@launch
            }
            val uri = safTreeUri ?: repository.getSafTreeUri(vaultMode)
            if (uri == null) {
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    "Please choose a Backup Folder first."
                )
                return@launch
            }
            val key = backupPassphrase.trim()
            if (key.isEmpty()) {
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    "Please enter your Encryption Key."
                )
                return@launch
            }

            isBackingUp = true
            progressCurrent = 0
            progressTotal = 0
            progressBytes = 0L
            statusBanner = null

            try {
                val keyMatches = withContext(Dispatchers.IO) {
                    val existing = repository.getEncryptionKeyHintHash(vaultMode)
                    if (existing != null) repository.verifyEncryptionKeyPassphrase(vaultMode, key) else true
                }
                if (!keyMatches) {
                    val fail = withContext(Dispatchers.IO) { repository.recordPasscodeFailure() }
                    statusBanner = if (fail.cooldownSeconds > 0) {
                        cooldownSec = fail.cooldownSeconds
                        BackupStatusBanner(
                            BackupStatusType.ERROR,
                            "Incorrect Encryption Key. Locked for ${fail.cooldownSeconds}s."
                        )
                    } else {
                        BackupStatusBanner(BackupStatusType.ERROR, "Incorrect Encryption Key.")
                    }
                    isBackingUp = false
                    return@launch
                }

                val savedPasswords = withContext(Dispatchers.IO) { repository.loadSavedPasswords(vaultMode) }
                val quickNotes = withContext(Dispatchers.IO) { repository.loadQuickNotes(vaultMode) }
                withContext(Dispatchers.IO) { repository.resetPasscodeFailures() }

                val bundle = VaultBackupBundle(
                    logs = logs,
                    customFonts = customFonts,
                    micSettings = micSettings,
                    savedPasswords = savedPasswords,
                    quickNotes = quickNotes
                )

                val result = withContext(Dispatchers.IO) {
                    streamCryptoEngine.backupVaultToSafTree(
                        bundle = bundle,
                        passphrase = key,
                        vaultMode = vaultMode,
                        treeUri = uri
                    ) { currentItem, totalItems, bytesWritten ->
                        progressCurrent = currentItem
                        progressTotal = totalItems
                        progressBytes = bytesWritten
                    }
                }

                val nextHash = withContext(Dispatchers.IO) {
                    repository.getEncryptionKeyHintHash(vaultMode)
                        ?: repository.setEncryptionKeyPassphrase(vaultMode, key)
                }
                savedKeyHash = nextHash
                backupMeta = withContext(Dispatchers.IO) { repository.loadBackupMetadata(vaultMode) }

                statusBanner = if (result.failedMediaCount > 0) {
                    BackupStatusBanner(
                        BackupStatusType.ERROR,
                        "Backup finished with warnings: ${result.failedMediaCount} media files could not be archived."
                    )
                } else {
                    BackupStatusBanner(
                        BackupStatusType.SUCCESS,
                        "Backup completed successfully (${result.entryCount} diaries · ${formatReadableSize(result.totalBytes)})."
                    )
                }
            } catch (err: Throwable) {
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    err.message ?: "Backup failed."
                )
            } finally {
                isBackingUp = false
            }
        }
    }

    fun handleStartRestore() {
        scope.launch {
            val rem = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            if (rem > 0) {
                cooldownSec = rem
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    "Too many wrong attempts. Please wait ${rem}s."
                )
                return@launch
            }
            val uri = safTreeUri ?: repository.getSafTreeUri(vaultMode)
            if (uri == null) {
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    "Please choose a Backup Folder first."
                )
                return@launch
            }
            val key = restorePassphrase.trim()
            if (key.isEmpty()) {
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    "Please enter your Encryption Key."
                )
                return@launch
            }

            isRestoring = true
            statusBanner = null
            try {
                val restoredBundle = withContext(Dispatchers.IO) {
                    streamCryptoEngine.restoreFromSafTree(
                        passphrase = key,
                        vaultMode = vaultMode,
                        treeUri = uri,
                        writeMediaToDisk = true
                    )
                }

                withContext(Dispatchers.IO) {
                    repository.resetPasscodeFailures()
                    savedKeyHash = repository.setEncryptionKeyPassphrase(vaultMode, key)
                    repository.saveLogs(vaultMode, restoredBundle.logs)
                    if (restoredBundle.customFonts.isNotEmpty()) {
                        repository.saveCustomFonts(vaultMode, restoredBundle.customFonts)
                    }
                    repository.saveMicSettings(vaultMode, restoredBundle.micSettings)
                    if (restoredBundle.savedPasswords.isNotEmpty()) {
                        repository.saveSavedPasswords(vaultMode, restoredBundle.savedPasswords)
                    }
                    if (restoredBundle.quickNotes.isNotEmpty()) {
                        repository.saveQuickNotes(vaultMode, restoredBundle.quickNotes)
                    }
                }

                onBundleRestored(restoredBundle)
                restorePassphrase = ""
                statusBanner = if (restoredBundle.skippedMediaCount > 0) {
                    BackupStatusBanner(
                        BackupStatusType.ERROR,
                        "Data restored with warnings: ${restoredBundle.skippedMediaCount} incomplete media items were skipped."
                    )
                } else {
                    BackupStatusBanner(
                        BackupStatusType.SUCCESS,
                        "Data restored successfully (${restoredBundle.logs.size} diaries)."
                    )
                }
            } catch (badKey: InvalidEncryptionKeyException) {
                val fail = withContext(Dispatchers.IO) { repository.recordPasscodeFailure() }
                statusBanner = if (fail.cooldownSeconds > 0) {
                    cooldownSec = fail.cooldownSeconds
                    BackupStatusBanner(
                        BackupStatusType.ERROR,
                        "Incorrect Encryption Key. Locked for ${fail.cooldownSeconds}s."
                    )
                } else {
                    BackupStatusBanner(
                        BackupStatusType.ERROR,
                        badKey.message ?: "Incorrect Encryption Key."
                    )
                }
            } catch (err: Throwable) {
                statusBanner = BackupStatusBanner(
                    BackupStatusType.ERROR,
                    err.message ?: "Failed to restore backup."
                )
            } finally {
                isRestoring = false
            }
        }
    }

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
                    .clickable(enabled = !isBusy) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text = "Backup & Restore",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isCoolingDown) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.crimson.copy(alpha = 0.10f))
                        .border(1.dp, colors.crimson)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = colors.crimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Cooldown active due to continuous wrong attempts: ${cooldownSec}s remaining",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.crimson
                    )
                }
            }

            statusBanner?.let { banner ->
                val isSuccess = banner.type == BackupStatusType.SUCCESS
                val accentColor = if (isSuccess) colors.emerald else colors.crimson
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentColor.copy(alpha = 0.10f))
                        .border(1.dp, accentColor)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = banner.text,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = if (isSuccess) colors.text else colors.crimson,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 1. SAF Backup Folder Selection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(1.dp, colors.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Backup Folder (SAF)",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.text
                        )
                    }
                    if (safTreeUri != null) {
                        Text(
                            text = "✓ Linked",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.emerald
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                Text(
                    text = if (safTreeUri != null) {
                        "Location: ${safFolderLabel.ifEmpty { "Selected Storage" }}/${vaultCfg.safFolderName}/${vaultCfg.dbFilename}"
                    } else {
                        "Select a device folder to store encrypted LKSTRM02 vault backups."
                    },
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 11.sp,
                    color = colors.muted
                )

                if (backupMeta.exists && backupMeta.lastBackupAt != null) {
                    val dateStr = SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.ENGLISH)
                        .format(Date(backupMeta.lastBackupAt!!))
                    Text(
                        text = "Last Backup: $dateStr · ${backupMeta.entryCount} diaries · ${formatReadableSize(backupMeta.sizeBytes)}",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.muted
                    )
                }

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .alpha(if (isBusy) 0.5f else 1f)
                        .background(colors.surface)
                        .border(1.dp, colors.border)
                        .clickable(enabled = !isBusy) { folderPickerLauncher.launch(null) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (safTreeUri != null) "Change Backup Folder" else "Choose Backup Folder",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.text
                    )
                }
            }

            // 2. Set Encryption Key
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(1.dp, colors.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Set Encryption Key",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.text
                        )
                    }
                    if (!savedKeyHash.isNullOrBlank()) {
                        Text(
                            text = "✓ Key Set",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.emerald
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                EncryptionKeyInputField(
                    label = "Encryption Key",
                    value = newKey,
                    onValueChange = { newKey = it },
                    placeholder = "Enter encryption key",
                    showText = showKeyText,
                    onToggleVisibility = { showKeyText = !showKeyText },
                    enabled = !isBusy
                )

                EncryptionKeyInputField(
                    label = "Confirm Encryption Key",
                    value = confirmKey,
                    onValueChange = { confirmKey = it },
                    placeholder = "Confirm encryption key",
                    showText = showKeyText,
                    onToggleVisibility = null,
                    enabled = !isBusy
                )

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .alpha(if (isBusy) 0.5f else 1f)
                        .background(colors.surface)
                        .border(1.dp, colors.border)
                        .clickable(enabled = !isBusy) { handleSaveEncryptionKey() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Save Key",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.text
                    )
                }
            }

            // 3. Streaming Backup
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(1.dp, colors.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Backup",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                EncryptionKeyInputField(
                    label = "Encryption Key",
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    placeholder = "Enter encryption key",
                    showText = showKeyText,
                    onToggleVisibility = { showKeyText = !showKeyText },
                    enabled = !isBusy && !isCoolingDown
                )

                if (isBackingUp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (progressTotal > 0) {
                            LinearProgressIndicator(
                                progress = { (progressCurrent.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f) },
                                color = colors.accent,
                                trackColor = colors.hairline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            Text(
                                text = "Streaming encrypted media $progressCurrent/$progressTotal (${formatReadableSize(progressBytes)} written)...",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        } else {
                            LinearProgressIndicator(
                                color = colors.accent,
                                trackColor = colors.hairline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            Text(
                                text = "Deriving key & encrypting manifest...",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .alpha(if (isBusy || isCoolingDown) 0.5f else 1f)
                        .background(colors.accent)
                        .clickable(enabled = !isBusy && !isCoolingDown) { handleStartBackup() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = when {
                            isCoolingDown -> "Wait ${cooldownSec}s"
                            isBackingUp -> "Backing up..."
                            else -> "Backup"
                        },
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }

            // 4. Streaming Restore
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(1.dp, colors.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Restore",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                EncryptionKeyInputField(
                    label = "Encryption Key",
                    value = restorePassphrase,
                    onValueChange = { restorePassphrase = it },
                    placeholder = "Enter encryption key",
                    showText = showKeyText,
                    onToggleVisibility = { showKeyText = !showKeyText },
                    enabled = !isBusy && !isCoolingDown
                )

                if (isRestoring) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            color = colors.accent,
                            trackColor = colors.hairline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        Text(
                            text = "Decrypting streaming backup vault...",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .alpha(if (isBusy || isCoolingDown) 0.5f else 1f)
                        .background(colors.surface)
                        .border(1.dp, colors.accent)
                        .clickable(enabled = !isBusy && !isCoolingDown) { handleStartRestore() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = when {
                            isCoolingDown -> "Wait ${cooldownSec}s"
                            isRestoring -> "Restoring..."
                            else -> "Restore"
                        },
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun EncryptionKeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showText: Boolean,
    onToggleVisibility: (() -> Unit)?,
    enabled: Boolean
) {
    val colors = LikkhoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = colors.text
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .background(colors.surface)
                .border(1.dp, colors.border)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = if (showText) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 12.sp,
                        color = colors.text
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (onToggleVisibility != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onToggleVisibility() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showText) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatReadableSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

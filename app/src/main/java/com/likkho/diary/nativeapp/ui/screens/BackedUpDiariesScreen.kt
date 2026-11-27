package com.likkho.diary.nativeapp.ui.screens

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.likkho.diary.nativeapp.crypto.InvalidEncryptionKeyException
import com.likkho.diary.nativeapp.crypto.StreamCryptoEngine
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.media.NativeMediaEngine
import com.likkho.diary.nativeapp.model.CanvasAudioAttachment
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogOutlineButton
import com.likkho.diary.nativeapp.ui.components.LikkhoDialogPrimaryButton
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineBorder
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineDialog
import com.likkho.diary.nativeapp.model.DiaryLog
import com.likkho.diary.nativeapp.model.VaultBackupBundle
import com.likkho.diary.nativeapp.model.VaultConfig
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Native Compose Backed-up Diaries Viewer & Single-Entry Manager.
 *
 * Parity with `src/components/BackedUpDiariesModal.tsx`:
 * - Links SAF backup folder if needed and unlocks `real_vault.db` / `decoy_vault.db` via [StreamCryptoEngine]
 * - Shared progressive brute-force cooldown timer
 * - Live search across unlocked backup entries
 * - Individual diary PIN gate for locked diaries
 * - Full reader modal displaying decoded text, cached/bounded images, and native [MediaPlayer] voice notes
 * - Restore single diary back into the local vault or delete an entry directly from the backup archive
 */
@Composable
fun BackedUpDiariesScreen(
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    streamCryptoEngine: StreamCryptoEngine,
    mediaEngine: NativeMediaEngine,
    onRestoreSingleDiary: (DiaryLog) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val vaultCfg = remember(vaultMode) { VaultConfig.forMode(vaultMode) }

    var safTreeUri by remember(vaultMode) { mutableStateOf(repository.getSafTreeUri(vaultMode)) }
    var safFolderLabel by remember(vaultMode) { mutableStateOf(repository.getSafFolderDisplayName(vaultMode)) }

    var unlockedBundle by remember(vaultMode) { mutableStateOf<VaultBackupBundle?>(null) }
    var unlockedPassphrase by remember(vaultMode) { mutableStateOf("") }

    var passphraseInput by remember { mutableStateOf("") }
    var showKeyText by remember { mutableStateOf(false) }
    var isUnlocking by remember { mutableStateOf(false) }
    var isUpdatingBackup by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var pendingLockedDiary by remember { mutableStateOf<DiaryLog?>(null) }
    var diaryPinInput by remember { mutableStateOf("") }
    var diaryPinError by remember { mutableStateOf<String?>(null) }

    var viewingDiary by remember { mutableStateOf<DiaryLog?>(null) }
    var pendingDeleteDiary by remember { mutableStateOf<DiaryLog?>(null) }

    var cooldownSec by remember {
        mutableIntStateOf(repository.getRemainingPasscodeCooldownSeconds())
    }
    val isCoolingDown = cooldownSec > 0

    BackHandler {
        when {
            pendingDeleteDiary != null -> if (!isUpdatingBackup) pendingDeleteDiary = null
            viewingDiary != null -> viewingDiary = null
            pendingLockedDiary != null -> pendingLockedDiary = null
            isSearchVisible -> {
                isSearchVisible = false
                searchQuery = ""
            }
            !isUnlocking && !isUpdatingBackup -> onBack()
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

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(3000L)
            statusBanner = null
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            repository.persistSafTreeUri(vaultMode, pickedUri)
            safTreeUri = repository.getSafTreeUri(vaultMode)
            safFolderLabel = repository.getSafFolderDisplayName(vaultMode)
            errorMsg = null
        }
    }

    fun handleUnlockBackup() {
        scope.launch {
            val rem = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            if (rem > 0) {
                cooldownSec = rem
                errorMsg = "Too many wrong attempts. Please wait ${rem}s."
                return@launch
            }
            val treeUri = safTreeUri ?: repository.getSafTreeUri(vaultMode)
            if (treeUri == null) {
                errorMsg = "Please choose your Backup Folder first."
                return@launch
            }
            val key = passphraseInput.trim()
            if (key.isEmpty()) {
                errorMsg = "Please enter your Encryption Key."
                return@launch
            }

            isUnlocking = true
            errorMsg = null
            try {
                val bundle = withContext(Dispatchers.IO) {
                    streamCryptoEngine.restoreFromSafTree(
                        passphrase = key,
                        vaultMode = vaultMode,
                        treeUri = treeUri,
                        writeMediaToDisk = true
                    )
                }
                withContext(Dispatchers.IO) {
                    repository.resetPasscodeFailures()
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                unlockedBundle = bundle
                unlockedPassphrase = key
                passphraseInput = ""
            } catch (badKey: InvalidEncryptionKeyException) {
                val fail = withContext(Dispatchers.IO) { repository.recordPasscodeFailure() }
                if (fail.cooldownSeconds > 0) {
                    cooldownSec = fail.cooldownSeconds
                    errorMsg = "Incorrect Encryption Key. Locked for ${fail.cooldownSeconds}s."
                } else {
                    errorMsg = badKey.message ?: "Incorrect Encryption Key."
                }
            } catch (err: Throwable) {
                errorMsg = err.message ?: "Unable to read backed-up vault."
            } finally {
                isUnlocking = false
            }
        }
    }

    fun handleDeleteFromBackup(target: DiaryLog) {
        val currentBundle = unlockedBundle ?: return
        val key = unlockedPassphrase
        val treeUri = safTreeUri ?: repository.getSafTreeUri(vaultMode) ?: return
        if (key.isBlank()) return

        scope.launch {
            isUpdatingBackup = true
            errorMsg = null
            try {
                val remainingLogs = currentBundle.logs.filterNot { it.id == target.id }
                val updatedBundle = currentBundle.copy(logs = remainingLogs)
                withContext(Dispatchers.IO) {
                    streamCryptoEngine.backupVaultToSafTree(
                        bundle = updatedBundle,
                        passphrase = key,
                        vaultMode = vaultMode,
                        treeUri = treeUri
                    )
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                unlockedBundle = updatedBundle
                if (viewingDiary?.id == target.id) {
                    viewingDiary = null
                }
                pendingDeleteDiary = null
                statusBanner = "Deleted \"${target.heading.ifBlank { "Untitled Diary" }}\" from backup."
            } catch (err: Throwable) {
                errorMsg = err.message ?: "Failed to update backup file."
                pendingDeleteDiary = null
            } finally {
                isUpdatingBackup = false
            }
        }
    }

    fun requestReadDiary(diary: DiaryLog) {
        if (!diary.diaryLockPin.isNullOrBlank()) {
            diaryPinInput = ""
            diaryPinError = null
            pendingLockedDiary = diary
        } else {
            viewingDiary = diary
        }
    }

    val filteredDiaries = remember(unlockedBundle, searchQuery) {
        val logs = unlockedBundle?.logs ?: emptyList()
        val q = searchQuery.trim().lowercase()
        val sorted = logs.sortedWith(
            compareByDescending<DiaryLog> { it.pinned }.thenByDescending { it.updatedAt }
        )
        if (q.isEmpty()) {
            sorted
        } else {
            sorted.filter {
                it.heading.lowercase().contains(q) ||
                    it.plainPreview.lowercase().contains(q) ||
                    it.dateStamp.lowercase().contains(q)
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(enabled = !isUnlocking && !isUpdatingBackup) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Backed-up Diaries",
                    fontFamily = LikkhoFontFamilies.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (unlockedBundle != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (isSearchVisible) colors.accent else colors.bg)
                            .border(1.dp, if (isSearchVisible) colors.accent else colors.border)
                            .clickable {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) searchQuery = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search backup",
                            tint = if (isSearchVisible) Color.White else colors.text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .background(colors.bg)
                            .border(1.dp, colors.border)
                            .clickable {
                                unlockedBundle = null
                                unlockedPassphrase = ""
                                searchQuery = ""
                                isSearchVisible = false
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lock",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.text
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        if (unlockedBundle == null) {
            // ==================== Locked / Unlock Gate ====================
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
                            text = "Too many wrong attempts. Please wait ${cooldownSec}s before trying again.",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.crimson
                        )
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.crimson.copy(alpha = 0.10f))
                            .border(1.dp, colors.crimson)
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.crimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = errorMsg!!,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            color = colors.crimson,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // SAF Folder Selection Card
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
                                text = "Vault Folder",
                                fontFamily = LikkhoFontFamilies.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
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

                    Text(
                        text = if (safTreeUri != null) {
                            "${safFolderLabel.ifEmpty { "Selected Folder" }}/${vaultCfg.safFolderName}/${vaultCfg.dbFilename}"
                        } else {
                            "Choose the folder containing your ${vaultCfg.safFolderName} backup."
                        },
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 11.sp,
                        color = colors.muted
                    )

                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .clickable(enabled = !isUnlocking) { folderPickerLauncher.launch(null) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (safTreeUri != null) "Change Folder" else "Choose Backup Folder",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.text
                        )
                    }
                }

                // Unlock Key Card
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
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Unlock Encrypted Vault",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.text
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                    Text(
                        text = "Enter your Encryption Key to decrypt and browse diaries stored inside ${vaultCfg.safFolderName}/${vaultCfg.dbFilename}.",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = colors.muted
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .alpha(if (isUnlocking || isCoolingDown) 0.5f else 1f)
                            .background(colors.surface)
                            .border(1.dp, colors.border)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (passphraseInput.isEmpty()) {
                                Text(
                                    text = "Enter encryption key",
                                    fontFamily = LikkhoFontFamilies.Sans,
                                    fontSize = 12.sp,
                                    color = colors.muted
                                )
                            }
                            BasicTextField(
                                value = passphraseInput,
                                onValueChange = {
                                    passphraseInput = it
                                    errorMsg = null
                                },
                                enabled = !isUnlocking && !isCoolingDown,
                                singleLine = true,
                                visualTransformation = if (showKeyText) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                textStyle = TextStyle(
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontSize = 13.sp,
                                    color = colors.text
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (!isUnlocking && !isCoolingDown) handleUnlockBackup() }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { showKeyText = !showKeyText },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showKeyText) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle key visibility",
                                tint = colors.muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isUnlocking) {
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
                            .alpha(if (isUnlocking || isCoolingDown) 0.5f else 1f)
                            .background(colors.accent)
                            .clickable(enabled = !isUnlocking && !isCoolingDown) { handleUnlockBackup() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isCoolingDown -> "Wait ${cooldownSec}s"
                                isUnlocking -> "Decrypting Vault..."
                                else -> "Unlock Backed-up Diaries"
                            },
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // ==================== Unlocked Diaries List ====================
            val totalCount = unlockedBundle?.logs?.size ?: 0

            if (isSearchVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(colors.bg)
                            .border(1.dp, colors.border)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search backed-up diaries...",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Search
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(colors.bg)
                                .border(1.dp, colors.border)
                                .clickable { searchQuery = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = colors.muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            }

            // Vault summary strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vault Folder: ${vaultCfg.safFolderName}",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 11.sp,
                    color = colors.muted
                )
                Text(
                    text = "$totalCount ${if (totalCount == 1) "entry" else "entries"}",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = colors.accent
                )
            }
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!statusBanner.isNullOrBlank()) {
                    item(key = "status_banner") {
                        Text(
                            text = statusBanner!!,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.emerald,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.emerald.copy(alpha = 0.10f))
                                .border(1.dp, colors.emerald)
                                .padding(10.dp)
                        )
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    item(key = "error_banner") {
                        Text(
                            text = errorMsg!!,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.crimson,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.crimson.copy(alpha = 0.10f))
                                .border(1.dp, colors.crimson)
                                .padding(10.dp)
                        )
                    }
                }

                if (filteredDiaries.isEmpty()) {
                    item(key = "empty_diaries") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .border(1.dp, colors.border)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Book,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching backed-up diaries" else "No diaries in this backup",
                                fontFamily = LikkhoFontFamilies.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.text
                            )
                        }
                    }
                } else {
                    items(filteredDiaries, key = { it.id }) { diary ->
                        val isLocked = !diary.diaryLockPin.isNullOrBlank()
                        val imgCount = diary.canvasImages.size + if (!diary.canvasBgDataUrl.isNullOrBlank()) 1 else 0
                        val audCount = diary.audioAttachments.size

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bg)
                                .border(1.dp, colors.border)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = diary.heading.ifBlank { "Untitled Diary" },
                                        fontFamily = LikkhoFontFamilies.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${diary.dateStamp} · ${diary.timeStamp}",
                                        style = TextStyle(
                                            fontFamily = LikkhoFontFamilies.Mono,
                                            fontSize = 10.sp,
                                            color = colors.muted,
                                            fontFeatureSettings = "tnum"
                                        )
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (diary.pinned) {
                                        Box(
                                            modifier = Modifier
                                                .background(colors.accent.copy(alpha = 0.12f))
                                                .border(1.dp, colors.accent.copy(alpha = 0.4f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.PushPin,
                                                    contentDescription = null,
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "PINNED",
                                                    fontFamily = LikkhoFontFamilies.Sans,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    color = colors.accent
                                                )
                                            }
                                        }
                                    }
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .background(colors.crimson.copy(alpha = 0.10f))
                                                .border(1.dp, colors.crimson.copy(alpha = 0.4f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Lock,
                                                    contentDescription = null,
                                                    tint = colors.crimson,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "LOCKED",
                                                    fontFamily = LikkhoFontFamilies.Sans,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    color = colors.crimson
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = if (isLocked) {
                                    "Protected entry. Unlock with Diary PIN to read."
                                } else {
                                    diary.plainPreview.ifBlank { "No text content." }
                                },
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = colors.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (imgCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Image,
                                                contentDescription = null,
                                                tint = colors.muted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "$imgCount",
                                                fontFamily = LikkhoFontFamilies.Mono,
                                                fontSize = 11.sp,
                                                color = colors.muted
                                            )
                                        }
                                    }
                                    if (audCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.GraphicEq,
                                                contentDescription = null,
                                                tint = colors.muted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "$audCount",
                                                fontFamily = LikkhoFontFamilies.Mono,
                                                fontSize = 11.sp,
                                                color = colors.muted
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .background(colors.accent)
                                            .clickable { requestReadDiary(diary) }
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Outlined.Book,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Read",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .background(colors.surface)
                                            .border(1.dp, colors.border)
                                            .clickable(enabled = !isUpdatingBackup) { pendingDeleteDiary = diary }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete from backup",
                                            tint = colors.crimson,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Delete",
                                            fontFamily = LikkhoFontFamilies.Sans,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = colors.crimson
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== Individual Diary PIN Dialog ====================
    val lockedTarget = pendingLockedDiary
    if (lockedTarget != null) {
        LikkhoHairlineDialog(
            onDismissRequest = { pendingLockedDiary = null },
            title = "Locked Diary Entry",
            leadingIcon = Icons.Outlined.Lock,
            maxWidth = 400.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    onClick = { pendingLockedDiary = null }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = "Unlock",
                    onClick = {
                        scope.launch {
                            val rem = withContext(Dispatchers.IO) {
                                repository.getRemainingPasscodeCooldownSeconds()
                            }
                            if (rem > 0) {
                                cooldownSec = rem
                                diaryPinError = "Too many wrong attempts. Wait ${rem}s."
                                return@launch
                            }
                            if (diaryPinInput == lockedTarget.diaryLockPin) {
                                withContext(Dispatchers.IO) { repository.resetPasscodeFailures() }
                                pendingLockedDiary = null
                                viewingDiary = lockedTarget
                            } else {
                                val fail = withContext(Dispatchers.IO) { repository.recordPasscodeFailure() }
                                diaryPinInput = ""
                                diaryPinError = if (fail.cooldownSeconds > 0) {
                                    cooldownSec = fail.cooldownSeconds
                                    "Incorrect PIN. Locked for ${fail.cooldownSeconds}s."
                                } else {
                                    "Incorrect Diary PIN."
                                }
                            }
                        }
                    }
                )
            }
        ) {
            Text(
                text = "Enter the individual PIN for \"${lockedTarget.heading.ifBlank { "Untitled Diary" }}\".",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.muted
            )
            if (!diaryPinError.isNullOrBlank()) {
                Text(
                    text = diaryPinError!!,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (diaryPinInput.isEmpty()) {
                    Text(
                        text = "Enter Diary PIN",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }
                BasicTextField(
                    value = diaryPinInput,
                    onValueChange = { ch ->
                        if (ch.length <= 8 && ch.all { it.isDigit() }) {
                            diaryPinInput = ch
                            diaryPinError = null
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 14.sp,
                        color = colors.text
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // ==================== Read Backed-up Diary Dialog ====================
    val readerDiary = viewingDiary
    if (readerDiary != null) {
        BackedUpDiaryReaderDialog(
            diary = readerDiary,
            repository = repository,
            mediaEngine = mediaEngine,
            onRestoreThisDiary = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repository.upsertLog(vaultMode, readerDiary)
                    }
                    onRestoreSingleDiary(readerDiary)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewingDiary = null
                    statusBanner = "Restored \"${readerDiary.heading.ifBlank { "Untitled Diary" }}\" to app."
                }
            },
            onDismiss = { viewingDiary = null }
        )
    }

    // ==================== Delete From Backup Confirmation Dialog ====================
    val deleteTarget = pendingDeleteDiary
    if (deleteTarget != null) {
        LikkhoHairlineDialog(
            onDismissRequest = { if (!isUpdatingBackup) pendingDeleteDiary = null },
            title = "Delete from Backup?",
            leadingIcon = Icons.Outlined.WarningAmber,
            leadingIconTint = colors.crimson,
            borderColor = colors.crimson.copy(alpha = 0.7f),
            titleColor = colors.crimson,
            dismissEnabled = !isUpdatingBackup,
            maxWidth = 400.dp,
            footer = {
                LikkhoDialogOutlineButton(
                    text = "Cancel",
                    enabled = !isUpdatingBackup,
                    onClick = { pendingDeleteDiary = null }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LikkhoDialogPrimaryButton(
                    text = if (isUpdatingBackup) "Updating..." else "Delete",
                    bgColor = colors.crimson,
                    enabled = !isUpdatingBackup,
                    onClick = { handleDeleteFromBackup(deleteTarget) }
                )
            }
        ) {
            Text(
                text = "Remove \"${deleteTarget.heading.ifBlank { "Untitled Diary" }}\" permanently from ${vaultCfg.dbFilename}? This rewrites the encrypted backup archive.",
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.text
            )
            if (isUpdatingBackup) {
                LinearProgressIndicator(
                    color = colors.crimson,
                    trackColor = colors.hairline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
    }
}

@Composable
private fun BackedUpDiaryReaderDialog(
    diary: DiaryLog,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    onRestoreThisDiary: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current

    val pfpBitmap by produceState<Bitmap?>(initialValue = null, diary.id, diary.pfpDataUrl) {
        val ref = diary.pfpDataUrl
        value = if (!ref.isNullOrBlank()) {
            withContext(Dispatchers.IO) { mediaEngine.decodeBoundedBitmapForExport(ref, 192) }
        } else {
            null
        }
    }

    val imageRefs = remember(diary.id, diary.canvasBgDataUrl, diary.canvasImages) {
        buildList {
            if (!diary.canvasBgDataUrl.isNullOrBlank()) add(diary.canvasBgDataUrl)
            diary.canvasImages.forEach { if (it.dataUrl.isNotBlank()) add(it.dataUrl) }
        }
    }

    val decodedImages by produceState<List<Bitmap>>(initialValue = emptyList(), imageRefs) {
        value = withContext(Dispatchers.IO) {
            imageRefs.mapNotNull { ref ->
                mediaEngine.decodeBoundedBitmapForExport(ref, 720)
            }
        }
    }

    val readableBody = remember(diary.id, diary.contentHtml, diary.plainPreview) {
        val html = diary.contentHtml
        if (html.isNotBlank()) {
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace('\u00A0', ' ')
                .trim()
                .ifBlank { diary.plainPreview }
        } else {
            diary.plainPreview
        }
    }

    var playingAudioId by remember(diary.id) { mutableStateOf<String?>(null) }
    var activePlayer by remember(diary.id) { mutableStateOf<MediaPlayer?>(null) }
    var activeTempAudioFile by remember(diary.id) { mutableStateOf<File?>(null) }

    fun cleanupTempAudioFile() {
        val tempFile = activeTempAudioFile
        activeTempAudioFile = null
        if (tempFile != null && tempFile.name.startsWith("tmp_play_")) {
            runCatching { tempFile.delete() }
        }
    }

    fun stopPlayback() {
        runCatching {
            activePlayer?.stop()
        }
        runCatching {
            activePlayer?.release()
        }
        activePlayer = null
        playingAudioId = null
        cleanupTempAudioFile()
    }

    DisposableEffect(diary.id) {
        onDispose {
            stopPlayback()
        }
    }

    fun toggleAudio(attachment: CanvasAudioAttachment) {
        if (playingAudioId == attachment.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val audioFile = resolvePlayableAudioFile(context, repository, mediaEngine, attachment) ?: return
        if (audioFile.name.startsWith("tmp_play_")) {
            activeTempAudioFile = audioFile
        }
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    playingAudioId = null
                    runCatching { it.release() }
                    if (activePlayer === it) activePlayer = null
                    cleanupTempAudioFile()
                }
                prepare()
                start()
            }
            activePlayer = mp
            playingAudioId = attachment.id
        }.onFailure {
            cleanupTempAudioFile()
        }
    }

    LikkhoHairlineDialog(
        onDismissRequest = {
            stopPlayback()
            onDismiss()
        },
        title = diary.heading.ifBlank { "Untitled Diary" },
        subtitle = "${diary.dateStamp} · ${diary.timeStamp}",
        leadingIcon = Icons.Outlined.Book,
        maxWidth = 520.dp,
        maxHeight = 680.dp,
        bodyPadding = 16.dp,
        footer = {
            LikkhoDialogPrimaryButton(
                text = "Restore this Diary to App",
                leadingIcon = Icons.Outlined.Restore,
                modifier = Modifier.weight(1f),
                onClick = {
                    stopPlayback()
                    onRestoreThisDiary()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogOutlineButton(
                text = "Close",
                onClick = {
                    stopPlayback()
                    onDismiss()
                }
            )
        }
    ) {
        val bmp = pfpBitmap
        if (bmp != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.border, RectangleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENCRYPTED BACKUP ENTRY",
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        color = colors.accent
                    )
                    Text(
                        text = diary.heading.ifBlank { "Untitled Diary" },
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = readableBody.ifBlank { "Empty diary entry." },
            fontFamily = LikkhoFontFamilies.Serif,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = colors.text
        )

        if (decodedImages.isNotEmpty()) {
            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
            Text(
                text = "ATTACHED IMAGES (${decodedImages.size})",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = colors.muted
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                decodedImages.chunked(2).forEach { rowBmps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowBmps.forEach { imgBmp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.33f)
                                    .background(colors.surface)
                                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            ) {
                                Image(
                                    bitmap = imgBmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (rowBmps.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (diary.audioAttachments.isNotEmpty()) {
            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
            Text(
                text = "VOICE NOTES (${diary.audioAttachments.size})",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = colors.muted
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                diary.audioAttachments.forEach { aud ->
                    val isPlaying = playingAudioId == aud.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, if (isPlaying) colors.accent else colors.border, RectangleShape)
                            .clickable { toggleAudio(aud) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (isPlaying) colors.accent else colors.bg)
                                .border(LikkhoHairlineBorder, if (isPlaying) colors.accent else colors.border, RectangleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isPlaying) Color.White else colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = aud.name.ifBlank { "Voice Note" },
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val durText = aud.durationSec?.let { formatAudioDuration(it) } ?: aud.format.uppercase(Locale.US)
                            Text(
                                text = "${aud.format.uppercase(Locale.US)} · $durText",
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolvePlayableAudioFile(
    context: android.content.Context,
    repository: NativeVaultRepository,
    mediaEngine: NativeMediaEngine,
    attachment: CanvasAudioAttachment
): File? {
    repository.resolveExistingAudioFile(attachment.id, attachment.format)?.let { return it }
    attachment.audioUri?.takeIf { it.isNotBlank() }?.let { uri ->
        repository.resolveExistingAudioFile(repository.normalizeMediaId(uri), attachment.format)?.let { return it }
    }
    val rawUrl = attachment.dataUrl
    if (!rawUrl.isNullOrBlank() && rawUrl.startsWith("data:")) {
        val parsed = mediaEngine.parseDataUrl(rawUrl)
        if (parsed != null) {
            return runCatching {
                val ext = attachment.format.ifBlank { parsed.ext }
                val tmp = File(context.cacheDir, "tmp_play_${attachment.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.$ext")
                FileOutputStream(tmp).use { it.write(parsed.bytes) }
                tmp
            }.getOrNull()
        }
        val commaIdx = rawUrl.indexOf(',')
        if (commaIdx > 0) {
            runCatching {
                val bytes = Base64.decode(rawUrl.substring(commaIdx + 1), Base64.DEFAULT)
                val ext = attachment.format.ifBlank { "wav" }
                val tmp = File(context.cacheDir, "tmp_play_${attachment.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.$ext")
                FileOutputStream(tmp).use { it.write(bytes) }
                return tmp
            }
        }
    }
    return null
}

private fun formatAudioDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

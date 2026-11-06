package com.likkho.nativeapp.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.nativeapp.data.model.MicRecordingSettings
import com.likkho.nativeapp.data.security.NativeStreamVaultEngine
import com.likkho.nativeapp.data.security.SecurityStorageManager
import com.likkho.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    securityManager: SecurityStorageManager,
    vaultEngine: NativeStreamVaultEngine,
    onBack: () -> Unit,
    onSelectSafDirectory: () -> Unit,
    onOpenBackedUpDiaries: () -> Unit,
    onClearAllInAppDiaries: () -> Unit,
    onBackupRequested: () -> Unit,
    onRestoreRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDarkMode by remember { mutableStateOf(securityManager.isDarkMode()) }
    var isBiometricEnabled by remember { mutableStateOf(securityManager.isBiometricsEnabled()) }
    var isPasscodeSet by remember { mutableStateOf(securityManager.getPrimaryPasscode() != null) }
    var isDecoyPasscodeSet by remember { mutableStateOf(securityManager.getSecondaryPasscode() != null) }

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showRecordingFormatDialog by remember { mutableStateOf(false) }

    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var decoyPinInput by remember { mutableStateOf("") }
    var confirmDecoyPinInput by remember { mutableStateOf("") }
    var decoyPinError by remember { mutableStateOf<String?>(null) }

    var micSettings by remember { mutableStateOf(securityManager.getMicSettings()) }

    val safDirectoryUri = securityManager.getSafTreeUri("primary")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Section 1: APPEARANCE
            item {
                SettingsSectionHeader("APPEARANCE")
                SettingsCard {
                    SettingsRow(
                        title = "Dark Mode",
                        subtitle = if (isDarkMode) "Wikipedia Dark Palette" else "Wikipedia Clean Light",
                        icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        trailing = {
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { checked ->
                                    isDarkMode = checked
                                    securityManager.setDarkMode(checked)
                                }
                            )
                        }
                    )
                }
            }

            // Section 2: SECURITY & PASSCODE
            item {
                SettingsSectionHeader("SECURITY & PASSCODE")
                SettingsCard {
                    var isPressing by remember { mutableStateOf(false) }
                    var holdSeconds by remember { mutableStateOf(0) }

                    LaunchedEffect(isPressing) {
                        if (isPressing) {
                            holdSeconds = 0
                            while (isPressing) {
                                delay(1000)
                                holdSeconds++
                                if (holdSeconds >= 10) {
                                    isPressing = false
                                    showDecoyPinDialog = true
                                    Toast.makeText(
                                        context,
                                        "Covert Secondary PIN Vault Configuration Activated",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    break
                                }
                            }
                        } else {
                            holdSeconds = 0
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressing = true
                                        tryAwaitRelease()
                                        isPressing = false
                                    },
                                    onTap = {
                                        newPinInput = ""
                                        confirmPinInput = ""
                                        pinError = null
                                        showSetPinDialog = true
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = WikipediaBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPasscodeSet) "Change Passcode" else "Set Passcode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPressing && holdSeconds > 2) {
                                        "Hold for covert vault... ${10 - holdSeconds}s"
                                    } else if (isPasscodeSet) {
                                        "Passcode protection is active"
                                    } else {
                                        "Lock app with a 4-6 digit numeric PIN"
                                    },
                                    fontSize = 12.sp,
                                    color = if (isPressing) WikipediaBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isPasscodeSet) {
                                TextButton(
                                    onClick = {
                                        securityManager.setPrimaryPasscode(null)
                                        isPasscodeSet = false
                                        isDecoyPasscodeSet = false
                                        Toast.makeText(context, "Passcode removed", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Remove", color = WikipediaRed, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Biometric Unlock
                    SettingsRow(
                        title = "Biometric Unlock",
                        subtitle = "Unlock quickly using Fingerprint or Face ID",
                        icon = Icons.Default.Fingerprint,
                        trailing = {
                            Switch(
                                checked = isBiometricEnabled,
                                enabled = isPasscodeSet,
                                onCheckedChange = { checked ->
                                    isBiometricEnabled = checked
                                    securityManager.setBiometricsEnabled(checked)
                                }
                            )
                        }
                    )

                    if (isDecoyPasscodeSet) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsRow(
                            title = "Secondary Vault (Covert)",
                            subtitle = "Separate isolated vault is configured",
                            icon = Icons.Default.Security,
                            trailing = {
                                TextButton(
                                    onClick = {
                                        securityManager.setSecondaryPasscode(null)
                                        isDecoyPasscodeSet = false
                                        Toast.makeText(context, "Secondary PIN removed", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Clear", color = WikipediaRed, fontSize = 13.sp)
                                }
                            }
                        )
                    }
                }
            }

            // Section 3: STORAGE ACCESS FRAMEWORK (SAF) & BACKUPS
            item {
                SettingsSectionHeader("SAF STORAGE & VAULT BACKUP")
                SettingsCard {
                    SettingsRow(
                        title = "Storage Access Folder",
                        subtitle = if (safDirectoryUri != null) "Folder active (.likkho_backups)" else "Select safe storage location",
                        icon = Icons.Default.FolderOpen,
                        onClick = onSelectSafDirectory,
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsRow(
                        title = "Save Streaming Backup",
                        subtitle = "Direct-IO atomic backup to real_vault.db",
                        icon = Icons.Default.CloudUpload,
                        onClick = onBackupRequested,
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = WikipediaBlue
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsRow(
                        title = "Restore from Backup File",
                        subtitle = "Direct-IO low RAM stream restore",
                        icon = Icons.Default.CloudDownload,
                        onClick = onRestoreRequested,
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsRow(
                        title = "Backed-up Diaries Vault",
                        subtitle = "Inspect, read, or delete diaries in backup file",
                        icon = Icons.Default.AutoStories,
                        onClick = onOpenBackedUpDiaries,
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = WikipediaBlue
                            )
                        }
                    )
                }
            }

            // Section 4: AUDIO RECORDING QUALITY
            item {
                SettingsSectionHeader("VOICE RECORDING")
                SettingsCard {
                    SettingsRow(
                        title = "Audio Sample Rate",
                        subtitle = when (micSettings.sampleRate) {
                            44100 -> "44.1 kHz (CD High Quality, Recommended)"
                            22050 -> "22.05 kHz (Compact Voice Recording)"
                            16000 -> "16 kHz (Ultra-Low Storage Size)"
                            else -> "${micSettings.sampleRate} Hz"
                        },
                        icon = Icons.Default.Mic,
                        onClick = { showRecordingFormatDialog = true },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Section 5: DANGER ZONE
            item {
                SettingsSectionHeader("DANGER ZONE")
                SettingsCard {
                    SettingsRow(
                        title = "Delete All In-App Diaries",
                        subtitle = "Clears app storage only; backups remain safe",
                        icon = Icons.Default.DeleteForever,
                        iconTint = WikipediaRed,
                        onClick = { showClearDataDialog = true },
                        trailing = {
                            Text(
                                text = "Clear",
                                color = WikipediaRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Set PIN Dialog
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = {
                Text(
                    text = if (isPasscodeSet) "Change Primary Passcode" else "Set Primary Passcode",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a 4 to 6 digit numeric PIN to protect your private journal.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("New PIN (4-6 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(text = pinError!!, color = WikipediaRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length < 4) {
                            pinError = "PIN must be at least 4 digits."
                            return@Button
                        }
                        if (newPinInput != confirmPinInput) {
                            pinError = "PINs do not match."
                            return@Button
                        }
                        securityManager.setPrimaryPasscode(newPinInput)
                        isPasscodeSet = true
                        showSetPinDialog = false
                        Toast.makeText(context, "Passcode saved successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WikipediaBlue)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Secret Decoy PIN Dialog
    if (showDecoyPinDialog) {
        AlertDialog(
            onDismissRequest = { showDecoyPinDialog = false },
            title = {
                Text(
                    text = "Configure Covert Secondary PIN",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = WikipediaRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Under duress, entering this covert PIN will unlock a completely separate decoy vault without revealing your real diaries.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = decoyPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) decoyPinInput = it },
                        label = { Text("Covert PIN (4-6 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmDecoyPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmDecoyPinInput = it },
                        label = { Text("Confirm Covert PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (decoyPinError != null) {
                        Text(text = decoyPinError!!, color = WikipediaRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (decoyPinInput.length < 4) {
                            decoyPinError = "PIN must be at least 4 digits."
                            return@Button
                        }
                        if (decoyPinInput != confirmDecoyPinInput) {
                            decoyPinError = "PINs do not match."
                            return@Button
                        }
                        securityManager.setSecondaryPasscode(decoyPinInput)
                        isDecoyPasscodeSet = true
                        showDecoyPinDialog = false
                        Toast.makeText(context, "Covert vault PIN activated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WikipediaRed)
                ) {
                    Text("Save Covert PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDecoyPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Audio Sample Rate Selection Dialog
    if (showRecordingFormatDialog) {
        AlertDialog(
            onDismissRequest = { showRecordingFormatDialog = false },
            title = {
                Text(
                    text = "Select Audio Sample Rate",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        44100 to "44.1 kHz (CD High Quality)",
                        22050 to "22.05 kHz (Compact Voice Recording)",
                        16000 to "16 kHz (Ultra-Low Storage Size)"
                    ).forEach { (rate, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = micSettings.copy(sampleRate = rate)
                                    micSettings = updated
                                    securityManager.setMicSettings(updated)
                                    showRecordingFormatDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (micSettings.sampleRate == rate),
                                onClick = {
                                    val updated = micSettings.copy(sampleRate = rate)
                                    micSettings = updated
                                    securityManager.setMicSettings(updated)
                                    showRecordingFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecordingFormatDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = {
                Text(
                    text = "Delete All In-App Diaries?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = WikipediaRed
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all in-app entries and audio recordings from this device's internal storage.\n\nYour SAF backup file (real_vault.db) in your chosen folder will NOT be touched and can be restored at any time.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        onClearAllInAppDiaries()
                        Toast.makeText(context, "In-app storage cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WikipediaRed)
                ) {
                    Text("Delete Everything In App")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

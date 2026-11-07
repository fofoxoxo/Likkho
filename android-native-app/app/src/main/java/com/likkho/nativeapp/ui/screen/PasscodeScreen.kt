package com.likkho.nativeapp.ui.screen

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.nativeapp.data.security.SecurityStorageManager
import com.likkho.nativeapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PasscodeScreen(
    securityManager: SecurityStorageManager,
    onUnlocked: (vaultMode: String) -> Unit,
    onTriggerBiometrics: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    var inputPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cooldownSec by remember { mutableStateOf(securityManager.getRemainingCooldownSeconds()) }

    // Shake animation state on error
    var shakeTrigger by remember { mutableStateOf(false) }
    val shakeOffset by animateFloatAsState(
        targetValue = if (shakeTrigger) 0f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "shake"
    )

    // Cooldown countdown timer
    LaunchedEffect(cooldownSec) {
        if (cooldownSec > 0) {
            delay(1000L)
            cooldownSec = securityManager.getRemainingCooldownSeconds()
        }
    }

    val primaryPin = remember { securityManager.getPrimaryPasscode() }
    val secondaryPin = remember { securityManager.getSecondaryPasscode() }
    val biometricsEnabled = remember { securityManager.isBiometricsEnabled() }

    fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (ignored: Exception) {}
    }

    fun vibrateError() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (ignored: Exception) {}
    }

    fun handleDigitPress(digit: String) {
        if (cooldownSec > 0) return
        if (inputPin.length >= 6) return

        vibrateShort()
        val next = inputPin + digit
        inputPin = next
        errorMessage = null

        // If length >= 4, check matches
        if (next.length >= 4) {
            if (next == primaryPin) {
                securityManager.resetPasscodeFailures()
                onUnlocked("primary")
            } else if (secondaryPin != null && next == secondaryPin) {
                securityManager.resetPasscodeFailures()
                onUnlocked("decoy")
            } else if (next.length == 6 || (primaryPin?.length == 4 && next.length == 4)) {
                // Incorrect PIN
                vibrateError()
                val rem = securityManager.recordPasscodeFailure()
                cooldownSec = rem
                errorMessage = if (rem > 0) "Locked for ${rem}s." else "Incorrect Passcode."
                inputPin = ""
                shakeTrigger = !shakeTrigger
            }
        }
    }

    fun handleBackspace() {
        if (inputPin.isNotEmpty()) {
            vibrateShort()
            inputPin = inputPin.dropLast(1)
            errorMessage = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header Icon & Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = WikiBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Likkho",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = if (cooldownSec > 0) "Too many attempts. Wait ${cooldownSec}s." else "Enter Passcode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cooldownSec > 0) WikiRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (errorMessage != null && cooldownSec == 0) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WikiRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Passcode Dots Indicator (up to 6 dots)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val maxDots = Math.max(4, Math.max(primaryPin?.length ?: 4, secondaryPin?.length ?: 4))
                    for (i in 0 until maxDots) {
                        val isFilled = i < inputPin.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) WikiBlue else Color.Transparent)
                                .border(1.5.dp, if (isFilled) WikiBlue else MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
            }

            // Numeric Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in digits) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (d in row) {
                            KeypadButton(text = d, onClick = { handleDigitPress(d) })
                        }
                    }
                }

                // Bottom row: Biometrics | 0 | Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (biometricsEnabled) {
                        IconButton(
                            onClick = onTriggerBiometrics,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Unlock",
                                tint = WikiBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(68.dp))
                    }

                    KeypadButton(text = "0", onClick = { handleDigitPress("0") })

                    IconButton(
                        onClick = { handleBackspace() },
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 26.sp,
            fontFamily = WikiSerif,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

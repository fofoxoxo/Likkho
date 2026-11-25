package com.likkho.diary.nativeapp.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native Compose Lockscreen implementing:
 * - 4–8 digit PIN keypad with dynamic dot indicator
 * - Dual-vault routing (Primary vs Covert Decoy Vault via salted PBKDF2 evaluation)
 * - Progressive brute-force cooldown timer synced with [NativeVaultRepository]
 * - One-shot [BiometricPrompt] integration that cancels cleanly if user begins typing PIN
 */
@Composable
fun PasscodeLockScreen(
    repository: NativeVaultRepository,
    biometricsEnabled: Boolean = repository.isBiometricEnabled(),
    onUnlocked: (VaultMode) -> Unit,
    onRequestBiometric: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bioMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var cooldownSec by remember {
        mutableIntStateOf(repository.getRemainingPasscodeCooldownSeconds())
    }
    var hasAutoPromptedBio by remember { mutableStateOf(false) }
    var activePrompt by remember { mutableStateOf<BiometricPrompt?>(null) }
    var pendingVerifyJob by remember { mutableStateOf<Job?>(null) }

    val isCoolingDown = cooldownSec > 0

    // Clear focus when lock screen appears
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
    }

    // Live progressive cooldown countdown
    LaunchedEffect(isCoolingDown) {
        while (true) {
            val remaining = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            cooldownSec = remaining
            if (remaining <= 0) break
            delay(500L)
        }
    }

    fun cancelBiometricPrompt() {
        try {
            activePrompt?.cancelAuthentication()
        } catch (_: Exception) {
        }
        activePrompt = null
    }

    fun launchBiometric() {
        if (cooldownSec > 0) return
        bioMessage = null
        if (onRequestBiometric != null) {
            onRequestBiometric.invoke()
            return
        }
        val activity = context.findFragmentActivity()
        if (activity == null) {
            bioMessage = "Use your PIN to unlock."
            return
        }
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            bioMessage = "Biometrics unavailable. Enter your PIN."
            return
        }
        cancelBiometricPrompt()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    activePrompt = null
                    scope.launch {
                        val rem = withContext(Dispatchers.IO) {
                            repository.getRemainingPasscodeCooldownSeconds()
                        }
                        if (rem > 0) {
                            cooldownSec = rem
                            return@launch
                        }
                        withContext(Dispatchers.IO) {
                            repository.resetPasscodeFailures()
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUnlocked(VaultMode.PRIMARY)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    activePrompt = null
                    if (
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        bioMessage = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    bioMessage = "Biometric not recognized. Try again or enter PIN."
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Likkho")
            .setSubtitle("Verify your identity to access your vault")
            .setNegativeButtonText("Use Passcode")
            .setAllowedAuthenticators(authenticators)
            .build()
        activePrompt = prompt
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            activePrompt = null
            bioMessage = "Use your PIN to unlock."
        }
    }

    // Prompt biometrics ONCE when lockscreen opens (never interrupts typing)
    LaunchedEffect(biometricsEnabled) {
        if (biometricsEnabled && !hasAutoPromptedBio) {
            hasAutoPromptedBio = true
            delay(220L)
            val rem = withContext(Dispatchers.IO) {
                repository.getRemainingPasscodeCooldownSeconds()
            }
            cooldownSec = rem
            if (rem == 0 && enteredPin.isEmpty()) {
                launchBiometric()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingVerifyJob?.cancel()
            cancelBiometricPrompt()
        }
    }

    fun evaluateCandidatePin(pin: String, rejectOnMismatch: Boolean) {
        pendingVerifyJob?.cancel()
        pendingVerifyJob = scope.launch {
            isChecking = true
            val matchedMode = withContext(Dispatchers.Default) {
                repository.evaluatePasscode(pin)
            }
            if (matchedMode != null) {
                withContext(Dispatchers.IO) {
                    repository.resetPasscodeFailures()
                }
                cooldownSec = 0
                isChecking = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onUnlocked(matchedMode)
                return@launch
            }
            if (rejectOnMismatch) {
                val failure = withContext(Dispatchers.IO) {
                    repository.recordPasscodeFailure()
                }
                isError = true
                errorMessage = if (failure.cooldownSeconds > 0) {
                    cooldownSec = failure.cooldownSeconds
                    "Locked for ${failure.cooldownSeconds} seconds due to wrong attempts"
                } else {
                    "Incorrect passcode"
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isChecking = false
                delay(320L)
                enteredPin = ""
            } else {
                isChecking = false
            }
        }
    }

    fun onDigitPressed(digit: String) {
        if (isCoolingDown || enteredPin.length >= MAX_PIN_LENGTH) return
        cancelBiometricPrompt()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isError = false
        errorMessage = null
        bioMessage = null
        val next = (enteredPin + digit).take(MAX_PIN_LENGTH)
        enteredPin = next

        if (next.length >= MIN_PIN_LENGTH) {
            // Auto-unlocks immediately on any matching 4..8 digit Primary or Decoy PIN;
            // only counts a failed attempt if user explicitly taps Unlock or hits max length (8).
            evaluateCandidatePin(
                pin = next,
                rejectOnMismatch = next.length >= MAX_PIN_LENGTH
            )
        }
    }

    fun onBackspacePressed() {
        if (isCoolingDown || enteredPin.isEmpty()) return
        cancelBiometricPrompt()
        pendingVerifyJob?.cancel()
        isChecking = false
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isError = false
        errorMessage = null
        enteredPin = enteredPin.dropLast(1)
    }

    fun onClearPressed() {
        if (isCoolingDown || enteredPin.isEmpty()) return
        cancelBiometricPrompt()
        pendingVerifyJob?.cancel()
        isChecking = false
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isError = false
        errorMessage = null
        enteredPin = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Identity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.surface)
                    .border(1.dp, colors.border),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Likkho Locked",
                    tint = colors.accent,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Likkho Locked",
                fontFamily = LikkhoFontFamilies.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.text,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isCoolingDown -> "Too many failed attempts. Try again in ${cooldownSec}s"
                    biometricsEnabled -> "Unlock with Biometrics or enter your passcode"
                    else -> "Enter your passcode to unlock"
                },
                fontFamily = LikkhoFontFamilies.Sans,
                fontSize = 12.sp,
                color = colors.muted,
                textAlign = TextAlign.Center
            )
        }

        // Passcode Dots + Status + Optional Submit Button for 4-7 digit wrong/verify check
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            val dotCount = maxOf(MIN_PIN_LENGTH, enteredPin.length)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until dotCount) {
                    val filled = i < enteredPin.length
                    val dotBg by animateColorAsState(
                        targetValue = when {
                            isError || isCoolingDown -> colors.crimson
                            filled -> colors.accent
                            else -> Color.Transparent
                        },
                        label = "dotBg"
                    )
                    val dotBorder = when {
                        isError || isCoolingDown -> colors.crimson
                        filled -> colors.accent
                        else -> colors.border
                    }
                    Box(
                        modifier = Modifier
                            .size(if (filled && !isError && !isCoolingDown) 16.dp else 15.dp)
                            .clip(CircleShape)
                            .background(dotBg)
                            .border(1.5.dp, dotBorder, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isCoolingDown -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.crimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Locked for $cooldownSec seconds due to wrong attempts",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.crimson,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                isError && errorMessage != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.crimson,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = errorMessage ?: "Incorrect passcode",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.crimson
                        )
                    }
                }
                !bioMessage.isNullOrBlank() -> {
                    Text(
                        text = bioMessage ?: "",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        color = colors.muted,
                        textAlign = TextAlign.Center
                    )
                }
                enteredPin.length in MIN_PIN_LENGTH until MAX_PIN_LENGTH -> {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .background(colors.accent)
                            .border(1.dp, colors.accentHover)
                            .clickable(enabled = !isChecking && !isCoolingDown) {
                                evaluateCandidatePin(enteredPin, rejectOnMismatch = true)
                            }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isChecking) "Verifying..." else "Unlock",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(34.dp))
                }
            }
        }

        // Numeric Keypad (3 columns x 4 rows)
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            rows.forEach { rowDigits ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowDigits.forEach { digit ->
                        KeypadDigitButton(
                            label = digit,
                            enabled = !isCoolingDown,
                            onClick = { onDigitPressed(digit) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom Row: Biometric / Clear | 0 | Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    biometricsEnabled -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .alpha(if (isCoolingDown) 0.4f else 1f)
                                .background(colors.accent.copy(alpha = 0.10f))
                                .border(1.dp, colors.accent)
                                .clickable(enabled = !isCoolingDown) { launchBiometric() }
                                .semantics { contentDescription = "Unlock with Biometrics" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = "Unlock with Biometrics",
                                tint = colors.accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    enteredPin.isNotEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .alpha(if (isCoolingDown) 0.4f else 1f)
                                .background(colors.surface)
                                .border(1.dp, colors.border)
                                .clickable(enabled = !isCoolingDown) { onClearPressed() }
                                .semantics { contentDescription = "Clear passcode" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = colors.muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        Spacer(modifier = Modifier.weight(1f).height(56.dp))
                    }
                }

                KeypadDigitButton(
                    label = "0",
                    enabled = !isCoolingDown,
                    onClick = { onDigitPressed("0") },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .alpha(if (isCoolingDown) 0.4f else 1f)
                        .background(colors.surface)
                        .border(1.dp, colors.border)
                        .clickable(enabled = !isCoolingDown) { onBackspacePressed() }
                        .semantics { contentDescription = "Backspace" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Backspace",
                        tint = colors.muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(colors.surface)
            .border(1.dp, colors.border)
            .clickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            color = colors.text
        )
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8

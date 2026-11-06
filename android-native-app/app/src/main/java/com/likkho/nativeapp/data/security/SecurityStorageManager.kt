package com.likkho.nativeapp.data.security

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.likkho.nativeapp.data.model.MicRecordingSettings
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecurityStorageManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "likkho_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Primary & Decoy Passcode
    fun getPrimaryPasscode(): String? = securePrefs.getString("primary_passcode", null)
    fun setPrimaryPasscode(pin: String?) {
        securePrefs.edit().apply {
            if (pin != null) putString("primary_passcode", pin)
            else {
                remove("primary_passcode")
                remove("secondary_passcode") // Removing primary resets secondary
            }
        }.apply()
    }

    fun getSecondaryPasscode(): String? = securePrefs.getString("secondary_passcode", null)
    fun setSecondaryPasscode(pin: String?) {
        securePrefs.edit().apply {
            if (pin != null) putString("secondary_passcode", pin)
            else remove("secondary_passcode")
        }.apply()
    }

    // Biometrics
    fun isBiometricsEnabled(): Boolean = securePrefs.getBoolean("biometrics_enabled", false)
    fun setBiometricsEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean("biometrics_enabled", enabled).apply()
    }

    // Dark Mode & Fullscreen
    fun isDarkMode(): Boolean = securePrefs.getBoolean("dark_theme", false)
    fun setDarkMode(dark: Boolean) {
        securePrefs.edit().putBoolean("dark_theme", dark).apply()
    }

    // SAF Persisted Folder Tree URI for Primary & Decoy
    fun getSafTreeUri(vaultMode: String): Uri? {
        val key = if (vaultMode == "decoy") "saf_tree_uri_decoy" else "saf_tree_uri_primary"
        val raw = securePrefs.getString(key, null) ?: return null
        return try { Uri.parse(raw) } catch (e: Exception) { null }
    }

    fun setSafTreeUri(vaultMode: String, uri: Uri?) {
        val key = if (vaultMode == "decoy") "saf_tree_uri_decoy" else "saf_tree_uri_primary"
        securePrefs.edit().putString(key, uri?.toString()).apply()
    }

    // Encryption Key Hash for verification
    fun getKeyHash(vaultMode: String): String? {
        val key = if (vaultMode == "decoy") "enc_hash_decoy" else "enc_hash_primary"
        return securePrefs.getString(key, null)
    }

    fun setKeyHash(vaultMode: String, hash: String?) {
        val key = if (vaultMode == "decoy") "enc_hash_decoy" else "enc_hash_primary"
        securePrefs.edit().putString(key, hash).apply()
    }

    // Mic Settings
    fun getMicSettings(): MicRecordingSettings {
        val fmt = securePrefs.getString("mic_format", "wav") ?: "wav"
        val rate = securePrefs.getInt("mic_sample_rate", 44100)
        val bit = securePrefs.getInt("mic_bit_rate", 128000)
        return MicRecordingSettings(fmt, rate, bit)
    }

    fun setMicSettings(settings: MicRecordingSettings) {
        securePrefs.edit()
            .putString("mic_format", settings.format)
            .putInt("mic_sample_rate", settings.sampleRate)
            .putInt("mic_bit_rate", settings.bitRate)
            .apply()
    }

    // Cooldown logic for passcode attempts
    fun getRemainingCooldownSeconds(): Int {
        val lockedUntilMs = securePrefs.getLong("passcode_locked_until", 0L)
        val now = System.currentTimeMillis()
        return if (lockedUntilMs > now) ((lockedUntilMs - now) / 1000L).toInt() else 0
    }

    fun recordPasscodeFailure(): Int {
        var fails = securePrefs.getInt("passcode_fail_count", 0) + 1
        securePrefs.edit().putInt("passcode_fail_count", fails).apply()

        val cooldownSec = when {
            fails >= 10 -> 300 // 5 minutes
            fails >= 7 -> 60   // 1 minute
            fails >= 5 -> 30   // 30 seconds
            fails >= 3 -> 10   // 10 seconds
            else -> 0
        }

        if (cooldownSec > 0) {
            val lockedUntil = System.currentTimeMillis() + (cooldownSec * 1000L)
            securePrefs.edit().putLong("passcode_locked_until", lockedUntil).apply()
        }
        return cooldownSec
    }

    fun resetPasscodeFailures() {
        securePrefs.edit()
            .putInt("passcode_fail_count", 0)
            .putLong("passcode_locked_until", 0L)
            .apply()
    }

    // Hash passphrase with SHA-256 for Primary or SHA-512 for Decoy
    fun hashPassphrase(passphrase: String, vaultMode: String): String {
        val algorithm = if (vaultMode == "decoy") "SHA-512" else "SHA-256"
        val digest = MessageDigest.getInstance(algorithm)
        val hashBytes = digest.digest(passphrase.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

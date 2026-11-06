package com.likkho.nativeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.likkho.nativeapp.data.local.LikkhoDatabase
import com.likkho.nativeapp.data.model.DiaryLog
import com.likkho.nativeapp.data.model.VaultBackupBundle
import com.likkho.nativeapp.data.security.NativeStreamVaultEngine
import com.likkho.nativeapp.data.security.SecurityStorageManager
import com.likkho.nativeapp.ui.screen.*
import com.likkho.nativeapp.ui.theme.LikkhoTheme
import kotlinx.coroutines.launch

sealed class Screen {
    object Passcode : Screen()
    object DiaryList : Screen()
    data class WritingWorkspace(val diaryId: String? = null) : Screen()
    object Settings : Screen()
    object BackedUpDiaries : Screen()
}

class MainActivity : FragmentActivity() {

    private lateinit var securityManager: SecurityStorageManager
    private lateinit var database: LikkhoDatabase
    private lateinit var vaultEngine: NativeStreamVaultEngine

    private var isDecoySession = mutableStateOf(false)
    private var currentScreen = mutableStateOf<Screen>(Screen.DiaryList)
    private var isAppLocked = mutableStateOf(true)

    private var pendingSafIsDecoy = false
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            securityManager.saveSafTreeUri(uri, pendingSafIsDecoy)
            Toast.makeText(this, "SAF Backup folder saved!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        securityManager = SecurityStorageManager(this)
        database = LikkhoDatabase.getDatabase(this)
        vaultEngine = NativeStreamVaultEngine(this)

        if (securityManager.isPasscodeSet()) {
            isAppLocked.value = true
            currentScreen.value = Screen.Passcode
            applyFlagSecure(true)
            if (securityManager.isBiometricEnabled()) {
                showBiometricPrompt()
            }
        } else {
            isAppLocked.value = false
            currentScreen.value = Screen.DiaryList
            applyFlagSecure(false)
        }

        setContent {
            val isDarkPref = securityManager.isDarkMode()
            val useDarkTheme = isDarkPref

            LikkhoTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val screen = currentScreen.value) {
                        is Screen.Passcode -> {
                            PasscodeScreen(
                                securityManager = securityManager,
                                onUnlockSuccess = { isDecoy ->
                                    isDecoySession.value = isDecoy
                                    isAppLocked.value = false
                                    applyFlagSecure(true)
                                    currentScreen.value = Screen.DiaryList
                                }
                            )
                        }

                        is Screen.DiaryList -> {
                            val diaryDao = database.diaryDao()
                            val vaultMode = if (isDecoySession.value) "decoy" else "primary"
                            val diariesFlow = diaryDao.getAllLogs(vaultMode)
                            val diaries by diariesFlow.collectAsState(initial = emptyList())

                            DiaryListScreen(
                                diaries = diaries,
                                isDecoy = isDecoySession.value,
                                onOpenDiary = { id ->
                                    currentScreen.value = Screen.WritingWorkspace(id)
                                },
                                onCreateNewDiary = {
                                    currentScreen.value = Screen.WritingWorkspace(null)
                                },
                                onOpenSettings = {
                                    currentScreen.value = Screen.Settings
                                },
                                onTogglePin = { diary ->
                                    lifecycleScope.launch {
                                        diaryDao.togglePin(diary.id)
                                    }
                                },
                                onDeleteDiary = { diary ->
                                    lifecycleScope.launch {
                                        diaryDao.deleteLogById(diary.id)
                                    }
                                }
                            )
                        }

                        is Screen.WritingWorkspace -> {
                            val diaryDao = database.diaryDao()
                            val diaryId = screen.diaryId
                            var activeDiary by remember { mutableStateOf<DiaryLog?>(null) }

                            LaunchedEffect(diaryId) {
                                if (diaryId != null) {
                                    val log = diaryDao.getLogById(diaryId)
                                    activeDiary = log
                                }
                            }

                            WritingWorkspaceScreen(
                                initialDiary = activeDiary,
                                isDecoy = isDecoySession.value,
                                onBack = {
                                    currentScreen.value = Screen.DiaryList
                                },
                                onSaveDiary = { log ->
                                    lifecycleScope.launch {
                                        diaryDao.upsertLog(log)
                                        currentScreen.value = Screen.DiaryList
                                    }
                                },
                                onDeleteDiary = { log ->
                                    lifecycleScope.launch {
                                        diaryDao.deleteLogById(log.id)
                                        currentScreen.value = Screen.DiaryList
                                    }
                                }
                            )
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                securityManager = securityManager,
                                vaultEngine = vaultEngine,
                                onBack = {
                                    currentScreen.value = Screen.DiaryList
                                },
                                onSelectSafDirectory = {
                                    pendingSafIsDecoy = isDecoySession.value
                                    openDocumentTreeLauncher.launch(null)
                                },
                                onOpenBackedUpDiaries = {
                                    currentScreen.value = Screen.BackedUpDiaries
                                },
                                onClearAllInAppDiaries = {
                                    lifecycleScope.launch {
                                        val dao = database.diaryDao()
                                        val mode = if (isDecoySession.value) "decoy" else "primary"
                                        dao.clearVaultLogs(mode)
                                    }
                                },
                                onBackupRequested = {
                                    triggerFullStreamingBackup()
                                },
                                onRestoreRequested = {
                                    triggerFullStreamingRestore()
                                }
                            )
                        }

                        is Screen.BackedUpDiaries -> {
                            BackedUpDiariesScreen(
                                securityManager = securityManager,
                                vaultEngine = vaultEngine,
                                isDecoy = isDecoySession.value,
                                onBack = {
                                    currentScreen.value = Screen.Settings
                                },
                                onRestoreSingleDiaryToApp = { diary ->
                                    lifecycleScope.launch {
                                        database.diaryDao().upsertLog(diary)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyFlagSecure(enable: Boolean) {
        if (enable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isDecoySession.value = false
                    isAppLocked.value = false
                    currentScreen.value = Screen.DiaryList
                    Toast.makeText(this@MainActivity, "Unlocked with Biometrics", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Likkho")
            .setSubtitle("Use Fingerprint or Face ID to open your journal")
            .setNegativeButtonText("Use Passcode")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun triggerFullStreamingBackup() {
        val uri = securityManager.getSafTreeUri(isDecoySession.value)
        if (uri == null) {
            Toast.makeText(this, "Please select a backup storage folder first", Toast.LENGTH_LONG).show()
            pendingSafIsDecoy = isDecoySession.value
            openDocumentTreeLauncher.launch(null)
            return
        }

        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Starting low-RAM streaming backup...", Toast.LENGTH_SHORT).show()
            val dao = database.diaryDao()
            val mode = if (isDecoySession.value) "decoy" else "primary"
            val diaries = dao.getAllLogsList(mode)

            val bundle = VaultBackupBundle(
                version = "1.3.0",
                exportedAt = System.currentTimeMillis(),
                vaultMode = mode,
                totalEntries = diaries.size,
                diaries = diaries
            )

            val passHash = securityManager.getPasscodeHash(isDecoySession.value)
            val pass = passHash ?: "likkho_default_safe_key"

            val result = vaultEngine.performStreamingBackup(
                treeUri = uri,
                bundle = bundle,
                isDecoy = isDecoySession.value,
                password = pass
            ) { _ -> }

            result.onSuccess {
                Toast.makeText(this@MainActivity, "Backup successfully saved to SAF vault!", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Backup failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun triggerFullStreamingRestore() {
        val uri = securityManager.getSafTreeUri(isDecoySession.value)
        if (uri == null) {
            Toast.makeText(this, "Please select the backup folder first", Toast.LENGTH_LONG).show()
            pendingSafIsDecoy = isDecoySession.value
            openDocumentTreeLauncher.launch(null)
            return
        }

        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Streaming and restoring from vault...", Toast.LENGTH_SHORT).show()
            val passHash = securityManager.getPasscodeHash(isDecoySession.value)
            val pass = passHash ?: "likkho_default_safe_key"

            val result = vaultEngine.performStreamingRestore(
                treeUri = uri,
                isDecoy = isDecoySession.value,
                password = pass
            ) { _ -> }

            result.onSuccess { bundle ->
                val dao = database.diaryDao()
                dao.upsertLogs(bundle.diaries)
                Toast.makeText(
                    this@MainActivity,
                    "Restored ${bundle.diaries.size} entries into Likkho!",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentScreen.value) {
            is Screen.WritingWorkspace, is Screen.Settings -> {
                currentScreen.value = Screen.DiaryList
            }
            is Screen.BackedUpDiaries -> {
                currentScreen.value = Screen.Settings
            }
            is Screen.DiaryList -> {
                super.onBackPressed()
            }
            is Screen.Passcode -> {
                finish()
            }
        }
    }
}

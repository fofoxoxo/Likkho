package com.likkho.nativeapp.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.likkho.nativeapp.data.model.DiaryLog
import com.likkho.nativeapp.data.model.VaultBackupBundle
import com.likkho.nativeapp.data.security.NativeStreamVaultEngine
import com.likkho.nativeapp.data.security.SecurityStorageManager
import com.likkho.nativeapp.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackedUpDiariesScreen(
    securityManager: SecurityStorageManager,
    vaultEngine: NativeStreamVaultEngine,
    isDecoy: Boolean,
    onBack: () -> Unit,
    onRestoreSingleDiaryToApp: (DiaryLog) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadedBundle by remember { mutableStateOf<VaultBackupBundle?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDiaryForReading by remember { mutableStateOf<DiaryLog?>(null) }
    var diaryToDeleteFromVault by remember { mutableStateOf<DiaryLog?>(null) }

    val safUri = securityManager.getSafTreeUri(isDecoy)

    fun loadVault() {
        if (safUri == null) {
            isLoading = false
            errorMessage = "No SAF backup storage folder configured. Please select one in Settings."
            return
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            val passHash = securityManager.getPasscodeHash(isDecoy)
            val pass = passHash ?: "likkho_default_safe_key"
            val result = vaultEngine.performStreamingRestore(safUri, isDecoy, pass) { _ -> }

            isLoading = false
            result.onSuccess { bundle ->
                loadedBundle = bundle
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to read backup vault."
            }
        }
    }

    LaunchedEffect(safUri) {
        loadVault()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Backed-up Diaries",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDecoy) "Decoy Vault (decoy_vault.db)" else "Primary Vault (real_vault.db)",
                            fontSize = 11.sp,
                            color = if (isDecoy) WikipediaRed else WikipediaBlue
                        )
                    }
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
                actions = {
                    IconButton(onClick = { loadVault() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = WikipediaBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Direct-IO reading backup vault...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WikipediaRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadVault() },
                            colors = ButtonDefaults.buttonColors(containerColor = WikipediaBlue)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                loadedBundle != null -> {
                    val bundle = loadedBundle!!
                    val filteredLogs = remember(bundle, searchQuery) {
                        if (searchQuery.isBlank()) {
                            bundle.diaries
                        } else {
                            val q = searchQuery.lowercase(Locale.getDefault())
                            bundle.diaries.filter {
                                it.heading.lowercase(Locale.getDefault()).contains(q) ||
                                it.plainPreview.lowercase(Locale.getDefault()).contains(q) ||
                                it.contentHtml.lowercase(Locale.getDefault()).contains(q)
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search backed-up entries...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${filteredLogs.size} entries found in vault",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val backupDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                        .format(Date(bundle.exportedAt))
                                    Text(
                                        text = "Saved: $backupDate",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        if (filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No entries match '$searchQuery'" else "No diaries found in this backup vault file.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredLogs, key = { it.id }) { diary ->
                                    BackedUpDiaryCard(
                                        diary = diary,
                                        onRead = { selectedDiaryForReading = diary },
                                        onRestore = {
                                            onRestoreSingleDiaryToApp(diary)
                                            Toast.makeText(context, "Restored '${diary.heading}' to active app!", Toast.LENGTH_SHORT).show()
                                        },
                                        onDelete = { diaryToDeleteFromVault = diary }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedDiaryForReading != null) {
        val diary = selectedDiaryForReading!!
        AlertDialog(
            onDismissRequest = { selectedDiaryForReading = null },
            modifier = Modifier.fillMaxWidth(0.95f),
            title = {
                Column {
                    Text(
                        text = diary.heading.ifEmpty { "Untitled Entry" },
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(diary.createdAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = diary.plainPreview.ifEmpty { diary.contentHtml.replace(Regex("<[^>]*>"), "") },
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (diary.canvasImages.isNotEmpty()) {
                        item {
                            Text(
                                text = "Attached Photos (${diary.canvasImages.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(diary.canvasImages) { img ->
                            AsyncImage(
                                model = if (img.filePath.isNotEmpty()) File(img.filePath) else img.dataUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    if (diary.audioAttachments.isNotEmpty()) {
                        item {
                            Text(
                                text = "Voice Recordings (${diary.audioAttachments.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(diary.audioAttachments) { audio ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = WikipediaBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = audio.name.ifEmpty { "Voice Note" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${audio.durationSec.toInt()}s",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreSingleDiaryToApp(diary)
                        Toast.makeText(context, "Restored '${diary.heading}' to active app!", Toast.LENGTH_SHORT).show()
                        selectedDiaryForReading = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WikipediaBlue)
                ) {
                    Text("Restore to App")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDiaryForReading = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (diaryToDeleteFromVault != null) {
        val diary = diaryToDeleteFromVault!!
        AlertDialog(
            onDismissRequest = { diaryToDeleteFromVault = null },
            title = {
                Text(
                    text = "Delete from Backup Vault?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = WikipediaRed
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${diary.heading}' permanently from your backup file?\n\nThe remaining entries will be streamed back to disk safely.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        diaryToDeleteFromVault = null
                        if (safUri != null && loadedBundle != null) {
                            val currentDiaries = loadedBundle!!.diaries.filter { it.id != diary.id }
                            val updatedBundle = loadedBundle!!.copy(
                                diaries = currentDiaries,
                                totalEntries = currentDiaries.size,
                                exportedAt = System.currentTimeMillis()
                            )
                            loadedBundle = updatedBundle

                            scope.launch {
                                val passHash = securityManager.getPasscodeHash(isDecoy)
                                val pass = passHash ?: "likkho_default_safe_key"
                                val backupRes = vaultEngine.performStreamingBackup(
                                    treeUri = safUri,
                                    bundle = updatedBundle,
                                    isDecoy = isDecoy,
                                    password = pass
                                ) { _ -> }

                                if (backupRes.isSuccess) {
                                    Toast.makeText(context, "Removed from vault and backup updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to update backup file", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WikipediaRed)
                ) {
                    Text("Delete from Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { diaryToDeleteFromVault = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BackedUpDiaryCard(
    diary: DiaryLog,
    onRead: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { onRead() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diary.heading.ifEmpty { "Untitled Entry" },
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(diary.createdAt)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = diary.plainPreview.ifEmpty { diary.contentHtml.replace(Regex("<[^>]*>"), "") },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (diary.canvasImages.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = WikipediaBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${diary.canvasImages.size}",
                                fontSize = 11.sp,
                                color = WikipediaBlue
                            )
                        }
                    }
                    if (diary.audioAttachments.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = WikipediaBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${diary.audioAttachments.size}",
                                fontSize = 11.sp,
                                color = WikipediaBlue
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onRestore,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = WikipediaBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp, color = WikipediaBlue)
                    }

                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = WikipediaRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, color = WikipediaRed)
                    }
                }
            }
        }
    }
}

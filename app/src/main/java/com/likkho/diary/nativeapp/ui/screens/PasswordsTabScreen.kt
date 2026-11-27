package com.likkho.diary.nativeapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.SavedPasswordItem
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
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class PasswordSortMode(val label: String) {
    ALPHABETICAL("A–Z"),
    RECENT("Recent")
}

/**
 * Everyday Tab 5: Dedicated Offline Password Manager.
 *
 * Features:
 * - Searchable across Service/Name, Username/Email, URL, and Notes
 * - Preserves 100% backward compatibility with legacy [SavedPasswordItem] entries
 * - 1-tap Show/Hide reveal, Copy Password, Copy Username, and Launch URL
 * - Built-in `SecureRandom` strong password generator with configurable length & character sets
 */
@Composable
fun PasswordsTabScreen(
    passwords: List<SavedPasswordItem>,
    searchQuery: String,
    vaultMode: VaultMode,
    repository: NativeVaultRepository,
    onPasswordsUpdated: (List<SavedPasswordItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var sortMode by remember { mutableStateOf(PasswordSortMode.ALPHABETICAL) }
    var isAddingPassword by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf<SavedPasswordItem?>(null) }
    var pendingDeletePassword by remember { mutableStateOf<SavedPasswordItem?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    val revealedMap = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2600L)
            feedbackBanner = null
        }
    }

    BackHandler(
        enabled = isAddingPassword || editingPassword != null || pendingDeletePassword != null
    ) {
        when {
            pendingDeletePassword != null -> pendingDeletePassword = null
            isAddingPassword || editingPassword != null -> {
                isAddingPassword = false
                editingPassword = null
            }
        }
    }

    fun persistPasswords(nextList: List<SavedPasswordItem>, feedback: String) {
        onPasswordsUpdated(nextList)
        scope.launch(Dispatchers.IO) {
            repository.saveSavedPasswords(vaultMode, nextList)
        }
        feedbackBanner = feedback
    }

    fun copyToClipboard(label: String, text: String) {
        if (text.isEmpty()) return
        clipboard.setText(AnnotatedString(text))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        feedbackBanner = "$label copied to clipboard."
    }

    fun openExternalUrl(rawUrl: String) {
        val clean = rawUrl.trim()
        if (clean.isEmpty()) return
        val resolved = if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) {
            clean
        } else {
            "https://$clean"
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolved)))
        }.onFailure {
            feedbackBanner = "Unable to open URL in browser."
        }
    }

    val filteredPasswords = remember(passwords, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        val matched = if (q.isEmpty()) {
            passwords
        } else {
            passwords.filter { item ->
                item.name.lowercase(Locale.getDefault()).contains(q) ||
                    item.username.lowercase(Locale.getDefault()).contains(q) ||
                    item.url.lowercase(Locale.getDefault()).contains(q) ||
                    item.notes.lowercase(Locale.getDefault()).contains(q)
            }
        }
        when (sortMode) {
            PasswordSortMode.ALPHABETICAL -> matched.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { "zzz" } }
            )
            PasswordSortMode.RECENT -> matched.sortedByDescending { maxOf(it.updatedAt, it.createdAt) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Summary & Sort Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VpnKey,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "${filteredPasswords.size} of ${passwords.size} matching"
                        } else {
                            "${passwords.size} saved credentials"
                        },
                        style = TextStyle(
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.muted,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PasswordSortMode.entries.forEach { mode ->
                        val selected = sortMode == mode
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .background(if (selected) colors.accent else colors.bg)
                                .border(
                                    LikkhoHairlineBorder,
                                    if (selected) colors.accentHover else colors.border,
                                    RectangleShape
                                )
                                .clickable { sortMode = mode }
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (selected) Color.White else colors.text
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)

            if (!feedbackBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.emerald.copy(alpha = 0.08f))
                        .border(LikkhoHairlineBorder, colors.emerald, RectangleShape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.emerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = feedbackBanner.orEmpty(),
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filteredPasswords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.border, RectangleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchQuery.isNotBlank()) {
                                    Icons.Outlined.Search
                                } else {
                                    Icons.Outlined.Key
                                },
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching passwords" else "No Saved Passwords",
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No saved logins matched \"$searchQuery\"."
                            } else {
                                "Store logins, usernames, URLs, and notes offline in encrypted vault storage with 1-tap copy and built-in password generator."
                            },
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LikkhoDialogPrimaryButton(
                                text = "Add Password",
                                leadingIcon = Icons.Outlined.Add,
                                onClick = { isAddingPassword = true }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPasswords, key = { it.id }) { item ->
                        val isRevealed = revealedMap[item.id] == true
                        PasswordCardItem(
                            item = item,
                            isRevealed = isRevealed,
                            onToggleReveal = { revealedMap[item.id] = !isRevealed },
                            onCopyUsername = {
                                copyToClipboard("Username for ${item.name.ifBlank { "account" }}", item.username)
                            },
                            onCopyPassword = {
                                copyToClipboard("Password for ${item.name.ifBlank { "account" }}", item.password)
                            },
                            onOpenUrl = { openExternalUrl(item.url) },
                            onEdit = { editingPassword = item },
                            onDelete = { pendingDeletePassword = item }
                        )
                    }
                }
            }
        }

        // Bottom-right rectangular FAB
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .shadow(8.dp, RectangleShape)
                .height(42.dp)
                .background(colors.accent)
                .border(LikkhoHairlineBorder, colors.accentHover, RectangleShape)
                .clickable { isAddingPassword = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add Password",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Password",
                fontFamily = LikkhoFontFamilies.Sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        }

        if (isAddingPassword || editingPassword != null) {
            PasswordEditorDialog(
                initialItem = editingPassword,
                onDismiss = {
                    isAddingPassword = false
                    editingPassword = null
                },
                onSave = { savedItem ->
                    val wasEditing = editingPassword != null
                    isAddingPassword = false
                    editingPassword = null
                    val nextList = if (wasEditing) {
                        passwords.map { if (it.id == savedItem.id) savedItem else it }
                    } else {
                        listOf(savedItem) + passwords
                    }
                    persistPasswords(
                        nextList,
                        if (wasEditing) {
                            "Updated \"${savedItem.name}\"."
                        } else {
                            "Saved \"${savedItem.name}\" to vault."
                        }
                    )
                }
            )
        }

        pendingDeletePassword?.let { target ->
            LikkhoHairlineDialog(
                onDismissRequest = { pendingDeletePassword = null },
                title = "Delete Password",
                subtitle = "PERMANENT CREDENTIAL REMOVAL",
                leadingIcon = Icons.Outlined.WarningAmber,
                leadingIconTint = colors.crimson,
                borderColor = colors.crimson,
                footer = {
                    LikkhoDialogOutlineButton(
                        text = "Cancel",
                        onClick = { pendingDeletePassword = null }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LikkhoDialogPrimaryButton(
                        text = "Delete",
                        bgColor = colors.crimson,
                        onClick = {
                            val toDelete = target
                            pendingDeletePassword = null
                            revealedMap.remove(toDelete.id)
                            val remaining = passwords.filterNot { it.id == toDelete.id }
                            persistPasswords(
                                remaining,
                                "Deleted \"${toDelete.name.ifBlank { "Saved Password" }}\"."
                            )
                        }
                    )
                }
            ) {
                Text(
                    text = "Delete saved login for \"${target.name.ifBlank { "Untitled" }}\"?",
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.text
                )
            }
        }
    }
}

@Composable
private fun PasswordCardItem(
    item: SavedPasswordItem,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onOpenUrl: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LikkhoTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.surface)
            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.accent)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Service Name + URL + Edit/Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name.ifBlank { "Untitled Account" },
                            fontFamily = LikkhoFontFamilies.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.url.isNotBlank()) {
                            Text(
                                text = item.url.trim(),
                                fontFamily = LikkhoFontFamilies.Mono,
                                fontSize = 10.sp,
                                color = colors.link,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            val stampMs = maxOf(item.updatedAt, item.createdAt)
                            if (stampMs > 0L) {
                                Text(
                                    text = "Updated ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(stampMs))}",
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontSize = 9.sp,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.url.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colors.bg)
                                .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                                .clickable { onOpenUrl() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = "Open URL",
                                tint = colors.link,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onEdit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Password",
                            tint = colors.text,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Password",
                            tint = colors.crimson,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Username Row
            if (item.username.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                        .clickable { onCopyUsername() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = item.username,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 12.sp,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy username",
                            tint = colors.muted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "User",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.muted
                        )
                    }
                }
            }

            // Password Row (Reveal + 1-Tap Copy)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isRevealed) {
                        item.password
                    } else {
                        "•".repeat(item.password.length.coerceIn(8, 18))
                    },
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 13.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.surface)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .clickable { onToggleReveal() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRevealed) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (isRevealed) "Hide password" else "Show password",
                            tint = colors.muted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .background(colors.accent.copy(alpha = 0.1f))
                            .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.5f), RectangleShape)
                            .clickable { onCopyPassword() }
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy password",
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Copy",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = colors.accent
                        )
                    }
                }
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PasswordEditorDialog(
    initialItem: SavedPasswordItem?,
    onDismiss: () -> Unit,
    onSave: (SavedPasswordItem) -> Unit
) {
    val colors = LikkhoTheme.colors
    val nameFocus = remember { FocusRequester() }

    var name by remember(initialItem) { mutableStateOf(initialItem?.name.orEmpty()) }
    var username by remember(initialItem) { mutableStateOf(initialItem?.username.orEmpty()) }
    var password by remember(initialItem) { mutableStateOf(initialItem?.password.orEmpty()) }
    var url by remember(initialItem) { mutableStateOf(initialItem?.url.orEmpty()) }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }
    var showPassword by remember(initialItem) { mutableStateOf(initialItem == null) }
    var showGenerator by remember { mutableStateOf(false) }

    var genLength by remember { mutableIntStateOf(16) }
    var genUpper by remember { mutableStateOf(true) }
    var genLower by remember { mutableStateOf(true) }
    var genDigits by remember { mutableStateOf(true) }
    var genSymbols by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (initialItem == null) {
            nameFocus.requestFocus()
        }
    }

    val strength = remember(password) { evaluatePasswordStrength(password) }

    LikkhoHairlineDialog(
        onDismissRequest = onDismiss,
        title = if (initialItem == null) "Add Password" else "Edit Password",
        subtitle = "ENCRYPTED LOGIN VAULT",
        leadingIcon = Icons.Outlined.VpnKey,
        maxWidth = 450.dp,
        maxHeight = 680.dp,
        footer = {
            LikkhoDialogOutlineButton(text = "Cancel", onClick = onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            LikkhoDialogPrimaryButton(
                text = if (initialItem == null) "Save Password" else "Update",
                enabled = name.trim().isNotEmpty() && password.isNotEmpty(),
                onClick = {
                    val now = System.currentTimeMillis()
                    val item = SavedPasswordItem(
                        id = initialItem?.id ?: "pw_${now}_${UUID.randomUUID().toString().take(4)}",
                        name = name.trim(),
                        password = password,
                        createdAt = initialItem?.createdAt ?: now,
                        username = username.trim(),
                        url = url.trim(),
                        notes = notes.trim(),
                        updatedAt = now
                    )
                    onSave(item)
                }
            )
        }
    ) {
        // Service / App Name
        PwTextField(
            label = "SERVICE / APP NAME",
            value = name,
            onValueChange = { name = it },
            placeholder = "e.g., Gmail, HDFC Bank, GitHub...",
            focusRequester = nameFocus
        )

        // Username / Email
        PwTextField(
            label = "USERNAME / EMAIL (OPTIONAL)",
            value = username,
            onValueChange = { username = it },
            placeholder = "e.g., name@example.com",
            keyboardType = KeyboardType.Email
        )

        // Password + Generator Trigger
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PASSWORD",
                    fontFamily = LikkhoFontFamilies.Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.muted
                )
                Row(
                    modifier = Modifier
                        .background(if (showGenerator) colors.accent else colors.surface)
                        .border(
                            LikkhoHairlineBorder,
                            if (showGenerator) colors.accentHover else colors.border,
                            RectangleShape
                        )
                        .clickable {
                            showGenerator = !showGenerator
                            if (showGenerator && password.isEmpty()) {
                                password = generateStrongPassword(genLength, genUpper, genLower, genDigits, genSymbols)
                                showPassword = true
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = if (showGenerator) Color.White else colors.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (showGenerator) "Hide Generator" else "Generate Strong",
                        fontFamily = LikkhoFontFamilies.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = if (showGenerator) Color.White else colors.accent
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (password.isEmpty()) {
                        Text(
                            text = "Enter or generate a password...",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 12.sp,
                            color = colors.muted
                        )
                    }
                    BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = if (showPassword) {
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
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (showPassword) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = "Toggle visibility",
                    tint = colors.muted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { showPassword = !showPassword }
                )
            }

            if (password.isNotEmpty()) {
                val strengthColor = when (strength.score) {
                    0, 1 -> colors.crimson
                    2 -> colors.accent
                    else -> colors.emerald
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(4) { step ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(if (step <= strength.score) strengthColor else colors.hairline)
                            )
                        }
                    }
                    Text(
                        text = strength.label,
                        fontFamily = LikkhoFontFamilies.Mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = strengthColor
                    )
                }
            }

            // Inline Strong Password Generator Panel
            if (showGenerator) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(LikkhoHairlineBorder, colors.accent.copy(alpha = 0.5f), RectangleShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "LENGTH: $genLength",
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(12, 16, 20, 24).forEach { len ->
                                val active = genLength == len
                                Box(
                                    modifier = Modifier
                                        .background(if (active) colors.accent else colors.bg)
                                        .border(
                                            LikkhoHairlineBorder,
                                            if (active) colors.accentHover else colors.border,
                                            RectangleShape
                                        )
                                        .clickable {
                                            genLength = len
                                            password = generateStrongPassword(len, genUpper, genLower, genDigits, genSymbols)
                                            showPassword = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$len",
                                        fontFamily = LikkhoFontFamilies.Mono,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) Color.White else colors.text
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val anyOther = { u: Boolean, l: Boolean, d: Boolean, s: Boolean -> u || l || d || s }
                        GenCharChip("A–Z", genUpper, Modifier.weight(1f)) {
                            if (anyOther(!genUpper, genLower, genDigits, genSymbols)) {
                                genUpper = !genUpper
                                password = generateStrongPassword(genLength, genUpper, genLower, genDigits, genSymbols)
                                showPassword = true
                            }
                        }
                        GenCharChip("a–z", genLower, Modifier.weight(1f)) {
                            if (anyOther(genUpper, !genLower, genDigits, genSymbols)) {
                                genLower = !genLower
                                password = generateStrongPassword(genLength, genUpper, genLower, genDigits, genSymbols)
                                showPassword = true
                            }
                        }
                        GenCharChip("0–9", genDigits, Modifier.weight(1f)) {
                            if (anyOther(genUpper, genLower, !genDigits, genSymbols)) {
                                genDigits = !genDigits
                                password = generateStrongPassword(genLength, genUpper, genLower, genDigits, genSymbols)
                                showPassword = true
                            }
                        }
                        GenCharChip("!@#", genSymbols, Modifier.weight(1f)) {
                            if (anyOther(genUpper, genLower, genDigits, !genSymbols)) {
                                genSymbols = !genSymbols
                                password = generateStrongPassword(genLength, genUpper, genLower, genDigits, genSymbols)
                                showPassword = true
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
                            .clickable {
                                password = generateStrongPassword(genLength, genUpper, genLower, genDigits, genSymbols)
                                showPassword = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Regenerate Password",
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = colors.accent
                        )
                    }
                }
            }
        }

        // Website URL
        PwTextField(
            label = "WEBSITE / APP URL (OPTIONAL)",
            value = url,
            onValueChange = { url = it },
            placeholder = "https://example.com",
            keyboardType = KeyboardType.Uri
        )

        // Notes
        PwTextField(
            label = "NOTES (OPTIONAL)",
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Recovery PIN, security question, customer ID...",
            singleLine = false
        )
    }
}

@Composable
private fun GenCharChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LikkhoTheme.colors
    Box(
        modifier = modifier
            .height(28.dp)
            .background(if (active) colors.accent.copy(alpha = 0.14f) else colors.bg)
            .border(
                LikkhoHairlineBorder,
                if (active) colors.accent else colors.border,
                RectangleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Mono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) colors.accent else colors.muted
        )
    }
}

@Composable
private fun PwTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null
) {
    val colors = LikkhoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            fontFamily = LikkhoFontFamilies.Mono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.muted
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    color = colors.muted
                )
            }
            val fieldMod = if (focusRequester != null) {
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                maxLines = if (singleLine) 1 else 4,
                textStyle = TextStyle(
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 13.sp,
                    color = colors.text
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (keyboardType == KeyboardType.Text) {
                        KeyboardCapitalization.Sentences
                    } else {
                        KeyboardCapitalization.None
                    },
                    keyboardType = keyboardType,
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
                ),
                modifier = fieldMod
            )
        }
    }
}

private data class PasswordStrength(val score: Int, val label: String)

private fun evaluatePasswordStrength(pw: String): PasswordStrength {
    if (pw.length < 6) return PasswordStrength(0, "Weak")
    var kinds = 0
    if (pw.any { it.isUpperCase() }) kinds++
    if (pw.any { it.isLowerCase() }) kinds++
    if (pw.any { it.isDigit() }) kinds++
    if (pw.any { !it.isLetterOrDigit() }) kinds++
    return when {
        pw.length >= 14 && kinds >= 3 -> PasswordStrength(3, "Very Strong")
        pw.length >= 10 && kinds >= 3 -> PasswordStrength(2, "Strong")
        pw.length >= 8 && kinds >= 2 -> PasswordStrength(1, "Fair")
        else -> PasswordStrength(0, "Weak")
    }
}

private fun generateStrongPassword(
    length: Int,
    includeUpper: Boolean,
    includeLower: Boolean,
    includeDigits: Boolean,
    includeSymbols: Boolean
): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghijkmnopqrstuvwxyz"
    val digits = "23456789"
    val symbols = "!@#$%^&*_-+="

    val pools = mutableListOf<String>()
    if (includeUpper) pools.add(upper)
    if (includeLower) pools.add(lower)
    if (includeDigits) pools.add(digits)
    if (includeSymbols) pools.add(symbols)
    if (pools.isEmpty()) pools.add(lower)

    val combined = pools.joinToString("")
    val rng = SecureRandom()
    val chars = ArrayList<Char>(length)

    // Ensure at least one character from each enabled pool
    for (pool in pools) {
        if (chars.size < length) {
            chars.add(pool[rng.nextInt(pool.length)])
        }
    }
    while (chars.size < length) {
        chars.add(combined[rng.nextInt(combined.length)])
    }
    // Fisher-Yates shuffle with SecureRandom
    for (i in chars.lastIndex downTo 1) {
        val j = rng.nextInt(i + 1)
        val tmp = chars[i]
        chars[i] = chars[j]
        chars[j] = tmp
    }
    return chars.joinToString("")
}

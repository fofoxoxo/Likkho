package com.likkho.diary.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.diary.nativeapp.data.NativeVaultRepository
import com.likkho.diary.nativeapp.model.PersonalDocument
import com.likkho.diary.nativeapp.model.QuickNote
import com.likkho.diary.nativeapp.model.RecurringTracker
import com.likkho.diary.nativeapp.model.SavedPasswordItem
import com.likkho.diary.nativeapp.model.StandaloneReminder
import com.likkho.diary.nativeapp.model.VaultMode
import com.likkho.diary.nativeapp.ui.components.LikkhoHairlineBorder
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme

enum class EverydayTab(
    val label: String,
    val icon: ImageVector
) {
    NOTES("Notes", Icons.Outlined.CheckBox),
    TRACKERS("Trackers", Icons.Outlined.CalendarMonth),
    REMINDERS("Reminders", Icons.Outlined.NotificationsActive),
    DOCUMENTS("Documents", Icons.Outlined.Description),
    PASSWORDS("Passwords", Icons.Outlined.VpnKey)
}

/**
 * Everyday Mode 5-Tab Shell (`VaultMode.PRIMARY`).
 *
 * - Wikipedia editorial Cormorant Garamond header with live Search, Instant Lock & Settings menu.
 * - Crisp rectangular 5-tab Bottom Navigation (`Notes`, `Trackers`, `Reminders`, `Documents`, `Passwords`).
 * - Step-by-step [BackHandler]: closes Search -> returns to `Notes` tab -> exits app.
 * - Zero hints or references to Secret Writing anywhere in Everyday UI.
 */
@Composable
fun EverydayShellScreen(
    quickNotes: List<QuickNote>,
    trackers: List<RecurringTracker>,
    reminders: List<StandaloneReminder>,
    documents: List<PersonalDocument>,
    savedPasswords: List<SavedPasswordItem>,
    repository: NativeVaultRepository,
    isLoadingVault: Boolean = false,
    statusBanner: String? = null,
    processingBanner: String? = null,
    onDismissStatusBanner: () -> Unit = {},
    onLockApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickNotesUpdated: (List<QuickNote>) -> Unit,
    onTrackersUpdated: (List<RecurringTracker>) -> Unit,
    onRemindersUpdated: (List<StandaloneReminder>) -> Unit,
    onDocumentsUpdated: (List<PersonalDocument>) -> Unit,
    onPasswordsUpdated: (List<SavedPasswordItem>) -> Unit,
    onSystemPickerStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LikkhoTheme.colors

    var activeTab by rememberSaveable { mutableStateOf(EverydayTab.NOTES) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = isSearchOpen || activeTab != EverydayTab.NOTES) {
        when {
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
            }
            activeTab != EverydayTab.NOTES -> {
                activeTab = EverydayTab.NOTES
                searchQuery = ""
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
        EverydayHeaderBar(
            activeTab = activeTab,
            isSearchOpen = isSearchOpen,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onOpenSearch = { isSearchOpen = true },
            onCloseSearch = {
                isSearchOpen = false
                searchQuery = ""
            },
            onLockApp = onLockApp,
            onOpenSettings = onOpenSettings
        )

        if (!processingBanner.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = processingBanner,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (!statusBanner.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(LikkhoHairlineBorder, colors.emerald, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusBanner,
                    fontFamily = LikkhoFontFamilies.Sans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss banner",
                    tint = colors.muted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDismissStatusBanner() }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoadingVault) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                when (activeTab) {
                    EverydayTab.NOTES -> {
                        NotesTabScreen(
                            notes = quickNotes,
                            searchQuery = searchQuery,
                            vaultMode = VaultMode.PRIMARY,
                            repository = repository,
                            onNotesUpdated = onQuickNotesUpdated
                        )
                    }

                    EverydayTab.TRACKERS -> {
                        TrackersTabScreen(
                            trackers = trackers,
                            searchQuery = searchQuery,
                            vaultMode = VaultMode.PRIMARY,
                            repository = repository,
                            onTrackersUpdated = onTrackersUpdated
                        )
                    }

                    EverydayTab.REMINDERS -> {
                        RemindersTabScreen(
                            reminders = reminders,
                            searchQuery = searchQuery,
                            vaultMode = VaultMode.PRIMARY,
                            repository = repository,
                            onRemindersUpdated = onRemindersUpdated
                        )
                    }

                    EverydayTab.DOCUMENTS -> {
                        DocumentsTabScreen(
                            documents = documents,
                            searchQuery = searchQuery,
                            vaultMode = VaultMode.PRIMARY,
                            repository = repository,
                            onDocumentsUpdated = onDocumentsUpdated,
                            onSystemPickerStateChanged = onSystemPickerStateChanged
                        )
                    }

                    EverydayTab.PASSWORDS -> {
                        PasswordsTabScreen(
                            passwords = savedPasswords,
                            searchQuery = searchQuery,
                            vaultMode = VaultMode.PRIMARY,
                            repository = repository,
                            onPasswordsUpdated = onPasswordsUpdated
                        )
                    }
                }
            }
        }

        EverydayBottomNavBar(
            activeTab = activeTab,
            onSelectTab = { nextTab ->
                if (activeTab != nextTab) {
                    activeTab = nextTab
                    if (isSearchOpen) {
                        searchQuery = ""
                    }
                }
            }
        )
    }
}

@Composable
private fun EverydayHeaderBar(
    activeTab: EverydayTab,
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onLockApp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LikkhoTheme.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!isSearchOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Likkho",
                        fontFamily = LikkhoFontFamilies.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.hairline, RectangleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = activeTab.label.uppercase(),
                            fontFamily = LikkhoFontFamilies.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = colors.muted,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { onOpenSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = colors.text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { onLockApp() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Lock App",
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.bg)
                            .border(LikkhoHairlineBorder, colors.border, RectangleShape)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Settings",
                            tint = colors.text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(colors.bg)
                        .border(LikkhoHairlineBorder, colors.accent, RectangleShape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(17.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search ${activeTab.label.lowercase()}...",
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 13.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = LikkhoFontFamilies.Sans,
                                fontSize = 13.sp,
                                color = colors.text
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close Search",
                        tint = colors.muted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onCloseSearch() }
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

@Composable
private fun EverydayBottomNavBar(
    activeTab: EverydayTab,
    onSelectTab: (EverydayTab) -> Unit
) {
    val colors = LikkhoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EverydayTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .background(if (selected) colors.bg else colors.surface)
                        .clickable { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) colors.accent else colors.surface)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) colors.accent else colors.muted,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            fontFamily = LikkhoFontFamilies.Sans,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp,
                            color = if (selected) colors.accent else colors.muted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


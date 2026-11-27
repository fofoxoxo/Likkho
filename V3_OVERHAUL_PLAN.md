# Likkho v3.0 — Personal Utility & Secret Journal Overhaul Plan

> **Multi-AI Collaborator Instructions (READ THIS FIRST!)**
> Multiple AI Studio workspaces are collaborating sequentially on this repository.
> **On every session:**
> 1. Read this file (`V3_OVERHAUL_PLAN.md`) completely before modifying any code.
> 2. Execute **ONLY the first unchecked `[ ]` step** in the Checklist below (unless the user explicitly asks for a specific task/fix).
> 3. Inspect existing files under `app/src/main/java/com/likkho/diary/nativeapp/` and `src/` to build cleanly on top of previous steps.
> 4. Maintain strict quality & compatibility contracts:
>    - Keep `npm run build` (`vite build`) and `npm run lint` (`tsc --noEmit`) passing.
>    - Verify all Kotlin `.kt` files for clean imports and `minSdk = 24` / `compileSdk = 35` compatibility (Gradle compiles the APK in GitHub Actions).
>    - **Zero Data Loss & Zero Breaking Crypto Changes:** Never break `applicationId = "com.likkho.diary"`, release keystore signing, existing `likkho_vaults/decoy/` diaries/media, or `LKSTRM02` / `LKSTRM01` / `WIKILOG_VAULT_V1` backup formats (`real_vault.db` & `decoy_vault.db`).
>    - **Debug APK Only During Intermediate Steps:** Do **NOT** bump version numbers (`3.0.0`) or publish a GitHub Release until **Step 7** (or when explicitly ordered by the user).
> 5. After completing a step, mark its checkbox `[x]` below and append a concise entry to the **Progress Log** with modified/created files and the next pending step.

---

## Target Architecture (`Likkho v3.0`)

Likkho v3.0 removes heavy/unwanted editor bloat (Wikipedia search, LaTeX math, Spoiler cipher, Word Analysis, custom `.ttf` uploader, Image Studio filters, Mic Foreground Service bloat, WebView Canvas runtime in APK) and transforms into a fast, **100% Pure Native Kotlin + Jetpack Compose** app with an **Inverted Split-PIN Dual-Vault**:

1. **Everyday Mode (`VaultMode.PRIMARY` / `real_vault.db`)** — Unlocked by Everyday PIN **or** Fingerprint/Biometrics:
   - **Tab 1 — Notes:** Clean Quick Notes & interactive Checklists (synced with system Quick Note shortcuts/notifications).
   - **Tab 2 — Trackers (Calendar Hisaab):** Recurring daily trackers (e.g., Milk, Maid, Newspaper, Medicine, Gym) with a monthly calendar grid, 1-tap `Present ✓` / `Missed ✗` / `Skipped` marking, optional quantity + unit rate, and automatic monthly total/bill summary.
   - **Tab 3 — Reminders:** Scheduled reminders with Date & Time picker, Repeat (`Once`, `Daily`, `Weekly`, `Monthly`), `AlarmManager` notifications, and Upcoming/Completed views.
   - **Tab 4 — Documents:** Ready-to-use encrypted personal document vault (`likkho_docs/` with `.nomedia` + `FLAG_SECURE`): Aadhaar, PAN, Passport, Finance, Medical, Travel, etc. Supports PDF & image import, quick-copy ID numbers, full-screen pinch-zoom image & `PdfRenderer` viewer, and instant FileProvider Share.
   - **Tab 5 — Passwords:** Dedicated searchable password vault (Service/App, Username/Email, Password with reveal/copy/generator, URL, Notes) preserving legacy `SavedPasswordItem` entries.

2. **Secret Writing Mode (`VaultMode.DECOY` / `decoy_vault.db`)** — Unlocked **strictly via manual Secret PIN** (NEVER via Biometrics, zero hint/mention anywhere in Everyday UI):
   - Distraction-free private **Deep Thoughts / Writing** journal stored in `VaultMode.DECOY` (`likkho_vaults/decoy/`).
   - 100% backward-compatible with all existing Decoy diaries (renders legacy HTML cleanly as readable text + existing canvas images & audio attachments) plus a safe one-time import/move option for any legacy `VaultMode.PRIMARY` diaries so zero entries are ever lost.

---

## Step-by-Step Checklist

- [x] **Step 1: v3.0 Core Data Models, Encrypted Repository Extensions & `LKSTRM02` Backup Support**
  - Retire `NATIVE_MIGRATION_PLAN.md` and establish `V3_OVERHAUL_PLAN.md`.
  - Extend `VaultModels.kt` with `RecurringTracker`, `TrackerDayEntry`, `TrackerEntryStatus`, `StandaloneReminder`, `ReminderRepeat`, `PersonalDocument`, `DocumentCategory`, backward-compatible `SavedPasswordItem` (`username`, `url`, `notes`, `updatedAt`), and extended `VaultBackupBundle` (`trackers`, `reminders`, `documents`).
  - Extend `NativeVaultRepository.kt` with encrypted CRUD for Trackers, Reminders, Documents, `likkho_docs/` `.nomedia` path-confined document storage (`lkdoc_*`), and legacy primary-to-secret diary migration helpers (`hasLegacyPrimaryLogs`, `migrateLegacyPrimaryLogsToSecretVault`).
  - Extend `StreamCryptoEngine.kt` so `LKSTRM02` backup/restore seamlessly encrypts, streams, and restores `trackers`, `reminders`, `documents`, extended `savedPasswords`, and `likkho_docs/` binary files while remaining 100% compatible with old v2 backups.

- [ ] **Step 2: Split-PIN Lockscreen, Biometric Rules, 5-Tab Everyday Shell & Notes Tab**
  - Update `PasscodeLockScreen.kt` & `NativeMainActivity.kt` so Biometrics (Fingerprint) unlock **ONLY** Everyday Mode (`VaultMode.PRIMARY`), while Secret Writing (`VaultMode.DECOY`) opens strictly via manual PIN and leaves zero clue in Everyday UI.
  - Create `EverydayShellScreen.kt` with Cormorant Garamond header, Search, Instant Lock, Settings action, Wikipedia-editorial 5-tab Bottom Navigation (`Notes`, `Trackers`, `Reminders`, `Documents`, `Passwords`), and step-by-step `BackHandler` returning to the default tab (`Notes`) before app exit.
  - Create `NotesTabScreen.kt` (upgrading Quick Notes & Checklists into a clean native tab with search, pin, checklist toggle, and `QuickNoteSystemSync` support).

- [ ] **Step 3: Calendar-Based Recurring Trackers ("Milk ka Hisaab" / `TrackersTabScreen.kt`)**
  - Create `TrackersTabScreen.kt`:
    - List of recurring trackers with quick today-status badge and monthly summary pill.
    - Create/Edit tracker modal (Name e.g. "Milk", Unit e.g. "L", Default Qty e.g. `1.0`, Optional Rate/unit `₹65`, Accent color, Notes, Archive/Delete).
    - Interactive Monthly Calendar view (`< Month YYYY >`) where tapping a date cycles/sets `Present ✓` / `Missed ✗` / `Skipped` / `Clear`, and long-pressing (or tapping edit) lets the user enter custom quantity & day note.
    - Automatic Monthly Summary card showing Present days, Missed days, Total Quantity, and Total Amount (`₹`) when rate > 0.

- [ ] **Step 4: Native Reminders & Alarm Notifications (`RemindersTabScreen.kt`)**
  - Create `RemindersTabScreen.kt` with Upcoming / Completed filter, Search, Create/Edit/Delete reminder sheet (Title, Note, Date & Time picker, Repeat: `Once`, `Daily`, `Weekly`, `Monthly`), 1-tap Mark Done, and Snooze.
  - Wire exact `AlarmManager` scheduling & automatic next-occurrence rescheduling for repeating reminders in `NativeMainActivity.kt` and `.github/workflows/android-debug-apk.yml` `ReminderReceiver` (plus `BOOT_COMPLETED` safety).

- [ ] **Step 5: Personal Documents Vault (`DocumentsTabScreen.kt`) & Dedicated Passwords Vault (`PasswordsTabScreen.kt`)**
  - Create `DocumentsTabScreen.kt`:
    - Import PDF or Image from system picker (`ActivityResultContracts.OpenDocument` / `GetContent`), store securely in `likkho_docs/` (`lkdoc_*`), save Title, Category (`ID Card`, `Finance`, `Travel`, `Education`, `Medical`, `Other`), Quick-Copy ID/Number, Issued/Expiry dates, Notes, and Pin toggle.
    - Built-in Ready-to-Use Viewer: full-screen pinch-to-zoom Image Viewer + native `android.graphics.pdf.PdfRenderer` multi-page PDF reader, 1-tap Copy ID Number, and FileProvider Share intent (with temporary decrypted cache file cleanup).
  - Create `PasswordsTabScreen.kt`:
    - Searchable dedicated Password Manager displaying Service/Name, Username/Email, Password (show/hide + 1-tap copy + built-in strong password generator), URL, Notes, Add/Edit/Delete dialogs, preserving all legacy `SavedPasswordItem` entries.

- [ ] **Step 6: Pure Native Secret Writing (`SecretWritingScreen.kt`), Legacy Data Migration & Streamlined Settings/Backup**
  - Create `SecretWritingScreen.kt` & `SecretWritingEditorScreen.kt` (100% Pure Native Compose for `VaultMode.DECOY`):
    - Clean distraction-free journal list & editor (Heading, Date/Time, Clean rich/formatted text body, optional simple Photo attachment, plus clean HTML-to-readable-text rendering and media/audio playback for all existing legacy Decoy diaries; graceful read-only PIN unlock if an old diary had `diaryLockPin`).
  - Update `SettingsScreen.kt` & `BackupRestoreScreen.kt` for v3.0:
    - Split-PIN setup (Everyday PIN + Secret Writing PIN) cleanly separated from Everyday UI hints; Biometrics toggle labeled for Everyday lock; Legacy Real-Vault Diaries safe move/import utility inside Secret Writing Settings; `LKSTRM02` Backup/Restore covering all v3 data & `likkho_docs/`.
  - Remove obsolete bloated screens/bridges (`CanvasWebViewScreen.kt`, `LegacyWebViewMigrationHelper.kt`, `HomeScreen.kt` old diary shell, Word Analysis subscreen, Image Studio filters) cleanly once replaced by native screens.

- [ ] **Step 7: Final Integration, CI/Manifest Cleanup & `v3.0.0` Major Release**
  - Clean up unused WebView/MicForegroundService/bloated exporter permissions/code from `NativeMainActivity.kt`, `NativeExportEngine.kt`, and `.github/workflows/android-debug-apk.yml` (while keeping `ReminderReceiver`, `QuickNoteReceiver`, `LikkhoWidgetProvider`, `QuickNoteTileService`, and `FileProvider` working cleanly).
  - Verify `npm run build` (`vite build`) and `tsc --noEmit` (`npm run lint`) pass cleanly.
  - Bump version to `3.0.0` (`versionCode = 18`) across `package.json`, `app/build.gradle.kts`, `src/utils/appUpdateChecker.ts`, `SettingsScreen.kt`, and `.github/workflows/android-debug-apk.yml` release notes.

---

## Progress Log
- **Step 1 (Completed):** Retired `NATIVE_MIGRATION_PLAN.md` and created `V3_OVERHAUL_PLAN.md`. Extended `VaultModels.kt` (`RecurringTracker`, `TrackerDayEntry`, `TrackerEntryStatus`, `StandaloneReminder`, `ReminderRepeat`, `PersonalDocument`, `DocumentCategory`, backward-compatible `SavedPasswordItem` + `VaultBackupBundle`), `NativeVaultRepository.kt` (encrypted CRUD for Trackers, Reminders, Documents, `.nomedia` `likkho_docs/` storage with `lkdoc_*` IDs, and legacy primary-to-secret diary migration helpers), and `StreamCryptoEngine.kt` (`LKSTRM02` streaming backup & restore for v3 entities and `likkho_docs/` files with 100% compatibility with v2 backups). Next up: **Step 2** (Split-PIN Lockscreen, Biometrics rule, 5-Tab Everyday Shell & `NotesTabScreen.kt`).

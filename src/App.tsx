import React, { useEffect, useRef, useState } from 'react';
import {
  Lock,
  Menu,
  Plus,
  BellRing,
  BookOpen,
  X,
  VolumeX,
  MoreVertical,
  Pin,
  Trash2,
  Download,
  FileDown,
  Search,
  CheckCircle2,
  AlertTriangle,
  KeyRound,
} from 'lucide-react';
import {
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  VaultMode,
  loadActiveAppStateFromIDB,
  saveActiveAppStateToIDB,
  getRemainingPasscodeCooldownSeconds,
  recordPasscodeFailure,
  resetPasscodeFailures,
} from './utils/cryptoVault';
import {
  soundManager,
  triggerSystemNotification,
} from './utils/notificationSound';
import {
  EXPORT_FORMATS,
  ExportFormat,
  exportDiaryLog,
} from './utils/exportLog';
import { WritingWorkspace } from './components/WritingWorkspace';
import { SettingsPage } from './components/SettingsPage';
import { BackupRestorePage } from './components/BackupRestorePage';
import { PasscodeScreen } from './components/PasscodeScreen';

type PageRoute = 'home' | 'workspace' | 'settings' | 'backup';

// Primary Vault (`real_vault.db`) Storage Keys
const STORAGE_LOGS_KEY = 'wikilog_in_app_logs_v1';
const STORAGE_DARK_KEY = 'wikilog_dark_theme_v1';
const STORAGE_PIN_KEY = 'wikilog_passcode_v1';
const STORAGE_BIO_KEY = 'wikilog_biometrics_enabled_v1';
const STORAGE_ENC_HASH_KEY = 'wikilog_encryption_key_hash_v1';
const STORAGE_MIC_SETTINGS_KEY = 'wikilog_mic_settings_v1';
const IDB_LOGS_KEY = 'active_diary_logs_with_media';
const IDB_FONTS_KEY = 'active_custom_ttf_fonts';

// Covert Secondary / Decoy Vault (`decoy_vault.db`) Storage Keys (Completely isolated namespace)
const STORAGE_SECONDARY_PIN_KEY = 'wikilog_covert_secondary_pin_v2';
const STORAGE_DECOY_LOGS_KEY = 'wikilog_decoy_in_app_logs_v2';
const STORAGE_DECOY_ENC_HASH_KEY = 'wikilog_decoy_encryption_key_hash_v2';
const STORAGE_DECOY_MIC_SETTINGS_KEY = 'wikilog_decoy_mic_settings_v2';
const IDB_DECOY_LOGS_KEY = 'decoy_active_diary_logs_with_media';
const IDB_DECOY_FONTS_KEY = 'decoy_active_custom_ttf_fonts';

const DEFAULT_MIC_SETTINGS: MicRecordingSettings = {
  format: 'wav',
  sampleRate: 44100,
  bitRate: 128000,
};

const INITIAL_STARTER_LOGS: DiaryLog[] = [
  {
    id: 'log_starter_1',
    heading: 'My First Entry',
    contentHtml:
      '<p>Welcome to <strong>Likkho</strong>. Start writing your daily thoughts and notes here.</p>',
    plainPreview:
      'Welcome to Likkho. Start writing your daily thoughts and notes here.',
    pfpDataUrl: null,
    createdAt: Date.now() - 3600 * 1000 * 2,
    updatedAt: Date.now() - 3600 * 1000 * 2,
    dateStamp: new Date(Date.now() - 3600 * 1000 * 2).toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }),
    timeStamp: new Date(Date.now() - 3600 * 1000 * 2).toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }),
    updatedDateStamp: new Date(Date.now() - 3600 * 1000 * 2).toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }),
    updatedTimeStamp: new Date(Date.now() - 3600 * 1000 * 2).toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }),
    reminderAt: null,
    pinned: false,
  },
];

function getFallbackMonographPfp(): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
    <rect width="120" height="120" fill="#FFFFFF" stroke="#a2a9b1" stroke-width="2"/>
    <text x="60" y="68" font-family="Georgia, 'Times New Roman', serif" font-size="28" font-weight="bold" fill="#000000" text-anchor="middle">Likkho</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function extractFullPlainText(html: string): string {
  const temp = document.createElement('div');
  temp.innerHTML = html;
  return (temp.textContent || temp.innerText || '').toLowerCase();
}

function injectGlobalFontFaceCss(fontFamily: string, dataUrl: string) {
  try {
    const styleId = `likkho-font-style-${fontFamily}`;
    if (!document.getElementById(styleId)) {
      const styleEl = document.createElement('style');
      styleEl.id = styleId;
      styleEl.textContent = `@font-face { font-family: '${fontFamily}'; src: url('${dataUrl}') format('truetype'), url('${dataUrl}'); font-weight: normal; font-style: normal; font-display: swap; }`;
      document.head.appendChild(styleEl);
    }
    const base64Idx = dataUrl.indexOf(',');
    if (base64Idx >= 0) {
      const bin = atob(dataUrl.slice(base64Idx + 1));
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) {
        bytes[i] = bin.charCodeAt(i);
      }
      const face = new FontFace(fontFamily, bytes.buffer);
      face.load().then((loaded) => document.fonts.add(loaded)).catch(() => {});
    }
  } catch {
    // ignore
  }
}

export default function App() {
  // Active Vault Mode ('primary' -> real_vault.db, 'decoy' -> decoy_vault.db)
  const [vaultMode, setVaultMode] = useState<VaultMode>('primary');
  const isVaultHydratedRef = useRef<boolean>(false);

  const [logs, setLogs] = useState<DiaryLog[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_LOGS_KEY);
      if (saved) {
        return JSON.parse(saved);
      }
    } catch {
      // ignore
    }
    return INITIAL_STARTER_LOGS;
  });

  const [customFonts, setCustomFonts] = useState<CustomFontItem[]>([]);

  const [micSettings, setMicSettings] = useState<MicRecordingSettings>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_MIC_SETTINGS_KEY);
      if (saved) {
        return JSON.parse(saved);
      }
    } catch {
      // ignore
    }
    return DEFAULT_MIC_SETTINGS;
  });

  const [darkMode, setDarkMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem(STORAGE_DARK_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const [savedPasscode, setSavedPasscode] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_PIN_KEY) || null;
    } catch {
      return null;
    }
  });

  // Covert Secondary PIN for Decoy Vault (`decoy_vault.db`), activated only when Primary Passcode is set and user holds "Set Passcode" for 10s in Primary Settings
  const [secondaryPasscode, setSecondaryPasscode] = useState<string | null>(() => {
    try {
      const primaryPin = localStorage.getItem(STORAGE_PIN_KEY);
      if (!primaryPin) {
        localStorage.removeItem(STORAGE_SECONDARY_PIN_KEY);
        return null;
      }
      return localStorage.getItem(STORAGE_SECONDARY_PIN_KEY) || null;
    } catch {
      return null;
    }
  });

  const [biometricsEnabled, setBiometricsEnabled] = useState<boolean>(() => {
    try {
      return localStorage.getItem(STORAGE_BIO_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const [savedKeyHash, setSavedKeyHash] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_ENC_HASH_KEY) || null;
    } catch {
      return null;
    }
  });

  // App starts LOCKED automatically whenever Primary Passcode is set
  const [isLocked, setIsLocked] = useState<boolean>(() => {
    try {
      return Boolean(localStorage.getItem(STORAGE_PIN_KEY));
    } catch {
      return false;
    }
  });

  const [route, setRoute] = useState<PageRoute>('home');
  const [editingLog, setEditingLog] = useState<DiaryLog | null>(null);
  const workspaceBackHandlerRef = useRef<(() => void) | null>(null);

  // Search bar state in Homepage Header
  const [isSearchOpen, setIsSearchOpen] = useState<boolean>(false);
  const [searchQuery, setSearchQuery] = useState<string>('');

  // 3-dots menu, Export modal, Delete Confirmation Modal & Locked Diary Prompt states
  const [openMenuLogId, setOpenMenuLogId] = useState<string | null>(null);
  const [exportingLog, setExportingLog] = useState<DiaryLog | null>(null);
  const [exportStatusBanner, setExportStatusBanner] = useState<string | null>(null);
  const [pendingDeleteLog, setPendingDeleteLog] = useState<DiaryLog | null>(null);
  const [pendingUnlockDiary, setPendingUnlockDiary] = useState<{
    log: DiaryLog;
    action: 'open' | 'export';
  } | null>(null);
  const [diaryUnlockInput, setDiaryUnlockInput] = useState<string>('');
  const [diaryUnlockError, setDiaryUnlockError] = useState<string | null>(null);
  const [diaryCooldownSec, setDiaryCooldownSec] = useState<number>(() =>
    getRemainingPasscodeCooldownSeconds()
  );

  // Active ringing Android notification alert banner
  const [ringingAlert, setRingingAlert] = useState<{
    id: string;
    heading: string;
    preview: string;
  } | null>(null);

  // Poll cooldown timer when Locked Diary modal is open
  useEffect(() => {
    if (!pendingUnlockDiary) return;
    const sync = () => setDiaryCooldownSec(getRemainingPasscodeCooldownSeconds());
    sync();
    const timer = window.setInterval(sync, 500);
    return () => window.clearInterval(timer);
  }, [pendingUnlockDiary]);

  // Automatically lock the app whenever it is closed, backgrounded, or hidden (if passcode is set)
  // AND dynamically toggle Android OS Anti-Screenshot / Anti-Screen-Recording (FLAG_SECURE) + Recent Apps Black/Blur Privacy Preview!
  useEffect(() => {
    const isProtected = Boolean(savedPasscode || secondaryPasscode);
    if (
      window.LikkhoNative &&
      typeof window.LikkhoNative.setAppPasscodeProtectionEnabled === 'function'
    ) {
      window.LikkhoNative.setAppPasscodeProtectionEnabled(isProtected);
    }

    if (!isProtected) return;

    const handleAppClosedOrBackgrounded = () => {
      if (document.visibilityState === 'hidden') {
        setIsLocked(true);
      }
    };

    const handlePageHide = () => {
      setIsLocked(true);
    };

    document.addEventListener('visibilitychange', handleAppClosedOrBackgrounded);
    window.addEventListener('pagehide', handlePageHide);
    return () => {
      document.removeEventListener('visibilitychange', handleAppClosedOrBackgrounded);
      window.removeEventListener('pagehide', handlePageHide);
    };
  }, [savedPasscode, secondaryPasscode]);

  // Hydrate isolated data whenever `vaultMode` changes ('primary' -> real_vault.db vs 'decoy' -> decoy_vault.db)
  useEffect(() => {
    isVaultHydratedRef.current = false;
    const logsStorageKey =
      vaultMode === 'decoy' ? STORAGE_DECOY_LOGS_KEY : STORAGE_LOGS_KEY;
    const idbLogsKey = vaultMode === 'decoy' ? IDB_DECOY_LOGS_KEY : IDB_LOGS_KEY;
    const idbFontsKey = vaultMode === 'decoy' ? IDB_DECOY_FONTS_KEY : IDB_FONTS_KEY;
    const encHashKey =
      vaultMode === 'decoy' ? STORAGE_DECOY_ENC_HASH_KEY : STORAGE_ENC_HASH_KEY;
    const micKey =
      vaultMode === 'decoy'
        ? STORAGE_DECOY_MIC_SETTINGS_KEY
        : STORAGE_MIC_SETTINGS_KEY;

    // 1. Load isolated encryption key hash & mic settings
    try {
      setSavedKeyHash(localStorage.getItem(encHashKey) || null);
    } catch {
      setSavedKeyHash(null);
    }

    try {
      const savedMic = localStorage.getItem(micKey);
      setMicSettings(savedMic ? JSON.parse(savedMic) : DEFAULT_MIC_SETTINGS);
    } catch {
      setMicSettings(DEFAULT_MIC_SETTINGS);
    }

    // 2. Load initial logs from isolated localStorage before IDB resolves
    let initialLocalLogs: DiaryLog[] =
      vaultMode === 'decoy' ? [] : INITIAL_STARTER_LOGS;
    try {
      const raw = localStorage.getItem(logsStorageKey);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          initialLocalLogs = parsed;
        }
      }
    } catch {
      // ignore
    }
    setLogs(initialLocalLogs);

    // 3. Load rich media logs & custom .ttf fonts from isolated IndexedDB database (`WikiLogPrivateSystemVaultDB` vs `WikiLogIsolatedDecoyVaultDB`)
    Promise.all([
      loadActiveAppStateFromIDB<DiaryLog[]>(idbLogsKey, vaultMode),
      loadActiveAppStateFromIDB<CustomFontItem[]>(idbFontsKey, vaultMode),
    ])
      .then(([idbLogs, idbFonts]) => {
        if (idbLogs && Array.isArray(idbLogs)) {
          if (idbLogs.length > 0 || vaultMode === 'decoy') {
            setLogs(idbLogs);
          }
        }
        if (idbFonts && Array.isArray(idbFonts)) {
          setCustomFonts(idbFonts);
          for (const f of idbFonts) {
            injectGlobalFontFaceCss(f.fontFamily, f.dataUrl);
          }
        } else {
          setCustomFonts([]);
        }
      })
      .finally(() => {
        isVaultHydratedRef.current = true;
      });
  }, [vaultMode]);

  // Sync Light & Dark Theme with DOM & Android OS Status Bar + Navigation Bar
  useEffect(() => {
    const rootEl = document.documentElement;
    const metaTheme = document.getElementById('meta-theme-color');
    const surfaceColor = darkMode ? '#1a1f24' : '#f8f9fa';
    const bgColor = darkMode ? '#101418' : '#ffffff';

    if (darkMode) {
      rootEl.classList.add('dark');
    } else {
      rootEl.classList.remove('dark');
    }
    rootEl.style.backgroundColor = bgColor;

    try {
      localStorage.setItem(STORAGE_DARK_KEY, String(darkMode));
    } catch {
      // ignore
    }

    if (metaTheme) {
      metaTheme.setAttribute('content', surfaceColor);
    }

    // Sync Android OS Status Bar (matches top header --wiki-surface) & Navigation Bar (matches bottom --wiki-bg)
    if (window.LikkhoNative && typeof window.LikkhoNative.setSystemBarsTheme === 'function') {
      window.LikkhoNative.setSystemBarsTheme(surfaceColor, bgColor, !darkMode);
    }
  }, [darkMode]);

  // Save in-app logs + media to isolated IndexedDB and localStorage for the active vault
  useEffect(() => {
    if (!isVaultHydratedRef.current) return;
    const logsStorageKey =
      vaultMode === 'decoy' ? STORAGE_DECOY_LOGS_KEY : STORAGE_LOGS_KEY;
    const idbLogsKey = vaultMode === 'decoy' ? IDB_DECOY_LOGS_KEY : IDB_LOGS_KEY;

    saveActiveAppStateToIDB(idbLogsKey, logs, vaultMode);
    try {
      localStorage.setItem(logsStorageKey, JSON.stringify(logs));
    } catch {
      // Large media gracefully stored in isolated IDB above
    }
  }, [logs, vaultMode]);

  // Save custom .ttf fonts to isolated IndexedDB for the active vault
  useEffect(() => {
    if (!isVaultHydratedRef.current) return;
    const idbFontsKey = vaultMode === 'decoy' ? IDB_DECOY_FONTS_KEY : IDB_FONTS_KEY;
    if (customFonts.length > 0) {
      saveActiveAppStateToIDB(idbFontsKey, customFonts, vaultMode);
    }
  }, [customFonts, vaultMode]);

  // Save mic recording settings for the active vault
  useEffect(() => {
    if (!isVaultHydratedRef.current) return;
    const micKey =
      vaultMode === 'decoy'
        ? STORAGE_DECOY_MIC_SETTINGS_KEY
        : STORAGE_MIC_SETTINGS_KEY;
    try {
      localStorage.setItem(micKey, JSON.stringify(micSettings));
    } catch {
      // ignore
    }
  }, [micSettings, vaultMode]);

  // Sync browser/Android Hardware Back Button:
  // "User agar App me homepage ke alawa kisi aur page par ho to back karne par pahle homepage par aayega fir back hoga"
  useEffect(() => {
    window.__handleLikkhoAndroidBack = () => {
      if (pendingUnlockDiary) {
        setPendingUnlockDiary(null);
        return 'HANDLED';
      }
      if (pendingDeleteLog) {
        setPendingDeleteLog(null);
        return 'HANDLED';
      }
      if (exportingLog) {
        setExportingLog(null);
        return 'HANDLED';
      }
      if (route === 'workspace' && workspaceBackHandlerRef.current) {
        workspaceBackHandlerRef.current();
        return 'HANDLED';
      }
      if (route !== 'home') {
        setRoute('home');
        setEditingLog(null);
        return 'HANDLED';
      }
      if (isSearchOpen) {
        setIsSearchOpen(false);
        setSearchQuery('');
        return 'HANDLED';
      }
      return 'EXIT';
    };

    const handlePopState = () => {
      if (pendingDeleteLog) {
        setPendingDeleteLog(null);
        window.history.pushState({ page: route }, '');
        return;
      }
      if (exportingLog) {
        setExportingLog(null);
        window.history.pushState({ page: route }, '');
        return;
      }
      if (route === 'workspace' && workspaceBackHandlerRef.current) {
        workspaceBackHandlerRef.current();
        return;
      }
      if (route !== 'home') {
        setRoute('home');
        setEditingLog(null);
      } else if (isSearchOpen) {
        setIsSearchOpen(false);
        setSearchQuery('');
      }
    };

    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, [route, isSearchOpen, exportingLog, pendingDeleteLog, pendingUnlockDiary]);

  const navigateTo = (target: PageRoute, logToEdit: DiaryLog | null = null) => {
    setOpenMenuLogId(null);
    if (target === 'home') {
      setRoute('home');
      setEditingLog(null);
      window.history.replaceState({ page: 'home' }, '');
    } else {
      if (route === 'home') {
        window.history.pushState({ page: target }, '');
      } else {
        window.history.replaceState({ page: target }, '');
      }
      setEditingLog(logToEdit);
      setRoute(target);
    }
  };

  // Poll for scheduled reminders inside the active vault
  useEffect(() => {
    const timer = window.setInterval(() => {
      const now = Date.now();
      let triggeredLog: DiaryLog | null = null;

      setLogs((prevLogs) =>
        prevLogs.map((item) => {
          if (
            item.reminderAt &&
            !item.reminderFired &&
            now >= item.reminderAt
          ) {
            triggeredLog = item;
            return { ...item, reminderFired: true };
          }
          return item;
        })
      );

      if (triggeredLog) {
        const alertLog = triggeredLog as DiaryLog;
        triggerSystemNotification(
          `Likkho: ${alertLog.heading}`,
          alertLog.plainPreview
        );
        setRingingAlert({
          id: alertLog.id,
          heading: alertLog.heading,
          preview: alertLog.plainPreview,
        });
      }
    }, 2500);

    return () => window.clearInterval(timer);
  }, []);

  const handleInstantLock = () => {
    if (!savedPasscode) {
      navigateTo('settings');
      return;
    }
    setIsLocked(true);
  };

  const handleUpsertLog = (updatedLog: DiaryLog, exitAfterSave: boolean) => {
    setLogs((prev) => {
      const exists = prev.some((l) => l.id === updatedLog.id);
      if (exists) {
        return prev.map((l) => (l.id === updatedLog.id ? updatedLog : l));
      }
      return [updatedLog, ...prev];
    });
    setEditingLog(updatedLog);
    if (exitAfterSave) {
      navigateTo('home');
    }
  };

  // Request confirmation popup before deleting ANY entry
  const requestDeleteLogConfirmation = (id: string) => {
    setOpenMenuLogId(null);
    const target = logs.find((l) => l.id === id) || editingLog;
    if (target) {
      setPendingDeleteLog(target);
    }
  };

  const confirmDeleteLog = () => {
    if (!pendingDeleteLog) return;
    const id = pendingDeleteLog.id;
    if (window.LikkhoNative?.cancelNativeReminder) {
      window.LikkhoNative.cancelNativeReminder(id);
    }
    setLogs((prev) => prev.filter((l) => l.id !== id));
    setPendingDeleteLog(null);
    if (route !== 'home') {
      navigateTo('home');
    }
  };

  const handleTogglePinLog = (id: string) => {
    setLogs((prev) =>
      prev.map((l) => (l.id === id ? { ...l, pinned: !l.pinned } : l))
    );
    setOpenMenuLogId(null);
  };

  const handleSelectExportFormat = async (format: ExportFormat) => {
    if (!exportingLog) return;
    const targetLog = exportingLog;
    setExportingLog(null);
    try {
      const msg = await exportDiaryLog(targetLog, format);
      setExportStatusBanner(msg);
      window.setTimeout(() => {
        setExportStatusBanner((prev) => (prev === msg ? null : prev));
      }, 3200);
    } catch {
      setExportStatusBanner('Export failed. Please check storage permissions.');
      window.setTimeout(() => setExportStatusBanner(null), 3000);
    }
  };

  // Filter logs by searched word across heading, full diary body text, preview, and date
  const trimmedQuery = searchQuery.trim().toLowerCase();
  const matchingLogs = logs.filter((log) => {
    if (!trimmedQuery) return true;
    const inHeading = log.heading.toLowerCase().includes(trimmedQuery);
    const inPreview = log.plainPreview.toLowerCase().includes(trimmedQuery);
    const inFullContent = extractFullPlainText(log.contentHtml).includes(trimmedQuery);
    const inDate = log.dateStamp.toLowerCase().includes(trimmedQuery);
    return inHeading || inPreview || inFullContent || inDate;
  });

  // Sort pinned logs to top, then by createdAt descending
  const sortedLogs = [...matchingLogs].sort((a, b) => {
    if (a.pinned && !b.pinned) return -1;
    if (!a.pinned && b.pinned) return 1;
    return b.createdAt - a.createdAt;
  });

  const effectivePrimaryPin = savedPasscode || secondaryPasscode || '';

  return (
    <div
      className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)] overflow-hidden"
      onClick={() => {
        if (openMenuLogId) setOpenMenuLogId(null);
      }}
    >
      {/* Export / Status Confirmation Banner */}
      {exportStatusBanner && (
        <div className="fixed bottom-20 left-4 right-4 z-50 mx-auto flex max-w-sm items-center gap-2 border border-[#14866d] bg-[var(--wiki-bg)] px-3.5 py-2.5 text-xs font-semibold text-[#14866d] shadow-xl">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span className="truncate">{exportStatusBanner}</span>
        </div>
      )}

      {/* Android Heads-Up Ringing Notification Banner */}
      {ringingAlert && (
        <div className="fixed left-3 right-3 top-3 z-50 mx-auto max-w-md border-2 border-[#3366cc] bg-[var(--wiki-bg)] p-3.5 shadow-2xl">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-start gap-2.5">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center bg-[#3366cc] text-white">
                <BellRing className="h-5 w-5 animate-bounce" />
              </div>
              <div>
                <h4 className="font-wiki-serif text-base font-bold text-[var(--wiki-text)]">
                  {ringingAlert.heading}
                </h4>
                <p className="line-clamp-1 text-xs text-[var(--wiki-muted)]">
                  {ringingAlert.preview}
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                soundManager.stopRing();
                setRingingAlert(null);
              }}
              className="flex h-8 items-center gap-1 bg-[#b32424] px-2.5 text-xs font-semibold text-white"
            >
              <VolumeX className="h-3.5 w-3.5" />
              Dismiss
            </button>
          </div>
        </div>
      )}

      {/* Instant Lock & Launch Lock Overlay — Routes to Primary (`real_vault.db`) or Secondary (`decoy_vault.db`) */}
      {isLocked && effectivePrimaryPin ? (
        <PasscodeScreen
          savedPasscode={savedPasscode || ''}
          secondaryPasscode={secondaryPasscode}
          biometricsEnabled={biometricsEnabled}
          onUnlock={(unlockedMode) => {
            setVaultMode(unlockedMode);
            setRoute('home');
            setEditingLog(null);
            setIsLocked(false);
          }}
        />
      ) : route === 'workspace' ? (
        <WritingWorkspace
          vaultMode={vaultMode}
          initialLog={editingLog}
          onSaveLog={handleUpsertLog}
          onDeleteLog={requestDeleteLogConfirmation}
          onExitWithoutSave={() => navigateTo('home')}
          customFonts={customFonts}
          onAddCustomFont={(font) => setCustomFonts((prev) => [...prev, font])}
          micSettings={micSettings}
          registerBackHandler={(fn) => {
            workspaceBackHandlerRef.current = fn;
          }}
        />
      ) : route === 'settings' ? (
        <SettingsPage
          vaultMode={vaultMode}
          darkMode={darkMode}
          onToggleDarkMode={() => setDarkMode((d) => !d)}
          savedPasscode={vaultMode === 'decoy' ? secondaryPasscode : savedPasscode}
          onUpdatePasscode={(pin) => {
            if (vaultMode === 'decoy') {
              if (pin && savedPasscode && pin === savedPasscode) {
                return false;
              }
              setSecondaryPasscode(pin);
              if (pin) {
                localStorage.setItem(STORAGE_SECONDARY_PIN_KEY, pin);
              } else {
                // Removing Secondary Passcode deactivates Secondary Vault and switches back to Primary Vault
                localStorage.removeItem(STORAGE_SECONDARY_PIN_KEY);
                setVaultMode('primary');
              }
              return true;
            } else {
              if (pin && secondaryPasscode && pin === secondaryPasscode) {
                return false;
              }
              setSavedPasscode(pin);
              if (pin) {
                localStorage.setItem(STORAGE_PIN_KEY, pin);
              } else {
                // Removing Primary Passcode also deactivates Secondary Vault immediately
                localStorage.removeItem(STORAGE_PIN_KEY);
                setSecondaryPasscode(null);
                localStorage.removeItem(STORAGE_SECONDARY_PIN_KEY);
                setVaultMode('primary');
                setIsLocked(false);
              }
              return true;
            }
          }}
          secondaryPasscode={secondaryPasscode}
          onUpdateSecondaryPasscode={(pin) => {
            if (!savedPasscode) {
              return false;
            }
            if (pin && pin === savedPasscode) {
              return false;
            }
            setSecondaryPasscode(pin);
            if (pin) {
              localStorage.setItem(STORAGE_SECONDARY_PIN_KEY, pin);
            } else {
              localStorage.removeItem(STORAGE_SECONDARY_PIN_KEY);
              if (vaultMode === 'decoy') {
                setVaultMode('primary');
              }
            }
            return true;
          }}
          biometricsEnabled={biometricsEnabled}
          onToggleBiometrics={(enabled) => {
            setBiometricsEnabled(enabled);
            try {
              localStorage.setItem(STORAGE_BIO_KEY, String(enabled));
            } catch {
              // ignore
            }
          }}
          onOpenBackupRestore={() => navigateTo('backup')}
          onBackToHome={() => navigateTo('home')}
          micSettings={micSettings}
          onUpdateMicSettings={(next) => setMicSettings(next)}
        />
      ) : route === 'backup' ? (
        <BackupRestorePage
          vaultMode={vaultMode}
          logs={logs}
          customFonts={customFonts}
          micSettings={micSettings}
          savedKeyHash={savedKeyHash}
          onSaveKeyHash={(hash) => {
            setSavedKeyHash(hash);
            const encHashKey =
              vaultMode === 'decoy'
                ? STORAGE_DECOY_ENC_HASH_KEY
                : STORAGE_ENC_HASH_KEY;
            localStorage.setItem(encHashKey, hash);
          }}
          onRestoreBundle={async (bundle) => {
            setLogs(bundle.logs);
            if (bundle.customFonts && bundle.customFonts.length > 0) {
              setCustomFonts(bundle.customFonts);
              for (const f of bundle.customFonts) {
                injectGlobalFontFaceCss(f.fontFamily, f.dataUrl);
              }
            }
            if (bundle.micSettings) {
              setMicSettings(bundle.micSettings);
            }
          }}
          onBackToHome={() => navigateTo('home')}
        />
      ) : (
        /* HOMEPAGE VIEW — No bar or buttons between Header and Logs */
        <div className="relative flex h-full w-full flex-col bg-[var(--wiki-bg)]">
          {/* Header: Left App Name ("Likkho" — 10s Hold Triggers Covert Secondary Vault Setup) | Right Search Button + Instant Lock + Hamburger Menu */}
          <header className="flex h-14 shrink-0 items-center justify-between gap-2 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
            {isSearchOpen ? (
              <div className="flex flex-1 items-center gap-2 min-w-0">
                <Search className="h-4 w-4 shrink-0 text-[#3366cc]" />
                <input
                  type="search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search diaries..."
                  className="h-9 min-w-0 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                  autoFocus
                />
                <button
                  type="button"
                  onClick={() => {
                    setIsSearchOpen(false);
                    setSearchQuery('');
                  }}
                  className="flex h-9 w-9 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
                  title="Close search"
                  aria-label="Close search"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <span className="font-wiki-serif text-2xl font-bold tracking-tight text-[var(--wiki-text)] select-none">
                Likkho
              </span>
            )}

            <div className="flex shrink-0 items-center gap-1.5">
              {!isSearchOpen && (
                <button
                  type="button"
                  onClick={() => setIsSearchOpen(true)}
                  className="flex h-10 w-10 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc] active:scale-[0.98] transition-all"
                  title="Search diaries"
                  aria-label="Search diaries"
                >
                  <Search className="h-4 w-4" />
                </button>
              )}

              <button
                type="button"
                onClick={handleInstantLock}
                className="flex h-10 items-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 text-xs font-semibold text-[var(--wiki-text)] hover:border-[#3366cc] active:scale-[0.98] transition-all whitespace-nowrap"
                title="Instant Lock"
                aria-label="Instant Lock"
              >
                <Lock className="h-4 w-4 text-[#3366cc]" />
                <span>Lock</span>
              </button>

              <button
                type="button"
                onClick={() => navigateTo('settings')}
                className="flex h-10 w-10 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc] active:scale-[0.98] transition-all"
                title="Settings"
                aria-label="Open Settings"
              >
                <Menu className="h-5 w-5" />
              </button>
            </div>
          </header>

          {/* Directly Logs List below Header */}
          <main className="flex-1 overflow-y-auto divide-y divide-[var(--wiki-hairline)] pb-24">
            {sortedLogs.length === 0 ? (
              <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
                <div className="flex h-12 w-12 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                  <BookOpen className="h-6 w-6 text-[var(--wiki-muted)]" />
                </div>
                <h2 className="mt-4 font-wiki-serif text-xl font-bold">
                  {trimmedQuery ? 'No Matching Diaries Found' : 'No Logs Yet'}
                </h2>
                <p className="mt-1 max-w-xs text-xs leading-relaxed text-[var(--wiki-muted)]">
                  {trimmedQuery
                    ? `No diary entry contains "${searchQuery}".`
                    : 'Tap the create button below to start writing.'}
                </p>
              </div>
            ) : (
              sortedLogs.map((log) => {
                const avatarSrc = log.pfpDataUrl || getFallbackMonographPfp();
                const isMenuOpen = openMenuLogId === log.id;

                return (
                  <article
                    key={log.id}
                    onClick={() => {
                      if (log.diaryLockPin) {
                        setPendingUnlockDiary({ log, action: 'open' });
                        setDiaryUnlockInput('');
                        setDiaryUnlockError(null);
                      } else {
                        navigateTo('workspace', log);
                      }
                    }}
                    className="group relative flex cursor-pointer items-center gap-3.5 px-4 py-3.5 hover:bg-[var(--wiki-surface)] active:bg-[var(--wiki-hairline)] transition-colors"
                  >
                    {/* 1:1 Square PFP */}
                    <div className="h-13 w-13 shrink-0 overflow-hidden border border-[var(--wiki-border)] bg-white">
                      <img
                        src={avatarSrc}
                        alt={log.heading}
                        referrerPolicy="no-referrer"
                        className="h-full w-full object-cover aspect-square"
                      />
                    </div>

                    {/* Log Details: Heading (+ Lock Icon if locked), 1-line preview (empty if no content), Created & Modified stamps */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5">
                        {log.pinned && (
                          <Pin className="h-3.5 w-3.5 shrink-0 text-[#3366cc] fill-[#3366cc]" />
                        )}
                        {log.diaryLockPin && (
                          <Lock
                            className="h-3.5 w-3.5 shrink-0 text-[#b32424]"
                            title="Locked Diary"
                          />
                        )}
                        <h2 className="font-wiki-serif text-lg font-bold leading-snug text-[var(--wiki-text)] group-hover:text-[#3366cc] truncate">
                          {log.heading}
                        </h2>
                      </div>

                      <p className="mt-0.5 min-h-[1rem] truncate font-wiki-prose text-xs text-[var(--wiki-muted)]">
                        {log.diaryLockPin
                          ? ''
                          : log.plainPreview === 'Reminder scheduled.'
                          ? ''
                          : log.plainPreview}
                      </p>

                      <div className="mt-1.5 flex flex-col gap-0.5 font-wiki-mono text-[10px] text-[var(--wiki-muted)]">
                        <div className="flex flex-wrap items-center gap-1">
                          <span className="font-semibold text-[var(--wiki-text)]/80">Created:</span>
                          <span>
                            {log.dateStamp ||
                              new Date(log.createdAt).toLocaleDateString('en-IN', {
                                day: '2-digit',
                                month: 'short',
                                year: 'numeric',
                              })}
                          </span>
                          <span aria-hidden="true">·</span>
                          <span>
                            {log.timeStamp ||
                              new Date(log.createdAt).toLocaleTimeString('en-IN', {
                                hour: '2-digit',
                                minute: '2-digit',
                                hour12: true,
                              })}
                          </span>
                        </div>

                        <div className="flex flex-wrap items-center gap-1">
                          <span className="font-semibold text-[var(--wiki-text)]/80">Modified:</span>
                          <span>
                            {log.updatedDateStamp ||
                              new Date(log.updatedAt || log.createdAt).toLocaleDateString(
                                'en-IN',
                                {
                                  day: '2-digit',
                                  month: 'short',
                                  year: 'numeric',
                                }
                              )}
                          </span>
                          <span aria-hidden="true">·</span>
                          <span>
                            {log.updatedTimeStamp ||
                              new Date(log.updatedAt || log.createdAt).toLocaleTimeString(
                                'en-IN',
                                {
                                  hour: '2-digit',
                                  minute: '2-digit',
                                  hour12: true,
                                }
                              )}
                          </span>

                          {log.reminderAt && log.reminderAt > Date.now() && (
                            <>
                              <span aria-hidden="true">·</span>
                              <span className="flex items-center gap-1 text-[#3366cc]">
                                <BellRing className="h-3 w-3" />
                                {new Date(log.reminderAt).toLocaleTimeString('en-IN', {
                                  hour: '2-digit',
                                  minute: '2-digit',
                                  hour12: true,
                                })}
                              </span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* 3 Dots Action Button */}
                    <div
                      className="relative shrink-0"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        type="button"
                        onClick={() =>
                          setOpenMenuLogId((prev) => (prev === log.id ? null : log.id))
                        }
                        className="flex h-10 w-10 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)] hover:text-[var(--wiki-text)]"
                        aria-label="Log options"
                      >
                        <MoreVertical className="h-5 w-5" />
                      </button>

                      {/* 3-Dots Dropdown Menu: Pin to Top, Export, Delete */}
                      {isMenuOpen && (
                        <div className="absolute right-0 top-11 z-40 w-44 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] py-1 shadow-xl">
                          <button
                            type="button"
                            onClick={() => handleTogglePinLog(log.id)}
                            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)]"
                          >
                            <Pin className="h-4 w-4 text-[#3366cc]" />
                            <span>{log.pinned ? 'Unpin from Top' : 'Pin to Top'}</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              setOpenMenuLogId(null);
                              if (log.diaryLockPin) {
                                setPendingUnlockDiary({ log, action: 'export' });
                                setDiaryUnlockInput('');
                                setDiaryUnlockError(null);
                              } else {
                                setExportingLog(log);
                              }
                            }}
                            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)]"
                          >
                            <Download className="h-4 w-4 text-[#3366cc]" />
                            <span>Export</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => requestDeleteLogConfirmation(log.id)}
                            className="flex w-full items-center gap-2.5 border-t border-[var(--wiki-hairline)] px-3.5 py-2.5 text-left text-xs font-medium text-[#b32424] hover:bg-[var(--wiki-surface)]"
                          >
                            <Trash2 className="h-4 w-4" />
                            <span>Delete</span>
                          </button>
                        </div>
                      )}
                    </div>
                  </article>
                );
              })
            )}
          </main>

          {/* Floating Create Button */}
          <button
            type="button"
            onClick={() => navigateTo('workspace', null)}
            className="fixed bottom-6 right-5 z-30 flex h-14 items-center gap-2 border border-[#2a56b0] bg-[#3366cc] px-5 text-sm font-semibold text-white shadow-lg hover:bg-[#2a56b0] active:scale-95 transition-transform"
            aria-label="Create new diary log"
          >
            <Plus className="h-5 w-5" />
            <span className="font-wiki-serif text-base tracking-wide">Create</span>
          </button>
        </div>
      )}

      {/* Individual Locked Diary Passcode Verification Modal (with Progressive Cooldown) */}
      {pendingUnlockDiary && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs"
          onClick={() => setPendingUnlockDiary(null)}
        >
          <div
            className="w-full max-w-xs border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2 min-w-0">
                <Lock className="h-4 w-4 shrink-0 text-[#b32424]" />
                <h3 className="font-wiki-serif text-base font-bold truncate">
                  Unlock "{pendingUnlockDiary.log.heading}"
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPendingUnlockDiary(null)}
                className="flex h-7 w-7 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                const rem = getRemainingPasscodeCooldownSeconds();
                if (rem > 0) {
                  setDiaryCooldownSec(rem);
                  setDiaryUnlockError(`Too many wrong attempts. Wait ${rem}s.`);
                  return;
                }
                if (diaryUnlockInput.trim() === pendingUnlockDiary.log.diaryLockPin) {
                  resetPasscodeFailures();
                  setDiaryCooldownSec(0);
                  const targetLog = pendingUnlockDiary.log;
                  const act = pendingUnlockDiary.action;
                  setPendingUnlockDiary(null);
                  setDiaryUnlockInput('');
                  setDiaryUnlockError(null);
                  if (act === 'open') {
                    navigateTo('workspace', targetLog);
                  } else {
                    setExportingLog(targetLog);
                  }
                } else {
                  const fail = recordPasscodeFailure();
                  if (fail.cooldownSeconds > 0) {
                    setDiaryCooldownSec(fail.cooldownSeconds);
                    setDiaryUnlockError(
                      `Incorrect passcode. Locked for ${fail.cooldownSeconds}s.`
                    );
                  } else {
                    setDiaryUnlockError('Incorrect diary passcode.');
                  }
                }
              }}
              className="p-4 space-y-3"
            >
              {diaryCooldownSec > 0 ? (
                <p className="text-xs font-semibold text-[#b32424]">
                  Locked for {diaryCooldownSec}s due to continuous wrong attempts.
                </p>
              ) : (
                diaryUnlockError && (
                  <p className="text-xs font-medium text-[#b32424]">
                    {diaryUnlockError}
                  </p>
                )
              )}
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                  Enter Diary Passcode
                </label>
                <input
                  type="password"
                  value={diaryUnlockInput}
                  disabled={diaryCooldownSec > 0}
                  onChange={(e) => {
                    setDiaryUnlockInput(e.target.value);
                    setDiaryUnlockError(null);
                  }}
                  placeholder="Passcode..."
                  className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc] disabled:opacity-50"
                  autoFocus
                />
              </div>
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setPendingUnlockDiary(null)}
                  className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-medium text-[var(--wiki-text)]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={diaryCooldownSec > 0}
                  className="h-9 bg-[#3366cc] px-4 text-xs font-semibold text-white disabled:opacity-50"
                >
                  {diaryCooldownSec > 0 ? `Wait ${diaryCooldownSec}s` : 'Unlock'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Entry Confirmation Pop-Up Modal */}
      {pendingDeleteLog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs"
          onClick={() => setPendingDeleteLog(null)}
        >
          <div
            className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 text-[#b32424]" />
                <h3 className="font-wiki-serif text-lg font-bold">
                  Delete Entry?
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPendingDeleteLog(null)}
                className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-4">
              <p className="text-xs leading-relaxed text-[var(--wiki-text)]">
                Are you sure you want to permanently delete{' '}
                <strong>"{pendingDeleteLog.heading}"</strong>? This action cannot be undone.
              </p>

              <div className="mt-4 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setPendingDeleteLog(null)}
                  className="h-10 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 text-xs font-medium text-[var(--wiki-text)]"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={confirmDeleteLog}
                  className="flex h-10 items-center gap-1.5 bg-[#b32424] px-4 text-xs font-semibold text-white hover:bg-[#941d1d]"
                >
                  <Trash2 className="h-4 w-4" />
                  Delete
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Multi-Format Export Modal */}
      {exportingLog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs"
          onClick={() => setExportingLog(null)}
        >
          <div
            className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2 min-w-0">
                <FileDown className="h-4 w-4 shrink-0 text-[#3366cc]" />
                <h3 className="font-wiki-serif text-lg font-bold truncate">
                  Export "{exportingLog.heading}"
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setExportingLog(null)}
                className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-4">
              <p className="mb-3 text-xs text-[var(--wiki-muted)]">
                Choose a file format to save to <strong>Downloads/Likkho</strong>:
              </p>
              <div className="grid grid-cols-2 gap-2">
                {EXPORT_FORMATS.map((item) => (
                  <button
                    key={item.ext}
                    type="button"
                    onClick={() => handleSelectExportFormat(item.ext)}
                    className="flex flex-col items-start border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-2.5 text-left hover:border-[#3366cc] hover:bg-[var(--wiki-bg)] transition-colors"
                  >
                    <span className="font-wiki-mono text-xs font-bold text-[#3366cc]">
                      {item.label}
                    </span>
                    <span className="mt-0.5 text-[10px] text-[var(--wiki-muted)]">
                      {item.desc}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

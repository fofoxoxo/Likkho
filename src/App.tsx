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
} from 'lucide-react';
import {
  AppThemeMode,
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  loadActiveAppStateFromIDB,
  saveActiveAppStateToIDB,
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

const STORAGE_LOGS_KEY = 'wikilog_in_app_logs_v1';
const STORAGE_THEME_KEY = 'wikilog_theme_mode_v2';
const STORAGE_DARK_KEY = 'wikilog_dark_theme_v1';
const STORAGE_PIN_KEY = 'wikilog_passcode_v1';
const STORAGE_BIO_KEY = 'wikilog_biometrics_enabled_v1';
const STORAGE_ENC_HASH_KEY = 'wikilog_encryption_key_hash_v1';
const STORAGE_MIC_SETTINGS_KEY = 'wikilog_mic_settings_v1';
const IDB_LOGS_KEY = 'active_diary_logs_with_media';
const IDB_FONTS_KEY = 'active_custom_ttf_fonts';

const THEME_BAR_PALETTE: Record<
  Exclude<AppThemeMode, 'system'>,
  { bg: string; surface: string; isDark: boolean }
> = {
  light: { bg: '#ffffff', surface: '#f8f9fa', isDark: false },
  amoled: { bg: '#000000', surface: '#0a0a0a', isDark: true },
  dark_charcoal: { bg: '#101418', surface: '#1a1f24', isDark: true },
  grayscale: { bg: '#e5e7eb', surface: '#d1d5db', isDark: false },
  eink: { bg: '#f6f3e9', surface: '#ece7d8', isDark: false },
  tinted_cool: { bg: '#eef3f9', surface: '#e1eaf4', isDark: false },
  tinted_warm: { bg: '#f9f2ea', surface: '#f0e4d6', isDark: false },
};

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

  const [themeMode, setThemeMode] = useState<AppThemeMode>(() => {
    try {
      const savedV2 = localStorage.getItem(STORAGE_THEME_KEY) as AppThemeMode | null;
      if (savedV2 && (savedV2 === 'system' || savedV2 in THEME_BAR_PALETTE)) {
        return savedV2;
      }
      const legacyDark = localStorage.getItem(STORAGE_DARK_KEY);
      if (legacyDark === 'true') return 'dark_charcoal';
      if (legacyDark === 'false') return 'light';
    } catch {
      // ignore
    }
    return 'system';
  });

  const [savedPasscode, setSavedPasscode] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_PIN_KEY) || null;
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

  // App starts LOCKED automatically whenever a Passcode is set (when app is closed & reopened)
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

  // Active ringing Android notification alert banner
  const [ringingAlert, setRingingAlert] = useState<{
    id: string;
    heading: string;
    preview: string;
  } | null>(null);

  // Automatically lock the app whenever it is closed, backgrounded, or hidden (if passcode is set)
  useEffect(() => {
    if (!savedPasscode) return;

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
  }, [savedPasscode]);

  // Load rich media logs & custom .ttf fonts from IndexedDB on initial mount
  useEffect(() => {
    loadActiveAppStateFromIDB<DiaryLog[]>(IDB_LOGS_KEY).then((idbLogs) => {
      if (idbLogs && Array.isArray(idbLogs) && idbLogs.length > 0) {
        setLogs(idbLogs);
      }
    });

    loadActiveAppStateFromIDB<CustomFontItem[]>(IDB_FONTS_KEY).then(
      async (idbFonts) => {
        if (idbFonts && Array.isArray(idbFonts) && idbFonts.length > 0) {
          setCustomFonts(idbFonts);
          for (const f of idbFonts) {
            injectGlobalFontFaceCss(f.fontFamily, f.dataUrl);
          }
        }
      }
    );
  }, []);

  // Sync Selected Theme (including System Default) with DOM & Android OS Status Bar + Navigation Bar
  useEffect(() => {
    const applyResolvedTheme = () => {
      const rootEl = document.documentElement;
      const metaTheme = document.getElementById('meta-theme-color');
      const isSystemDark =
        typeof window.matchMedia === 'function' &&
        window.matchMedia('(prefers-color-scheme: dark)').matches;

      const resolvedTheme: Exclude<AppThemeMode, 'system'> =
        themeMode === 'system'
          ? isSystemDark
            ? 'dark_charcoal'
            : 'light'
          : themeMode;

      const palette = THEME_BAR_PALETTE[resolvedTheme] || THEME_BAR_PALETTE.light;

      rootEl.setAttribute('data-theme', resolvedTheme);
      rootEl.style.backgroundColor = palette.bg;

      if (palette.isDark) {
        rootEl.classList.add('dark');
      } else {
        rootEl.classList.remove('dark');
      }

      if (metaTheme) {
        metaTheme.setAttribute('content', palette.surface);
      }

      // Sync Android OS Status Bar (matches top header --wiki-surface) & Navigation Bar (matches bottom --wiki-bg)
      if (window.LikkhoNative && typeof window.LikkhoNative.setSystemBarsTheme === 'function') {
        window.LikkhoNative.setSystemBarsTheme(
          palette.surface,
          palette.bg,
          !palette.isDark
        );
      }
    };

    applyResolvedTheme();

    try {
      localStorage.setItem(STORAGE_THEME_KEY, themeMode);
      localStorage.setItem(
        STORAGE_DARK_KEY,
        String(themeMode === 'dark_charcoal' || themeMode === 'amoled')
      );
    } catch {
      // ignore
    }

    if (themeMode === 'system' && typeof window.matchMedia === 'function') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      const listener = () => applyResolvedTheme();
      mq.addEventListener('change', listener);
      return () => mq.removeEventListener('change', listener);
    }
  }, [themeMode]);

  // Save in-app logs + media to IndexedDB and localStorage
  useEffect(() => {
    saveActiveAppStateToIDB(IDB_LOGS_KEY, logs);
    try {
      localStorage.setItem(STORAGE_LOGS_KEY, JSON.stringify(logs));
    } catch {
      // Large media gracefully stored in IDB_LOGS_KEY above
    }
  }, [logs]);

  // Save custom .ttf fonts to IndexedDB
  useEffect(() => {
    if (customFonts.length > 0) {
      saveActiveAppStateToIDB(IDB_FONTS_KEY, customFonts);
    }
  }, [customFonts]);

  // Save mic recording settings
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_MIC_SETTINGS_KEY, JSON.stringify(micSettings));
    } catch {
      // ignore
    }
  }, [micSettings]);

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
  }, [route, isSearchOpen, exportingLog, pendingDeleteLog]);

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

  // Poll for scheduled reminders
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

  return (
    <div
      className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)] overflow-hidden"
      onClick={() => {
        if (openMenuLogId) setOpenMenuLogId(null);
      }}
    >
      {/* Export Confirmation Banner */}
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

      {/* Instant Lock & Launch Lock Overlay */}
      {isLocked && savedPasscode ? (
        <PasscodeScreen
          savedPasscode={savedPasscode}
          biometricsEnabled={biometricsEnabled}
          onUnlock={() => setIsLocked(false)}
        />
      ) : route === 'workspace' ? (
        <WritingWorkspace
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
          themeMode={themeMode}
          onChangeThemeMode={(mode) => setThemeMode(mode)}
          savedPasscode={savedPasscode}
          onUpdatePasscode={(pin) => {
            setSavedPasscode(pin);
            if (pin) {
              localStorage.setItem(STORAGE_PIN_KEY, pin);
            } else {
              localStorage.removeItem(STORAGE_PIN_KEY);
              setIsLocked(false);
            }
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
          logs={logs}
          customFonts={customFonts}
          micSettings={micSettings}
          savedKeyHash={savedKeyHash}
          onSaveKeyHash={(hash) => {
            setSavedKeyHash(hash);
            localStorage.setItem(STORAGE_ENC_HASH_KEY, hash);
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
          {/* Header: Left App Name ("Likkho") | Right Search Button + Instant Lock + Hamburger Menu */}
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
              <span className="font-wiki-serif text-2xl font-bold tracking-tight text-[var(--wiki-text)]">
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

      {/* Individual Locked Diary Passcode Verification Modal */}
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
                if (diaryUnlockInput.trim() === pendingUnlockDiary.log.diaryLockPin) {
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
                  setDiaryUnlockError('Incorrect diary passcode.');
                }
              }}
              className="p-4 space-y-3"
            >
              {diaryUnlockError && (
                <p className="text-xs font-medium text-[#b32424]">
                  {diaryUnlockError}
                </p>
              )}
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                  Enter Diary Passcode
                </label>
                <input
                  type="password"
                  value={diaryUnlockInput}
                  onChange={(e) => {
                    setDiaryUnlockInput(e.target.value);
                    setDiaryUnlockError(null);
                  }}
                  placeholder="Passcode..."
                  className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
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
                  className="h-9 bg-[#3366cc] px-4 text-xs font-semibold text-white"
                >
                  Unlock
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

import { useEffect, useRef, useState } from 'react';
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
  Loader2,
  AlertTriangle,
  Share2,
} from 'lucide-react';
import {
  CanvasAudioAttachment,
  CanvasDraggableImage,
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  VaultMode,
  loadActiveAppStateFromIDB,
  saveActiveAppStateToIDB,
  clearAllAppVaultData,
  getRemainingPasscodeCooldownSeconds,
  recordPasscodeFailure,
  resetPasscodeFailures,
  isExternalFilePickerPending,
  clearExternalFilePickerPending,
  markExternalFilePickerPending,
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
import { persistAudioAttachment } from './utils/audioStorage';
import {
  shareDiaryAsLikkhoArchive,
  unpackLikkhoBase64Payload,
} from './utils/likkhoShareArchive';
import {
  getImageObjectUrl,
  getCanvasPreviewUrl,
  preloadLogImages,
  migrateLogsMediaToBlobStore,
  dataUrlToBlob,
  processImageFileToBlob,
} from './utils/imageStorage';
import { AppUpdateModal } from './components/AppUpdateModal';
import { checkForAppUpdate, AppUpdateInfo } from './utils/appUpdateChecker';
import { QuickNoteCreatorModal } from './components/QuickNoteCreatorModal';
import {
  getQuickNotes,
  saveQuickNotes,
  markQuickNoteDone,
  syncQuickNoteToNotification,
  syncActiveNoteToHomeWidget,
} from './utils/quickNotesStorage';
import {
  saveAppSpecificInternalFile,
  readAppSpecificInternalFile,
  deleteAppSpecificInternalFile,
} from './utils/internalEncryptedStorage';
import { runLegacyWebViewMigration } from './utils/legacyWebMigration';

type PageRoute = 'home' | 'workspace' | 'settings' | 'backup';

// Primary Vault (`real_vault.db`) Storage Keys
const STORAGE_LOGS_KEY = 'wikilog_in_app_logs_v1';
const STORAGE_DARK_KEY = 'wikilog_dark_theme_v1';
const STORAGE_FULLSCREEN_KEY = 'wikilog_fullscreen_mode_v1';
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

const STARTER_LOG_IDS = new Set([
  'log_starter_1',
  'log_starter_welcome_guide_v1',
]);

function stripStarterGuideLogs(list: DiaryLog[]): DiaryLog[] {
  return list.filter((item) => !STARTER_LOG_IDS.has(item.id));
}

function detectQuickNoteLaunchSync(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const urlParams = new URLSearchParams(window.location.search);
    if (
      urlParams.get('action') === 'quick_note' ||
      window.location.hash.includes('quick_note')
    ) {
      return true;
    }
    if (
      window.LikkhoNative &&
      typeof window.LikkhoNative.hasPendingQuickNoteAction === 'function' &&
      window.LikkhoNative.hasPendingQuickNoteAction()
    ) {
      return true;
    }
  } catch {
    // ignore
  }
  return false;
}

interface IncomingSharePayload {
  title: string;
  text: string;
  imageDataUrl: string;
}

function convertPlainTextToParagraphHtml(rawText: string): string {
  const trimmed = (rawText || '').trim();
  if (!trimmed) return '';
  const lines = trimmed.split(/\r?\n/);
  const parts: string[] = [];
  for (const line of lines) {
    const p = document.createElement('p');
    p.textContent = line.length > 0 ? line : '\u00A0';
    parts.push(p.outerHTML);
  }
  return parts.join('');
}

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

interface NativeCanvasLaunchPayload {
  vaultMode: VaultMode;
  isDarkTheme: boolean;
  startInEditMode: boolean;
  log: DiaryLog | null;
  customFonts: CustomFontItem[];
  micSettings: MicRecordingSettings;
  wordAnalysesJson?: string;
}

function detectRuntimeShellMode(): 'migrate' | 'canvas' | 'web' {
  if (typeof window === 'undefined') return 'web';
  try {
    const params = new URLSearchParams(window.location.search);
    const modeParam = params.get('mode');
    if (modeParam === 'migrate' || Boolean(window.LikkhoLegacyMigrationBridge)) {
      return 'migrate';
    }
    if (modeParam === 'canvas' || Boolean(window.LikkhoCanvasBridge)) {
      return 'canvas';
    }
  } catch {
    // ignore
  }
  return 'web';
}

function LegacyMigrationShell() {
  useEffect(() => {
    runLegacyWebViewMigration();
  }, []);
  return <div className="h-full w-full bg-[var(--wiki-bg)]" />;
}

function CanvasOnlyShell() {
  const [payload, setPayload] = useState<NativeCanvasLaunchPayload>(() => {
    let initialDark = false;
    try {
      const params = new URLSearchParams(window.location.search);
      initialDark = params.get('dark') === '1';
    } catch {
      // ignore
    }
    return {
      vaultMode: 'primary',
      isDarkTheme: initialDark,
      startInEditMode: false,
      log: null,
      customFonts: [],
      micSettings: DEFAULT_MIC_SETTINGS,
    };
  });
  const [customFonts, setCustomFonts] = useState<CustomFontItem[]>([]);
  const [darkMode, setDarkMode] = useState<boolean>(payload.isDarkTheme);
  const [sessionSeq, setSessionSeq] = useState<number>(0);
  const [hasLoadedPayload, setHasLoadedPayload] = useState<boolean>(false);
  const backHandlerRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    if (darkMode) {
      root.classList.add('dark');
      root.style.backgroundColor = '#101418';
      root.style.colorScheme = 'dark';
    } else {
      root.classList.remove('dark');
      root.style.backgroundColor = '#ffffff';
      root.style.colorScheme = 'light';
    }
  }, [darkMode]);

  useEffect(() => {
    const applyIncomingPayload = (rawJson: string) => {
      if (!rawJson || !rawJson.trim()) return;
      try {
        const parsed = JSON.parse(rawJson) as Partial<NativeCanvasLaunchPayload>;
        const mode: VaultMode = parsed.vaultMode === 'decoy' ? 'decoy' : 'primary';
        const fonts = Array.isArray(parsed.customFonts) ? parsed.customFonts : [];
        for (const f of fonts) {
          if (f?.fontFamily && f?.dataUrl) {
            injectGlobalFontFaceCss(f.fontFamily, f.dataUrl);
          }
        }
        if (typeof parsed.wordAnalysesJson === 'string' && parsed.wordAnalysesJson.trim()) {
          const storageKey =
            mode === 'decoy'
              ? 'wikilog_decoy_v2_saved_word_analyses'
              : 'wikilog_v1_saved_word_analyses';
          try {
            localStorage.setItem(storageKey, parsed.wordAnalysesJson);
          } catch {
            // ignore
          }
        }
        setCustomFonts(fonts);
        if (typeof parsed.isDarkTheme === 'boolean') {
          setDarkMode(parsed.isDarkTheme);
        }
        setPayload({
          vaultMode: mode,
          isDarkTheme: Boolean(parsed.isDarkTheme),
          startInEditMode: Boolean(parsed.startInEditMode),
          log: parsed.log || null,
          customFonts: fonts,
          micSettings: parsed.micSettings || DEFAULT_MIC_SETTINGS,
          wordAnalysesJson: parsed.wordAnalysesJson,
        });
        setSessionSeq((s) => s + 1);
        setHasLoadedPayload(true);
      } catch (err) {
        console.error('Failed to parse native canvas payload:', err);
      }
    };

    window.__likkhoCanvasLoad = applyIncomingPayload;
    window.__likkhoCanvasSetTheme = (isDark: boolean) => {
      setDarkMode(Boolean(isDark));
    };
    window.__handleLikkhoAndroidBack = () => {
      if (backHandlerRef.current) {
        backHandlerRef.current();
      } else {
        window.LikkhoCanvasBridge?.exitWithoutSave?.();
      }
      return 'handled';
    };

    try {
      const initialRaw = window.LikkhoCanvasBridge?.consumeInitialCanvasPayload?.();
      if (initialRaw && initialRaw.trim().length > 0) {
        applyIncomingPayload(initialRaw);
      }
      window.LikkhoCanvasBridge?.onCanvasReady?.();
    } catch {
      // ignore
    }

    return () => {
      window.__likkhoCanvasLoad = undefined;
      window.__likkhoCanvasSetTheme = undefined;
      window.__handleLikkhoAndroidBack = undefined;
    };
  }, []);

  if (!hasLoadedPayload) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
        <Loader2 className="h-6 w-6 animate-spin text-[#3366cc]" />
      </div>
    );
  }

  return (
    <div className="h-full w-full bg-[var(--wiki-bg)] text-[var(--wiki-text)] font-wiki-sans">
      <WritingWorkspace
        key={`${payload.vaultMode}_${payload.log?.id || 'new'}_${sessionSeq}`}
        canvasOnly={true}
        vaultMode={payload.vaultMode}
        initialLog={payload.log}
        startInEditMode={payload.startInEditMode}
        customFonts={customFonts}
        micSettings={payload.micSettings}
        registerBackHandler={(fn) => {
          backHandlerRef.current = fn;
        }}
        onAddCustomFont={(font) => {
          injectGlobalFontFaceCss(font.fontFamily, font.dataUrl);
          setCustomFonts((prev) => [...prev, font]);
          try {
            window.LikkhoCanvasBridge?.onAddCustomFontJson?.(JSON.stringify(font));
          } catch {
            // ignore
          }
        }}
        onSaveLog={(savedLog, exitAfterSave) => {
          try {
            window.LikkhoCanvasBridge?.saveLogJson?.(
              JSON.stringify(savedLog),
              exitAfterSave
            );
          } catch (err) {
            console.error('Failed to save log across LikkhoCanvasBridge:', err);
          }
        }}
        onDeleteLog={(logId) => {
          try {
            window.LikkhoCanvasBridge?.deleteLog?.(logId);
          } catch {
            // ignore
          }
        }}
        onExitWithoutSave={() => {
          try {
            window.LikkhoCanvasBridge?.exitWithoutSave?.();
          } catch {
            // ignore
          }
        }}
      />
    </div>
  );
}

export default function App() {
  const mode = detectRuntimeShellMode();
  if (mode === 'migrate') return <LegacyMigrationShell />;
  if (mode === 'canvas') return <CanvasOnlyShell />;
  return <FullWebApp />;
}

function FullWebApp() {
  // Active Vault Mode ('primary' -> real_vault.db, 'decoy' -> decoy_vault.db)
  const [vaultMode, setVaultMode] = useState<VaultMode>('primary');
  const isVaultHydratedRef = useRef<boolean>(false);
  const [vaultHydratedSeq, setVaultHydratedSeq] = useState<number>(0);

  const [logs, setLogs] = useState<DiaryLog[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_LOGS_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        return Array.isArray(parsed) ? stripStarterGuideLogs(parsed) : [];
      }
    } catch {
      // ignore
    }
    return [];
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

  const [fullScreenMode, setFullScreenMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem(STORAGE_FULLSCREEN_KEY) === 'true';
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
  const [availableUpdate, setAvailableUpdate] = useState<AppUpdateInfo | null>(null);

  // Background check for GitHub release updates when app loads or data connects
  useEffect(() => {
    let active = true;
    const triggerUpdateCheck = async () => {
      if (typeof navigator !== 'undefined' && navigator.onLine) {
        try {
          const info = await checkForAppUpdate();
          if (active && info && info.hasUpdate) {
            setAvailableUpdate(info);
          }
        } catch {
          // ignore network failure
        }
      }
    };

    triggerUpdateCheck();

    const handleOnline = () => {
      triggerUpdateCheck();
    };

    window.addEventListener('online', handleOnline);
    return () => {
      active = false;
      window.removeEventListener('online', handleOnline);
    };
  }, []);
  const [exportingLog, setExportingLog] = useState<DiaryLog | null>(null);
  const [exportProcessingBanner, setExportProcessingBanner] = useState<string | null>(
    null
  );
  const [exportStatusBanner, setExportStatusBanner] = useState<string | null>(null);
  const [pendingDeleteLog, setPendingDeleteLog] = useState<DiaryLog | null>(null);
  const [pendingUnlockDiary, setPendingUnlockDiary] = useState<{
    log: DiaryLog;
    action: 'open' | 'export' | 'delete';
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

  // Global Quick Note creator modal (triggered from Home screen shortcut, Quick Settings tile, widget, or notification)
  const [showGlobalQuickNoteModal, setShowGlobalQuickNoteModal] = useState<boolean>(() =>
    detectQuickNoteLaunchSync()
  );
  const [isClosingQuickNotePopup, setIsClosingQuickNotePopup] = useState<boolean>(false);
  const dismissQuickNoteFallbackTimerRef = useRef<number | null>(null);
  const [pendingNewDiaryIntent, setPendingNewDiaryIntent] = useState<boolean>(false);
  const [pendingIncomingSharePayload, setPendingIncomingSharePayload] =
    useState<IncomingSharePayload | null>(null);
  const [startShareDraftEdit, setStartShareDraftEdit] = useState<boolean>(false);
  const [workspaceSessionSeq, setWorkspaceSessionSeq] = useState<number>(0);
  const isProcessingShareRef = useRef<boolean>(false);

  const handleDismissGlobalQuickNote = (wasSaved: boolean) => {
    try {
      window.getSelection()?.removeAllRanges();
      if (document.activeElement && 'blur' in document.activeElement) {
        (document.activeElement as HTMLElement).blur();
      }
      window.LikkhoNative?.clearNativeSelectionAndActionMode?.();
      window.LikkhoNative?.cancelBiometricPrompt?.();
    } catch {
      // ignore
    }

    const hasNativeClose =
      typeof window !== 'undefined' &&
      typeof window.LikkhoNative?.closeQuickNotePopup === 'function';

    if (hasNativeClose) {
      // Keep opaque privacy shield active until Android backgrounds Activity (`visibilitychange: hidden`)
      // so the App Lock Screen / Biometric Prompt NEVER flashes after Quick Note Save/Cancel.
      setIsClosingQuickNotePopup(true);
      setShowGlobalQuickNoteModal(false);
      if (dismissQuickNoteFallbackTimerRef.current) {
        window.clearTimeout(dismissQuickNoteFallbackTimerRef.current);
      }
      dismissQuickNoteFallbackTimerRef.current = window.setTimeout(() => {
        setIsClosingQuickNotePopup(false);
      }, 5000);
      window.setTimeout(() => {
        try {
          window.LikkhoNative?.closeQuickNotePopup?.(wasSaved);
        } catch {
          // ignore
        }
      }, 60);
    } else {
      setIsClosingQuickNotePopup(false);
      setShowGlobalQuickNoteModal(false);
    }
  };

  // Reset Quick Note closing shield once the app actually moves to the background (ready for next normal launch lockscreen)
  useEffect(() => {
    const handleVisibilityReset = () => {
      if (document.visibilityState === 'hidden') {
        if (dismissQuickNoteFallbackTimerRef.current) {
          window.clearTimeout(dismissQuickNoteFallbackTimerRef.current);
          dismissQuickNoteFallbackTimerRef.current = null;
        }
        setIsClosingQuickNotePopup(false);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityReset);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityReset);
      if (dismissQuickNoteFallbackTimerRef.current) {
        window.clearTimeout(dismissQuickNoteFallbackTimerRef.current);
        dismissQuickNoteFallbackTimerRef.current = null;
      }
    };
  }, []);

  // Sync latest Quick Note to Android Home Screen Widget whenever vault changes
  useEffect(() => {
    syncActiveNoteToHomeWidget(getQuickNotes(vaultMode));
  }, [vaultMode]);

  // Dismiss Android Native Launch Veil immediately after React paints first UI frame (zero white/homepage flash)
  useEffect(() => {
    let raf1 = 0;
    let raf2 = 0;
    raf1 = window.requestAnimationFrame(() => {
      raf2 = window.requestAnimationFrame(() => {
        window.LikkhoNative?.notifyAppReady?.();
      });
    });
    return () => {
      window.cancelAnimationFrame(raf1);
      window.cancelAnimationFrame(raf2);
    };
  }, []);

  // Ensure any native biometric prompt is cancelled whenever Quick Note Creator opens
  useEffect(() => {
    if (showGlobalQuickNoteModal) {
      window.LikkhoNative?.cancelBiometricPrompt?.();
    }
  }, [showGlobalQuickNoteModal]);

  // Check for Quick Note launch action, New Diary action, and Share-to-Likkho incoming intents
  useEffect(() => {
    const checkQuickNoteAndIntentActions = () => {
      let isQuickNoteLaunch = false;
      let isNewDiaryLaunch = false;
      if (typeof window !== 'undefined') {
        const urlParams = new URLSearchParams(window.location.search);
        const actionParam = urlParams.get('action');
        if (actionParam === 'quick_note' || window.location.hash.includes('quick_note')) {
          isQuickNoteLaunch = true;
        } else if (actionParam === 'new_diary' || window.location.hash.includes('new_diary')) {
          isNewDiaryLaunch = true;
        }
        if (isQuickNoteLaunch || isNewDiaryLaunch) {
          try {
            window.history.replaceState({}, '', window.location.pathname);
          } catch {
            // ignore
          }
        }
      }
      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.consumePendingQuickNoteAction === 'function' &&
        window.LikkhoNative.consumePendingQuickNoteAction()
      ) {
        isQuickNoteLaunch = true;
      }
      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.consumePendingNewDiaryAction === 'function' &&
        window.LikkhoNative.consumePendingNewDiaryAction()
      ) {
        isNewDiaryLaunch = true;
      }

      if (isQuickNoteLaunch) {
        if (dismissQuickNoteFallbackTimerRef.current) {
          window.clearTimeout(dismissQuickNoteFallbackTimerRef.current);
          dismissQuickNoteFallbackTimerRef.current = null;
        }
        setIsClosingQuickNotePopup(false);
        window.LikkhoNative?.cancelBiometricPrompt?.();
        setShowGlobalQuickNoteModal(true);
      } else if (isNewDiaryLaunch) {
        setPendingNewDiaryIntent(true);
      }

      // Check for Share-to-Likkho (text/plain or image/* from other apps)
      try {
        if (
          window.LikkhoNative &&
          typeof window.LikkhoNative.consumePendingIncomingShare === 'function'
        ) {
          const rawShare = window.LikkhoNative.consumePendingIncomingShare();
          if (rawShare && rawShare.trim().startsWith('{')) {
            const parsed = JSON.parse(rawShare) as Partial<IncomingSharePayload>;
            if (parsed.title || parsed.text || parsed.imageDataUrl) {
              setPendingIncomingSharePayload({
                title: parsed.title || '',
                text: parsed.text || '',
                imageDataUrl: parsed.imageDataUrl || '',
              });
            }
          }
        }
      } catch {
        // ignore invalid share JSON
      }
    };

    checkQuickNoteAndIntentActions();
    window.addEventListener('focus', checkQuickNoteAndIntentActions);
    document.addEventListener('visibilitychange', checkQuickNoteAndIntentActions);

    // Global callback from Android Quick Settings tile, widget, or shortcut
    window.__onLikkhoOpenQuickNoteCreator = () => {
      if (dismissQuickNoteFallbackTimerRef.current) {
        window.clearTimeout(dismissQuickNoteFallbackTimerRef.current);
        dismissQuickNoteFallbackTimerRef.current = null;
      }
      setIsClosingQuickNotePopup(false);
      window.LikkhoNative?.cancelBiometricPrompt?.();
      setShowGlobalQuickNoteModal(true);
      return 'OPENED';
    };

    window.__onLikkhoNewDiaryAction = () => {
      setPendingNewDiaryIntent(true);
      return 'QUEUED';
    };

    window.__onLikkhoIncomingShareAvailable = () => {
      checkQuickNoteAndIntentActions();
      return 'RECEIVED';
    };

    // Global callback from Android Notification Action: "Done"
    window.__onLikkhoQuickNoteDone = (noteId: string) => {
      markQuickNoteDone(noteId, vaultMode);
    };

    return () => {
      window.removeEventListener('focus', checkQuickNoteAndIntentActions);
      document.removeEventListener('visibilitychange', checkQuickNoteAndIntentActions);
      delete window.__onLikkhoOpenQuickNoteCreator;
      delete window.__onLikkhoNewDiaryAction;
      delete window.__onLikkhoIncomingShareAvailable;
      delete window.__onLikkhoQuickNoteDone;
    };
  }, [vaultMode]);

  // Execute queued New Diary intent or Share-to-Likkho draft ONLY after vault is unlocked and hydrated
  useEffect(() => {
    if (isLocked || vaultHydratedSeq === 0) return;

    if (pendingIncomingSharePayload && !isProcessingShareRef.current) {
      isProcessingShareRef.current = true;
      const payload = pendingIncomingSharePayload;
      setPendingIncomingSharePayload(null);

      (async () => {
        setExportProcessingBanner('Preparing shared content...');
        try {
          const nowMs = Date.now();
          const canvasImages: CanvasDraggableImage[] = [];

          if (payload.imageDataUrl) {
            const imgBlob = await dataUrlToBlob(payload.imageDataUrl);
            const processed = await processImageFileToBlob(imgBlob, 2560);
            const displayW = 240;
            const displayH =
              processed.width > 0
                ? Math.max(80, Math.round((processed.height / processed.width) * displayW))
                : 180;
            canvasImages.push({
              id: `img_${nowMs}`,
              dataUrl: processed.id,
              x: 24,
              y: 120,
              width: displayW,
              height: displayH,
              opacity: 1,
              rotation: 0,
              layer: 'foreground',
              sizeBytes: processed.sizeBytes,
              originalSizeBytes: processed.originalSizeBytes,
            });
          }

          const contentHtml = convertPlainTextToParagraphHtml(payload.text);
          const cleanPreviewText = (payload.text || '').replace(/\s+/g, ' ').trim();
          const plainPreview = cleanPreviewText
            ? cleanPreviewText.slice(0, 140)
            : canvasImages.length > 0
            ? 'Shared image'
            : 'Shared note';

          const draftLog: DiaryLog = {
            id: `log_${nowMs}_${Math.random().toString(36).slice(2, 7)}`,
            heading: (payload.title || '').trim(),
            contentHtml,
            plainPreview,
            pfpDataUrl: null,
            createdAt: nowMs,
            updatedAt: nowMs,
            dateStamp: new Date(nowMs).toLocaleDateString('en-IN', {
              day: '2-digit',
              month: 'short',
              year: 'numeric',
            }),
            timeStamp: new Date(nowMs).toLocaleTimeString('en-IN', {
              hour: '2-digit',
              minute: '2-digit',
              hour12: true,
            }),
            reminderAt: null,
            canvasImages,
          };

          setShowGlobalQuickNoteModal(false);
          setEditingLog(draftLog);
          setStartShareDraftEdit(true);
          setWorkspaceSessionSeq((s) => s + 1);
          setRoute('workspace');
          setExportProcessingBanner(null);
          setExportStatusBanner('Shared content ready to edit & save.');
          window.setTimeout(() => setExportStatusBanner(null), 3000);
        } catch {
          setExportProcessingBanner(null);
          setExportStatusBanner('Could not open shared content.');
          window.setTimeout(() => setExportStatusBanner(null), 3000);
        } finally {
          isProcessingShareRef.current = false;
        }
      })();
      return;
    }

    if (pendingNewDiaryIntent) {
      setPendingNewDiaryIntent(false);
      setShowGlobalQuickNoteModal(false);
      setEditingLog(null);
      setStartShareDraftEdit(false);
      setWorkspaceSessionSeq((s) => s + 1);
      setRoute('workspace');
    }
  }, [isLocked, vaultHydratedSeq, pendingNewDiaryIntent, pendingIncomingSharePayload]);

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

    // Automatically mark external file picker pending whenever any <input type="file"> is clicked anywhere in the app
    const handleGlobalClickCapture = (ev: MouseEvent) => {
      const target = ev.target as HTMLElement | null;
      if (!target) return;
      if (
        (target.tagName === 'INPUT' && (target as HTMLInputElement).type === 'file') ||
        target.closest('input[type="file"]')
      ) {
        markExternalFilePickerPending();
      }
    };

    const handleAppClosedOrBackgrounded = () => {
      if (document.visibilityState === 'hidden') {
        if (isExternalFilePickerPending()) {
          return;
        }
        try {
          window.getSelection()?.removeAllRanges();
          if (document.activeElement && 'blur' in document.activeElement) {
            (document.activeElement as HTMLElement).blur();
          }
          if (window.LikkhoNative?.clearNativeSelectionAndActionMode) {
            window.LikkhoNative.clearNativeSelectionAndActionMode();
          }
        } catch {
          // ignore
        }
        setIsLocked(true);
      } else if (document.visibilityState === 'visible') {
        window.setTimeout(() => {
          clearExternalFilePickerPending();
        }, 1200);
      }
    };

    const handlePageHide = () => {
      if (isExternalFilePickerPending()) {
        return;
      }
      try {
        window.getSelection()?.removeAllRanges();
        if (document.activeElement && 'blur' in document.activeElement) {
          (document.activeElement as HTMLElement).blur();
        }
        if (window.LikkhoNative?.clearNativeSelectionAndActionMode) {
          window.LikkhoNative.clearNativeSelectionAndActionMode();
        }
      } catch {
        // ignore
      }
      setIsLocked(true);
    };

    document.addEventListener('click', handleGlobalClickCapture, true);
    document.addEventListener('visibilitychange', handleAppClosedOrBackgrounded);
    window.addEventListener('pagehide', handlePageHide);
    return () => {
      document.removeEventListener('click', handleGlobalClickCapture, true);
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
    let initialLocalLogs: DiaryLog[] = [];
    try {
      const raw = localStorage.getItem(logsStorageKey);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          initialLocalLogs = stripStarterGuideLogs(parsed);
        }
      }
    } catch {
      // ignore
    }
    setLogs(initialLocalLogs);

    // 3. Load rich media logs & custom fonts from App-Specific Internal Files Directory (encrypted at rest) and IndexedDB
    Promise.all([
      readAppSpecificInternalFile<DiaryLog[]>(idbLogsKey, vaultMode).then(
        (internal) => internal || loadActiveAppStateFromIDB<DiaryLog[]>(idbLogsKey, vaultMode)
      ),
      readAppSpecificInternalFile<CustomFontItem[]>(idbFontsKey, vaultMode).then(
        (internal) => internal || loadActiveAppStateFromIDB<CustomFontItem[]>(idbFontsKey, vaultMode)
      ),
    ])
      .then(async ([savedLogs, savedFonts]) => {
        const candidateLogs =
          savedLogs && Array.isArray(savedLogs) && (savedLogs.length > 0 || vaultMode === 'decoy')
            ? savedLogs
            : initialLocalLogs;

        if (candidateLogs && Array.isArray(candidateLogs)) {
          // Automatic migration of legacy dataURLs to IndexedDB Blob store
          const { logs: migratedLogs, migratedCount } = await migrateLogsMediaToBlobStore(candidateLogs);
          const finalLogs = stripStarterGuideLogs(migratedLogs);
          const starterRemoved = finalLogs.length !== candidateLogs.length;
          setLogs(finalLogs);
          if (migratedCount > 0 || starterRemoved) {
            await saveActiveAppStateToIDB(idbLogsKey, finalLogs, vaultMode);
            await saveAppSpecificInternalFile(idbLogsKey, finalLogs, vaultMode);
            try {
              localStorage.setItem(logsStorageKey, JSON.stringify(finalLogs));
            } catch {
              // ignore
            }
          }
        }
        if (savedFonts && Array.isArray(savedFonts)) {
          setCustomFonts(savedFonts);
          for (const f of savedFonts) {
            injectGlobalFontFaceCss(f.fontFamily, f.dataUrl);
          }
        } else {
          setCustomFonts([]);
        }
      })
      .finally(() => {
        isVaultHydratedRef.current = true;
        setVaultHydratedSeq((s) => s + 1);
      });
  }, [vaultMode]);

  // Sync Light & Dark Theme with DOM & Android OS Status Bar + Navigation Bar (even when System Dark Mode is ON)
  useEffect(() => {
    const rootEl = document.documentElement;
    const metaTheme = document.getElementById('meta-theme-color');
    const metaScheme = document.getElementById('meta-color-scheme');
    const surfaceColor = darkMode ? '#1a1f24' : '#f8f9fa';
    const bgColor = darkMode ? '#101418' : '#ffffff';

    if (darkMode) {
      rootEl.classList.add('dark');
    } else {
      rootEl.classList.remove('dark');
    }
    rootEl.style.backgroundColor = bgColor;
    rootEl.style.colorScheme = darkMode ? 'dark' : 'light';
    document.body.style.backgroundColor = bgColor;
    document.body.style.colorScheme = darkMode ? 'dark' : 'light';

    try {
      localStorage.setItem(STORAGE_DARK_KEY, String(darkMode));
    } catch {
      // ignore
    }

    if (metaTheme) {
      metaTheme.setAttribute('content', surfaceColor);
    }
    if (metaScheme) {
      metaScheme.setAttribute('content', darkMode ? 'dark' : 'light');
    }

    const syncNativeBars = () => {
      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.setSystemBarsTheme === 'function'
      ) {
        window.LikkhoNative.setSystemBarsTheme(surfaceColor, bgColor, !darkMode);
      }
      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.setFullScreenMode === 'function'
      ) {
        window.LikkhoNative.setFullScreenMode(fullScreenMode);
      }
    };

    syncNativeBars();
    document.addEventListener('visibilitychange', syncNativeBars);
    return () => {
      document.removeEventListener('visibilitychange', syncNativeBars);
    };
  }, [darkMode, fullScreenMode]);

  // Global listener for background native microphone recording completion
  // Ensures voice recording stopped from notification is ALWAYS saved to the diary even if app was backgrounded, locked, or reopened
  useEffect(() => {
    const handleGlobalAudioPayload = async (
      audioPayloadOrUri: string,
      durationSec: number,
      targetLogId?: string,
      nativePath?: string
    ) => {
      if (!audioPayloadOrUri) return;

      // Also notify active canvas toolbar if mounted
      if (typeof window.__onLikkhoNativeMicFinished === 'function') {
        try {
          window.__onLikkhoNativeMicFinished(audioPayloadOrUri, durationSec);
        } catch {
          // ignore
        }
      }

      const nowMs = Date.now();
      const stampLabel = new Date(nowMs).toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      });

      const audioFormat = micSettings.format || 'wav';
      const isNative =
        Boolean(nativePath) ||
        audioPayloadOrUri.startsWith('http://') ||
        audioPayloadOrUri.startsWith('https://') ||
        audioPayloadOrUri.startsWith('file://') ||
        audioPayloadOrUri.startsWith('/data/');

      let finalAudioUri: string | undefined = undefined;
      let finalDataUrl: string = '';

      if (isNative) {
        finalAudioUri = nativePath || audioPayloadOrUri;
        finalDataUrl = '';
      } else {
        const persisted = await persistAudioAttachment(`rec_${nowMs}`, audioPayloadOrUri);
        finalAudioUri = persisted.audioUri;
        finalDataUrl = persisted.dataUrl;
      }

      const audioAttachment: CanvasAudioAttachment = {
        id: `rec_${nowMs}`,
        name: `Voice (${stampLabel}).${audioFormat}`,
        format: audioFormat,
        audioUri: finalAudioUri,
        dataUrl: finalDataUrl,
        durationSec: Math.max(1, durationSec || 1),
        createdAt: nowMs,
        x: 24,
        y: 120,
        width: 270,
        height: 56,
        rotation: 0,
        playbackRate: 1,
        layer: 'foreground',
      };

      const resolvedLogId =
        targetLogId ||
        editingLog?.id ||
        (logs.length > 0 ? logs[0].id : null);

      let updatedList: DiaryLog[];
      let updatedLog: DiaryLog;

      const existingIndex = resolvedLogId
        ? logs.findIndex((l) => l.id === resolvedLogId)
        : -1;

      if (existingIndex >= 0) {
        const cur = logs[existingIndex];
        const alreadyHas = (cur.audioAttachments || []).some(
          (a) =>
            (finalAudioUri && a.audioUri === finalAudioUri) ||
            (finalDataUrl && a.dataUrl === finalDataUrl)
        );
        if (alreadyHas) {
          window.LikkhoNative?.clearPendingFinishedNativeRecording?.();
          return;
        }
        updatedLog = {
          ...cur,
          audioAttachments: [...(cur.audioAttachments || []), audioAttachment],
          updatedAt: nowMs,
        };
        updatedList = [...logs];
        updatedList[existingIndex] = updatedLog;
      } else if (editingLog) {
        updatedLog = {
          ...editingLog,
          audioAttachments: [
            ...(editingLog.audioAttachments || []),
            audioAttachment,
          ],
          updatedAt: nowMs,
        };
        updatedList = [updatedLog, ...logs];
      } else {
        updatedLog = {
          id: resolvedLogId || `log_${nowMs}`,
          heading: `Voice Entry (${stampLabel})`,
          contentHtml: '',
          plainPreview: 'Voice recording attached.',
          pfpDataUrl: null,
          createdAt: nowMs,
          updatedAt: nowMs,
          dateStamp: new Date(nowMs).toLocaleDateString('en-IN', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
          }),
          timeStamp: new Date(nowMs).toLocaleTimeString('en-IN', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
          }),
          reminderAt: null,
          audioAttachments: [audioAttachment],
        };
        updatedList = [updatedLog, ...logs];
      }

      setLogs(updatedList);
      if (editingLog && editingLog.id === updatedLog.id) {
        setEditingLog(updatedLog);
      }

      const storageLogsKey =
        vaultMode === 'decoy' ? STORAGE_DECOY_LOGS_KEY : STORAGE_LOGS_KEY;
      const idbLogsKey =
        vaultMode === 'decoy' ? IDB_DECOY_LOGS_KEY : IDB_LOGS_KEY;

      await saveActiveAppStateToIDB(idbLogsKey, updatedList, vaultMode);
      try {
        localStorage.setItem(storageLogsKey, JSON.stringify(updatedList));
      } catch {
        // ignore
      }

      if (window.LikkhoNative?.clearPendingFinishedNativeRecording) {
        window.LikkhoNative.clearPendingFinishedNativeRecording();
      }

      setExportStatusBanner('Voice recording saved to diary.');
      window.setTimeout(() => setExportStatusBanner(null), 3200);
    };

    window.__onLikkhoNativeAudioFileReady = (audioUri, nativePath, duration, targetLog) => {
      handleGlobalAudioPayload(audioUri, duration, targetLog, nativePath);
    };

    window.__onLikkhoGlobalNativeMicFinished = (payload, duration, targetLog) => {
      handleGlobalAudioPayload(payload, duration, targetLog);
    };

    const pollPendingRecording = () => {
      try {
        if (window.LikkhoNative?.getPendingFinishedNativeRecording) {
          const raw = window.LikkhoNative.getPendingFinishedNativeRecording();
          if (raw && raw.trim().startsWith('{')) {
            const parsed = JSON.parse(raw);
            if (parsed.audioUri || parsed.filePath || parsed.base64) {
              handleGlobalAudioPayload(
                parsed.audioUri || parsed.filePath || parsed.base64,
                parsed.duration || 1,
                parsed.logId,
                parsed.filePath
              );
            }
          }
        }
      } catch {
        // ignore
      }
    };

    pollPendingRecording();
    const timer = window.setTimeout(pollPendingRecording, 1000);
    document.addEventListener('visibilitychange', pollPendingRecording);

    return () => {
      window.__onLikkhoGlobalNativeMicFinished = undefined;
      window.__onLikkhoNativeAudioFileReady = undefined;
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', pollPendingRecording);
    };
  }, [logs, editingLog, micSettings, vaultMode]);

  const handleToggleFullScreenMode = async () => {
    const next = !fullScreenMode;
    setFullScreenMode(next);
    try {
      localStorage.setItem(STORAGE_FULLSCREEN_KEY, String(next));
    } catch {
      // ignore
    }

    if (
      window.LikkhoNative &&
      typeof window.LikkhoNative.setFullScreenMode === 'function'
    ) {
      window.LikkhoNative.setFullScreenMode(next);
    }

    try {
      if (next) {
        if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
          await document.documentElement.requestFullscreen();
        }
      } else {
        if (document.fullscreenElement && document.exitFullscreen) {
          await document.exitFullscreen();
        }
      }
    } catch {
      // ignore browser fullscreen restriction when inside native WebView or iframe
    }
  };

  // Save in-app logs + media to App-Specific Internal Files Directory (encrypted at rest), IndexedDB and localStorage
  useEffect(() => {
    if (!isVaultHydratedRef.current) return;
    const logsStorageKey =
      vaultMode === 'decoy' ? STORAGE_DECOY_LOGS_KEY : STORAGE_LOGS_KEY;
    const idbLogsKey = vaultMode === 'decoy' ? IDB_DECOY_LOGS_KEY : IDB_LOGS_KEY;

    saveActiveAppStateToIDB(idbLogsKey, logs, vaultMode);
    saveAppSpecificInternalFile(idbLogsKey, logs, vaultMode);
    try {
      localStorage.setItem(logsStorageKey, JSON.stringify(logs));
    } catch {
      // Large media gracefully stored in App-Specific Internal Files Directory & IDB above
    }
  }, [logs, vaultMode]);

  // Save custom .ttf fonts to App-Specific Internal Files Directory and isolated IndexedDB
  useEffect(() => {
    if (!isVaultHydratedRef.current) return;
    const idbFontsKey = vaultMode === 'decoy' ? IDB_DECOY_FONTS_KEY : IDB_FONTS_KEY;
    if (customFonts.length > 0) {
      saveActiveAppStateToIDB(idbFontsKey, customFonts, vaultMode);
      saveAppSpecificInternalFile(idbFontsKey, customFonts, vaultMode);
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
      if (showGlobalQuickNoteModal) {
        handleDismissGlobalQuickNote(false);
        return 'HANDLED';
      }
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
  }, [route, isSearchOpen, exportingLog, pendingDeleteLog, pendingUnlockDiary, showGlobalQuickNoteModal]);

  const navigateTo = (target: PageRoute, logToEdit: DiaryLog | null = null) => {
    setOpenMenuLogId(null);
    setStartShareDraftEdit(false);
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

  // Request confirmation popup before deleting ANY entry (Locked diaries MUST verify diary passcode first!)
  const requestDeleteLogConfirmation = (id: string) => {
    setOpenMenuLogId(null);
    const target = logs.find((l) => l.id === id) || editingLog;
    if (!target) return;
    if (target.diaryLockPin) {
      setPendingUnlockDiary({ log: target, action: 'delete' });
      setDiaryUnlockInput('');
      setDiaryUnlockError(null);
      return;
    }
    setPendingDeleteLog(target);
  };

  const confirmDeleteLog = () => {
    if (!pendingDeleteLog) return;
    const id = pendingDeleteLog.id;
    if (window.LikkhoNative?.cancelNativeReminder) {
      window.LikkhoNative.cancelNativeReminder(id);
    }
    // Delete native audio files from App-Specific Internal Files Directory if applicable
    if (pendingDeleteLog.audioAttachments && Array.isArray(pendingDeleteLog.audioAttachments)) {
      for (const att of pendingDeleteLog.audioAttachments) {
        if (att.audioUri && window.LikkhoNative?.deleteNativeAudioFile) {
          window.LikkhoNative.deleteNativeAudioFile(att.audioUri);
        }
      }
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

  // Share a diary entry as a compressed .likkho archive file (with GitHub download link message).
  // If the diary is locked, it is shared directly in its locked state so the receiver also receives it locked!
  const handleShareDiaryLog = async (log: DiaryLog) => {
    if (exportProcessingBanner) return;
    setOpenMenuLogId(null);
    setExportStatusBanner(null);
    setExportProcessingBanner(
      `Packing "${log.heading}" as .likkho archive... Please wait.`
    );
    await new Promise((resolve) => window.setTimeout(resolve, 40));
    try {
      const msg = await shareDiaryAsLikkhoArchive(log);
      setExportProcessingBanner(null);
      setExportStatusBanner(msg);
      window.setTimeout(() => {
        setExportStatusBanner((prev) => (prev === msg ? null : prev));
      }, 3200);
    } catch {
      setExportProcessingBanner(null);
      setExportStatusBanner('Could not share .likkho diary archive.');
      window.setTimeout(() => setExportStatusBanner(null), 3000);
    }
  };

  // Handle incoming .likkho files opened via Android Custom Intent (WhatsApp, Telegram, File Manager)
  useEffect(() => {
    const processIncomingLikkhoBase64 = async (base64Payload: string) => {
      if (!base64Payload || !base64Payload.trim()) return;
      setExportProcessingBanner('Unpacking received .likkho diary...');
      try {
        const unpackedLog = await unpackLikkhoBase64Payload(base64Payload);
        setLogs((prev) => [unpackedLog, ...prev]);
        setExportProcessingBanner(null);

        if (unpackedLog.diaryLockPin) {
          // Receiver receives locked diary in locked state!
          setRoute('home');
          setEditingLog(null);
          setPendingUnlockDiary({ log: unpackedLog, action: 'open' });
          setDiaryUnlockInput('');
          setDiaryUnlockError(null);
          setExportStatusBanner(
            `Received locked diary "${unpackedLog.heading}". Enter passcode to view.`
          );
        } else {
          // Open directly on canvas in exact order
          setEditingLog(unpackedLog);
          setRoute('workspace');
          setExportStatusBanner(`Opened shared diary "${unpackedLog.heading}".`);
        }
        window.setTimeout(() => setExportStatusBanner(null), 3500);
      } catch {
        setExportProcessingBanner(null);
        setExportStatusBanner('Unable to unpack .likkho file.');
        window.setTimeout(() => setExportStatusBanner(null), 3000);
      }
    };

    window.__onLikkhoIncomingArchive = (base64Archive: string) => {
      processIncomingLikkhoBase64(base64Archive);
    };

    // Also poll once on startup in case Android launched the Activity with a .likkho intent before React mounted
    const timer = window.setTimeout(() => {
      try {
        if (
          window.LikkhoNative &&
          typeof window.LikkhoNative.consumePendingIncomingLikkhoArchive ===
            'function'
        ) {
          const pendingB64 =
            window.LikkhoNative.consumePendingIncomingLikkhoArchive();
          if (pendingB64 && pendingB64.trim().length > 0) {
            processIncomingLikkhoBase64(pendingB64);
          }
        }
      } catch {
        // ignore
      }
    }, 350);

    return () => {
      window.clearTimeout(timer);
      window.__onLikkhoIncomingArchive = undefined;
    };
  }, []);

  const handleSelectExportFormat = async (format: ExportFormat) => {
    if (!exportingLog || exportProcessingBanner) return;
    const targetLog = exportingLog;
    setExportingLog(null);
    setExportStatusBanner(null);
    setExportProcessingBanner(
      `Exporting "${targetLog.heading}" as .${format.toUpperCase()}... Please wait.`
    );
    // Allow React to paint the processing pop-up before heavy export work begins
    await new Promise((resolve) => window.setTimeout(resolve, 50));
    try {
      const msg = await exportDiaryLog(targetLog, format);
      setExportProcessingBanner(null);
      setExportStatusBanner(msg);
      window.setTimeout(() => {
        setExportStatusBanner((prev) => (prev === msg ? null : prev));
      }, 3200);
    } catch {
      setExportProcessingBanner(null);
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
      {/* Export Processing Pop-up Banner (Prevents duplicate export taps during large exports) */}
      {exportProcessingBanner && (
        <div className="fixed bottom-20 left-4 right-4 z-50 mx-auto flex max-w-sm items-center gap-2.5 border border-[#3366cc] bg-[var(--wiki-bg)] px-3.5 py-2.5 text-xs font-semibold text-[#3366cc]">
          <Loader2 className="h-4 w-4 shrink-0 animate-spin" />
          <span className="truncate">{exportProcessingBanner}</span>
        </div>
      )}

      {/* Export / Status Confirmation Banner */}
      {exportStatusBanner && !exportProcessingBanner && (
        <div className="fixed bottom-20 left-4 right-4 z-50 mx-auto flex max-w-sm items-center gap-2 border border-[#14866d] bg-[var(--wiki-bg)] px-3.5 py-2.5 text-xs font-semibold text-[#14866d]">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span className="truncate">{exportStatusBanner}</span>
        </div>
      )}

      {/* Android Heads-Up Ringing Notification Banner */}
      {ringingAlert && (
        <div className="fixed left-3 right-3 top-3 z-50 mx-auto max-w-md border border-[#3366cc] bg-[var(--wiki-bg)] p-3.5">
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

      {/* Opaque privacy shield while Quick Note Creator opens or dismisses directly over a locked vault (prevents diary leak and suppresses Lockscreen + Biometrics) */}
      {(showGlobalQuickNoteModal || isClosingQuickNotePopup) && isLocked && effectivePrimaryPin && (
        <div
          className="fixed inset-0 z-[100] bg-[var(--wiki-bg)]"
          aria-hidden="true"
        />
      )}

      {/* Instant Lock & Launch Lock Overlay — Hidden when Quick Note modal is opened or dismissing directly from Home Screen Shortcut / Quick Settings Tile */}
      {isLocked && effectivePrimaryPin && !showGlobalQuickNoteModal && !isClosingQuickNotePopup && (
        <div className="fixed inset-0 z-[100] flex h-full w-full flex-col bg-[var(--wiki-bg)]">
          <PasscodeScreen
            savedPasscode={savedPasscode || ''}
            secondaryPasscode={secondaryPasscode}
            biometricsEnabled={biometricsEnabled}
            onUnlock={(unlockedMode) => {
              clearExternalFilePickerPending();
              if (unlockedMode !== vaultMode) {
                setVaultMode(unlockedMode);
                setRoute('home');
                setEditingLog(null);
              }
              setIsLocked(false);
            }}
          />
        </div>
      )}

      {route === 'workspace' ? (
        <WritingWorkspace
          key={`ws-${workspaceSessionSeq}`}
          vaultMode={vaultMode}
          initialLog={editingLog}
          startInEditMode={startShareDraftEdit}
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
          fullScreenMode={fullScreenMode}
          onToggleFullScreenMode={handleToggleFullScreenMode}
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
          logs={logs}
          customFonts={customFonts}
          savedKeyHash={savedKeyHash}
          onRestoreSingleDiary={async (singleLog) => {
            await preloadLogImages([singleLog]);
            setLogs((prev) => {
              const exists = prev.some((l) => l.id === singleLog.id);
              if (exists) {
                return prev.map((l) => (l.id === singleLog.id ? singleLog : l));
              }
              return [singleLog, ...prev];
            });
            setExportStatusBanner(`Restored "${singleLog.heading || 'Diary'}" to app.`);
            window.setTimeout(() => setExportStatusBanner(null), 3000);
          }}
          onClearAllAppData={async () => {
            // Cancel any scheduled reminders
            logs.forEach((l) => {
              if (window.LikkhoNative?.cancelNativeReminder) {
                window.LikkhoNative.cancelNativeReminder(l.id);
              }
            });
            await clearAllAppVaultData();
            await deleteAppSpecificInternalFile(IDB_LOGS_KEY, 'primary');
            await deleteAppSpecificInternalFile(IDB_DECOY_LOGS_KEY, 'decoy');
            await deleteAppSpecificInternalFile(IDB_FONTS_KEY, 'primary');
            await deleteAppSpecificInternalFile(IDB_DECOY_FONTS_KEY, 'decoy');
            setLogs([]);
            setCustomFonts([]);
            setMicSettings(DEFAULT_MIC_SETTINGS);
            setSavedPasscode(null);
            setSecondaryPasscode(null);
            setBiometricsEnabled(false);
            setSavedKeyHash(null);
            setVaultMode('primary');
            setIsLocked(false);
            // Persist empty logs state after clear
            await saveActiveAppStateToIDB(IDB_LOGS_KEY, [], 'primary');
            try {
              localStorage.setItem(STORAGE_LOGS_KEY, JSON.stringify([]));
            } catch {
              // ignore
            }
            setExportStatusBanner('All application data has been deleted.');
            window.setTimeout(() => setExportStatusBanner(null), 3200);
          }}
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
            await preloadLogImages(bundle.logs);
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
                const avatarSrc =
                  getCanvasPreviewUrl(log.pfpDataUrl) ||
                  getImageObjectUrl(log.pfpDataUrl) ||
                  getFallbackMonographPfp();
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
                          <span title="Locked Diary">
                            <Lock className="h-3.5 w-3.5 shrink-0 text-[#b32424]" />
                          </span>
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

                      {/* 3-Dots Dropdown Menu: Pin to Top, Share (.likkho), Export, Delete */}
                      {isMenuOpen && (
                        <div className="absolute right-0 top-11 z-40 w-44 divide-y divide-[var(--wiki-hairline)] border border-[var(--wiki-border)] bg-[var(--wiki-bg)]">
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
                            disabled={Boolean(exportProcessingBanner)}
                            onClick={() => handleShareDiaryLog(log)}
                            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)] disabled:opacity-50"
                          >
                            <Share2 className="h-4 w-4 text-[#3366cc]" />
                            <span>Share (.likkho)</span>
                          </button>

                          <button
                            type="button"
                            disabled={Boolean(exportProcessingBanner)}
                            onClick={() => {
                              if (exportProcessingBanner) return;
                              setOpenMenuLogId(null);
                              if (log.diaryLockPin) {
                                setPendingUnlockDiary({ log, action: 'export' });
                                setDiaryUnlockInput('');
                                setDiaryUnlockError(null);
                              } else {
                                setExportingLog(log);
                              }
                            }}
                            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)] disabled:opacity-50"
                          >
                            <Download className="h-4 w-4 text-[#3366cc]" />
                            <span>
                              {exportProcessingBanner ? 'Exporting...' : 'Export'}
                            </span>
                          </button>

                          <button
                            type="button"
                            onClick={() => requestDeleteLogConfirmation(log.id)}
                            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-xs font-medium text-[#b32424] hover:bg-[var(--wiki-surface)]"
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
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4"
          onClick={() => setPendingUnlockDiary(null)}
        >
          <div
            className="flex max-h-[88dvh] w-full max-w-xs flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2 min-w-0">
                <Lock className="h-4 w-4 shrink-0 text-[#b32424]" />
                <h3 className="font-wiki-serif text-base font-bold truncate">
                  Unlock "{pendingUnlockDiary.log.heading}"
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPendingUnlockDiary(null)}
                className="flex h-7 w-7 shrink-0 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:border-[var(--wiki-border)] hover:text-[var(--wiki-text)]"
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
                  } else if (act === 'export') {
                    setExportingLog(targetLog);
                  } else if (act === 'delete') {
                    setPendingDeleteLog(targetLog);
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
              className="flex flex-col overflow-y-auto"
            >
              <div className="space-y-3 p-4">
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
              </div>
              <div className="flex items-center justify-end gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
                <button
                  type="button"
                  onClick={() => setPendingUnlockDiary(null)}
                  className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 text-xs font-medium text-[var(--wiki-text)]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={diaryCooldownSec > 0}
                  className="h-9 border border-[#3366cc] bg-[#3366cc] px-4 text-xs font-semibold text-white disabled:opacity-50"
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
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4"
          onClick={() => setPendingDeleteLog(null)}
        >
          <div
            className="flex max-h-[88dvh] w-full max-w-sm flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 text-[#b32424]" />
                <h3 className="font-wiki-serif text-lg font-bold">
                  Delete Entry?
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPendingDeleteLog(null)}
                className="flex h-8 w-8 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:border-[var(--wiki-border)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="overflow-y-auto p-4">
              <p className="text-xs leading-relaxed text-[var(--wiki-text)]">
                Are you sure you want to permanently delete{' '}
                <strong>"{pendingDeleteLog.heading}"</strong>? This action cannot be undone.
              </p>
            </div>

            <div className="flex shrink-0 items-center justify-end gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <button
                type="button"
                onClick={() => setPendingDeleteLog(null)}
                className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-4 text-xs font-medium text-[var(--wiki-text)]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={confirmDeleteLog}
                className="flex h-9 items-center gap-1.5 border border-[#b32424] bg-[#b32424] px-4 text-xs font-semibold text-white hover:bg-[#941d1d]"
              >
                <Trash2 className="h-4 w-4" />
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Multi-Format Export Modal */}
      {exportingLog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4"
          onClick={() => setExportingLog(null)}
        >
          <div
            className="flex max-h-[88dvh] w-full max-w-sm flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2 min-w-0">
                <FileDown className="h-4 w-4 shrink-0 text-[#3366cc]" />
                <h3 className="font-wiki-serif text-lg font-bold truncate">
                  Export "{exportingLog.heading}"
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setExportingLog(null)}
                className="flex h-8 w-8 shrink-0 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:border-[var(--wiki-border)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="overflow-y-auto p-4">
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

      {/* Auto-Update Notification Modal */}
      {availableUpdate && (
        <AppUpdateModal
          updateInfo={availableUpdate}
          onClose={() => setAvailableUpdate(null)}
        />
      )}

      {/* Global Quick Note Creator Modal (from Quick Settings Tile, Widget, or Home Screen Shortcut) */}
      {showGlobalQuickNoteModal && (
        <QuickNoteCreatorModal
          onClose={() => handleDismissGlobalQuickNote(false)}
          onSave={(newNote) => {
            const compulsoryNote = {
              ...newNote,
              isDone: false,
              isPinnedToNotification: true,
            };
            const currentList = getQuickNotes(vaultMode);
            const nextList = [compulsoryNote, ...currentList];
            saveQuickNotes(nextList, vaultMode);
            syncQuickNoteToNotification(compulsoryNote);
            window.dispatchEvent(
              new CustomEvent('likkho-quick-notes-changed', {
                detail: { noteId: compulsoryNote.id },
              })
            );
            handleDismissGlobalQuickNote(true);
          }}
        />
      )}
    </div>
  );
}

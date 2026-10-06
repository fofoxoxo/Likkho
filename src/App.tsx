import React, { useEffect, useState } from 'react';
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
} from 'lucide-react';
import { DiaryLog } from './utils/cryptoVault';
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
const STORAGE_DARK_KEY = 'wikilog_dark_theme_v1';
const STORAGE_PIN_KEY = 'wikilog_passcode_v1';
const STORAGE_ENC_HASH_KEY = 'wikilog_encryption_key_hash_v1';

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
    reminderAt: null,
    pinned: false,
  },
];

function getFallbackMonographPfp(heading: string, darkMode: boolean): string {
  const letter = (heading.trim()[0] || 'L').toUpperCase();
  const bg = darkMode ? '#1a1f24' : '#f8f9fa';
  const fg = darkMode ? '#eaecf0' : '#202122';
  const border = darkMode ? '#3a4047' : '#a2a9b1';
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
    <rect width="120" height="120" fill="${bg}" stroke="${border}" stroke-width="2"/>
    <text x="60" y="78" font-family="Georgia, serif" font-size="60" font-weight="bold" fill="${fg}" text-anchor="middle">${letter}</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
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

  const [darkMode, setDarkMode] = useState<boolean>(() => {
    return localStorage.getItem(STORAGE_DARK_KEY) === 'true';
  });

  const [savedPasscode, setSavedPasscode] = useState<string | null>(() => {
    return localStorage.getItem(STORAGE_PIN_KEY) || null;
  });

  const [savedKeyHash, setSavedKeyHash] = useState<string | null>(() => {
    return localStorage.getItem(STORAGE_ENC_HASH_KEY) || null;
  });

  const [isLocked, setIsLocked] = useState<boolean>(false);
  const [route, setRoute] = useState<PageRoute>('home');
  const [editingLog, setEditingLog] = useState<DiaryLog | null>(null);

  // 3-dots menu & Export modal states
  const [openMenuLogId, setOpenMenuLogId] = useState<string | null>(null);
  const [exportingLog, setExportingLog] = useState<DiaryLog | null>(null);

  // Active ringing Android notification alert banner
  const [ringingAlert, setRingingAlert] = useState<{
    id: string;
    heading: string;
    preview: string;
  } | null>(null);

  // Sync Dark Theme with DOM & Android Status Bar / Navigation Bar theme-color
  useEffect(() => {
    const rootEl = document.documentElement;
    const metaTheme = document.getElementById('meta-theme-color');
    const surfaceColor = darkMode ? '#101418' : '#ffffff';

    if (darkMode) {
      rootEl.classList.add('dark');
    } else {
      rootEl.classList.remove('dark');
    }
    localStorage.setItem(STORAGE_DARK_KEY, String(darkMode));

    if (metaTheme) {
      metaTheme.setAttribute('content', surfaceColor);
    }
  }, [darkMode]);

  // Save in-app logs automatically
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_LOGS_KEY, JSON.stringify(logs));
    } catch {
      // ignore quota error
    }
  }, [logs]);

  // Sync browser/Android Hardware Back Button:
  // "User app ke kisi bhi page par ho, back karne par pahle homepage par aayega"
  useEffect(() => {
    window.history.replaceState({ page: 'home' }, '');

    const handlePopState = () => {
      if (route !== 'home') {
        setRoute('home');
        setEditingLog(null);
        window.history.pushState({ page: 'home' }, '');
      }
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [route]);

  const navigateTo = (target: PageRoute, logToEdit: DiaryLog | null = null) => {
    setOpenMenuLogId(null);
    if (target === 'home') {
      setRoute('home');
      setEditingLog(null);
    } else {
      window.history.pushState({ page: target }, '');
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

  const handleDeleteLog = (id: string) => {
    setLogs((prev) => prev.filter((l) => l.id !== id));
    setOpenMenuLogId(null);
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

  const handleSelectExportFormat = (format: ExportFormat) => {
    if (!exportingLog) return;
    exportDiaryLog(exportingLog, format);
    setExportingLog(null);
  };

  // Sort pinned logs to top, then by createdAt descending
  const sortedLogs = [...logs].sort((a, b) => {
    if (a.pinned && !b.pinned) return -1;
    if (!a.pinned && b.pinned) return 1;
    return b.createdAt - a.createdAt;
  });

  return (
    <div
      className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)] overflow-hidden"
      style={{
        paddingTop: 'env(safe-area-inset-top, 0px)',
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
      }}
      onClick={() => {
        if (openMenuLogId) setOpenMenuLogId(null);
      }}
    >
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

      {/* Instant Lock Overlay */}
      {isLocked && savedPasscode ? (
        <PasscodeScreen
          savedPasscode={savedPasscode}
          onUnlock={() => setIsLocked(false)}
        />
      ) : route === 'workspace' ? (
        <WritingWorkspace
          initialLog={editingLog}
          onSaveLog={handleUpsertLog}
          onDeleteLog={handleDeleteLog}
          onExitWithoutSave={() => navigateTo('home')}
        />
      ) : route === 'settings' ? (
        <SettingsPage
          darkMode={darkMode}
          onToggleDarkMode={() => setDarkMode((d) => !d)}
          savedPasscode={savedPasscode}
          onUpdatePasscode={(pin) => {
            setSavedPasscode(pin);
            if (pin) {
              localStorage.setItem(STORAGE_PIN_KEY, pin);
            } else {
              localStorage.removeItem(STORAGE_PIN_KEY);
            }
          }}
          onOpenBackupRestore={() => navigateTo('backup')}
          onBackToHome={() => navigateTo('home')}
        />
      ) : route === 'backup' ? (
        <BackupRestorePage
          logs={logs}
          savedKeyHash={savedKeyHash}
          onSaveKeyHash={(hash) => {
            setSavedKeyHash(hash);
            localStorage.setItem(STORAGE_ENC_HASH_KEY, hash);
          }}
          onRestoreLogs={(restored) => {
            setLogs(restored);
          }}
          onBackToHome={() => navigateTo('home')}
        />
      ) : (
        /* HOMEPAGE VIEW — No bar or buttons between Header and Logs */
        <div className="relative flex h-full w-full flex-col bg-[var(--wiki-bg)]">
          {/* Header: Left App Name ("Likkho") | Right Instant Lock + Hamburger Menu */}
          <header className="flex h-14 shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
            <span className="font-wiki-serif text-2xl font-bold tracking-tight text-[var(--wiki-text)]">
              Likkho
            </span>

            <div className="flex items-center gap-2">
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

          {/* Directly Logs List below Header — Zero intermediate text or buttons */}
          <main className="flex-1 overflow-y-auto divide-y divide-[var(--wiki-hairline)] pb-24">
            {sortedLogs.length === 0 ? (
              <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
                <div className="flex h-12 w-12 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                  <BookOpen className="h-6 w-6 text-[var(--wiki-muted)]" />
                </div>
                <h2 className="mt-4 font-wiki-serif text-xl font-bold">
                  No Logs Yet
                </h2>
                <p className="mt-1 max-w-xs text-xs leading-relaxed text-[var(--wiki-muted)]">
                  Tap the create button below to start writing.
                </p>
              </div>
            ) : (
              sortedLogs.map((log) => {
                const avatarSrc =
                  log.pfpDataUrl || getFallbackMonographPfp(log.heading, darkMode);
                const isMenuOpen = openMenuLogId === log.id;

                return (
                  <article
                    key={log.id}
                    onClick={() => navigateTo('workspace', log)}
                    className="group relative flex cursor-pointer items-center gap-3.5 px-4 py-3.5 hover:bg-[var(--wiki-surface)] active:bg-[var(--wiki-hairline)] transition-colors"
                  >
                    {/* 1:1 Square PFP */}
                    <div className="h-13 w-13 shrink-0 overflow-hidden border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                      <img
                        src={avatarSrc}
                        alt={log.heading}
                        referrerPolicy="no-referrer"
                        className="h-full w-full object-cover aspect-square"
                      />
                    </div>

                    {/* Log Details: Heading, 1-line preview, date stamp · time stamp */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5">
                        {log.pinned && (
                          <Pin className="h-3.5 w-3.5 shrink-0 text-[#3366cc] fill-[#3366cc]" />
                        )}
                        <h2 className="font-wiki-serif text-lg font-bold leading-snug text-[var(--wiki-text)] group-hover:text-[#3366cc] truncate">
                          {log.heading}
                        </h2>
                      </div>

                      <p className="mt-0.5 truncate font-wiki-prose text-xs text-[var(--wiki-muted)]">
                        {log.plainPreview}
                      </p>

                      <div className="mt-1.5 flex items-center gap-1.5 font-wiki-mono text-[11px] text-[var(--wiki-muted)]">
                        <span>{log.dateStamp}</span>
                        <span aria-hidden="true">·</span>
                        <span>{log.timeStamp}</span>
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
                              setExportingLog(log);
                            }}
                            className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)]"
                          >
                            <Download className="h-4 w-4 text-[#3366cc]" />
                            <span>Export</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => handleDeleteLog(log.id)}
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

      {/* Multi-Format Export Modal (.txt, .md, .pdf, .rtf, .html, .json, .csv, .docx, .xml, .tsv) */}
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
                Choose a file format to download this log:
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

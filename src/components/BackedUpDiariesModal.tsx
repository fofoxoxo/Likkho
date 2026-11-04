import React, { useEffect, useState } from 'react';
import {
  ArrowLeft,
  Search,
  X,
  BookOpen,
  Eye,
  Trash2,
  Lock,
  Unlock,
  KeyRound,
  Fingerprint,
  RotateCcw,
  AlertTriangle,
  CheckCircle2,
  Volume2,
  Image as ImageIcon,
  Calendar,
  Pin,
  Clock,
  Play,
  Pause,
} from 'lucide-react';
import {
  CanvasAudioAttachment,
  CanvasDraggableImage,
  DiaryLog,
  VaultBackupBundle,
  VaultMode,
  hashPassphrase,
  performStreamingVaultBackup,
  performStreamingVaultRestore,
  getRemainingPasscodeCooldownSeconds,
  recordPasscodeFailure,
  resetPasscodeFailures,
} from '../utils/cryptoVault';
import { getImageObjectUrl } from '../utils/imageStorage';
import { acquireAudioPlaybackUrl, releaseAudioPlaybackUrl } from '../utils/audioStorage';

interface BackedUpDiariesModalProps {
  vaultMode: VaultMode;
  savedKeyHash: string | null;
  biometricsEnabled: boolean;
  onClose: () => void;
  onRestoreSingleDiary?: (log: DiaryLog) => void;
  onGoToBackupRestore?: () => void;
}

export const BackedUpDiariesModal: React.FC<BackedUpDiariesModalProps> = ({
  vaultMode,
  savedKeyHash,
  biometricsEnabled,
  onClose,
  onRestoreSingleDiary,
  onGoToBackupRestore,
}) => {
  // Vault unlock state
  const [passphrase, setPassphrase] = useState<string>('');
  const [showPassphrase, setShowPassphrase] = useState<boolean>(false);
  const [isUnlocked, setIsUnlocked] = useState<boolean>(false);
  const [activePassphrase, setActivePassphrase] = useState<string>('');
  const [activeKeyHash, setActiveKeyHash] = useState<string>('');
  const [isBusy, setIsBusy] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [cooldownSec, setCooldownSec] = useState<number>(() =>
    getRemainingPasscodeCooldownSeconds()
  );

  // Loaded backup bundle state
  const [bundle, setBundle] = useState<VaultBackupBundle | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isSearchOpen, setIsSearchOpen] = useState<boolean>(false);

  // Read Modal state
  const [readingLog, setReadingLog] = useState<DiaryLog | null>(null);
  const [readingDiaryUnlocked, setReadingDiaryUnlocked] = useState<boolean>(false);
  const [diaryPinInput, setDiaryPinInput] = useState<string>('');
  const [diaryPinError, setDiaryPinError] = useState<string | null>(null);

  // Audio playback state for reading log
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);
  const [audioUrls, setAudioUrls] = useState<Record<string, string>>({});
  const [imageUrls, setImageUrls] = useState<Record<string, string>>({});
  const audioElementRef = React.useRef<HTMLAudioElement | null>(null);

  // Delete Confirmation state
  const [pendingDeleteLog, setPendingDeleteLog] = useState<DiaryLog | null>(null);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  // Sync cooldown timer
  useEffect(() => {
    const sync = () => setCooldownSec(getRemainingPasscodeCooldownSeconds());
    sync();
    const timer = window.setInterval(sync, 500);
    return () => window.clearInterval(timer);
  }, []);

  // Cleanup audio playback on unmount or when reading log changes
  useEffect(() => {
    return () => {
      if (audioElementRef.current) {
        audioElementRef.current.pause();
        audioElementRef.current = null;
      }
      Object.values(audioUrls).forEach((url) => releaseAudioPlaybackUrl(url));
    };
  }, [audioUrls]);

  // Load Object URLs for images & audios when reading a log
  useEffect(() => {
    if (!readingLog) {
      setImageUrls({});
      setAudioUrls({});
      setReadingDiaryUnlocked(false);
      setDiaryPinInput('');
      setDiaryPinError(null);
      return;
    }

    if (!readingLog.diaryLockPin) {
      setReadingDiaryUnlocked(true);
    } else {
      setReadingDiaryUnlocked(false);
    }

    // Resolve Image URLs
    const newImgUrls: Record<string, string> = {};
    if (readingLog.canvasImages && Array.isArray(readingLog.canvasImages)) {
      for (const img of readingLog.canvasImages) {
        if (img.dataUrl) {
          const url = getImageObjectUrl(img.dataUrl);
          if (url) newImgUrls[img.id] = url;
        }
      }
    }
    setImageUrls(newImgUrls);

    // Resolve Audio URLs
    const newAudioUrls: Record<string, string> = {};
    const audioList = readingLog.audioAttachments;
    if (audioList && Array.isArray(audioList)) {
      (async () => {
        for (const att of audioList) {
          try {
            const res = await acquireAudioPlaybackUrl(att);
            if (res && res.url) newAudioUrls[att.id] = res.url;
          } catch {
            // ignore
          }
        }
        setAudioUrls(newAudioUrls);
      })();
    }
  }, [readingLog]);

  const handleUnlockVault = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const rem = getRemainingPasscodeCooldownSeconds();
    if (rem > 0) {
      setCooldownSec(rem);
      setErrorMessage(`Too many wrong attempts. Please wait ${rem}s.`);
      return;
    }

    if (!passphrase.trim()) {
      setErrorMessage('Please enter your Encryption Key.');
      return;
    }

    setIsBusy(true);
    setErrorMessage(null);
    try {
      const keyHash = await hashPassphrase(passphrase.trim(), vaultMode);
      if (savedKeyHash && keyHash !== savedKeyHash) {
        const fail = recordPasscodeFailure();
        if (fail.cooldownSeconds > 0) {
          setCooldownSec(fail.cooldownSeconds);
          setErrorMessage(`Incorrect Encryption Key. Locked for ${fail.cooldownSeconds}s.`);
        } else {
          setErrorMessage('Incorrect Encryption Key.');
        }
        setIsBusy(false);
        return;
      }

      resetPasscodeFailures();
      const restored = await performStreamingVaultRestore(passphrase.trim(), vaultMode);
      setBundle(restored);
      setActivePassphrase(passphrase.trim());
      setActiveKeyHash(keyHash);
      setIsUnlocked(true);
      setSuccessMessage('Vault unlocked successfully.');
      setTimeout(() => setSuccessMessage(null), 2500);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to read backup vault.';
      if (msg.includes('Incorrect Encryption Key')) {
        const fail = recordPasscodeFailure();
        if (fail.cooldownSeconds > 0) {
          setCooldownSec(fail.cooldownSeconds);
          setErrorMessage(`Incorrect Encryption Key. Locked for ${fail.cooldownSeconds}s.`);
        } else {
          setErrorMessage('Incorrect Encryption Key.');
        }
      } else {
        setErrorMessage(msg);
      }
    } finally {
      setIsBusy(false);
    }
  };

  const handleBiometricUnlock = () => {
    if (
      typeof window !== 'undefined' &&
      window.LikkhoNative &&
      typeof window.LikkhoNative.authenticateBiometric === 'function'
    ) {
      window.__onLikkhoBiometricResult = (success: boolean) => {
        window.__onLikkhoBiometricResult = undefined;
        if (success) {
          if (savedKeyHash && passphrase) {
            handleUnlockVault();
          } else {
            setSuccessMessage('Biometric verified. Please enter encryption key.');
          }
        } else {
          setErrorMessage('Biometric verification failed.');
        }
      };
      window.LikkhoNative.authenticateBiometric();
    }
  };

  const handleConfirmDeleteFromBackup = async () => {
    if (!pendingDeleteLog || !bundle || !activePassphrase) return;

    setIsDeleting(true);
    try {
      const updatedLogs = bundle.logs.filter((l) => l.id !== pendingDeleteLog.id);
      const updatedBundle: VaultBackupBundle = {
        logs: updatedLogs,
        customFonts: bundle.customFonts || [],
        micSettings: bundle.micSettings,
      };

      // Write updated bundle back to SAF / OPFS streaming backup file
      await performStreamingVaultBackup(
        updatedBundle,
        activePassphrase,
        activeKeyHash,
        vaultMode
      );

      setBundle(updatedBundle);
      setPendingDeleteLog(null);
      setSuccessMessage(`"${pendingDeleteLog.heading || 'Diary'}" deleted from backup vault.`);
      setTimeout(() => setSuccessMessage(null), 3000);
    } catch (err) {
      setErrorMessage(
        err instanceof Error
          ? `Failed to update backup file: ${err.message}`
          : 'Failed to delete diary from backup.'
      );
    } finally {
      setIsDeleting(false);
    }
  };

  const handleTogglePlayAudio = (att: CanvasAudioAttachment) => {
    const url = audioUrls[att.id];
    if (!url) return;

    if (playingAudioId === att.id) {
      if (audioElementRef.current) {
        audioElementRef.current.pause();
      }
      setPlayingAudioId(null);
    } else {
      if (audioElementRef.current) {
        audioElementRef.current.pause();
      }
      const audio = new Audio(url);
      audioElementRef.current = audio;
      setPlayingAudioId(att.id);
      audio.onended = () => setPlayingAudioId(null);
      audio.onerror = () => setPlayingAudioId(null);
      audio.play().catch(() => setPlayingAudioId(null));
    }
  };

  const extractPlainTextSnippet = (log: DiaryLog): string => {
    if (log.plainPreview && log.plainPreview.trim()) {
      return log.plainPreview.trim().slice(0, 160);
    }
    if (!log.contentHtml) return '';
    const temp = document.createElement('div');
    temp.innerHTML = log.contentHtml;
    const txt = temp.textContent || temp.innerText || '';
    return txt.trim().slice(0, 160);
  };

  const formatTimestamp = (ts: number): string => {
    try {
      const d = new Date(ts);
      return d.toLocaleDateString(undefined, {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return '';
    }
  };

  const filteredLogs = (bundle?.logs || []).filter((l) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    const headingMatch = (l.heading || '').toLowerCase().includes(q);
    const bodyMatch = (l.contentHtml || '').toLowerCase().includes(q);
    const previewMatch = (l.plainPreview || '').toLowerCase().includes(q);
    return headingMatch || bodyMatch || previewMatch;
  });

  // 1. Unlock Screen (if not yet unlocked)
  if (!isUnlocked) {
    return (
      <div className="fixed inset-0 z-50 flex flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
        {/* Header */}
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex h-10 w-10 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
              aria-label="Back to Settings"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <h1 className="font-wiki-serif text-xl font-bold leading-tight">
              Backed-up Diaries Vault
            </h1>
          </div>
        </header>

        <div className="flex flex-1 items-center justify-center p-4">
          <div className="w-full max-w-md border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-6 space-y-5">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[#3366cc]">
                <KeyRound className="h-6 w-6" />
              </div>
              <div>
                <h2 className="font-wiki-serif text-lg font-bold">
                  Unlock Backup Vault
                </h2>
                <p className="text-xs text-[var(--wiki-muted)]">
                  Enter your encryption key to view and manage backed-up diaries.
                </p>
              </div>
            </div>

            {errorMessage && (
              <div className="flex items-center gap-2 border border-[#b32424] bg-[#b32424]/10 px-3 py-2 text-xs font-medium text-[#b32424]">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                <span>{errorMessage}</span>
              </div>
            )}

            {successMessage && (
              <div className="flex items-center gap-2 border border-[#14866d] bg-[#14866d]/10 px-3 py-2 text-xs font-medium text-[#14866d]">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>{successMessage}</span>
              </div>
            )}

            <form onSubmit={handleUnlockVault} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[var(--wiki-text)] mb-1">
                  Encryption Key
                </label>
                <div className="relative">
                  <input
                    type={showPassphrase ? 'text' : 'password'}
                    value={passphrase}
                    onChange={(e) => {
                      setPassphrase(e.target.value);
                      setErrorMessage(null);
                    }}
                    placeholder="Enter your key..."
                    disabled={isBusy || cooldownSec > 0}
                    className="h-11 w-full border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 pr-10 text-sm font-wiki-mono outline-none focus:border-[#3366cc]"
                    autoFocus
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassphrase(!showPassphrase)}
                    className="absolute right-2 top-2.5 flex h-6 w-6 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
                    tabIndex={-1}
                  >
                    {showPassphrase ? <Eye className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="submit"
                  disabled={isBusy || cooldownSec > 0 || !passphrase.trim()}
                  className="flex h-11 flex-1 items-center justify-center gap-2 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] disabled:opacity-50"
                >
                  <Unlock className="h-4 w-4" />
                  <span>
                    {isBusy
                      ? 'Reading Backup Vault...'
                      : cooldownSec > 0
                      ? `Locked (${cooldownSec}s)`
                      : 'Unlock & Read Diaries'}
                  </span>
                </button>

                {biometricsEnabled && (
                  <button
                    type="button"
                    onClick={handleBiometricUnlock}
                    disabled={isBusy}
                    className="flex h-11 w-11 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[#3366cc] hover:border-[#3366cc]"
                    title="Unlock with Biometrics"
                  >
                    <Fingerprint className="h-5 w-5" />
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      </div>
    );
  }

  // 2. Backed-up Diaries Main Page
  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Header */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
        {isSearchOpen ? (
          <div className="flex flex-1 items-center gap-2 min-w-0">
            <button
              type="button"
              onClick={() => {
                setIsSearchOpen(false);
                setSearchQuery('');
              }}
              className="flex h-9 w-9 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
              aria-label="Close Search"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <Search className="h-4 w-4 shrink-0 text-[#3366cc]" />
            <input
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search backed-up diaries..."
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
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-3 min-w-0">
              <button
                type="button"
                onClick={onClose}
                className="flex h-10 w-10 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
                aria-label="Back to Settings"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
              <div className="min-w-0">
                <h1 className="font-wiki-serif text-lg sm:text-xl font-bold leading-tight truncate">
                  Backed-up Diaries
                </h1>
                <div className="text-[11px] text-[var(--wiki-muted)] truncate">
                  Vault Folder: {vaultMode === 'decoy' ? '.likkho_decoy_backups' : '.likkho_backups'} ({bundle?.logs.length || 0} entries)
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                onClick={() => setIsSearchOpen(true)}
                className="flex h-10 w-10 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]"
                title="Search Backed-up Diaries"
              >
                <Search className="h-4 w-4 text-[#3366cc]" />
              </button>
            </div>
          </>
        )}
      </header>

      {/* Status Banners */}
      {successMessage && (
        <div className="flex items-center gap-2 border-b border-[#14866d] bg-[#14866d]/10 px-4 py-2.5 text-xs font-medium text-[#14866d]">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {errorMessage && (
        <div className="flex items-center gap-2 border-b border-[#b32424] bg-[#b32424]/10 px-4 py-2.5 text-xs font-medium text-[#b32424]">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {filteredLogs.length === 0 ? (
          <div className="flex flex-col items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-6 py-16 text-center space-y-3">
            <div className="flex h-14 w-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[#3366cc]">
              <BookOpen className="h-7 w-7" />
            </div>
            <h2 className="font-wiki-serif text-lg font-bold">
              {searchQuery.trim() ? 'No Matching Backed-up Diaries' : 'No Backed-up Diaries Found'}
            </h2>
            <p className="max-w-sm text-xs leading-relaxed text-[var(--wiki-muted)]">
              {searchQuery.trim()
                ? `No backed-up entry matches "${searchQuery}".`
                : 'There are currently no diaries in your backup vault file. When you back up your diaries from Backup & Restore, they will be listed here for reading and selective deletion.'}
            </p>
            {onGoToBackupRestore && !searchQuery.trim() && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onGoToBackupRestore();
                }}
                className="mt-2 flex h-9 items-center gap-2 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0]"
              >
                Go to Backup &amp; Restore
              </button>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            {filteredLogs.map((log) => {
              const snippet = extractPlainTextSnippet(log);
              const imgCount = (log.canvasImages || []).length;
              const audioCount = (log.audioAttachments || []).length;

              return (
                <article
                  key={log.id}
                  className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-4 space-y-2 hover:border-[#3366cc]/60 transition-colors"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        {log.pinned && (
                          <span className="inline-flex items-center gap-1 border border-[#3366cc] bg-[#3366cc]/10 px-1.5 py-0.5 text-[10px] font-semibold text-[#3366cc]">
                            <Pin className="h-2.5 w-2.5" /> Pinned
                          </span>
                        )}
                        {log.diaryLockPin && (
                          <span className="inline-flex items-center gap-1 border border-[#ac6600] bg-[#ac6600]/10 px-1.5 py-0.5 text-[10px] font-semibold text-[#ac6600]">
                            <Lock className="h-2.5 w-2.5" /> Locked
                          </span>
                        )}
                        <h3 className="font-wiki-serif text-base font-bold text-[var(--wiki-text)] truncate">
                          {log.heading || 'Untitled Diary'}
                        </h3>
                      </div>

                      <div className="mt-1 flex items-center gap-2 text-[11px] text-[var(--wiki-muted)]">
                        <Calendar className="h-3 w-3 shrink-0" />
                        <span>{formatTimestamp(log.createdAt || log.updatedAt)}</span>
                      </div>
                    </div>

                    {/* Action Buttons: Read & Delete */}
                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => setReadingLog(log)}
                        className="flex h-9 items-center gap-1.5 border border-[#3366cc] bg-[#3366cc] px-3 text-xs font-semibold text-white hover:bg-[#2a56b0]"
                        title="Read backed-up diary"
                      >
                        <Eye className="h-4 w-4" />
                        <span>Read</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setPendingDeleteLog(log)}
                        className="flex h-9 w-9 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-muted)] hover:border-[#b32424] hover:text-[#b32424]"
                        title="Delete from backup vault"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>

                  {snippet && (
                    <p className="text-xs leading-relaxed text-[var(--wiki-muted)] line-clamp-2">
                      {snippet}
                    </p>
                  )}

                  {(imgCount > 0 || audioCount > 0) && (
                    <div className="flex items-center gap-3 pt-1 text-[11px] text-[var(--wiki-muted)]">
                      {imgCount > 0 && (
                        <span className="inline-flex items-center gap-1">
                          <ImageIcon className="h-3.5 w-3.5 text-[#3366cc]" />
                          {imgCount} {imgCount === 1 ? 'image' : 'images'}
                        </span>
                      )}
                      {audioCount > 0 && (
                        <span className="inline-flex items-center gap-1">
                          <Volume2 className="h-3.5 w-3.5 text-[#14866d]" />
                          {audioCount} {audioCount === 1 ? 'audio' : 'audios'}
                        </span>
                      )}
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </div>

      {/* Read Diary Full Modal */}
      {readingLog && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center bg-black/75 p-3 sm:p-6 backdrop-blur-xs"
          onClick={() => setReadingLog(null)}
        >
          <div
            className="flex h-full max-h-[92vh] w-full max-w-2xl flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <header className="flex h-14 shrink-0 items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4">
              <div className="flex items-center gap-2 min-w-0">
                <BookOpen className="h-5 w-5 shrink-0 text-[#3366cc]" />
                <h3 className="font-wiki-serif text-base sm:text-lg font-bold truncate">
                  {readingLog.heading || 'Untitled Diary'}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setReadingLog(null)}
                className="flex h-9 w-9 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-5 w-5" />
              </button>
            </header>

            {/* If Diary is locked with its own PIN and not unlocked yet */}
            {readingLog.diaryLockPin && !readingDiaryUnlocked ? (
              <div className="flex flex-1 flex-col items-center justify-center p-6 text-center space-y-4">
                <div className="flex h-12 w-12 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[#ac6600]">
                  <Lock className="h-6 w-6" />
                </div>
                <h4 className="font-wiki-serif text-lg font-bold">
                  This Backed-up Diary is Password Protected
                </h4>
                <p className="text-xs text-[var(--wiki-muted)] max-w-xs">
                  Enter this diary's 4-digit passcode to unlock and read its content.
                </p>
                {diaryPinError && (
                  <div className="text-xs font-semibold text-[#b32424]">
                    {diaryPinError}
                  </div>
                )}
                <div className="flex items-center gap-2 w-full max-w-xs">
                  <input
                    type="password"
                    maxLength={10}
                    value={diaryPinInput}
                    onChange={(e) => setDiaryPinInput(e.target.value)}
                    placeholder="Diary passcode..."
                    className="h-10 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-center text-sm font-wiki-mono outline-none focus:border-[#3366cc]"
                    autoFocus
                  />
                  <button
                    type="button"
                    onClick={() => {
                      if (diaryPinInput === readingLog.diaryLockPin) {
                        setReadingDiaryUnlocked(true);
                        setDiaryPinError(null);
                      } else {
                        setDiaryPinError('Incorrect passcode for this diary.');
                      }
                    }}
                    className="h-10 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0]"
                  >
                    Unlock
                  </button>
                </div>
              </div>
            ) : (
              /* Diary Full Reading Content */
              <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-5">
                {/* Meta details */}
                <div className="border-b border-[var(--wiki-hairline)] pb-3">
                  <h2 className="font-wiki-serif text-2xl font-bold">
                    {readingLog.heading || 'Untitled Diary'}
                  </h2>
                  <div className="mt-1 flex flex-wrap items-center gap-4 text-xs text-[var(--wiki-muted)]">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3.5 w-3.5" />
                      {formatTimestamp(readingLog.createdAt || readingLog.updatedAt)}
                    </span>
                    {readingLog.pinned && (
                      <span className="text-[#3366cc] font-medium">• Pinned</span>
                    )}
                  </div>
                </div>

                {/* Body Content */}
                <div
                  className="font-wiki-serif text-sm leading-relaxed text-[var(--wiki-text)] space-y-3 break-words"
                  dangerouslySetInnerHTML={{
                    __html: readingLog.contentHtml || '<p class="text-[var(--wiki-muted)]">Empty content.</p>',
                  }}
                />

                {/* Attached Images */}
                {readingLog.canvasImages && readingLog.canvasImages.length > 0 && (
                  <div className="border-t border-[var(--wiki-hairline)] pt-4 space-y-2">
                    <h4 className="font-wiki-serif text-xs font-bold uppercase tracking-wider text-[var(--wiki-muted)]">
                      Attached Photos ({readingLog.canvasImages.length})
                    </h4>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                      {readingLog.canvasImages.map((img: CanvasDraggableImage) => {
                        const src = imageUrls[img.id] || img.dataUrl;
                        return (
                          <div
                            key={img.id}
                            className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-1 overflow-hidden"
                          >
                            <img
                              src={src}
                              alt="Attached photo"
                              className="h-32 w-full object-cover"
                            />
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Attached Audio Recordings */}
                {readingLog.audioAttachments && readingLog.audioAttachments.length > 0 && (
                  <div className="border-t border-[var(--wiki-hairline)] pt-4 space-y-2">
                    <h4 className="font-wiki-serif text-xs font-bold uppercase tracking-wider text-[var(--wiki-muted)]">
                      Attached Voice Notes ({readingLog.audioAttachments.length})
                    </h4>
                    <div className="space-y-2">
                      {readingLog.audioAttachments.map((att: CanvasAudioAttachment) => {
                        const isPlaying = playingAudioId === att.id;
                        return (
                          <div
                            key={att.id}
                            className="flex items-center justify-between border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 py-2 text-xs"
                          >
                            <div className="flex items-center gap-2 min-w-0">
                              <Volume2 className="h-4 w-4 text-[#14866d] shrink-0" />
                              <span className="font-medium truncate">
                                {att.name || 'Voice Note'}
                              </span>
                              {att.durationSec && (
                                <span className="text-[10px] text-[var(--wiki-muted)]">
                                  ({Math.round(att.durationSec)}s)
                                </span>
                              )}
                            </div>

                            <button
                              type="button"
                              onClick={() => handleTogglePlayAudio(att)}
                              className="flex h-8 items-center gap-1 bg-[#14866d] px-3 text-[11px] font-semibold text-white hover:bg-[#116f5a]"
                            >
                              {isPlaying ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                              <span>{isPlaying ? 'Pause' : 'Play'}</span>
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Footer with "Restore to App" and "Close" */}
            <footer className="flex items-center justify-between border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              {onRestoreSingleDiary && (
                <button
                  type="button"
                  onClick={() => {
                    onRestoreSingleDiary(readingLog);
                    setSuccessMessage(`Restored "${readingLog.heading || 'Diary'}" to in-app diaries.`);
                    setTimeout(() => setSuccessMessage(null), 3000);
                  }}
                  className="flex h-9 items-center gap-1.5 border border-[#14866d] bg-[#14866d]/10 px-3 text-xs font-semibold text-[#14866d] hover:bg-[#14866d] hover:text-white transition-colors"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  <span>Restore this Diary to App</span>
                </button>
              )}

              <button
                type="button"
                onClick={() => setReadingLog(null)}
                className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-4 text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)]"
              >
                Close
              </button>
            </footer>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {pendingDeleteLog && (
        <div
          className="fixed inset-0 z-70 flex items-center justify-center bg-black/75 p-4 backdrop-blur-xs"
          onClick={() => {
            if (!isDeleting) setPendingDeleteLog(null);
          }}
        >
          <div
            className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0 text-[#b32424]" />
                <h3 className="font-wiki-serif text-base font-bold">
                  Delete from Backup Vault?
                </h3>
              </div>
              <button
                type="button"
                disabled={isDeleting}
                onClick={() => setPendingDeleteLog(null)}
                className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-4 space-y-4">
              <p className="text-xs leading-relaxed text-[var(--wiki-text)]">
                Are you sure you want to permanently delete{' '}
                <strong>"{pendingDeleteLog.heading || 'Untitled Diary'}"</strong> from your backup vault file?
              </p>
              <p className="text-[11px] leading-relaxed text-[var(--wiki-muted)]">
                This will remove it from the backup file in your chosen folder on disk. This action cannot be undone.
              </p>

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={() => setPendingDeleteLog(null)}
                  className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-medium text-[var(--wiki-text)]"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={handleConfirmDeleteFromBackup}
                  className="flex h-9 items-center gap-1.5 bg-[#b32424] px-4 text-xs font-semibold text-white hover:bg-[#941d1d] disabled:opacity-50"
                >
                  <Trash2 className="h-4 w-4" />
                  <span>{isDeleting ? 'Deleting...' : 'Delete from Vault'}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

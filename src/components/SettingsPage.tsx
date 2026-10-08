import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Moon,
  Sun,
  KeyRound,
  FolderLock,
  ChevronRight,
  Check,
  Trash2,
  Mic,
  Fingerprint,
  BookOpenCheck,
  Search,
  X,
} from 'lucide-react';
import {
  AudioFormatOption,
  MicRecordingSettings,
  VaultMode,
} from '../utils/cryptoVault';
import {
  deleteSavedWordAnalysis,
  getSavedWordAnalyses,
  WordAnalysisRecord,
} from '../utils/wordAnalysisEngine';
import { WordAnalysisModal } from './WordAnalysisModal';

interface SettingsPageProps {
  vaultMode: VaultMode;
  darkMode: boolean;
  onToggleDarkMode: () => void;
  savedPasscode: string | null;
  onUpdatePasscode: (newPasscode: string | null) => boolean;
  secondaryPasscode: string | null;
  onUpdateSecondaryPasscode: (newPasscode: string | null) => boolean;
  biometricsEnabled: boolean;
  onToggleBiometrics: (enabled: boolean) => void;
  onOpenBackupRestore: () => void;
  onBackToHome: () => void;
  micSettings: MicRecordingSettings;
  onUpdateMicSettings: (next: MicRecordingSettings) => void;
}

const AUDIO_FORMATS: { id: AudioFormatOption; label: string }[] = [
  { id: 'wav', label: '.wav' },
  { id: 'flac', label: '.flac' },
  { id: 'm4a', label: '.m4a' },
  { id: 'aac', label: '.aac' },
  { id: 'mp3', label: '.mp3' },
  { id: 'ogg', label: '.ogg' },
  { id: 'webm', label: '.webm' },
];

const SAMPLE_RATES: { value: MicRecordingSettings['sampleRate']; label: string }[] = [
  { value: 8000, label: '8,000 Hz' },
  { value: 16000, label: '16,000 Hz' },
  { value: 22050, label: '22,050 Hz' },
  { value: 44100, label: '44,100 Hz' },
  { value: 48000, label: '48,000 Hz' },
];

const BIT_RATES: { value: MicRecordingSettings['bitRate']; label: string }[] = [
  { value: 64000, label: '64 kbps' },
  { value: 128000, label: '128 kbps' },
  { value: 192000, label: '192 kbps' },
  { value: 256000, label: '256 kbps' },
  { value: 320000, label: '320 kbps' },
];

export const SettingsPage: React.FC<SettingsPageProps> = ({
  vaultMode,
  darkMode,
  onToggleDarkMode,
  savedPasscode,
  onUpdatePasscode,
  secondaryPasscode,
  onUpdateSecondaryPasscode,
  biometricsEnabled,
  onToggleBiometrics,
  onOpenBackupRestore,
  onBackToHome,
  micSettings,
  onUpdateMicSettings,
}) => {
  const [showPasscodeModal, setShowPasscodeModal] = useState<boolean>(false);
  // Whether the currently open Set Passcode modal is configuring the covert Secondary Vault (via 10s hold in Primary)
  const [isConfiguringSecondaryViaHold, setIsConfiguringSecondaryViaHold] =
    useState<boolean>(false);
  const [pinInput, setPinInput] = useState<string>(savedPasscode || '');
  const [pinError, setPinError] = useState<string>('');
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  // Saved Word Analyses (Offline Alphabetical Dictionary Page in Settings)
  const [showSavedWordsPage, setShowSavedWordsPage] = useState<boolean>(false);
  const [isWordSearchOpen, setIsWordSearchOpen] = useState<boolean>(false);
  const [savedWords, setSavedWords] = useState<WordAnalysisRecord[]>(() =>
    getSavedWordAnalyses(vaultMode)
  );
  const [wordFilterQuery, setWordFilterQuery] = useState<string>('');
  const [selectedWordRecord, setSelectedWordRecord] =
    useState<WordAnalysisRecord | null>(null);

  useEffect(() => {
    setSavedWords(getSavedWordAnalyses(vaultMode));
  }, [vaultMode, showSavedWordsPage]);

  // 10-second long press on "Set Passcode" button (only in Primary Vault when Primary Passcode is already active)
  const holdTimerRef = useRef<number | null>(null);
  const holdTriggeredRef = useRef<boolean>(false);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => {
      setToastMsg((prev) => (prev === msg ? null : prev));
    }, 2600);
  };

  const startSetPasscodeHold = () => {
    holdTriggeredRef.current = false;
    if (holdTimerRef.current) {
      window.clearTimeout(holdTimerRef.current);
      holdTimerRef.current = null;
    }

    // Secondary vault can only be activated from Primary Vault when Primary Passcode is already set
    if (vaultMode !== 'primary' || !savedPasscode) {
      return;
    }

    holdTimerRef.current = window.setTimeout(() => {
      holdTimerRef.current = null;
      holdTriggeredRef.current = true;
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        navigator.vibrate(70);
      }
      setIsConfiguringSecondaryViaHold(true);
      setPinInput(secondaryPasscode || '');
      setPinError('');
      setShowPasscodeModal(true);
    }, 10000); // 10 seconds continuous long press
  };

  const cancelSetPasscodeHold = () => {
    if (holdTimerRef.current) {
      window.clearTimeout(holdTimerRef.current);
      holdTimerRef.current = null;
    }
  };

  const handleSetPasscodeClick = () => {
    if (holdTriggeredRef.current) {
      holdTriggeredRef.current = false;
      return;
    }
    setIsConfiguringSecondaryViaHold(false);
    setPinInput(savedPasscode || '');
    setPinError('');
    setShowPasscodeModal(true);
  };

  const handleSavePin = (e: React.FormEvent) => {
    e.preventDefault();
    const cleaned = pinInput.replace(/\D/g, '');
    if (cleaned.length < 4 || cleaned.length > 6) {
      setPinError('Passcode must be 4 to 6 digits.');
      return;
    }

    if (isConfiguringSecondaryViaHold) {
      const ok = onUpdateSecondaryPasscode(cleaned);
      if (!ok) {
        setPinError('Passcode must be different from existing passcode.');
        return;
      }
      setShowPasscodeModal(false);
      setIsConfiguringSecondaryViaHold(false);
      showToast('Passcode saved.');
      return;
    }

    const ok = onUpdatePasscode(cleaned);
    if (!ok) {
      setPinError('Passcode must be different from existing passcode.');
      return;
    }
    setShowPasscodeModal(false);
    showToast('Passcode saved.');
  };

  const handleRemovePin = () => {
    if (isConfiguringSecondaryViaHold) {
      onUpdateSecondaryPasscode(null);
      setShowPasscodeModal(false);
      setIsConfiguringSecondaryViaHold(false);
      showToast('Passcode removed.');
      return;
    }

    onUpdatePasscode(null);
    if (vaultMode === 'primary') {
      onToggleBiometrics(false);
    }
    setShowPasscodeModal(false);
    showToast('Passcode removed.');
  };

  const handleBiometricToggleClick = () => {
    if (!savedPasscode) {
      setIsConfiguringSecondaryViaHold(false);
      setPinInput('');
      setPinError('Please set a Passcode first to enable Biometric unlock.');
      setShowPasscodeModal(true);
      return;
    }

    const nextState = !biometricsEnabled;
    if (nextState) {
      // Request Android biometric permission / verification when enabling
      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.authenticateBiometric === 'function'
      ) {
        window.__onLikkhoBiometricResult = (success: boolean) => {
          if (success) {
            onToggleBiometrics(true);
            showToast('Biometric unlock enabled.');
          } else {
            showToast('Biometric verification cancelled.');
          }
          window.__onLikkhoBiometricResult = undefined;
        };
        window.LikkhoNative.authenticateBiometric();
        return;
      }
      onToggleBiometrics(true);
      showToast('Biometric unlock enabled.');
    } else {
      onToggleBiometrics(false);
      showToast('Biometric unlock disabled.');
    }
  };

  const currentModalHasExistingPasscode = isConfiguringSecondaryViaHold
    ? Boolean(secondaryPasscode)
    : Boolean(savedPasscode);

  const filteredSavedWords = savedWords.filter((item) =>
    item.word.toLowerCase().includes(wordFilterQuery.trim().toLowerCase())
  );

  // Dedicated "Saved Word Analysis" Sub-Page when user clicks the "Saved Word Analysis" button in Settings
  if (showSavedWordsPage) {
    return (
      <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
        {/* Header with Back Button, Title, and Top Search Button */}
        <header className="flex h-14 shrink-0 items-center justify-between gap-2 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
          {isWordSearchOpen ? (
            <div className="flex flex-1 items-center gap-2 min-w-0">
              <button
                type="button"
                onClick={() => {
                  setIsWordSearchOpen(false);
                  setWordFilterQuery('');
                }}
                className="flex h-9 w-9 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
                aria-label="Close Search"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
              <Search className="h-4 w-4 shrink-0 text-[#3366cc]" />
              <input
                type="search"
                value={wordFilterQuery}
                onChange={(e) => setWordFilterQuery(e.target.value)}
                placeholder="Search saved words..."
                className="h-9 min-w-0 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                autoFocus
              />
              <button
                type="button"
                onClick={() => {
                  setIsWordSearchOpen(false);
                  setWordFilterQuery('');
                }}
                className="flex h-9 w-9 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
                title="Close search"
                aria-label="Close search"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-3 min-w-0">
                <button
                  type="button"
                  onClick={() => {
                    setShowSavedWordsPage(false);
                    setIsWordSearchOpen(false);
                    setWordFilterQuery('');
                  }}
                  className="flex h-10 w-10 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
                  aria-label="Back to Settings"
                >
                  <ArrowLeft className="h-5 w-5" />
                </button>
                <div className="min-w-0">
                  <h1 className="font-wiki-serif text-xl font-bold leading-tight truncate">
                    Saved Word Analysis
                  </h1>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setIsWordSearchOpen(true)}
                className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc] active:scale-[0.98] transition-all"
                title="Search saved words"
                aria-label="Search saved words"
              >
                <Search className="h-4 w-4 text-[#3366cc]" />
              </button>
            </>
          )}
        </header>

        {/* Alphabetical (A–Z) Saved Words List */}
        <div className="flex-1 overflow-y-auto px-4 py-4">
          {savedWords.length === 0 ? (
            <div className="flex flex-col items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-6 py-16 text-center">
              <div className="flex h-12 w-12 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                <BookOpenCheck className="h-6 w-6 text-[#3366cc]" />
              </div>
              <h2 className="mt-3 font-wiki-serif text-lg font-bold">
                No Saved Words Yet
              </h2>
              <p className="mt-1 max-w-xs text-xs leading-relaxed text-[var(--wiki-muted)]">
                Words analyzed on the canvas will automatically save here in alphabetical order (A–Z) for offline viewing.
              </p>
            </div>
          ) : filteredSavedWords.length === 0 ? (
            <div className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-6 py-12 text-center">
              <p className="text-xs text-[var(--wiki-muted)]">
                No saved word matches <strong>"{wordFilterQuery}"</strong>.
              </p>
            </div>
          ) : (
            <div className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] divide-y divide-[var(--wiki-hairline)]">
              {filteredSavedWords.map((item) => (
                <div
                  key={item.word.toLowerCase()}
                  className="flex items-center justify-between gap-3 px-4 py-3.5 hover:bg-[var(--wiki-surface)] transition-colors"
                >
                  <button
                    type="button"
                    onClick={() => setSelectedWordRecord(item)}
                    className="flex-1 min-w-0 text-left"
                  >
                    <div className="flex items-center gap-2">
                      <span className="font-wiki-serif text-base font-bold text-[var(--wiki-text)]">
                        {item.word}
                      </span>
                      <span className="font-wiki-mono text-xs text-[#3366cc]">
                        {item.phonetics.phoneticTranscription}
                      </span>
                    </div>
                    <div className="mt-0.5 truncate text-xs text-[var(--wiki-muted)]">
                      {item.lexical.definitions[0]?.definition ||
                        item.morphology.partOfSpeech.join(', ')}
                    </div>
                  </button>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => setSelectedWordRecord(item)}
                      className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 py-1.5 text-xs font-semibold text-[#3366cc] hover:border-[#3366cc]"
                    >
                      View
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        const next = deleteSavedWordAnalysis(item.word, vaultMode);
                        setSavedWords(next);
                      }}
                      className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[#b32424]"
                      title="Delete saved word"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Offline Word Analysis Detail Modal */}
        {selectedWordRecord && (
          <WordAnalysisModal
            record={selectedWordRecord}
            onClose={() => setSelectedWordRecord(null)}
          />
        )}
      </div>
    );
  }

  return (
    <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Header */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onBackToHome}
            className="flex h-10 w-10 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
            aria-label="Back to Homepage"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <h1 className="font-wiki-serif text-xl font-bold leading-tight">
            Settings
          </h1>
        </div>
      </header>

      {/* Main Settings List */}
      <div className="flex-1 overflow-y-auto px-4 py-5 space-y-5">
        {toastMsg && (
          <div className="flex items-center gap-2 border border-[#14866d] bg-[#14866d]/10 px-3 py-2.5 text-xs font-medium text-[var(--wiki-text)]">
            <Check className="h-4 w-4 text-[#14866d]" />
            {toastMsg}
          </div>
        )}

        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] divide-y divide-[var(--wiki-hairline)]">
          {/* 1. Toggle Light / Dark Theme */}
          <button
            type="button"
            onClick={onToggleDarkMode}
            className="flex w-full items-center justify-between px-4 py-4 text-left hover:bg-[var(--wiki-surface)] transition-colors"
          >
            <div className="flex items-center gap-3.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                {darkMode ? (
                  <Moon className="h-5 w-5 text-[#6699ff]" />
                ) : (
                  <Sun className="h-5 w-5 text-[#ac6600]" />
                )}
              </div>
              <div>
                <div className="font-wiki-serif text-base font-bold">
                  Dark Theme
                </div>
                <div className="text-xs text-[var(--wiki-muted)]">
                  {darkMode ? 'Dark Mode' : 'Light Mode'}
                </div>
              </div>
            </div>

            <div
              className={`flex h-6 w-11 shrink-0 items-center rounded-full border p-0.5 transition-colors ${
                darkMode
                  ? 'border-[#3366cc] bg-[#3366cc] justify-end'
                  : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] justify-start'
              }`}
            >
              <span className="h-4 w-4 rounded-full bg-white shadow-2xs" />
            </div>
          </button>

          {/* 2. Set Passcode (10-second long press in Primary when Primary Passcode is set opens identical Set Passcode popup for Secondary Vault) */}
          <button
            type="button"
            onPointerDown={startSetPasscodeHold}
            onPointerUp={cancelSetPasscodeHold}
            onPointerLeave={cancelSetPasscodeHold}
            onPointerCancel={cancelSetPasscodeHold}
            onContextMenu={(e) => e.preventDefault()}
            onClick={handleSetPasscodeClick}
            className="flex w-full items-center justify-between px-4 py-4 text-left hover:bg-[var(--wiki-surface)] transition-colors select-none"
          >
            <div className="flex items-center gap-3.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                <KeyRound className="h-5 w-5 text-[#3366cc]" />
              </div>
              <div>
                <div className="font-wiki-serif text-base font-bold">
                  Set Passcode
                </div>
                <div className="text-xs text-[var(--wiki-muted)]">
                  {savedPasscode ? 'Passcode enabled' : 'Set a passcode for lock'}
                </div>
              </div>
            </div>
            <ChevronRight className="h-5 w-5 text-[var(--wiki-muted)]" />
          </button>

          {/* 3. Toggle Biometric Unlock (Hidden in Secondary Vault) */}
          {vaultMode === 'primary' && (
            <button
              type="button"
              onClick={handleBiometricToggleClick}
              className="flex w-full items-center justify-between px-4 py-4 text-left hover:bg-[var(--wiki-surface)] transition-colors"
            >
              <div className="flex items-center gap-3.5">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                  <Fingerprint className="h-5 w-5 text-[#3366cc]" />
                </div>
                <div>
                  <div className="font-wiki-serif text-base font-bold">
                    Biometric Unlock
                  </div>
                  <div className="text-xs text-[var(--wiki-muted)]">
                    {biometricsEnabled
                      ? 'Fingerprint / Face unlock enabled'
                      : 'Use device biometrics with passcode'}
                  </div>
                </div>
              </div>

              <div
                className={`flex h-6 w-11 shrink-0 items-center rounded-full border p-0.5 transition-colors ${
                  biometricsEnabled
                    ? 'border-[#3366cc] bg-[#3366cc] justify-end'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] justify-start'
                }`}
              >
                <span className="h-4 w-4 rounded-full bg-white shadow-2xs" />
              </div>
            </button>
          )}

          {/* 4. Backup & Restore */}
          <button
            type="button"
            onClick={onOpenBackupRestore}
            className="flex w-full items-center justify-between px-4 py-4 text-left hover:bg-[var(--wiki-surface)] transition-colors"
          >
            <div className="flex items-center gap-3.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                <FolderLock className="h-5 w-5 text-[#3366cc]" />
              </div>
              <div>
                <div className="font-wiki-serif text-base font-bold">
                  Backup &amp; Restore
                </div>
                <div className="text-xs text-[var(--wiki-muted)]">
                  Backup and restore diary text &amp; media
                </div>
              </div>
            </div>
            <ChevronRight className="h-5 w-5 text-[var(--wiki-muted)]" />
          </button>

          {/* 5. Saved Word Analysis Button (Opens dedicated Alphabetical A–Z Saved Words page with Search button) */}
          <button
            type="button"
            onClick={() => {
              setSavedWords(getSavedWordAnalyses(vaultMode));
              setIsWordSearchOpen(false);
              setWordFilterQuery('');
              setShowSavedWordsPage(true);
            }}
            className="flex w-full items-center justify-between px-4 py-4 text-left hover:bg-[var(--wiki-surface)] transition-colors"
          >
            <div className="flex items-center gap-3.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
                <BookOpenCheck className="h-5 w-5 text-[#3366cc]" />
              </div>
              <div>
                <div className="font-wiki-serif text-base font-bold">
                  Saved Word Analysis
                </div>
                <div className="text-xs text-[var(--wiki-muted)]">
                  View saved words in alphabetical order (A–Z)
                </div>
              </div>
            </div>
            <ChevronRight className="h-5 w-5 text-[var(--wiki-muted)]" />
          </button>
        </section>

        {/* 6. Microphone Recording Settings (Format, Sample Rate, Bitrate) */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4 space-y-4">
          <div className="flex items-center gap-2.5 border-b border-[var(--wiki-hairline)] pb-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
              <Mic className="h-4 w-4 text-[#3366cc]" />
            </div>
            <div>
              <h2 className="font-wiki-serif text-base font-bold">
                Microphone Recording
              </h2>
              <p className="text-xs text-[var(--wiki-muted)]">
                Audio format, sample rate, and bitrate
              </p>
            </div>
          </div>

          {/* Audio Format Selection */}
          <div>
            <label className="mb-1.5 block text-xs font-semibold text-[var(--wiki-text)]">
              Recording Format
            </label>
            <div className="grid grid-cols-4 gap-1.5">
              {AUDIO_FORMATS.map((fmt) => (
                <button
                  key={fmt.id}
                  type="button"
                  onClick={() =>
                    onUpdateMicSettings({ ...micSettings, format: fmt.id })
                  }
                  className={`py-2 border font-wiki-mono text-xs font-semibold transition-colors ${
                    micSettings.format === fmt.id
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                >
                  {fmt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Sample Rate & Bitrate */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-semibold text-[var(--wiki-text)]">
                Sample Rate
              </label>
              <select
                value={micSettings.sampleRate}
                onChange={(e) =>
                  onUpdateMicSettings({
                    ...micSettings,
                    sampleRate: Number(
                      e.target.value
                    ) as MicRecordingSettings['sampleRate'],
                  })
                }
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 font-wiki-mono text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              >
                {SAMPLE_RATES.map((sr) => (
                  <option key={sr.value} value={sr.value}>
                    {sr.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-[var(--wiki-text)]">
                Bitrate
              </label>
              <select
                value={micSettings.bitRate}
                onChange={(e) =>
                  onUpdateMicSettings({
                    ...micSettings,
                    bitRate: Number(
                      e.target.value
                    ) as MicRecordingSettings['bitRate'],
                  })
                }
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 font-wiki-mono text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              >
                {BIT_RATES.map((br) => (
                  <option key={br.value} value={br.value}>
                    {br.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </section>
      </div>

      {/* Offline Word Analysis Detail Modal */}
      {selectedWordRecord && (
        <WordAnalysisModal
          record={selectedWordRecord}
          onClose={() => setSelectedWordRecord(null)}
        />
      )}

      {/* Set Passcode Modal (Identical UI for Primary Passcode and Secondary Passcode via 10s hold) */}
      {showPasscodeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs">
          <div className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4 text-[var(--wiki-text)] shadow-xl">
            <h3 className="font-wiki-serif text-lg font-bold">
              Set Passcode
            </h3>
            <p className="mt-1 text-xs text-[var(--wiki-muted)]">
              Enter a 4 to 6 digit PIN.
            </p>

            <form onSubmit={handleSavePin} className="mt-4 space-y-3">
              <input
                type="password"
                inputMode="numeric"
                maxLength={6}
                value={pinInput}
                onChange={(e) => {
                  setPinInput(e.target.value.replace(/\D/g, ''));
                  setPinError('');
                }}
                placeholder="••••"
                className="h-11 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-center font-wiki-mono text-lg tracking-[0.4em] text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                autoFocus
              />

              {pinError && (
                <p className="text-xs font-medium text-[#b32424]">{pinError}</p>
              )}

              <div className="flex items-center justify-between pt-2">
                {currentModalHasExistingPasscode ? (
                  <button
                    type="button"
                    onClick={handleRemovePin}
                    className="flex h-10 items-center gap-1 border border-[#b32424]/40 px-3 text-xs font-medium text-[#b32424]"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    Remove
                  </button>
                ) : (
                  <div />
                )}

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setShowPasscodeModal(false);
                      setIsConfiguringSecondaryViaHold(false);
                    }}
                    className="h-10 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 text-xs font-medium"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="h-10 bg-[#3366cc] px-4 text-xs font-semibold text-white"
                  >
                    Save
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

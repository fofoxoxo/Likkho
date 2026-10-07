import React, { useState } from 'react';
import {
  ArrowLeft,
  Palette,
  KeyRound,
  FolderLock,
  ChevronRight,
  Check,
  Trash2,
  Mic,
  Fingerprint,
} from 'lucide-react';
import {
  AppThemeMode,
  AudioFormatOption,
  MicRecordingSettings,
} from '../utils/cryptoVault';

interface SettingsPageProps {
  themeMode: AppThemeMode;
  onChangeThemeMode: (mode: AppThemeMode) => void;
  savedPasscode: string | null;
  onUpdatePasscode: (newPasscode: string | null) => void;
  biometricsEnabled: boolean;
  onToggleBiometrics: (enabled: boolean) => void;
  onOpenBackupRestore: () => void;
  onBackToHome: () => void;
  micSettings: MicRecordingSettings;
  onUpdateMicSettings: (next: MicRecordingSettings) => void;
}

export const THEME_OPTIONS: {
  id: AppThemeMode;
  label: string;
  desc: string;
  bgPreview: string;
  surfacePreview: string;
  textPreview: string;
}[] = [
  {
    id: 'system',
    label: 'System Default',
    desc: 'Follows your Android OS Light / Dark setting automatically',
    bgPreview: '#ffffff',
    surfacePreview: '#101418',
    textPreview: '#202122',
  },
  {
    id: 'light',
    label: 'Classic Light',
    desc: 'Clean Wikipedia paper white',
    bgPreview: '#ffffff',
    surfacePreview: '#f8f9fa',
    textPreview: '#202122',
  },
  {
    id: 'amoled',
    label: 'Pure High-Contrast (AMOLED)',
    desc: 'True #000000 pitch black with crisp white text',
    bgPreview: '#000000',
    surfacePreview: '#0a0a0a',
    textPreview: '#ffffff',
  },
  {
    id: 'dark_charcoal',
    label: 'Dark Charcoal (Soft Dark)',
    desc: 'Low-glare charcoal slate for comfortable night writing',
    bgPreview: '#101418',
    surfacePreview: '#1a1f24',
    textPreview: '#eaecf0',
  },
  {
    id: 'grayscale',
    label: 'Grayscale (Multi-Shade Gray)',
    desc: 'Balanced neutral multi-shade monochrome gray',
    bgPreview: '#e5e7eb',
    surfacePreview: '#d1d5db',
    textPreview: '#111827',
  },
  {
    id: 'eink',
    label: 'E-Ink Paperwhite',
    desc: 'Matte warm paperwhite reading surface with ink contrast',
    bgPreview: '#f6f3e9',
    surfacePreview: '#ece7d8',
    textPreview: '#1c1a17',
  },
  {
    id: 'tinted_cool',
    label: 'Tinted Monochrome (Cool)',
    desc: 'Softnordic cool slate-blue monochrome tint',
    bgPreview: '#eef3f9',
    surfacePreview: '#e1eaf4',
    textPreview: '#132235',
  },
  {
    id: 'tinted_warm',
    label: 'Tinted Monochrome (Warm)',
    desc: 'Warm sepia parchment monochrome tint',
    bgPreview: '#f9f2ea',
    surfacePreview: '#f0e4d6',
    textPreview: '#2b1d10',
  },
];

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
  themeMode,
  onChangeThemeMode,
  savedPasscode,
  onUpdatePasscode,
  biometricsEnabled,
  onToggleBiometrics,
  onOpenBackupRestore,
  onBackToHome,
  micSettings,
  onUpdateMicSettings,
}) => {
  const [showPasscodeModal, setShowPasscodeModal] = useState<boolean>(false);
  const [pinInput, setPinInput] = useState<string>(savedPasscode || '');
  const [pinError, setPinError] = useState<string>('');
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => {
      setToastMsg((prev) => (prev === msg ? null : prev));
    }, 2600);
  };

  const handleSavePin = (e: React.FormEvent) => {
    e.preventDefault();
    const cleaned = pinInput.replace(/\D/g, '');
    if (cleaned.length < 4 || cleaned.length > 6) {
      setPinError('Passcode must be 4 to 6 digits.');
      return;
    }
    onUpdatePasscode(cleaned);
    setShowPasscodeModal(false);
    showToast('Passcode saved.');
  };

  const handleBiometricToggleClick = () => {
    if (!savedPasscode) {
      setPinInput('');
      setPinError('Please set a Passcode first to enable Biometric unlock.');
      setShowPasscodeModal(true);
      return;
    }

    const nextState = !biometricsEnabled;
    if (nextState) {
      // Request Android biometric permission / verification when enabling
      if (window.LikkhoNative && typeof window.LikkhoNative.authenticateBiometric === 'function') {
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

        {/* 1. App Themes Selector */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4 space-y-3">
          <div className="flex items-center gap-2.5 border-b border-[var(--wiki-hairline)] pb-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
              <Palette className="h-4 w-4 text-[#3366cc]" />
            </div>
            <div>
              <h2 className="font-wiki-serif text-base font-bold">
                App Theme
              </h2>
              <p className="text-xs text-[var(--wiki-muted)]">
                Choose your preferred reading &amp; writing color palette
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {THEME_OPTIONS.map((t) => {
              const isSelected = themeMode === t.id;
              return (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => onChangeThemeMode(t.id)}
                  className={`flex items-center justify-between gap-3 border p-3 text-left transition-colors ${
                    isSelected
                      ? 'border-[#3366cc] bg-[var(--wiki-surface)] ring-1 ring-[#3366cc]'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:bg-[var(--wiki-surface)]'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    {/* Swatch Preview */}
                    <div className="flex h-8 w-8 shrink-0 overflow-hidden border border-[var(--wiki-border)]">
                      {t.id === 'system' ? (
                        <>
                          <span className="h-full w-1/2 bg-[#ffffff]" />
                          <span className="h-full w-1/2 bg-[#101418]" />
                        </>
                      ) : (
                        <span
                          className="flex h-full w-full items-center justify-center font-wiki-serif text-xs font-bold"
                          style={{
                            backgroundColor: t.bgPreview,
                            color: t.textPreview,
                          }}
                        >
                          Aa
                        </span>
                      )}
                    </div>
                    <div className="min-w-0">
                      <div className="font-wiki-serif text-sm font-bold text-[var(--wiki-text)] truncate">
                        {t.label}
                      </div>
                      <div className="text-[11px] text-[var(--wiki-muted)] line-clamp-1">
                        {t.desc}
                      </div>
                    </div>
                  </div>
                  {isSelected && (
                    <Check className="h-4 w-4 shrink-0 text-[#3366cc]" />
                  )}
                </button>
              );
            })}
          </div>
        </section>

        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] divide-y divide-[var(--wiki-hairline)]">
          {/* 2. Set Passcode */}
          <button
            type="button"
            onClick={() => {
              setPinInput(savedPasscode || '');
              setPinError('');
              setShowPasscodeModal(true);
            }}
            className="flex w-full items-center justify-between px-4 py-4 text-left hover:bg-[var(--wiki-surface)] transition-colors"
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

          {/* 3. Toggle Biometric Unlock */}
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
        </section>

        {/* 5. Microphone Recording Settings (Format, Sample Rate, Bitrate) */}
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

      {/* Set Passcode Modal */}
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
                {savedPasscode ? (
                  <button
                    type="button"
                    onClick={() => {
                      onUpdatePasscode(null);
                      onToggleBiometrics(false);
                      setShowPasscodeModal(false);
                    }}
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
                    onClick={() => setShowPasscodeModal(false)}
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

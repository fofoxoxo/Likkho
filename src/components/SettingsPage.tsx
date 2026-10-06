import React, { useState } from 'react';
import {
  ArrowLeft,
  Moon,
  Sun,
  KeyRound,
  FolderLock,
  ChevronRight,
  Check,
  Trash2,
} from 'lucide-react';

interface SettingsPageProps {
  darkMode: boolean;
  onToggleDarkMode: () => void;
  savedPasscode: string | null;
  onUpdatePasscode: (newPasscode: string | null) => void;
  onOpenBackupRestore: () => void;
  onBackToHome: () => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  darkMode,
  onToggleDarkMode,
  savedPasscode,
  onUpdatePasscode,
  onOpenBackupRestore,
  onBackToHome,
}) => {
  const [showPasscodeModal, setShowPasscodeModal] = useState<boolean>(false);
  const [pinInput, setPinInput] = useState<string>(savedPasscode || '');
  const [pinError, setPinError] = useState<string>('');
  const [pinSavedToast, setPinSavedToast] = useState<boolean>(false);

  const handleSavePin = (e: React.FormEvent) => {
    e.preventDefault();
    const cleaned = pinInput.replace(/\D/g, '');
    if (cleaned.length < 4 || cleaned.length > 6) {
      setPinError('Passcode must be 4 to 6 digits.');
      return;
    }
    onUpdatePasscode(cleaned);
    setShowPasscodeModal(false);
    setPinSavedToast(true);
    setTimeout(() => setPinSavedToast(false), 2500);
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
      <div className="flex-1 overflow-y-auto px-4 py-5 space-y-4">
        {pinSavedToast && (
          <div className="flex items-center gap-2 border border-[#14866d] bg-[#14866d]/10 px-3 py-2.5 text-xs font-medium text-[var(--wiki-text)]">
            <Check className="h-4 w-4 text-[#14866d]" />
            Passcode saved.
          </div>
        )}

        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] divide-y divide-[var(--wiki-hairline)]">
          {/* 1. Toggle Dark Theme */}
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
                  {darkMode ? 'On' : 'Off'}
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

          {/* 3. Backup & Restore */}
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
                  Manage encrypted diary backup and restoration
                </div>
              </div>
            </div>
            <ChevronRight className="h-5 w-5 text-[var(--wiki-muted)]" />
          </button>
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

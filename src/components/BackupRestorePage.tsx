import React, { useState } from 'react';
import {
  ArrowLeft,
  ShieldCheck,
  KeyRound,
  HardDriveDownload,
  RotateCcw,
  CheckCircle2,
  AlertTriangle,
  Eye,
  EyeOff,
  Lock,
} from 'lucide-react';
import {
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  VaultBackupBundle,
  encryptVaultBundle,
  decryptVaultBundle,
  hashPassphrase,
  writeToPrivateSpecialFolder,
  readFromPrivateSpecialFolder,
} from '../utils/cryptoVault';

interface BackupRestorePageProps {
  logs: DiaryLog[];
  customFonts: CustomFontItem[];
  micSettings: MicRecordingSettings;
  savedKeyHash: string | null;
  onSaveKeyHash: (hash: string) => void;
  onRestoreBundle: (bundle: VaultBackupBundle) => void;
  onBackToHome: () => void;
}

export const BackupRestorePage: React.FC<BackupRestorePageProps> = ({
  logs,
  customFonts,
  micSettings,
  savedKeyHash,
  onSaveKeyHash,
  onRestoreBundle,
  onBackToHome,
}) => {
  const [newKey, setNewKey] = useState<string>('');
  const [confirmKey, setConfirmKey] = useState<string>('');
  const [showKeyText, setShowKeyText] = useState<boolean>(false);

  const [backupPassphrase, setBackupPassphrase] = useState<string>('');
  const [restorePassphrase, setRestorePassphrase] = useState<string>('');
  const [statusBanner, setStatusBanner] = useState<{
    type: 'success' | 'error';
    text: string;
  } | null>(null);
  const [isBusy, setIsBusy] = useState<boolean>(false);

  const handleSetEncryptionKey = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newKey.trim().length < 4) {
      setStatusBanner({
        type: 'error',
        text: 'Encryption Key must be at least 4 characters.',
      });
      return;
    }
    if (newKey !== confirmKey) {
      setStatusBanner({
        type: 'error',
        text: 'Encryption Keys do not match.',
      });
      return;
    }

    const hashed = await hashPassphrase(newKey.trim());
    onSaveKeyHash(hashed);
    setBackupPassphrase(newKey.trim());
    setNewKey('');
    setConfirmKey('');
    setStatusBanner({
      type: 'success',
      text: 'Encryption Key saved.',
    });
  };

  const handleBackup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!backupPassphrase.trim()) {
      setStatusBanner({
        type: 'error',
        text: 'Please enter your Encryption Key.',
      });
      return;
    }

    setIsBusy(true);
    try {
      const inputHash = await hashPassphrase(backupPassphrase.trim());
      if (savedKeyHash && inputHash !== savedKeyHash) {
        setStatusBanner({
          type: 'error',
          text: 'Incorrect Encryption Key.',
        });
        setIsBusy(false);
        return;
      }

      const encryptedEnvelope = await encryptVaultBundle(
        {
          logs,
          customFonts,
          micSettings,
        },
        backupPassphrase.trim()
      );
      await writeToPrivateSpecialFolder(
        encryptedEnvelope,
        logs.length,
        inputHash
      );
      if (!savedKeyHash) {
        onSaveKeyHash(inputHash);
      }
      setStatusBanner({
        type: 'success',
        text: 'Backup completed.',
      });
    } catch (err) {
      setStatusBanner({
        type: 'error',
        text: err instanceof Error ? err.message : 'Backup failed.',
      });
    } finally {
      setIsBusy(false);
    }
  };

  const handleRestore = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!restorePassphrase.trim()) {
      setStatusBanner({
        type: 'error',
        text: 'Please enter your Encryption Key.',
      });
      return;
    }

    setIsBusy(true);
    try {
      const { metadata, encryptedEnvelope } = await readFromPrivateSpecialFolder();
      if (!metadata.exists || !encryptedEnvelope) {
        setStatusBanner({
          type: 'error',
          text: 'No backup found to restore.',
        });
        setIsBusy(false);
        return;
      }

      const bundle = await decryptVaultBundle(
        encryptedEnvelope,
        restorePassphrase.trim()
      );
      const keyHash = await hashPassphrase(restorePassphrase.trim());
      onSaveKeyHash(keyHash);
      onRestoreBundle(bundle);
      setRestorePassphrase('');
      setStatusBanner({
        type: 'success',
        text: 'Data restored.',
      });
    } catch (err) {
      setStatusBanner({
        type: 'error',
        text: err instanceof Error ? err.message : 'Incorrect Encryption Key.',
      });
    } finally {
      setIsBusy(false);
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
            Backup &amp; Restore
          </h1>
        </div>
      </header>

      {/* Body — Clean UI with only useful texts and buttons, zero backend info */}
      <div className="flex-1 overflow-y-auto px-4 py-5 space-y-5">
        {statusBanner && (
          <div
            className={`flex items-start gap-2.5 border p-3 text-xs leading-relaxed ${
              statusBanner.type === 'success'
                ? 'border-[#14866d] bg-[#14866d]/10 text-[var(--wiki-text)]'
                : 'border-[#b32424] bg-[#b32424]/10 text-[#b32424]'
            }`}
          >
            {statusBanner.type === 'success' ? (
              <CheckCircle2 className="h-4 w-4 shrink-0 text-[#14866d] mt-0.5" />
            ) : (
              <AlertTriangle className="h-4 w-4 shrink-0 text-[#b32424] mt-0.5" />
            )}
            <div className="flex-1">{statusBanner.text}</div>
          </div>
        )}

        {/* 1. Set Encryption Key */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4">
          <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] pb-2.5">
            <div className="flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-[#3366cc]" />
              <h2 className="font-wiki-serif text-base font-bold">
                Set Encryption Key
              </h2>
            </div>
            {savedKeyHash && (
              <span className="text-xs font-medium text-[#14866d]">
                ✓ Key Set
              </span>
            )}
          </div>

          <form onSubmit={handleSetEncryptionKey} className="mt-3 space-y-3">
            <div className="relative">
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                Encryption Key
              </label>
              <input
                type={showKeyText ? 'text' : 'password'}
                value={newKey}
                onChange={(e) => setNewKey(e.target.value)}
                placeholder="Enter encryption key"
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 pr-10 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
              <button
                type="button"
                onClick={() => setShowKeyText((s) => !s)}
                className="absolute right-2.5 top-7 text-[var(--wiki-muted)]"
                aria-label="Toggle visibility"
              >
                {showKeyText ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                Confirm Encryption Key
              </label>
              <input
                type={showKeyText ? 'text' : 'password'}
                value={confirmKey}
                onChange={(e) => setConfirmKey(e.target.value)}
                placeholder="Confirm encryption key"
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
            </div>

            <button
              type="submit"
              className="flex h-10 items-center justify-center gap-2 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 text-xs font-semibold text-[var(--wiki-text)] hover:border-[#3366cc]"
            >
              <ShieldCheck className="h-4 w-4 text-[#3366cc]" />
              Save Key
            </button>
          </form>
        </section>

        {/* 2. Backup */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4">
          <div className="flex items-center gap-2 border-b border-[var(--wiki-hairline)] pb-2.5">
            <HardDriveDownload className="h-4 w-4 text-[#3366cc]" />
            <h2 className="font-wiki-serif text-base font-bold">
              Backup
            </h2>
          </div>

          <form onSubmit={handleBackup} className="mt-3 space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                Encryption Key
              </label>
              <input
                type={showKeyText ? 'text' : 'password'}
                value={backupPassphrase}
                onChange={(e) => setBackupPassphrase(e.target.value)}
                placeholder="Enter encryption key"
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
            </div>

            <button
              type="submit"
              disabled={isBusy}
              className="flex h-10 w-full items-center justify-center gap-2 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] disabled:opacity-50"
            >
              <Lock className="h-4 w-4" />
              {isBusy ? 'Backing up...' : 'Backup'}
            </button>
          </form>
        </section>

        {/* 3. Restore */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4">
          <div className="flex items-center gap-2 border-b border-[var(--wiki-hairline)] pb-2.5">
            <RotateCcw className="h-4 w-4 text-[#3366cc]" />
            <h2 className="font-wiki-serif text-base font-bold">
              Restore
            </h2>
          </div>

          <form onSubmit={handleRestore} className="mt-3 space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                Encryption Key
              </label>
              <input
                type={showKeyText ? 'text' : 'password'}
                value={restorePassphrase}
                onChange={(e) => setRestorePassphrase(e.target.value)}
                placeholder="Enter encryption key"
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
            </div>

            <button
              type="submit"
              disabled={isBusy}
              className="flex h-10 w-full items-center justify-center gap-2 border border-[#3366cc] bg-[var(--wiki-surface)] px-4 text-xs font-semibold text-[#3366cc] hover:bg-[#3366cc] hover:text-white disabled:opacity-50 transition-colors"
            >
              <RotateCcw className="h-4 w-4" />
              {isBusy ? 'Restoring...' : 'Restore'}
            </button>
          </form>
        </section>
      </div>
    </div>
  );
};

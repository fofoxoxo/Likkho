import React, { useEffect, useState } from 'react';
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
  Fingerprint,
  FolderSearch,
} from 'lucide-react';
import {
  CustomFontItem,
  DEVICE_LOCK_BOUND_SECRET,
  DiaryLog,
  EncryptedVaultMetadata,
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
  const [vaultMeta, setVaultMeta] = useState<EncryptedVaultMetadata>({
    exists: false,
    lastBackupAt: null,
    entryCount: 0,
    vaultPath: '/storage/emulated/0/Download/.likkho_private_vault/likkho_encrypted_vault.bak',
    sizeBytes: 0,
    keyHintHash: null,
  });

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

  const refreshVaultStatus = () => {
    readFromPrivateSpecialFolder().then((res) => {
      setVaultMeta(res.metadata);
    });
  };

  useEffect(() => {
    refreshVaultStatus();
  }, []);

  /**
   * Prompts the user's phone Biometric / Device Lock (Fingerprint, Face, PIN, or Pattern)
   * before allowing Backup or Restore.
   */
  const verifyDeviceBiometricOrLockScreen = (): Promise<boolean> => {
    return new Promise((resolve) => {
      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.authenticateDeviceLockForBackup === 'function'
      ) {
        window.__onLikkhoDeviceLockBackupResult = (success: boolean, message?: string) => {
          window.__onLikkhoDeviceLockBackupResult = undefined;
          if (!success && message) {
            setStatusBanner({
              type: 'error',
              text: message,
            });
          }
          resolve(success);
        };
        window.LikkhoNative.authenticateDeviceLockForBackup();
        return;
      }

      if (
        window.LikkhoNative &&
        typeof window.LikkhoNative.authenticateBiometric === 'function'
      ) {
        window.__onLikkhoBiometricResult = (success: boolean, message?: string) => {
          window.__onLikkhoBiometricResult = undefined;
          if (!success && message) {
            setStatusBanner({
              type: 'error',
              text: message,
            });
          }
          resolve(success);
        };
        window.LikkhoNative.authenticateBiometric();
        return;
      }

      // In standard desktop/mobile browser preview without native bridge, allow proceeding
      resolve(true);
    });
  };

  const handleSetEncryptionKey = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newKey.trim().length < 4) {
      setStatusBanner({
        type: 'error',
        text: 'Encryption key must be at least 4 characters.',
      });
      return;
    }
    if (newKey !== confirmKey) {
      setStatusBanner({
        type: 'error',
        text: 'Encryption keys do not match.',
      });
      return;
    }

    const verified = await verifyDeviceBiometricOrLockScreen();
    if (!verified) return;

    const hashed = await hashPassphrase(newKey.trim());
    onSaveKeyHash(hashed);
    setBackupPassphrase(newKey.trim());
    setNewKey('');
    setConfirmKey('');
    setStatusBanner({
      type: 'success',
      text: 'Encryption Key saved and bound to your Device Lock / Biometrics.',
    });
  };

  const executeEncryptedBackup = async (keyToUse: string) => {
    setIsBusy(true);
    try {
      const verified = await verifyDeviceBiometricOrLockScreen();
      if (!verified) {
        setIsBusy(false);
        return;
      }

      const inputHash = await hashPassphrase(keyToUse);
      if (savedKeyHash && keyToUse !== DEVICE_LOCK_BOUND_SECRET && inputHash !== savedKeyHash) {
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
        keyToUse
      );
      const meta = await writeToPrivateSpecialFolder(
        encryptedEnvelope,
        logs.length,
        inputHash
      );
      setVaultMeta(meta);
      setStatusBanner({
        type: 'success',
        text: 'Encrypted backup saved in hidden folder (.likkho_private_vault with .nomedia).',
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

  const handleBackupToSpecialFolder = async (e: React.FormEvent) => {
    e.preventDefault();
    const keyToUse = backupPassphrase.trim() || DEVICE_LOCK_BOUND_SECRET;
    await executeEncryptedBackup(keyToUse);
  };

  const executeRestoreWithEnvelope = async (
    encryptedEnvelope: string,
    keyToUse: string
  ) => {
    try {
      const bundle = await decryptVaultBundle(encryptedEnvelope, keyToUse);
      const keyHash = await hashPassphrase(keyToUse);
      onSaveKeyHash(keyHash);
      onRestoreBundle(bundle);
      setRestorePassphrase('');
      refreshVaultStatus();
      setStatusBanner({
        type: 'success',
        text: `Restored ${bundle.logs.length} diary entries and media from hidden vault.`,
      });
    } catch (err) {
      setStatusBanner({
        type: 'error',
        text:
          err instanceof Error
            ? err.message
            : 'Decryption failed. Please check your Encryption Key.',
      });
    }
  };

  const handleRestoreFromSpecialFolder = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsBusy(true);
    try {
      const verified = await verifyDeviceBiometricOrLockScreen();
      if (!verified) {
        setIsBusy(false);
        return;
      }

      const keyToUse = restorePassphrase.trim() || DEVICE_LOCK_BOUND_SECRET;
      const { metadata, encryptedEnvelope } = await readFromPrivateSpecialFolder();

      if (metadata.exists && encryptedEnvelope) {
        await executeRestoreWithEnvelope(encryptedEnvelope, keyToUse);
        setIsBusy(false);
        return;
      }

      // If Android 11+ Scoped Storage restricted automatic read after reinstall, open native file picker directly to the backup file
      if (window.LikkhoNative && typeof window.LikkhoNative.pickVaultBackupFile === 'function') {
        window.__onLikkhoVaultFilePicked = async (pickedEnvelope: string) => {
          window.__onLikkhoVaultFilePicked = undefined;
          if (pickedEnvelope && pickedEnvelope.trim().length > 0) {
            await executeRestoreWithEnvelope(pickedEnvelope, keyToUse);
          } else {
            setStatusBanner({
              type: 'error',
              text: 'No backup file found in .likkho_private_vault.',
            });
          }
          setIsBusy(false);
        };
        window.LikkhoNative.pickVaultBackupFile();
        return;
      }

      setStatusBanner({
        type: 'error',
        text: 'No backup found to restore.',
      });
    } catch (err) {
      setStatusBanner({
        type: 'error',
        text: err instanceof Error ? err.message : 'Restore failed.',
      });
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Top Bar */}
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

      {/* Scrollable Body */}
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

        {/* 1. Biometric / Device Lock Bound Backup & Restore (One-Tap Hardware Protected) */}
        <section className="border border-[#3366cc] bg-[var(--wiki-bg)] p-4 space-y-3">
          <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] pb-2.5">
            <div className="flex items-center gap-2">
              <Fingerprint className="h-4 w-4 text-[#3366cc]" />
              <h2 className="font-wiki-serif text-base font-bold">
                Biometric / Device Lock Vault
              </h2>
            </div>
            <span className="font-wiki-mono text-[10px] text-[#14866d]">
              .nomedia Hidden Folder
            </span>
          </div>

          <p className="text-xs leading-relaxed text-[var(--wiki-muted)]">
            Directly binds your encrypted backup in{' '}
            <code className="font-wiki-mono text-[11px] text-[var(--wiki-text)]">
              Download/.likkho_private_vault/
            </code>{' '}
            to your phone&apos;s Fingerprint, Face, PIN, or Pattern lock.
          </p>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <button
              type="button"
              disabled={isBusy}
              onClick={() => executeEncryptedBackup(backupPassphrase.trim() || DEVICE_LOCK_BOUND_SECRET)}
              className="flex h-10 items-center justify-center gap-1.5 bg-[#3366cc] px-3 text-xs font-semibold text-white hover:bg-[#2a56b0] disabled:opacity-50"
            >
              <Fingerprint className="h-4 w-4" />
              <span>Backup with Lock</span>
            </button>

            <button
              type="button"
              disabled={isBusy}
              onClick={handleRestoreFromSpecialFolder}
              className="flex h-10 items-center justify-center gap-1.5 border border-[#3366cc] bg-[var(--wiki-surface)] px-3 text-xs font-semibold text-[#3366cc] hover:bg-[#3366cc] hover:text-white disabled:opacity-50 transition-colors"
            >
              <RotateCcw className="h-4 w-4" />
              <span>Restore with Lock</span>
            </button>
          </div>
        </section>

        {/* 2. Custom Encryption Key (Optional Additional Passphrase) */}
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
                placeholder="Enter encryption key..."
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
                placeholder="Confirm encryption key..."
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

        {/* 3. Backup with Key */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4">
          <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] pb-2.5">
            <div className="flex items-center gap-2">
              <HardDriveDownload className="h-4 w-4 text-[#3366cc]" />
              <h2 className="font-wiki-serif text-base font-bold">
                Backup Diary &amp; Media
              </h2>
            </div>
            {vaultMeta.lastBackupAt && (
              <span className="font-wiki-mono text-[11px] text-[var(--wiki-muted)]">
                {new Date(vaultMeta.lastBackupAt).toLocaleDateString('en-IN')}
              </span>
            )}
          </div>

          <form onSubmit={handleBackupToSpecialFolder} className="mt-3 space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                Encryption Key (or leave blank to use Device Lock)
              </label>
              <input
                type={showKeyText ? 'text' : 'password'}
                value={backupPassphrase}
                onChange={(e) => setBackupPassphrase(e.target.value)}
                placeholder="Enter encryption key or verify with fingerprint..."
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
            </div>

            <button
              type="submit"
              disabled={isBusy}
              className="flex h-10 w-full items-center justify-center gap-2 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] disabled:opacity-50"
            >
              <Lock className="h-4 w-4" />
              {isBusy ? 'Backing up...' : 'Backup Now'}
            </button>
          </form>
        </section>

        {/* 4. Restore with Key */}
        <section className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-4">
          <div className="flex items-center gap-2 border-b border-[var(--wiki-hairline)] pb-2.5">
            <RotateCcw className="h-4 w-4 text-[#3366cc]" />
            <h2 className="font-wiki-serif text-base font-bold">
              Restore Diary &amp; Media
            </h2>
          </div>

          <form onSubmit={handleRestoreFromSpecialFolder} className="mt-3 space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                Encryption Key (or leave blank if backed up with Device Lock)
              </label>
              <input
                type={showKeyText ? 'text' : 'password'}
                value={restorePassphrase}
                onChange={(e) => setRestorePassphrase(e.target.value)}
                placeholder="Enter encryption key to restore..."
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
            </div>

            <div className="flex flex-col gap-2">
              <button
                type="submit"
                disabled={isBusy}
                className="flex h-10 w-full items-center justify-center gap-2 border border-[#3366cc] bg-[var(--wiki-surface)] px-4 text-xs font-semibold text-[#3366cc] hover:bg-[#3366cc] hover:text-white disabled:opacity-50 transition-colors"
              >
                <RotateCcw className="h-4 w-4" />
                Restore Data
              </button>

              {window.LikkhoNative?.pickVaultBackupFile && (
                <button
                  type="button"
                  disabled={isBusy}
                  onClick={async () => {
                    const verified = await verifyDeviceBiometricOrLockScreen();
                    if (!verified) return;
                    const keyToUse = restorePassphrase.trim() || DEVICE_LOCK_BOUND_SECRET;
                    window.__onLikkhoVaultFilePicked = async (pickedEnvelope: string) => {
                      window.__onLikkhoVaultFilePicked = undefined;
                      if (pickedEnvelope && pickedEnvelope.trim().length > 0) {
                        await executeRestoreWithEnvelope(pickedEnvelope, keyToUse);
                      }
                    };
                    window.LikkhoNative?.pickVaultBackupFile?.();
                  }}
                  className="flex h-9 w-full items-center justify-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 text-xs font-medium text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
                >
                  <FolderSearch className="h-3.5 w-3.5 text-[#3366cc]" />
                  <span>Select Backup File from Storage</span>
                </button>
              )}
            </div>
          </form>
        </section>
      </div>
    </div>
  );
};

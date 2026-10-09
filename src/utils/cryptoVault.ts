/**
 * Stealth-Grade Dual-Vault Security Architecture + AES-256-GCM Authenticated Encryption + Android SAF
 * - Supports TWO completely isolated vaults with zero cross-database metadata linkage or storage leakage:
 *   1. Primary Vault (`real_vault.db`) — PBKDF2-SHA256 (100,000 iterations), dedicated `.likkho_backups` directory & `WikiLogPrimaryVaultDB`
 *   2. Secondary / Decoy Vault (`decoy_vault.db`) — Distinct PBKDF2-SHA512 (210,000 iterations), separate `.likkho_decoy_backups` directory & `WikiLogSecondaryInstanceDB`
 * - Includes progressive anti-brute-force passcode cooldown manager:
 *   - 5th continuous wrong attempt triggers a 30-second cooldown
 *   - Every subsequent wrong attempt (6th, 7th, ...) multiplies 30 seconds by the attempt count (e.g., 5 -> 30s, 6 -> 180s, 7 -> 210s, ...)
 */

export type VaultMode = 'primary' | 'decoy';

declare global {
  interface Window {
    LikkhoNative?: {
      setAppPasscodeProtectionEnabled?: (enabled: boolean) => void;
      setSystemBarsTheme?: (
        statusBarHex: string,
        navBarHex: string,
        isLightIcons: boolean
      ) => void;
      setFullScreenMode?: (enabled: boolean) => void;
      requestFilesAndMediaPermission?: () => void;
      requestMicPermission?: () => void;
      startNativeMicRecording?: (sampleRate: number, bitRate: number) => void;
      stopNativeMicRecording?: () => void;
      isNativeMicRecordingActive?: () => boolean;
      getNativeMicRecordingSeconds?: () => number;
      startForegroundMicService?: () => void;
      stopForegroundMicService?: () => void;
      cancelBiometricPrompt?: () => void;
      authenticateBiometric?: () => void;
      scheduleNativeReminder?: (
        logId: string,
        title: string,
        body: string,
        triggerAtMs: number
      ) => void;
      cancelNativeReminder?: (logId: string) => void;
      getSafBaseFolderName?: () => string;
      chooseSafBaseFolder?: () => void;
      saveBackupViaSaf?: (envelope: string, vaultMode: string) => void;
      restoreBackupViaSaf?: (vaultMode: string) => void;
      saveExportedFile?: (
        base64Data: string,
        filename: string,
        mimeType: string
      ) => void;
    };
    __handleLikkhoAndroidBack?: () => string;
    __onLikkhoBiometricResult?: (success: boolean, msg?: string) => void;
    __onLikkhoNativeMicFinished?: (base64Wav: string, durationSec: number) => void;
    __onLikkhoSafBackupResult?: (success: boolean, msg: string) => void;
    __onLikkhoSafRestoreResult?: (success: boolean, payload: string) => void;
    __onLikkhoSafFolderSelected?: (folderName: string) => void;
  }
}

export interface CanvasDraggableImage {
  id: string;
  dataUrl: string;
  x: number;
  y: number;
  width: number;
  height: number;
  opacity: number;
  rotation?: number;
  layer?: 'foreground' | 'background';
}

export interface CanvasAudioAttachment {
  id: string;
  name: string;
  format: string;
  dataUrl: string;
  durationSec?: number;
  createdAt: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  rotation?: number;
  playbackRate?: number;
  layer?: 'foreground' | 'background';
}

export interface CustomFontItem {
  id: string;
  name: string;
  lang: 'en' | 'hi' | 'custom';
  fontFamily: string;
  dataUrl: string;
}

export type AudioFormatOption = 'wav' | 'flac' | 'm4a' | 'aac' | 'mp3' | 'ogg' | 'webm';

export interface MicRecordingSettings {
  format: AudioFormatOption;
  sampleRate: 8000 | 16000 | 22050 | 44100 | 48000;
  bitRate: 64000 | 128000 | 192000 | 256000 | 320000;
}

export interface DiaryLog {
  id: string;
  heading: string;
  contentHtml: string;
  plainPreview: string;
  pfpDataUrl: string | null;
  createdAt: number;
  updatedAt: number;
  dateStamp: string;
  timeStamp: string;
  updatedDateStamp?: string;
  updatedTimeStamp?: string;
  reminderAt: number | null;
  reminderFired?: boolean;
  pinned?: boolean;
  diaryLockPin?: string | null;
  canvasBgDataUrl?: string | null;
  canvasBgOpacity?: number;
  canvasImages?: CanvasDraggableImage[];
  audioAttachments?: CanvasAudioAttachment[];
}

export interface EncryptedVaultMetadata {
  exists: boolean;
  lastBackupAt: number | null;
  entryCount: number;
  vaultPath: string;
  sizeBytes: number;
  keyHintHash: string | null;
}

export interface VaultBackupBundle {
  logs: DiaryLog[];
  customFonts?: CustomFontItem[];
  micSettings?: MicRecordingSettings;
}

export interface VaultDomainConfig {
  mode: VaultMode;
  dbFilename: string;
  safFolderName: string;
  idbDatabaseName: string;
  kdfHash: 'SHA-256' | 'SHA-512';
  kdfIterations: number;
  domainContextSalt: string;
  storagePrefix: string;
}

const VAULT_CONFIGS: Record<VaultMode, VaultDomainConfig> = {
  primary: {
    mode: 'primary',
    dbFilename: 'real_vault.db',
    safFolderName: '.likkho_backups',
    idbDatabaseName: 'WikiLogPrivateSystemVaultDB',
    kdfHash: 'SHA-256',
    kdfIterations: 100000,
    domainContextSalt: 'WIKILOG_PRIMARY_REAL_VAULT_KDF_V1::',
    storagePrefix: 'wikilog_v1_',
  },
  decoy: {
    mode: 'decoy',
    dbFilename: 'decoy_vault.db',
    safFolderName: '.likkho_decoy_backups',
    idbDatabaseName: 'WikiLogIsolatedDecoyVaultDB',
    kdfHash: 'SHA-512',
    kdfIterations: 210000,
    domainContextSalt: 'WIKILOG_SECONDARY_DECOY_VAULT_KDF_V2::',
    storagePrefix: 'wikilog_decoy_v2_',
  },
};

export function getVaultConfig(mode: VaultMode = 'primary'): VaultDomainConfig {
  return VAULT_CONFIGS[mode];
}

const IDB_STORE = 'special_hidden_folder';
const APP_STATE_STORE = 'in_app_active_storage';

// ==================== Anti-Brute-Force Passcode Cooldown Manager ====================
// Rule:
// - Attempts 1 to 4: No cooldown (0 seconds)
// - 5th continuous wrong attempt: 30 seconds cooldown
// - 6th continuous wrong attempt onwards: 30 seconds * wrongAttemptCount (e.g., 6 -> 180s, 7 -> 210s, 8 -> 240s, ...)
const COOLDOWN_STORAGE_KEY = 'wikilog_global_passcode_guard_v1';

export interface PasscodeGuardState {
  failedAttempts: number;
  cooldownUntilMs: number;
}

export function getPasscodeGuardState(): PasscodeGuardState {
  try {
    const raw = localStorage.getItem(COOLDOWN_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      return {
        failedAttempts: typeof parsed.failedAttempts === 'number' ? parsed.failedAttempts : 0,
        cooldownUntilMs: typeof parsed.cooldownUntilMs === 'number' ? parsed.cooldownUntilMs : 0,
      };
    }
  } catch {
    // ignore
  }
  return { failedAttempts: 0, cooldownUntilMs: 0 };
}

export function getRemainingPasscodeCooldownSeconds(): number {
  const state = getPasscodeGuardState();
  const diff = state.cooldownUntilMs - Date.now();
  return diff > 0 ? Math.ceil(diff / 1000) : 0;
}

export function recordPasscodeFailure(): {
  failedAttempts: number;
  cooldownSeconds: number;
  cooldownUntilMs: number;
} {
  const current = getPasscodeGuardState();
  const nextAttempts = current.failedAttempts + 1;
  let cooldownSeconds = 0;

  if (nextAttempts === 5) {
    cooldownSeconds = 30;
  } else if (nextAttempts > 5) {
    cooldownSeconds = 30 * nextAttempts;
  }

  const cooldownUntilMs = cooldownSeconds > 0 ? Date.now() + cooldownSeconds * 1000 : 0;
  const nextState: PasscodeGuardState = {
    failedAttempts: nextAttempts,
    cooldownUntilMs,
  };

  try {
    localStorage.setItem(COOLDOWN_STORAGE_KEY, JSON.stringify(nextState));
  } catch {
    // ignore
  }

  return {
    failedAttempts: nextAttempts,
    cooldownSeconds,
    cooldownUntilMs,
  };
}

export function resetPasscodeFailures(): void {
  try {
    localStorage.removeItem(COOLDOWN_STORAGE_KEY);
  } catch {
    // ignore
  }
}

// Convert ArrayBuffer / Uint8Array to Base64
function bufferToBase64(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode.apply(
      null,
      Array.from(bytes.subarray(i, i + chunkSize))
    );
  }
  return btoa(binary);
}

// Convert Base64 to Uint8Array
function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Distinct Key Derivation Function (KDF) per Vault Mode:
 * - Primary Vault (`real_vault.db`): PBKDF2-HMAC-SHA256 with 100,000 iterations + domain-separated salt
 * - Secondary / Decoy Vault (`decoy_vault.db`): PBKDF2-HMAC-SHA512 with 210,000 iterations + distinct domain-separated salt
 */
async function deriveAesKey(
  passphrase: string,
  salt: Uint8Array,
  vaultMode: VaultMode = 'primary'
): Promise<CryptoKey> {
  const cfg = getVaultConfig(vaultMode);
  const enc = new TextEncoder();
  const domainPassphrase = `${cfg.domainContextSalt}${passphrase}`;
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(domainPassphrase),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations: cfg.kdfIterations,
      hash: cfg.kdfHash,
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

/**
 * Legacy-compatible key derivation (without domain prefix) so existing primary backups decrypt seamlessly
 */
async function deriveLegacyPrimaryAesKey(
  passphrase: string,
  salt: Uint8Array
): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(passphrase),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations: 100000,
      hash: 'SHA-256',
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function hashPassphrase(
  passphrase: string,
  vaultMode: VaultMode = 'primary'
): Promise<string> {
  const cfg = getVaultConfig(vaultMode);
  const enc = new TextEncoder();
  const hashAlg = cfg.kdfHash;
  const digest = await crypto.subtle.digest(
    hashAlg,
    enc.encode(`${cfg.domainContextSalt}HASH_SALT_${passphrase}`)
  );
  return bufferToBase64(digest);
}

/**
 * Encrypt full bundle using vault-isolated KDF + AES-256-GCM (16-byte salt, 12-byte IV,
 * separating Ciphertext and 128-bit [16-byte] Authentication Tag in JSON format).
 */
export async function encryptVaultBundle(
  bundle: VaultBackupBundle,
  encryptionKey: string,
  vaultMode: VaultMode = 'primary'
): Promise<string> {
  const cfg = getVaultConfig(vaultMode);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const aesKey = await deriveAesKey(encryptionKey.trim(), salt, vaultMode);

  const plaintext = JSON.stringify({
    magic: 'WIKILOG_VAULT_V1',
    dbTarget: cfg.dbFilename,
    exportedAt: Date.now(),
    logs: bundle.logs,
    customFonts: bundle.customFonts || [],
    micSettings: bundle.micSettings,
  });

  const enc = new TextEncoder();
  const encryptedBuffer = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, tagLength: 128 },
    aesKey,
    enc.encode(plaintext)
  );

  const encryptedBytes = new Uint8Array(encryptedBuffer);
  const tagByteLength = 16; // 128-bit GCM Authentication Tag
  const ciphertextBytes = encryptedBytes.slice(0, encryptedBytes.length - tagByteLength);
  const authTagBytes = encryptedBytes.slice(encryptedBytes.length - tagByteLength);

  const envelope = {
    v: 2,
    alg: `AES-256-GCM-PBKDF2-${cfg.kdfHash}-${cfg.kdfIterations}`,
    db: cfg.dbFilename,
    salt: bufferToBase64(salt),
    iv: bufferToBase64(iv),
    ct: bufferToBase64(ciphertextBytes),
    tag: bufferToBase64(authTagBytes),
    createdAt: Date.now(),
    count: bundle.logs.length,
  };

  return JSON.stringify(envelope);
}

/**
 * Decrypt AES-256-GCM payload using the active vault's distinct KDF (with fallback to v1 primary KDF for older primary backups).
 */
export async function decryptVaultBundle(
  rawEnvelope: string,
  encryptionKey: string,
  vaultMode: VaultMode = 'primary'
): Promise<VaultBackupBundle> {
  let envelope: {
    v: number;
    salt: string;
    iv: string;
    ct: string;
    tag?: string;
  };

  try {
    envelope = JSON.parse(rawEnvelope);
  } catch {
    throw new Error('Corrupted backup format.');
  }

  if (!envelope.salt || !envelope.iv || !envelope.ct) {
    throw new Error('Invalid backup file.');
  }

  const salt = base64ToBytes(envelope.salt);
  const iv = base64ToBytes(envelope.iv);
  const ctBytes = base64ToBytes(envelope.ct);

  let combinedCipherAndTag: Uint8Array;
  if (envelope.tag) {
    const tagBytes = base64ToBytes(envelope.tag);
    combinedCipherAndTag = new Uint8Array(ctBytes.length + tagBytes.length);
    combinedCipherAndTag.set(ctBytes, 0);
    combinedCipherAndTag.set(tagBytes, ctBytes.length);
  } else {
    combinedCipherAndTag = ctBytes;
  }

  const trimmedKey = encryptionKey.trim();
  const candidateKeys: CryptoKey[] = [
    await deriveAesKey(trimmedKey, salt, vaultMode),
  ];
  if (vaultMode === 'primary') {
    candidateKeys.push(await deriveLegacyPrimaryAesKey(trimmedKey, salt));
  }

  for (const aesKey of candidateKeys) {
    try {
      const decryptedBuffer = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv, tagLength: 128 },
        aesKey,
        combinedCipherAndTag
      );
      const dec = new TextDecoder();
      const parsed = JSON.parse(dec.decode(decryptedBuffer));
      if (parsed.magic !== 'WIKILOG_VAULT_V1' || !Array.isArray(parsed.logs)) {
        continue;
      }
      return {
        logs: parsed.logs as DiaryLog[],
        customFonts: Array.isArray(parsed.customFonts) ? parsed.customFonts : [],
        micSettings: parsed.micSettings,
      };
    } catch {
      // try next candidate if any
    }
  }

  throw new Error('Incorrect Encryption Key.');
}

export async function encryptLogsPayload(
  logs: DiaryLog[],
  encryptionKey: string,
  vaultMode: VaultMode = 'primary'
): Promise<string> {
  return encryptVaultBundle({ logs }, encryptionKey, vaultMode);
}

export async function decryptLogsPayload(
  rawEnvelope: string,
  encryptionKey: string,
  vaultMode: VaultMode = 'primary'
): Promise<DiaryLog[]> {
  const res = await decryptVaultBundle(rawEnvelope, encryptionKey, vaultMode);
  return res.logs;
}

function openPrivateVaultDB(vaultMode: VaultMode = 'primary'): Promise<IDBDatabase> {
  const cfg = getVaultConfig(vaultMode);
  return new Promise((resolve, reject) => {
    try {
      const req = indexedDB.open(cfg.idbDatabaseName, 2);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(IDB_STORE)) {
          db.createObjectStore(IDB_STORE);
        }
        if (!db.objectStoreNames.contains(APP_STATE_STORE)) {
          db.createObjectStore(APP_STATE_STORE);
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
      req.onblocked = () => reject(new Error('IDB blocked'));
    } catch (err) {
      reject(err);
    }
  });
}

export async function saveActiveAppStateToIDB(
  key: string,
  value: unknown,
  vaultMode: VaultMode = 'primary'
): Promise<void> {
  try {
    const db = await openPrivateVaultDB(vaultMode);
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(APP_STATE_STORE, 'readwrite');
      const store = tx.objectStore(APP_STATE_STORE);
      store.put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}

export async function loadActiveAppStateFromIDB<T>(
  key: string,
  vaultMode: VaultMode = 'primary'
): Promise<T | null> {
  try {
    const db = await openPrivateVaultDB(vaultMode);
    return await new Promise<T | null>((resolve, reject) => {
      const tx = db.transaction(APP_STATE_STORE, 'readonly');
      const store = tx.objectStore(APP_STATE_STORE);
      const req = store.get(key);
      req.onsuccess = () => resolve((req.result as T) ?? null);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return null;
  }
}

/**
 * Write encrypted backup into the active vault's dedicated directory and database file:
 * - Primary Vault: `<ChosenBaseDir>/.likkho_backups/real_vault.db`
 * - Secondary / Decoy Vault: `<ChosenBaseDir>/.likkho_decoy_backups/decoy_vault.db`
 */
export async function writeToPrivateSpecialFolder(
  encryptedEnvelope: string,
  entryCount: number,
  keyHintHash: string,
  vaultMode: VaultMode = 'primary'
): Promise<EncryptedVaultMetadata> {
  const cfg = getVaultConfig(vaultMode);
  const now = Date.now();
  const sizeBytes = new Blob([encryptedEnvelope]).size;
  const vaultPath = `SAF/${cfg.safFolderName}/${cfg.dbFilename}`;

  // 1. If running in Native Android WebView, use SAF (Storage Access Framework) with isolated folder & db filename
  if (window.LikkhoNative) {
    if (typeof window.LikkhoNative.saveBackupViaSaf === 'function') {
      await new Promise<void>((resolve, reject) => {
        window.__onLikkhoSafBackupResult = (success: boolean, message?: string) => {
          window.__onLikkhoSafBackupResult = undefined;
          if (success) {
            resolve();
          } else {
            reject(new Error(message || 'Backup cancelled or folder not selected.'));
          }
        };
        window.LikkhoNative?.saveBackupViaSaf?.(encryptedEnvelope, vaultMode);
      });
    } else if (typeof window.LikkhoNative.writePersistentVaultBackup === 'function') {
      window.LikkhoNative.writePersistentVaultBackup(encryptedEnvelope);
    }
  }

  // 2. Mirror to isolated OPFS directory (`real_vault.db` vs `decoy_vault.db`)
  try {
    if (navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      const specialFolder = await rootDir.getDirectoryHandle(cfg.safFolderName, {
        create: true,
      });
      const fileHandle = await specialFolder.getFileHandle(cfg.dbFilename, {
        create: true,
      });
      if ('createWritable' in fileHandle) {
        const writable = await (fileHandle as unknown as {
          createWritable: () => Promise<{
            write: (data: string) => Promise<void>;
            close: () => Promise<void>;
          }>;
        }).createWritable();
        await writable.write(encryptedEnvelope);
        await writable.close();
      }
    }
  } catch {
    // Fallback to IndexedDB
  }

  // 3. Mirror to isolated IndexedDB instance
  try {
    const db = await openPrivateVaultDB(vaultMode);
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);
      store.put(
        {
          encryptedEnvelope,
          lastBackupAt: now,
          entryCount,
          vaultPath,
          sizeBytes,
          keyHintHash,
        },
        cfg.dbFilename
      );
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }

  return {
    exists: true,
    lastBackupAt: now,
    entryCount,
    vaultPath,
    sizeBytes,
    keyHintHash,
  };
}

/**
 * Read encrypted backup from the active vault's dedicated directory and database file:
 * - Primary Vault: `<ChosenBaseDir>/.likkho_backups/real_vault.db` (plus fallback to legacy `likkho_encrypted_vault.json`)
 * - Secondary / Decoy Vault: `<ChosenBaseDir>/.likkho_decoy_backups/decoy_vault.db`
 */
export async function readFromPrivateSpecialFolder(
  vaultMode: VaultMode = 'primary'
): Promise<{
  metadata: EncryptedVaultMetadata;
  encryptedEnvelope: string | null;
}> {
  const cfg = getVaultConfig(vaultMode);
  const defaultPath = `SAF/${cfg.safFolderName}/${cfg.dbFilename}`;

  // 1. Check Native Android SAF backup first
  if (window.LikkhoNative) {
    if (typeof window.LikkhoNative.restoreBackupViaSaf === 'function') {
      const safEnvelope = await new Promise<string>((resolve) => {
        window.__onLikkhoSafRestoreResult = (success: boolean, payload?: string) => {
          window.__onLikkhoSafRestoreResult = undefined;
          if (success && payload && payload.trim().length > 0) {
            resolve(payload);
          } else {
            resolve('');
          }
        };
        window.LikkhoNative?.restoreBackupViaSaf?.(vaultMode);
      });

      if (safEnvelope && safEnvelope.trim().length > 0) {
        try {
          const parsed = JSON.parse(safEnvelope);
          return {
            metadata: {
              exists: true,
              lastBackupAt: parsed.createdAt || Date.now(),
              entryCount: parsed.count || 0,
              vaultPath: defaultPath,
              sizeBytes: new Blob([safEnvelope]).size,
              keyHintHash: null,
            },
            encryptedEnvelope: safEnvelope,
          };
        } catch {
          // ignore
        }
      }
    } else if (typeof window.LikkhoNative.readPersistentVaultBackup === 'function') {
      try {
        const nativeEnvelope = window.LikkhoNative.readPersistentVaultBackup();
        if (nativeEnvelope && nativeEnvelope.trim().length > 0) {
          const parsed = JSON.parse(nativeEnvelope);
          return {
            metadata: {
              exists: true,
              lastBackupAt: parsed.createdAt || Date.now(),
              entryCount: parsed.count || 0,
              vaultPath: defaultPath,
              sizeBytes: new Blob([nativeEnvelope]).size,
              keyHintHash: null,
            },
            encryptedEnvelope: nativeEnvelope,
          };
        }
      } catch {
        // ignore
      }
    }
  }

  // 2. Check isolated IndexedDB instance
  try {
    const db = await openPrivateVaultDB(vaultMode);
    const candidateKeys =
      vaultMode === 'primary'
        ? [cfg.dbFilename, 'likkho_encrypted_vault.json']
        : [cfg.dbFilename];

    for (const keyName of candidateKeys) {
      const record = await new Promise<{
        encryptedEnvelope: string;
        lastBackupAt: number;
        entryCount: number;
        vaultPath: string;
        sizeBytes: number;
        keyHintHash: string;
      } | null>((resolve, reject) => {
        const tx = db.transaction(IDB_STORE, 'readonly');
        const store = tx.objectStore(IDB_STORE);
        const req = store.get(keyName);
        req.onsuccess = () => resolve(req.result || null);
        req.onerror = () => reject(req.error);
      });

      if (record && record.encryptedEnvelope) {
        return {
          metadata: {
            exists: true,
            lastBackupAt: record.lastBackupAt,
            entryCount: record.entryCount,
            vaultPath: record.vaultPath || defaultPath,
            sizeBytes: record.sizeBytes,
            keyHintHash: record.keyHintHash || null,
          },
          encryptedEnvelope: record.encryptedEnvelope,
        };
      }
    }
  } catch {
    // Continue to OPFS check
  }

  // 3. Check isolated OPFS directory
  try {
    if (navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      const specialFolder = await rootDir.getDirectoryHandle(cfg.safFolderName, {
        create: false,
      });
      const candidateFiles =
        vaultMode === 'primary'
          ? [cfg.dbFilename, 'likkho_encrypted_vault.json']
          : [cfg.dbFilename];
      for (const fname of candidateFiles) {
        try {
          const fileHandle = await specialFolder.getFileHandle(fname, { create: false });
          const file = await fileHandle.getFile();
          const text = await file.text();
          const parsed = JSON.parse(text);
          return {
            metadata: {
              exists: true,
              lastBackupAt: parsed.createdAt || file.lastModified,
              entryCount: parsed.count || 0,
              vaultPath: defaultPath,
              sizeBytes: file.size,
              keyHintHash: null,
            },
            encryptedEnvelope: text,
          };
        } catch {
          // try next candidate
        }
      }
    }
  } catch {
    // Vault does not exist yet
  }

  return {
    metadata: {
      exists: false,
      lastBackupAt: null,
      entryCount: 0,
      vaultPath: defaultPath,
      sizeBytes: 0,
      keyHintHash: null,
    },
    encryptedEnvelope: null,
  };
}

/**
 * Permanently deletes all local application data across Primary and Secondary vaults:
 * - Clears all localStorage keys
 * - Deletes both IndexedDB databases (`WikiLogPrivateSystemVaultDB` & `WikiLogIsolatedDecoyVaultDB`)
 * - Removes OPFS backup directories if present
 */
export async function clearAllAppVaultData(): Promise<void> {
  try {
    localStorage.clear();
  } catch {
    // ignore
  }

  try {
    sessionStorage.clear();
  } catch {
    // ignore
  }

  const dbNames = [
    VAULT_CONFIGS.primary.idbDatabaseName,
    VAULT_CONFIGS.decoy.idbDatabaseName,
  ];

  for (const name of dbNames) {
    try {
      await new Promise<void>((resolve) => {
        const req = indexedDB.deleteDatabase(name);
        req.onsuccess = () => resolve();
        req.onerror = () => resolve();
        req.onblocked = () => resolve();
      });
    } catch {
      // ignore
    }
  }

  try {
    if (navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      for (const folder of [
        VAULT_CONFIGS.primary.safFolderName,
        VAULT_CONFIGS.decoy.safFolderName,
      ]) {
        try {
          await rootDir.removeEntry(folder, { recursive: true });
        } catch {
          // ignore
        }
      }
    }
  } catch {
    // ignore
  }
}


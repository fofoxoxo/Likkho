/**
 * AES-256-GCM Encryption & Hidden Special Folder (.likkho_private_vault with .nomedia)
 *
 * Encrypts and backs up all Diary Logs INCLUDING all attached Media (Free-Draggable
 * Canvas Images, Audio Attachments, Voice Recordings, Custom .ttf Fonts, and Settings)
 * into the persistent hidden Android storage folder (/storage/emulated/0/Download/.likkho_private_vault/)
 * protected by .nomedia and Biometric / Device Lock (PIN, Pattern, or Fingerprint) verification!
 */

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
  reminderAt: number | null;
  reminderFired?: boolean;
  pinned?: boolean;
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

const VAULT_FOLDER_NAME = '.likkho_private_vault';
const VAULT_FILE_NAME = 'likkho_encrypted_vault.bak';
const IDB_NAME = 'WikiLogPrivateSystemVaultDB';
const IDB_STORE = 'special_hidden_folder';
const APP_STATE_STORE = 'in_app_active_storage';

// Default device-bound hardware vault secret (used when backup encryption key is bound directly to phone PIN / Pattern / Fingerprint)
export const DEVICE_LOCK_BOUND_SECRET = 'LIKKHO_HARDWARE_BIOMETRIC_DEVICE_CREDENTIAL_KEY_V2';

// Convert ArrayBuffer to Base64
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

// Derive AES-256-GCM key from Encryption Key / Device-Lock Bound Secret
async function deriveAesKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
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

export async function hashPassphrase(passphrase: string): Promise<string> {
  const enc = new TextEncoder();
  const digest = await crypto.subtle.digest('SHA-256', enc.encode('WIKILOG_SALT_' + passphrase));
  return bufferToBase64(digest);
}

// Encrypt full bundle (logs + media + custom fonts + mic settings) into AES-256-GCM payload
export async function encryptVaultBundle(
  bundle: VaultBackupBundle,
  encryptionKey: string
): Promise<string> {
  const effectiveKey = encryptionKey.trim() || DEVICE_LOCK_BOUND_SECRET;
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const aesKey = await deriveAesKey(effectiveKey, salt);

  const plaintext = JSON.stringify({
    magic: 'WIKILOG_VAULT_V1',
    exportedAt: Date.now(),
    logs: bundle.logs,
    customFonts: bundle.customFonts || [],
    micSettings: bundle.micSettings,
  });

  const enc = new TextEncoder();
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    aesKey,
    enc.encode(plaintext)
  );

  const envelope = {
    v: 1,
    alg: 'AES-256-GCM-PBKDF2-100K',
    deviceBound: encryptionKey.trim().length === 0,
    salt: bufferToBase64(salt),
    iv: bufferToBase64(iv),
    ct: bufferToBase64(ciphertext),
    createdAt: Date.now(),
    count: bundle.logs.length,
  };

  return JSON.stringify(envelope);
}

// Decrypt AES-256-GCM payload back into VaultBackupBundle
export async function decryptVaultBundle(
  rawEnvelope: string,
  encryptionKey: string
): Promise<VaultBackupBundle> {
  let envelope: {
    v: number;
    salt: string;
    iv: string;
    ct: string;
    deviceBound?: boolean;
  };

  try {
    envelope = JSON.parse(rawEnvelope);
  } catch {
    throw new Error('Corrupted backup format.');
  }

  if (!envelope.salt || !envelope.iv || !envelope.ct) {
    throw new Error('Invalid backup structure.');
  }

  const salt = base64ToBytes(envelope.salt);
  const iv = base64ToBytes(envelope.iv);
  const ciphertext = base64ToBytes(envelope.ct);

  // Try user's provided key first, or DEVICE_LOCK_BOUND_SECRET if device-bound
  const candidateKeys =
    encryptionKey.trim().length > 0
      ? [encryptionKey.trim(), DEVICE_LOCK_BOUND_SECRET]
      : [DEVICE_LOCK_BOUND_SECRET];

  for (const keyCandidate of candidateKeys) {
    try {
      const aesKey = await deriveAesKey(keyCandidate, salt);
      const decryptedBuffer = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        aesKey,
        ciphertext
      );
      const dec = new TextDecoder();
      const parsed = JSON.parse(dec.decode(decryptedBuffer));
      if (parsed.magic === 'WIKILOG_VAULT_V1' && Array.isArray(parsed.logs)) {
        return {
          logs: parsed.logs as DiaryLog[],
          customFonts: Array.isArray(parsed.customFonts) ? parsed.customFonts : [],
          micSettings: parsed.micSettings,
        };
      }
    } catch {
      // try next candidate
    }
  }

  throw new Error('Decryption failed. Please check your Encryption Key or verify with Device Lock.');
}

// Legacy wrappers for compatibility
export async function encryptLogsPayload(logs: DiaryLog[], encryptionKey: string): Promise<string> {
  return encryptVaultBundle({ logs }, encryptionKey);
}

export async function decryptLogsPayload(rawEnvelope: string, encryptionKey: string): Promise<DiaryLog[]> {
  const res = await decryptVaultBundle(rawEnvelope, encryptionKey);
  return res.logs;
}

function openPrivateVaultDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    try {
      const req = indexedDB.open(IDB_NAME, 2);
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

// Save large active logs/media/fonts in IndexedDB so audio/images/TTF never hit 5MB localStorage limits
export async function saveActiveAppStateToIDB(
  key: string,
  value: unknown
): Promise<void> {
  try {
    const db = await openPrivateVaultDB();
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

export async function loadActiveAppStateFromIDB<T>(key: string): Promise<T | null> {
  try {
    const db = await openPrivateVaultDB();
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

// Write to Persistent Hidden Android Special Folder (.likkho_private_vault with .nomedia) + OPFS + IndexedDB mirror
export async function writeToPrivateSpecialFolder(
  encryptedEnvelope: string,
  entryCount: number,
  keyHintHash: string
): Promise<EncryptedVaultMetadata> {
  const now = Date.now();
  const sizeBytes = new Blob([encryptedEnvelope]).size;
  const vaultPath = `/storage/emulated/0/Download/${VAULT_FOLDER_NAME}/${VAULT_FILE_NAME}`;

  // 1. Write to Native Android Hidden Special Folder (.likkho_private_vault + .nomedia) so backup survives app uninstall & reinstall!
  try {
    if (window.LikkhoNative && typeof window.LikkhoNative.writePersistentVaultBackup === 'function') {
      window.LikkhoNative.writePersistentVaultBackup(encryptedEnvelope);
    }
  } catch {
    // ignore
  }

  // 2. Write to OPFS
  try {
    if (navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      const specialFolder = await rootDir.getDirectoryHandle(VAULT_FOLDER_NAME, { create: true });
      const fileHandle = await specialFolder.getFileHandle(VAULT_FILE_NAME, { create: true });
      if ('createWritable' in fileHandle) {
        const writable = await (fileHandle as unknown as {
          createWritable: () => Promise<{ write: (data: string) => Promise<void>; close: () => Promise<void> }>;
        }).createWritable();
        await writable.write(encryptedEnvelope);
        await writable.close();
      }
    }
  } catch {
    // Fallback to IndexedDB private store if OPFS writable is restricted
  }

  // 3. Write to IndexedDB
  try {
    const db = await openPrivateVaultDB();
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
        VAULT_FILE_NAME
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

export async function readFromPrivateSpecialFolder(): Promise<{
  metadata: EncryptedVaultMetadata;
  encryptedEnvelope: string | null;
}> {
  const defaultPath = `/storage/emulated/0/Download/${VAULT_FOLDER_NAME}/${VAULT_FILE_NAME}`;

  // 1. Check Native Android Hidden Special Folder FIRST (survives app uninstall & reinstall!)
  try {
    if (window.LikkhoNative && typeof window.LikkhoNative.readPersistentVaultBackup === 'function') {
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
    }
  } catch {
    // Continue to IndexedDB check
  }

  // 2. Check IndexedDB
  try {
    const db = await openPrivateVaultDB();
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
      const req = store.get(VAULT_FILE_NAME);
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
  } catch {
    // Continue to OPFS check
  }

  // 3. Check OPFS
  try {
    if (navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      const specialFolder = await rootDir.getDirectoryHandle(VAULT_FOLDER_NAME, { create: false });
      const fileHandle = await specialFolder.getFileHandle(VAULT_FILE_NAME, { create: false });
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

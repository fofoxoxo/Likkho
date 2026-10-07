/**
 * AES-256-GCM Authenticated Encryption Architecture + Android SAF (Storage Access Framework)
 * - Derives a 256-bit key from user's passphrase via PBKDF2 (100,000 iterations + random 16-byte salt)
 * - Encrypts with a fresh 12-byte IV producing ciphertext + 128-bit GCM Authentication Tag stored in JSON format
 * - Uses Android SAF System File Picker (ACTION_OPEN_DOCUMENT_TREE) to create a hidden subfolder (.likkho_backups)
 *   with a .nomedia file inside the user's chosen directory (e.g., Documents), allowing targetSdkVersion = 34
 *   with zero Google Play Protect warnings while surviving app reinstalls.
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

const VAULT_FOLDER_NAME = '.likkho_backups';
const VAULT_FILE_NAME = 'likkho_encrypted_vault.json';
const IDB_NAME = 'WikiLogPrivateSystemVaultDB';
const IDB_STORE = 'special_hidden_folder';
const APP_STATE_STORE = 'in_app_active_storage';

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

// Derive 256-bit AES-GCM key from passphrase using PBKDF2 (100,000 iterations + 16-byte salt)
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

/**
 * Encrypt full bundle using AES-256-GCM (PBKDF2 100k iterations, 16-byte salt, 12-byte IV,
 * separating Ciphertext and 128-bit [16-byte] Authentication Tag in JSON format).
 */
export async function encryptVaultBundle(
  bundle: VaultBackupBundle,
  encryptionKey: string
): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const aesKey = await deriveAesKey(encryptionKey.trim(), salt);

  const plaintext = JSON.stringify({
    magic: 'WIKILOG_VAULT_V1',
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
    v: 1,
    alg: 'AES-256-GCM-PBKDF2-100K',
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
 * Decrypt AES-256-GCM JSON payload with 128-bit GCM Tag validation
 */
export async function decryptVaultBundle(
  rawEnvelope: string,
  encryptionKey: string
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

  const aesKey = await deriveAesKey(encryptionKey.trim(), salt);

  try {
    const decryptedBuffer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv, tagLength: 128 },
      aesKey,
      combinedCipherAndTag
    );
    const dec = new TextDecoder();
    const parsed = JSON.parse(dec.decode(decryptedBuffer));
    if (parsed.magic !== 'WIKILOG_VAULT_V1' || !Array.isArray(parsed.logs)) {
      throw new Error('Invalid backup signature.');
    }
    return {
      logs: parsed.logs as DiaryLog[],
      customFonts: Array.isArray(parsed.customFonts) ? parsed.customFonts : [],
      micSettings: parsed.micSettings,
    };
  } catch {
    throw new Error('Incorrect Encryption Key.');
  }
}

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

/**
 * Write encrypted backup via Android SAF (Storage Access Framework) into `<ChosenBaseDir>/.likkho_backups/likkho_encrypted_vault.json`
 * with `.nomedia` file. If no SAF directory is selected yet, Android automatically launches the SAF System Directory Picker!
 */
export async function writeToPrivateSpecialFolder(
  encryptedEnvelope: string,
  entryCount: number,
  keyHintHash: string
): Promise<EncryptedVaultMetadata> {
  const now = Date.now();
  const sizeBytes = new Blob([encryptedEnvelope]).size;
  const vaultPath = `SAF/${VAULT_FOLDER_NAME}/${VAULT_FILE_NAME}`;

  // 1. If running in Native Android WebView, use SAF (Storage Access Framework)
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
        window.LikkhoNative?.saveBackupViaSaf?.(encryptedEnvelope);
      });
    } else if (typeof window.LikkhoNative.writePersistentVaultBackup === 'function') {
      window.LikkhoNative.writePersistentVaultBackup(encryptedEnvelope);
    }
  }

  // 2. Mirror to OPFS
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
    // Fallback to IndexedDB
  }

  // 3. Mirror to IndexedDB
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

/**
 * Read encrypted backup from Android SAF (`<ChosenBaseDir>/.likkho_backups/likkho_encrypted_vault.json`).
 * If the app was freshly reinstalled and hasn't been granted the SAF directory URI yet, Android automatically
 * opens the SAF System Directory Picker so the user picks their base folder (e.g., Documents) once and Likkho
 * immediately reads `.likkho_backups/likkho_encrypted_vault.json` inside it!
 */
export async function readFromPrivateSpecialFolder(): Promise<{
  metadata: EncryptedVaultMetadata;
  encryptedEnvelope: string | null;
}> {
  const defaultPath = `SAF/${VAULT_FOLDER_NAME}/${VAULT_FILE_NAME}`;

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
        window.LikkhoNative?.restoreBackupViaSaf?.();
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

/**
 * Dedicated App-Specific Internal Files Directory Storage Engine (Encrypted At Rest)
 * 
 * Provides secure, dedicated internal storage for in-app data (diaries, images, audio references):
 * 1. Dedicated Android App-Specific Internal Files Directory (`context.getFilesDir() / likkho_internal_data`).
 * 2. Dedicated Web / PWA Origin Private File System (`navigator.storage.getDirectory() / likkho_internal_data`).
 * 3. Transparent AES-GCM 256-bit encryption at rest for all in-app saved diaries & state.
 * 4. Deleting a diary from the app strictly removes it from this internal storage.
 * 5. Backups in the user's selected SAF vault folder remain intact until restored or managed.
 */

import { VaultMode } from './cryptoVault';

const INTERNAL_CIPHER_VERSION = 1;
const INTERNAL_DIR_NAME = 'likkho_internal_data';

// Deterministic internal device master seed for in-app at-rest encryption
const INTERNAL_DEVICE_SALT = new Uint8Array([
  0x4c, 0x69, 0x6b, 0x6b, 0x68, 0x6f, 0x49, 0x6e,
  0x74, 0x65, 0x72, 0x6e, 0x61, 0x6c, 0x53, 0x65
]); // "LikkhoInternalSe"

let cachedInternalAesKey: CryptoKey | null = null;

async function getInternalAppAesKey(): Promise<CryptoKey> {
  if (cachedInternalAesKey) return cachedInternalAesKey;

  const enc = new TextEncoder();
  const rawKeyMaterial = enc.encode('Likkho::AppSpecificInternalStorage::AES-GCM-256::AtRest');
  const baseKey = await crypto.subtle.importKey(
    'raw',
    rawKeyMaterial,
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  const derived = await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: INTERNAL_DEVICE_SALT,
      iterations: 50000,
      hash: 'SHA-256',
    },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );

  cachedInternalAesKey = derived;
  return derived;
}

function bufferToBase64(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToBuffer(base64: string): Uint8Array {
  const binary = atob(base64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Encrypts an object or data payload with AES-GCM 256 for internal files directory storage.
 */
export async function encryptForInternalStorage(data: unknown): Promise<string> {
  const aesKey = await getInternalAppAesKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const enc = new TextEncoder();
  const jsonStr = JSON.stringify(data);
  const encodedPlaintext = enc.encode(jsonStr);

  const cipherBuffer = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    aesKey,
    encodedPlaintext
  );

  const envelope = {
    v: INTERNAL_CIPHER_VERSION,
    iv: bufferToBase64(iv),
    ct: bufferToBase64(cipherBuffer),
    ts: Date.now(),
  };

  return JSON.stringify(envelope);
}

/**
 * Decrypts an AES-GCM 256 encrypted payload from internal files directory storage.
 */
export async function decryptFromInternalStorage<T>(rawEnvelopeString: string): Promise<T | null> {
  if (!rawEnvelopeString || !rawEnvelopeString.trim()) return null;

  try {
    const envelope = JSON.parse(rawEnvelopeString);
    if (!envelope || !envelope.iv || !envelope.ct) {
      // If legacy unencrypted JSON, return directly
      return envelope as T;
    }

    const aesKey = await getInternalAppAesKey();
    const iv = base64ToBuffer(envelope.iv);
    const cipherBuffer = base64ToBuffer(envelope.ct);

    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      aesKey,
      cipherBuffer as unknown as BufferSource
    );

    const dec = new TextDecoder();
    const jsonStr = dec.decode(decrypted);
    return JSON.parse(jsonStr) as T;
  } catch (err) {
    console.warn('Failed to decrypt internal storage payload:', err);
    return null;
  }
}

/**
 * Saves encrypted data to Android App-Specific Internal Files Directory and/or Web OPFS.
 */
export async function saveAppSpecificInternalFile(
  filename: string,
  data: unknown,
  vaultMode: VaultMode = 'primary'
): Promise<boolean> {
  try {
    const prefixedFilename = `${vaultMode}_${filename}.enc`;
    const encryptedString = await encryptForInternalStorage(data);

    let savedNative = false;
    // 1. Android App-Specific Internal Files Directory (context.getFilesDir() / likkho_internal_data)
    if (
      typeof window !== 'undefined' &&
      window.LikkhoNative &&
      typeof window.LikkhoNative.writeInternalAppFile === 'function'
    ) {
      savedNative = window.LikkhoNative.writeInternalAppFile(prefixedFilename, encryptedString);
    }

    // 2. Web OPFS (Origin Private File System) dedicated internal directory
    let savedOpfs = false;
    try {
      if (typeof navigator !== 'undefined' && navigator.storage && typeof navigator.storage.getDirectory === 'function') {
        const rootDir = await navigator.storage.getDirectory();
        const internalFolder = await rootDir.getDirectoryHandle(INTERNAL_DIR_NAME, { create: true });
        const fileHandle = await internalFolder.getFileHandle(prefixedFilename, { create: true });
        if ('createWritable' in fileHandle) {
          const writable = await (fileHandle as any).createWritable();
          await writable.write(encryptedString);
          await writable.close();
          savedOpfs = true;
        }
      }
    } catch {
      // OPFS not available or error
    }

    return savedNative || savedOpfs;
  } catch (err) {
    console.error('saveAppSpecificInternalFile error:', err);
    return false;
  }
}

/**
 * Reads and decrypts data from Android App-Specific Internal Files Directory and/or Web OPFS.
 */
export async function readAppSpecificInternalFile<T>(
  filename: string,
  vaultMode: VaultMode = 'primary'
): Promise<T | null> {
  const prefixedFilename = `${vaultMode}_${filename}.enc`;

  // 1. Check Android App-Specific Internal Files Directory
  if (
    typeof window !== 'undefined' &&
    window.LikkhoNative &&
    typeof window.LikkhoNative.readInternalAppFile === 'function'
  ) {
    try {
      const raw = window.LikkhoNative.readInternalAppFile(prefixedFilename);
      if (raw && raw.trim().length > 0) {
        const decrypted = await decryptFromInternalStorage<T>(raw);
        if (decrypted !== null) return decrypted;
      }
    } catch {
      // ignore
    }
  }

  // 2. Check Web OPFS internal directory
  try {
    if (typeof navigator !== 'undefined' && navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      const internalFolder = await rootDir.getDirectoryHandle(INTERNAL_DIR_NAME, { create: false });
      const fileHandle = await internalFolder.getFileHandle(prefixedFilename, { create: false });
      const file = await fileHandle.getFile();
      const text = await file.text();
      if (text && text.trim().length > 0) {
        const decrypted = await decryptFromInternalStorage<T>(text);
        if (decrypted !== null) return decrypted;
      }
    }
  } catch {
    // ignore
  }

  return null;
}

/**
 * Deletes a file from Android App-Specific Internal Files Directory and/or Web OPFS.
 */
export async function deleteAppSpecificInternalFile(
  filename: string,
  vaultMode: VaultMode = 'primary'
): Promise<boolean> {
  const prefixedFilename = `${vaultMode}_${filename}.enc`;

  let deletedNative = false;
  if (
    typeof window !== 'undefined' &&
    window.LikkhoNative &&
    typeof window.LikkhoNative.deleteInternalAppFile === 'function'
  ) {
    deletedNative = window.LikkhoNative.deleteInternalAppFile(prefixedFilename);
  }

  let deletedOpfs = false;
  try {
    if (typeof navigator !== 'undefined' && navigator.storage && typeof navigator.storage.getDirectory === 'function') {
      const rootDir = await navigator.storage.getDirectory();
      const internalFolder = await rootDir.getDirectoryHandle(INTERNAL_DIR_NAME, { create: false });
      await internalFolder.removeEntry(prefixedFilename);
      deletedOpfs = true;
    }
  } catch {
    // ignore
  }

  return deletedNative || deletedOpfs;
}

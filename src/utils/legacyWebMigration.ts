import {
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  loadActiveAppStateFromIDB,
} from './cryptoVault';
import { uint8ArrayToBase64Chunk } from './chunkedVaultStream';

const STORAGE_DARK_KEY = 'wikilog_dark_theme_v1';
const STORAGE_FULLSCREEN_KEY = 'wikilog_fullscreen_mode_v1';
const STORAGE_PIN_KEY = 'wikilog_passcode_v1';
const STORAGE_BIO_KEY = 'wikilog_biometrics_enabled_v1';
const STORAGE_ENC_HASH_KEY = 'wikilog_encryption_key_hash_v1';
const STORAGE_MIC_SETTINGS_KEY = 'wikilog_mic_settings_v1';
const STORAGE_LOGS_KEY = 'wikilog_in_app_logs_v1';

const STORAGE_SECONDARY_PIN_KEY = 'wikilog_covert_secondary_pin_v2';
const STORAGE_DECOY_ENC_HASH_KEY = 'wikilog_decoy_encryption_key_hash_v2';
const STORAGE_DECOY_MIC_SETTINGS_KEY = 'wikilog_decoy_mic_settings_v2';
const STORAGE_DECOY_LOGS_KEY = 'wikilog_decoy_in_app_logs_v2';

const IDB_LOGS_KEY = 'active_diary_logs_with_media';
const IDB_FONTS_KEY = 'active_custom_ttf_fonts';
const IDB_DECOY_LOGS_KEY = 'decoy_active_diary_logs_with_media';
const IDB_DECOY_FONTS_KEY = 'decoy_active_custom_ttf_fonts';

const IMAGE_DB_NAME = 'likkho_image_media_vault_v1';
const IMAGE_STORE_NAME = 'image_blobs';
const AUDIO_DB_NAME = 'likkho_audio_media_vault_v1';
const AUDIO_STORE_NAME = 'audio_blobs';

const CHUNK_BYTE_SIZE = 384 * 1024;

function parseJsonSafe<T>(raw: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function readLocalLogsFallback(key: string): DiaryLog[] {
  const parsed = parseJsonSafe<DiaryLog[]>(localStorage.getItem(key));
  return Array.isArray(parsed) ? parsed : [];
}

function openExistingIdbIfPresent(dbName: string): Promise<IDBDatabase | null> {
  return new Promise((resolve) => {
    try {
      if (typeof indexedDB === 'undefined') {
        resolve(null);
        return;
      }
      const req = indexedDB.open(dbName);
      let createdFresh = false;
      req.onupgradeneeded = () => {
        createdFresh = true;
      };
      req.onsuccess = () => {
        const db = req.result;
        if (createdFresh) {
          db.close();
          try {
            indexedDB.deleteDatabase(dbName);
          } catch {
            // ignore
          }
          resolve(null);
        } else {
          resolve(db);
        }
      };
      req.onerror = () => resolve(null);
      req.onblocked = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

async function listKeysFromIdbStore(db: IDBDatabase, storeName: string): Promise<string[]> {
  if (!db.objectStoreNames.contains(storeName)) return [];
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(storeName, 'readonly');
      const store = tx.objectStore(storeName);
      const req = store.getAllKeys();
      req.onsuccess = () => {
        const keys = Array.isArray(req.result) ? req.result.map((k) => String(k)) : [];
        resolve(keys);
      };
      req.onerror = () => resolve([]);
    } catch {
      resolve([]);
    }
  });
}

async function readBlobByKey(
  db: IDBDatabase,
  storeName: string,
  key: string
): Promise<Blob | null> {
  if (!db.objectStoreNames.contains(storeName)) return null;
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(storeName, 'readonly');
      const req = tx.objectStore(storeName).get(key);
      req.onsuccess = () => {
        const val = req.result;
        resolve(val instanceof Blob ? val : null);
      };
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

async function streamMediaStoreToKotlin(
  dbName: string,
  storeName: string,
  writer: (
    id: string,
    mimeType: string,
    base64Chunk: string,
    isFirst: boolean,
    isLast: boolean
  ) => boolean
): Promise<number> {
  const db = await openExistingIdbIfPresent(dbName);
  if (!db) return 0;

  let migratedCount = 0;
  try {
    const keys = await listKeysFromIdbStore(db, storeName);
    for (const id of keys) {
      const blob = await readBlobByKey(db, storeName, id);
      if (!blob || blob.size === 0) continue;
      const mimeType = blob.type || '';
      const bytes = new Uint8Array(await blob.arrayBuffer());
      const totalLen = bytes.length;
      if (totalLen === 0) continue;

      let ok = true;
      for (let offset = 0; offset < totalLen; offset += CHUNK_BYTE_SIZE) {
        const end = Math.min(offset + CHUNK_BYTE_SIZE, totalLen);
        const slice = bytes.subarray(offset, end);
        const isFirst = offset === 0;
        const isLast = end >= totalLen;
        const chunkB64 = uint8ArrayToBase64Chunk(slice);
        ok = writer(id, mimeType, chunkB64, isFirst, isLast);
        if (!ok) break;
      }
      if (ok) migratedCount += 1;
    }
  } finally {
    db.close();
  }
  return migratedCount;
}

export async function runLegacyWebViewMigration(): Promise<void> {
  const bridge = window.LikkhoLegacyMigrationBridge;
  if (!bridge) return;

  try {
    const primaryIdbLogs = await loadActiveAppStateFromIDB<DiaryLog[]>(IDB_LOGS_KEY, 'primary');
    const primaryIdbFonts = await loadActiveAppStateFromIDB<CustomFontItem[]>(IDB_FONTS_KEY, 'primary');
    const decoyIdbLogs = await loadActiveAppStateFromIDB<DiaryLog[]>(IDB_DECOY_LOGS_KEY, 'decoy');
    const decoyIdbFonts = await loadActiveAppStateFromIDB<CustomFontItem[]>(IDB_DECOY_FONTS_KEY, 'decoy');

    const primaryLogs: DiaryLog[] =
      Array.isArray(primaryIdbLogs) && primaryIdbLogs.length > 0
        ? primaryIdbLogs
        : readLocalLogsFallback(STORAGE_LOGS_KEY);
    const primaryFonts: CustomFontItem[] = Array.isArray(primaryIdbFonts) ? primaryIdbFonts : [];

    const decoyLogs: DiaryLog[] =
      Array.isArray(decoyIdbLogs) && decoyIdbLogs.length > 0
        ? decoyIdbLogs
        : readLocalLogsFallback(STORAGE_DECOY_LOGS_KEY);
    const decoyFonts: CustomFontItem[] = Array.isArray(decoyIdbFonts) ? decoyIdbFonts : [];

    let migratedImages = 0;
    if (bridge.writeMediaImageChunk) {
      migratedImages = await streamMediaStoreToKotlin(
        IMAGE_DB_NAME,
        IMAGE_STORE_NAME,
        bridge.writeMediaImageChunk.bind(bridge)
      );
    }

    let migratedAudio = 0;
    if (bridge.writeMediaAudioChunk) {
      migratedAudio = await streamMediaStoreToKotlin(
        AUDIO_DB_NAME,
        AUDIO_STORE_NAME,
        bridge.writeMediaAudioChunk.bind(bridge)
      );
    }

    const payload = {
      isDarkTheme: localStorage.getItem(STORAGE_DARK_KEY) === 'true',
      isFullScreen: localStorage.getItem(STORAGE_FULLSCREEN_KEY) === 'true',
      isBiometricEnabled: localStorage.getItem(STORAGE_BIO_KEY) === 'true',
      primaryPin: localStorage.getItem(STORAGE_PIN_KEY) || '',
      decoyPin: localStorage.getItem(STORAGE_SECONDARY_PIN_KEY) || '',
      primary: {
        logs: primaryLogs,
        customFonts: primaryFonts,
        micSettings: parseJsonSafe<MicRecordingSettings>(
          localStorage.getItem(STORAGE_MIC_SETTINGS_KEY)
        ),
        quickNotes: parseJsonSafe<unknown[]>(localStorage.getItem('likkho_quick_notes_primary')) || [],
        savedPasswords:
          parseJsonSafe<unknown[]>(localStorage.getItem('likkho_saved_passwords_primary')) || [],
        savedWordAnalysesJson: localStorage.getItem('wikilog_v1_saved_word_analyses') || '[]',
        encryptionKeyHash: localStorage.getItem(STORAGE_ENC_HASH_KEY) || '',
      },
      decoy: {
        logs: decoyLogs,
        customFonts: decoyFonts,
        micSettings: parseJsonSafe<MicRecordingSettings>(
          localStorage.getItem(STORAGE_DECOY_MIC_SETTINGS_KEY)
        ),
        quickNotes: parseJsonSafe<unknown[]>(localStorage.getItem('likkho_quick_notes_decoy')) || [],
        savedPasswords:
          parseJsonSafe<unknown[]>(localStorage.getItem('likkho_saved_passwords_decoy')) || [],
        savedWordAnalysesJson:
          localStorage.getItem('wikilog_decoy_v2_saved_word_analyses') || '[]',
        encryptionKeyHash: localStorage.getItem(STORAGE_DECOY_ENC_HASH_KEY) || '',
      },
      migratedImages,
      migratedAudio,
    };

    bridge.commitVaultPayload?.(JSON.stringify(payload));
    bridge.markMigrationDone?.(
      JSON.stringify({
        primaryLogs: primaryLogs.length,
        decoyLogs: decoyLogs.length,
        migratedImages,
        migratedAudio,
      })
    );
  } catch (err) {
    const msg = err instanceof Error ? err.message : 'Unknown migration error';
    bridge.markMigrationError?.(msg);
  }
}

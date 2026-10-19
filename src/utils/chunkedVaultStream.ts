/**
 * Streaming Chunked Binary Encryption & Storage Engine for Likkho
 * 
 * Solves Memory Bloat & Android WebView Crashes on 200MB+ Media:
 * 1. ZERO giant `JSON.stringify` of entire vault + media.
 * 2. Manifest record contains ONLY lightweight metadata (IDs/references, fonts, mic settings).
 * 3. Media Blobs are sliced (1.5MB chunks) via `blob.slice()` and encrypted independently.
 * 4. Each chunk uses a unique IV (Base IV + chunk counter) and AAD (Additional Authenticated Data)
 *    containing record type, chunk index, total chunks, and media ID to prevent reordering or tampering.
 * 5. Chunks are piped directly to output stream (Native SAF or OPFS createWritable) without RAM accumulation.
 * 6. Low-RAM Streaming Restore: reads frame-by-frame via `StreamSliceReader` (zero monolithic Blob in RAM).
 * 7. Low-RAM Multi-chunk assembly: decrypted chunks are immediately written to temporary IndexedDB blob records,
 *    keeping only one ~1.5MB chunk in memory at any point.
 * 8. Backward compatibility: supports legacy v1 (LKSTRM01) and dynamic KDF iterations/hashes (LKSTRM02).
 * 9. Completeness & Resiliency: verifies EOF marker (0xFF); skips incomplete media if EOF is seen without
 *    failing the entire restore, and cleans up temporary chunk records automatically on completion or error.
 * 10. Native-picked audio support: falls back to attachment `audioUri` if blob is not yet cached in store.
 */

import {
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  VaultMode,
  getVaultConfig,
  CanvasAudioAttachment,
} from './cryptoVault';
import {
  getImageBlobFromStore,
  saveImageBlobToStore,
  preloadLogImages,
} from './imageStorage';
import {
  getAudioBlobFromStore,
  saveAudioBlobToStore,
  convertNativeFileToWebUri,
} from './audioStorage';

export const CHUNKED_STREAM_MAGIC_V1 = new Uint8Array([
  0x4c, 0x4b, 0x53, 0x54, 0x52, 0x4d, 0x30, 0x31, // "LKSTRM01" (8 bytes)
]);

export const CHUNKED_STREAM_MAGIC_V2 = new Uint8Array([
  0x4c, 0x4b, 0x53, 0x54, 0x52, 0x4d, 0x30, 0x32, // "LKSTRM02" (8 bytes)
]);

export const STREAM_RECORD_TYPE_MANIFEST = 0x01;
export const STREAM_RECORD_TYPE_MEDIA_CHUNK = 0x02;
export const STREAM_RECORD_TYPE_EOF = 0xff;

export const DEFAULT_CHUNK_SIZE = 1.5 * 1024 * 1024; // 1.5 MB per chunk

export interface StreamVaultManifest {
  magic: 'WIKILOG_STREAM_V1';
  dbTarget: string;
  exportedAt: number;
  logs: DiaryLog[];
  customFonts: CustomFontItem[];
  micSettings?: MicRecordingSettings;
}

export interface StreamRestoreResult {
  logs: DiaryLog[];
  customFonts: CustomFontItem[];
  micSettings?: MicRecordingSettings;
  skippedMediaCount?: number;
  skippedMediaIds?: string[];
}

export interface StreamSliceReader {
  size: number;
  readSlice(offset: number, length: number): Promise<Uint8Array>;
}

export interface StreamBackupResult {
  totalBytes: number;
  entryCount: number;
  failedMediaCount: number;
  failedMediaIds: string[];
}

/**
 * Normalizes a Blob, File, or StreamSliceReader into a standard StreamSliceReader.
 */
export function asStreamSliceReader(source: StreamSliceReader | Blob): StreamSliceReader {
  if (
    source &&
    typeof (source as StreamSliceReader).readSlice === 'function' &&
    typeof (source as StreamSliceReader).size === 'number'
  ) {
    return source as StreamSliceReader;
  }
  const blob = source as Blob;
  return {
    size: blob.size,
    async readSlice(offset: number, length: number): Promise<Uint8Array> {
      const slice = blob.slice(offset, offset + length);
      const buf = await slice.arrayBuffer();
      return new Uint8Array(buf);
    },
  };
}

/**
 * Checks if raw bytes match LKSTRM01 or LKSTRM02 magic bytes.
 */
export function isChunkedStreamMagic(bytes: Uint8Array): boolean {
  if (bytes.length < 8) return false;
  let matchesV1 = true;
  let matchesV2 = true;
  for (let i = 0; i < 8; i++) {
    if (bytes[i] !== CHUNKED_STREAM_MAGIC_V1[i]) matchesV1 = false;
    if (bytes[i] !== CHUNKED_STREAM_MAGIC_V2[i]) matchesV2 = false;
  }
  return matchesV1 || matchesV2;
}

/**
 * Derives unique 12-byte IV for a chunk from base IV and chunk counter.
 */
export function deriveChunkIv(baseIv: Uint8Array, counter: number): Uint8Array {
  const iv = new Uint8Array(12);
  iv.set(baseIv.subarray(0, 8), 0);
  const dv = new DataView(iv.buffer);
  dv.setUint32(8, counter, false); // Big-Endian in last 4 bytes
  return iv;
}

/**
 * Builds AAD (Additional Authenticated Data) for AES-256-GCM.
 */
export function buildChunkAAD(
  recordType: number,
  chunkIndex: number,
  totalChunks: number,
  id: string
): Uint8Array {
  const enc = new TextEncoder();
  const idBytes = enc.encode(id);
  const aad = new Uint8Array(1 + 4 + 4 + idBytes.length);
  aad[0] = recordType;
  const dv = new DataView(aad.buffer);
  dv.setUint32(1, chunkIndex, false);
  dv.setUint32(5, totalChunks, false);
  aad.set(idBytes, 9);
  return aad;
}

/**
 * Derives AES-256-GCM CryptoKey using domain-configured KDF parameters.
 * Backward compatible with custom iterations / hash read from backup stream headers.
 */
export async function deriveStreamAesKey(
  passphrase: string,
  salt: Uint8Array,
  vaultMode: VaultMode = 'primary',
  customIterations?: number,
  customHash?: 'SHA-256' | 'SHA-512'
): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const cfg = getVaultConfig(vaultMode);
  const iterations = customIterations ?? cfg.kdfIterations;
  const hash = customHash ?? cfg.kdfHash;
  const contextPrefix = `LIKKHO_STREAM_VAULT_KDF_${cfg.dbFilename}::`;
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(`${contextPrefix}${passphrase}`),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt as unknown as BufferSource,
      iterations,
      hash,
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

/**
 * Builds a framed record buffer.
 */
export function createRecordFrame(
  recordType: number,
  id: string,
  chunkIndex: number,
  totalChunks: number,
  mimeType: string,
  iv: Uint8Array,
  cipherData: Uint8Array
): Uint8Array {
  const enc = new TextEncoder();
  const idBytes = enc.encode(id);
  const mimeBytes = enc.encode(mimeType);

  // Layout:
  // 1 byte: recordType
  // 2 bytes: idLen
  // idLen bytes: id
  // 4 bytes: chunkIndex
  // 4 bytes: totalChunks
  // 2 bytes: mimeLen
  // mimeLen bytes: mimeType
  // 12 bytes: iv
  // 4 bytes: cipherLen
  // cipherLen bytes: cipherData
  const headerLen = 1 + 2 + idBytes.length + 4 + 4 + 2 + mimeBytes.length + 12 + 4;
  const frame = new Uint8Array(headerLen + cipherData.length);
  const dv = new DataView(frame.buffer);

  let offset = 0;
  frame[offset++] = recordType;

  dv.setUint16(offset, idBytes.length, false);
  offset += 2;
  frame.set(idBytes, offset);
  offset += idBytes.length;

  dv.setUint32(offset, chunkIndex, false);
  offset += 4;

  dv.setUint32(offset, totalChunks, false);
  offset += 4;

  dv.setUint16(offset, mimeBytes.length, false);
  offset += 2;
  frame.set(mimeBytes, offset);
  offset += mimeBytes.length;

  frame.set(iv, offset);
  offset += 12;

  dv.setUint32(offset, cipherData.length, false);
  offset += 4;

  frame.set(cipherData, offset);
  return frame;
}

/**
 * Converts Uint8Array to Base64 in small chunks to avoid call stack limits.
 */
export function uint8ArrayToBase64Chunk(bytes: Uint8Array): string {
  let binary = '';
  const len = bytes.length;
  const chunk = 0x8000;
  for (let i = 0; i < len; i += chunk) {
    binary += String.fromCharCode.apply(
      null,
      Array.from(bytes.subarray(i, i + chunk))
    );
  }
  return btoa(binary);
}

/**
 * Converts Base64 to Uint8Array.
 */
export function base64ToUint8ArrayChunk(b64: string): Uint8Array {
  const bin = atob(b64);
  const len = bin.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes;
}

// ==================== Low-RAM Temporary Multi-Chunk IndexedDB Store ====================
const RESTORE_TEMP_DB_NAME = 'likkho_restore_temp_chunks_v1';
const RESTORE_TEMP_STORE = 'chunks';

let restoreDbPromise: Promise<IDBDatabase> | null = null;
function getRestoreTempDB(): Promise<IDBDatabase> {
  if (restoreDbPromise) return restoreDbPromise;
  restoreDbPromise = new Promise((resolve, reject) => {
    try {
      const req = indexedDB.open(RESTORE_TEMP_DB_NAME, 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(RESTORE_TEMP_STORE)) {
          db.createObjectStore(RESTORE_TEMP_STORE);
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    } catch (err) {
      reject(err);
    }
  });
  return restoreDbPromise;
}

async function writeTempChunkBlob(id: string, chunkIndex: number, blob: Blob): Promise<void> {
  const db = await getRestoreTempDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(RESTORE_TEMP_STORE, 'readwrite');
    tx.objectStore(RESTORE_TEMP_STORE).put(blob, `${id}_chunk_${chunkIndex}`);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function readTempChunkBlob(id: string, chunkIndex: number): Promise<Blob | null> {
  const db = await getRestoreTempDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(RESTORE_TEMP_STORE, 'readonly');
    const req = tx.objectStore(RESTORE_TEMP_STORE).get(`${id}_chunk_${chunkIndex}`);
    req.onsuccess = () => resolve((req.result as Blob) || null);
    req.onerror = () => reject(req.error);
  });
}

export async function deleteTempChunkBlobs(id: string, totalChunks: number): Promise<void> {
  try {
    const db = await getRestoreTempDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(RESTORE_TEMP_STORE, 'readwrite');
      const store = tx.objectStore(RESTORE_TEMP_STORE);
      for (let i = 0; i < totalChunks; i++) {
        store.delete(`${id}_chunk_${i}`);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}

export async function clearAllRestoreTempChunks(): Promise<void> {
  try {
    const db = await getRestoreTempDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(RESTORE_TEMP_STORE, 'readwrite');
      tx.objectStore(RESTORE_TEMP_STORE).clear();
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}

/**
 * Streams an entire backup chunk-by-chunk to the specified chunk consumer.
 * Never accumulates the 200MB+ dataset in memory.
 * Collects failed media items and returns counts to prevent false 100% success reporting.
 * Automatically falls back to attachment `audioUri` if blob is not yet cached in store.
 */
export async function streamBackupChunks(
  bundle: {
    logs: DiaryLog[];
    customFonts: CustomFontItem[];
    micSettings?: MicRecordingSettings;
  },
  passphrase: string,
  vaultMode: VaultMode,
  onWriteChunk: (chunkBytes: Uint8Array) => Promise<void>
): Promise<StreamBackupResult> {
  const enc = new TextEncoder();
  const cfg = getVaultConfig(vaultMode);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const baseIv = crypto.getRandomValues(new Uint8Array(12));
  const aesKey = await deriveStreamAesKey(passphrase, salt, vaultMode);

  let totalBytesWritten = 0;
  let chunkCounter = 0;
  const failedMediaIds: string[] = [];

  // 1. Write Stream Header (44 bytes - V2 format with dynamic KDF info)
  // Layout:
  // 0..7   (8 bytes) : Magic "LKSTRM02"
  // 8..9   (2 bytes) : Version = 2 (uint16)
  // 10..25 (16 bytes): Salt
  // 26..29 (4 bytes) : KDF iterations (uint32)
  // 30     (1 byte)  : Hash ID (1 = SHA-256, 2 = SHA-512)
  // 31     (1 byte)  : Reserved (0x00)
  // 32..43 (12 bytes): Base IV
  const header = new Uint8Array(44);
  header.set(CHUNKED_STREAM_MAGIC_V2, 0); // 8 bytes
  const hDv = new DataView(header.buffer);
  hDv.setUint16(8, 2, false); // version = 2
  header.set(salt, 10); // 16 bytes
  hDv.setUint32(26, cfg.kdfIterations, false); // dynamic KDF iterations
  header[30] = cfg.kdfHash === 'SHA-512' ? 2 : 1; // hash identifier
  header[31] = 0; // reserved
  header.set(baseIv, 32); // 12 bytes

  await onWriteChunk(header);
  totalBytesWritten += header.length;

  // 2. Write Manifest Record (Type 0x01)
  // Contains only logs with media IDs, custom fonts, mic settings. Lightweight JSON!
  const manifest: StreamVaultManifest = {
    magic: 'WIKILOG_STREAM_V1',
    dbTarget: cfg.dbFilename,
    exportedAt: Date.now(),
    logs: bundle.logs,
    customFonts: bundle.customFonts || [],
    micSettings: bundle.micSettings,
  };
  const manifestBytes = enc.encode(JSON.stringify(manifest));
  const manifestIv = deriveChunkIv(baseIv, chunkCounter++);
  const manifestAad = buildChunkAAD(STREAM_RECORD_TYPE_MANIFEST, 0, 1, 'manifest');
  const encryptedManifest = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: manifestIv as unknown as BufferSource, additionalData: manifestAad as unknown as BufferSource, tagLength: 128 },
    aesKey,
    manifestBytes
  );

  const manifestFrame = createRecordFrame(
    STREAM_RECORD_TYPE_MANIFEST,
    'manifest',
    0,
    1,
    'application/json',
    manifestIv,
    new Uint8Array(encryptedManifest)
  );
  await onWriteChunk(manifestFrame);
  totalBytesWritten += manifestFrame.length;

  // 3. Collect media references to stream
  const mediaToStream: {
    id: string;
    type: 'image' | 'audio';
    audioAttachment?: CanvasAudioAttachment;
  }[] = [];
  const processedMediaIds = new Set<string>();

  for (const log of bundle.logs) {
    const imgRefs = [
      log.pfpDataUrl,
      log.canvasBgDataUrl,
      ...(log.canvasImages || []).map((c) => c.dataUrl),
    ];
    for (const r of imgRefs) {
      if (
        r &&
        !r.startsWith('data:') &&
        !r.startsWith('blob:') &&
        !r.startsWith('http') &&
        !processedMediaIds.has(r)
      ) {
        processedMediaIds.add(r);
        mediaToStream.push({ id: r, type: 'image' });
      }
    }
    for (const a of log.audioAttachments || []) {
      if (a.id && !processedMediaIds.has(a.id)) {
        processedMediaIds.add(a.id);
        mediaToStream.push({ id: a.id, type: 'audio', audioAttachment: a });
      }
    }
  }

  // 4. Stream each media blob chunk-by-chunk
  for (const item of mediaToStream) {
    try {
      let blob: Blob | null = null;
      if (item.type === 'image') {
        blob = await getImageBlobFromStore(item.id);
      } else {
        blob = await getAudioBlobFromStore(item.id);
        // Fallback: If not in Blob store but attachment has audioUri or dataUrl
        if ((!blob || blob.size === 0) && item.audioAttachment) {
          if (item.audioAttachment.audioUri) {
            try {
              const webUri = convertNativeFileToWebUri(item.audioAttachment.audioUri);
              const res = await fetch(webUri);
              if (res.ok) {
                blob = await res.blob();
              }
            } catch (fetchErr) {
              console.warn(`Failed fetching audioUri for attachment ${item.id}:`, fetchErr);
            }
          }
          if ((!blob || blob.size === 0) && item.audioAttachment.dataUrl) {
            try {
              const res = await fetch(item.audioAttachment.dataUrl);
              if (res.ok) {
                blob = await res.blob();
              }
            } catch {}
          }
        }
      }

      if (!blob || blob.size === 0) {
        if (!failedMediaIds.includes(item.id)) {
          failedMediaIds.push(item.id);
        }
        continue;
      }

      const totalChunks = Math.max(1, Math.ceil(blob.size / DEFAULT_CHUNK_SIZE));
      let mimeType: string;
      if (item.type === 'audio') {
        if (blob.type && blob.type.startsWith('audio/')) {
          mimeType = blob.type;
        } else {
          const fmt = (item.audioAttachment?.format || '').toLowerCase().trim();
          if (fmt === 'mp3') {
            mimeType = 'audio/mpeg';
          } else if (fmt === 'm4a') {
            mimeType = 'audio/mp4';
          } else if (fmt) {
            mimeType = `audio/${fmt}`;
          } else {
            mimeType = 'audio/wav';
          }
        }
      } else {
        mimeType = (blob.type && blob.type.startsWith('image/')) ? blob.type : 'image/jpeg';
      }

      for (let cIdx = 0; cIdx < totalChunks; cIdx++) {
        const start = cIdx * DEFAULT_CHUNK_SIZE;
        const end = Math.min(blob.size, start + DEFAULT_CHUNK_SIZE);
        const sliceBlob = blob.slice(start, end);
        const sliceBuffer = await sliceBlob.arrayBuffer();

        const chunkIv = deriveChunkIv(baseIv, chunkCounter++);
        const aad = buildChunkAAD(STREAM_RECORD_TYPE_MEDIA_CHUNK, cIdx, totalChunks, item.id);

        const encryptedSlice = await crypto.subtle.encrypt(
          { name: 'AES-GCM', iv: chunkIv as unknown as BufferSource, additionalData: aad as unknown as BufferSource, tagLength: 128 },
          aesKey,
          sliceBuffer
        );

        const frame = createRecordFrame(
          STREAM_RECORD_TYPE_MEDIA_CHUNK,
          item.id,
          cIdx,
          totalChunks,
          mimeType,
          chunkIv,
          new Uint8Array(encryptedSlice)
        );

        await onWriteChunk(frame);
        totalBytesWritten += frame.length;
      }
    } catch (err) {
      console.warn(`Failed to stream media chunk for ${item.id}:`, err);
      if (!failedMediaIds.includes(item.id)) {
        failedMediaIds.push(item.id);
      }
      // If consumer stream encountered a fatal write failure, rethrow to terminate backup
      if (err instanceof Error && (
        err.message.includes('SAF storage') ||
        err.message.includes('createWritable') ||
        err.message.includes('Backup folder') ||
        err.message.includes('abort')
      )) {
        throw err;
      }
    }
  }

  // 5. Write EOF Record (Type 0xFF)
  const eofIv = deriveChunkIv(baseIv, chunkCounter++);
  const eofAad = buildChunkAAD(STREAM_RECORD_TYPE_EOF, 0, 1, 'eof');
  const encryptedEof = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: eofIv as unknown as BufferSource, additionalData: eofAad as unknown as BufferSource, tagLength: 128 },
    aesKey,
    enc.encode('EOF')
  );
  const eofFrame = createRecordFrame(
    STREAM_RECORD_TYPE_EOF,
    'eof',
    0,
    1,
    'text/plain',
    eofIv,
    new Uint8Array(encryptedEof)
  );
  await onWriteChunk(eofFrame);
  totalBytesWritten += eofFrame.length;

  return {
    totalBytes: totalBytesWritten,
    entryCount: bundle.logs.length,
    failedMediaCount: failedMediaIds.length,
    failedMediaIds,
  };
}

/**
 * Restores a chunked streaming backup file by reading slice-by-slice.
 * Compatible with `StreamSliceReader`, `File`, or `Blob`.
 * Enforces EOF verification and low-RAM multi-chunk handling via IndexedDB.
 * If EOF is seen, skips incomplete media chunks without failing the entire restore.
 * Automatically cleans up any leftover temporary chunks on failure.
 */
export async function restoreFromStreamSource(
  source: StreamSliceReader | Blob,
  passphrase: string,
  vaultMode: VaultMode
): Promise<StreamRestoreResult | null> {
  const dec = new TextDecoder();
  const reader = asStreamSliceReader(source);
  const fileSize = reader.size;

  if (fileSize < 42) {
    return null; // Not a valid stream
  }

  // Clear any residual temp chunks from earlier runs
  await clearAllRestoreTempChunks();

  // Track multi-chunk media items being assembled into IndexedDB
  const multiChunkTracker = new Map<string, { totalChunks: number; mimeType: string; receivedIndices: Set<number> }>();

  try {
    // 1. Read first 44 bytes to determine header format
    const initialHeaderBytes = await reader.readSlice(0, Math.min(44, fileSize));
    if (!isChunkedStreamMagic(initialHeaderBytes)) {
      return null; // Not a streaming chunked file (fallback to legacy v1/v2 single envelope)
    }

    const hDv = new DataView(initialHeaderBytes.buffer, initialHeaderBytes.byteOffset, initialHeaderBytes.byteLength);
    const version = hDv.getUint16(8, false);

    let headerLength: number;
    let salt: Uint8Array;
    let iterations: number;
    let hash: 'SHA-256' | 'SHA-512';
    let baseIv: Uint8Array;

    if (version === 1 || initialHeaderBytes[7] === 0x31) {
      // Legacy LKSTRM01 V1 header: 42 bytes, fixed 100k iterations, SHA-256
      headerLength = 42;
      salt = initialHeaderBytes.subarray(10, 26);
      iterations = hDv.getUint32(26, false) || 100000;
      hash = 'SHA-256';
      baseIv = initialHeaderBytes.subarray(30, 42);
    } else {
      // LKSTRM02 V2 header: 44 bytes, dynamic iterations & hash
      if (fileSize < 44) return null;
      headerLength = 44;
      salt = initialHeaderBytes.subarray(10, 26);
      iterations = hDv.getUint32(26, false);
      const hashId = initialHeaderBytes[30];
      hash = hashId === 2 ? 'SHA-512' : 'SHA-256';
      baseIv = initialHeaderBytes.subarray(32, 44);
    }

    const aesKey = await deriveStreamAesKey(passphrase, salt, vaultMode, iterations, hash);

    let cursor = headerLength;
    let restoredManifest: StreamVaultManifest | null = null;
    let eofSeen = false;

    // Track audio IDs whose Blobs were restored into IndexedDB
    const savedAudioIds = new Set<string>();

    while (cursor < fileSize) {
      // Read frame header prefix (up to 300 bytes)
      const readLen = Math.min(300, fileSize - cursor);
      const frameHeaderBytes = await reader.readSlice(cursor, readLen);
      if (frameHeaderBytes.length < 25) break;

      const dv = new DataView(frameHeaderBytes.buffer, frameHeaderBytes.byteOffset, frameHeaderBytes.byteLength);
      let offset = 0;
      const recordType = frameHeaderBytes[offset++];
      const idLen = dv.getUint16(offset, false);
      offset += 2;

      if (offset + idLen > frameHeaderBytes.length) {
        // Very long ID header safety
        const extendedHeaderBytes = await reader.readSlice(cursor, Math.min(offset + idLen + 50, fileSize - cursor));
        if (extendedHeaderBytes.length < offset + idLen + 24) break;
      }

      const id = dec.decode(frameHeaderBytes.subarray(offset, offset + idLen));
      offset += idLen;

      const chunkIndex = dv.getUint32(offset, false);
      offset += 4;

      const totalChunks = dv.getUint32(offset, false);
      offset += 4;

      const mimeLen = dv.getUint16(offset, false);
      offset += 2;

      const mimeType = dec.decode(frameHeaderBytes.subarray(offset, offset + mimeLen));
      offset += mimeLen;

      const chunkIv = frameHeaderBytes.subarray(offset, offset + 12);
      offset += 12;

      const cipherLen = dv.getUint32(offset, false);
      offset += 4;

      const frameHeaderLength = offset;
      const cipherDataStart = cursor + frameHeaderLength;
      const cipherDataEnd = cipherDataStart + cipherLen;

      if (cipherDataEnd > fileSize) {
        throw new Error('Backup file adhoori hai: corrupted or truncated stream frame.');
      }

      // Read ONLY this chunk's ciphertext into memory
      const cipherBytes = await reader.readSlice(cipherDataStart, cipherLen);
      cursor = cipherDataEnd;

      const aad = buildChunkAAD(recordType, chunkIndex, totalChunks, id);

      let decryptedBuffer: ArrayBuffer;
      try {
        decryptedBuffer = await crypto.subtle.decrypt(
          { name: 'AES-GCM', iv: chunkIv as unknown as BufferSource, additionalData: aad as unknown as BufferSource, tagLength: 128 },
          aesKey,
          cipherBytes as unknown as BufferSource
        );
      } catch {
        throw new Error('Incorrect Encryption Key.');
      }

      if (recordType === STREAM_RECORD_TYPE_MANIFEST) {
        const jsonStr = dec.decode(decryptedBuffer);
        restoredManifest = JSON.parse(jsonStr) as StreamVaultManifest;
      } else if (recordType === STREAM_RECORD_TYPE_MEDIA_CHUNK) {
        if (totalChunks === 1) {
          // Single chunk: write directly to IndexedDB store as Blob without accumulation
          const blob = new Blob([decryptedBuffer], { type: mimeType });
          if (typeof mimeType === 'string' && mimeType.startsWith('audio/')) {
            await saveAudioBlobToStore(id, blob);
            savedAudioIds.add(id);
          } else {
            await saveImageBlobToStore(id, blob);
          }
        } else {
          // Multi-chunk: immediately write chunk to temporary IndexedDB store to prevent RAM accumulation
          const chunkBlob = new Blob([decryptedBuffer], { type: 'application/octet-stream' });
          await writeTempChunkBlob(id, chunkIndex, chunkBlob);

          if (!multiChunkTracker.has(id)) {
            multiChunkTracker.set(id, { totalChunks, mimeType, receivedIndices: new Set() });
          }
          const tracker = multiChunkTracker.get(id)!;
          tracker.receivedIndices.add(chunkIndex);

          // Check if all chunks for this media item are gathered
          if (tracker.receivedIndices.size === totalChunks) {
            const chunkParts: Blob[] = [];
            for (let i = 0; i < totalChunks; i++) {
              const part = await readTempChunkBlob(id, i);
              if (!part) {
                throw new Error(`Missing chunk ${i} for media ${id}.`);
              }
              chunkParts.push(part);
            }

            // Composite Blob is created from disk-backed IndexedDB Blobs (zero JS heap bloat)
            const fullBlob = new Blob(chunkParts, { type: tracker.mimeType });
            if (typeof tracker.mimeType === 'string' && tracker.mimeType.startsWith('audio/')) {
              await saveAudioBlobToStore(id, fullBlob);
              savedAudioIds.add(id);
            } else {
              await saveImageBlobToStore(id, fullBlob);
            }

            await deleteTempChunkBlobs(id, totalChunks);
            multiChunkTracker.delete(id);
          }
        }
      } else if (recordType === STREAM_RECORD_TYPE_EOF) {
        eofSeen = true;
        break;
      }
    }

    // 1. Sirf EOF na milne par 'Backup file adhoori hai' error do!
    if (!eofSeen) {
      throw new Error('Backup file adhoori hai: missing EOF record marker.');
    }

    // 2. Agar EOF mil chuka hai to adhoore multi-chunk media ko skip karke unke temp chunks delete karo aur warning list return karo
    const skippedMediaIds: string[] = [];
    if (multiChunkTracker.size > 0) {
      for (const [id, tracker] of multiChunkTracker.entries()) {
        await deleteTempChunkBlobs(id, tracker.totalChunks);
        skippedMediaIds.push(id);
      }
      multiChunkTracker.clear();
    }

    if (!restoredManifest || !Array.isArray(restoredManifest.logs)) {
      throw new Error('Backup file adhoori hai: stream backup contains invalid or missing logs manifest.');
    }

    // For every audio attachment whose Blob was restored into IndexedDB, clear stale audioUri
    for (const log of restoredManifest.logs) {
      if (log.audioAttachments && Array.isArray(log.audioAttachments)) {
        for (const att of log.audioAttachments) {
          if (savedAudioIds.has(att.id)) {
            att.audioUri = undefined;
          }
        }
      }
    }

    await preloadLogImages(restoredManifest.logs);

    return {
      logs: restoredManifest.logs,
      customFonts: restoredManifest.customFonts || [],
      micSettings: restoredManifest.micSettings,
      skippedMediaCount: skippedMediaIds.length,
      skippedMediaIds,
    };
  } catch (err) {
    // Restore fail hone par deleteTempChunkBlobs se bache hue temp chunks saaf karo
    try {
      for (const [id, tracker] of multiChunkTracker.entries()) {
        await deleteTempChunkBlobs(id, tracker.totalChunks);
      }
      await clearAllRestoreTempChunks();
    } catch {
      // ignore
    }
    throw err;
  }
}

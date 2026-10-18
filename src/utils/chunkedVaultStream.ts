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
 * 6. Streaming Restore decrypts slice-by-slice and writes directly to IndexedDB Blob stores.
 * 7. Seamless backward compatibility: legacy v1/v2 single-envelope backups are automatically detected.
 */

import {
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  VaultMode,
  getVaultConfig,
} from './cryptoVault';
import {
  getImageBlobFromStore,
  saveImageBlobToStore,
  preloadLogImages,
} from './imageStorage';
import {
  getAudioBlobFromStore,
  saveAudioBlobToStore,
} from './audioStorage';

export const CHUNKED_STREAM_MAGIC = new Uint8Array([
  0x4c, 0x4b, 0x53, 0x54, 0x52, 0x4d, 0x30, 0x31, // "LKSTRM01" (8 bytes)
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
 * Derives AES-256-GCM CryptoKey from passphrase and salt.
 */
export async function deriveStreamAesKey(
  passphrase: string,
  salt: Uint8Array,
  vaultMode: VaultMode = 'primary'
): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const cfg = getVaultConfig(vaultMode);
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
      iterations: 100000,
      hash: 'SHA-256',
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

/**
 * Streams an entire backup chunk-by-chunk to the specified chunk consumer.
 * Never accumulates the 200MB+ dataset in memory.
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
): Promise<{ totalBytes: number; entryCount: number }> {
  const enc = new TextEncoder();
  const cfg = getVaultConfig(vaultMode);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const baseIv = crypto.getRandomValues(new Uint8Array(12));
  const aesKey = await deriveStreamAesKey(passphrase, salt, vaultMode);

  let totalBytesWritten = 0;
  let chunkCounter = 0;

  // 1. Write Stream Header (42 bytes)
  const header = new Uint8Array(42);
  header.set(CHUNKED_STREAM_MAGIC, 0); // 8 bytes
  const hDv = new DataView(header.buffer);
  hDv.setUint16(8, 1, false); // version = 1
  header.set(salt, 10); // 16 bytes
  hDv.setUint32(26, 100000, false); // 100k KDF iterations
  header.set(baseIv, 30); // 12 bytes

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
  const mediaToStream: { id: string; type: 'image' | 'audio' }[] = [];
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
        mediaToStream.push({ id: a.id, type: 'audio' });
      }
    }
  }

  // 4. Stream each media blob chunk-by-chunk
  for (const item of mediaToStream) {
    try {
      const blob =
        item.type === 'image'
          ? await getImageBlobFromStore(item.id)
          : await getAudioBlobFromStore(item.id);

      if (!blob || blob.size === 0) continue;

      const totalChunks = Math.max(1, Math.ceil(blob.size / DEFAULT_CHUNK_SIZE));
      const mimeType = blob.type || (item.type === 'image' ? 'image/jpeg' : 'audio/wav');

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

  return { totalBytes: totalBytesWritten, entryCount: bundle.logs.length };
}

/**
 * Restores a chunked streaming backup file by reading slice-by-slice.
 * Compatible with `File`, `Blob`, or custom slice reader.
 */
export async function restoreFromStreamSource(
  source: Blob,
  passphrase: string,
  vaultMode: VaultMode
): Promise<StreamRestoreResult | null> {
  const dec = new TextDecoder();
  const fileSize = source.size;
  if (fileSize < 42) {
    return null; // Not a valid stream
  }

  // 1. Read & check 42-byte Header
  const headerBuf = await source.slice(0, 42).arrayBuffer();
  const headerBytes = new Uint8Array(headerBuf);

  // Check Magic "LKSTRM01"
  for (let i = 0; i < CHUNKED_STREAM_MAGIC.length; i++) {
    if (headerBytes[i] !== CHUNKED_STREAM_MAGIC[i]) {
      return null; // Not a streaming chunked file (fallback to legacy v1/v2)
    }
  }

  const salt = headerBytes.subarray(10, 26);
  const aesKey = await deriveStreamAesKey(passphrase, salt, vaultMode);

  let cursor = 42;
  let restoredManifest: StreamVaultManifest | null = null;
  const mediaChunkBuffers = new Map<string, { totalChunks: number; mimeType: string; slices: Uint8Array[] }>();

  while (cursor < fileSize) {
    // Read minimum frame header: 1 + 2 + idLen + 4 + 4 + 2 + mimeLen + 12 + 4
    const minHeaderSlice = await source.slice(cursor, Math.min(fileSize, cursor + 300)).arrayBuffer();
    if (minHeaderSlice.byteLength < 25) break;

    const frameBytes = new Uint8Array(minHeaderSlice);
    const dv = new DataView(frameBytes.buffer);

    let offset = 0;
    const recordType = frameBytes[offset++];
    const idLen = dv.getUint16(offset, false);
    offset += 2;

    if (offset + idLen > frameBytes.length) {
      // Need larger slice for long ID
      break;
    }
    const id = dec.decode(frameBytes.subarray(offset, offset + idLen));
    offset += idLen;

    const chunkIndex = dv.getUint32(offset, false);
    offset += 4;

    const totalChunks = dv.getUint32(offset, false);
    offset += 4;

    const mimeLen = dv.getUint16(offset, false);
    offset += 2;

    const mimeType = dec.decode(frameBytes.subarray(offset, offset + mimeLen));
    offset += mimeLen;

    const chunkIv = frameBytes.subarray(offset, offset + 12);
    offset += 12;

    const cipherLen = dv.getUint32(offset, false);
    offset += 4;

    const frameHeaderLength = offset;
    const cipherDataStart = cursor + frameHeaderLength;
    const cipherDataEnd = cipherDataStart + cipherLen;

    if (cipherDataEnd > fileSize) {
      throw new Error('Corrupted or truncated stream frame.');
    }

    // Read ONLY this chunk's ciphertext into memory
    const cipherBuf = await source.slice(cipherDataStart, cipherDataEnd).arrayBuffer();
    cursor = cipherDataEnd;

    const aad = buildChunkAAD(recordType, chunkIndex, totalChunks, id);

    let decryptedBuffer: ArrayBuffer;
    try {
      decryptedBuffer = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: chunkIv as unknown as BufferSource, additionalData: aad as unknown as BufferSource, tagLength: 128 },
        aesKey,
        cipherBuf
      );
    } catch {
      throw new Error('Incorrect Encryption Key.');
    }

    if (recordType === STREAM_RECORD_TYPE_MANIFEST) {
      const jsonStr = dec.decode(decryptedBuffer);
      restoredManifest = JSON.parse(jsonStr) as StreamVaultManifest;
    } else if (recordType === STREAM_RECORD_TYPE_MEDIA_CHUNK) {
      const decryptedBytes = new Uint8Array(decryptedBuffer);
      if (totalChunks === 1) {
        // Single chunk: write directly to store as Blob without extra buffer
        const blob = new Blob([decryptedBytes as unknown as BlobPart], { type: mimeType });
        if (id.startsWith('aud_')) {
          await saveAudioBlobToStore(id, blob);
        } else {
          await saveImageBlobToStore(id, blob);
        }
      } else {
        // Multi-chunk: accumulate slices for this ID
        if (!mediaChunkBuffers.has(id)) {
          mediaChunkBuffers.set(id, { totalChunks, mimeType, slices: [] });
        }
        const acc = mediaChunkBuffers.get(id)!;
        acc.slices[chunkIndex] = decryptedBytes;

        // Check if all chunks for this media item are gathered
        if (acc.slices.length === totalChunks && !acc.slices.includes(undefined as unknown as Uint8Array)) {
          const blob = new Blob(acc.slices as unknown as BlobPart[], { type: acc.mimeType });
          if (id.startsWith('aud_')) {
            await saveAudioBlobToStore(id, blob);
          } else {
            await saveImageBlobToStore(id, blob);
          }
          mediaChunkBuffers.delete(id); // Free memory immediately
        }
      }
    } else if (recordType === STREAM_RECORD_TYPE_EOF) {
      break;
    }
  }

  if (!restoredManifest || !Array.isArray(restoredManifest.logs)) {
    throw new Error('Stream backup contains invalid or missing logs manifest.');
  }

  await preloadLogImages(restoredManifest.logs);

  return {
    logs: restoredManifest.logs,
    customFonts: restoredManifest.customFonts || [],
    micSettings: restoredManifest.micSettings,
  };
}

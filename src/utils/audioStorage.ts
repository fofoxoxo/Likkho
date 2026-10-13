/**
 * High-Performance Native File URI & IndexedDB Binary Audio Storage Engine
 * 
 * Solves Android WebView & JavaScript Runtime Out-Of-Memory (OOM) Errors:
 * 1. Stream directly from Native Local File Paths / Capacitor URIs without holding Base64 in RAM.
 * 2. Dedicated IndexedDB Blob Store for Web/PWA: Blobs are written directly to device disk by Blink/Chromium,
 *    keeping 0 megabytes of audio binary strings in the JavaScript Heap and React State.
 * 3. Lazy Loading & Single Active Playback Session: Players only initialize on explicit user Play click.
 * 4. Automatic Garbage Collection & Object URL Revocation: Blobs are revoked immediately upon stop/pause/unmount.
 */

import { CanvasAudioAttachment } from './cryptoVault';

const AUDIO_DB_NAME = 'likkho_audio_media_vault_v1';
const AUDIO_STORE_NAME = 'audio_blobs';

// Map of currently active Object URLs created for playback: attachmentId -> blobUrl
const activeObjectUrls = new Map<string, string>();

let dbPromise: Promise<IDBDatabase> | null = null;

function getAudioDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    try {
      const request = indexedDB.open(AUDIO_DB_NAME, 1);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(AUDIO_STORE_NAME)) {
          db.createObjectStore(AUDIO_STORE_NAME);
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error('Audio IndexedDB blocked'));
    } catch (err) {
      reject(err);
    }
  });
  return dbPromise;
}

/**
 * Saves a binary audio Blob into IndexedDB.
 * Chromium and Android WebView persist Blobs directly onto disk without keeping them in the V8 heap.
 */
export async function saveAudioBlobToStore(id: string, blob: Blob): Promise<void> {
  try {
    const db = await getAudioDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(AUDIO_STORE_NAME, 'readwrite');
      const store = tx.objectStore(AUDIO_STORE_NAME);
      store.put(blob, id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } catch (err) {
    console.error('Failed to save audio blob to IndexedDB:', err);
  }
}

/**
 * Retrieves a binary audio Blob from IndexedDB on-demand.
 */
export async function getAudioBlobFromStore(id: string): Promise<Blob | null> {
  try {
    const db = await getAudioDB();
    return await new Promise<Blob | null>((resolve, reject) => {
      const tx = db.transaction(AUDIO_STORE_NAME, 'readonly');
      const store = tx.objectStore(AUDIO_STORE_NAME);
      const req = store.get(id);
      req.onsuccess = () => resolve((req.result as Blob) || null);
      req.onerror = () => reject(req.error);
    });
  } catch (err) {
    console.error('Failed to get audio blob from IndexedDB:', err);
    return null;
  }
}

/**
 * Deletes an audio Blob from IndexedDB.
 */
export async function deleteAudioBlobFromStore(id: string): Promise<void> {
  try {
    const db = await getAudioDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(AUDIO_STORE_NAME, 'readwrite');
      const store = tx.objectStore(AUDIO_STORE_NAME);
      store.delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}

/**
 * Converts a native device file path into a streamable WebView URI.
 * Uses Capacitor.convertFileSrc if available, or formats http://localhost/_capacitor_file_
 */
export function convertNativeFileToWebUri(filePath: string): string {
  if (!filePath) return '';
  if (
    filePath.startsWith('http://') ||
    filePath.startsWith('https://') ||
    filePath.startsWith('blob:') ||
    filePath.startsWith('data:')
  ) {
    return filePath;
  }

  // 1. Check Capacitor bridge
  const cap = (window as unknown as { Capacitor?: { convertFileSrc?: (p: string) => string } }).Capacitor;
  if (cap?.convertFileSrc) {
    return cap.convertFileSrc(filePath);
  }

  // 2. Check Likkho Native bridge
  if (window.LikkhoNative && typeof window.LikkhoNative.getNativeAudioFileUri === 'function') {
    const bridgeUri = window.LikkhoNative.getNativeAudioFileUri(filePath);
    if (bridgeUri) return bridgeUri;
  }

  // 3. Fallback to standard Capacitor file serving scheme
  const cleanPath = filePath.startsWith('/') ? filePath : `/${filePath}`;
  return `http://localhost/_capacitor_file_${cleanPath}`;
}

/**
 * Solution 2 & 3: Acquire Audio Playback URL On-Demand (Lazy Loading)
 * - Returns a streamable native URL or creates a temporary Blob URL.
 * - Flags isObjectUrl = true if a URL.createObjectURL was created so it can be revoked upon finish/pause.
 */
export async function acquireAudioPlaybackUrl(
  attachment: CanvasAudioAttachment
): Promise<{ url: string; isObjectUrl: boolean }> {
  // 1. Direct native file path / Capacitor stream (Zero JS heap memory!)
  if (attachment.audioUri) {
    const webUri = convertNativeFileToWebUri(attachment.audioUri);
    return { url: webUri, isObjectUrl: false };
  }

  // 2. Fetch binary Blob from IndexedDB on-demand
  const storedBlob = await getAudioBlobFromStore(attachment.id);
  if (storedBlob) {
    // Revoke any previous object URL for this attachment to prevent memory leak
    const existingUrl = activeObjectUrls.get(attachment.id);
    if (existingUrl) {
      try {
        URL.revokeObjectURL(existingUrl);
      } catch {
        // ignore
      }
    }
    const newObjectUrl = URL.createObjectURL(storedBlob);
    activeObjectUrls.set(attachment.id, newObjectUrl);
    return { url: newObjectUrl, isObjectUrl: true };
  }

  // 3. If dataUrl exists (legacy logs or imported smaller voice notes)
  if (attachment.dataUrl) {
    // If it's a large data URL, convert to Blob on-the-fly and save to IndexedDB to migrate it
    if (attachment.dataUrl.startsWith('data:') && attachment.dataUrl.length > 50000) {
      try {
        const res = await fetch(attachment.dataUrl);
        const blob = await res.blob();
        await saveAudioBlobToStore(attachment.id, blob);
        const objUrl = URL.createObjectURL(blob);
        activeObjectUrls.set(attachment.id, objUrl);
        return { url: objUrl, isObjectUrl: true };
      } catch {
        return { url: attachment.dataUrl, isObjectUrl: false };
      }
    }
    return { url: attachment.dataUrl, isObjectUrl: false };
  }

  return { url: '', isObjectUrl: false };
}

/**
 * Solution 3: Proper Memory Cleanup and Revoking Blob URLs
 * Immediately frees browser engine memory when playback pauses, ends, or component unmounts.
 */
export function releaseAudioPlaybackUrl(attachmentId: string, specificUrl?: string): void {
  // Check tracked object URL
  const trackedUrl = activeObjectUrls.get(attachmentId);
  if (trackedUrl) {
    try {
      URL.revokeObjectURL(trackedUrl);
    } catch {
      // ignore
    }
    activeObjectUrls.delete(attachmentId);
  }

  // Check passed URL if different and starts with blob:
  if (specificUrl && specificUrl.startsWith('blob:') && specificUrl !== trackedUrl) {
    try {
      URL.revokeObjectURL(specificUrl);
    } catch {
      // ignore
    }
  }
}

/**
 * Revokes all currently active playback object URLs across the application.
 */
export function releaseAllAudioPlaybackUrls(): void {
  activeObjectUrls.forEach((url) => {
    try {
      URL.revokeObjectURL(url);
    } catch {
      // ignore
    }
  });
  activeObjectUrls.clear();
}

/**
 * Solution 1: Saves audio data efficiently so giant Base64 strings are never stored in log state.
 * If source is a Blob, writes directly to IndexedDB and returns dataUrl: ''.
 * If nativePath is provided, stores nativePath in audioUri and returns dataUrl: ''.
 */
export async function persistAudioAttachment(
  id: string,
  source: Blob | string,
  nativePath?: string
): Promise<{ audioUri?: string; dataUrl: string }> {
  if (nativePath) {
    return { audioUri: nativePath, dataUrl: '' };
  }

  if (source instanceof Blob) {
    await saveAudioBlobToStore(id, source);
    return { dataUrl: '' };
  }

  if (typeof source === 'string') {
    if (
      source.startsWith('http://') ||
      source.startsWith('https://') ||
      source.startsWith('file://') ||
      source.startsWith('/data/')
    ) {
      return { audioUri: source, dataUrl: '' };
    }

    if (source.startsWith('data:')) {
      try {
        const res = await fetch(source);
        const blob = await res.blob();
        await saveAudioBlobToStore(id, blob);
        return { dataUrl: '' };
      } catch {
        return { dataUrl: source };
      }
    }
  }

  return { dataUrl: '' };
}

/**
 * On-demand binary extractor for Export (ZIP / .likkho Diary Archive).
 * Loads binary bytes on-demand without keeping them in React state.
 */
export async function getAudioBinaryBytes(
  attachment: CanvasAudioAttachment
): Promise<Uint8Array | null> {
  // 1. Check IndexedDB store
  const storedBlob = await getAudioBlobFromStore(attachment.id);
  if (storedBlob) {
    const arrayBuffer = await storedBlob.arrayBuffer();
    return new Uint8Array(arrayBuffer);
  }

  // 2. Check native file path / URI
  if (attachment.audioUri) {
    try {
      const webUri = convertNativeFileToWebUri(attachment.audioUri);
      const res = await fetch(webUri);
      const arrayBuffer = await res.arrayBuffer();
      return new Uint8Array(arrayBuffer);
    } catch {
      // ignore
    }
  }

  // 3. Check dataUrl fallback
  if (attachment.dataUrl && attachment.dataUrl.startsWith('data:')) {
    try {
      const res = await fetch(attachment.dataUrl);
      const arrayBuffer = await res.arrayBuffer();
      return new Uint8Array(arrayBuffer);
    } catch {
      // ignore
    }
  }

  return null;
}

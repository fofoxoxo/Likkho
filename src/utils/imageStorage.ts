/**
 * High-Performance IndexedDB Binary Image Storage & Downscaling Engine
 * 
 * Solves Android WebView & JavaScript Runtime Out-Of-Memory (OOM) Errors:
 * 1. Images are stored purely as binary Blobs in IndexedDB (zero base64 strings in React state).
 * 2. Downscales heavy photos with `createImageBitmap` to 2048-4096px max dimension before storage.
 * 3. Output is produced with `canvas.toBlob()` (never `toDataURL()`).
 * 4. `DiaryLog` stores only lightweight image reference IDs (`img_blob_...`).
 * 5. Automatic migration of legacy dataURLs to IndexedDB Blob storage.
 * 6. Synchronous and async Object URL resolution with memory-leak cleanup.
 */

import { DiaryLog } from './cryptoVault';
import { saveAudioBlobToStore } from './audioStorage';

const IMAGE_DB_NAME = 'likkho_image_media_vault_v1';
const IMAGE_STORE_NAME = 'image_blobs';

// Map of currently active Object URLs: mediaRef -> blobUrl
const activeImageObjectUrls = new Map<string, string>();

let dbPromise: Promise<IDBDatabase> | null = null;

function getImageDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    try {
      const request = indexedDB.open(IMAGE_DB_NAME, 1);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(IMAGE_STORE_NAME)) {
          db.createObjectStore(IMAGE_STORE_NAME);
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error('Image IndexedDB blocked'));
    } catch (err) {
      reject(err);
    }
  });
  return dbPromise;
}

/**
 * Saves a binary image Blob into IndexedDB.
 */
export async function saveImageBlobToStore(id: string, blob: Blob): Promise<void> {
  try {
    const db = await getImageDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(IMAGE_STORE_NAME, 'readwrite');
      const store = tx.objectStore(IMAGE_STORE_NAME);
      store.put(blob, id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } catch (err) {
    console.error('Failed to save image blob to IndexedDB:', err);
  }
}

/**
 * Retrieves a binary image Blob from IndexedDB on-demand.
 */
export async function getImageBlobFromStore(id: string): Promise<Blob | null> {
  try {
    const db = await getImageDB();
    return await new Promise<Blob | null>((resolve, reject) => {
      const tx = db.transaction(IMAGE_STORE_NAME, 'readonly');
      const store = tx.objectStore(IMAGE_STORE_NAME);
      const req = store.get(id);
      req.onsuccess = () => resolve((req.result as Blob) || null);
      req.onerror = () => reject(req.error);
    });
  } catch (err) {
    console.error('Failed to get image blob from IndexedDB:', err);
    return null;
  }
}

/**
 * Deletes an image Blob from IndexedDB.
 */
export async function deleteImageBlobFromStore(id: string): Promise<void> {
  try {
    const existing = activeImageObjectUrls.get(id);
    if (existing) {
      try {
        URL.revokeObjectURL(existing);
      } catch {
        // ignore
      }
      activeImageObjectUrls.delete(id);
    }

    const db = await getImageDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(IMAGE_STORE_NAME, 'readwrite');
      const store = tx.objectStore(IMAGE_STORE_NAME);
      store.delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}

/**
 * Synchronous lookup for image URL.
 * Returns the active object URL if already cached, or the ref itself if it's already a URL/dataURL.
 */
export function getImageObjectUrl(mediaRef: string | null | undefined): string {
  if (!mediaRef) return '';
  if (
    mediaRef.startsWith('data:') ||
    mediaRef.startsWith('blob:') ||
    mediaRef.startsWith('http://') ||
    mediaRef.startsWith('https://') ||
    mediaRef.startsWith('/')
  ) {
    return mediaRef;
  }
  return activeImageObjectUrls.get(mediaRef) || '';
}

/**
 * Asynchronously ensures an Object URL exists for the given media reference ID.
 */
export async function ensureImageObjectUrl(mediaRef: string | null | undefined): Promise<string> {
  if (!mediaRef) return '';
  if (
    mediaRef.startsWith('data:') ||
    mediaRef.startsWith('blob:') ||
    mediaRef.startsWith('http://') ||
    mediaRef.startsWith('https://') ||
    mediaRef.startsWith('/')
  ) {
    return mediaRef;
  }

  const existing = activeImageObjectUrls.get(mediaRef);
  if (existing) return existing;

  const blob = await getImageBlobFromStore(mediaRef);
  if (blob) {
    const url = URL.createObjectURL(blob);
    activeImageObjectUrls.set(mediaRef, url);
    return url;
  }

  return '';
}

/**
 * Saves a Blob directly and returns its media ID reference along with an object URL.
 */
export async function saveImageBlobAndGetRef(
  blob: Blob,
  preferredId?: string
): Promise<{ id: string; objectUrl: string }> {
  const id = preferredId || `img_blob_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  await saveImageBlobToStore(id, blob);
  const objectUrl = URL.createObjectURL(blob);
  activeImageObjectUrls.set(id, objectUrl);
  return { id, objectUrl };
}

/**
 * Releases a specific cached Object URL to free RAM.
 */
export function releaseImageObjectUrl(id: string): void {
  const url = activeImageObjectUrls.get(id);
  if (url) {
    try {
      URL.revokeObjectURL(url);
    } catch {
      // ignore
    }
    activeImageObjectUrls.delete(id);
  }
}

/**
 * Revokes all image Object URLs across the app.
 */
export function releaseAllImageObjectUrls(): void {
  activeImageObjectUrls.forEach((url) => {
    try {
      URL.revokeObjectURL(url);
    } catch {
      // ignore
    }
  });
  activeImageObjectUrls.clear();
}

/**
 * Helper to convert a data URL string to a binary Blob
 */
export async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  const res = await fetch(dataUrl);
  return await res.blob();
}

/**
 * Loads a file or Blob with createImageBitmap (falling back to HTMLImageElement if necessary),
 * scales down the longest dimension to within 2048-4096px (default 2560px),
 * converts to a Blob with canvas.toBlob() (NOT toDataURL),
 * saves to IndexedDB, and returns the reference ID and dimensions.
 */
export async function processImageFileToBlob(
  file: Blob | File,
  maxDimension: number = 2560
): Promise<{ id: string; blob: Blob; width: number; height: number; objectUrl: string }> {
  let sourceWidth = 0;
  let sourceHeight = 0;
  let drawSource: CanvasImageSource | null = null;
  let bitmapToClose: ImageBitmap | null = null;

  try {
    if (typeof createImageBitmap === 'function') {
      const bmp = await createImageBitmap(file);
      bitmapToClose = bmp;
      drawSource = bmp;
      sourceWidth = bmp.width;
      sourceHeight = bmp.height;
    }
  } catch {
    drawSource = null;
  }

  // Fallback to HTMLImageElement if createImageBitmap fails
  if (!drawSource) {
    const tempUrl = URL.createObjectURL(file);
    try {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const el = new Image();
        el.crossOrigin = 'anonymous';
        el.onload = () => resolve(el);
        el.onerror = (e) => reject(e);
        el.src = tempUrl;
      });
      drawSource = img;
      sourceWidth = img.naturalWidth || img.width;
      sourceHeight = img.naturalHeight || img.height;
    } finally {
      URL.revokeObjectURL(tempUrl);
    }
  }

  // Calculate target dimensions keeping aspect ratio
  const maxSide = Math.max(sourceWidth, sourceHeight);
  let targetWidth = sourceWidth;
  let targetHeight = sourceHeight;

  if (maxSide > maxDimension && maxSide > 0) {
    const scale = maxDimension / maxSide;
    targetWidth = Math.max(1, Math.round(sourceWidth * scale));
    targetHeight = Math.max(1, Math.round(sourceHeight * scale));
  }

  const canvas = document.createElement('canvas');
  canvas.width = targetWidth;
  canvas.height = targetHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    if (bitmapToClose) bitmapToClose.close();
    throw new Error('Canvas 2D context not available');
  }

  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(drawSource, 0, 0, targetWidth, targetHeight);

  if (bitmapToClose) {
    bitmapToClose.close();
  }

  const isPng = file.type === 'image/png';
  const mimeType = isPng ? 'image/png' : 'image/jpeg';
  const quality = isPng ? undefined : 0.88;

  const finalBlob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error('Failed to create blob from image canvas'));
      },
      mimeType,
      quality
    );
  });

  const id = `img_blob_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  await saveImageBlobToStore(id, finalBlob);

  const objectUrl = URL.createObjectURL(finalBlob);
  activeImageObjectUrls.set(id, objectUrl);

  return {
    id,
    blob: finalBlob,
    width: targetWidth,
    height: targetHeight,
    objectUrl,
  };
}

/**
 * Preloads all media references in logs so their Object URLs are instantly ready.
 */
export async function preloadLogImages(logs: DiaryLog[]): Promise<void> {
  const idsToLoad = new Set<string>();

  for (const log of logs) {
    if (log.pfpDataUrl && !log.pfpDataUrl.startsWith('data:') && !log.pfpDataUrl.startsWith('blob:') && !log.pfpDataUrl.startsWith('http')) {
      idsToLoad.add(log.pfpDataUrl);
    }
    if (log.canvasBgDataUrl && !log.canvasBgDataUrl.startsWith('data:') && !log.canvasBgDataUrl.startsWith('blob:') && !log.canvasBgDataUrl.startsWith('http')) {
      idsToLoad.add(log.canvasBgDataUrl);
    }
    if (Array.isArray(log.canvasImages)) {
      for (const img of log.canvasImages) {
        if (img.dataUrl && !img.dataUrl.startsWith('data:') && !img.dataUrl.startsWith('blob:') && !img.dataUrl.startsWith('http')) {
          idsToLoad.add(img.dataUrl);
        }
      }
    }
  }

  await Promise.all(
    Array.from(idsToLoad).map(async (id) => {
      if (!activeImageObjectUrls.has(id)) {
        await ensureImageObjectUrl(id);
      }
    })
  );
}

/**
 * Purane logs me jo dataURL pehle se hain, unhe pehli baar load hone par Blob store me migrate kar do.
 * Migrates base64 data URLs in logs to IndexedDB Blobs and replaces them with reference IDs.
 */
export async function migrateLogsMediaToBlobStore(
  logs: DiaryLog[]
): Promise<{ logs: DiaryLog[]; migratedCount: number }> {
  let migratedCount = 0;
  const updatedLogs: DiaryLog[] = [];

  for (const log of logs) {
    let logModified = false;
    let nextPfp = log.pfpDataUrl;
    let nextBg = log.canvasBgDataUrl;
    let nextImages = log.canvasImages;
    let nextAudios = log.audioAttachments;

    // 1. Migrate PFP if data:image
    if (nextPfp && nextPfp.startsWith('data:image/')) {
      try {
        const blob = await dataUrlToBlob(nextPfp);
        const refId = `img_blob_pfp_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
        await saveImageBlobToStore(refId, blob);
        const objUrl = URL.createObjectURL(blob);
        activeImageObjectUrls.set(refId, objUrl);
        nextPfp = refId;
        logModified = true;
        migratedCount++;
      } catch (err) {
        console.error('Failed to migrate PFP dataUrl:', err);
      }
    }

    // 2. Migrate Canvas Background if data:image
    if (nextBg && nextBg.startsWith('data:image/')) {
      try {
        const blob = await dataUrlToBlob(nextBg);
        const refId = `img_blob_bg_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
        await saveImageBlobToStore(refId, blob);
        const objUrl = URL.createObjectURL(blob);
        activeImageObjectUrls.set(refId, objUrl);
        nextBg = refId;
        logModified = true;
        migratedCount++;
      } catch (err) {
        console.error('Failed to migrate canvasBg dataUrl:', err);
      }
    }

    // 3. Migrate Canvas Images if data:image
    if (Array.isArray(nextImages) && nextImages.some((img) => img.dataUrl && img.dataUrl.startsWith('data:image/'))) {
      const migratedImages = await Promise.all(
        nextImages.map(async (img) => {
          if (img.dataUrl && img.dataUrl.startsWith('data:image/')) {
            try {
              const blob = await dataUrlToBlob(img.dataUrl);
              const refId = `img_blob_cnv_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
              await saveImageBlobToStore(refId, blob);
              const objUrl = URL.createObjectURL(blob);
              activeImageObjectUrls.set(refId, objUrl);
              migratedCount++;
              logModified = true;
              return { ...img, dataUrl: refId };
            } catch {
              return img;
            }
          }
          return img;
        })
      );
      nextImages = migratedImages;
    }

    // 4. Migrate Audio attachments if data:audio
    if (Array.isArray(nextAudios) && nextAudios.some((aud) => aud.dataUrl && aud.dataUrl.startsWith('data:audio/'))) {
      const migratedAudios = await Promise.all(
        nextAudios.map(async (aud) => {
          if (aud.dataUrl && aud.dataUrl.startsWith('data:audio/')) {
            try {
              const blob = await dataUrlToBlob(aud.dataUrl);
              await saveAudioBlobToStore(aud.id, blob);
              migratedCount++;
              logModified = true;
              return { ...aud, dataUrl: '' };
            } catch {
              return aud;
            }
          }
          return aud;
        })
      );
      nextAudios = migratedAudios;
    }

    if (logModified) {
      updatedLogs.push({
        ...log,
        pfpDataUrl: nextPfp,
        canvasBgDataUrl: nextBg,
        canvasImages: nextImages,
        audioAttachments: nextAudios,
      });
    } else {
      updatedLogs.push(log);
    }
  }

  // Preload any non-migrated reference IDs
  await preloadLogImages(updatedLogs);

  return { logs: updatedLogs, migratedCount };
}

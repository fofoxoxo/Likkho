import {
  CanvasAudioAttachment,
  CanvasDraggableImage,
  DiaryLog,
  markExternalFilePickerPending,
} from './cryptoVault';
import { buildZipArchiveBlob, ZipEntryFile } from './zipDataExporter';

export const LIKKHO_GITHUB_DOWNLOAD_URL = 'https://github.com/Likkho/Likkho/releases';

export const LIKKHO_SHARE_MESSAGE_TEMPLATE = (heading: string, githubUrl: string) =>
  `📖 Shared a diary entry "${heading}" from Likkho (लिक्खो).\n\nOpen the attached .likkho file in the Likkho app to read the text, images, and audio recordings in their exact order.\n\nDownload Likkho APK from GitHub:\n${githubUrl}`;

interface LikkhoArchiveManifest {
  magic: 'LIKKHO_SHARED_DIARY_ARCHIVE_V1';
  formatVersion: 1;
  sharedAt: number;
  isLocked: boolean;
  diary: {
    id: string;
    heading: string;
    contentHtml: string;
    plainPreview: string;
    pfpPath: string | null;
    pfpDataUrlFallback: string | null;
    createdAt: number;
    updatedAt: number;
    dateStamp: string;
    timeStamp: string;
    updatedDateStamp?: string;
    updatedTimeStamp?: string;
    pinned?: boolean;
    diaryLockPin?: string | null;
    canvasBgPath: string | null;
    canvasBgDataUrlFallback: string | null;
    canvasBgOpacity?: number;
    inlineImages: {
      placeholderId: string;
      archivePath: string;
      mimeType: string;
      alt: string;
    }[];
    canvasImages: {
      id: string;
      archivePath: string;
      mimeType: string;
      dataUrlFallback?: string;
      x: number;
      y: number;
      width: number;
      height: number;
      opacity: number;
      rotation?: number;
      layer?: 'foreground' | 'background';
    }[];
    audioAttachments: {
      id: string;
      name: string;
      format: string;
      archivePath: string;
      mimeType: string;
      dataUrlFallback?: string;
      durationSec?: number;
      createdAt: number;
      x?: number;
      y?: number;
      width?: number;
      height?: number;
      rotation?: number;
      playbackRate?: number;
      layer?: 'foreground' | 'background';
    }[];
  };
}

function sanitizeArchiveSlug(input: string, fallback: string): string {
  const cleaned = (input || '')
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '_')
    .replace(/\s+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 48);
  return cleaned || fallback;
}

function parseDataUrlToBytesAndMeta(
  dataUrl: string,
  fallbackExt: string,
  fallbackMime: string
): { bytes: Uint8Array; ext: string; mimeType: string } | null {
  try {
    if (!dataUrl || !dataUrl.startsWith('data:')) return null;
    const commaIdx = dataUrl.indexOf(',');
    if (commaIdx < 0) return null;
    const header = dataUrl.slice(5, commaIdx);
    const body = dataUrl.slice(commaIdx + 1);
    const isBase64 = header.includes(';base64');
    const mimeType = (header.split(';')[0] || fallbackMime).toLowerCase();

    let ext = fallbackExt.replace(/^\.+/, '').toLowerCase();
    if (mimeType.includes('png')) ext = 'png';
    else if (mimeType.includes('jpeg') || mimeType.includes('jpg')) ext = 'jpg';
    else if (mimeType.includes('webp')) ext = 'webp';
    else if (mimeType.includes('gif')) ext = 'gif';
    else if (mimeType.includes('svg')) ext = 'svg';
    else if (mimeType.includes('wav')) ext = 'wav';
    else if (mimeType.includes('flac')) ext = 'flac';
    else if (mimeType.includes('mpeg') || mimeType.includes('mp3')) ext = 'mp3';
    else if (mimeType.includes('mp4') || mimeType.includes('m4a')) ext = 'm4a';
    else if (mimeType.includes('aac')) ext = 'aac';
    else if (mimeType.includes('ogg')) ext = 'ogg';
    else if (mimeType.includes('webm')) ext = 'webm';

    if (isBase64) {
      const bin = atob(body);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) {
        bytes[i] = bin.charCodeAt(i);
      }
      return { bytes, ext, mimeType };
    } else {
      const decoded = decodeURIComponent(body);
      return {
        bytes: new TextEncoder().encode(decoded),
        ext,
        mimeType,
      };
    }
  } catch {
    return null;
  }
}

function bytesToDataUrl(bytes: Uint8Array, mimeType: string): string {
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode.apply(
      null,
      Array.from(bytes.subarray(i, i + chunkSize))
    );
  }
  return `data:${mimeType};base64,${btoa(binary)}`;
}

function convertHtmlToReadableMarkdown(log: DiaryLog): string {
  let md = `# ${log.heading || 'Untitled Diary'}\n\n`;
  md += `- **Created:** ${log.dateStamp} · ${log.timeStamp}\n`;
  if (log.updatedDateStamp && log.updatedTimeStamp) {
    md += `- **Modified:** ${log.updatedDateStamp} · ${log.updatedTimeStamp}\n`;
  }
  if (log.diaryLockPin) {
    md += `- **Locked Diary:** Yes (Passcode Protected)\n`;
  }
  md += `\n---\n\n`;

  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = log.contentHtml || '';

  tempDiv.querySelectorAll('span[data-wiki-latex="true"]').forEach((latEl) => {
    const src = latEl.getAttribute('data-latex-src') || latEl.textContent || '';
    const mdNode = document.createTextNode(`$${src}$`);
    latEl.parentNode?.replaceChild(mdNode, latEl);
  });

  const htmlBody = tempDiv.innerHTML
    .replace(/<h1[^>]*>(.*?)<\/h1>/gi, '\n# $1\n\n')
    .replace(/<h2[^>]*>(.*?)<\/h2>/gi, '\n## $1\n\n')
    .replace(/<h3[^>]*>(.*?)<\/h3>/gi, '\n### $1\n\n')
    .replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**')
    .replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**')
    .replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*')
    .replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*')
    .replace(/<u[^>]*>(.*?)<\/u>/gi, '_$1_')
    .replace(/<strike[^>]*>(.*?)<\/strike>/gi, '~~$1~~')
    .replace(/<s[^>]*>(.*?)<\/s>/gi, '~~$1~~')
    .replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
    .replace(/<blockquote[^>]*>(.*?)<\/blockquote>/gi, '\n> $1\n\n')
    .replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
    .replace(/<hr\s*\/?>/gi, '\n---\n\n')
    .replace(/<\/p>/gi, '\n\n')
    .replace(/<br\s*\/?>/gi, '\n');

  const textExtractor = document.createElement('div');
  textExtractor.innerHTML = htmlBody;
  const cleanedBody = (textExtractor.textContent || textExtractor.innerText || '')
    .replace(/\u200B/g, '')
    .trim();

  md += `${cleanedBody}\n`;
  return md;
}

async function compressDataPipe(bytes: Uint8Array): Promise<Uint8Array | null> {
  try {
    if (typeof CompressionStream === 'undefined') return null;
    const cs = new CompressionStream('gzip');
    const writer = cs.writable.getWriter();
    writer.write(bytes).catch(() => {});
    writer.close().catch(() => {});
    const compressedBuffer = await new Response(cs.readable).arrayBuffer();
    return new Uint8Array(compressedBuffer);
  } catch {
    return null;
  }
}

async function decompressDataPipe(bytes: Uint8Array): Promise<Uint8Array | null> {
  try {
    if (typeof DecompressionStream === 'undefined') return null;
    const ds = new DecompressionStream('gzip');
    const writer = ds.writable.getWriter();
    writer.write(bytes).catch(() => {});
    writer.close().catch(() => {});
    const decompressedBuffer = await new Response(ds.readable).arrayBuffer();
    return new Uint8Array(decompressedBuffer);
  } catch {
    return null;
  }
}

/**
 * Packs a single DiaryLog (text, markdown formatting, inline & floating images,
 * canvas background, PFP, audio recordings, and locked diary passcode state)
 * into a single compressed `.likkho` archive Blob.
 */
export async function packDiaryToLikkhoBlob(
  log: DiaryLog
): Promise<{ blob: Blob; filename: string }> {
  const zipEntries: ZipEntryFile[] = [];
  const enc = new TextEncoder();

  // 1. Extract Profile Picture (PFP)
  let pfpPath: string | null = null;
  if (log.pfpDataUrl) {
    const parsedPfp = parseDataUrlToBytesAndMeta(
      log.pfpDataUrl,
      'png',
      'image/png'
    );
    if (parsedPfp) {
      pfpPath = `images/pfp.${parsedPfp.ext}`;
      zipEntries.push({ path: pfpPath, data: parsedPfp.bytes });
    }
  }

  // 2. Extract Canvas Background Image
  let canvasBgPath: string | null = null;
  if (log.canvasBgDataUrl) {
    const parsedBg = parseDataUrlToBytesAndMeta(
      log.canvasBgDataUrl,
      'png',
      'image/png'
    );
    if (parsedBg) {
      canvasBgPath = `images/canvas_bg.${parsedBg.ext}`;
      zipEntries.push({ path: canvasBgPath, data: parsedBg.bytes });
    }
  }

  // 3. Extract Inline Images inside `contentHtml` while preserving exact DOM order
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = log.contentHtml || '';
  const inlineImagesMeta: LikkhoArchiveManifest['diary']['inlineImages'] = [];
  const inlineImgElements = Array.from(tempDiv.querySelectorAll('img'));

  inlineImgElements.forEach((imgEl, index) => {
    const src = imgEl.getAttribute('src') || '';
    const alt = imgEl.getAttribute('alt') || `inline_${index + 1}`;
    const parsedInline = parseDataUrlToBytesAndMeta(src, 'png', 'image/png');
    if (parsedInline) {
      const placeholderId = `__LIKKHO_INLINE_IMG_${index + 1}__`;
      const archivePath = `images/inline_${index + 1}.${parsedInline.ext}`;
      zipEntries.push({ path: archivePath, data: parsedInline.bytes });
      inlineImagesMeta.push({
        placeholderId,
        archivePath,
        mimeType: parsedInline.mimeType,
        alt,
      });
      imgEl.setAttribute('data-likkho-inline-id', placeholderId);
      imgEl.removeAttribute('src');
    }
  });

  const tokenizedHtml = tempDiv.innerHTML;

  // 4. Extract Floating / Layered Canvas Images in exact order
  const canvasImagesMeta: LikkhoArchiveManifest['diary']['canvasImages'] = [];
  (log.canvasImages || []).forEach((img, idx) => {
    const parsed = parseDataUrlToBytesAndMeta(
      img.dataUrl,
      'png',
      'image/png'
    );
    if (parsed) {
      const archivePath = `images/canvas_img_${idx + 1}.${parsed.ext}`;
      zipEntries.push({ path: archivePath, data: parsed.bytes });
      canvasImagesMeta.push({
        id: img.id,
        archivePath,
        mimeType: parsed.mimeType,
        x: img.x,
        y: img.y,
        width: img.width,
        height: img.height,
        opacity: img.opacity,
        rotation: img.rotation,
        layer: img.layer,
      });
    } else {
      canvasImagesMeta.push({
        id: img.id,
        archivePath: '',
        mimeType: 'image/png',
        dataUrlFallback: img.dataUrl,
        x: img.x,
        y: img.y,
        width: img.width,
        height: img.height,
        opacity: img.opacity,
        rotation: img.rotation,
        layer: img.layer,
      });
    }
  });

  // 5. Extract Audio Recordings / Attachments in exact order and format
  const audioMeta: LikkhoArchiveManifest['diary']['audioAttachments'] = [];
  (log.audioAttachments || []).forEach((aud, idx) => {
    const preferredExt = (aud.format || 'wav').replace(/^\.+/, '').toLowerCase();
    const parsedAud = parseDataUrlToBytesAndMeta(
      aud.dataUrl,
      preferredExt,
      `audio/${preferredExt}`
    );
    if (parsedAud) {
      const finalExt = preferredExt || parsedAud.ext;
      const archivePath = `audio/audio_${idx + 1}.${finalExt}`;
      zipEntries.push({ path: archivePath, data: parsedAud.bytes });
      audioMeta.push({
        id: aud.id,
        name: aud.name,
        format: aud.format,
        archivePath,
        mimeType: parsedAud.mimeType,
        durationSec: aud.durationSec,
        createdAt: aud.createdAt,
        x: aud.x,
        y: aud.y,
        width: aud.width,
        height: aud.height,
        rotation: aud.rotation,
        playbackRate: aud.playbackRate,
        layer: aud.layer,
      });
    } else {
      audioMeta.push({
        id: aud.id,
        name: aud.name,
        format: aud.format,
        archivePath: '',
        mimeType: 'audio/wav',
        dataUrlFallback: aud.dataUrl,
        durationSec: aud.durationSec,
        createdAt: aud.createdAt,
        x: aud.x,
        y: aud.y,
        width: aud.width,
        height: aud.height,
        rotation: aud.rotation,
        playbackRate: aud.playbackRate,
        layer: aud.layer,
      });
    }
  });

  // 6. Include Markdown representation (`diary.md`) inside archive
  const markdownText = convertHtmlToReadableMarkdown(log);
  zipEntries.push({
    path: 'diary.md',
    data: enc.encode(markdownText),
  });

  // 7. Create `manifest.json` (and gzip-compressed `manifest.json.gz` if supported)
  const manifest: LikkhoArchiveManifest = {
    magic: 'LIKKHO_SHARED_DIARY_ARCHIVE_V1',
    formatVersion: 1,
    sharedAt: Date.now(),
    isLocked: Boolean(log.diaryLockPin),
    diary: {
      id: log.id,
      heading: log.heading,
      contentHtml: tokenizedHtml,
      plainPreview: log.plainPreview,
      pfpPath,
      pfpDataUrlFallback: pfpPath ? null : log.pfpDataUrl,
      createdAt: log.createdAt,
      updatedAt: log.updatedAt,
      dateStamp: log.dateStamp,
      timeStamp: log.timeStamp,
      updatedDateStamp: log.updatedDateStamp,
      updatedTimeStamp: log.updatedTimeStamp,
      pinned: log.pinned,
      diaryLockPin: log.diaryLockPin || null,
      canvasBgPath,
      canvasBgDataUrlFallback: canvasBgPath ? null : log.canvasBgDataUrl || null,
      canvasBgOpacity: log.canvasBgOpacity ?? 0.25,
      inlineImages: inlineImagesMeta,
      canvasImages: canvasImagesMeta,
      audioAttachments: audioMeta,
    },
  };

  const manifestBytes = enc.encode(JSON.stringify(manifest));
  const gzManifest = await compressDataPipe(manifestBytes);
  if (gzManifest) {
    zipEntries.push({
      path: 'manifest.json.gz',
      data: gzManifest,
    });
  }
  zipEntries.push({
    path: 'manifest.json',
    data: manifestBytes,
  });

  const zipBlob = buildZipArchiveBlob(zipEntries);
  const likkhoBlob = new Blob([zipBlob], {
    type: 'application/x-likkho',
  });

  const slug = sanitizeArchiveSlug(log.heading, 'Likkho_Diary');
  const filename = `${slug}.likkho`;

  return { blob: likkhoBlob, filename };
}

/**
 * Unpacks a `.likkho` archive (ArrayBuffer / Uint8Array) back into a complete `DiaryLog`
 * with all text, markdown/HTML formatting, inline & floating images, audio players,
 * and locked diary passcode state intact in their exact order.
 */
export async function unpackLikkhoArchiveBuffer(
  buffer: ArrayBuffer
): Promise<DiaryLog> {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const decoder = new TextDecoder('utf-8');
  const fileMap = new Map<string, Uint8Array>();

  // Check if the buffer is a PKZIP container (starts with 0x04034b50 'PK\x03\x04')
  if (bytes.length >= 4 && view.getUint32(0, true) === 0x04034b50) {
    let offset = 0;
    while (offset + 30 <= bytes.length) {
      const sig = view.getUint32(offset, true);
      if (sig !== 0x04034b50) break;

      const compressionMethod = view.getUint16(offset + 8, true);
      const compressedSize = view.getUint32(offset + 18, true);
      const fileNameLen = view.getUint16(offset + 26, true);
      const extraLen = view.getUint16(offset + 28, true);

      const nameBytes = bytes.subarray(offset + 30, offset + 30 + fileNameLen);
      const fileName = decoder.decode(nameBytes);
      const dataStart = offset + 30 + fileNameLen + extraLen;
      const dataEnd = dataStart + compressedSize;
      if (dataEnd > bytes.length) break;

      const rawSlice = bytes.subarray(dataStart, dataEnd);
      if (compressionMethod === 0) {
        fileMap.set(fileName, rawSlice);
      } else if (
        compressionMethod === 8 &&
        typeof DecompressionStream !== 'undefined'
      ) {
        try {
          const ds = new DecompressionStream('deflate-raw');
          const writer = ds.writable.getWriter();
          writer.write(rawSlice).catch(() => {});
          writer.close().catch(() => {});
          const decompressedBuf = await new Response(ds.readable).arrayBuffer();
          fileMap.set(fileName, new Uint8Array(decompressedBuf));
        } catch {
          // ignore
        }
      }

      offset = dataEnd;
    }
  } else {
    // Fallback if plain JSON or gzip JSON was passed directly
    const decompressed = await decompressDataPipe(bytes);
    const jsonText = decoder.decode(decompressed || bytes);
    fileMap.set('manifest.json', new TextEncoder().encode(jsonText));
  }

  let manifestJsonStr = '';
  if (fileMap.has('manifest.json')) {
    manifestJsonStr = decoder.decode(fileMap.get('manifest.json')!);
  } else if (fileMap.has('manifest.json.gz')) {
    const decompressed = await decompressDataPipe(fileMap.get('manifest.json.gz')!);
    if (decompressed) {
      manifestJsonStr = decoder.decode(decompressed);
    }
  }

  if (!manifestJsonStr) {
    throw new Error('Invalid or corrupted .likkho file.');
  }

  const manifest = JSON.parse(manifestJsonStr) as LikkhoArchiveManifest;
  if (
    !manifest ||
    manifest.magic !== 'LIKKHO_SHARED_DIARY_ARCHIVE_V1' ||
    !manifest.diary
  ) {
    throw new Error('Unrecognized .likkho diary archive.');
  }

  const d = manifest.diary;

  // 1. Reconstruct PFP
  let pfpDataUrl: string | null = d.pfpDataUrlFallback || null;
  if (d.pfpPath && fileMap.has(d.pfpPath)) {
    const ext = d.pfpPath.split('.').pop()?.toLowerCase() || 'png';
    const mime =
      ext === 'svg'
        ? 'image/svg+xml'
        : ext === 'jpg' || ext === 'jpeg'
        ? 'image/jpeg'
        : ext === 'webp'
        ? 'image/webp'
        : 'image/png';
    pfpDataUrl = bytesToDataUrl(fileMap.get(d.pfpPath)!, mime);
  }

  // 2. Reconstruct Canvas Background Image
  let canvasBgDataUrl: string | null = d.canvasBgDataUrlFallback || null;
  if (d.canvasBgPath && fileMap.has(d.canvasBgPath)) {
    const ext = d.canvasBgPath.split('.').pop()?.toLowerCase() || 'png';
    const mime =
      ext === 'jpg' || ext === 'jpeg'
        ? 'image/jpeg'
        : ext === 'webp'
        ? 'image/webp'
        : 'image/png';
    canvasBgDataUrl = bytesToDataUrl(fileMap.get(d.canvasBgPath)!, mime);
  }

  // 3. Reconstruct Inline Images inside `contentHtml` in exact order
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = d.contentHtml || '';
  (d.inlineImages || []).forEach((inl) => {
    const imgEl = tempDiv.querySelector(
      `img[data-likkho-inline-id="${inl.placeholderId}"]`
    );
    if (imgEl && inl.archivePath && fileMap.has(inl.archivePath)) {
      const dataUrl = bytesToDataUrl(
        fileMap.get(inl.archivePath)!,
        inl.mimeType || 'image/png'
      );
      imgEl.setAttribute('src', dataUrl);
      imgEl.removeAttribute('data-likkho-inline-id');
    }
  });
  const restoredContentHtml = tempDiv.innerHTML;

  // 4. Reconstruct Floating / Layered Canvas Images in exact order
  const canvasImages: CanvasDraggableImage[] = [];
  (d.canvasImages || []).forEach((cImg) => {
    let dataUrl = cImg.dataUrlFallback || '';
    if (cImg.archivePath && fileMap.has(cImg.archivePath)) {
      dataUrl = bytesToDataUrl(
        fileMap.get(cImg.archivePath)!,
        cImg.mimeType || 'image/png'
      );
    }
    if (dataUrl) {
      canvasImages.push({
        id: cImg.id,
        dataUrl,
        x: cImg.x,
        y: cImg.y,
        width: cImg.width,
        height: cImg.height,
        opacity: cImg.opacity,
        rotation: cImg.rotation,
        layer: cImg.layer,
      });
    }
  });

  // 5. Reconstruct Audio Recordings / Player Cards in exact order
  const audioAttachments: CanvasAudioAttachment[] = [];
  (d.audioAttachments || []).forEach((aud) => {
    let dataUrl = aud.dataUrlFallback || '';
    if (aud.archivePath && fileMap.has(aud.archivePath)) {
      dataUrl = bytesToDataUrl(
        fileMap.get(aud.archivePath)!,
        aud.mimeType || 'audio/wav'
      );
    }
    if (dataUrl) {
      audioAttachments.push({
        id: aud.id,
        name: aud.name,
        format: aud.format,
        dataUrl,
        durationSec: aud.durationSec,
        createdAt: aud.createdAt,
        x: aud.x,
        y: aud.y,
        width: aud.width,
        height: aud.height,
        rotation: aud.rotation,
        playbackRate: aud.playbackRate,
        layer: aud.layer,
      });
    }
  });

  return {
    id: `log_shared_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    heading: d.heading || 'Shared Diary',
    contentHtml: restoredContentHtml,
    plainPreview: d.plainPreview || '',
    pfpDataUrl,
    createdAt: d.createdAt || Date.now(),
    updatedAt: d.updatedAt || Date.now(),
    dateStamp: d.dateStamp || '',
    timeStamp: d.timeStamp || '',
    updatedDateStamp: d.updatedDateStamp,
    updatedTimeStamp: d.updatedTimeStamp,
    reminderAt: null,
    pinned: false,
    // Preserve locked diary passcode so if sender shared a locked diary, receiver receives it locked!
    diaryLockPin: d.diaryLockPin || null,
    canvasBgDataUrl,
    canvasBgOpacity: d.canvasBgOpacity ?? 0.25,
    canvasImages,
    audioAttachments,
  };
}

export async function unpackLikkhoBase64Payload(base64Data: string): Promise<DiaryLog> {
  const cleanB64 = base64Data.includes(',')
    ? base64Data.slice(base64Data.indexOf(',') + 1)
    : base64Data;
  const bin = atob(cleanB64.trim());
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return unpackLikkhoArchiveBuffer(bytes.buffer);
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      if (typeof reader.result === 'string') {
        const idx = reader.result.indexOf(',');
        resolve(idx >= 0 ? reader.result.slice(idx + 1) : reader.result);
      } else {
        reject(new Error('Failed to encode .likkho archive'));
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

/**
 * Shares a DiaryLog as a `.likkho` compressed archive file with an auto-attached
 * promotional message containing the GitHub download link for Likkho.
 */
export async function shareDiaryAsLikkhoArchive(log: DiaryLog): Promise<string> {
  const { blob, filename } = await packDiaryToLikkhoBlob(log);
  const shareMessage = LIKKHO_SHARE_MESSAGE_TEMPLATE(
    log.heading || 'Diary Entry',
    LIKKHO_GITHUB_DOWNLOAD_URL
  );

  // 1. Native Android Share Sheet (ACTION_SEND via FileProvider with .likkho attachment + GitHub link message)
  if (
    window.LikkhoNative &&
    typeof window.LikkhoNative.shareLikkhoDiaryArchive === 'function'
  ) {
    markExternalFilePickerPending();
    const base64Data = await blobToBase64(blob);
    window.LikkhoNative.shareLikkhoDiaryArchive(base64Data, filename, shareMessage);
    return `Sharing "${filename}"...`;
  }

  // 2. Web Share API Level 2 (if running in a mobile browser that supports sharing files)
  try {
    const file = new File([blob], filename, {
      type: 'application/x-likkho',
    });
    if (
      navigator.share &&
      navigator.canShare &&
      navigator.canShare({ files: [file] })
    ) {
      markExternalFilePickerPending();
      await navigator.share({
        title: `${log.heading} — Likkho Diary`,
        text: shareMessage,
        files: [file],
      });
      return `Shared "${filename}"`;
    }
  } catch {
    // Fallback to downloading .likkho archive + copying message
  }

  // 3. Desktop / WebView fallback: Download the `.likkho` file and copy share message
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(shareMessage);
    }
  } catch {
    // ignore
  }

  if (
    window.LikkhoNative &&
    typeof window.LikkhoNative.saveExportedFile === 'function'
  ) {
    const base64Data = await blobToBase64(blob);
    window.LikkhoNative.saveExportedFile(
      base64Data,
      filename,
      'application/x-likkho'
    );
    return `Saved "${filename}" to Downloads/Likkho (Share message copied)`;
  }

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 3000);
  return `Downloaded "${filename}" (Share message with GitHub link copied)`;
}

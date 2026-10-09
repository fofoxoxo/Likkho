import { CustomFontItem, DiaryLog, VaultMode } from './cryptoVault';
import { getSavedWordAnalyses } from './wordAnalysisEngine';

export interface ZipEntryFile {
  path: string;
  data: Uint8Array;
}

const CRC32_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC32_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

/**
 * Builds a standard PKZIP (.zip) binary archive (Store method = 0)
 * compatible with all Android, Windows, macOS, iOS, and Linux archive extractors.
 */
export function buildZipArchiveBlob(entries: ZipEntryFile[]): Blob {
  const enc = new TextEncoder();
  const localChunks: Uint8Array[] = [];
  const centralChunks: Uint8Array[] = [];
  let localOffset = 0;

  const now = new Date();
  const dosTime =
    ((now.getHours() & 0x1f) << 11) |
    ((now.getMinutes() & 0x3f) << 5) |
    ((now.getSeconds() >> 1) & 0x1f);
  const dosDate =
    (((now.getFullYear() - 1980) & 0x7f) << 9) |
    (((now.getMonth() + 1) & 0x0f) << 5) |
    (now.getDate() & 0x1f);

  for (const entry of entries) {
    const nameBytes = enc.encode(entry.path.replace(/^\/+/, ''));
    const dataBytes = entry.data;
    const crc = crc32(dataBytes);
    const size = dataBytes.length;

    // Local File Header (30 bytes + filename)
    const localHeader = new Uint8Array(30 + nameBytes.length);
    const lv = new DataView(localHeader.buffer);
    lv.setUint32(0, 0x04034b50, true); // Local file header signature
    lv.setUint16(4, 20, true); // Version needed to extract (2.0)
    lv.setUint16(6, 0x0800, true); // General purpose bit flag (UTF-8 filename)
    lv.setUint16(8, 0, true); // Compression method (0 = Store)
    lv.setUint16(10, dosTime, true);
    lv.setUint16(12, dosDate, true);
    lv.setUint32(14, crc, true);
    lv.setUint32(18, size, true); // Compressed size
    lv.setUint32(22, size, true); // Uncompressed size
    lv.setUint16(26, nameBytes.length, true);
    lv.setUint16(28, 0, true); // Extra field length
    localHeader.set(nameBytes, 30);

    localChunks.push(localHeader);
    localChunks.push(dataBytes);

    // Central Directory File Header (46 bytes + filename)
    const centralHeader = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(centralHeader.buffer);
    cv.setUint32(0, 0x02014b50, true); // Central file header signature
    cv.setUint16(4, 20, true); // Version made by
    cv.setUint16(6, 20, true); // Version needed to extract
    cv.setUint16(8, 0x0800, true); // UTF-8 flag
    cv.setUint16(10, 0, true); // Store method
    cv.setUint16(12, dosTime, true);
    cv.setUint16(14, dosDate, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, size, true);
    cv.setUint32(24, size, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint16(30, 0, true); // Extra field length
    cv.setUint16(32, 0, true); // File comment length
    cv.setUint16(34, 0, true); // Disk number start
    cv.setUint16(36, 0, true); // Internal file attributes
    cv.setUint32(38, 0, true); // External file attributes
    cv.setUint32(42, localOffset, true); // Relative offset of local header
    centralHeader.set(nameBytes, 46);

    centralChunks.push(centralHeader);
    localOffset += localHeader.length + dataBytes.length;
  }

  let centralSize = 0;
  for (const ch of centralChunks) {
    centralSize += ch.length;
  }

  // End of Central Directory Record (22 bytes)
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true); // EOCD signature
  ev.setUint16(4, 0, true); // Number of this disk
  ev.setUint16(6, 0, true); // Disk where central directory starts
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, centralSize, true);
  ev.setUint32(16, localOffset, true);
  ev.setUint16(20, 0, true); // Comment length

  return new Blob([...localChunks, ...centralChunks, eocd], {
    type: 'application/zip',
  });
}

function parseDataUrlToBytesAndExt(
  dataUrl: string,
  fallbackExt: string
): { bytes: Uint8Array; ext: string } | null {
  try {
    if (!dataUrl || !dataUrl.startsWith('data:')) return null;
    const commaIdx = dataUrl.indexOf(',');
    if (commaIdx < 0) return null;
    const header = dataUrl.slice(5, commaIdx);
    const body = dataUrl.slice(commaIdx + 1);
    const isBase64 = header.includes(';base64');
    const mime = header.split(';')[0].toLowerCase();

    let ext = fallbackExt.replace(/^\.+/, '').toLowerCase();
    if (mime.includes('png')) ext = 'png';
    else if (mime.includes('jpeg') || mime.includes('jpg')) ext = 'jpg';
    else if (mime.includes('webp')) ext = 'webp';
    else if (mime.includes('gif')) ext = 'gif';
    else if (mime.includes('svg')) ext = 'svg';
    else if (mime.includes('wav')) ext = 'wav';
    else if (mime.includes('flac')) ext = 'flac';
    else if (mime.includes('mpeg') || mime.includes('mp3')) ext = 'mp3';
    else if (mime.includes('mp4') || mime.includes('m4a')) ext = 'm4a';
    else if (mime.includes('aac')) ext = 'aac';
    else if (mime.includes('ogg')) ext = 'ogg';
    else if (mime.includes('webm')) ext = 'webm';
    else if (mime.includes('ttf') || mime.includes('font')) ext = 'ttf';

    if (isBase64) {
      const bin = atob(body);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) {
        bytes[i] = bin.charCodeAt(i);
      }
      return { bytes, ext };
    } else {
      const decoded = decodeURIComponent(body);
      return { bytes: new TextEncoder().encode(decoded), ext };
    }
  } catch {
    return null;
  }
}

function sanitizeFileSlug(input: string, fallback: string): string {
  const cleaned = (input || '')
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/gi, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 42);
  return cleaned || fallback;
}

function convertHtmlToMarkdownWithExtractedMedia(
  log: DiaryLog,
  entrySlug: string,
  zipFiles: ZipEntryFile[]
): string {
  const enc = new TextEncoder();
  let md = `# ${log.heading || 'Untitled Entry'}\n\n`;
  md += `- **Created:** ${log.dateStamp} · ${log.timeStamp}\n`;
  if (log.updatedDateStamp && log.updatedTimeStamp) {
    md += `- **Modified:** ${log.updatedDateStamp} · ${log.updatedTimeStamp}\n`;
  }
  if (log.pinned) {
    md += `- **Pinned:** Yes\n`;
  }
  md += `\n---\n\n`;

  // Extract PFP if present
  if (log.pfpDataUrl) {
    const parsedPfp = parseDataUrlToBytesAndExt(log.pfpDataUrl, 'png');
    if (parsedPfp) {
      const pfpPath = `images/${entrySlug}_pfp.${parsedPfp.ext}`;
      zipFiles.push({ path: pfpPath, data: parsedPfp.bytes });
      md += `![Entry Profile Picture](../${pfpPath})\n\n`;
    }
  }

  // Extract Canvas Background Image if present
  if (log.canvasBgDataUrl) {
    const parsedBg = parseDataUrlToBytesAndExt(log.canvasBgDataUrl, 'png');
    if (parsedBg) {
      const bgPath = `images/${entrySlug}_canvas_bg.${parsedBg.ext}`;
      zipFiles.push({ path: bgPath, data: parsedBg.bytes });
      md += `> **Canvas Background Image:** [../${bgPath}](../${bgPath}) (Opacity: ${Math.round(
        (log.canvasBgOpacity ?? 0.25) * 100
      )}%)\n\n`;
    }
  }

  // Parse HTML and extract any inline <img> tags (such as Wikipedia inline images)
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = log.contentHtml || '';
  const inlineImgs = Array.from(tempDiv.querySelectorAll('img'));
  inlineImgs.forEach((imgEl, idx) => {
    const src = imgEl.getAttribute('src') || '';
    const alt = imgEl.getAttribute('alt') || `inline_image_${idx + 1}`;
    const parsedInline = parseDataUrlToBytesAndExt(src, 'png');
    if (parsedInline) {
      const inlinePath = `images/${entrySlug}_inline_${idx + 1}.${parsedInline.ext}`;
      zipFiles.push({ path: inlinePath, data: parsedInline.bytes });
      const mdReplacement = document.createTextNode(`\n\n![${alt}](../${inlinePath})\n\n`);
      imgEl.parentNode?.replaceChild(mdReplacement, imgEl);
    } else if (src) {
      const mdReplacement = document.createTextNode(`\n\n![${alt}](${src})\n\n`);
      imgEl.parentNode?.replaceChild(mdReplacement, imgEl);
    }
  });

  // Convert LaTeX spans to `$...$` Markdown math syntax
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

  // Extract Floating Canvas Images in their exact image format
  const canvasImages = log.canvasImages || [];
  if (canvasImages.length > 0) {
    md += `\n\n## Attached Canvas Images\n\n`;
    canvasImages.forEach((cImg, idx) => {
      const parsedImg = parseDataUrlToBytesAndExt(cImg.dataUrl, 'png');
      if (parsedImg) {
        const imgPath = `images/${entrySlug}_image_${idx + 1}.${parsedImg.ext}`;
        zipFiles.push({ path: imgPath, data: parsedImg.bytes });
        md += `- ![Canvas Image ${idx + 1}](../${imgPath}) — \`${imgPath}\` (Layer: ${
          cImg.layer || 'foreground'
        }, Width: ${cImg.width}px, Rotation: ${cImg.rotation || 0}°)\n`;
      }
    });
  }

  // Extract Audio Attachments in their exact audio format (.wav, .flac, .m4a, .aac, .mp3, .ogg, .webm)
  const audioAttachments = log.audioAttachments || [];
  if (audioAttachments.length > 0) {
    md += `\n\n## Attached Audio Recordings\n\n`;
    audioAttachments.forEach((aud, idx) => {
      const preferredExt = (aud.format || 'wav').replace(/^\.+/, '').toLowerCase();
      const parsedAud = parseDataUrlToBytesAndExt(aud.dataUrl, preferredExt);
      if (parsedAud) {
        // Keep the user's configured audio format extension if specified
        const finalExt = preferredExt || parsedAud.ext;
        const audSlug = sanitizeFileSlug(
          aud.name.replace(/\.[^.]+$/, ''),
          `audio_${idx + 1}`
        );
        const audPath = `audio/${entrySlug}_${idx + 1}_${audSlug}.${finalExt}`;
        zipFiles.push({ path: audPath, data: parsedAud.bytes });
        md += `- **${aud.name}** — [../${audPath}](../${audPath})\n`;
      }
    });
  }

  zipFiles.push({
    path: `diaries/${entrySlug}.md`,
    data: enc.encode(md),
  });

  return md;
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      if (typeof reader.result === 'string') {
        const idx = reader.result.indexOf(',');
        resolve(idx >= 0 ? reader.result.slice(idx + 1) : reader.result);
      } else {
        reject(new Error('Failed to encode ZIP archive'));
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

/**
 * Exports all data in the active vault as a single `.zip` archive:
 * - Every diary's content is exported as `.md` (Markdown) inside `diaries/`
 * - Every image (PFP, Canvas Background, Inline Images, Floating Canvas Images) is exported in its native format inside `images/`
 * - Every audio recording/attachment is exported in its native format (`.wav`, `.flac`, `.m4a`, `.aac`, `.mp3`, `.ogg`, `.webm`) inside `audio/`
 * - Saved Word Analyses and Custom Fonts (if any) are also included in the archive.
 */
export async function exportAllAppDataAsZip(
  logs: DiaryLog[],
  customFonts: CustomFontItem[],
  vaultMode: VaultMode = 'primary'
): Promise<string> {
  const zipFiles: ZipEntryFile[] = [];
  const enc = new TextEncoder();

  logs.forEach((log, idx) => {
    const numPrefix = String(idx + 1).padStart(2, '0');
    const slug = `${numPrefix}_${sanitizeFileSlug(log.heading, `diary_${idx + 1}`)}`;
    convertHtmlToMarkdownWithExtractedMedia(log, slug, zipFiles);
  });

  // Include Saved Offline Word Analyses as a Markdown reference if present
  const savedWords = getSavedWordAnalyses(vaultMode);
  if (savedWords.length > 0) {
    let wordsMd = `# Saved Word Analysis (Alphabetical A–Z)\n\n`;
    for (const w of savedWords) {
      wordsMd += `## ${w.word} (${w.phonetics.phoneticTranscription || 'N/A'})\n`;
      wordsMd += `- **Part of Speech:** ${w.morphology.partOfSpeech.join(', ')}\n`;
      if (w.lexical.definitions[0]) {
        wordsMd += `- **Definition:** ${w.lexical.definitions[0].definition}\n`;
      }
      if (w.lexical.synonyms.length > 0) {
        wordsMd += `- **Synonyms:** ${w.lexical.synonyms.join(', ')}\n`;
      }
      if (w.morphology.etymologyAndOrigin) {
        wordsMd += `- **Etymology:** ${w.morphology.etymologyAndOrigin}\n`;
      }
      wordsMd += `\n`;
    }
    zipFiles.push({
      path: `word_analysis/saved_word_analysis.md`,
      data: enc.encode(wordsMd),
    });
  }

  // Include Custom Imported Fonts (.ttf) if present
  customFonts.forEach((font, idx) => {
    const parsedFont = parseDataUrlToBytesAndExt(font.dataUrl, 'ttf');
    if (parsedFont) {
      const fontSlug = sanitizeFileSlug(font.name, `custom_font_${idx + 1}`);
      zipFiles.push({
        path: `fonts/${fontSlug}.ttf`,
        data: parsedFont.bytes,
      });
    }
  });

  // Always include a top-level README.md summary in the ZIP
  const exportedDate = new Date().toLocaleString('en-IN');
  const readmeMd = `# Likkho Data Export Archive\n\n- **Exported At:** ${exportedDate}\n- **Total Diaries (.md):** ${logs.length}\n- **Saved Word Analyses:** ${savedWords.length}\n- **Custom Fonts (.ttf):** ${customFonts.length}\n\n## Folder Structure\n- \`diaries/\` — All diary entries in Markdown (\`.md\`) format.\n- \`images/\` — All profile pictures, canvas backgrounds, inline images, and floating canvas images in their original image formats.\n- \`audio/\` — All recorded and attached audio files in their original audio formats.\n`;
  zipFiles.push({
    path: `README.md`,
    data: enc.encode(readmeMd),
  });

  const zipBlob = buildZipArchiveBlob(zipFiles);
  const dateTag = new Date().toISOString().slice(0, 10);
  const filename = `likkho_all_data_${dateTag}.zip`;
  const mimeType = 'application/zip';

  if (
    window.LikkhoNative &&
    typeof window.LikkhoNative.saveExportedFile === 'function'
  ) {
    const base64Data = await blobToBase64(zipBlob);
    window.LikkhoNative.saveExportedFile(base64Data, filename, mimeType);
    return `Saved to Downloads/Likkho/${filename}`;
  }

  const url = URL.createObjectURL(zipBlob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 3000);
  return `Exported ${filename}`;
}

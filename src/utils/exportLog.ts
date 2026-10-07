import { CanvasAudioAttachment, CanvasDraggableImage, DiaryLog } from './cryptoVault';

declare global {
  interface Window {
    LikkhoNative?: {
      requestFilesAndMediaPermission?: () => void;
      requestMicPermission?: () => void;
      saveExportedFile?: (base64Data: string, filename: string, mimeType: string) => void;
      writePersistentVaultBackup?: (envelope: string) => boolean;
      readPersistentVaultBackup?: () => string;
      saveBackupViaSaf?: (envelope: string) => void;
      restoreBackupViaSaf?: () => void;
      chooseSafBaseFolder?: () => void;
      getSafBaseFolderName?: () => string;
      pickVaultBackupFile?: () => void;
      authenticateBiometric?: () => void;
      cancelBiometricPrompt?: () => void;
      authenticateDeviceLockForBackup?: () => void;
      scheduleNativeReminder?: (logId: string, title: string, body: string, triggerAtMs: number) => void;
      cancelNativeReminder?: (logId: string) => void;
      startForegroundMicService?: () => void;
      stopForegroundMicService?: () => void;
      startNativeMicRecording?: (sampleRate: number, bitRate: number) => void;
      stopNativeMicRecording?: () => void;
      isNativeMicRecordingActive?: () => boolean;
      getNativeMicRecordingSeconds?: () => number;
      setSystemBarsTheme?: (statusBarHex: string, navBarHex: string, isLightIcons: boolean) => void;
      setAppPasscodeProtectionEnabled?: (enabled: boolean) => void;
    };
    __handleLikkhoAndroidBack?: () => string;
    __onLikkhoBiometricResult?: (success: boolean, message?: string) => void;
    __onLikkhoDeviceLockBackupResult?: (success: boolean, message?: string) => void;
    __onLikkhoNativeMicFinished?: (base64Wav: string, durationSec: number) => void;
    __onLikkhoVaultFilePicked?: (envelope: string) => void;
    __onLikkhoSafBackupResult?: (success: boolean, message?: string) => void;
    __onLikkhoSafRestoreResult?: (success: boolean, payload?: string) => void;
    __onLikkhoSafFolderSelected?: (folderName: string) => void;
  }
}

export type ExportFormat =
  | 'txt'
  | 'md'
  | 'pdf'
  | 'rtf'
  | 'html'
  | 'json'
  | 'csv'
  | 'docx'
  | 'xml'
  | 'tsv';

export const EXPORT_FORMATS: { ext: ExportFormat; label: string; desc: string }[] = [
  { ext: 'txt', label: '.txt', desc: 'Plain Text Document' },
  { ext: 'md', label: '.md', desc: 'Markdown Document' },
  { ext: 'pdf', label: '.pdf', desc: 'PDF Printable Document (Full Canvas)' },
  { ext: 'rtf', label: '.rtf', desc: 'Rich Text Format' },
  { ext: 'html', label: '.html', desc: 'Web Page Archive' },
  { ext: 'docx', label: '.docx', desc: 'Microsoft Word Document (Full Canvas)' },
  { ext: 'json', label: '.json', desc: 'Structured JSON Data' },
  { ext: 'csv', label: '.csv', desc: 'Comma-Separated Values' },
  { ext: 'tsv', label: '.tsv', desc: 'Tab-Separated Values' },
  { ext: 'xml', label: '.xml', desc: 'Extensible Markup Language' },
];

function htmlToPlainText(html: string): string {
  const div = document.createElement('div');
  div.innerHTML = html
    .replace(/<\/p>/gi, '\n\n')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/h[1-6]>/gi, '\n\n')
    .replace(/<\/li>/gi, '\n');
  return (div.textContent || div.innerText || '').trim();
}

function htmlToMarkdown(heading: string, dateStamp: string, timeStamp: string, html: string): string {
  let md = `# ${heading}\n*${dateStamp} · ${timeStamp}*\n\n`;
  const body = html
    .replace(/<h1[^>]*>(.*?)<\/h1>/gi, '# $1\n\n')
    .replace(/<h2[^>]*>(.*?)<\/h2>/gi, '## $1\n\n')
    .replace(/<h3[^>]*>(.*?)<\/h3>/gi, '### $1\n\n')
    .replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**')
    .replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**')
    .replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*')
    .replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*')
    .replace(/<u[^>]*>(.*?)<\/u>/gi, '_$1_')
    .replace(/<strike[^>]*>(.*?)<\/strike>/gi, '~~$1~~')
    .replace(/<s[^>]*>(.*?)<\/s>/gi, '~~$1~~')
    .replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
    .replace(/<blockquote[^>]*>(.*?)<\/blockquote>/gi, '> $1\n\n')
    .replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
    .replace(/<hr\s*\/?>/gi, '\n---\n\n')
    .replace(/<\/p>/gi, '\n\n')
    .replace(/<br\s*\/?>/gi, '\n');

  const temp = document.createElement('div');
  temp.innerHTML = body;
  md += (temp.textContent || temp.innerText || '').trim();
  return md;
}

function escapeXml(unsafe: string): string {
  return unsafe
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function escapeRtf(str: string): string {
  let res = '';
  for (let i = 0; i < str.length; i++) {
    const code = str.charCodeAt(i);
    if (code === 10) {
      res += '\\par\n';
    } else if (code === 92 || code === 123 || code === 125) {
      res += '\\' + str[i];
    } else if (code > 127) {
      res += `\\u${code}?`;
    } else {
      res += str[i];
    }
  }
  return res;
}

function formatAudioSeconds(sec?: number): string {
  if (!sec || !Number.isFinite(sec) || sec < 0) return '00:01';
  const total = Math.floor(sec);
  const mins = Math.floor(total / 60);
  const rem = total % 60;
  return `${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
}

function formatAudioStamp(ms?: number): string {
  const ts = ms && Number.isFinite(ms) ? ms : Date.now();
  const d = new Date(ts);
  const datePart = d.toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
  const timePart = d.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
  return `${datePart} · ${timePart}`;
}

function loadImageElement(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

/**
 * Renders the exact visual Canvas of a DiaryLog onto an HTML5 Canvas:
 * - Header (PFP + Heading + Created & Modified timestamps)
 * - Selected Canvas Background Image (with its exact opacity)
 * - Background-layer Canvas Images (`layer === 'background'`) at their exact (x, y, width, rotation)
 * - Background-layer Audio Players (`layer === 'background'`) at their exact (x, y, width, height, rotation)
 * - Rich Text Content (headings, bold, italic, underline, colors, highlights, quotes, lists, spoilers)
 * - Foreground-layer Canvas Images (`layer !== 'background'`) at their exact (x, y, width, rotation)
 * - Foreground-layer Audio Players (`layer !== 'background'`) at their exact (x, y, width, height, rotation) with creation date & time stamp!
 */
async function renderFullDiaryCanvasToJpeg(log: DiaryLog): Promise<{
  jpegDataUrl: string;
  jpegBytes: Uint8Array;
  pixelWidth: number;
  pixelHeight: number;
  logicalWidth: number;
  logicalHeight: number;
}> {
  const logicalWidth = 768;
  const headerHeight = 96;
  const sidePadding = 32;
  const topCanvasPad = 24;

  const images: CanvasDraggableImage[] = log.canvasImages || [];
  const audios: CanvasAudioAttachment[] = log.audioAttachments || [];

  // Measure rich text height and collect styled text blocks by mounting a hidden offscreen DOM container
  const host = document.createElement('div');
  host.style.position = 'fixed';
  host.style.left = '-9999px';
  host.style.top = '0';
  host.style.width = `${logicalWidth - sidePadding * 2}px`;
  host.style.fontFamily = "'Lora', Georgia, 'Times New Roman', serif";
  host.style.fontSize = '16px';
  host.style.lineHeight = '1.72';
  host.style.color = '#202122';
  host.innerHTML = log.contentHtml || '';
  document.body.appendChild(host);

  const measuredDomHeight = Math.max(420, host.scrollHeight + 80);

  let maxMediaBottom = 0;
  for (const im of images) {
    const b = (im.y || 0) + (im.height || im.width || 220) + 60;
    if (b > maxMediaBottom) maxMediaBottom = b;
  }
  for (const au of audios) {
    const b = (au.y ?? 140) + (au.height ?? 64) + 60;
    if (b > maxMediaBottom) maxMediaBottom = b;
  }

  const canvasAreaHeight = Math.max(680, measuredDomHeight, maxMediaBottom);
  const logicalHeight = headerHeight + canvasAreaHeight;
  const scale = 2; // High-DPI crisp export
  const pixelWidth = logicalWidth * scale;
  const pixelHeight = logicalHeight * scale;

  const canvas = document.createElement('canvas');
  canvas.width = pixelWidth;
  canvas.height = pixelHeight;
  const ctx = canvas.getContext('2d')!;
  ctx.scale(scale, scale);

  // 1. Base white background
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, logicalWidth, logicalHeight);

  // 2. Draw Diary Header Bar
  ctx.fillStyle = '#f8f9fa';
  ctx.fillRect(0, 0, logicalWidth, headerHeight);
  ctx.strokeStyle = '#a2a9b1';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(0, headerHeight);
  ctx.lineTo(logicalWidth, headerHeight);
  ctx.stroke();

  let titleStartX = sidePadding;
  if (log.pfpDataUrl) {
    const pfpImg = await loadImageElement(log.pfpDataUrl);
    if (pfpImg) {
      ctx.save();
      ctx.strokeStyle = '#a2a9b1';
      ctx.lineWidth = 1;
      ctx.strokeRect(sidePadding, 20, 56, 56);
      ctx.drawImage(pfpImg, sidePadding, 20, 56, 56);
      ctx.restore();
      titleStartX = sidePadding + 70;
    }
  }

  ctx.fillStyle = '#202122';
  ctx.font = "bold 24px Georgia, 'Times New Roman', serif";
  ctx.fillText(log.heading || 'Untitled Entry', titleStartX, 46);

  const createdLine = `Created: ${log.dateStamp} · ${log.timeStamp}`;
  const modifiedLine =
    log.updatedDateStamp && log.updatedTimeStamp
      ? `   |   Modified: ${log.updatedDateStamp} · ${log.updatedTimeStamp}`
      : '';
  ctx.fillStyle = '#54595d';
  ctx.font = '12px monospace';
  ctx.fillText(createdLine + modifiedLine, titleStartX, 68);

  // 3. Draw Canvas Background Image (strictly inside Canvas area below Header)
  if (log.canvasBgDataUrl) {
    const bgImg = await loadImageElement(log.canvasBgDataUrl);
    if (bgImg) {
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, headerHeight, logicalWidth, canvasAreaHeight);
      ctx.clip();
      ctx.globalAlpha = Math.max(0.05, Math.min(1, log.canvasBgOpacity ?? 0.25));

      const imgRatio = bgImg.width / Math.max(1, bgImg.height);
      const areaRatio = logicalWidth / canvasAreaHeight;
      let drawW = logicalWidth;
      let drawH = canvasAreaHeight;
      let drawX = 0;
      let drawY = headerHeight;
      if (imgRatio > areaRatio) {
        drawH = canvasAreaHeight;
        drawW = drawH * imgRatio;
        drawX = (logicalWidth - drawW) / 2;
      } else {
        drawW = logicalWidth;
        drawH = drawW / imgRatio;
        drawY = headerHeight + (canvasAreaHeight - drawH) / 2;
      }
      ctx.drawImage(bgImg, drawX, drawY, drawW, drawH);
      ctx.restore();
    }
  }

  // Helper to draw a positioned & rotated Canvas Image
  const drawCanvasImageItem = async (item: CanvasDraggableImage) => {
    const el = await loadImageElement(item.dataUrl);
    if (!el) return;
    const w = item.width || 220;
    const aspect = el.height / Math.max(1, el.width);
    const h = Math.round(w * aspect);
    const x = item.x ?? 24;
    const y = headerHeight + (item.y ?? 80);
    const rot = ((item.rotation || 0) * Math.PI) / 180;

    ctx.save();
    ctx.translate(x + w / 2, y + h / 2);
    ctx.rotate(rot);
    ctx.globalAlpha = item.opacity ?? 1;
    ctx.drawImage(el, -w / 2, -h / 2, w, h);
    ctx.restore();
  };

  // Helper to draw the Opaque Red Rectangular Audio Player Card at its exact position, size & rotation
  const drawCanvasAudioItem = (aud: CanvasAudioAttachment, idx: number) => {
    const w = aud.width ?? 270;
    const h = aud.height ?? 64;
    const x = aud.x ?? 24 + (idx * 20) % 80;
    const y = headerHeight + (aud.y ?? 140 + idx * 88);
    const rot = ((aud.rotation ?? 0) * Math.PI) / 180;

    ctx.save();
    ctx.translate(x + w / 2, y + h / 2);
    ctx.rotate(rot);

    // Shadow & Opaque Red Body (#b32424)
    ctx.fillStyle = '#b32424';
    ctx.fillRect(-w / 2, -h / 2, w, h);
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = '#7a1616';
    ctx.strokeRect(-w / 2, -h / 2, w, h);

    // Play Button Square BEFORE Slider
    const btnSize = 30;
    const btnX = -w / 2 + 12;
    const btnY = -btnSize / 2;
    ctx.fillStyle = '#8e1b1b';
    ctx.fillRect(btnX, btnY, btnSize, btnSize);
    ctx.strokeStyle = 'rgba(255,255,255,0.45)';
    ctx.lineWidth = 1;
    ctx.strokeRect(btnX, btnY, btnSize, btnSize);

    // Play triangle icon
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.moveTo(btnX + 10, btnY + 8);
    ctx.lineTo(btnX + 10, btnY + 22);
    ctx.lineTo(btnX + 22, btnY + 15);
    ctx.closePath();
    ctx.fill();

    // Slider Track
    const sliderX = btnX + btnSize + 10;
    const sliderW = Math.max(40, w - (btnSize + 34));
    const sliderY = -h / 2 + 16;
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    ctx.fillRect(sliderX, sliderY, sliderW, 4);
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(sliderX + 6, sliderY + 2, 5, 0, Math.PI * 2);
    ctx.fill();

    // Audio Name & Duration Row
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 10px monospace';
    const maxNameChars = Math.max(10, Math.floor((sliderW - 68) / 6));
    const displayName =
      aud.name.length > maxNameChars
        ? aud.name.slice(0, maxNameChars - 1) + '…'
        : aud.name;
    ctx.fillText(displayName, sliderX, sliderY + 18);

    const durText = `00:00 / ${formatAudioSeconds(aud.durationSec)}`;
    ctx.font = '10px monospace';
    const durWidth = ctx.measureText(durText).width;
    ctx.fillText(durText, sliderX + sliderW - durWidth, sliderY + 18);

    // Creation Date & Time Stamp Row
    const createdStamp = formatAudioStamp(aud.createdAt);
    ctx.fillStyle = 'rgba(255,255,255,0.88)';
    ctx.font = '9px monospace';
    ctx.fillText(createdStamp, sliderX, sliderY + 31);

    ctx.restore();
  };

  // 4. Draw Background-Layer Canvas Images & Background-Layer Audio Cards (behind text)
  for (const im of images) {
    if (im.layer === 'background') {
      await drawCanvasImageItem(im);
    }
  }
  audios.forEach((aud, idx) => {
    if (aud.layer === 'background') {
      drawCanvasAudioItem(aud, idx);
    }
  });

  // 5. Render Rich Text Content from the DOM onto the Canvas
  let cursorY = headerHeight + topCanvasPad + 18;
  const maxTextWidth = logicalWidth - sidePadding * 2;

  const renderDomBlockList = (parent: HTMLElement) => {
    const children = Array.from(parent.childNodes);
    if (children.length === 0) return;

    for (const node of children) {
      if (node.nodeType === Node.TEXT_NODE) {
        const txt = (node.textContent || '').replace(/\u200B/g, '').trim();
        if (txt) {
          drawWrappedText(ctx, txt, sidePadding, maxTextWidth, 16, 'normal', 'normal', '#202122', null);
        }
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        const el = node as HTMLElement;
        const tag = el.tagName.toUpperCase();

        if (tag === 'HR') {
          cursorY += 8;
          ctx.strokeStyle = '#a2a9b1';
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(sidePadding, cursorY);
          ctx.lineTo(logicalWidth - sidePadding, cursorY);
          ctx.stroke();
          cursorY += 18;
          continue;
        }

        const computed = window.getComputedStyle(el);
        const fontSizePx = parseFloat(computed.fontSize) || (tag === 'H1' ? 26 : tag === 'H2' ? 21 : tag === 'H3' ? 18 : 16);
        const isBold =
          tag === 'H1' ||
          tag === 'H2' ||
          tag === 'H3' ||
          tag === 'B' ||
          tag === 'STRONG' ||
          parseInt(computed.fontWeight || '400', 10) >= 600;
        const isItalic =
          tag === 'I' ||
          tag === 'EM' ||
          tag === 'BLOCKQUOTE' ||
          computed.fontStyle === 'italic';
        const color = computed.color || '#202122';
        const bgHighlight =
          tag === 'MARK' ? computed.backgroundColor || '#fef08a' : null;

        if (tag === 'UL' || tag === 'OL') {
          const items = Array.from(el.querySelectorAll(':scope > li'));
          items.forEach((li, idx) => {
            const prefix = tag === 'OL' ? `${idx + 1}. ` : '• ';
            const liText = prefix + (li.textContent || '').replace(/\u200B/g, '').trim();
            drawWrappedText(
              ctx,
              liText,
              sidePadding + 14,
              maxTextWidth - 14,
              fontSizePx,
              isBold ? 'bold' : 'normal',
              isItalic ? 'italic' : 'normal',
              color,
              bgHighlight
            );
          });
          cursorY += 6;
          continue;
        }

        if (tag === 'BLOCKQUOTE') {
          const quoteText = (el.textContent || '').replace(/\u200B/g, '').trim();
          const startQuoteY = cursorY - 12;
          drawWrappedText(
            ctx,
            quoteText,
            sidePadding + 16,
            maxTextWidth - 20,
            fontSizePx,
            'normal',
            'italic',
            '#54595d',
            '#f8f9fa'
          );
          ctx.strokeStyle = '#a2a9b1';
          ctx.lineWidth = 3;
          ctx.beginPath();
          ctx.moveTo(sidePadding + 4, startQuoteY);
          ctx.lineTo(sidePadding + 4, cursorY - 6);
          ctx.stroke();
          cursorY += 8;
          continue;
        }

        const blockText = (el.textContent || '').replace(/\u200B/g, '');
        if (blockText.trim().length === 0) {
          cursorY += Math.round(fontSizePx * 1.2);
          continue;
        }

        drawWrappedText(
          ctx,
          blockText,
          sidePadding,
          maxTextWidth,
          fontSizePx,
          isBold ? 'bold' : 'normal',
          isItalic ? 'italic' : 'normal',
          color,
          bgHighlight
        );

        if (tag === 'H1' || tag === 'H2') {
          ctx.strokeStyle = '#eaecf0';
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(sidePadding, cursorY - 10);
          ctx.lineTo(logicalWidth - sidePadding, cursorY - 10);
          ctx.stroke();
          cursorY += 6;
        } else if (tag === 'P' || tag === 'DIV') {
          cursorY += 6;
        }
      }
    }
  };

  const drawWrappedText = (
    c: CanvasRenderingContext2D,
    text: string,
    startX: number,
    maxWidth: number,
    fontSize: number,
    fontWeight: string,
    fontStyle: string,
    textColor: string,
    highlightColor: string | null
  ) => {
    c.font = `${fontStyle} ${fontWeight} ${fontSize}px Georgia, 'Times New Roman', serif`;
    const lineHeight = Math.round(fontSize * 1.6);
    const paragraphs = text.split(/\r?\n/);

    for (const para of paragraphs) {
      const words = para.split(/\s+/);
      let line = '';
      for (let i = 0; i < words.length; i++) {
        const testLine = line ? `${line} ${words[i]}` : words[i];
        const metrics = c.measureText(testLine);
        if (metrics.width > maxWidth && line) {
          if (highlightColor && highlightColor !== 'rgba(0, 0, 0, 0)') {
            c.fillStyle = highlightColor;
            c.fillRect(
              startX,
              cursorY - fontSize,
              c.measureText(line).width + 4,
              fontSize + 4
            );
          }
          c.fillStyle = textColor;
          c.fillText(line, startX, cursorY);
          line = words[i];
          cursorY += lineHeight;
        } else {
          line = testLine;
        }
      }
      if (line) {
        if (highlightColor && highlightColor !== 'rgba(0, 0, 0, 0)') {
          c.fillStyle = highlightColor;
          c.fillRect(
            startX,
            cursorY - fontSize,
            c.measureText(line).width + 4,
            fontSize + 4
          );
        }
        c.fillStyle = textColor;
        c.fillText(line, startX, cursorY);
        cursorY += lineHeight;
      }
    }
  };

  renderDomBlockList(host);
  document.body.removeChild(host);

  // 6. Draw Foreground-Layer Canvas Images & Foreground-Layer Audio Cards (in front of text)
  for (const im of images) {
    if (im.layer !== 'background') {
      await drawCanvasImageItem(im);
    }
  }
  audios.forEach((aud, idx) => {
    if (aud.layer !== 'background') {
      drawCanvasAudioItem(aud, idx);
    }
  });

  const jpegDataUrl = canvas.toDataURL('image/jpeg', 0.92);
  const b64Idx = jpegDataUrl.indexOf(',');
  const b64 = b64Idx >= 0 ? jpegDataUrl.slice(b64Idx + 1) : jpegDataUrl;
  const bin = atob(b64);
  const jpegBytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    jpegBytes[i] = bin.charCodeAt(i);
  }

  return {
    jpegDataUrl,
    jpegBytes,
    pixelWidth,
    pixelHeight,
    logicalWidth,
    logicalHeight,
  };
}

/**
 * Builds a valid PDF 1.4 binary containing the full rendered Diary Canvas (background, positioned images, audio cards, and text)
 */
async function buildVisualCanvasPdfBlob(log: DiaryLog): Promise<Blob> {
  const rendered = await renderFullDiaryCanvasToJpeg(log);
  const pageWidthPt = 595.28; // A4 width in pt
  const scaleFactor = pageWidthPt / rendered.logicalWidth;
  const pageHeightPt = Math.max(841.89, Math.round(rendered.logicalHeight * scaleFactor));
  const drawWidthPt = pageWidthPt;
  const drawHeightPt = Math.round(rendered.logicalHeight * scaleFactor);
  const drawYPt = pageHeightPt - drawHeightPt;

  const encoder = new TextEncoder();
  const chunks: Uint8Array[] = [];
  let byteOffset = 0;
  const objectOffsets: number[] = [];

  const pushStr = (s: string) => {
    const bytes = encoder.encode(s);
    chunks.push(bytes);
    byteOffset += bytes.length;
  };

  const pushBytes = (b: Uint8Array) => {
    chunks.push(b);
    byteOffset += b.length;
  };

  pushStr('%PDF-1.4\n%\xFF\xFF\xFF\xFF\n');

  // Object 1: Catalog
  objectOffsets.push(byteOffset);
  pushStr('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n');

  // Object 2: Pages
  objectOffsets.push(byteOffset);
  pushStr('2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n');

  // Object 3: Page
  objectOffsets.push(byteOffset);
  pushStr(
    `3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidthPt.toFixed(
      2
    )} ${pageHeightPt.toFixed(
      2
    )}] /Resources << /XObject << /Im1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n`
  );

  // Object 4: Image XObject (DCTDecode JPEG of the full Diary Canvas)
  objectOffsets.push(byteOffset);
  pushStr(
    `4 0 obj\n<< /Type /XObject /Subtype /Image /Width ${rendered.pixelWidth} /Height ${rendered.pixelHeight} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${rendered.jpegBytes.length} >>\nstream\n`
  );
  pushBytes(rendered.jpegBytes);
  pushStr('\nendstream\nendobj\n');

  // Object 5: Page Content Stream drawing /Im1 across the page
  const contentStream = `q\n${drawWidthPt.toFixed(2)} 0 0 ${drawHeightPt.toFixed(
    2
  )} 0 ${drawYPt.toFixed(2)} cm\n/Im1 Do\nQ\n`;
  objectOffsets.push(byteOffset);
  pushStr(
    `5 0 obj\n<< /Length ${contentStream.length} >>\nstream\n${contentStream}endstream\nendobj\n`
  );

  // XRef table
  const xrefStart = byteOffset;
  pushStr(`xref\n0 ${objectOffsets.length + 1}\n0000000000 65535 f \n`);
  for (const off of objectOffsets) {
    pushStr(`${String(off).padStart(10, '0')} 00000 n \n`);
  }
  pushStr(
    `trailer\n<< /Size ${
      objectOffsets.length + 1
    } /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`
  );

  return new Blob(chunks, { type: 'application/pdf' });
}

/**
 * Builds a Rich Visual Canvas HTML representation for DOCX & HTML exports
 * preserving the selected canvas background, positioned & rotated images, and opaque red audio player cards with creation timestamps!
 */
async function buildVisualCanvasDocxBlob(log: DiaryLog): Promise<Blob> {
  const rendered = await renderFullDiaryCanvasToJpeg(log);
  const images: CanvasDraggableImage[] = log.canvasImages || [];
  const audios: CanvasAudioAttachment[] = log.audioAttachments || [];

  const positionedElementsHtml = [
    ...images.map((im) => {
      const z = im.layer === 'background' ? 5 : 20;
      return `<div style="position:absolute;left:${im.x ?? 24}px;top:${
        im.y ?? 80
      }px;width:${im.width || 220}px;transform:rotate(${
        im.rotation || 0
      }deg);z-index:${z};"><img src="${
        im.dataUrl
      }" style="width:100%;height:auto;display:block;" /></div>`;
    }),
    ...audios.map((aud, idx) => {
      const z = aud.layer === 'background' ? 6 : 21;
      const x = aud.x ?? 24 + (idx * 20) % 80;
      const y = aud.y ?? 140 + idx * 88;
      const w = aud.width ?? 270;
      const h = aud.height ?? 64;
      const rot = aud.rotation ?? 0;
      const createdStamp = formatAudioStamp(aud.createdAt);
      return `<div style="position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;transform:rotate(${rot}deg);z-index:${z};background:#b32424;border:1px solid #7a1616;color:#ffffff;padding:8px 12px;box-sizing:border-box;font-family:monospace;">
        <div style="font-weight:bold;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">▶ ${escapeXml(
          aud.name
        )} (${formatAudioSeconds(aud.durationSec)})</div>
        <div style="font-size:9px;opacity:0.9;margin-top:3px;">${escapeXml(
          createdStamp
        )}</div>
      </div>`;
    }),
  ].join('\n');

  const wordDoc = `<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
<head>
<meta charset='utf-8'>
<title>${escapeXml(log.heading)}</title>
</head>
<body style="font-family: 'Times New Roman', Georgia, serif; font-size: 12pt; margin: 0; padding: 16px; background: #ffffff; color: #202122;">
  <!-- High-Resolution Full Canvas Snapshot (Shows exact Canvas background, positioned images, and red audio player cards in Word & Docs viewers) -->
  <div style="margin-bottom: 20px; border: 1px solid #a2a9b1;">
    <img src="${rendered.jpegDataUrl}" alt="${escapeXml(
    log.heading
  )}" width="680" style="width: 100%; max-width: 680px; height: auto; display: block;" />
  </div>

  <!-- Editable Structured Canvas Layer -->
  <div style="position: relative; min-height: 480px; padding: 20px; border: 1px solid #eaecf0; background-color: #ffffff;">
    <h2>${escapeXml(log.heading)}</h2>
    <p style="color:#54595d;font-size:9.5pt;font-family:monospace;">Created: ${escapeXml(
      log.dateStamp
    )} · ${escapeXml(log.timeStamp)}${
    log.updatedDateStamp && log.updatedTimeStamp
      ? ` | Modified: ${escapeXml(log.updatedDateStamp)} · ${escapeXml(
          log.updatedTimeStamp
        )}`
      : ''
  }</p>
    <hr style="border:none;border-top:1px solid #a2a9b1;margin:12px 0;" />
    <div style="position:relative;z-index:10;">
      ${log.contentHtml}
    </div>
    ${positionedElementsHtml}
  </div>
</body>
</html>`;

  const mimeType =
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  return new Blob(['\ufeff', wordDoc], { type: mimeType });
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      if (typeof reader.result === 'string') {
        const idx = reader.result.indexOf(',');
        resolve(idx >= 0 ? reader.result.slice(idx + 1) : reader.result);
      } else {
        reject(new Error('Failed to encode blob'));
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

export async function exportDiaryLog(log: DiaryLog, format: ExportFormat): Promise<string> {
  const plainText = htmlToPlainText(log.contentHtml);
  const safeBaseName =
    (log.heading || 'diary_entry')
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/gi, '_')
      .replace(/^_+|_+$/g, '')
      .slice(0, 40) || 'likkho_log';

  const dateLine = `${log.dateStamp} · ${log.timeStamp}`;
  let blob: Blob;
  let mimeType = 'text/plain';
  const filename = `${safeBaseName}.${format}`;

  switch (format) {
    case 'txt': {
      const content = `${log.heading}\n${dateLine}\n${'='.repeat(40)}\n\n${plainText}\n`;
      mimeType = 'text/plain';
      blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
      break;
    }
    case 'md': {
      const content = htmlToMarkdown(log.heading, log.dateStamp, log.timeStamp, log.contentHtml);
      mimeType = 'text/markdown';
      blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
      break;
    }
    case 'html': {
      const content = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<title>${escapeXml(log.heading)}</title>
<style>
  body { font-family: Georgia, 'Times New Roman', serif; max-width: 720px; margin: 40px auto; padding: 0 20px; color: #202122; line-height: 1.7; }
  h1.title { border-bottom: 1px solid #a2a9b1; padding-bottom: 8px; margin-bottom: 4px; }
  .meta { color: #54595d; font-size: 0.85rem; margin-bottom: 24px; font-family: monospace; }
  blockquote { border-left: 3px solid #a2a9b1; padding: 8px 16px; background: #f8f9fa; font-style: italic; }
  a { color: #3366cc; }
</style>
</head>
<body>
  <h1 class="title">${escapeXml(log.heading)}</h1>
  <div class="meta">${escapeXml(dateLine)}</div>
  <div class="content">${log.contentHtml}</div>
</body>
</html>`;
      mimeType = 'text/html';
      blob = new Blob([content], { type: 'text/html;charset=utf-8' });
      break;
    }
    case 'rtf': {
      const rtfContent = `{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs32\\b ${escapeRtf(
        log.heading
      )}\\b0\\par\\fs20 ${escapeRtf(dateLine)}\\par\\par\\fs24 ${escapeRtf(plainText)}\\par}`;
      mimeType = 'application/rtf';
      blob = new Blob([rtfContent], { type: 'application/rtf' });
      break;
    }
    case 'docx': {
      mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
      blob = await buildVisualCanvasDocxBlob(log);
      break;
    }
    case 'json': {
      const content = JSON.stringify(
        {
          app: 'Likkho',
          id: log.id,
          heading: log.heading,
          dateStamp: log.dateStamp,
          timeStamp: log.timeStamp,
          createdAt: log.createdAt,
          updatedAt: log.updatedAt,
          plainText,
          contentHtml: log.contentHtml,
        },
        null,
        2
      );
      mimeType = 'application/json';
      blob = new Blob([content], { type: 'application/json;charset=utf-8' });
      break;
    }
    case 'csv': {
      const escCsv = (val: string) => `"${val.replace(/"/g, '""')}"`;
      const csv = `id,heading,date,time,content\n${[
        escCsv(log.id),
        escCsv(log.heading),
        escCsv(log.dateStamp),
        escCsv(log.timeStamp),
        escCsv(plainText),
      ].join(',')}\n`;
      mimeType = 'text/csv';
      blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      break;
    }
    case 'tsv': {
      const cleanTab = (val: string) => val.replace(/[\t\r\n]+/g, ' ');
      const tsv = `id\theading\tdate\ttime\tcontent\n${[
        cleanTab(log.id),
        cleanTab(log.heading),
        cleanTab(log.dateStamp),
        cleanTab(log.timeStamp),
        cleanTab(plainText),
      ].join('\t')}\n`;
      mimeType = 'text/tab-separated-values';
      blob = new Blob([tsv], { type: 'text/tab-separated-values;charset=utf-8' });
      break;
    }
    case 'xml': {
      const xml = `<?xml version="1.0" encoding="UTF-8"?>
<likkhoEntry>
  <id>${escapeXml(log.id)}</id>
  <heading>${escapeXml(log.heading)}</heading>
  <dateStamp>${escapeXml(log.dateStamp)}</dateStamp>
  <timeStamp>${escapeXml(log.timeStamp)}</timeStamp>
  <plainText>${escapeXml(plainText)}</plainText>
  <contentHtml><![CDATA[${log.contentHtml}]]></contentHtml>
</likkhoEntry>`;
      mimeType = 'application/xml';
      blob = new Blob([xml], { type: 'application/xml;charset=utf-8' });
      break;
    }
    case 'pdf': {
      mimeType = 'application/pdf';
      blob = await buildVisualCanvasPdfBlob(log);
      break;
    }
  }

  // 1. If running inside Android APK WebView with LikkhoNative bridge, save directly to Downloads/Likkho/
  if (window.LikkhoNative && typeof window.LikkhoNative.saveExportedFile === 'function') {
    const base64Data = await blobToBase64(blob);
    window.LikkhoNative.saveExportedFile(base64Data, filename, mimeType);
    return `Saved to Downloads/Likkho/${filename}`;
  }

  // 2. Otherwise trigger browser download
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 2500);
  return `Exported ${filename}`;
}

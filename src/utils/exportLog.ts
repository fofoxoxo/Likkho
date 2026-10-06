import { DiaryLog } from './cryptoVault';

declare global {
  interface Window {
    LikkhoNative?: {
      requestFilesAndMediaPermission?: () => void;
      requestMicPermission?: () => void;
      saveExportedFile?: (base64Data: string, filename: string, mimeType: string) => void;
    };
    __handleLikkhoAndroidBack?: () => string;
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
  { ext: 'pdf', label: '.pdf', desc: 'PDF Printable Document' },
  { ext: 'rtf', label: '.rtf', desc: 'Rich Text Format' },
  { ext: 'html', label: '.html', desc: 'Web Page Archive' },
  { ext: 'docx', label: '.docx', desc: 'Microsoft Word Document' },
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

function buildMinimalPdfBlob(heading: string, dateLine: string, bodyText: string): Blob {
  const sanitizePdfText = (s: string) =>
    s
      .replace(/[^\x20-\x7E\n]/g, '')
      .replace(/\\/g, '\\\\')
      .replace(/\(/g, '\\(')
      .replace(/\)/g, '\\)');

  const rawLines = bodyText.split(/\r?\n/);
  const wrappedLines: string[] = [];
  for (const line of rawLines) {
    const clean = sanitizePdfText(line);
    if (clean.length <= 80) {
      wrappedLines.push(clean);
    } else {
      for (let i = 0; i < clean.length; i += 80) {
        wrappedLines.push(clean.slice(i, i + 80));
      }
    }
  }

  let stream = 'BT\n';
  stream += '/F1 18 Tf\n50 760 Td\n';
  stream += `(${sanitizePdfText(heading || 'Untitled')}) Tj\n`;
  stream += '/F1 10 Tf\n0 -20 Td\n';
  stream += `(${sanitizePdfText(dateLine)}) Tj\n`;
  stream += '/F1 11 Tf\n0 -24 Td\n';

  const maxLines = Math.min(wrappedLines.length, 42);
  for (let i = 0; i < maxLines; i++) {
    stream += `(${wrappedLines[i]}) Tj\n0 -15 Td\n`;
  }
  stream += 'ET';

  const objects: string[] = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n',
    `4 0 obj\n<< /Length ${stream.length} >>\nstream\n${stream}\nendstream\nendobj\n`,
    '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman >>\nendobj\n',
  ];

  let pdf = '%PDF-1.4\n';
  const offsets: number[] = [];
  for (const obj of objects) {
    offsets.push(pdf.length);
    pdf += obj;
  }
  const xrefStart = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const off of offsets) {
    pdf += `${String(off).padStart(10, '0')} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;

  return new Blob([pdf], { type: 'application/pdf' });
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
      const wordDoc = `<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
<head><meta charset='utf-8'><title>${escapeXml(log.heading)}</title></head>
<body style="font-family: 'Times New Roman', Georgia, serif; font-size: 12pt;">
  <h1>${escapeXml(log.heading)}</h1>
  <p style="color:#54595d;font-size:10pt;">${escapeXml(dateLine)}</p>
  <hr/>
  ${log.contentHtml}
</body>
</html>`;
      mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
      blob = new Blob(['\ufeff', wordDoc], { type: mimeType });
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
      blob = buildMinimalPdfBlob(log.heading, dateLine, plainText);
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

/**
 * Multi-Format Document Importer for Likkho Canvas
 * Supports importing & editing:
 * TXT, MD, RTF, CSV, JSON, XML, PDF, DOCX, DOC, ODT, HTML, EPUB, LOG
 */

export interface ImportedDocumentResult {
  title: string;
  html: string;
  format: string;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function plainTextToHtml(text: string): string {
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
  if (!normalized) return '<p><br></p>';
  const blocks = normalized.split(/\n{2,}/);
  return blocks
    .map((block) => {
      const lines = block
        .split('\n')
        .map((line) => escapeHtml(line))
        .join('<br>');
      return `<p>${lines}</p>`;
    })
    .join('');
}

function markdownToHtml(md: string): string {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  const out: string[] = [];
  let inList = false;

  const formatInline = (str: string): string => {
    let s = escapeHtml(str);
    s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/\*(.+?)\*/g, '<em>$1</em>');
    s = s.replace(/_(.+?)_/g, '<u>$1</u>');
    s = s.replace(/~~(.+?)~~/g, '<s>$1</s>');
    s = s.replace(/`(.+?)`/g, '<code>$1</code>');
    s = s.replace(
      /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
    );
    return s;
  };

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    if (/^###\s+(.+)/.test(line)) {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
      out.push(`<h3>${formatInline(line.replace(/^###\s+/, ''))}</h3>`);
    } else if (/^##\s+(.+)/.test(line)) {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
      out.push(`<h2>${formatInline(line.replace(/^##\s+/, ''))}</h2>`);
    } else if (/^#\s+(.+)/.test(line)) {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
      out.push(`<h1>${formatInline(line.replace(/^#\s+/, ''))}</h1>`);
    } else if (/^[-*]\s+(.+)/.test(line)) {
      if (!inList) {
        out.push('<ul>');
        inList = true;
      }
      out.push(`<li>${formatInline(line.replace(/^[-*]\s+/, ''))}</li>`);
    } else if (/^>\s*(.+)/.test(line)) {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
      out.push(`<blockquote>${formatInline(line.replace(/^>\s*/, ''))}</blockquote>`);
    } else if (/^---+$/.test(line.trim())) {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
      out.push('<hr>');
    } else if (line.trim().length === 0) {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
    } else {
      if (inList) {
        out.push('</ul>');
        inList = false;
      }
      out.push(`<p>${formatInline(line)}</p>`);
    }
  }
  if (inList) {
    out.push('</ul>');
  }
  return out.join('') || '<p><br></p>';
}

function rtfToHtml(rtf: string): string {
  // Decode RTF unicode escapes \uN? and hex escapes \'xx and paragraph breaks \par / \line
  let text = rtf;
  // Remove header groups like {\fonttbl...}, {\colortbl...}, {\stylesheet...}, {\info...}
  text = text.replace(/\{\\(?:fonttbl|colortbl|stylesheet|info|pict|header|footer)[^{}]*(?:\{[^{}]*\}[^{}]*)*\}/gi, '');
  text = text.replace(/\\par\b\s*/gi, '\n\n');
  text = text.replace(/\\line\b\s*/gi, '\n');
  text = text.replace(/\\tab\b\s*/gi, '\t');
  text = text.replace(/\\u(-?\d+)\??/g, (_, codeStr) => {
    let code = parseInt(codeStr, 10);
    if (code < 0) code += 65536;
    return String.fromCharCode(code);
  });
  text = text.replace(/\\'([0-9a-fA-F]{2})/g, (_, hex) =>
    String.fromCharCode(parseInt(hex, 16))
  );
  // Strip remaining RTF control words and braces
  text = text.replace(/\\[a-zA-Z]+-?\d*\s?/g, '');
  text = text.replace(/[{}]/g, '');
  return plainTextToHtml(text);
}

function csvToHtml(csvText: string): string {
  const lines = csvText
    .replace(/\r\n/g, '\n')
    .split('\n')
    .filter((l) => l.trim().length > 0);
  if (lines.length === 0) return '<p><br></p>';

  const parseCsvRow = (row: string): string[] => {
    const cells: string[] = [];
    let cur = '';
    let inQuotes = false;
    for (let i = 0; i < row.length; i++) {
      const ch = row[i];
      if (ch === '"') {
        if (inQuotes && row[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if ((ch === ',' || ch === '\t') && !inQuotes) {
        cells.push(cur.trim());
        cur = '';
      } else {
        cur += ch;
      }
    }
    cells.push(cur.trim());
    return cells;
  };

  const rows = lines.map(parseCsvRow);
  const htmlRows = rows
    .map((cells, idx) => {
      const tag = idx === 0 ? 'strong' : 'span';
      const formatted = cells
        .map((c) => `<${tag}>${escapeHtml(c)}</${tag}>`)
        .join(' &nbsp;|&nbsp; ');
      return `<p>${formatted}</p>`;
    })
    .join('');

  return htmlRows;
}

function jsonToHtml(rawJson: string): { title?: string; html: string } {
  try {
    const parsed = JSON.parse(rawJson);
    if (parsed && typeof parsed === 'object') {
      if (typeof parsed.contentHtml === 'string' && parsed.contentHtml.trim()) {
        return {
          title: typeof parsed.heading === 'string' ? parsed.heading : undefined,
          html: parsed.contentHtml,
        };
      }
      if (typeof parsed.plainText === 'string' && parsed.plainText.trim()) {
        return {
          title: typeof parsed.heading === 'string' ? parsed.heading : undefined,
          html: plainTextToHtml(parsed.plainText),
        };
      }
    }
    return {
      html: `<pre><code>${escapeHtml(JSON.stringify(parsed, null, 2))}</code></pre>`,
    };
  } catch {
    return { html: plainTextToHtml(rawJson) };
  }
}

function xmlToHtml(rawXml: string): { title?: string; html: string } {
  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(rawXml, 'text/xml');
    const cdataHtml = doc.querySelector('contentHtml')?.textContent;
    const heading = doc.querySelector('heading')?.textContent;
    if (cdataHtml && cdataHtml.trim()) {
      return {
        title: heading || undefined,
        html: cdataHtml,
      };
    }
    const plain = doc.querySelector('plainText')?.textContent;
    if (plain && plain.trim()) {
      return {
        title: heading || undefined,
        html: plainTextToHtml(plain),
      };
    }
    const textContent = doc.documentElement?.textContent || rawXml;
    return { html: plainTextToHtml(textContent) };
  } catch {
    return { html: plainTextToHtml(rawXml) };
  }
}

function sanitizeImportedHtml(rawHtml: string): { title?: string; html: string } {
  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(rawHtml, 'text/html');
    doc.querySelectorAll('script, style, iframe, object, embed').forEach((el) => el.remove());
    const titleEl = doc.querySelector('h1.title') || doc.querySelector('title');
    const title = titleEl?.textContent?.trim() || undefined;
    const contentDiv = doc.querySelector('.content') || doc.body;
    const bodyHtml = contentDiv?.innerHTML?.trim() || '';
    return {
      title,
      html: bodyHtml || plainTextToHtml(doc.body?.textContent || rawHtml),
    };
  } catch {
    return { html: plainTextToHtml(rawHtml) };
  }
}

/**
 * Minimal Pure-JS ZIP Extractor using Web DecompressionStream('deflate-raw')
 * Used to unpack .docx (word/document.xml), .odt (content.xml), and .epub (.xhtml/.html chapters)
 */
async function extractZipEntriesAsText(
  buffer: ArrayBuffer,
  matcher: (filename: string) => boolean
): Promise<{ name: string; content: string }[]> {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const results: { name: string; content: string }[] = [];
  const decoder = new TextDecoder('utf-8');

  let offset = 0;
  while (offset + 30 <= bytes.length) {
    const sig = view.getUint32(offset, true);
    if (sig !== 0x04034b50) {
      break;
    }
    const compressionMethod = view.getUint16(offset + 8, true);
    const compressedSize = view.getUint32(offset + 18, true);
    const fileNameLen = view.getUint16(offset + 26, true);
    const extraLen = view.getUint16(offset + 28, true);

    const nameBytes = bytes.subarray(offset + 30, offset + 30 + fileNameLen);
    const fileName = decoder.decode(nameBytes);
    const dataStart = offset + 30 + fileNameLen + extraLen;
    const dataEnd = dataStart + compressedSize;

    if (dataEnd > bytes.length) break;

    if (matcher(fileName) && compressedSize > 0) {
      const rawSlice = bytes.subarray(dataStart, dataEnd);
      try {
        if (compressionMethod === 0) {
          results.push({ name: fileName, content: decoder.decode(rawSlice) });
        } else if (compressionMethod === 8 && typeof DecompressionStream !== 'undefined') {
          const ds = new DecompressionStream('deflate-raw');
          const writer = ds.writable.getWriter();
          writer.write(rawSlice).catch(() => {});
          writer.close().catch(() => {});
          const decompressedBuf = await new Response(ds.readable).arrayBuffer();
          results.push({ name: fileName, content: decoder.decode(decompressedBuf) });
        }
      } catch {
        // skip unreadable entry
      }
    }

    offset = dataEnd;
  }

  return results;
}

async function parseDocxBuffer(buffer: ArrayBuffer): Promise<string> {
  // Check if it's actually an HTML-based .docx exported by Likkho or Word
  const headText = new TextDecoder('utf-8').decode(buffer.slice(0, 512));
  if (headText.includes('<html') || headText.includes('<body') || headText.includes('<!DOCTYPE')) {
    const fullText = new TextDecoder('utf-8').decode(buffer);
    return sanitizeImportedHtml(fullText).html;
  }

  const entries = await extractZipEntriesAsText(
    buffer,
    (name) => name === 'word/document.xml'
  );
  if (entries.length > 0) {
    const xml = entries[0].content;
    const parser = new DOMParser();
    const doc = parser.parseFromString(xml, 'text/xml');
    const paragraphs = Array.from(doc.getElementsByTagName('w:p'));
    const htmlParas = paragraphs
      .map((p) => {
        const texts = Array.from(p.getElementsByTagName('w:t'))
          .map((t) => t.textContent || '')
          .join('');
        return texts.trim() ? `<p>${escapeHtml(texts)}</p>` : '';
      })
      .filter(Boolean)
      .join('');
    if (htmlParas) return htmlParas;
  }

  return extractReadableStringsFromBinary(buffer);
}

async function parseOdtBuffer(buffer: ArrayBuffer): Promise<string> {
  const entries = await extractZipEntriesAsText(
    buffer,
    (name) => name === 'content.xml'
  );
  if (entries.length > 0) {
    const xml = entries[0].content;
    const parser = new DOMParser();
    const doc = parser.parseFromString(xml, 'text/xml');
    const nodes = Array.from(doc.querySelectorAll('p, h, text\\:p, text\\:h'));
    if (nodes.length > 0) {
      const html = nodes
        .map((n) => {
          const txt = (n.textContent || '').trim();
          return txt ? `<p>${escapeHtml(txt)}</p>` : '';
        })
        .filter(Boolean)
        .join('');
      if (html) return html;
    }
    const fallbackText = doc.documentElement?.textContent || '';
    if (fallbackText.trim()) return plainTextToHtml(fallbackText);
  }
  return extractReadableStringsFromBinary(buffer);
}

async function parseEpubBuffer(buffer: ArrayBuffer): Promise<string> {
  const entries = await extractZipEntriesAsText(buffer, (name) =>
    /\.(xhtml|html|htm)$/i.test(name)
  );
  if (entries.length > 0) {
    const chapters = entries
      .map((entry) => sanitizeImportedHtml(entry.content).html)
      .filter((h) => h && h !== '<p><br></p>');
    if (chapters.length > 0) {
      return chapters.join('<hr>');
    }
  }
  return extractReadableStringsFromBinary(buffer);
}

async function parsePdfBuffer(buffer: ArrayBuffer): Promise<string> {
  const rawLatin1 = new TextDecoder('iso-8859-1').decode(buffer);
  const extractedLines: string[] = [];

  // Extract parenthesized strings inside BT ... ET blocks
  const btBlocks = rawLatin1.match(/BT[\s\S]*?ET/g) || [];
  for (const block of btBlocks) {
    const strMatches = block.match(/\((?:\\.|[^\\()])*\)\s*Tj/g) || [];
    for (const m of strMatches) {
      const inner = m
        .replace(/\)\s*Tj$/, '')
        .replace(/^\(/, '')
        .replace(/\\\(/g, '(')
        .replace(/\\\)/g, ')')
        .replace(/\\\\/g, '\\');
      if (inner.trim()) {
        extractedLines.push(inner);
      }
    }
    const arrayMatches = block.match(/\[[\s\S]*?\]\s*TJ/g) || [];
    for (const arr of arrayMatches) {
      const parts = arr.match(/\((?:\\.|[^\\()])*\)/g) || [];
      const line = parts
        .map((p) =>
          p
            .slice(1, -1)
            .replace(/\\\(/g, '(')
            .replace(/\\\)/g, ')')
            .replace(/\\\\/g, '\\')
        )
        .join('');
      if (line.trim()) {
        extractedLines.push(line);
      }
    }
  }

  if (extractedLines.length > 0) {
    return plainTextToHtml(extractedLines.join('\n'));
  }

  return extractReadableStringsFromBinary(buffer);
}

function extractReadableStringsFromBinary(buffer: ArrayBuffer): string {
  const utf8 = new TextDecoder('utf-8', { fatal: false }).decode(buffer);
  if (utf8.includes('<html') || utf8.includes('<body')) {
    return sanitizeImportedHtml(utf8).html;
  }

  const bytes = new Uint8Array(buffer);
  const chunks: string[] = [];
  let current = '';
  for (let i = 0; i < bytes.length; i++) {
    const b = bytes[i];
    if ((b >= 32 && b <= 126) || b === 10 || b === 13 || b === 9) {
      current += String.fromCharCode(b);
    } else {
      if (current.trim().length >= 4) {
        chunks.push(current.trim());
      }
      current = '';
    }
  }
  if (current.trim().length >= 4) {
    chunks.push(current.trim());
  }

  const cleaned = chunks
    .filter(
      (c) =>
        !/^(obj|endobj|stream|endstream|xref|trailer|startxref|Content_Types|_rels)$/i.test(
          c
        )
    )
    .join('\n');

  return plainTextToHtml(cleaned || 'Imported document.');
}

export async function parseImportedFileToHtml(
  file: File
): Promise<ImportedDocumentResult> {
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  const baseTitle = file.name.replace(/\.[^.]+$/, '');

  if (ext === 'txt' || ext === 'log') {
    const text = await file.text();
    return {
      title: baseTitle,
      html: plainTextToHtml(text),
      format: ext.toUpperCase(),
    };
  }

  if (ext === 'md') {
    const text = await file.text();
    return {
      title: baseTitle,
      html: markdownToHtml(text),
      format: 'MD',
    };
  }

  if (ext === 'rtf') {
    const text = await file.text();
    return {
      title: baseTitle,
      html: rtfToHtml(text),
      format: 'RTF',
    };
  }

  if (ext === 'csv' || ext === 'tsv') {
    const text = await file.text();
    return {
      title: baseTitle,
      html: csvToHtml(text),
      format: ext.toUpperCase(),
    };
  }

  if (ext === 'json') {
    const text = await file.text();
    const res = jsonToHtml(text);
    return {
      title: res.title || baseTitle,
      html: res.html,
      format: 'JSON',
    };
  }

  if (ext === 'xml') {
    const text = await file.text();
    const res = xmlToHtml(text);
    return {
      title: res.title || baseTitle,
      html: res.html,
      format: 'XML',
    };
  }

  if (ext === 'html' || ext === 'htm') {
    const text = await file.text();
    const res = sanitizeImportedHtml(text);
    return {
      title: res.title || baseTitle,
      html: res.html,
      format: 'HTML',
    };
  }

  const buffer = await file.arrayBuffer();

  if (ext === 'docx') {
    return {
      title: baseTitle,
      html: await parseDocxBuffer(buffer),
      format: 'DOCX',
    };
  }

  if (ext === 'odt') {
    return {
      title: baseTitle,
      html: await parseOdtBuffer(buffer),
      format: 'ODT',
    };
  }

  if (ext === 'epub') {
    return {
      title: baseTitle,
      html: await parseEpubBuffer(buffer),
      format: 'EPUB',
    };
  }

  if (ext === 'pdf') {
    return {
      title: baseTitle,
      html: await parsePdfBuffer(buffer),
      format: 'PDF',
    };
  }

  if (ext === 'doc') {
    return {
      title: baseTitle,
      html: extractReadableStringsFromBinary(buffer),
      format: 'DOC',
    };
  }

  // Fallback for any other text-like file
  const fallbackText = await file.text();
  return {
    title: baseTitle,
    html: plainTextToHtml(fallbackText),
    format: ext.toUpperCase() || 'FILE',
  };
}

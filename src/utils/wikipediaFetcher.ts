/**
 * Wikipedia Structured Article Importer
 * Uses the hardcoded free Wikipedia MediaWiki REST API to search and import structured,
 * clean, fully-editable HTML content (with optional images) directly onto the Canvas.
 */

const WIKIPEDIA_REST_V1_BASE = 'https://en.wikipedia.org/api/rest_v1';
const WIKIPEDIA_CORE_REST_SEARCH = 'https://en.wikipedia.org/w/rest.php/v1/search/title';

export interface WikipediaStructuredResult {
  title: string;
  html: string;
}

/**
 * Resolves the best-matching canonical Wikipedia page title for a user query.
 */
async function resolveWikipediaTitle(query: string): Promise<string> {
  const trimmed = query.trim();
  const searchUrl = `${WIKIPEDIA_CORE_REST_SEARCH}?q=${encodeURIComponent(trimmed)}&limit=1`;
  const res = await fetch(searchUrl, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (res.ok) {
    const data = await res.json();
    if (data && Array.isArray(data.pages) && data.pages.length > 0) {
      return data.pages[0].key || data.pages[0].title || trimmed;
    }
  }
  return trimmed;
}

/**
 * Cleans and transforms raw MediaWiki REST API HTML into clean, structured,
 * natively editable canvas HTML (headings, paragraphs, lists, blockquotes, tables, and optional images).
 */
function transformWikipediaDomToEditableHtml(
  rawHtml: string,
  articleTitle: string,
  description: string | undefined,
  leadThumbnailUrl: string | undefined,
  includeImages: boolean
): string {
  const parser = new DOMParser();
  const doc = parser.parseFromString(rawHtml, 'text/html');

  // Remove unwanted Wikipedia UI, citations, edit buttons, navboxes, metadata, and hidden elements
  const unwantedSelectors = [
    'script',
    'style',
    'link',
    'meta',
    'base',
    'noscript',
    'sup.reference',
    '.mw-ref',
    '.mw-editsection',
    '.reflist',
    '.mw-references-wrap',
    'ol.references',
    '.navbox',
    '.vertical-navbox',
    '.sidebar',
    '.sistersitebox',
    '.ambox',
    '.tmbox',
    '.cmbox',
    '.ombox',
    '.fmbox',
    '.mbox-small',
    '.hatnote',
    '.shortdescription',
    '.portal',
    '.noprint',
    '.mw-empty-elt',
    '.authority-control',
    '.catlinks',
    'audio',
    'video',
  ];

  doc.querySelectorAll(unwantedSelectors.join(',')).forEach((el) => el.remove());

  // Remove reference/external/see-also trailing sections if marked by section headers
  doc.querySelectorAll('section').forEach((sec) => {
    const heading = sec.querySelector('h2, h3');
    if (heading) {
      const hText = (heading.textContent || '').trim().toLowerCase();
      if (
        hText === 'references' ||
        hText === 'external links' ||
        hText === 'further reading' ||
        hText === 'notes' ||
        hText === 'citations'
      ) {
        sec.remove();
      }
    }
  });

  const outputBlocks: string[] = [];

  // 1. Main Article Title & Short Description Header
  const safeTitle = escapeHtml(articleTitle);
  outputBlocks.push(`<h1>${safeTitle}</h1>`);
  if (description && description.trim().length > 0) {
    outputBlocks.push(`<blockquote>${escapeHtml(description.trim())}</blockquote>`);
  }

  // Track added image URLs so we don't duplicate the lead image
  const addedImageUrls = new Set<string>();

  if (includeImages && leadThumbnailUrl) {
    const normalizedLead = normalizeWikiUrl(leadThumbnailUrl);
    addedImageUrls.add(normalizedLead);
    outputBlocks.push(
      `<p><img src="${escapeHtml(normalizedLead)}" alt="${safeTitle}" style="max-width:100%;height:auto;display:block;margin:10px 0;border-radius:2px;" /></p>`
    );
  }

  // Helper to convert an inline node to clean HTML string (preserving bold, italic, code, sub, sup, br)
  const serializeInlineNodes = (node: Node): string => {
    if (node.nodeType === Node.TEXT_NODE) {
      return escapeHtml(node.textContent || '');
    }
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return '';
    }
    const el = node as HTMLElement;
    const tag = el.tagName.toLowerCase();

    // Skip bracketed citation numbers like [1], [citation needed]
    if (tag === 'sup' && /^\s*\[.*\]\s*$/.test(el.textContent || '')) {
      return '';
    }

    const inner = Array.from(el.childNodes).map(serializeInlineNodes).join('');
    if (!inner.trim() && tag !== 'br') return inner;

    if (tag === 'b' || tag === 'strong') return `<strong>${inner}</strong>`;
    if (tag === 'i' || tag === 'em') return `<em>${inner}</em>`;
    if (tag === 'u') return `<u>${inner}</u>`;
    if (tag === 's' || tag === 'del') return `<s>${inner}</s>`;
    if (tag === 'sub') return `<sub>${inner}</sub>`;
    if (tag === 'sup') return `<sup>${inner}</sup>`;
    if (tag === 'code') return `<code>${inner}</code>`;
    if (tag === 'br') return '<br>';
    // Convert links to normal editable text so tapping inside canvas edits text instead of navigating away
    return inner;
  };

  // Helper to extract an image from figure/thumb/img element when includeImages is true
  const extractImageBlock = (el: HTMLElement): string | null => {
    if (!includeImages) return null;
    const img = el.tagName.toLowerCase() === 'img' ? (el as HTMLImageElement) : el.querySelector('img');
    if (!img) return null;

    const rawSrc = img.getAttribute('src') || img.getAttribute('data-src') || '';
    if (!rawSrc) return null;

    // Ignore tiny icons, math formulas, or UI sprites
    const widthAttr = parseInt(img.getAttribute('width') || '0', 10);
    const heightAttr = parseInt(img.getAttribute('height') || '0', 10);
    if ((widthAttr > 0 && widthAttr < 45) || (heightAttr > 0 && heightAttr < 45)) {
      return null;
    }
    if (rawSrc.includes('Special:FilePath') || rawSrc.includes('/math/')) {
      return null;
    }

    const fullSrc = normalizeWikiUrl(rawSrc);
    if (addedImageUrls.has(fullSrc)) return null;
    addedImageUrls.add(fullSrc);

    const captionEl = el.querySelector('figcaption, .thumbcaption');
    const captionText = captionEl ? (captionEl.textContent || '').trim() : '';
    const altText = escapeHtml(img.getAttribute('alt') || articleTitle);

    let html = `<p><img src="${escapeHtml(fullSrc)}" alt="${altText}" style="max-width:100%;height:auto;display:block;margin:10px 0;border-radius:2px;" />`;
    if (captionText) {
      html += `<em>${escapeHtml(captionText)}</em>`;
    }
    html += `</p>`;
    return html;
  };

  // Walk the document body in reading order and build clean structured blocks
  const processElement = (el: HTMLElement) => {
    const tag = el.tagName.toLowerCase();

    if (tag === 'figure' || el.classList.contains('thumb')) {
      const imgHtml = extractImageBlock(el);
      if (imgHtml) outputBlocks.push(imgHtml);
      return;
    }

    if (tag === 'h1' || tag === 'h2') {
      const text = (el.textContent || '').trim();
      if (text && text.toLowerCase() !== articleTitle.toLowerCase()) {
        outputBlocks.push(`<h2>${escapeHtml(text)}</h2>`);
      }
      return;
    }

    if (tag === 'h3' || tag === 'h4' || tag === 'h5' || tag === 'h6') {
      const text = (el.textContent || '').trim();
      if (text) {
        outputBlocks.push(`<h3>${escapeHtml(text)}</h3>`);
      }
      return;
    }

    if (tag === 'p') {
      if (includeImages) {
        el.querySelectorAll('img').forEach((inlineImg) => {
          const imgHtml = extractImageBlock(inlineImg);
          if (imgHtml) outputBlocks.push(imgHtml);
          inlineImg.remove();
        });
      }
      const inner = serializeInlineNodes(el).trim();
      if (inner) {
        outputBlocks.push(`<p>${inner}</p>`);
      }
      return;
    }

    if (tag === 'blockquote') {
      const inner = serializeInlineNodes(el).trim();
      if (inner) {
        outputBlocks.push(`<blockquote>${inner}</blockquote>`);
      }
      return;
    }

    if (tag === 'ul' || tag === 'ol') {
      const items: string[] = [];
      Array.from(el.children).forEach((child) => {
        if (child.tagName.toLowerCase() === 'li') {
          const liHtml = serializeInlineNodes(child).trim();
          if (liHtml) {
            items.push(`<li>${liHtml}</li>`);
          }
        }
      });
      if (items.length > 0) {
        outputBlocks.push(`<${tag}>${items.join('')}</${tag}>`);
      }
      return;
    }

    if (tag === 'dl') {
      const items: string[] = [];
      Array.from(el.children).forEach((child) => {
        const cTag = child.tagName.toLowerCase();
        const text = serializeInlineNodes(child).trim();
        if (!text) return;
        if (cTag === 'dt') {
          items.push(`<p><strong>${text}</strong></p>`);
        } else if (cTag === 'dd') {
          items.push(`<p>${text}</p>`);
        }
      });
      if (items.length > 0) {
        outputBlocks.push(items.join(''));
      }
      return;
    }

    if (tag === 'table' && el.classList.contains('infobox')) {
      // Extract infobox lead image if images are enabled
      if (includeImages) {
        const infoboxImg = el.querySelector('img');
        if (infoboxImg) {
          const imgHtml = extractImageBlock(infoboxImg);
          if (imgHtml) outputBlocks.push(imgHtml);
        }
      }
      // Extract key facts from infobox rows as a structured list
      const facts: string[] = [];
      el.querySelectorAll('tr').forEach((row) => {
        const th = row.querySelector('th');
        const td = row.querySelector('td');
        if (th && td) {
          const label = (th.textContent || '').replace(/\s+/g, ' ').trim();
          const val = (td.textContent || '').replace(/\s+/g, ' ').trim();
          if (label && val) {
            facts.push(`<li><strong>${escapeHtml(label)}:</strong> ${escapeHtml(val)}</li>`);
          }
        }
      });
      if (facts.length > 0) {
        outputBlocks.push(`<ul>${facts.join('')}</ul>`);
      }
      return;
    }

    // Recurse into containers (body, section, div, article, main)
    Array.from(el.children).forEach((child) => {
      processElement(child as HTMLElement);
    });
  };

  processElement(doc.body);

  return outputBlocks.join('\n');
}

function normalizeWikiUrl(url: string): string {
  const trimmed = url.trim();
  if (trimmed.startsWith('//')) {
    return `https:${trimmed}`;
  }
  if (trimmed.startsWith('/')) {
    return `https://en.wikipedia.org${trimmed}`;
  }
  return trimmed;
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Searches Wikipedia for the given topic/word and returns structured, editable HTML
 * for the Canvas, with or without images.
 */
export async function fetchWikipediaStructuredTopic(
  topicQuery: string,
  includeImages: boolean
): Promise<WikipediaStructuredResult> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw new Error('No internet connection.');
  }

  const canonicalKey = await resolveWikipediaTitle(topicQuery);
  const encodedTitle = encodeURIComponent(canonicalKey.replace(/ /g, '_'));

  // Fetch summary + full structured HTML in parallel from Wikipedia MediaWiki REST API
  const summaryPromise = fetch(`${WIKIPEDIA_REST_V1_BASE}/page/summary/${encodedTitle}`, {
    headers: { Accept: 'application/json' },
  }).then((r) => (r.ok ? r.json() : null)).catch(() => null);

  const htmlResponse = await fetch(`${WIKIPEDIA_REST_V1_BASE}/page/html/${encodedTitle}`, {
    headers: { Accept: 'text/html; charset=utf-8' },
  });

  const summaryData = await summaryPromise;

  if (!htmlResponse.ok && !summaryData) {
    throw new Error('Topic not found on Wikipedia.');
  }

  const displayTitle: string =
    (summaryData && (summaryData.titles?.normalized || summaryData.title)) ||
    canonicalKey.replace(/_/g, ' ');
  const description: string | undefined = summaryData?.description;
  const leadThumb: string | undefined =
    summaryData?.originalimage?.source || summaryData?.thumbnail?.source;

  if (htmlResponse.ok) {
    const rawHtml = await htmlResponse.text();
    const structuredHtml = transformWikipediaDomToEditableHtml(
      rawHtml,
      displayTitle,
      description,
      leadThumb,
      includeImages
    );
    if (structuredHtml.trim().length > 0) {
      return {
        title: displayTitle,
        html: structuredHtml,
      };
    }
  }

  // Fallback to summary extract if full HTML wasn't available
  if (summaryData && summaryData.extract_html) {
    const blocks: string[] = [`<h1>${escapeHtml(displayTitle)}</h1>`];
    if (description) {
      blocks.push(`<blockquote>${escapeHtml(description)}</blockquote>`);
    }
    if (includeImages && leadThumb) {
      blocks.push(
        `<p><img src="${escapeHtml(normalizeWikiUrl(leadThumb))}" alt="${escapeHtml(displayTitle)}" style="max-width:100%;height:auto;display:block;margin:10px 0;border-radius:2px;" /></p>`
      );
    }
    blocks.push(summaryData.extract_html);
    return {
      title: displayTitle,
      html: blocks.join('\n'),
    };
  }

  throw new Error('Topic not found on Wikipedia.');
}

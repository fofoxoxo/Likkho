/**
 * Wikipedia Structured Article Importer (English & Hindi) + Live Search Suggestions
 * Uses the hardcoded free Wikipedia MediaWiki REST API (`en.wikipedia.org` & `hi.wikipedia.org`)
 * to provide live search disambiguation suggestions and import structured,
 * clean, fully-editable HTML content plus editable canvas images directly onto the Canvas.
 */

export type WikipediaLanguage = 'en' | 'hi';

const WIKIPEDIA_ENDPOINTS: Record<
  WikipediaLanguage,
  {
    restV1Base: string;
    coreRestSearch: string;
    originBase: string;
  }
> = {
  en: {
    restV1Base: 'https://en.wikipedia.org/api/rest_v1',
    coreRestSearch: 'https://en.wikipedia.org/w/rest.php/v1/search/title',
    originBase: 'https://en.wikipedia.org',
  },
  hi: {
    restV1Base: 'https://hi.wikipedia.org/api/rest_v1',
    coreRestSearch: 'https://hi.wikipedia.org/w/rest.php/v1/search/title',
    originBase: 'https://hi.wikipedia.org',
  },
};

export interface WikipediaSuggestionItem {
  id: number | string;
  key: string;
  title: string;
  description: string;
  thumbnailUrl?: string;
}

export interface WikipediaStructuredResult {
  title: string;
  html: string;
  images: string[];
}

/**
 * Fetches live disambiguation/search suggestions as the user types (e.g., "apple" -> Apple Inc. company vs Apple fruit).
 */
export async function fetchWikipediaSuggestions(
  query: string,
  lang: WikipediaLanguage = 'en',
  signal?: AbortSignal
): Promise<WikipediaSuggestionItem[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];

  const endpoints = WIKIPEDIA_ENDPOINTS[lang];
  const searchUrl = `${endpoints.coreRestSearch}?q=${encodeURIComponent(trimmed)}&limit=8`;

  const res = await fetch(searchUrl, {
    headers: { Accept: 'application/json' },
    signal,
  });

  if (!res.ok) return [];
  const data = await res.json();
  if (!data || !Array.isArray(data.pages)) return [];

  return data.pages.map((page: Record<string, any>, idx: number) => {
    const thumbRaw = page.thumbnail?.url || '';
    return {
      id: page.id ?? `${page.key || idx}`,
      key: String(page.key || page.title || trimmed),
      title: String(page.title || page.key || trimmed),
      description: String(page.description || page.excerpt?.replace(/<[^>]+>/g, '') || ''),
      thumbnailUrl: thumbRaw ? normalizeWikiUrl(thumbRaw, lang) : undefined,
    };
  });
}

/**
 * Resolves the best-matching canonical Wikipedia page key for a user query.
 */
async function resolveWikipediaTitle(
  query: string,
  lang: WikipediaLanguage
): Promise<string> {
  const trimmed = query.trim();
  const endpoints = WIKIPEDIA_ENDPOINTS[lang];
  const searchUrl = `${endpoints.coreRestSearch}?q=${encodeURIComponent(trimmed)}&limit=1`;
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
 * natively editable canvas HTML (headings, paragraphs, lists, blockquotes, infobox facts)
 * and extracts full-resolution article images so they can be added as editable Canvas images.
 */
function transformWikipediaDomToEditableHtml(
  rawHtml: string,
  articleTitle: string,
  description: string | undefined,
  leadThumbnailUrl: string | undefined,
  includeImages: boolean,
  lang: WikipediaLanguage
): { html: string; images: string[] } {
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

  // Remove reference/external/see-also trailing sections if marked by section headers (English & Hindi)
  const ignoredHeadings = new Set([
    'references',
    'external links',
    'further reading',
    'notes',
    'citations',
    'सन्दर्भ',
    'संदर्भ',
    'बाहरी कड़ियाँ',
    'इन्हें भी देखें',
    'टिप्पणियाँ',
  ]);

  doc.querySelectorAll('section').forEach((sec) => {
    const heading = sec.querySelector('h2, h3');
    if (heading) {
      const hText = (heading.textContent || '').trim().toLowerCase();
      if (ignoredHeadings.has(hText)) {
        sec.remove();
      }
    }
  });

  const outputBlocks: string[] = [];
  const extractedImages: string[] = [];

  // 1. Main Article Title & Short Description Header
  const safeTitle = escapeHtml(articleTitle);
  outputBlocks.push(`<h1>${safeTitle}</h1>`);
  if (description && description.trim().length > 0) {
    outputBlocks.push(`<blockquote>${escapeHtml(description.trim())}</blockquote>`);
  }

  // Track added image URLs so we don't duplicate images
  const addedImageUrls = new Set<string>();

  if (includeImages && leadThumbnailUrl) {
    const normalizedLead = normalizeWikiUrl(leadThumbnailUrl, lang);
    addedImageUrls.add(normalizedLead);
    extractedImages.push(normalizedLead);
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

    // Skip bracketed citation numbers like [1], [सन्दर्भ आवश्यक]
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
    return inner;
  };

  // Helper to collect an image from figure/thumb/img element when includeImages is true
  const collectEditableImage = (el: HTMLElement): string | null => {
    if (!includeImages) return null;
    const img =
      el.tagName.toLowerCase() === 'img'
        ? (el as HTMLImageElement)
        : el.querySelector('img');
    if (!img) return null;

    const rawSrc = img.getAttribute('src') || img.getAttribute('data-src') || '';
    if (!rawSrc) return null;

    // Ignore tiny icons, math formulas, or UI sprites
    const widthAttr = parseInt(img.getAttribute('width') || '0', 10);
    const heightAttr = parseInt(img.getAttribute('height') || '0', 10);
    if ((widthAttr > 0 && widthAttr < 55) || (heightAttr > 0 && heightAttr < 55)) {
      return null;
    }
    if (
      rawSrc.includes('Special:FilePath') ||
      rawSrc.includes('/math/') ||
      rawSrc.includes('Ambox_') ||
      rawSrc.includes('Question_book') ||
      rawSrc.includes('Wiki_letter')
    ) {
      return null;
    }

    const fullSrc = normalizeWikiUrl(rawSrc, lang);
    if (addedImageUrls.has(fullSrc)) return null;
    addedImageUrls.add(fullSrc);
    if (extractedImages.length < 10) {
      extractedImages.push(fullSrc);
    }

    const captionEl = el.querySelector('figcaption, .thumbcaption');
    const captionText = captionEl ? (captionEl.textContent || '').trim() : '';
    if (captionText) {
      return `<p><em>${escapeHtml(captionText)}</em></p>`;
    }
    return null;
  };

  // Walk the document body in reading order and build clean structured blocks
  const processElement = (el: HTMLElement) => {
    const tag = el.tagName.toLowerCase();

    if (tag === 'figure' || el.classList.contains('thumb')) {
      const captionHtml = collectEditableImage(el);
      if (captionHtml) outputBlocks.push(captionHtml);
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
          collectEditableImage(inlineImg);
          inlineImg.remove();
        });
      } else {
        el.querySelectorAll('img').forEach((inlineImg) => inlineImg.remove());
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
      if (includeImages) {
        const infoboxImg = el.querySelector('img');
        if (infoboxImg) {
          collectEditableImage(infoboxImg);
        }
      }
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

    Array.from(el.children).forEach((child) => {
      processElement(child as HTMLElement);
    });
  };

  processElement(doc.body);

  return {
    html: outputBlocks.join('\n'),
    images: extractedImages,
  };
}

function normalizeWikiUrl(url: string, lang: WikipediaLanguage): string {
  const trimmed = url.trim();
  if (trimmed.startsWith('//')) {
    return `https:${trimmed}`;
  }
  if (trimmed.startsWith('/')) {
    return `${WIKIPEDIA_ENDPOINTS[lang].originBase}${trimmed}`;
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
 * Converts a remote Wikimedia image URL to a self-contained Data URL if CORS allows,
 * falling back to the direct HTTPS URL so it works both online and in the Image Studio.
 */
export async function resolveEditableImageDataUrl(imageUrl: string): Promise<string> {
  try {
    const res = await fetch(imageUrl, { mode: 'cors' });
    if (res.ok) {
      const blob = await res.blob();
      return await new Promise<string>((resolve) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          resolve(typeof reader.result === 'string' ? reader.result : imageUrl);
        };
        reader.onerror = () => resolve(imageUrl);
        reader.readAsDataURL(blob);
      });
    }
  } catch {
    // Fallback to direct URL
  }
  return imageUrl;
}

/**
 * Searches Wikipedia (in English or Hindi) for the given topic/word (or exact canonical key)
 * and returns structured, editable HTML + editable canvas images.
 */
export async function fetchWikipediaStructuredTopic(
  topicQueryOrKey: string,
  includeImages: boolean,
  lang: WikipediaLanguage = 'en',
  isExactKey: boolean = false
): Promise<WikipediaStructuredResult> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw new Error('No internet connection.');
  }

  const endpoints = WIKIPEDIA_ENDPOINTS[lang];
  const canonicalKey = isExactKey
    ? topicQueryOrKey.trim()
    : await resolveWikipediaTitle(topicQueryOrKey, lang);
  const encodedTitle = encodeURIComponent(canonicalKey.replace(/ /g, '_'));

  const summaryPromise = fetch(`${endpoints.restV1Base}/page/summary/${encodedTitle}`, {
    headers: { Accept: 'application/json' },
  })
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null);

  const htmlResponse = await fetch(`${endpoints.restV1Base}/page/html/${encodedTitle}`, {
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
    summaryData?.thumbnail?.source || summaryData?.originalimage?.source;

  if (htmlResponse.ok) {
    const rawHtml = await htmlResponse.text();
    const { html: structuredHtml, images: rawImages } = transformWikipediaDomToEditableHtml(
      rawHtml,
      displayTitle,
      description,
      leadThumb,
      includeImages,
      lang
    );

    const resolvedImages = includeImages
      ? await Promise.all(rawImages.slice(0, 6).map((u) => resolveEditableImageDataUrl(u)))
      : [];

    if (structuredHtml.trim().length > 0) {
      return {
        title: displayTitle,
        html: structuredHtml,
        images: resolvedImages,
      };
    }
  }

  // Fallback to summary extract if full HTML wasn't available
  if (summaryData && summaryData.extract_html) {
    const blocks: string[] = [`<h1>${escapeHtml(displayTitle)}</h1>`];
    if (description) {
      blocks.push(`<blockquote>${escapeHtml(description)}</blockquote>`);
    }
    blocks.push(summaryData.extract_html);

    const fallbackImages: string[] = [];
    if (includeImages && leadThumb) {
      const resolvedLead = await resolveEditableImageDataUrl(
        normalizeWikiUrl(leadThumb, lang)
      );
      fallbackImages.push(resolvedLead);
    }

    return {
      title: displayTitle,
      html: blocks.join('\n'),
      images: fallbackImages,
    };
  }

  throw new Error('Topic not found on Wikipedia.');
}

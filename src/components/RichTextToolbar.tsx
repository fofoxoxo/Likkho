import React, { useEffect, useRef, useState } from 'react';
import {
  Bold,
  Italic,
  Underline,
  Strikethrough,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  CheckSquare,
  Quote,
  Code,
  AlignLeft,
  AlignCenter,
  AlignRight,
  AlignJustify,
  Highlighter,
  Type,
  Minus,
  CalendarClock,
  Undo2,
  Redo2,
  Superscript,
  Subscript,
  Indent,
  Outdent,
  Link2,
  Image as ImageIcon,
  Trash2,
  Mic,
  Square,
  FolderPlus,
  CaseSensitive,
  ALargeSmall,
  Upload,
  EyeOff,
  Lock,
  Unlock,
  FileUp,
  Search,
  Loader2,
  BookOpenCheck,
  Sigma,
} from 'lucide-react';
import {
  MATH_SYMBOL_CATEGORIES,
  MathSymbolItem,
  renderLatexExpressionToHtml,
} from '../utils/latexMathEngine';
import {
  CanvasAudioAttachment,
  CustomFontItem,
  MicRecordingSettings,
  VaultMode,
} from '../utils/cryptoVault';
import {
  finalizeRecordedAudioToDataUrl,
  getMimeTypeForFormat,
} from '../utils/audioRecorder';
import { parseImportedFileToHtml } from '../utils/importFileToCanvas';
import {
  buildSpoilerMaskString,
  encryptSpoilerSecretText,
} from '../utils/spoilerCipher';
import {
  fetchWikipediaStructuredTopic,
  fetchWikipediaSuggestions,
  WikipediaLanguage,
  WikipediaSuggestionItem,
} from '../utils/wikipediaFetcher';
import {
  analyzeWordOnline,
  saveWordAnalysisToStore,
  WordAnalysisRecord,
} from '../utils/wordAnalysisEngine';
import { WordAnalysisModal } from './WordAnalysisModal';

interface RichTextToolbarProps {
  editorRef: React.RefObject<HTMLDivElement | null>;
  onContentChange: () => void;
  onUndoCanvas?: () => void;
  onRedoCanvas?: () => void;
  canvasBgDataUrl: string | null;
  canvasBgOpacity: number;
  onChangeCanvasBg: (dataUrl: string | null, opacity: number) => void;
  onOpenBgImageStudio?: (rawBgImageDataUrl: string) => void;
  onOpenMediaImageStudio: (rawImageDataUrl: string) => void;
  onOpenMediaAudioStudio?: (
    rawAudioDataUrl: string,
    fileName: string,
    ext: string
  ) => void;
  onAddAudioAttachment: (audio: CanvasAudioAttachment) => void;
  customFonts: CustomFontItem[];
  onAddCustomFont: (font: CustomFontItem) => void;
  micSettings: MicRecordingSettings;
  vaultMode?: VaultMode;
  diaryLockPin?: string | null;
  onChangeDiaryLockPin?: (pin: string | null) => void;
  onImportDocument?: (title: string, html: string, editableImages?: string[]) => void;
}

interface ActiveFormats {
  bold: boolean;
  italic: boolean;
  underline: boolean;
  strikeThrough: boolean;
  insertUnorderedList: boolean;
  insertOrderedList: boolean;
  justifyLeft: boolean;
  justifyCenter: boolean;
  justifyRight: boolean;
  justifyFull: boolean;
  superscript: boolean;
  subscript: boolean;
  quote: boolean;
  code: boolean;
  highlight: boolean;
  textColor: boolean;
  h1: boolean;
  h2: boolean;
  h3: boolean;
}

const TEXT_COLORS = [
  '#202122',
  '#54595d',
  '#3366cc',
  '#1d4ed8',
  '#0ea5e9',
  '#0d9488',
  '#14866d',
  '#15803d',
  '#65a30d',
  '#ca8a04',
  '#ac6600',
  '#ea580c',
  '#b32424',
  '#e11d48',
  '#db2777',
  '#9333ea',
  '#6b4ba1',
  '#4f46e5',
  '#78350f',
  'default',
];

const HIGHLIGHT_COLORS = [
  '#fef08a',
  '#fde047',
  '#fed7aa',
  '#fecaca',
  '#fbcfe8',
  '#e9d5ff',
  '#ddd6fe',
  '#c7d2fe',
  '#bfdbfe',
  '#bae6fd',
  '#a5f3fc',
  '#99f6e4',
  '#bbf7d0',
  '#d9f99d',
  '#fef6e7',
  '#eaf3ff',
  '#fee7e6',
  '#d5fdf4',
  '#f3e5f5',
  'transparent',
];

const BUILTIN_FONTS = [
  { id: 'builtin_serif', name: 'Cormorant', family: 'Cormorant Garamond, serif' },
  { id: 'builtin_sans', name: 'Jakarta Sans', family: 'Plus Jakarta Sans, sans-serif' },
  { id: 'builtin_mono', name: 'Monospace', family: 'IBM Plex Mono, monospace' },
];

export const RichTextToolbar: React.FC<RichTextToolbarProps> = ({
  editorRef,
  onContentChange,
  onUndoCanvas,
  onRedoCanvas,
  canvasBgDataUrl,
  canvasBgOpacity,
  onChangeCanvasBg,
  onOpenBgImageStudio,
  onOpenMediaImageStudio,
  onOpenMediaAudioStudio,
  onAddAudioAttachment,
  customFonts,
  onAddCustomFont,
  micSettings,
  vaultMode = 'primary',
  diaryLockPin,
  onChangeDiaryLockPin,
  onImportDocument,
}) => {
  const [showColorPicker, setShowColorPicker] = useState<'text' | 'highlight' | null>(null);
  const [showLinkInput, setShowLinkInput] = useState<boolean>(false);
  const [showBgControl, setShowBgControl] = useState<boolean>(false);
  const [showFontPanel, setShowFontPanel] = useState<boolean>(false);
  const [showSizePanel, setShowSizePanel] = useState<boolean>(false);
  const [showMediaPickerMenu, setShowMediaPickerMenu] = useState<boolean>(false);
  const [showSpoilerPopover, setShowSpoilerPopover] = useState<boolean>(false);
  const [spoilerPin, setSpoilerPin] = useState<string>('');
  const [showDiaryLockPopover, setShowDiaryLockPopover] = useState<boolean>(false);
  const [diaryPinInput, setDiaryPinInput] = useState<string>('');
  const [showWikipediaPopover, setShowWikipediaPopover] = useState<boolean>(false);
  const [wikiSearchQuery, setWikiSearchQuery] = useState<string>('');
  const [wikiIncludeImages, setWikiIncludeImages] = useState<boolean>(true);
  const [wikiLang, setWikiLang] = useState<WikipediaLanguage>('en');
  const [wikiSuggestions, setWikiSuggestions] = useState<WikipediaSuggestionItem[]>([]);
  const [isWikiSuggestionsLoading, setIsWikiSuggestionsLoading] = useState<boolean>(false);
  const [isWikiLoading, setIsWikiLoading] = useState<boolean>(false);

  // Word Analysis Toolbar Popover & Result Modal State
  const [showWordAnalysisPopover, setShowWordAnalysisPopover] = useState<boolean>(false);
  const [wordAnalysisQuery, setWordAnalysisQuery] = useState<string>('');
  const [isWordAnalysisLoading, setIsWordAnalysisLoading] = useState<boolean>(false);
  const [activeWordAnalysisRecord, setActiveWordAnalysisRecord] =
    useState<WordAnalysisRecord | null>(null);

  // LaTeX & Mathematical Symbols Toolbar Popover State
  const [showMathPopover, setShowMathPopover] = useState<boolean>(false);
  const [activeMathCategoryId, setActiveMathCategoryId] = useState<string>(
    MATH_SYMBOL_CATEGORIES[0].id
  );
  const [latexInput, setLatexInput] = useState<string>('');
  const latexInputRef = useRef<HTMLInputElement | null>(null);

  const [linkUrl, setLinkUrl] = useState<string>('https://');
  const [selectedTextPreview, setSelectedTextPreview] = useState<string>('');
  const [hintToast, setHintToast] = useState<string | null>(null);

  // Active Custom Font Family state (null = Default Font active)
  const [activeFontFamily, setActiveFontFamily] = useState<string | null>(null);

  // Text Size px slider state (10px to 48px) and explicit enable/disable state
  const [textPxSize, setTextPxSize] = useState<number>(16);
  const [isTextPxEnabled, setIsTextPxEnabled] = useState<boolean>(false);

  // Voice recording state
  const [isRecording, setIsRecording] = useState<boolean>(false);
  const [recordingSeconds, setRecordingSeconds] = useState<number>(0);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);
  const recordTimerRef = useRef<number | null>(null);
  const recordStreamRef = useRef<MediaStream | null>(null);

  // Track exact selection range inside editor so cursor NEVER jumps to top
  const savedRangeRef = useRef<Range | null>(null);
  const bgFileInputRef = useRef<HTMLInputElement | null>(null);
  const mediaImageInputRef = useRef<HTMLInputElement | null>(null);
  const mediaAudioInputRef = useRef<HTMLInputElement | null>(null);
  const ttfFontInputRef = useRef<HTMLInputElement | null>(null);
  const importDocInputRef = useRef<HTMLInputElement | null>(null);

  const [activeFormats, setActiveFormats] = useState<ActiveFormats>({
    bold: false,
    italic: false,
    underline: false,
    strikeThrough: false,
    insertUnorderedList: false,
    insertOrderedList: false,
    justifyLeft: false,
    justifyCenter: false,
    justifyRight: false,
    justifyFull: false,
    superscript: false,
    subscript: false,
    quote: false,
    code: false,
    highlight: false,
    textColor: false,
    h1: false,
    h2: false,
    h3: false,
  });

  const showBriefHint = (msg: string) => {
    setHintToast(msg);
    window.setTimeout(() => {
      setHintToast((prev) => (prev === msg ? null : prev));
    }, 2400);
  };

  const closeAllPopovers = () => {
    setShowColorPicker(null);
    setShowLinkInput(false);
    setShowBgControl(false);
    setShowFontPanel(false);
    setShowSizePanel(false);
    setShowMediaPickerMenu(false);
    setShowSpoilerPopover(false);
    setShowDiaryLockPopover(false);
    setShowWikipediaPopover(false);
    setShowWordAnalysisPopover(false);
    setShowMathPopover(false);
  };

  const findAncestorTag = (node: Node | null, tags: string[]): HTMLElement | null => {
    if (!editorRef.current) return null;
    let cur: Node | null = node;
    while (cur && cur !== editorRef.current) {
      if (cur.nodeType === Node.ELEMENT_NODE) {
        const el = cur as HTMLElement;
        if (tags.includes(el.tagName.toUpperCase())) {
          return el;
        }
      }
      cur = cur.parentNode;
    }
    return null;
  };

  const findAncestorByAttr = (node: Node | null, attrName: string): HTMLElement | null => {
    if (!editorRef.current) return null;
    let cur: Node | null = node;
    while (cur && cur !== editorRef.current) {
      if (cur.nodeType === Node.ELEMENT_NODE) {
        const el = cur as HTMLElement;
        if (el.hasAttribute(attrName)) {
          return el;
        }
      }
      cur = cur.parentNode;
    }
    return null;
  };

  // Query active formatting state and continuously save exact cursor/selection range
  const checkActiveFormats = () => {
    try {
      const sel = window.getSelection();
      if (!sel || sel.rangeCount === 0 || !editorRef.current) return;

      const currentRange = sel.getRangeAt(0);
      if (
        editorRef.current === currentRange.commonAncestorContainer ||
        editorRef.current.contains(currentRange.commonAncestorContainer)
      ) {
        savedRangeRef.current = currentRange.cloneRange();
        const selStr = sel.toString().trim();
        if (selStr.length > 0) {
          setSelectedTextPreview(selStr);
        }
      }

      const anchor = sel.anchorNode;
      const isQuote = !!findAncestorTag(anchor, ['Q', 'BLOCKQUOTE']);
      const isCode = !!findAncestorTag(anchor, ['CODE', 'PRE']);
      const isHighlight = !!findAncestorTag(anchor, ['MARK']);
      const isColor = !!findAncestorByAttr(anchor, 'data-wiki-color');
      const fontSpan = findAncestorByAttr(anchor, 'data-wiki-font');
      const pxSpan = findAncestorByAttr(anchor, 'data-wiki-px');

      if (fontSpan) {
        setActiveFontFamily(fontSpan.getAttribute('data-wiki-font'));
      } else {
        setActiveFontFamily(null);
      }

      if (pxSpan) {
        const parsedPx = parseInt(pxSpan.getAttribute('data-wiki-px') || '16', 10);
        if (!isNaN(parsedPx)) {
          setTextPxSize(parsedPx);
          setIsTextPxEnabled(true);
        }
      } else {
        setIsTextPxEnabled(false);
      }

      const isH1 = !!findAncestorTag(anchor, ['H1']);
      const isH2 = !!findAncestorTag(anchor, ['H2']);
      const isH3 = !!findAncestorTag(anchor, ['H3']);

      setActiveFormats({
        bold: document.queryCommandState('bold'),
        italic: document.queryCommandState('italic'),
        underline: document.queryCommandState('underline'),
        strikeThrough: document.queryCommandState('strikeThrough'),
        insertUnorderedList: document.queryCommandState('insertUnorderedList'),
        insertOrderedList: document.queryCommandState('insertOrderedList'),
        justifyLeft: document.queryCommandState('justifyLeft'),
        justifyCenter: document.queryCommandState('justifyCenter'),
        justifyRight: document.queryCommandState('justifyRight'),
        justifyFull: document.queryCommandState('justifyFull'),
        superscript: document.queryCommandState('superscript'),
        subscript: document.queryCommandState('subscript'),
        quote: isQuote,
        code: isCode,
        highlight: isHighlight,
        textColor: isColor,
        h1: isH1,
        h2: isH2,
        h3: isH3,
      });
    } catch {
      // Ignore query errors when editor not focused
    }
  };

  useEffect(() => {
    const handleSelectionChange = () => {
      if (!editorRef.current) return;
      const sel = window.getSelection();
      if (
        sel &&
        sel.anchorNode &&
        (editorRef.current === sel.anchorNode ||
          editorRef.current.contains(sel.anchorNode))
      ) {
        checkActiveFormats();
      }
    };

    const editorEl = editorRef.current;
    document.addEventListener('selectionchange', handleSelectionChange);
    editorEl?.addEventListener('keyup', checkActiveFormats);
    editorEl?.addEventListener('mouseup', checkActiveFormats);
    editorEl?.addEventListener('touchend', checkActiveFormats);

    return () => {
      document.removeEventListener('selectionchange', handleSelectionChange);
      editorEl?.removeEventListener('keyup', checkActiveFormats);
      editorEl?.removeEventListener('mouseup', checkActiveFormats);
      editorEl?.removeEventListener('touchend', checkActiveFormats);
    };
  }, [editorRef]);

  const preventFocusLoss = (e: React.MouseEvent | React.TouchEvent) => {
    e.preventDefault();
  };

  const focusEditorWithoutJump = () => {
    if (editorRef.current && document.activeElement !== editorRef.current) {
      editorRef.current.focus({ preventScroll: true });
    }
  };

  const saveCurrentSelection = () => {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0 && editorRef.current) {
      const range = sel.getRangeAt(0);
      if (
        editorRef.current === range.commonAncestorContainer ||
        editorRef.current.contains(range.commonAncestorContainer)
      ) {
        savedRangeRef.current = range.cloneRange();
        setSelectedTextPreview(sel.toString().trim());
      }
    }
  };

  const restoreSavedSelection = (): Range | null => {
    if (!editorRef.current) return null;
    const sel = window.getSelection();
    if (!sel) return null;

    if (sel.rangeCount > 0) {
      const liveRange = sel.getRangeAt(0);
      if (
        editorRef.current === liveRange.commonAncestorContainer ||
        editorRef.current.contains(liveRange.commonAncestorContainer)
      ) {
        if (
          savedRangeRef.current &&
          !savedRangeRef.current.collapsed &&
          liveRange.collapsed &&
          editorRef.current.contains(savedRangeRef.current.commonAncestorContainer)
        ) {
          focusEditorWithoutJump();
          sel.removeAllRanges();
          sel.addRange(savedRangeRef.current);
          return savedRangeRef.current;
        }
        savedRangeRef.current = liveRange.cloneRange();
        return liveRange;
      }
    }

    focusEditorWithoutJump();

    if (
      savedRangeRef.current &&
      (editorRef.current === savedRangeRef.current.commonAncestorContainer ||
        editorRef.current.contains(savedRangeRef.current.commonAncestorContainer))
    ) {
      sel.removeAllRanges();
      sel.addRange(savedRangeRef.current);
      return savedRangeRef.current;
    }

    const endRange = document.createRange();
    endRange.selectNodeContents(editorRef.current);
    endRange.collapse(false);
    sel.removeAllRanges();
    sel.addRange(endRange);
    savedRangeRef.current = endRange.cloneRange();
    return endRange;
  };

  // Unwrap a specific HTML element in-place while keeping its inner contents selected
  const unwrapElement = (el: HTMLElement) => {
    const parent = el.parentNode;
    if (!parent) return;
    const firstChild = el.firstChild;
    const lastChild = el.lastChild;

    while (el.firstChild) {
      parent.insertBefore(el.firstChild, el);
    }
    parent.removeChild(el);

    if (firstChild && lastChild) {
      const sel = window.getSelection();
      if (sel) {
        const range = document.createRange();
        range.setStartBefore(firstChild);
        range.setEndAfter(lastChild);
        sel.removeAllRanges();
        sel.addRange(range);
        savedRangeRef.current = range.cloneRange();
      }
    }
  };

  /**
   * Splits an inline styled element (`el`) RIGHT AT the current cursor position!
   * Even if the cursor is in the MIDDLE of a word written with a custom px size or custom font:
   * - The part before the cursor stays in `el` with its custom style.
   * - The part after the cursor stays in a cloned `el` with its custom style.
   * - Right at the cursor (between before and after), a clean default-styled text node is placed so
   *   anything typed from this exact cursor position uses the DEFAULT font / DEFAULT px size!
   */
  const splitElementAtCursorToDefault = (
    el: HTMLElement,
    cloneAttrName?: string
  ) => {
    const sel = window.getSelection();
    if (!sel || !el.parentNode) return;

    const cleanText = (el.textContent || '').replace(/\u200B/g, '');
    if (cleanText.length === 0) {
      const zwsp = document.createTextNode('\u200B');
      el.parentNode.replaceChild(zwsp, el);
      const newRange = document.createRange();
      newRange.setStart(zwsp, 1);
      newRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(newRange);
      savedRangeRef.current = newRange.cloneRange();
      return;
    }

    if (sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      const afterRange = document.createRange();
      afterRange.setStart(range.endContainer, range.endOffset);
      afterRange.setEndAfter(el);

      // Extract everything after the cursor out of `el`
      const trailingFragment = afterRange.extractContents();

      // Create the default-styled cursor anchor node right after `el`
      const zwsp = document.createTextNode('\u200B');
      const parent = el.parentNode;
      const nextSib = el.nextSibling;

      if (nextSib) {
        parent.insertBefore(zwsp, nextSib);
      } else {
        parent.appendChild(zwsp);
      }

      // If there was text after the cursor (e.g. cursor was in the middle of a word),
      // wrap that trailing part in a clone of `el` so the rest of that word keeps its original px/font!
      const trailingClean = (trailingFragment.textContent || '').replace(/\u200B/g, '');
      if (trailingClean.length > 0) {
        let trailingNodeToInsert: Node = trailingFragment;
        // Check if extractContents already cloned `el` as top-level child
        if (
          trailingFragment.childNodes.length === 1 &&
          trailingFragment.firstChild?.nodeType === Node.ELEMENT_NODE &&
          (trailingFragment.firstChild as HTMLElement).tagName === el.tagName
        ) {
          trailingNodeToInsert = trailingFragment;
        } else if (cloneAttrName) {
          const clone = el.cloneNode(false) as HTMLElement;
          clone.appendChild(trailingFragment);
          trailingNodeToInsert = clone;
        }

        if (zwsp.nextSibling) {
          parent.insertBefore(trailingNodeToInsert, zwsp.nextSibling);
        } else {
          parent.appendChild(trailingNodeToInsert);
        }
      }

      // If `el` before the cursor became empty (cursor was at the very start of the word), remove empty `el`
      const beforeClean = (el.textContent || '').replace(/\u200B/g, '');
      if (beforeClean.length === 0) {
        parent.removeChild(el);
      }

      const newRange = document.createRange();
      newRange.setStart(zwsp, 1);
      newRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(newRange);
      savedRangeRef.current = newRange.cloneRange();
    }
  };

  /**
   * Selection-Scoped Toggle for Quotation ('Q') and Code Snippet ('CODE')
   */
  const handleToggleSelectionWrapper = (mode: 'quote' | 'code') => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const targetTags = mode === 'quote' ? ['Q', 'BLOCKQUOTE'] : ['CODE', 'PRE'];

    const existingAncestor =
      findAncestorTag(range.commonAncestorContainer, targetTags) ||
      findAncestorTag(range.startContainer, targetTags) ||
      findAncestorTag(range.endContainer, targetTags);

    if (existingAncestor) {
      if (range.collapsed) {
        splitElementAtCursorToDefault(existingAncestor);
      } else {
        unwrapElement(existingAncestor);
      }
      checkActiveFormats();
      onContentChange();
      return;
    }

    if (range.collapsed || sel.toString().trim().length === 0) {
      showBriefHint(
        mode === 'quote'
          ? 'Select text first to apply Quotation'
          : 'Select text first to apply Code Snippet'
      );
      return;
    }

    const wrapper =
      mode === 'quote' ? document.createElement('q') : document.createElement('code');
    if (mode === 'quote') {
      wrapper.className = 'wiki-inline-quote';
    }

    const contents = range.extractContents();
    wrapper.appendChild(contents);
    range.insertNode(wrapper);

    const newRange = document.createRange();
    newRange.selectNodeContents(wrapper);
    sel.removeAllRanges();
    sel.addRange(newRange);
    savedRangeRef.current = newRange.cloneRange();

    checkActiveFormats();
    onContentChange();
  };

  /**
   * Selection-Scoped Text Color:
   * - Wraps ONLY the selected text in `<span data-wiki-color="...">`.
   * - Ensures unstyled boundary text nodes exist immediately before and after the colored span and places the cursor OUTSIDE the span after applying, so typing at the start or end of the word never bleeds the text color!
   */
  const applySelectionTextColor = (color: string) => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const existingColorSpan =
      findAncestorByAttr(range.commonAncestorContainer, 'data-wiki-color') ||
      findAncestorByAttr(range.startContainer, 'data-wiki-color') ||
      findAncestorByAttr(range.endContainer, 'data-wiki-color');

    if (color === 'default') {
      if (existingColorSpan) {
        if (range.collapsed) {
          splitElementAtCursorToDefault(existingColorSpan, 'data-wiki-color');
        } else {
          unwrapElement(existingColorSpan);
        }
      } else if (!range.collapsed) {
        // Also unwrap any `data-wiki-color` spans contained inside the selected range
        editorRef.current.querySelectorAll('[data-wiki-color]').forEach((node) => {
          if (range.intersectsNode(node)) {
            unwrapElement(node as HTMLElement);
          }
        });
      }
      setShowColorPicker(null);
      checkActiveFormats();
      onContentChange();
      return;
    }

    if (existingColorSpan && range.collapsed) {
      splitElementAtCursorToDefault(existingColorSpan, 'data-wiki-color');
      setShowColorPicker(null);
      checkActiveFormats();
      onContentChange();
      return;
    }

    if (range.collapsed || sel.toString().trim().length === 0) {
      showBriefHint('Select text first to apply Text Color');
      setShowColorPicker(null);
      return;
    }

    let targetSpan: HTMLElement;
    const extracted = range.cloneContents();
    const containsBlockElements = Boolean(
      extracted.querySelector('p, div, h1, h2, h3, li, blockquote, pre')
    );

    if (
      existingColorSpan &&
      !containsBlockElements &&
      existingColorSpan.textContent?.replace(/\u200B/g, '') ===
        sel.toString().replace(/\u200B/g, '')
    ) {
      existingColorSpan.style.color = color;
      existingColorSpan.setAttribute('data-wiki-color', color);
      targetSpan = existingColorSpan;
    } else if (containsBlockElements) {
      // Multi-paragraph or block selection: wrap each text node inside the selection range cleanly in its own inline `data-wiki-color` span so paragraph boundaries never break!
      const textNodesInSelection: Text[] = [];
      const walker = document.createTreeWalker(
        editorRef.current,
        NodeFilter.SHOW_TEXT
      );
      let curNode: Node | null = walker.nextNode();
      while (curNode) {
        if (
          curNode.nodeType === Node.TEXT_NODE &&
          range.intersectsNode(curNode) &&
          (curNode.textContent || '').replace(/\u200B/g, '').length > 0 &&
          !findAncestorByAttr(curNode, 'data-wiki-spoiler') &&
          !findAncestorByAttr(curNode, 'data-wiki-latex')
        ) {
          textNodesInSelection.push(curNode as Text);
        }
        curNode = walker.nextNode();
      }

      let firstWrapped: HTMLElement | null = null;
      let lastWrapped: HTMLElement | null = null;

      textNodesInSelection.forEach((tNode) => {
        const fullText = tNode.textContent || '';
        const startOff = tNode === range.startContainer ? range.startOffset : 0;
        const endOff = tNode === range.endContainer ? range.endOffset : fullText.length;
        if (endOff <= startOff) return;

        let midNode: Text = tNode;
        if (endOff < fullText.length) {
          midNode.splitText(endOff);
        }
        if (startOff > 0) {
          midNode = midNode.splitText(startOff);
        }

        const parentEl = midNode.parentElement;
        if (
          parentEl &&
          parentEl.hasAttribute('data-wiki-color') &&
          parentEl.childNodes.length === 1
        ) {
          parentEl.style.color = color;
          parentEl.setAttribute('data-wiki-color', color);
          if (!firstWrapped) firstWrapped = parentEl;
          lastWrapped = parentEl;
        } else if (midNode.parentNode) {
          const sp = document.createElement('span');
          sp.style.color = color;
          sp.setAttribute('data-wiki-color', color);
          midNode.parentNode.replaceChild(sp, midNode);
          sp.appendChild(midNode);
          if (!firstWrapped) firstWrapped = sp;
          lastWrapped = sp;
        }
      });

      if (firstWrapped && lastWrapped) {
        const nextRange = document.createRange();
        nextRange.setStartBefore(firstWrapped);
        nextRange.setEndAfter(lastWrapped);
        sel.removeAllRanges();
        sel.addRange(nextRange);
        savedRangeRef.current = nextRange.cloneRange();
      }
      setShowColorPicker(null);
      checkActiveFormats();
      onContentChange();
      return;
    } else {
      const span = document.createElement('span');
      span.style.color = color;
      span.setAttribute('data-wiki-color', color);
      span.appendChild(range.extractContents());
      range.insertNode(span);
      targetSpan = span;
    }

    // Ensure unstyled text anchor nodes exist right before and right after `targetSpan`
    // while keeping `targetSpan` selected so the user can change or uncolor the selected text anytime!
    const parent = targetSpan.parentNode;
    if (parent) {
      if (!targetSpan.previousSibling || targetSpan.previousSibling.nodeType !== Node.TEXT_NODE) {
        parent.insertBefore(document.createTextNode('\u200B'), targetSpan);
      }
      if (!targetSpan.nextSibling || targetSpan.nextSibling.nodeType !== Node.TEXT_NODE) {
        parent.insertBefore(document.createTextNode('\u200B'), targetSpan.nextSibling);
      }
    }

    // Strip any nested conflicting `data-wiki-color` spans inside `targetSpan` so changing color always overrides cleanly
    targetSpan.querySelectorAll('[data-wiki-color]').forEach((inner) => {
      const innerEl = inner as HTMLElement;
      innerEl.style.color = '';
      innerEl.removeAttribute('data-wiki-color');
    });

    const newRange = document.createRange();
    newRange.selectNodeContents(targetSpan);
    sel.removeAllRanges();
    sel.addRange(newRange);
    savedRangeRef.current = newRange.cloneRange();

    setShowColorPicker(null);
    checkActiveFormats();
    onContentChange();
  };

  /**
   * Selection-Scoped Highlight (<mark>):
   * - Wraps ONLY the selected text in `<mark>`.
   * - Ensures unstyled boundary text nodes exist immediately before and after the highlight `<mark>` and places the cursor OUTSIDE `<mark>` after applying, so typing at the start or end of the word never bleeds the highlight!
   */
  const applySelectionHighlight = (color: string) => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const existingMark =
      findAncestorTag(range.commonAncestorContainer, ['MARK']) ||
      findAncestorTag(range.startContainer, ['MARK']) ||
      findAncestorTag(range.endContainer, ['MARK']);

    if (color === 'transparent') {
      if (existingMark) {
        if (range.collapsed) {
          splitElementAtCursorToDefault(existingMark);
        } else {
          unwrapElement(existingMark);
        }
      } else if (!range.collapsed) {
        editorRef.current.querySelectorAll('mark').forEach((node) => {
          if (range.intersectsNode(node)) {
            unwrapElement(node as HTMLElement);
          }
        });
      }
      setShowColorPicker(null);
      checkActiveFormats();
      onContentChange();
      return;
    }

    if (existingMark && range.collapsed) {
      splitElementAtCursorToDefault(existingMark);
      setShowColorPicker(null);
      checkActiveFormats();
      onContentChange();
      return;
    }

    if (range.collapsed || sel.toString().trim().length === 0) {
      showBriefHint('Select text first to apply Highlight');
      setShowColorPicker(null);
      return;
    }

    let targetMark: HTMLElement;
    if (existingMark && existingMark.textContent === sel.toString()) {
      existingMark.style.backgroundColor = color;
      targetMark = existingMark;
    } else {
      const mark = document.createElement('mark');
      mark.style.backgroundColor = color;
      mark.style.padding = '0 2px';
      mark.style.borderRadius = '2px';
      mark.style.color = 'inherit';
      const contents = range.extractContents();
      mark.appendChild(contents);
      range.insertNode(mark);
      targetMark = mark;
    }

    // Ensure unstyled text anchor nodes exist right before and right after `targetMark`
    // while keeping `targetMark` selected so the user can change or remove the highlight anytime!
    const parent = targetMark.parentNode;
    if (parent) {
      if (!targetMark.previousSibling || targetMark.previousSibling.nodeType !== Node.TEXT_NODE) {
        parent.insertBefore(document.createTextNode('\u200B'), targetMark);
      }
      if (!targetMark.nextSibling || targetMark.nextSibling.nodeType !== Node.TEXT_NODE) {
        parent.insertBefore(document.createTextNode('\u200B'), targetMark.nextSibling);
      }
    }

    targetMark.querySelectorAll('mark').forEach((inner) => {
      unwrapElement(inner as HTMLElement);
    });

    const newRange = document.createRange();
    newRange.selectNodeContents(targetMark);
    sel.removeAllRanges();
    sel.addRange(newRange);
    savedRangeRef.current = newRange.cloneRange();

    setShowColorPicker(null);
    checkActiveFormats();
    onContentChange();
  };

  /**
   * Enable / Disable Font Family AND Apply to Selected Text while keeping cursor in place
   */
  const handleToggleFontFamily = (fontFamily: string | null) => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const existingFontSpan =
      findAncestorByAttr(range.commonAncestorContainer, 'data-wiki-font') ||
      findAncestorByAttr(range.startContainer, 'data-wiki-font') ||
      findAncestorByAttr(range.endContainer, 'data-wiki-font');

    const hasSelection = !range.collapsed && sel.toString().length > 0;

    const isSameFontOnSelection =
      existingFontSpan &&
      existingFontSpan.getAttribute('data-wiki-font') === fontFamily;

    const isTogglingOff =
      fontFamily === null ||
      (hasSelection && isSameFontOnSelection) ||
      (!hasSelection &&
        (isSameFontOnSelection || activeFontFamily === fontFamily));

    if (isTogglingOff) {
      if (existingFontSpan) {
        if (!hasSelection) {
          splitElementAtCursorToDefault(existingFontSpan, 'data-wiki-font');
        } else {
          unwrapElement(existingFontSpan);
        }
      }
      setActiveFontFamily(null);
      if (sel.rangeCount > 0) {
        savedRangeRef.current = sel.getRangeAt(0).cloneRange();
      }
      onContentChange();
      return;
    }

    if (hasSelection) {
      if (
        existingFontSpan &&
        existingFontSpan.textContent?.replace(/\u200B/g, '') ===
          sel.toString().replace(/\u200B/g, '')
      ) {
        existingFontSpan.style.fontFamily = fontFamily;
        existingFontSpan.setAttribute('data-wiki-font', fontFamily);
        const newRange = document.createRange();
        newRange.selectNodeContents(existingFontSpan);
        sel.removeAllRanges();
        sel.addRange(newRange);
        savedRangeRef.current = newRange.cloneRange();
      } else {
        const span = document.createElement('span');
        span.style.fontFamily = fontFamily;
        span.setAttribute('data-wiki-font', fontFamily);
        const extracted = range.extractContents();
        span.appendChild(extracted);
        span.querySelectorAll('[data-wiki-font]').forEach((inner) => {
          const innerEl = inner as HTMLElement;
          innerEl.style.fontFamily = '';
          innerEl.removeAttribute('data-wiki-font');
        });
        range.insertNode(span);

        const newRange = document.createRange();
        newRange.selectNodeContents(span);
        sel.removeAllRanges();
        sel.addRange(newRange);
        savedRangeRef.current = newRange.cloneRange();
      }
    } else {
      if (existingFontSpan) {
        const clean = (existingFontSpan.textContent || '').replace(/\u200B/g, '');
        if (clean.length === 0) {
          existingFontSpan.style.fontFamily = fontFamily;
          existingFontSpan.setAttribute('data-wiki-font', fontFamily);
          setActiveFontFamily(fontFamily);
          return;
        }
        splitElementAtCursorToDefault(existingFontSpan, 'data-wiki-font');
      }

      const activeRange = sel.getRangeAt(0);
      const span = document.createElement('span');
      span.style.fontFamily = fontFamily;
      span.setAttribute('data-wiki-font', fontFamily);
      const zwsp = document.createTextNode('\u200B');
      span.appendChild(zwsp);
      activeRange.insertNode(span);

      const newRange = document.createRange();
      newRange.setStart(zwsp, 1);
      newRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(newRange);
      savedRangeRef.current = newRange.cloneRange();
    }

    setActiveFontFamily(fontFamily);
    onContentChange();
  };

  /**
   * Apply Text Size in px via Slider (10px to 48px):
   * - Setting the slider to any number locks in that px size for selected text or cursor typing.
   * - Tapping Disable splits the span right at the cursor (even inside the middle of a word!) so typing immediately reverts to default 16px while existing text keeps its px size.
   */
  const handleApplyTextPxSlider = (px: number) => {
    setTextPxSize(px);
    setIsTextPxEnabled(true);

    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const existingPxSpan =
      findAncestorByAttr(range.commonAncestorContainer, 'data-wiki-px') ||
      findAncestorByAttr(range.startContainer, 'data-wiki-px') ||
      findAncestorByAttr(range.endContainer, 'data-wiki-px');

    const hasSelection = !range.collapsed && sel.toString().length > 0;

    if (hasSelection) {
      const clonedFrag = range.cloneContents();
      const containsBlockElements = Boolean(
        clonedFrag.querySelector('p, div, h1, h2, h3, li, blockquote, pre')
      );

      if (
        existingPxSpan &&
        !containsBlockElements &&
        existingPxSpan.textContent?.replace(/\u200B/g, '') ===
          sel.toString().replace(/\u200B/g, '')
      ) {
        existingPxSpan.style.fontSize = `${px}px`;
        existingPxSpan.style.lineHeight = '1.45';
        existingPxSpan.setAttribute('data-wiki-px', String(px));
        // Clear any nested `data-wiki-px` or inline font-size inside `existingPxSpan` so inner spans never block resizing
        existingPxSpan.querySelectorAll('*').forEach((child) => {
          const childEl = child as HTMLElement;
          if (childEl.style && childEl.style.fontSize) {
            childEl.style.fontSize = '';
          }
          if (childEl.hasAttribute('data-wiki-px')) {
            childEl.removeAttribute('data-wiki-px');
          }
        });
        const newRange = document.createRange();
        newRange.selectNodeContents(existingPxSpan);
        sel.removeAllRanges();
        sel.addRange(newRange);
        savedRangeRef.current = newRange.cloneRange();
      } else if (containsBlockElements) {
        // When an entire paragraph or multiple paragraphs/lines are selected:
        // Wrap or update each text node in its own inline `data-wiki-px` span AND clear any conflicting inline fontSize on intermediate spans, so the actual text scales smoothly instead of just changing line gaps!
        const textNodesInSelection: Text[] = [];
        const walker = document.createTreeWalker(
          editorRef.current,
          NodeFilter.SHOW_TEXT
        );
        let curNode: Node | null = walker.nextNode();
        while (curNode) {
          if (
            curNode.nodeType === Node.TEXT_NODE &&
            range.intersectsNode(curNode) &&
            (curNode.textContent || '').replace(/\u200B/g, '').length > 0 &&
            !findAncestorByAttr(curNode, 'data-wiki-spoiler') &&
            !findAncestorByAttr(curNode, 'data-wiki-latex')
          ) {
            textNodesInSelection.push(curNode as Text);
          }
          curNode = walker.nextNode();
        }

        let firstWrapped: HTMLElement | null = null;
        let lastWrapped: HTMLElement | null = null;

        textNodesInSelection.forEach((tNode) => {
          const fullText = tNode.textContent || '';
          const startOff = tNode === range.startContainer ? range.startOffset : 0;
          const endOff = tNode === range.endContainer ? range.endOffset : fullText.length;
          if (endOff <= startOff) return;

          let midNode: Text = tNode;
          if (endOff < fullText.length) {
            midNode.splitText(endOff);
          }
          if (startOff > 0) {
            midNode = midNode.splitText(startOff);
          }

          const parentEl = midNode.parentElement;
          if (
            parentEl &&
            parentEl.hasAttribute('data-wiki-px') &&
            parentEl.childNodes.length === 1
          ) {
            parentEl.style.fontSize = `${px}px`;
            parentEl.style.lineHeight = '1.45';
            parentEl.setAttribute('data-wiki-px', String(px));
            if (!firstWrapped) firstWrapped = parentEl;
            lastWrapped = parentEl;
          } else if (midNode.parentNode) {
            const sp = document.createElement('span');
            sp.style.fontSize = `${px}px`;
            sp.style.lineHeight = '1.45';
            sp.setAttribute('data-wiki-px', String(px));
            midNode.parentNode.replaceChild(sp, midNode);
            sp.appendChild(midNode);
            if (!firstWrapped) firstWrapped = sp;
            lastWrapped = sp;
          }
        });

        if (firstWrapped && lastWrapped) {
          const nextRange = document.createRange();
          nextRange.setStartBefore(firstWrapped);
          nextRange.setEndAfter(lastWrapped);
          sel.removeAllRanges();
          sel.addRange(nextRange);
          savedRangeRef.current = nextRange.cloneRange();
        }
      } else {
        const span = document.createElement('span');
        span.style.fontSize = `${px}px`;
        span.style.lineHeight = '1.45';
        span.setAttribute('data-wiki-px', String(px));
        const extracted = range.extractContents();
        span.appendChild(extracted);
        // Clear any nested `data-wiki-px` or inline font-size on inner elements so increasing/decreasing px size always scales the actual text (not just line gaps!)
        span.querySelectorAll('*').forEach((child) => {
          const childEl = child as HTMLElement;
          if (childEl.style && childEl.style.fontSize) {
            childEl.style.fontSize = '';
          }
          if (childEl.hasAttribute('data-wiki-px')) {
            childEl.removeAttribute('data-wiki-px');
          }
        });
        range.insertNode(span);

        const newRange = document.createRange();
        newRange.selectNodeContents(span);
        sel.removeAllRanges();
        sel.addRange(newRange);
        savedRangeRef.current = newRange.cloneRange();
      }
    } else {
      if (existingPxSpan) {
        const clean = (existingPxSpan.textContent || '').replace(/\u200B/g, '');
        if (clean.length === 0) {
          existingPxSpan.style.fontSize = `${px}px`;
          existingPxSpan.style.lineHeight = '1.45';
          existingPxSpan.setAttribute('data-wiki-px', String(px));
          return;
        }
        splitElementAtCursorToDefault(existingPxSpan, 'data-wiki-px');
      }
      const activeRange = sel.getRangeAt(0);
      const span = document.createElement('span');
      span.style.fontSize = `${px}px`;
      span.style.lineHeight = '1.45';
      span.setAttribute('data-wiki-px', String(px));
      const zwsp = document.createTextNode('\u200B');
      span.appendChild(zwsp);
      activeRange.insertNode(span);

      const newRange = document.createRange();
      newRange.setStart(zwsp, 1);
      newRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(newRange);
      savedRangeRef.current = newRange.cloneRange();
    }

    onContentChange();
  };

  /**
   * Explicitly Disable Custom Text px Size:
   * - Even if the cursor is right in the middle of a word that was typed with px enabled,
   *   splits the `data-wiki-px` span at the exact cursor position and places the cursor in default text mode!
   */
  const handleDisableTextPx = () => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (sel && range && editorRef.current) {
      let existingPxSpan =
        findAncestorByAttr(range.commonAncestorContainer, 'data-wiki-px') ||
        findAncestorByAttr(range.startContainer, 'data-wiki-px') ||
        findAncestorByAttr(range.endContainer, 'data-wiki-px');

      if ( !range.collapsed ) {
        editorRef.current.querySelectorAll('[data-wiki-px]').forEach((node) => {
          if (range.intersectsNode(node)) {
            unwrapElement(node as HTMLElement);
          }
        });
      }
      while (existingPxSpan) {
        if (range.collapsed) {
          splitElementAtCursorToDefault(existingPxSpan, 'data-wiki-px');
        } else {
          unwrapElement(existingPxSpan);
        }
        const nextRange = sel.rangeCount > 0 ? sel.getRangeAt(0) : null;
        existingPxSpan = nextRange
          ? findAncestorByAttr(nextRange.commonAncestorContainer, 'data-wiki-px')
          : null;
      }
    }
    setIsTextPxEnabled(false);
    setTextPxSize(16);
    setShowSizePanel(false);
    onContentChange();
  };

  const handleTtfFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async () => {
      if (typeof reader.result === 'string') {
        const rawDataUrl = reader.result;
        const base64Idx = rawDataUrl.indexOf(',');
        const base64Payload = base64Idx >= 0 ? rawDataUrl.slice(base64Idx + 1) : rawDataUrl;
        const normalizedDataUrl = `data:font/ttf;base64,${base64Payload}`;
        const cleanName = file.name.replace(/\.(ttf|otf|woff2?)$/i, '');
        const familyId = `CustomFont_${Date.now()}`;

        try {
          // 1. Inject global @font-face CSS rule so non-Google / third-party Hindi & English .ttf fonts always apply on Canvas
          const styleId = `likkho-font-style-${familyId}`;
          if (!document.getElementById(styleId)) {
            const styleEl = document.createElement('style');
            styleEl.id = styleId;
            styleEl.textContent = `@font-face { font-family: '${familyId}'; src: url('${normalizedDataUrl}') format('truetype'), url('${rawDataUrl}'); font-weight: normal; font-style: normal; font-display: swap; }`;
            document.head.appendChild(styleEl);
          }

          // 2. Also load binary ArrayBuffer directly into document.fonts
          try {
            const bin = atob(base64Payload);
            const bytes = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) {
              bytes[i] = bin.charCodeAt(i);
            }
            const fontFace = new FontFace(familyId, bytes.buffer);
            const loaded = await fontFace.load();
            document.fonts.add(loaded);
          } catch {
            // @font-face style tag above still handles rendering even if strict OTS parser warns
          }

          const item: CustomFontItem = {
            id: familyId,
            name: cleanName,
            lang: 'custom',
            fontFamily: familyId,
            dataUrl: normalizedDataUrl,
          };
          onAddCustomFont(item);
          handleToggleFontFamily(familyId);
          showBriefHint(`Enabled font: ${cleanName}`);
        } catch {
          showBriefHint('Please select a valid .ttf font file.');
        }
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const breakOutOfInlineTags = (tagNames: string[]) => {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || !sel.isCollapsed || !editorRef.current) return false;

    const targetEl = findAncestorTag(sel.anchorNode, tagNames);
    if (!targetEl) return false;

    splitElementAtCursorToDefault(targetEl);
    return true;
  };

  const execToggleInline = (command: string) => {
    restoreSavedSelection();

    const tagMap: Record<string, string[]> = {
      bold: ['B', 'STRONG'],
      italic: ['I', 'EM'],
      underline: ['U'],
      strikeThrough: ['STRIKE', 'S', 'DEL'],
      superscript: ['SUP'],
      subscript: ['SUB'],
    };

    const wasActive = document.queryCommandState(command);
    document.execCommand(command, false, '');
    const isStillActive = document.queryCommandState(command);

    if (wasActive && isStillActive && tagMap[command]) {
      breakOutOfInlineTags(tagMap[command]);
    }

    checkActiveFormats();
    onContentChange();
  };

  const execToggleHeading = (tag: 'H1' | 'H2' | 'H3') => {
    restoreSavedSelection();
    const sel = window.getSelection();
    const anchor = sel && sel.rangeCount > 0 ? sel.anchorNode : null;
    const existingHeading = findAncestorTag(anchor, [tag]);

    if (existingHeading) {
      const p = document.createElement('p');
      while (existingHeading.firstChild) {
        p.appendChild(existingHeading.firstChild);
      }
      if (!p.firstChild) {
        p.appendChild(document.createElement('br'));
      }
      existingHeading.parentNode?.replaceChild(p, existingHeading);
      if (sel) {
        const range = document.createRange();
        range.selectNodeContents(p);
        range.collapse(false);
        sel.removeAllRanges();
        sel.addRange(range);
        savedRangeRef.current = range.cloneRange();
      }
    } else {
      document.execCommand('formatBlock', false, tag);
    }

    checkActiveFormats();
    onContentChange();
  };

  /**
   * Insert To-Do Checkbox ONLY (no "Task item" placeholder text)
   */
  const insertChecklist = () => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.style.width = '16px';
    checkbox.style.height = '16px';
    checkbox.style.accentColor = '#3366cc';
    checkbox.style.verticalAlign = 'middle';
    checkbox.style.marginRight = '6px';
    checkbox.style.cursor = 'pointer';

    const spaceNode = document.createTextNode('\u00A0');
    const frag = document.createDocumentFragment();
    frag.appendChild(checkbox);
    frag.appendChild(spaceNode);

    range.deleteContents();
    range.insertNode(frag);

    const nextRange = document.createRange();
    nextRange.setStartAfter(spaceNode);
    nextRange.collapse(true);
    sel.removeAllRanges();
    sel.addRange(nextRange);
    savedRangeRef.current = nextRange.cloneRange();

    checkActiveFormats();
    onContentChange();
  };

  /**
   * Insert Date & Time Stamp as an atomic, non-inheriting inline chip (`contentEditable="false"`):
   * - Keeps the exact monospace muted font for the timestamp itself.
   * - Cursor cannot get trapped inside the timestamp's font style, and pressing Enter or typing after it
   *   always writes in the normal default editor font!
   */
  const insertCurrentTimestamp = () => {
    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const now = new Date();
    const formatted = now.toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });

    const stampSpan = document.createElement('span');
    stampSpan.contentEditable = 'false';
    stampSpan.setAttribute('data-wiki-timestamp', 'true');
    stampSpan.style.fontFamily = 'var(--font-mono-wiki)';
    stampSpan.style.fontSize = '0.85em';
    stampSpan.style.color = 'var(--wiki-muted)';
    stampSpan.style.userSelect = 'all';
    stampSpan.textContent = `[${formatted}]`;

    const trailingSpace = document.createTextNode('\u00A0');
    const frag = document.createDocumentFragment();
    frag.appendChild(stampSpan);
    frag.appendChild(trailingSpace);

    range.deleteContents();
    range.insertNode(frag);

    const nextRange = document.createRange();
    nextRange.setStartAfter(trailingSpace);
    nextRange.collapse(true);
    sel.removeAllRanges();
    sel.addRange(nextRange);
    savedRangeRef.current = nextRange.cloneRange();

    checkActiveFormats();
    onContentChange();
  };

  // Start or Stop Microphone Voice Recording — Uses Native Android MicForegroundService (AudioRecord + Overlay) so recording continues even if app is cleared from Recent Apps!
  useEffect(() => {
    window.__onLikkhoNativeMicFinished = async (base64Wav: string, durationSec: number) => {
      if (recordTimerRef.current) {
        window.clearInterval(recordTimerRef.current);
        recordTimerRef.current = null;
      }
      setIsRecording(false);
      if (!base64Wav) return;

      try {
        const binary = atob(base64Wav);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
          bytes[i] = binary.charCodeAt(i);
        }
        const wavBlob = new Blob([bytes], { type: 'audio/wav' });
        const dataUrl = await finalizeRecordedAudioToDataUrl(wavBlob, micSettings);

        const nowMs = Date.now();
        const stampLabel = new Date(nowMs).toLocaleString('en-IN', {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          hour12: true,
        });

        onAddAudioAttachment({
          id: `rec_${nowMs}`,
          name: `Voice (${stampLabel}).${micSettings.format}`,
          format: micSettings.format,
          dataUrl,
          durationSec: Math.max(1, durationSec || 1),
          createdAt: nowMs,
        });
        showBriefHint(`Saved voice recording (.${micSettings.format})`);
      } catch {
        // fallback direct wav data url
        const nowMs = Date.now();
        const stampLabel = new Date(nowMs).toLocaleString('en-IN', {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          hour12: true,
        });
        onAddAudioAttachment({
          id: `rec_${nowMs}`,
          name: `Voice (${stampLabel}).wav`,
          format: 'wav',
          dataUrl: `data:audio/wav;base64,${base64Wav}`,
          durationSec: Math.max(1, durationSec || 1),
          createdAt: nowMs,
        });
        showBriefHint('Saved voice recording (.wav)');
      }
    };

    // If user swiped app from Recent Apps while recording and reopened Likkho, re-attach to active native recording!
    if (
      window.LikkhoNative?.isNativeMicRecordingActive &&
      window.LikkhoNative.isNativeMicRecordingActive()
    ) {
      setIsRecording(true);
      const elapsed = window.LikkhoNative.getNativeMicRecordingSeconds
        ? window.LikkhoNative.getNativeMicRecordingSeconds()
        : 0;
      setRecordingSeconds(elapsed);
      if (recordTimerRef.current) window.clearInterval(recordTimerRef.current);
      recordTimerRef.current = window.setInterval(() => {
        if (
          window.LikkhoNative?.isNativeMicRecordingActive &&
          !window.LikkhoNative.isNativeMicRecordingActive()
        ) {
          if (recordTimerRef.current) {
            window.clearInterval(recordTimerRef.current);
            recordTimerRef.current = null;
          }
          setIsRecording(false);
          return;
        }
        setRecordingSeconds((s) => s + 1);
      }, 1000);
    }

    return () => {
      window.__onLikkhoNativeMicFinished = undefined;
    };
  }, [micSettings, onAddAudioAttachment]);

  const handleToggleMicRecording = async () => {
    if (isRecording) {
      if (window.LikkhoNative?.stopNativeMicRecording) {
        window.LikkhoNative.stopNativeMicRecording();
        return;
      }
      if (window.LikkhoNative?.stopForegroundMicService) {
        window.LikkhoNative.stopForegroundMicService();
      }
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        mediaRecorderRef.current.stop();
      }
      if (recordTimerRef.current) {
        window.clearInterval(recordTimerRef.current);
        recordTimerRef.current = null;
      }
      setIsRecording(false);
      return;
    }

    // Immediately display the Active Mic Recorder Banner on tap so there is zero 4-5 second UI delay!
    setIsRecording(true);
    setRecordingSeconds(0);
    if (recordTimerRef.current) window.clearInterval(recordTimerRef.current);

    // Use Native Android Foreground Service AudioRecord when running in APK (survives Recent Apps clear + shows persistent Notification Bell)
    if (window.LikkhoNative && typeof window.LikkhoNative.startNativeMicRecording === 'function') {
      const invokedAt = Date.now();
      window.LikkhoNative.startNativeMicRecording(micSettings.sampleRate, micSettings.bitRate);
      recordTimerRef.current = window.setInterval(() => {
        // Give the Android ForegroundService a 2.5s grace window to spin up (or prompt permission) before checking active state
        if (
          Date.now() - invokedAt > 2500 &&
          window.LikkhoNative?.isNativeMicRecordingActive &&
          !window.LikkhoNative.isNativeMicRecordingActive()
        ) {
          if (recordTimerRef.current) {
            window.clearInterval(recordTimerRef.current);
            recordTimerRef.current = null;
          }
          setIsRecording(false);
          return;
        }
        setRecordingSeconds((s) => s + 1);
      }, 1000);
      return;
    }

    try {
      if (window.LikkhoNative?.requestMicPermission) {
        window.LikkhoNative.requestMicPermission();
      }

      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        setIsRecording(false);
        showBriefHint('Microphone recording is not supported in this browser.');
        return;
      }

      recordTimerRef.current = window.setInterval(() => {
        setRecordingSeconds((s) => s + 1);
      }, 1000);

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: micSettings.sampleRate,
          echoCancellation: true,
          noiseSuppression: true,
        },
      });
      recordStreamRef.current = stream;
      recordedChunksRef.current = [];

      if (window.LikkhoNative?.startForegroundMicService) {
        window.LikkhoNative.startForegroundMicService();
      }

      const mimeType = getMimeTypeForFormat(micSettings.format);
      const options: MediaRecorderOptions = {
        audioBitsPerSecond: micSettings.bitRate,
      };
      if (mimeType) {
        options.mimeType = mimeType;
      }

      const recorder = new MediaRecorder(stream, options);
      mediaRecorderRef.current = recorder;

      const startedAt = Date.now();
      recorder.ondataavailable = (ev) => {
        if (ev.data && ev.data.size > 0) {
          recordedChunksRef.current.push(ev.data);
        }
      };

      recorder.onstop = async () => {
        if (window.LikkhoNative?.stopForegroundMicService) {
          window.LikkhoNative.stopForegroundMicService();
        }
        const durationSec = Math.max(1, Math.round((Date.now() - startedAt) / 1000));
        const rawBlob = new Blob(recordedChunksRef.current, {
          type: recorder.mimeType || 'audio/webm',
        });
        const dataUrl = await finalizeRecordedAudioToDataUrl(rawBlob, micSettings);

        const nowMs = Date.now();
        const stampLabel = new Date(nowMs).toLocaleString('en-IN', {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          hour12: true,
        });

        onAddAudioAttachment({
          id: `rec_${nowMs}`,
          name: `Voice (${stampLabel}).${micSettings.format}`,
          format: micSettings.format,
          dataUrl,
          durationSec,
          createdAt: nowMs,
        });

        if (recordStreamRef.current) {
          recordStreamRef.current.getTracks().forEach((t) => t.stop());
          recordStreamRef.current = null;
        }
        showBriefHint(`Saved voice recording (.${micSettings.format})`);
      };

      recorder.start(1000);
    } catch {
      if (recordTimerRef.current) {
        window.clearInterval(recordTimerRef.current);
        recordTimerRef.current = null;
      }
      setIsRecording(false);
      showBriefHint('Please allow Microphone permission in Android OS to record audio.');
    }
  };

  const triggerAndroidMediaPermission = () => {
    try {
      if (window.LikkhoNative?.requestFilesAndMediaPermission) {
        window.LikkhoNative.requestFilesAndMediaPermission();
      }
    } catch {
      // ignore
    }
  };

  const handleMediaImagePicked = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setShowMediaPickerMenu(false);
        onOpenMediaImageStudio(reader.result);
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleMediaAudioPicked = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        const ext = (file.name.split('.').pop() || 'wav').toLowerCase();
        setShowMediaPickerMenu(false);
        if (onOpenMediaAudioStudio) {
          onOpenMediaAudioStudio(reader.result, file.name, ext);
        } else {
          onAddAudioAttachment({
            id: `aud_${Date.now()}`,
            name: file.name,
            format: ext,
            dataUrl: reader.result,
            createdAt: Date.now(),
          });
          showBriefHint(`Attached audio: ${file.name}`);
        }
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleToggleLinkPopover = () => {
    if (showLinkInput) {
      setShowLinkInput(false);
      return;
    }
    saveCurrentSelection();
    closeAllPopovers();
    setLinkUrl('https://');
    setShowLinkInput(true);
  };

  const handleApplyLink = (e: React.FormEvent) => {
    e.preventDefault();
    let finalUrl = linkUrl.trim();
    if (!finalUrl) {
      setShowLinkInput(false);
      return;
    }
    if (!/^https?:\/\//i.test(finalUrl) && !/^mailto:/i.test(finalUrl)) {
      finalUrl = 'https://' + finalUrl;
    }

    restoreSavedSelection();
    const sel = window.getSelection();
    const selectedText = sel ? sel.toString() : '';

    if (selectedText && selectedText.trim().length > 0) {
      document.execCommand('createLink', false, finalUrl);
      if (editorRef.current) {
        const anchors = editorRef.current.querySelectorAll('a');
        anchors.forEach((a) => {
          a.setAttribute('target', '_blank');
          a.setAttribute('rel', 'noopener noreferrer');
        });
      }
    } else {
      const safeText = selectedTextPreview || finalUrl;
      document.execCommand(
        'insertHTML',
        false,
        `<a href="${finalUrl}" target="_blank" rel="noopener noreferrer">${safeText}</a>&nbsp;`
      );
    }

    setShowLinkInput(false);
    checkActiveFormats();
    onContentChange();
  };

  const handleBgFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        if (onOpenBgImageStudio) {
          onOpenBgImageStudio(reader.result);
        } else {
          onChangeCanvasBg(reader.result, canvasBgOpacity);
        }
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  // Selection-Scoped Passcode Spoiler Tool
  const handleToggleSpoilerTool = () => {
    saveCurrentSelection();
    const sel = window.getSelection();
    const range = savedRangeRef.current;

    if (range) {
      const existingSpoiler =
        findAncestorByAttr(range.commonAncestorContainer, 'data-wiki-spoiler') ||
        findAncestorByAttr(range.startContainer, 'data-wiki-spoiler') ||
        findAncestorByAttr(range.endContainer, 'data-wiki-spoiler');

      if (existingSpoiler) {
        existingSpoiler.click();
        showBriefHint('Tap spoiler and enter passcode to disable or view it.');
        return;
      }
    }

    if (!sel || !range || range.collapsed || sel.toString().trim().length === 0) {
      showBriefHint('Select text first to apply Spoiler');
      return;
    }

    setSelectedTextPreview(sel.toString().trim().slice(0, 24));
    const next = !showSpoilerPopover;
    closeAllPopovers();
    setSpoilerPin('');
    setShowSpoilerPopover(next);
  };

  const handleApplySpoiler = (e: React.FormEvent) => {
    e.preventDefault();
    const pin = spoilerPin.trim();
    if (!pin) {
      showBriefHint('Please enter a passcode for this spoiler.');
      return;
    }

    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || range.collapsed || !editorRef.current) {
      setShowSpoilerPopover(false);
      return;
    }

    const rawSelectedText = sel.toString();
    const cipherPayload = encryptSpoilerSecretText(rawSelectedText, pin);
    const maskedDisplay = buildSpoilerMaskString(rawSelectedText.length);

    const span = document.createElement('span');
    span.className = 'wiki-spoiler-locked';
    span.contentEditable = 'false';
    span.setAttribute('contenteditable', 'false');
    span.setAttribute('spellcheck', 'false');
    span.setAttribute('data-wiki-spoiler', 'true');
    span.setAttribute('data-spoiler-pin', btoa(unescape(encodeURIComponent(pin))));
    span.setAttribute('data-spoiler-cipher', cipherPayload);
    span.setAttribute('data-spoiler-len', String(rawSelectedText.length));
    span.textContent = maskedDisplay;

    range.deleteContents();
    range.insertNode(span);

    // Ensure plain text nodes exist immediately before and after the non-editable spoiler span
    // without injecting unwanted extra spaces, and place the cursor outside the spoiler span
    // so typing right at the start or right at the end of the spoiler word never extends the spoiler!
    const parent = span.parentNode;
    if (parent) {
      if (!span.previousSibling || span.previousSibling.nodeType !== Node.TEXT_NODE) {
        parent.insertBefore(document.createTextNode('\u200B'), span);
      }
      let afterNode = span.nextSibling;
      if (!afterNode || afterNode.nodeType !== Node.TEXT_NODE) {
        afterNode = document.createTextNode('\u200B');
        parent.insertBefore(afterNode, span.nextSibling);
      }
      const newRange = document.createRange();
      const offset = (afterNode.textContent || '').startsWith('\u200B') ? 1 : 0;
      newRange.setStart(afterNode, offset);
      newRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(newRange);
      savedRangeRef.current = newRange.cloneRange();
    }

    setShowSpoilerPopover(false);
    setSpoilerPin('');
    checkActiveFormats();
    onContentChange();
    showBriefHint('Spoiler applied. Tap blurred text to unlock with passcode.');
  };

  // Individual Diary Lock Tool
  const handleApplyDiaryLock = (e: React.FormEvent) => {
    e.preventDefault();
    const pin = diaryPinInput.trim();
    if (!pin) {
      showBriefHint('Enter a passcode to lock this diary.');
      return;
    }
    if (onChangeDiaryLockPin) {
      onChangeDiaryLockPin(pin);
    }
    setDiaryPinInput('');
    setShowDiaryLockPopover(false);
    showBriefHint('Diary Lock passcode set for this entry.');
  };

  const handleRemoveDiaryLock = () => {
    if (onChangeDiaryLockPin) {
      onChangeDiaryLockPin(null);
    }
    setDiaryPinInput('');
    setShowDiaryLockPopover(false);
    showBriefHint('Diary Lock removed from this entry.');
  };

  // Multi-Format Document Import Tool (TXT, MD, RTF, CSV, JSON, XML, PDF, DOCX, DOC, ODT, HTML, EPUB, LOG)
  const handleImportFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const result = await parseImportedFileToHtml(file);
      if (onImportDocument) {
        onImportDocument(result.title, result.html);
      } else if (editorRef.current) {
        editorRef.current.innerHTML =
          (editorRef.current.innerHTML ? editorRef.current.innerHTML + '<hr>' : '') +
          result.html;
        onContentChange();
      }
      showBriefHint(`Imported ${result.format}: ${file.name}`);
    } catch {
      showBriefHint('Could not import selected file.');
    }
    e.target.value = '';
  };

  // Wikipedia Search & Structured Content Import onto Canvas
  // Live Search Suggestions Dropdown effect for Wikipedia tool (English & Hindi)
  useEffect(() => {
    if (!showWikipediaPopover) {
      setWikiSuggestions([]);
      return;
    }
    const q = wikiSearchQuery.trim();
    if (!q || isWikiLoading) {
      setWikiSuggestions([]);
      setIsWikiSuggestionsLoading(false);
      return;
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      setWikiSuggestions([]);
      return;
    }

    const controller = new AbortController();
    setIsWikiSuggestionsLoading(true);
    const timer = window.setTimeout(async () => {
      try {
        const list = await fetchWikipediaSuggestions(q, wikiLang, controller.signal);
        setWikiSuggestions(list);
      } catch {
        // ignore abort / network error
      } finally {
        setIsWikiSuggestionsLoading(false);
      }
    }, 220);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [wikiSearchQuery, wikiLang, showWikipediaPopover, isWikiLoading]);

  const executeWikipediaImport = async (
    targetQueryOrKey: string,
    isExactKey: boolean = false
  ) => {
    const query = targetQueryOrKey.trim();
    if (!query || isWikiLoading) return;

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      showBriefHint('Internet connection required to search Wikipedia.');
      return;
    }

    setIsWikiLoading(true);
    setWikiSuggestions([]);
    try {
      const result = await fetchWikipediaStructuredTopic(
        query,
        wikiIncludeImages,
        wikiLang,
        isExactKey
      );
      if (onImportDocument) {
        onImportDocument(result.title, result.html, result.images);
      } else if (editorRef.current) {
        const current = editorRef.current.innerHTML.trim();
        editorRef.current.innerHTML = current
          ? `${current}<hr>${result.html}`
          : result.html;
        onContentChange();
      }
      setShowWikipediaPopover(false);
      setWikiSearchQuery('');
      showBriefHint(`Added "${result.title}" to canvas`);
    } catch (err) {
      const msg =
        err instanceof Error && err.message
          ? err.message
          : 'Could not fetch topic. Check internet connection.';
      showBriefHint(msg);
    } finally {
      setIsWikiLoading(false);
    }
  };

  const handleSearchWikipedia = async (e: React.FormEvent) => {
    e.preventDefault();
    await executeWikipediaImport(wikiSearchQuery, false);
  };

  const runWordAnalysis = async (rawWord: string) => {
    const cleaned = rawWord.trim();
    if (!cleaned || isWordAnalysisLoading) return;

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      showBriefHint('Internet connection required for Word Analysis.');
      return;
    }

    setIsWordAnalysisLoading(true);
    try {
      const contextText = editorRef.current?.innerText?.slice(0, 400) || cleaned;
      const analysis = await analyzeWordOnline(cleaned, contextText);
      saveWordAnalysisToStore(analysis, vaultMode);
      setActiveWordAnalysisRecord(analysis);
      setShowWordAnalysisPopover(false);
      setWordAnalysisQuery('');
    } catch (err) {
      const msg =
        err instanceof Error && err.message
          ? err.message
          : 'Could not analyze word. Check internet connection.';
      showBriefHint(msg);
    } finally {
      setIsWordAnalysisLoading(false);
    }
  };

  const handleWordAnalysisSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await runWordAnalysis(wordAnalysisQuery);
  };

  /**
   * Append a symbol's LaTeX snippet into the LaTeX formula builder input,
   * or insert an inline-safe LaTeX formula chip directly into the Canvas without disturbing surrounding words/sentences!
   */
  const handleSelectMathSymbol = (sym: MathSymbolItem) => {
    const inputEl = latexInputRef.current;
    if (inputEl) {
      const start = inputEl.selectionStart ?? latexInput.length;
      const end = inputEl.selectionEnd ?? latexInput.length;
      const nextVal = latexInput.slice(0, start) + sym.latex + latexInput.slice(end);
      setLatexInput(nextVal);
      window.setTimeout(() => {
        inputEl.focus();
        const nextCursor = start + sym.latex.length;
        inputEl.setSelectionRange(nextCursor, nextCursor);
      }, 0);
    } else {
      setLatexInput((prev) => prev + sym.latex);
    }
  };

  const insertLatexFormulaIntoCanvas = (rawFormula: string) => {
    const cleaned = rawFormula.trim();
    if (!cleaned) {
      showBriefHint('Select a math symbol or type a LaTeX formula first.');
      return;
    }

    const range = restoreSavedSelection();
    const sel = window.getSelection();
    if (!sel || !range || !editorRef.current) return;

    const renderedInnerHtml = renderLatexExpressionToHtml(cleaned);
    if (!renderedInnerHtml) return;

    const formulaSpan = document.createElement('span');
    formulaSpan.className = 'wiki-latex-formula';
    formulaSpan.contentEditable = 'false';
    formulaSpan.setAttribute('contenteditable', 'false');
    formulaSpan.setAttribute('spellcheck', 'false');
    formulaSpan.setAttribute('data-wiki-latex', 'true');
    formulaSpan.setAttribute('data-latex-src', cleaned);
    formulaSpan.innerHTML = renderedInnerHtml;

    range.deleteContents();
    range.insertNode(formulaSpan);

    // Ensure clean plain text boundaries before & after the inline formula span
    // so surrounding words/sentences are NEVER disturbed and typing continues in normal text
    const parent = formulaSpan.parentNode;
    if (parent) {
      if (
        !formulaSpan.previousSibling ||
        formulaSpan.previousSibling.nodeType !== Node.TEXT_NODE
      ) {
        parent.insertBefore(document.createTextNode('\u200B'), formulaSpan);
      }
      let afterNode = formulaSpan.nextSibling;
      if (!afterNode || afterNode.nodeType !== Node.TEXT_NODE) {
        afterNode = document.createTextNode('\u00A0');
        parent.insertBefore(afterNode, formulaSpan.nextSibling);
      }
      const nextRange = document.createRange();
      const offset =
        (afterNode.textContent || '').startsWith('\u00A0') ||
        (afterNode.textContent || '').startsWith('\u200B')
          ? 1
          : 0;
      nextRange.setStart(afterNode, offset);
      nextRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(nextRange);
      savedRangeRef.current = nextRange.cloneRange();
    }

    setLatexInput('');
    checkActiveFormats();
    onContentChange();
    showBriefHint('Inserted LaTeX formula on canvas.');
  };

  const handleInsertLatexSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    insertLatexFormulaIntoCanvas(latexInput);
  };

  const getBtnClass = (isActive: boolean = false) =>
    `flex h-9 min-w-[36px] shrink-0 items-center justify-center rounded-xs border px-2 text-xs font-medium transition-colors ${
      isActive
        ? 'border-[#3366cc] bg-[#3366cc] text-white shadow-2xs'
        : 'border-transparent text-[var(--wiki-text)] hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)] active:bg-[#eaecf0] dark:active:bg-[#272d34]'
    }`;

  return (
    <div className="z-30 w-full max-w-full shrink-0 overflow-hidden border-t border-[var(--wiki-border)] bg-[var(--wiki-surface)] select-none">
      {/* Hidden File Inputs */}
      <input
        ref={bgFileInputRef}
        type="file"
        accept="image/*"
        onChange={handleBgFileSelect}
        className="hidden"
      />
      <input
        ref={mediaImageInputRef}
        type="file"
        accept="image/*"
        onChange={handleMediaImagePicked}
        className="hidden"
      />
      <input
        ref={mediaAudioInputRef}
        type="file"
        accept=".wav,.flac,.m4a,.aac,.mp3,.ogg,.webm,audio/*"
        onChange={handleMediaAudioPicked}
        className="hidden"
      />
      <input
        ref={ttfFontInputRef}
        type="file"
        accept=".ttf,.otf,.woff,.woff2"
        onChange={handleTtfFileUpload}
        className="hidden"
      />
      <input
        ref={importDocInputRef}
        type="file"
        accept=".txt,.md,.rtf,.csv,.tsv,.json,.xml,.pdf,.docx,.doc,.odt,.html,.htm,.epub,.log,text/plain,text/markdown,text/csv,application/json,text/xml,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.oasis.opendocument.text,text/html,application/epub+zip"
        onChange={handleImportFileSelected}
        className="hidden"
      />

      {/* Hint Toast */}
      {hintToast && (
        <div className="border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-1.5 text-center text-xs font-medium text-[#3366cc]">
          {hintToast}
        </div>
      )}

      {/* Active Mic Recording Banner */}
      {isRecording && (
        <div className="flex items-center justify-between border-b border-[#b32424] bg-[#b32424]/10 px-3 py-2">
          <div className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full bg-[#b32424] animate-ping" />
            <span className="font-wiki-mono text-xs font-semibold text-[#b32424]">
              Recording .{micSettings.format} ({Math.floor(recordingSeconds / 60)}:
              {String(recordingSeconds % 60).padStart(2, '0')}) · Background Active
            </span>
          </div>
          <button
            type="button"
            onClick={handleToggleMicRecording}
            className="flex h-7 items-center gap-1 bg-[#b32424] px-3 text-xs font-semibold text-white"
          >
            <Square className="h-3 w-3 fill-white" />
            Stop &amp; Save
          </button>
        </div>
      )}

      {/* Popover Row: Selection-Scoped Text Color & Highlight (20 Swatches each) */}
      {showColorPicker && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full flex-col gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[var(--wiki-text)]">
              {showColorPicker === 'text'
                ? 'Text Color (Applies to Selected Text)'
                : 'Highlight Color (Applies to Selected Text)'}
            </span>
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => setShowColorPicker(null)}
              className="text-xs text-[var(--wiki-muted)] underline"
            >
              Close
            </button>
          </div>
          <div className="grid grid-cols-10 gap-1.5">
            {(showColorPicker === 'text' ? TEXT_COLORS : HIGHLIGHT_COLORS).map((color, idx) => {
              const isReset = color === 'transparent' || color === 'default';
              return (
                <button
                  key={idx}
                  type="button"
                  onMouseDown={preventFocusLoss}
                  onClick={() => {
                    if (showColorPicker === 'text') {
                      applySelectionTextColor(color);
                    } else {
                      applySelectionHighlight(color);
                    }
                  }}
                  className="h-6 w-6 rounded-full border border-[var(--wiki-border)] shadow-2xs flex items-center justify-center text-[10px]"
                  style={{ backgroundColor: isReset ? '#ffffff' : color }}
                  title={isReset ? 'Reset / Remove' : color}
                >
                  {isReset ? '✕' : ''}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Popover Row: Enable / Disable Custom English & Hindi .ttf Fonts */}
      {showFontPanel && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full flex-col gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[var(--wiki-text)]">
              Fonts (Applies to Selected Text or Active Cursor · Tap Again to Disable)
            </span>
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => setShowFontPanel(false)}
              className="text-xs text-[var(--wiki-muted)] underline"
            >
              Close
            </button>
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => {
                saveCurrentSelection();
                triggerAndroidMediaPermission();
                ttfFontInputRef.current?.click();
              }}
              className="flex h-8 items-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-semibold hover:border-[#3366cc]"
            >
              <Upload className="h-3.5 w-3.5 text-[#3366cc]" />
              Upload .ttf
            </button>
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto py-1">
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => handleToggleFontFamily(null)}
              className={`shrink-0 border px-2.5 py-1 text-xs font-medium transition-colors ${
                activeFontFamily === null
                  ? 'border-[#3366cc] bg-[#3366cc] text-white'
                  : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)]'
              }`}
            >
              Default Font
            </button>

            {[
              ...BUILTIN_FONTS.map((bf) => ({
                name: bf.name,
                family: bf.family,
              })),
              ...customFonts.map((cf) => ({
                name: cf.name,
                family: cf.fontFamily,
              })),
            ].map((f, i) => {
              const isEnabled = activeFontFamily === f.family;
              return (
                <button
                  key={i}
                  type="button"
                  onMouseDown={preventFocusLoss}
                  onClick={() => handleToggleFontFamily(f.family)}
                  className={`shrink-0 border px-2.5 py-1 text-xs transition-colors ${
                    isEnabled
                      ? 'border-[#3366cc] bg-[#3366cc] text-white font-semibold'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                  style={{ fontFamily: f.family }}
                >
                  {f.name}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Popover Row: Text Size in px Slider (Applies to Selected Text & stays active until Disable is clicked) */}
      {showSizePanel && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full max-w-full items-center gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5 box-border"
        >
          <span className="shrink-0 font-wiki-mono text-xs font-semibold text-[var(--wiki-text)]">
            {textPxSize}px
          </span>
          <input
            type="range"
            min={10}
            max={48}
            step={1}
            value={textPxSize}
            onPointerDown={saveCurrentSelection}
            onChange={(e) => handleApplyTextPxSlider(parseInt(e.target.value, 10))}
            className="h-1.5 min-w-0 flex-1 cursor-pointer accent-[#3366cc]"
          />
          <button
            type="button"
            onMouseDown={preventFocusLoss}
            onClick={handleDisableTextPx}
            className={`shrink-0 border px-2 py-1 text-[11px] font-semibold transition-colors ${
              isTextPxEnabled
                ? 'border-[#b32424] bg-[#b32424] text-white'
                : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-muted)]'
            }`}
            title="Disable custom px size and return to default text size"
          >
            Disable
          </button>
          <button
            type="button"
            onMouseDown={preventFocusLoss}
            onClick={() => setShowSizePanel(false)}
            className="shrink-0 text-xs text-[var(--wiki-muted)] px-1"
          >
            Close
          </button>
        </div>
      )}

      {/* Popover Row: Media Picker Menu — Clean "Add Image" and "Attach Audio" labels */}
      {showMediaPickerMenu && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full items-center justify-between gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5"
        >
          <div className="flex items-center gap-2">
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => {
                triggerAndroidMediaPermission();
                mediaImageInputRef.current?.click();
              }}
              className="flex h-8 items-center gap-1.5 bg-[#3366cc] px-3 text-xs font-semibold text-white"
            >
              <ImageIcon className="h-3.5 w-3.5" />
              Add Image
            </button>
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => {
                triggerAndroidMediaPermission();
                mediaAudioInputRef.current?.click();
              }}
              className="flex h-8 items-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-semibold text-[var(--wiki-text)] hover:border-[#3366cc]"
            >
              <FolderPlus className="h-3.5 w-3.5 text-[#3366cc]" />
              Attach Audio
            </button>
          </div>
          <button
            type="button"
            onMouseDown={preventFocusLoss}
            onClick={() => setShowMediaPickerMenu(false)}
            className="text-xs text-[var(--wiki-muted)] underline"
          >
            Close
          </button>
        </div>
      )}

      {/* Popover Row: Link Selected Text to URL */}
      {showLinkInput && (
        <form
          onSubmit={handleApplyLink}
          className="flex w-full items-center gap-1.5 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2"
        >
          {selectedTextPreview && (
            <span className="max-w-[90px] shrink-0 truncate border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-2 py-1 text-[11px] font-medium text-[#3366cc]">
              "{selectedTextPreview}"
            </span>
          )}
          <input
            type="text"
            value={linkUrl}
            onChange={(e) => setLinkUrl(e.target.value)}
            placeholder="https://..."
            className="h-8 min-w-0 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
            autoFocus
          />
          <button
            type="submit"
            className="h-8 shrink-0 bg-[#3366cc] px-3 text-xs font-semibold text-white"
          >
            Link
          </button>
          <button
            type="button"
            onClick={() => setShowLinkInput(false)}
            className="h-8 shrink-0 px-1.5 text-xs text-[var(--wiki-muted)]"
          >
            ✕
          </button>
        </form>
      )}

      {/* Popover Row: Passcode Spoiler for Selected Text */}
      {showSpoilerPopover && (
        <form
          onSubmit={handleApplySpoiler}
          className="flex w-full items-center gap-1.5 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2"
        >
          <EyeOff className="h-4 w-4 shrink-0 text-[#3366cc]" />
          <input
            type="password"
            value={spoilerPin}
            onChange={(e) => setSpoilerPin(e.target.value)}
            placeholder="Set Spoiler Passcode..."
            className="h-8 min-w-0 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
            autoFocus
          />
          <button
            type="submit"
            className="h-8 shrink-0 bg-[#3366cc] px-3 text-xs font-semibold text-white"
          >
            Apply Spoiler
          </button>
          <button
            type="button"
            onClick={() => setShowSpoilerPopover(false)}
            className="h-8 shrink-0 px-1.5 text-xs text-[var(--wiki-muted)]"
          >
            ✕
          </button>
        </form>
      )}

      {/* Popover Row: Individual Diary Lock Passcode */}
      {showDiaryLockPopover && (
        <form
          onSubmit={handleApplyDiaryLock}
          className="flex w-full items-center gap-1.5 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2"
        >
          <Lock className="h-4 w-4 shrink-0 text-[#b32424]" />
          <input
            type="password"
            value={diaryPinInput}
            onChange={(e) => setDiaryPinInput(e.target.value)}
            placeholder={
              diaryLockPin ? 'Change Diary Lock Passcode...' : 'Set Diary Lock Passcode...'
            }
            className="h-8 min-w-0 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
            autoFocus
          />
          <button
            type="submit"
            className="h-8 shrink-0 bg-[#3366cc] px-3 text-xs font-semibold text-white"
          >
            {diaryLockPin ? 'Update' : 'Lock Diary'}
          </button>
          {diaryLockPin && (
            <button
              type="button"
              onClick={handleRemoveDiaryLock}
              className="flex h-8 shrink-0 items-center gap-1 border border-[#b32424]/50 bg-[#b32424]/10 px-2.5 text-xs font-semibold text-[#b32424]"
            >
              <Unlock className="h-3.5 w-3.5" />
              Unlock
            </button>
          )}
          <button
            type="button"
            onClick={() => setShowDiaryLockPopover(false)}
            className="h-8 shrink-0 px-1.5 text-xs text-[var(--wiki-muted)]"
          >
            ✕
          </button>
        </form>
      )}

      {/* Popover Row: Canvas Background Image & Controlled Transparency */}
      {showBgControl && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full max-w-full flex-col gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5 box-border"
        >
          <div className="flex w-full items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 min-w-0">
              <button
                type="button"
                onMouseDown={preventFocusLoss}
                onClick={() => {
                  triggerAndroidMediaPermission();
                  bgFileInputRef.current?.click();
                }}
                className="flex h-8 shrink-0 items-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 text-xs font-semibold text-[var(--wiki-text)] hover:border-[#3366cc]"
              >
                <ImageIcon className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>{canvasBgDataUrl ? 'Change Background' : 'Set Background'}</span>
              </button>

              {canvasBgDataUrl && (
                <>
                  {onOpenBgImageStudio && (
                    <button
                      type="button"
                      onMouseDown={preventFocusLoss}
                      onClick={() => onOpenBgImageStudio(canvasBgDataUrl)}
                      className="flex h-8 shrink-0 items-center gap-1 border border-[#3366cc]/50 bg-[#3366cc]/10 px-2 text-xs font-semibold text-[#3366cc]"
                    >
                      <span>Compress / Edit</span>
                    </button>
                  )}
                  <button
                    type="button"
                    onMouseDown={preventFocusLoss}
                    onClick={() => onChangeCanvasBg(null, canvasBgOpacity)}
                    className="flex h-8 shrink-0 items-center gap-1 border border-[#b32424]/40 px-2 text-xs font-medium text-[#b32424]"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    <span>Remove</span>
                  </button>
                </>
              )}
            </div>

            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => setShowBgControl(false)}
              className="shrink-0 text-xs text-[var(--wiki-muted)] px-1"
            >
              Done
            </button>
          </div>

          <div className="flex w-full items-center gap-2.5">
            <span className="shrink-0 text-[11px] font-medium text-[var(--wiki-muted)]">
              Opacity {Math.round(canvasBgOpacity * 100)}%
            </span>
            <input
              type="range"
              min={0.05}
              max={1}
              step={0.05}
              value={canvasBgOpacity}
              onChange={(e) =>
                onChangeCanvasBg(canvasBgDataUrl, parseFloat(e.target.value))
              }
              className="h-1.5 min-w-0 flex-1 cursor-pointer accent-[#3366cc]"
            />
          </div>
        </div>
      )}

      {/* Main Scrollable Formatting Toolbar Docked Above Keyboard */}
      <div
        onMouseDown={preventFocusLoss}
        className="flex items-center gap-1 overflow-x-auto px-2 py-1.5 whitespace-nowrap"
      >
        {/* History (Full-Canvas Undo & Redo) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            if (onUndoCanvas) {
              onUndoCanvas();
            } else {
              execToggleInline('undo');
            }
          }}
          className={getBtnClass(false)}
          title="Undo"
        >
          <Undo2 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            if (onRedoCanvas) {
              onRedoCanvas();
            } else {
              execToggleInline('redo');
            }
          }}
          className={getBtnClass(false)}
          title="Redo"
        >
          <Redo2 className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Voice Recorder Mic Button & Media Picker */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={handleToggleMicRecording}
          className={
            isRecording
              ? 'flex h-9 min-w-[36px] shrink-0 items-center justify-center rounded-xs border border-[#b32424] bg-[#b32424] px-2 text-xs font-medium text-white animate-pulse'
              : getBtnClass(false)
          }
          title="Record Voice Audio"
        >
          <Mic className="h-4 w-4" />
        </button>

        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            triggerAndroidMediaPermission();
            const next = !showMediaPickerMenu;
            closeAllPopovers();
            setShowMediaPickerMenu(next);
          }}
          className={getBtnClass(showMediaPickerMenu)}
          title="Media Picker"
        >
          <FolderPlus className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Custom English & Hindi .ttf Font Picker + Font Size px Slider Tool */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const next = !showFontPanel;
            closeAllPopovers();
            setShowFontPanel(next);
          }}
          className={getBtnClass(showFontPanel || activeFontFamily !== null)}
          title="Fonts"
        >
          <CaseSensitive className="h-4 w-4" />
        </button>

        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const next = !showSizePanel;
            closeAllPopovers();
            setShowSizePanel(next);
          }}
          className={getBtnClass(showSizePanel || isTextPxEnabled)}
          title="Text Size (px Slider)"
        >
          <ALargeSmall className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Inline Typography */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('bold')}
          className={getBtnClass(activeFormats.bold)}
          title="Bold"
        >
          <Bold className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('italic')}
          className={getBtnClass(activeFormats.italic)}
          title="Italic"
        >
          <Italic className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('underline')}
          className={getBtnClass(activeFormats.underline)}
          title="Underline"
        >
          <Underline className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('strikeThrough')}
          className={getBtnClass(activeFormats.strikeThrough)}
          title="Strikethrough"
        >
          <Strikethrough className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Headings (H1, H2, H3) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleHeading('H1')}
          className={getBtnClass(activeFormats.h1)}
          title="Heading 1"
        >
          <Heading1 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleHeading('H2')}
          className={getBtnClass(activeFormats.h2)}
          title="Heading 2"
        >
          <Heading2 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleHeading('H3')}
          className={getBtnClass(activeFormats.h3)}
          title="Heading 3"
        >
          <Heading3 className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Lists & Checklists */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('insertUnorderedList')}
          className={getBtnClass(activeFormats.insertUnorderedList)}
          title="Bullet List"
        >
          <List className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('insertOrderedList')}
          className={getBtnClass(activeFormats.insertOrderedList)}
          title="Numbered List"
        >
          <ListOrdered className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={insertChecklist}
          className={getBtnClass(false)}
          title="To-Do Checkbox"
        >
          <CheckSquare className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Selection-Scoped Quotation & Code Snippet + Divider */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => handleToggleSelectionWrapper('quote')}
          className={getBtnClass(activeFormats.quote)}
          title="Quotation"
        >
          <Quote className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => handleToggleSelectionWrapper('code')}
          className={getBtnClass(activeFormats.code)}
          title="Code Snippet"
        >
          <Code className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('insertHorizontalRule')}
          className={getBtnClass(false)}
          title="Section Divider"
        >
          <Minus className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Selection-Scoped Text Color, Selection-Scoped Highlight, Link, Canvas Background Image */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const next = showColorPicker === 'text' ? null : 'text';
            closeAllPopovers();
            setShowColorPicker(next);
          }}
          className={getBtnClass(showColorPicker === 'text' || activeFormats.textColor)}
          title="Text Color"
        >
          <Type className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const next = showColorPicker === 'highlight' ? null : 'highlight';
            closeAllPopovers();
            setShowColorPicker(next);
          }}
          className={getBtnClass(showColorPicker === 'highlight' || activeFormats.highlight)}
          title="Highlight Text"
        >
          <Highlighter className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={handleToggleLinkPopover}
          className={getBtnClass(showLinkInput)}
          title="Link Selected Text"
        >
          <Link2 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            triggerAndroidMediaPermission();
            const next = !showBgControl;
            closeAllPopovers();
            setShowBgControl(next);
          }}
          className={getBtnClass(showBgControl || !!canvasBgDataUrl)}
          title="Canvas Background Image & Transparency"
        >
          <ImageIcon className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Alignment & Indentation */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyLeft')}
          className={getBtnClass(activeFormats.justifyLeft)}
          title="Align Left"
        >
          <AlignLeft className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyCenter')}
          className={getBtnClass(activeFormats.justifyCenter)}
          title="Align Center"
        >
          <AlignCenter className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyRight')}
          className={getBtnClass(activeFormats.justifyRight)}
          title="Align Right"
        >
          <AlignRight className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyFull')}
          className={getBtnClass(activeFormats.justifyFull)}
          title="Justify Full"
        >
          <AlignJustify className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('indent')}
          className={getBtnClass(false)}
          title="Indent"
        >
          <Indent className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('outdent')}
          className={getBtnClass(false)}
          title="Outdent"
        >
          <Outdent className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Subscript, Superscript, LaTeX Math Formulas, Spoiler, Diary Lock, Import Document, Timestamp */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('superscript')}
          className={getBtnClass(activeFormats.superscript)}
          title="Superscript"
        >
          <Superscript className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('subscript')}
          className={getBtnClass(activeFormats.subscript)}
          title="Subscript"
        >
          <Subscript className="h-4 w-4" />
        </button>

        {/* LaTeX Formulas & Mathematical Symbols Tool */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const sel = window.getSelection();
            const selectedStr = sel ? sel.toString().trim() : '';
            const next = !showMathPopover;
            closeAllPopovers();
            if (next && selectedStr && !latexInput.trim()) {
              setLatexInput(selectedStr);
            }
            setShowMathPopover(next);
          }}
          className={getBtnClass(showMathPopover)}
          title="LaTeX Formulas & Mathematical Symbols"
        >
          <Sigma className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Passcode-Protected Spoiler Tool (Selected Text Only) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={handleToggleSpoilerTool}
          className={getBtnClass(showSpoilerPopover)}
          title="Spoiler (Blur Selected Text with Passcode)"
        >
          <EyeOff className="h-4 w-4" />
        </button>

        {/* Individual Diary Lock Tool */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const next = !showDiaryLockPopover;
            closeAllPopovers();
            setShowDiaryLockPopover(next);
          }}
          className={
            diaryLockPin
              ? 'flex h-9 min-w-[36px] shrink-0 items-center justify-center rounded-xs border border-[#b32424] bg-[#b32424] px-2 text-xs font-medium text-white shadow-2xs'
              : getBtnClass(showDiaryLockPopover)
          }
          title={diaryLockPin ? 'Diary Locked (Tap to Change/Unlock)' : 'Lock This Diary Entry'}
        >
          <Lock className="h-4 w-4" />
        </button>

        {/* Multi-Format Import Document Tool (TXT, MD, RTF, CSV, JSON, XML, PDF, DOCX, DOC, ODT, HTML, EPUB, LOG) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            triggerAndroidMediaPermission();
            importDocInputRef.current?.click();
          }}
          className={getBtnClass(false)}
          title="Import Document (TXT, MD, RTF, CSV, JSON, XML, PDF, DOCX, DOC, ODT, HTML, EPUB, LOG)"
        >
          <FileUp className="h-4 w-4" />
        </button>

        {/* Wikipedia Tool (English & Hindi with Live Suggestions & Editable Images) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const next = !showWikipediaPopover;
            closeAllPopovers();
            setShowWikipediaPopover(next);
          }}
          className={getBtnClass(showWikipediaPopover)}
          title="Wikipedia (English & Hindi)"
        >
          <span className="font-wiki-serif text-sm font-bold leading-none">W</span>
        </button>

        {/* Word Analysis Tool (Select Word or Search Word) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            saveCurrentSelection();
            const sel = window.getSelection();
            const selectedStr = sel ? sel.toString().trim() : '';
            const firstSelectedWord = selectedStr
              ? selectedStr.split(/\s+/)[0].replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '')
              : '';
            const next = !showWordAnalysisPopover;
            closeAllPopovers();
            if (next && firstSelectedWord) {
              setWordAnalysisQuery(firstSelectedWord);
            }
            setShowWordAnalysisPopover(next);
          }}
          className={getBtnClass(showWordAnalysisPopover)}
          title="Word Analysis"
        >
          <BookOpenCheck className="h-4 w-4" />
        </button>

        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={insertCurrentTimestamp}
          className={getBtnClass(false)}
          title="Insert Current Date/Time Stamp"
        >
          <CalendarClock className="h-4 w-4" />
        </button>
      </div>

      {/* Wikipedia Search Bar, Live Suggestions Dropdown, English/Hindi Switcher & With/Without Images Toggle Panel */}
      {showWikipediaPopover && (
        <form
          onSubmit={handleSearchWikipedia}
          className="flex w-full flex-col gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5"
        >
          <div className="flex w-full items-center gap-1.5">
            <div className="relative flex flex-1 items-center min-w-0">
              <Search className="pointer-events-none absolute left-2.5 h-3.5 w-3.5 text-[var(--wiki-muted)]" />
              <input
                type="search"
                value={wikiSearchQuery}
                disabled={isWikiLoading}
                onChange={(e) => setWikiSearchQuery(e.target.value)}
                placeholder={
                  wikiLang === 'hi'
                    ? 'विकिपीडिया पर विषय या शब्द खोजें...'
                    : 'Search Wikipedia topic or word...'
                }
                className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] pl-8 pr-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc] disabled:opacity-60"
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={isWikiLoading || !wikiSearchQuery.trim()}
              className="flex h-9 shrink-0 items-center gap-1.5 bg-[#3366cc] px-3.5 text-xs font-semibold text-white disabled:opacity-50"
            >
              {isWikiLoading ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Searching...</span>
                </>
              ) : (
                <span>Search</span>
              )}
            </button>
            <button
              type="button"
              onClick={() => setShowWikipediaPopover(false)}
              className="flex h-9 w-8 shrink-0 items-center justify-center text-xs text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              aria-label="Close Wikipedia search"
            >
              ✕
            </button>
          </div>

          {/* Live Search Suggestions Dropdown Below Search Box */}
          {(isWikiSuggestionsLoading || wikiSuggestions.length > 0) && (
            <div className="max-h-48 w-full overflow-y-auto border border-[var(--wiki-border)] bg-[var(--wiki-surface)] divide-y divide-[var(--wiki-hairline)] shadow-md">
              {isWikiSuggestionsLoading && wikiSuggestions.length === 0 ? (
                <div className="flex items-center gap-2 px-3 py-2 text-xs text-[var(--wiki-muted)]">
                  <Loader2 className="h-3.5 w-3.5 animate-spin text-[#3366cc]" />
                  <span>Loading suggestions...</span>
                </div>
              ) : (
                wikiSuggestions.map((sug) => (
                  <button
                    key={sug.id}
                    type="button"
                    onMouseDown={preventFocusLoss}
                    onClick={() => {
                      setWikiSearchQuery(sug.title);
                      executeWikipediaImport(sug.key, true);
                    }}
                    className="flex w-full items-center gap-2.5 px-3 py-2 text-left hover:bg-[var(--wiki-bg)] transition-colors"
                  >
                    {sug.thumbnailUrl && (
                      <img
                        src={sug.thumbnailUrl}
                        alt={sug.title}
                        referrerPolicy="no-referrer"
                        className="h-8 w-8 shrink-0 border border-[var(--wiki-border)] object-cover"
                      />
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="font-wiki-serif text-xs font-bold text-[var(--wiki-text)] truncate">
                        {sug.title}
                      </div>
                      {sug.description && (
                        <div className="text-[11px] text-[var(--wiki-muted)] truncate">
                          {sug.description}
                        </div>
                      )}
                    </div>
                  </button>
                ))
              )}
            </div>
          )}

          {/* Language Toggle (English / हिन्दी) + With / Without Images Toggle Row */}
          <div className="flex flex-wrap items-center justify-between gap-2 pt-0.5">
            <div className="flex items-center gap-1">
              <button
                type="button"
                onMouseDown={preventFocusLoss}
                onClick={() => setWikiLang('en')}
                className={`h-7 border px-2.5 text-xs font-semibold transition-colors ${
                  wikiLang === 'en'
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)]'
                }`}
              >
                English
              </button>
              <button
                type="button"
                onMouseDown={preventFocusLoss}
                onClick={() => setWikiLang('hi')}
                className={`h-7 border px-2.5 text-xs font-semibold transition-colors ${
                  wikiLang === 'hi'
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)]'
                }`}
              >
                हिन्दी
              </button>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onMouseDown={preventFocusLoss}
                onClick={() => setWikiIncludeImages(true)}
                className={`h-7 border px-2.5 text-xs font-semibold transition-colors ${
                  wikiIncludeImages
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)]'
                }`}
              >
                With Images
              </button>
              <button
                type="button"
                onMouseDown={preventFocusLoss}
                onClick={() => setWikiIncludeImages(false)}
                className={`h-7 border px-2.5 text-xs font-semibold transition-colors ${
                  !wikiIncludeImages
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)]'
                }`}
              >
                Without Images
              </button>
            </div>
          </div>
        </form>
      )}

      {/* LaTeX Formulas & Mathematical Symbols Categories Panel (Opens Below Toolbar) */}
      {showMathPopover && (
        <div className="flex w-full flex-col gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5">
          {/* LaTeX Formula Input + Live Inline Preview + Insert Button */}
          <form onSubmit={handleInsertLatexSubmit} className="flex w-full flex-col gap-1.5">
            <div className="flex w-full items-center gap-1.5">
              <div className="relative flex flex-1 items-center min-w-0">
                <Sigma className="pointer-events-none absolute left-2.5 h-3.5 w-3.5 text-[#3366cc]" />
                <input
                  ref={latexInputRef}
                  type="text"
                  value={latexInput}
                  onChange={(e) => setLatexInput(e.target.value)}
                  placeholder="Tap symbols below or type LaTeX (e.g. \frac{a}{b}, \int_{a}^{b} f(x)\,dx)..."
                  className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] pl-8 pr-2.5 font-wiki-mono text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                />
              </div>
              {latexInput.trim().length > 0 && (
                <button
                  type="button"
                  onMouseDown={preventFocusLoss}
                  onClick={() => setLatexInput('')}
                  className="flex h-9 shrink-0 items-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 text-xs font-medium text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
                  title="Clear formula input"
                >
                  Clear
                </button>
              )}
              <button
                type="submit"
                disabled={!latexInput.trim()}
                className="flex h-9 shrink-0 items-center gap-1 bg-[#3366cc] px-3.5 text-xs font-semibold text-white disabled:opacity-50"
              >
                <span>Insert Formula</span>
              </button>
              <button
                type="button"
                onClick={() => setShowMathPopover(false)}
                className="flex h-9 w-8 shrink-0 items-center justify-center text-xs text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
                aria-label="Close LaTeX Math tool"
              >
                ✕
              </button>
            </div>

            {/* Live Rendered LaTeX Preview Bar */}
            {latexInput.trim().length > 0 && (
              <div className="flex items-center justify-between gap-2 border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-2.5 py-1.5 text-xs">
                <div className="flex items-center gap-2 overflow-x-auto">
                  <span className="shrink-0 text-[10px] font-semibold uppercase tracking-wider text-[var(--wiki-muted)]">
                    Preview:
                  </span>
                  <span
                    className="wiki-latex-formula text-sm text-[var(--wiki-text)]"
                    dangerouslySetInnerHTML={{
                      __html: renderLatexExpressionToHtml(latexInput),
                    }}
                  />
                </div>
              </div>
            )}
          </form>

          {/* 6 Mathematical Categories Selector Tabs */}
          <div
            onMouseDown={preventFocusLoss}
            className="flex items-center gap-1 overflow-x-auto pb-0.5 whitespace-nowrap"
          >
            {MATH_SYMBOL_CATEGORIES.map((cat) => {
              const isSelected = cat.id === activeMathCategoryId;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onMouseDown={preventFocusLoss}
                  onClick={() => setActiveMathCategoryId(cat.id)}
                  className={`shrink-0 border px-2.5 py-1 text-[11px] font-semibold transition-colors ${
                    isSelected
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                  title={cat.title}
                >
                  {cat.title}
                </button>
              );
            })}
          </div>

          {/* Symbols Grid for Active Mathematical Category */}
          {(() => {
            const activeCategory =
              MATH_SYMBOL_CATEGORIES.find((c) => c.id === activeMathCategoryId) ||
              MATH_SYMBOL_CATEGORIES[0];
            return (
              <div
                onMouseDown={preventFocusLoss}
                className="max-h-44 overflow-y-auto border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-2"
              >
                <div className="mb-1.5 flex items-center justify-between">
                  <span className="text-[11px] font-bold text-[#3366cc]">
                    {activeCategory.title}
                  </span>
                  <span className="text-[10px] text-[var(--wiki-muted)]">
                    Tap symbol to add to formula · Double-tap to insert directly
                  </span>
                </div>
                <div className="grid grid-cols-4 sm:grid-cols-6 gap-1.5">
                  {activeCategory.symbols.map((sym) => (
                    <button
                      key={sym.id}
                      type="button"
                      onMouseDown={preventFocusLoss}
                      onClick={() => handleSelectMathSymbol(sym)}
                      onDoubleClick={() => insertLatexFormulaIntoCanvas(sym.latex)}
                      title={`${sym.label} (${sym.latex.trim()})`}
                      className="flex flex-col items-center justify-center gap-0.5 border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-1.5 py-1.5 text-center hover:border-[#3366cc] hover:bg-[#3366cc]/5 active:bg-[#3366cc]/15 transition-colors"
                    >
                      <span
                        className="wiki-latex-formula text-sm font-semibold text-[var(--wiki-text)]"
                        dangerouslySetInnerHTML={{
                          __html: renderLatexExpressionToHtml(sym.latex),
                        }}
                      />
                      <span className="w-full truncate text-[9px] leading-tight text-[var(--wiki-muted)]">
                        {sym.label}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            );
          })()}
        </div>
      )}

      {/* Word Analysis Search Bar Panel (Opens Below Toolbar) */}
      {showWordAnalysisPopover && (
        <form
          onSubmit={handleWordAnalysisSubmit}
          className="flex w-full flex-col gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5"
        >
          <div className="flex w-full items-center gap-1.5">
            <div className="relative flex flex-1 items-center min-w-0">
              <BookOpenCheck className="pointer-events-none absolute left-2.5 h-3.5 w-3.5 text-[#3366cc]" />
              <input
                type="search"
                value={wordAnalysisQuery}
                disabled={isWordAnalysisLoading}
                onChange={(e) => setWordAnalysisQuery(e.target.value)}
                placeholder="Enter or select a word to analyze..."
                className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] pl-8 pr-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc] disabled:opacity-60"
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={isWordAnalysisLoading || !wordAnalysisQuery.trim()}
              className="flex h-9 shrink-0 items-center gap-1.5 bg-[#3366cc] px-3.5 text-xs font-semibold text-white disabled:opacity-50"
            >
              {isWordAnalysisLoading ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Analyzing...</span>
                </>
              ) : (
                <span>Analyze</span>
              )}
            </button>
            <button
              type="button"
              onClick={() => setShowWordAnalysisPopover(false)}
              className="flex h-9 w-8 shrink-0 items-center justify-center text-xs text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              aria-label="Close Word Analysis"
            >
              ✕
            </button>
          </div>
        </form>
      )}

      {/* Word Analysis Full 6-Category Pop-Up Modal on Canvas */}
      {activeWordAnalysisRecord && (
        <WordAnalysisModal
          record={activeWordAnalysisRecord}
          onClose={() => setActiveWordAnalysisRecord(null)}
        />
      )}
    </div>
  );
};

import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Bell,
  BellRing,
  Check,
  ImagePlus,
  Save,
  Trash2,
  RotateCw,
  RotateCcw,
  ZoomIn,
  ZoomOut,
  Layers,
  Pencil,
  Eye,
  Lock,
  Unlock,
  X,
  Gauge,
  ArrowUp,
  ArrowDown,
  ArrowRight,
  MoveHorizontal,
  MoveVertical,
} from 'lucide-react';
import {
  AudioFormatOption,
  CanvasAudioAttachment,
  CanvasDraggableImage,
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
  VaultMode,
  getRemainingPasscodeCooldownSeconds,
  recordPasscodeFailure,
  resetPasscodeFailures,
} from '../utils/cryptoVault';
import { scheduleAndroidNativeReminder } from '../utils/notificationSound';
import { ImageCropperModal } from './ImageCropperModal';
import { ReminderModal } from './ReminderModal';
import { RichTextToolbar } from './RichTextToolbar';
import { MediaImageStudioModal } from './MediaImageStudioModal';
import { MediaAudioStudioModal } from './MediaAudioStudioModal';
import { CanvasAudioPlayerCard } from './CanvasAudioPlayerCard';
import {
  buildSpoilerMaskString,
  decryptSpoilerSecretText,
  lockAndMaskSpoilerElement,
} from '../utils/spoilerCipher';
import { renderLatexExpressionToHtml } from '../utils/latexMathEngine';

interface WritingWorkspaceProps {
  vaultMode?: VaultMode;
  initialLog: DiaryLog | null;
  onSaveLog: (log: DiaryLog, exitAfterSave: boolean) => void;
  onDeleteLog?: (id: string) => void;
  onExitWithoutSave: () => void;
  customFonts: CustomFontItem[];
  onAddCustomFont: (font: CustomFontItem) => void;
  micSettings: MicRecordingSettings;
  registerBackHandler?: (fn: () => void) => void;
}

interface CanvasHistorySnapshot {
  heading: string;
  pfpDataUrl: string | null;
  contentHtml: string;
  canvasBgDataUrl: string | null;
  canvasBgOpacity: number;
  canvasImages: CanvasDraggableImage[];
  audioAttachments: CanvasAudioAttachment[];
  diaryLockPin: string | null;
}

const AUDIO_SPEED_OPTIONS = [1, 1.25, 1.5, 2];
const SPOILER_MASK_SYMBOLS = '*@#&€¥%$¢π§∆';

function generateSpoilerSymbolMask(text: string): string {
  const len = Math.max(4, text.trim().length);
  let out = '';
  for (let i = 0; i < len; i++) {
    out += SPOILER_MASK_SYMBOLS[i % SPOILER_MASK_SYMBOLS.length];
  }
  return out;
}

function stripHtmlToSingleLine(html: string): string {
  const temp = document.createElement('div');
  temp.innerHTML = html;
  // Mask spoiler spans in plain text preview so hidden text never leaks on homepage
  temp.querySelectorAll('span[data-wiki-spoiler="true"]').forEach((el) => {
    el.textContent = '••••';
  });
  const text = temp.textContent || temp.innerText || '';
  return text.replace(/[\u200B]+/g, '').replace(/\s+/g, ' ').trim();
}

function normalizeSpoilersToLockedForSave(rawHtml: string): string {
  const temp = document.createElement('div');
  temp.innerHTML = rawHtml;
  temp.querySelectorAll('span[data-wiki-spoiler="true"]').forEach((el) => {
    lockAndMaskSpoilerElement(el as HTMLElement);
  });
  return temp.innerHTML;
}

function formatDateStamp(ms: number): string {
  return new Date(ms).toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function formatTimeStamp(ms: number): string {
  return new Date(ms).toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

export const WritingWorkspace: React.FC<WritingWorkspaceProps> = ({
  vaultMode = 'primary',
  initialLog,
  onSaveLog,
  onDeleteLog,
  onExitWithoutSave,
  customFonts,
  onAddCustomFont,
  micSettings,
  registerBackHandler,
}) => {
  const stableLogIdRef = useRef<string>(
    initialLog?.id || `log_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  );
  const stableCreatedAtRef = useRef<number>(initialLog?.createdAt || Date.now());

  // Saved diaries open in Reading Mode by default; new diaries open in Editing Mode
  const [isReadingMode, setIsReadingMode] = useState<boolean>(() => Boolean(initialLog));

  const [heading, setHeading] = useState<string>(initialLog?.heading || '');
  const [pfpDataUrl, setPfpDataUrl] = useState<string | null>(
    initialLog?.pfpDataUrl || null
  );
  const [reminderAt, setReminderAt] = useState<number | null>(
    initialLog?.reminderAt || null
  );
  const [diaryLockPin, setDiaryLockPin] = useState<string | null>(
    initialLog?.diaryLockPin || null
  );
  const [canvasBgDataUrl, setCanvasBgDataUrl] = useState<string | null>(
    initialLog?.canvasBgDataUrl || null
  );
  const [canvasBgOpacity, setCanvasBgOpacity] = useState<number>(
    initialLog?.canvasBgOpacity ?? 0.25
  );
  const [canvasImages, setCanvasImages] = useState<CanvasDraggableImage[]>(
    initialLog?.canvasImages || []
  );
  const [audioAttachments, setAudioAttachments] = useState<CanvasAudioAttachment[]>(() =>
    (initialLog?.audioAttachments || []).map((a, idx) => ({
      ...a,
      x: a.x ?? 24 + (idx * 20) % 80,
      y: a.y ?? 140 + idx * 88,
      width: a.width ?? 270,
      height: a.height ?? 56,
      rotation: a.rotation ?? 0,
      playbackRate: a.playbackRate ?? 1,
      layer: a.layer ?? 'foreground',
    }))
  );

  // Full-Canvas Undo & Redo History Stack
  const historyStackRef = useRef<CanvasHistorySnapshot[]>([]);
  const historyIndexRef = useRef<number>(0);
  const isRestoringHistoryRef = useRef<boolean>(false);
  const textDebounceTimerRef = useRef<number | null>(null);

  // Guard copy of HTML prior to user input so accidental deletion of spoiler spans is immediately reverted
  const lastValidHtmlWithSpoilersRef = useRef<string>(initialLog?.contentHtml || '');

  // Header 1:1 PFP Cropper & Emoji/Text Studio state
  const [rawSelectedImage, setRawSelectedImage] = useState<string | null>(null);
  const [showPfpStudioModal, setShowPfpStudioModal] = useState<boolean>(false);

  // Active inline LaTeX Formula edit state when user taps an existing formula in Edit Mode
  const [activeLatexSpan, setActiveLatexSpan] = useState<HTMLElement | null>(null);
  const [editLatexInput, setEditLatexInput] = useState<string>('');

  // Media Picker Image Studio state (for adding new image OR editing an existing canvas image or inline Wikipedia image)
  const [rawMediaStudioImage, setRawMediaStudioImage] = useState<string | null>(null);
  const [editingCanvasImageId, setEditingCanvasImageId] = useState<string | null>(null);
  const [selectedInlineImgId, setSelectedInlineImgId] = useState<string | null>(null);
  const [editingInlineImgId, setEditingInlineImgId] = useState<string | null>(null);
  const [, setInlineImgTick] = useState<number>(0);

  // Canvas Background Image Studio state (Compression Format, Resolution, Quality, Bit Depth, Color Space, EXIF, Crop, Adjust & 22+ Filters)
  const [rawBgStudioImage, setRawBgStudioImage] = useState<string | null>(null);

  // Media Picker Audio Studio state (Bitrate, Codec, Mono/Stereo Channels, Sampling Rate)
  const [rawMediaStudioAudio, setRawMediaStudioAudio] = useState<{
    dataUrl: string;
    fileName: string;
    format: AudioFormatOption;
  } | null>(null);

  // Free-dragging + 2-finger pinch resize & rotate state for Canvas Images
  const [activeDragId, setActiveDragId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [selectedCanvasImgId, setSelectedCanvasImgId] = useState<string | null>(null);
  const imgPointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());
  const pinchInitialRef = useRef<{
    id: string;
    dist: number;
    angle: number;
    startWidth: number;
    startRotation: number;
  } | null>(null);

  // Selected Canvas Audio Player ID (activated ONLY via Double Tap!)
  const [selectedCanvasAudioId, setSelectedCanvasAudioId] = useState<string | null>(null);
  const lastAudioSelectTimestampRef = useRef<number>(0);

  const selectAudioCardSafely = (audioId: string) => {
    lastAudioSelectTimestampRef.current = Date.now();
    setSelectedCanvasAudioId(audioId);
    setSelectedCanvasImgId(null);
  };

  // Spoiler Unlock / Disable Modal state (when user taps a blurred spoiler span on the Canvas)
  const [activeSpoilerSpan, setActiveSpoilerSpan] = useState<HTMLElement | null>(null);
  const [spoilerUnlockInput, setSpoilerUnlockInput] = useState<string>('');
  const [spoilerUnlockError, setSpoilerUnlockError] = useState<string | null>(null);
  const [spoilerCooldownSec, setSpoilerCooldownSec] = useState<number>(() =>
    getRemainingPasscodeCooldownSeconds()
  );

  useEffect(() => {
    if (!activeSpoilerSpan) return;
    const sync = () => setSpoilerCooldownSec(getRemainingPasscodeCooldownSeconds());
    sync();
    const timer = window.setInterval(sync, 500);
    return () => window.clearInterval(timer);
  }, [activeSpoilerSpan]);

  const [showReminderModal, setShowReminderModal] = useState<boolean>(false);
  const [savedIndicator, setSavedIndicator] = useState<boolean>(false);

  const editorRef = useRef<HTMLDivElement | null>(null);
  const canvasContainerRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Populate initial HTML into contentEditable once on mount & initialize history stack
  useEffect(() => {
    const startHtml = initialLog?.contentHtml || '';
    if (editorRef.current) {
      editorRef.current.innerHTML = startHtml;
      editorRef.current
        .querySelectorAll('span[data-wiki-spoiler="true"]')
        .forEach((el) => {
          lockAndMaskSpoilerElement(el as HTMLElement);
        });
      lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
    }
    const normalizedAudios = (initialLog?.audioAttachments || []).map((a, idx) => ({
      ...a,
      x: a.x ?? 24 + (idx * 20) % 80,
      y: a.y ?? 140 + idx * 88,
      width: a.width ?? 270,
      height: a.height ?? 64,
      rotation: a.rotation ?? 0,
      playbackRate: a.playbackRate ?? 1,
      layer: a.layer ?? 'foreground',
    }));
    const initialSnap: CanvasHistorySnapshot = {
      heading: initialLog?.heading || '',
      pfpDataUrl: initialLog?.pfpDataUrl || null,
      contentHtml: startHtml,
      canvasBgDataUrl: initialLog?.canvasBgDataUrl || null,
      canvasBgOpacity: initialLog?.canvasBgOpacity ?? 0.25,
      canvasImages: JSON.parse(JSON.stringify(initialLog?.canvasImages || [])),
      audioAttachments: JSON.parse(JSON.stringify(normalizedAudios)),
      diaryLockPin: initialLog?.diaryLockPin || null,
    };
    historyStackRef.current = [initialSnap];
    historyIndexRef.current = 0;
  }, [initialLog]);

  const normalizeSpoilersAndSelectionForSave = (rawHtml: string): string => {
    const cleaned = normalizeSpoilersToLockedForSave(rawHtml);
    const temp = document.createElement('div');
    temp.innerHTML = cleaned;
    temp.querySelectorAll('img').forEach((img) => {
      img.style.outline = '';
      img.style.outlineOffset = '';
    });
    return temp.innerHTML;
  };
  // Check whether the user actually modified any diary content compared to initialLog
  const hasContentChangedFromInitial = (): boolean => {
    if (!initialLog) return true;
    const initialSnap = historyStackRef.current[0];
    if (!initialSnap) return false;
    const currentHtml = editorRef.current
      ? normalizeSpoilersAndSelectionForSave(editorRef.current.innerHTML)
      : '';
    const initialNormalizedHtml = normalizeSpoilersAndSelectionForSave(
      initialSnap.contentHtml
    );

    if (heading.trim() !== initialSnap.heading.trim()) return true;
    if (pfpDataUrl !== initialSnap.pfpDataUrl) return true;
    if (currentHtml !== initialNormalizedHtml) return true;
    if (diaryLockPin !== initialSnap.diaryLockPin) return true;
    if (canvasBgDataUrl !== initialSnap.canvasBgDataUrl) return true;
    if (Math.abs(canvasBgOpacity - initialSnap.canvasBgOpacity) > 0.001) return true;
    if (JSON.stringify(canvasImages) !== JSON.stringify(initialSnap.canvasImages)) return true;
    if (JSON.stringify(audioAttachments) !== JSON.stringify(initialSnap.audioAttachments))
      return true;

    return false;
  };

  // Push a full-canvas snapshot onto the Undo/Redo stack
  const pushCanvasSnapshot = (overrides?: Partial<CanvasHistorySnapshot>) => {
    if (isRestoringHistoryRef.current) return;
    const currentHtml =
      overrides?.contentHtml !== undefined
        ? overrides.contentHtml
        : editorRef.current?.innerHTML || '';

    lastValidHtmlWithSpoilersRef.current = currentHtml;

    const snap: CanvasHistorySnapshot = {
      heading: overrides?.heading !== undefined ? overrides.heading : heading,
      pfpDataUrl: overrides?.pfpDataUrl !== undefined ? overrides.pfpDataUrl : pfpDataUrl,
      contentHtml: currentHtml,
      canvasBgDataUrl:
        overrides?.canvasBgDataUrl !== undefined
          ? overrides.canvasBgDataUrl
          : canvasBgDataUrl,
      canvasBgOpacity:
        overrides?.canvasBgOpacity !== undefined
          ? overrides.canvasBgOpacity
          : canvasBgOpacity,
      canvasImages: JSON.parse(
        JSON.stringify(
          overrides?.canvasImages !== undefined ? overrides.canvasImages : canvasImages
        )
      ),
      audioAttachments: JSON.parse(
        JSON.stringify(
          overrides?.audioAttachments !== undefined
            ? overrides.audioAttachments
            : audioAttachments
        )
      ),
      diaryLockPin:
        overrides?.diaryLockPin !== undefined ? overrides.diaryLockPin : diaryLockPin,
    };

    const currentTop = historyStackRef.current[historyIndexRef.current];
    if (currentTop && JSON.stringify(currentTop) === JSON.stringify(snap)) {
      return;
    }

    const nextStack = historyStackRef.current.slice(0, historyIndexRef.current + 1);
    nextStack.push(snap);
    if (nextStack.length > 60) {
      nextStack.shift();
    }
    historyStackRef.current = nextStack;
    historyIndexRef.current = nextStack.length - 1;
  };

  const applyCanvasSnapshot = (snap: CanvasHistorySnapshot) => {
    isRestoringHistoryRef.current = true;
    setHeading(snap.heading);
    setPfpDataUrl(snap.pfpDataUrl);
    setCanvasBgDataUrl(snap.canvasBgDataUrl);
    setCanvasBgOpacity(snap.canvasBgOpacity);
    setCanvasImages(JSON.parse(JSON.stringify(snap.canvasImages)));
    setAudioAttachments(JSON.parse(JSON.stringify(snap.audioAttachments)));
    setDiaryLockPin(snap.diaryLockPin);
    if (editorRef.current && editorRef.current.innerHTML !== snap.contentHtml) {
      editorRef.current.innerHTML = snap.contentHtml;
      lastValidHtmlWithSpoilersRef.current = snap.contentHtml;
    }
    window.setTimeout(() => {
      isRestoringHistoryRef.current = false;
    }, 20);
  };

  const handleCanvasUndo = () => {
    if (textDebounceTimerRef.current) {
      window.clearTimeout(textDebounceTimerRef.current);
      textDebounceTimerRef.current = null;
      pushCanvasSnapshot();
    }
    if (historyIndexRef.current > 0) {
      historyIndexRef.current -= 1;
      applyCanvasSnapshot(historyStackRef.current[historyIndexRef.current]);
    }
  };

  const handleCanvasRedo = () => {
    if (historyIndexRef.current < historyStackRef.current.length - 1) {
      historyIndexRef.current += 1;
      applyCanvasSnapshot(historyStackRef.current[historyIndexRef.current]);
    }
  };

  const scheduleTextHistorySnapshot = () => {
    if (isRestoringHistoryRef.current) return;
    if (editorRef.current) {
      lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
    }
    if (textDebounceTimerRef.current) {
      window.clearTimeout(textDebounceTimerRef.current);
    }
    textDebounceTimerRef.current = window.setTimeout(() => {
      pushCanvasSnapshot();
      textDebounceTimerRef.current = null;
    }, 350);
  };

  // Strictly protect ONLY the Spoiler word itself (`span[data-wiki-spoiler="true"]`) from Backspace, Delete, Cut, or extension:
  // - Other words in the exact same paragraph can be deleted with Backspace/Delete without any restriction!
  // - Even if the cursor is right at the start or right at the end of the spoiler word, typing never extends the spoiler span!
  const isNodeASpoilerSpan = (n: Node | null): n is HTMLElement => {
    return (
      Boolean(n) &&
      n!.nodeType === Node.ELEMENT_NODE &&
      (n as HTMLElement).getAttribute('data-wiki-spoiler') === 'true'
    );
  };

  const findEnclosingSpoilerSpan = (n: Node | null): HTMLElement | null => {
    let cur: Node | null = n;
    while (cur && cur !== editorRef.current) {
      if (isNodeASpoilerSpan(cur)) {
        return cur;
      }
      cur = cur.parentNode;
    }
    return null;
  };

  /**
   * If the cursor (or selection boundary) is inside a spoiler span or right on its edge,
   * step the cursor cleanly outside into an adjacent plain text node in the paragraph
   * so typing at the start or end of the spoiler word NEVER extends the spoiler!
   */
  const ensureCursorOutsideSpoilerSpan = (
    sel: Selection,
    range: Range
  ): Range => {
    if (!editorRef.current || !range.collapsed) return range;

    const enclosingSpoiler = findEnclosingSpoilerSpan(range.startContainer);
    if (!enclosingSpoiler || !enclosingSpoiler.parentNode) {
      return range;
    }

    const parent = enclosingSpoiler.parentNode;
    // Determine whether cursor is closer to the start or end of the spoiler span
    const preRange = document.createRange();
    preRange.selectNodeContents(enclosingSpoiler);
    preRange.setEnd(range.startContainer, range.startOffset);
    const isAtStart = preRange.toString().length === 0;

    if (isAtStart) {
      let prev = enclosingSpoiler.previousSibling;
      if (!prev || prev.nodeType !== Node.TEXT_NODE) {
        prev = document.createTextNode('');
        parent.insertBefore(prev, enclosingSpoiler);
      }
      const nextRange = document.createRange();
      nextRange.setStart(prev, (prev.textContent || '').length);
      nextRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(nextRange);
      return nextRange;
    } else {
      let next = enclosingSpoiler.nextSibling;
      if (!next || next.nodeType !== Node.TEXT_NODE) {
        next = document.createTextNode('');
        parent.insertBefore(next, enclosingSpoiler.nextSibling);
      }
      const nextRange = document.createRange();
      nextRange.setStart(next, 0);
      nextRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(nextRange);
      return nextRange;
    }
  };

  const selectionContainsSpoiler = (range: Range): boolean => {
    if (!editorRef.current) return false;
    const spoilers = editorRef.current.querySelectorAll('span[data-wiki-spoiler="true"]');
    for (let i = 0; i < spoilers.length; i++) {
      if (range.intersectsNode(spoilers[i])) {
        return true;
      }
    }
    return false;
  };

  /**
   * Checks ONLY whether a single-character Backspace or Forward-Delete at a collapsed cursor
   * would directly delete the `span[data-wiki-spoiler="true"]` element itself.
   * Never checks ancestor/paragraph containers via `querySelector`, so all other words in the
   * same paragraph can be freely edited and deleted with Backspace!
   */
  const isCollapsedCursorDirectlyTouchingSpoiler = (
    range: Range,
    isBackspace: boolean
  ): boolean => {
    if (!range.collapsed) return false;

    const node = range.startContainer;
    const offset = range.startOffset;

    if (findEnclosingSpoilerSpan(node)) {
      return true;
    }

    // Helper: walk previous/next inline sibling within the same block, skipping empty/zero-width text nodes
    const getAdjacentMeaningfulInlineSibling = (
      startNode: Node,
      dir: 'prev' | 'next'
    ): Node | null => {
      let sib: Node | null =
        dir === 'prev' ? startNode.previousSibling : startNode.nextSibling;
      while (sib) {
        if (sib.nodeType === Node.TEXT_NODE) {
          const clean = (sib.textContent || '').replace(/\u200B/g, '');
          if (clean.length === 0) {
            sib = dir === 'prev' ? sib.previousSibling : sib.nextSibling;
            continue;
          }
          return sib;
        }
        return sib;
      }
      return null;
    };

    if (isBackspace) {
      if (node.nodeType === Node.TEXT_NODE) {
        const textBefore = (node.textContent || '')
          .slice(0, offset)
          .replace(/\u200B/g, '');
        if (textBefore.length > 0) {
          // There are normal characters before the cursor in this text node — Backspace deletes those characters normally!
          return false;
        }
        // Cursor is at offset 0 of this text node: check what is immediately before this node
        let cur: Node | null = node;
        while (cur && cur !== editorRef.current) {
          const prevSib = getAdjacentMeaningfulInlineSibling(cur, 'prev');
          if (prevSib) {
            return isNodeASpoilerSpan(prevSib);
          }
          const parentEl = cur.parentElement;
          if (
            !parentEl ||
            parentEl === editorRef.current ||
            ['P', 'DIV', 'LI', 'H1', 'H2', 'H3', 'BLOCKQUOTE'].includes(
              parentEl.tagName.toUpperCase()
            )
          ) {
            break;
          }
          cur = parentEl;
        }
        return false;
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        for (let idx = offset - 1; idx >= 0; idx--) {
          const child = node.childNodes[idx];
          if (
            child.nodeType === Node.TEXT_NODE &&
            (child.textContent || '').replace(/\u200B/g, '').length === 0
          ) {
            continue;
          }
          return isNodeASpoilerSpan(child);
        }
      }
    } else {
      // Forward Delete
      if (node.nodeType === Node.TEXT_NODE) {
        const textAfter = (node.textContent || '')
          .slice(offset)
          .replace(/\u200B/g, '');
        if (textAfter.length > 0) {
          return false;
        }
        let cur: Node | null = node;
        while (cur && cur !== editorRef.current) {
          const nextSib = getAdjacentMeaningfulInlineSibling(cur, 'next');
          if (nextSib) {
            return isNodeASpoilerSpan(nextSib);
          }
          const parentEl = cur.parentElement;
          if (
            !parentEl ||
            parentEl === editorRef.current ||
            ['P', 'DIV', 'LI', 'H1', 'H2', 'H3', 'BLOCKQUOTE'].includes(
              parentEl.tagName.toUpperCase()
            )
          ) {
            break;
          }
          cur = parentEl;
        }
        return false;
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        for (let idx = offset; idx < node.childNodes.length; idx++) {
          const child = node.childNodes[idx];
          if (
            child.nodeType === Node.TEXT_NODE &&
            (child.textContent || '').replace(/\u200B/g, '').length === 0
          ) {
            continue;
          }
          return isNodeASpoilerSpan(child);
        }
      }
    }

    return false;
  };

  /**
   * Deletes all non-spoiler text inside `range` while keeping every `span[data-wiki-spoiler="true"]` intact.
   * Used when user selects multiple words (or when Android Gboard replaces a composing range) that happens to touch a spoiler.
   */
  const deleteNonSpoilerTextInRange = (range: Range) => {
    if (!editorRef.current || range.collapsed) return;
    const textNodesToModify: Text[] = [];
    const walker = document.createTreeWalker(
      editorRef.current,
      NodeFilter.SHOW_TEXT
    );
    let currentNode: Node | null = walker.nextNode();
    while (currentNode) {
      if (
        currentNode.nodeType === Node.TEXT_NODE &&
        range.intersectsNode(currentNode) &&
        !findEnclosingSpoilerSpan(currentNode)
      ) {
        textNodesToModify.push(currentNode as Text);
      }
      currentNode = walker.nextNode();
    }

    textNodesToModify.forEach((tNode) => {
      const full = tNode.textContent || '';
      const startOff = tNode === range.startContainer ? range.startOffset : 0;
      const endOff = tNode === range.endContainer ? range.endOffset : full.length;
      tNode.textContent = full.slice(0, startOff) + full.slice(endOff);
    });
  };

  /**
   * If the collapsed cursor is at the VERY START or VERY END of a Text Color (`span[data-wiki-color]`)
   * or Highlight (`<mark>`) element, step the cursor right outside that element before inserting text/space:
   * - If cursor is in the MIDDLE of the colored/highlighted word, it stays inside so the color/highlight continues normally.
   * - If cursor is at the start or end edge of the colored/highlighted word, typing a new word or space will NOT continue the color/highlight!
   */
  const escapeColorOrHighlightEdgeIfAtBoundary = (sel: Selection, range: Range) => {
    if (!range.collapsed || !editorRef.current) return;

    let outermostBoundaryEl: HTMLElement | null = null;
    let boundarySide: 'start' | 'end' | null = null;

    let cur: Node | null = range.startContainer;
    while (cur && cur !== editorRef.current) {
      if (cur.nodeType === Node.ELEMENT_NODE) {
        const el = cur as HTMLElement;
        const isColorOrMark =
          el.tagName === 'MARK' || el.hasAttribute('data-wiki-color');

        if (isColorOrMark) {
          const preRange = document.createRange();
          preRange.selectNodeContents(el);
          preRange.setEnd(range.startContainer, range.startOffset);
          const textBefore = (preRange.toString() || '').replace(/\u200B/g, '');

          const postRange = document.createRange();
          postRange.selectNodeContents(el);
          postRange.setStart(range.startContainer, range.startOffset);
          const textAfter = (postRange.toString() || '').replace(/\u200B/g, '');

          if (textBefore.length === 0) {
            outermostBoundaryEl = el;
            boundarySide = 'start';
          } else if (textAfter.length === 0) {
            outermostBoundaryEl = el;
            boundarySide = 'end';
          } else {
            // Cursor is strictly in the middle of this colored/highlighted word — keep style active!
            return;
          }
        }
      }
      cur = cur.parentNode;
    }

    if (!outermostBoundaryEl || !boundarySide || !outermostBoundaryEl.parentNode) {
      return;
    }

    const parent = outermostBoundaryEl.parentNode;
    if (boundarySide === 'start') {
      let prev = outermostBoundaryEl.previousSibling;
      if (!prev || prev.nodeType !== Node.TEXT_NODE) {
        prev = document.createTextNode('\u200B');
        parent.insertBefore(prev, outermostBoundaryEl);
      }
      const nextRange = document.createRange();
      const len = (prev.textContent || '').length;
      nextRange.setStart(prev, len);
      nextRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(nextRange);
    } else {
      let next = outermostBoundaryEl.nextSibling;
      if (!next || next.nodeType !== Node.TEXT_NODE) {
        next = document.createTextNode('\u200B');
        parent.insertBefore(next, outermostBoundaryEl.nextSibling);
      }
      const nextRange = document.createRange();
      const offset = (next.textContent || '').startsWith('\u200B') ? 1 : 0;
      nextRange.setStart(next, offset);
      nextRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(nextRange);
    }
  };

  const handleEditorBeforeInput = (e: React.FormEvent<HTMLDivElement>) => {
    const nativeEv = e.nativeEvent as InputEvent;
    if (!nativeEv || !editorRef.current) return;
    const inputType = nativeEv.inputType || '';

    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    let range = sel.getRangeAt(0);

    // If the collapsed cursor ever landed inside a spoiler span (at its start or end edge),
    // immediately step it outside so typing or deleting acts on normal surrounding text
    if (range.collapsed) {
      range = ensureCursorOutsideSpoilerSpan(sel, range);
    }

    if (inputType.startsWith('delete')) {
      const isBack = inputType.includes('Backward');
      if (!range.collapsed) {
        if (selectionContainsSpoiler(range)) {
          e.preventDefault();
          // Delete all non-spoiler text in the selected range while leaving the spoiler word untouched!
          deleteNonSpoilerTextInRange(range);
          lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
          scheduleTextHistorySnapshot();
        }
        return;
      }

      if (isCollapsedCursorDirectlyTouchingSpoiler(range, isBack)) {
        e.preventDefault();
        return;
      }
    } else if (!range.collapsed && selectionContainsSpoiler(range)) {
      // If user or Android Gboard replaces a range that overlaps a spoiler, only replace the non-spoiler text!
      e.preventDefault();
      deleteNonSpoilerTextInRange(range);
      const insertedData = nativeEv.data;
      if (insertedData) {
        const currentRange = sel.rangeCount > 0 ? sel.getRangeAt(0) : range;
        const safeRange = ensureCursorOutsideSpoilerSpan(sel, currentRange);
        const textNode = document.createTextNode(insertedData);
        safeRange.insertNode(textNode);
        const nextRange = document.createRange();
        nextRange.setStartAfter(textNode);
        nextRange.collapse(true);
        sel.removeAllRanges();
        sel.addRange(nextRange);
      }
      lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
      scheduleTextHistorySnapshot();
      return;
    } else if (range.collapsed && inputType.startsWith('insert')) {
      escapeColorOrHighlightEdgeIfAtBoundary(sel, range);
    }
  };

  const handleEditorKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (isReadingMode) return;
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    let range = sel.getRangeAt(0);

    if (range.collapsed) {
      range = ensureCursorOutsideSpoilerSpan(sel, range);
    }

    if (e.key === 'Backspace' || e.key === 'Delete') {
      const isBack = e.key === 'Backspace';
      if (!range.collapsed) {
        if (selectionContainsSpoiler(range)) {
          e.preventDefault();
          e.stopPropagation();
          deleteNonSpoilerTextInRange(range);
          if (editorRef.current) {
            lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
          }
          scheduleTextHistorySnapshot();
        }
        return;
      }

      if (isCollapsedCursorDirectlyTouchingSpoiler(range, isBack)) {
        e.preventDefault();
        e.stopPropagation();
      }
    }
  };

  // Fallback guard on input:
  // 1. If a browser/IME accidentally typed characters inside a spoiler span, extract those extra characters outside the spoiler span so the spoiler NEVER extends!
  // 2. If an IME bypassed beforeinput and deleted a spoiler span, restore it.
  const handleEditorInput = () => {
    if (!editorRef.current) return;
    const prevTemp = document.createElement('div');
    prevTemp.innerHTML = lastValidHtmlWithSpoilersRef.current;
    const prevSpoilers = prevTemp.querySelectorAll('span[data-wiki-spoiler="true"]');
    const curSpoilers = editorRef.current.querySelectorAll(
      'span[data-wiki-spoiler="true"]'
    );

    if (curSpoilers.length < prevSpoilers.length) {
      // A spoiler was deleted without unlocking/disabling via passcode — revert!
      editorRef.current.innerHTML = lastValidHtmlWithSpoilersRef.current;
      return;
    }

    // Ensure no locked spoiler span ever gets extended with typed characters at its start or end
    curSpoilers.forEach((spNode) => {
      const sp = spNode as HTMLElement;
      if (sp.classList.contains('wiki-spoiler-unlocked')) return;
      const expectedLen = parseInt(sp.getAttribute('data-spoiler-len') || '0', 10);
      if (expectedLen > 0) {
        const expectedMask = buildSpoilerMaskString(expectedLen);
        const currentText = sp.textContent || '';
        if (currentText !== expectedMask) {
          // If the browser inserted characters at the start or end inside the spoiler span, move them outside!
          const idx = currentText.indexOf(expectedMask);
          const parent = sp.parentNode;
          if (idx >= 0 && parent) {
            const prefix = currentText.slice(0, idx);
            const suffix = currentText.slice(idx + expectedMask.length);
            sp.textContent = expectedMask;
            if (prefix) {
              parent.insertBefore(document.createTextNode(prefix), sp);
            }
            if (suffix) {
              const afterNode = document.createTextNode(suffix);
              parent.insertBefore(afterNode, sp.nextSibling);
              const sel = window.getSelection();
              if (sel) {
                const r = document.createRange();
                r.setStart(afterNode, suffix.length);
                r.collapse(true);
                sel.removeAllRanges();
                sel.addRange(r);
              }
            }
          } else {
            sp.textContent = expectedMask;
          }
        }
      }
    });

    scheduleTextHistorySnapshot();
  };

  /**
   * Copy & Cut Handler for Editor:
   * - If the copied or cut selection contains spoiler words (`span[data-wiki-spoiler="true"]`),
   *   replace each spoiler word in the clipboard with `generateSpoilerSymbolMask` (`*@#&€¥%$¢π§∆`)!
   * - If Cut (`onCut`) is used: non-spoiler text in the selection is cut, while spoiler words remain untouched on Canvas (or selection cut is prevented from deleting the spoiler span).
   */
  const handleEditorCopyOrCut = (
    e: React.ClipboardEvent<HTMLDivElement>,
    isCut: boolean
  ) => {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    const range = sel.getRangeAt(0);
    if (range.collapsed) return;

    const clonedContents = range.cloneContents();
    const spoilerNodes = clonedContents.querySelectorAll(
      'span[data-wiki-spoiler="true"]'
    );

    if (spoilerNodes.length === 0) {
      return; // Normal copy/cut when no spoilers are in selection
    }

    e.preventDefault();

    // Replace every spoiler node in the cloned fragment with symbol mask (*@#&€¥%$¢π§∆)
    spoilerNodes.forEach((sp) => {
      const masked = generateSpoilerSymbolMask(sp.textContent || '');
      const textNode = document.createTextNode(masked);
      sp.parentNode?.replaceChild(textNode, sp);
    });

    const tempDiv = document.createElement('div');
    tempDiv.appendChild(clonedContents);

    const maskedPlainText = (tempDiv.textContent || tempDiv.innerText || '').replace(
      /\u200B/g,
      ''
    );
    const maskedHtml = tempDiv.innerHTML;

    e.clipboardData.setData('text/plain', maskedPlainText);
    e.clipboardData.setData('text/html', maskedHtml);

    if (isCut && !isReadingMode && editorRef.current) {
      // Remove only non-spoiler text nodes inside the selected range while keeping spoiler spans intact!
      const textNodesToClear: Text[] = [];
      const walker = document.createTreeWalker(
        range.commonAncestorContainer,
        NodeFilter.SHOW_TEXT
      );
      let currentNode: Node | null = walker.currentNode;
      while (currentNode) {
        if (
          currentNode.nodeType === Node.TEXT_NODE &&
          range.intersectsNode(currentNode)
        ) {
          // Check if inside spoiler
          let parent: Node | null = currentNode.parentNode;
          let insideSpoiler = false;
          while (parent && parent !== editorRef.current) {
            if (
              parent.nodeType === Node.ELEMENT_NODE &&
              (parent as HTMLElement).getAttribute('data-wiki-spoiler') === 'true'
            ) {
              insideSpoiler = true;
              break;
            }
            parent = parent.parentNode;
          }
          if (!insideSpoiler) {
            textNodesToClear.push(currentNode as Text);
          }
        }
        currentNode = walker.nextNode();
      }

      textNodesToClear.forEach((tNode) => {
        const full = tNode.textContent || '';
        const startOff = tNode === range.startContainer ? range.startOffset : 0;
        const endOff = tNode === range.endContainer ? range.endOffset : full.length;
        tNode.textContent = full.slice(0, startOff) + full.slice(endOff);
      });

      lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
      pushCanvasSnapshot();
    }
  };

  // Build a DiaryLog object from current workspace state:
  // - If content is empty, plainPreview stays '' (empty string, never "Reminder scheduled.")!
  // - Creation dateStamp & timeStamp NEVER change once created!
  // - Modification updatedDateStamp & updatedTimeStamp ONLY update when actual content is edited!
  const buildCurrentLogObject = (
    overrideReminderAt?: number | null,
    didModifyContent: boolean = true
  ): {
    log: DiaryLog;
    hasAnyEntry: boolean;
  } => {
    const rawHtml = editorRef.current
      ? normalizeSpoilersAndSelectionForSave(editorRef.current.innerHTML)
      : '';
    const plainPreview = stripHtmlToSingleLine(rawHtml);
    const trimmedHeading = heading.trim();
    const effectiveReminder =
      overrideReminderAt !== undefined ? overrideReminderAt : reminderAt;

    const hasAnyEntry =
      trimmedHeading.length > 0 ||
      plainPreview.length > 0 ||
      pfpDataUrl !== null ||
      canvasBgDataUrl !== null ||
      canvasImages.length > 0 ||
      audioAttachments.length > 0 ||
      effectiveReminder !== null;

    const now = Date.now();
    const createdAtMs = initialLog ? initialLog.createdAt : stableCreatedAtRef.current;

    const dateStamp = initialLog?.dateStamp || formatDateStamp(createdAtMs);
    const timeStamp = initialLog?.timeStamp || formatTimeStamp(createdAtMs);

    const updatedAtMs = didModifyContent
      ? now
      : initialLog?.updatedAt || createdAtMs;

    const updatedDateStamp = didModifyContent
      ? formatDateStamp(now)
      : initialLog?.updatedDateStamp || formatDateStamp(updatedAtMs);

    const updatedTimeStamp = didModifyContent
      ? formatTimeStamp(now)
      : initialLog?.updatedTimeStamp || formatTimeStamp(updatedAtMs);

    const log: DiaryLog = {
      id: stableLogIdRef.current,
      heading: trimmedHeading || (plainPreview.slice(0, 40) || 'Untitled Entry'),
      contentHtml: rawHtml,
      plainPreview,
      pfpDataUrl,
      createdAt: createdAtMs,
      updatedAt: updatedAtMs,
      dateStamp,
      timeStamp,
      updatedDateStamp,
      updatedTimeStamp,
      reminderAt: effectiveReminder,
      reminderFired:
        effectiveReminder && effectiveReminder > Date.now()
          ? false
          : initialLog?.reminderFired,
      pinned: initialLog?.pinned || false,
      diaryLockPin,
      canvasBgDataUrl,
      canvasBgOpacity,
      canvasImages,
      audioAttachments,
    };

    return { log, hasAnyEntry };
  };

  const handleExitWorkspace = () => {
    if (activeSpoilerSpan) {
      setActiveSpoilerSpan(null);
      return;
    }
    const contentEdited = hasContentChangedFromInitial();
    if (initialLog && !contentEdited) {
      onExitWithoutSave();
      return;
    }

    const { log, hasAnyEntry } = buildCurrentLogObject(undefined, true);
    if (hasAnyEntry) {
      scheduleAndroidNativeReminder(
        log.id,
        `Likkho: ${log.heading}`,
        log.plainPreview || log.heading,
        log.reminderAt
      );
      onSaveLog(log, true);
    } else {
      onExitWithoutSave();
    }
  };

  useEffect(() => {
    if (registerBackHandler) {
      registerBackHandler(handleExitWorkspace);
    }
  });

  const handleExplicitSave = () => {
    const contentEdited = hasContentChangedFromInitial();
    const { log, hasAnyEntry } = buildCurrentLogObject(
      undefined,
      !initialLog || contentEdited
    );
    if (!hasAnyEntry) {
      return;
    }
    scheduleAndroidNativeReminder(
      log.id,
      `Likkho: ${log.heading}`,
      log.plainPreview || log.heading,
      log.reminderAt
    );
    onSaveLog(log, false);
    setSelectedCanvasImgId(null);
    setSelectedInlineImgId(null);
    setSelectedCanvasAudioId(null);
    setIsReadingMode(true);
    setSavedIndicator(true);
    setTimeout(() => setSavedIndicator(false), 1800);
  };

  const handleImageFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setRawSelectedImage(reader.result);
        setShowPfpStudioModal(true);
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  // Handle clicks inside Editor:
  // 1. Ignore synthetic ghost clicks that fire within 420ms of double-tapping an Audio Player Card!
  // 2. Check if user clicked an inline Wikipedia / document image (`<img>`) in Edit Mode so they can edit it right in place!
  // 3. Check if user clicked a Passcode-Protected Spoiler span (`data-wiki-spoiler="true"`)
  // 4. Otherwise check if user clicked a hyperlink `<a>`
  const handleEditorClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (Date.now() - lastAudioSelectTimestampRef.current < 420) {
      return;
    }
    setSelectedCanvasImgId(null);
    setSelectedCanvasAudioId(null);
    const target = e.target as HTMLElement;

    const clickedImg = target.closest('img') as HTMLImageElement | null;
    if (clickedImg && !isReadingMode && editorRef.current?.contains(clickedImg)) {
      e.preventDefault();
      e.stopPropagation();
      if (!clickedImg.id) {
        clickedImg.id = `inline_img_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
      }
      // Highlight selected inline image
      editorRef.current.querySelectorAll('img').forEach((im) => {
        im.style.outline = im.id === clickedImg.id ? '2px solid #3366cc' : '';
        im.style.outlineOffset = im.id === clickedImg.id ? '2px' : '';
      });
      setSelectedInlineImgId(clickedImg.id);
      return;
    }

    if (selectedInlineImgId && editorRef.current) {
      editorRef.current.querySelectorAll('img').forEach((im) => {
        im.style.outline = '';
        im.style.outlineOffset = '';
      });
      setSelectedInlineImgId(null);
    }

    const latexEl = target.closest('span[data-wiki-latex="true"]') as HTMLElement | null;
    if (latexEl && !isReadingMode && editorRef.current?.contains(latexEl)) {
      e.preventDefault();
      e.stopPropagation();
      setActiveLatexSpan(latexEl);
      setEditLatexInput(latexEl.getAttribute('data-latex-src') || '');
      return;
    }

    const spoilerEl = target.closest('span[data-wiki-spoiler="true"]') as HTMLElement | null;
    if (spoilerEl) {
      e.preventDefault();
      e.stopPropagation();
      // If spoiler is currently unlocked (showing decrypted text), tapping it immediately re-locks & masks it!
      if (spoilerEl.classList.contains('wiki-spoiler-unlocked')) {
        lockAndMaskSpoilerElement(spoilerEl);
        if (editorRef.current) {
          lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
        }
        return;
      }
      setActiveSpoilerSpan(spoilerEl);
      setSpoilerUnlockInput('');
      setSpoilerUnlockError(null);
      return;
    }

    const anchor = target.closest('a') as HTMLAnchorElement | null;
    if (anchor && anchor.href) {
      e.preventDefault();
      e.stopPropagation();
      const tempLink = document.createElement('a');
      tempLink.href = anchor.href;
      tempLink.target = '_blank';
      tempLink.rel = 'noopener noreferrer';
      document.body.appendChild(tempLink);
      tempLink.click();
      document.body.removeChild(tempLink);
    }
  };

  const verifySpoilerPasscode = (): boolean => {
    if (!activeSpoilerSpan) return false;
    const rem = getRemainingPasscodeCooldownSeconds();
    if (rem > 0) {
      setSpoilerCooldownSec(rem);
      setSpoilerUnlockError(`Too many wrong attempts. Wait ${rem}s.`);
      return false;
    }
    const encodedPin = activeSpoilerSpan.getAttribute('data-spoiler-pin') || '';
    let expectedPin = '';
    try {
      expectedPin = decodeURIComponent(escape(atob(encodedPin)));
    } catch {
      expectedPin = '';
    }
    if (spoilerUnlockInput.trim() === expectedPin) {
      resetPasscodeFailures();
      setSpoilerCooldownSec(0);
      return true;
    }
    const fail = recordPasscodeFailure();
    if (fail.cooldownSeconds > 0) {
      setSpoilerCooldownSec(fail.cooldownSeconds);
      setSpoilerUnlockError(`Incorrect passcode. Locked for ${fail.cooldownSeconds}s.`);
    } else {
      setSpoilerUnlockError('Incorrect spoiler passcode.');
    }
    return false;
  };

  const getSpoilerDecryptedPlainText = (el: HTMLElement, pin: string): string => {
    const cipher = el.getAttribute('data-spoiler-cipher');
    if (cipher) {
      const dec = decryptSpoilerSecretText(cipher, pin);
      if (dec) return dec;
    }
    return el.textContent || '';
  };

  const handleVerifySpoilerView = (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeSpoilerSpan) return;
    if (verifySpoilerPasscode()) {
      const pin = spoilerUnlockInput.trim();
      const plain = getSpoilerDecryptedPlainText(activeSpoilerSpan, pin);
      activeSpoilerSpan.textContent = plain;
      activeSpoilerSpan.classList.remove('wiki-spoiler-locked');
      activeSpoilerSpan.classList.add('wiki-spoiler-unlocked');
      activeSpoilerSpan.setAttribute('contenteditable', 'false');
      setActiveSpoilerSpan(null);
      setSpoilerUnlockInput('');
      setSpoilerUnlockError(null);
    }
  };

  // Disable Spoiler with Passcode so the word becomes normal editable text again
  const handleDisableSpoilerForEditing = () => {
    if (!activeSpoilerSpan || !editorRef.current) return;
    if (!verifySpoilerPasscode()) return;

    const pin = spoilerUnlockInput.trim();
    const plain = getSpoilerDecryptedPlainText(activeSpoilerSpan, pin);
    const parent = activeSpoilerSpan.parentNode;
    if (parent) {
      const textNode = document.createTextNode(plain);
      parent.replaceChild(textNode, activeSpoilerSpan);
      lastValidHtmlWithSpoilersRef.current = editorRef.current.innerHTML;
      pushCanvasSnapshot({ contentHtml: editorRef.current.innerHTML });
    }
    setActiveSpoilerSpan(null);
    setSpoilerUnlockInput('');
    setSpoilerUnlockError(null);
  };

  // Pointer drag + 2-finger pinch resize & rotate handlers for Canvas Images (active in Edit Mode)
  const handleStartDragImage = (
    e: React.PointerEvent<HTMLDivElement>,
    img: CanvasDraggableImage
  ) => {
    if (isReadingMode) return;
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);
    setSelectedCanvasImgId(img.id);
    setSelectedCanvasAudioId(null);

    imgPointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (imgPointersRef.current.size === 2) {
      const pts = Array.from(imgPointersRef.current.values());
      const dist = Math.max(10, Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y));
      const angle = (Math.atan2(pts[1].y - pts[0].y, pts[1].x - pts[0].x) * 180) / Math.PI;
      pinchInitialRef.current = {
        id: img.id,
        dist,
        angle,
        startWidth: img.width,
        startRotation: img.rotation || 0,
      };
      setActiveDragId(null);
    } else if (imgPointersRef.current.size === 1) {
      setActiveDragId(img.id);
      setDragOffset({
        x: e.clientX - img.x,
        y: e.clientY - img.y,
      });
    }
  };

  const handleMoveDragImage = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isReadingMode) return;
    if (imgPointersRef.current.has(e.pointerId)) {
      imgPointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    }

    if (imgPointersRef.current.size === 2 && pinchInitialRef.current) {
      e.stopPropagation();
      const pts = Array.from(imgPointersRef.current.values());
      const dist = Math.max(10, Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y));
      const angle = (Math.atan2(pts[1].y - pts[0].y, pts[1].x - pts[0].x) * 180) / Math.PI;

      const scale = dist / pinchInitialRef.current.dist;
      const nextWidth = Math.min(
        420,
        Math.max(50, Math.round(pinchInitialRef.current.startWidth * scale))
      );
      const deltaAngle = angle - pinchInitialRef.current.angle;
      const nextRotation = Math.round(pinchInitialRef.current.startRotation + deltaAngle);
      const targetId = pinchInitialRef.current.id;

      setCanvasImages((prev) =>
        prev.map((item) =>
          item.id === targetId
            ? { ...item, width: nextWidth, rotation: nextRotation }
            : item
        )
      );
      return;
    }

    if (!activeDragId || imgPointersRef.current.size !== 1) return;
    e.stopPropagation();
    const nextX = Math.max(0, e.clientX - dragOffset.x);
    const nextY = Math.max(0, e.clientY - dragOffset.y);
    setCanvasImages((prev) =>
      prev.map((item) =>
        item.id === activeDragId ? { ...item, x: nextX, y: nextY } : item
      )
    );
  };

  const handleEndDragImage = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isReadingMode) return;
    imgPointersRef.current.delete(e.pointerId);
    if (imgPointersRef.current.size < 2) {
      pinchInitialRef.current = null;
    }
    if (imgPointersRef.current.size === 0) {
      setActiveDragId(null);
      pushCanvasSnapshot();
    }
  };

  const updateCanvasImagesWithHistory = (
    updater: (prev: CanvasDraggableImage[]) => CanvasDraggableImage[]
  ) => {
    setCanvasImages((prev) => {
      const next = updater(prev);
      pushCanvasSnapshot({ canvasImages: next });
      return next;
    });
  };

  const updateAudioAttachmentsWithHistory = (
    updater: (prev: CanvasAudioAttachment[]) => CanvasAudioAttachment[]
  ) => {
    setAudioAttachments((prev) => {
      const next = updater(prev);
      pushCanvasSnapshot({ audioAttachments: next });
      return next;
    });
  };

  const selectedCanvasImage =
    !isReadingMode
      ? canvasImages.find((c) => c.id === selectedCanvasImgId) || null
      : null;

  const selectedInlineImgElement: HTMLImageElement | null =
    !isReadingMode && selectedInlineImgId && editorRef.current
      ? (editorRef.current.querySelector(
          `#${CSS.escape(selectedInlineImgId)}`
        ) as HTMLImageElement | null)
      : null;

  const clearInlineImageSelection = () => {
    if (editorRef.current) {
      editorRef.current.querySelectorAll('img').forEach((im) => {
        im.style.outline = '';
        im.style.outlineOffset = '';
      });
    }
    setSelectedInlineImgId(null);
  };

  const mutateSelectedInlineImage = (fn: (el: HTMLImageElement) => void) => {
    if (!selectedInlineImgElement || !editorRef.current) return;
    fn(selectedInlineImgElement);
    setInlineImgTick((t) => t + 1);
    pushCanvasSnapshot({ contentHtml: editorRef.current.innerHTML });
  };

  const selectedCanvasAudio =
    !isReadingMode
      ? audioAttachments.find((a) => a.id === selectedCanvasAudioId) || null
      : null;

  return (
    <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Hidden File Input for Header 1:1 PFP Selection */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleImageFileSelect}
        className="hidden"
      />

      {/* Workspace Header — Solid background (z-20) so Canvas Background Image NEVER bleeds into Header */}
      <header className="relative z-20 flex min-h-[58px] shrink-0 items-center justify-between gap-2 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 py-2">
        <button
          type="button"
          onClick={handleExitWorkspace}
          className="flex h-10 w-10 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
          title="Back to Homepage"
          aria-label="Back to Homepage"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>

        {/* 1:1 Square PFP Selector + Diary Heading Input */}
        <div className="flex flex-1 items-center gap-2.5 min-w-0">
          <button
            type="button"
            disabled={isReadingMode}
            onClick={() => {
              if (isReadingMode) return;
              setRawSelectedImage(null);
              setShowPfpStudioModal(true);
            }}
            className={`group relative flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden border border-[var(--wiki-border)] bg-[var(--wiki-bg)] ${
              isReadingMode ? 'cursor-default' : 'hover:border-[#3366cc]'
            }`}
            title={
              isReadingMode
                ? 'Diary PFP'
                : 'Set Diary PFP (Photo, Emoji/Text & Background Color)'
            }
          >
            {pfpDataUrl ? (
              <img
                src={pfpDataUrl}
                alt="Diary PFP"
                referrerPolicy="no-referrer"
                className="h-full w-full object-cover"
              />
            ) : (
              <ImagePlus className="h-5 w-5 text-[var(--wiki-muted)] group-hover:text-[#3366cc]" />
            )}
          </button>

          <div className="flex flex-1 items-center gap-1.5 min-w-0">
            {diaryLockPin && (
              <Lock className="h-4 w-4 shrink-0 text-[#b32424]" title="Diary Locked" />
            )}
            <input
              type="text"
              value={heading}
              readOnly={isReadingMode}
              onChange={(e) => {
                setHeading(e.target.value);
                scheduleTextHistorySnapshot();
              }}
              placeholder="Diary Heading..."
              className="w-full border-b border-transparent bg-transparent font-wiki-serif text-lg font-bold text-[var(--wiki-text)] placeholder:text-[var(--wiki-muted)]/60 focus:border-[#3366cc] outline-none truncate"
            />
          </div>
        </div>

        {/* Reminder Button & Edit / Save Button */}
        <div className="flex shrink-0 items-center gap-1.5">
          {initialLog && onDeleteLog && (
            <button
              type="button"
              onClick={() => onDeleteLog(initialLog.id)}
              className="flex h-10 w-9 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:text-[#b32424]"
              title="Delete entry"
              aria-label="Delete entry"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}

          <button
            type="button"
            onClick={() => setShowReminderModal(true)}
            className={`flex h-10 items-center gap-1 border px-2.5 text-xs font-medium transition-colors ${
              reminderAt && reminderAt > Date.now()
                ? 'border-[#3366cc] bg-[#3366cc]/10 text-[#3366cc]'
                : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
            }`}
            title="Set Reminder Notification"
          >
            {reminderAt && reminderAt > Date.now() ? (
              <BellRing className="h-4 w-4 text-[#3366cc]" />
            ) : (
              <Bell className="h-4 w-4" />
            )}
            <span className="hidden sm:inline">Reminder</span>
          </button>

          {isReadingMode ? (
            <button
              type="button"
              onClick={() => setIsReadingMode(false)}
              className="flex h-10 items-center gap-1.5 bg-[#3366cc] px-3.5 text-xs font-semibold text-white hover:bg-[#2a56b0] active:scale-[0.98] transition-all"
            >
              <Pencil className="h-4 w-4" />
              <span>Edit</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={handleExplicitSave}
              className="flex h-10 items-center gap-1.5 bg-[#3366cc] px-3.5 text-xs font-semibold text-white hover:bg-[#2a56b0] active:scale-[0.98] transition-all"
            >
              {savedIndicator ? (
                <>
                  <Check className="h-4 w-4" />
                  <span>Saved</span>
                </>
              ) : (
                <>
                  <Save className="h-4 w-4" />
                  <span>Save</span>
                </>
              )}
            </button>
          )}
        </div>
      </header>

      {/* Active Reminder Sub-bar if scheduled */}
      {reminderAt && reminderAt > Date.now() && (
        <div className="relative z-20 flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-1.5 text-[11px]">
          <span className="font-wiki-mono text-[var(--wiki-text)]">
            🔔{' '}
            <strong>
              {new Date(reminderAt).toLocaleString('en-IN', {
                day: '2-digit',
                month: 'short',
                hour: '2-digit',
                minute: '2-digit',
                hour12: true,
              })}
            </strong>
          </span>
          <button
            type="button"
            onClick={() => setShowReminderModal(true)}
            className="font-semibold text-[#3366cc] underline"
          >
            Edit
          </button>
        </div>
      )}

      {/* Selected Canvas Image Control Bar: Resize (-/+), Rotate (Left/Right), Layer (Behind Text / Over Text), Delete */}
      {selectedCanvasImage && (
        <div className="relative z-30 flex flex-wrap items-center justify-between gap-2 border-b border-[#3366cc] bg-[var(--wiki-surface)] px-3 py-1.5 text-xs">
          <div className="flex items-center gap-1.5 overflow-x-auto">
            <button
              type="button"
              onClick={() =>
                updateCanvasImagesWithHistory((prev) =>
                  prev.map((c) =>
                    c.id === selectedCanvasImage.id
                      ? { ...c, width: Math.max(50, c.width - 20) }
                      : c
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Smaller Size"
            >
              <ZoomOut className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>Size -</span>
            </button>
            <button
              type="button"
              onClick={() =>
                updateCanvasImagesWithHistory((prev) =>
                  prev.map((c) =>
                    c.id === selectedCanvasImage.id
                      ? { ...c, width: Math.min(420, c.width + 20) }
                      : c
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Larger Size"
            >
              <ZoomIn className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>Size +</span>
            </button>

            <button
              type="button"
              onClick={() =>
                updateCanvasImagesWithHistory((prev) =>
                  prev.map((c) =>
                    c.id === selectedCanvasImage.id
                      ? { ...c, rotation: ((c.rotation || 0) - 15) % 360 }
                      : c
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Rotate Left 15°"
            >
              <RotateCcw className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>-15°</span>
            </button>
            <button
              type="button"
              onClick={() =>
                updateCanvasImagesWithHistory((prev) =>
                  prev.map((c) =>
                    c.id === selectedCanvasImage.id
                      ? { ...c, rotation: ((c.rotation || 0) + 15) % 360 }
                      : c
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Rotate Right 15°"
            >
              <RotateCw className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>+15°</span>
            </button>

            <button
              type="button"
              onClick={() => {
                setEditingCanvasImageId(selectedCanvasImage.id);
                setRawMediaStudioImage(selectedCanvasImage.dataUrl);
              }}
              className="flex h-7 items-center gap-1 border border-[#3366cc] bg-[#3366cc]/10 px-2.5 font-semibold text-[#3366cc] hover:bg-[#3366cc] hover:text-white transition-colors"
              title="Open full Image Studio (Crop, Compress, Opacity, Adjust & 22+ Filters)"
            >
              <Pencil className="h-3.5 w-3.5" />
              <span>Edit / Filters</span>
            </button>

            <button
              type="button"
              onClick={() =>
                updateCanvasImagesWithHistory((prev) =>
                  prev.map((c) =>
                    c.id === selectedCanvasImage.id
                      ? {
                          ...c,
                          layer:
                            c.layer === 'background' ? 'foreground' : 'background',
                        }
                      : c
                  )
                )
              }
              className={`flex h-7 items-center gap-1 border px-2.5 font-semibold transition-colors ${
                selectedCanvasImage.layer === 'background'
                  ? 'border-[#3366cc] bg-[#3366cc] text-white'
                  : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
              }`}
              title="Toggle whether image stays behind text or in front of text"
            >
              <Layers className="h-3.5 w-3.5" />
              <span>
                {selectedCanvasImage.layer === 'background'
                  ? 'Behind Text (BG)'
                  : 'In Front of Text (FG)'}
              </span>
            </button>
          </div>

          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => {
                updateCanvasImagesWithHistory((prev) =>
                  prev.filter((c) => c.id !== selectedCanvasImage.id)
                );
                setSelectedCanvasImgId(null);
              }}
              className="flex h-7 items-center gap-1 border border-[#b32424]/40 bg-[#b32424]/10 px-2 font-semibold text-[#b32424]"
              title="Remove image"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={() => setSelectedCanvasImgId(null)}
              className="flex h-7 items-center px-1.5 text-[var(--wiki-muted)]"
            >
              Done
            </button>
          </div>
        </div>
      )}

      {/* Selected Inline Wikipedia / Document Image Control Bar (Edits image right in its exact article place!) */}
      {selectedInlineImgElement && (
        <div className="relative z-30 flex flex-wrap items-center justify-between gap-2 border-b border-[#3366cc] bg-[var(--wiki-surface)] px-3 py-1.5 text-xs">
          <div className="flex items-center gap-1.5 overflow-x-auto">
            <button
              type="button"
              onClick={() =>
                mutateSelectedInlineImage((el) => {
                  const curW =
                    parseFloat(el.style.width || '') || el.clientWidth || 280;
                  el.style.width = `${Math.max(60, Math.round(curW - 24))}px`;
                })
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Smaller Size"
            >
              <ZoomOut className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>Size -</span>
            </button>
            <button
              type="button"
              onClick={() =>
                mutateSelectedInlineImage((el) => {
                  const curW =
                    parseFloat(el.style.width || '') || el.clientWidth || 280;
                  el.style.width = `${Math.min(640, Math.round(curW + 24))}px`;
                })
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Larger Size"
            >
              <ZoomIn className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>Size +</span>
            </button>

            <button
              type="button"
              onClick={() =>
                mutateSelectedInlineImage((el) => {
                  const m = (el.style.transform || '').match(
                    /rotate\(([-\d.]+)deg\)/i
                  );
                  const curRot = m ? parseFloat(m[1]) || 0 : 0;
                  const nextRot = (curRot - 15) % 360;
                  el.style.transform = `rotate(${nextRot}deg)`;
                })
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Rotate Left 15°"
            >
              <RotateCcw className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>-15°</span>
            </button>
            <button
              type="button"
              onClick={() =>
                mutateSelectedInlineImage((el) => {
                  const m = (el.style.transform || '').match(
                    /rotate\(([-\d.]+)deg\)/i
                  );
                  const curRot = m ? parseFloat(m[1]) || 0 : 0;
                  const nextRot = (curRot + 15) % 360;
                  el.style.transform = `rotate(${nextRot}deg)`;
                })
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Rotate Right 15°"
            >
              <RotateCw className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>+15°</span>
            </button>

            <button
              type="button"
              onClick={() => {
                setEditingInlineImgId(selectedInlineImgElement.id);
                setEditingCanvasImageId(null);
                setRawMediaStudioImage(selectedInlineImgElement.src);
              }}
              className="flex h-7 items-center gap-1 border border-[#3366cc] bg-[#3366cc]/10 px-2.5 font-semibold text-[#3366cc] hover:bg-[#3366cc] hover:text-white transition-colors"
              title="Open full Image Studio (Crop, Compress, Opacity, Adjust & 22+ Filters) right in place"
            >
              <Pencil className="h-3.5 w-3.5" />
              <span>Edit / Filters</span>
            </button>
          </div>

          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => {
                if (!editorRef.current) return;
                const fig = selectedInlineImgElement.closest('figure');
                if (fig && editorRef.current.contains(fig)) {
                  fig.remove();
                } else {
                  selectedInlineImgElement.remove();
                }
                setSelectedInlineImgId(null);
                pushCanvasSnapshot({ contentHtml: editorRef.current.innerHTML });
              }}
              className="flex h-7 items-center gap-1 border border-[#b32424]/40 bg-[#b32424]/10 px-2 font-semibold text-[#b32424]"
              title="Remove image"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={clearInlineImageSelection}
              className="flex h-7 items-center px-1.5 text-[var(--wiki-muted)]"
            >
              Done
            </button>
          </div>
        </div>
      )}

      {/* Selected Canvas Audio Player Toolbar (Activated ONLY via Double-Tap):
          - Directional Resize Buttons (Width -/+ & Height -/+) + Position Move Buttons (Left/Right/Up/Down)
          - 360° Rotation Slider (0° to 360°)
          - Speed (1x..2x), Layer (BG/FG), Delete */}
      {selectedCanvasAudio && (
        <div className="relative z-30 flex flex-col gap-2 border-b border-[#b32424] bg-[var(--wiki-surface)] px-3 py-2 text-xs">
          {/* Row 1: Any-Direction Resize Buttons (Width -/+, Height -/+), Position Arrows, Speed, Layer, Delete */}
          <div className="flex flex-wrap items-center justify-between gap-1.5">
            <div className="flex items-center gap-1 overflow-x-auto py-0.5">
              {/* Width Resize (Horizontal Direction) */}
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, width: Math.max(170, (a.width ?? 270) - 16) }
                        : a
                    )
                  )
                }
                className="flex h-7 shrink-0 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
                title="Decrease Width (Horizontal Resize -)"
              >
                <MoveHorizontal className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>W -</span>
              </button>
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, width: Math.min(440, (a.width ?? 270) + 16) }
                        : a
                    )
                  )
                }
                className="flex h-7 shrink-0 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
                title="Increase Width (Horizontal Resize +)"
              >
                <MoveHorizontal className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>W +</span>
              </button>

              {/* Height Resize (Vertical Direction) */}
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, height: Math.max(46, (a.height ?? 56) - 8) }
                        : a
                    )
                  )
                }
                className="flex h-7 shrink-0 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
                title="Decrease Height (Vertical Resize -)"
              >
                <MoveVertical className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>H -</span>
              </button>
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, height: Math.min(180, (a.height ?? 56) + 8) }
                        : a
                    )
                  )
                }
                className="flex h-7 shrink-0 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
                title="Increase Height (Vertical Resize +)"
              >
                <MoveVertical className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>H +</span>
              </button>

              {/* Top / Bottom / Left / Right Edge Expand-Shrink & Position Nudge Buttons */}
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, x: Math.max(0, (a.x ?? 24) - 16) }
                        : a
                    )
                  )
                }
                className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]"
                title="Move Left"
              >
                <ArrowLeft className="h-3.5 w-3.5 text-[#3366cc]" />
              </button>
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, x: (a.x ?? 24) + 16 }
                        : a
                    )
                  )
                }
                className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]"
                title="Move Right"
              >
                <ArrowRight className="h-3.5 w-3.5 text-[#3366cc]" />
              </button>
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, y: Math.max(0, (a.y ?? 140) - 16) }
                        : a
                    )
                  )
                }
                className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]"
                title="Move Up"
              >
                <ArrowUp className="h-3.5 w-3.5 text-[#3366cc]" />
              </button>
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, y: (a.y ?? 140) + 16 }
                        : a
                    )
                  )
                }
                className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]"
                title="Move Down"
              >
                <ArrowDown className="h-3.5 w-3.5 text-[#3366cc]" />
              </button>

              {/* Playback Speed Button in Audio Toolbar */}
              <button
                type="button"
                onClick={() => {
                  const curSpeed = selectedCanvasAudio.playbackRate || 1;
                  const nextIdx =
                    (AUDIO_SPEED_OPTIONS.indexOf(curSpeed) + 1) % AUDIO_SPEED_OPTIONS.length;
                  const nextSpeed = AUDIO_SPEED_OPTIONS[nextIdx];
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? { ...a, playbackRate: nextSpeed }
                        : a
                    )
                  );
                }}
                className="flex h-7 shrink-0 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-wiki-mono font-bold text-[var(--wiki-text)] hover:border-[#3366cc]"
                title="Change Playback Speed"
              >
                <Gauge className="h-3.5 w-3.5 text-[#b32424]" />
                <span>Speed {selectedCanvasAudio.playbackRate || 1}x</span>
              </button>

              {/* Background vs Foreground Toggle for Audio Card */}
              <button
                type="button"
                onClick={() =>
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.map((a) =>
                      a.id === selectedCanvasAudio.id
                        ? {
                            ...a,
                            layer:
                              a.layer === 'background' ? 'foreground' : 'background',
                          }
                        : a
                    )
                  )
                }
                className={`flex h-7 shrink-0 items-center gap-1 border px-2.5 font-semibold transition-colors ${
                  selectedCanvasAudio.layer === 'background'
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                }`}
                title="Toggle whether audio card stays behind text or in front of text"
              >
                <Layers className="h-3.5 w-3.5" />
                <span>
                  {selectedCanvasAudio.layer === 'background'
                    ? 'Behind Text (BG)'
                    : 'In Front of Text (FG)'}
                </span>
              </button>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => {
                  updateAudioAttachmentsWithHistory((prev) =>
                    prev.filter((a) => a.id !== selectedCanvasAudio.id)
                  );
                  setSelectedCanvasAudioId(null);
                }}
                className="flex h-7 items-center gap-1 border border-[#b32424] bg-[#b32424] px-2.5 font-semibold text-white hover:bg-[#941d1d]"
                title="Delete audio"
              >
                <Trash2 className="h-3.5 w-3.5" />
                <span>Delete</span>
              </button>
              <button
                type="button"
                onClick={() => setSelectedCanvasAudioId(null)}
                className="flex h-7 items-center px-1.5 text-[var(--wiki-muted)]"
              >
                Done
              </button>
            </div>
          </div>

          {/* Row 2: 360° Rotation Slider (0° to 360°) */}
          <div className="flex items-center gap-2.5 border-t border-[var(--wiki-hairline)] pt-1.5">
            <span className="shrink-0 font-wiki-mono text-[11px] font-semibold text-[var(--wiki-text)]">
              Rotate: {selectedCanvasAudio.rotation ?? 0}°
            </span>
            <input
              type="range"
              min={0}
              max={360}
              step={1}
              value={selectedCanvasAudio.rotation ?? 0}
              onChange={(e) => {
                const deg = parseInt(e.target.value, 10) || 0;
                setAudioAttachments((prev) =>
                  prev.map((a) =>
                    a.id === selectedCanvasAudio.id ? { ...a, rotation: deg } : a
                  )
                );
              }}
              onPointerUp={() => pushCanvasSnapshot()}
              className="h-1.5 min-w-0 flex-1 cursor-pointer accent-[#b32424]"
            />
            <button
              type="button"
              onClick={() =>
                updateAudioAttachmentsWithHistory((prev) =>
                  prev.map((a) =>
                    a.id === selectedCanvasAudio.id ? { ...a, rotation: 0 } : a
                  )
                )
              }
              className="shrink-0 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 py-0.5 font-wiki-mono text-[10px] font-semibold"
            >
              0°
            </button>
          </div>
        </div>
      )}

      {/* Canvas Viewport Wrapper — Background Image is strictly scoped inside this Canvas area (never in Header!) */}
      <div className="relative flex-1 overflow-hidden flex flex-col">
        {canvasBgDataUrl && (
          <div
            className="pointer-events-none absolute inset-0 z-0 bg-cover bg-center bg-no-repeat"
            style={{
              backgroundImage: `url(${canvasBgDataUrl})`,
              opacity: canvasBgOpacity,
            }}
          />
        )}

        <div
          ref={canvasContainerRef}
          className={`relative z-10 flex-1 overflow-y-auto px-4 py-5 sm:px-8 ${
            isReadingMode ? 'cursor-default' : 'cursor-text'
          }`}
          onClick={(e) => {
            if (Date.now() - lastAudioSelectTimestampRef.current < 420) {
              return;
            }
            const target = e.target as HTMLElement;
            if (target.closest('[data-canvas-audio-card="true"]')) {
              return;
            }
            if (!target.closest('img')) {
              clearInlineImageSelection();
            }
            setSelectedCanvasImgId(null);
            setSelectedCanvasAudioId(null);
            if (!isReadingMode && e.target === e.currentTarget && editorRef.current) {
              editorRef.current.focus({ preventScroll: true });
            }
          }}
        >
          {/* Free-Draggable, Pinch-Resizable & Pinch-Rotatable Images Layer on Canvas */}
          {canvasImages.map((img) => {
            const isSelected = !isReadingMode && selectedCanvasImgId === img.id;
            const isBehindText = img.layer === 'background';
            const computedZIndex = isSelected ? 25 : isBehindText ? 5 : 20;

            return (
              <div
                key={img.id}
                onPointerDown={(e) => handleStartDragImage(e, img)}
                onPointerMove={handleMoveDragImage}
                onPointerUp={handleEndDragImage}
                onPointerCancel={handleEndDragImage}
                onClick={(e) => {
                  if (isReadingMode) return;
                  e.stopPropagation();
                  setSelectedCanvasImgId(img.id);
                  setSelectedCanvasAudioId(null);
                }}
                style={{
                  position: 'absolute',
                  left: `${img.x}px`,
                  top: `${img.y}px`,
                  width: `${img.width}px`,
                  opacity: img.opacity ?? 1,
                  transform: `rotate(${img.rotation || 0}deg)`,
                  transformOrigin: 'center center',
                  zIndex: computedZIndex,
                }}
                className={`group touch-none select-none ${
                  isReadingMode
                    ? 'pointer-events-none'
                    : isSelected
                    ? 'cursor-move ring-2 ring-[#3366cc]'
                    : 'cursor-move hover:ring-1 hover:ring-[var(--wiki-border)]'
                }`}
              >
                <img
                  src={img.dataUrl}
                  alt="Canvas media"
                  referrerPolicy="no-referrer"
                  draggable={false}
                  className="block h-auto w-full pointer-events-none"
                />
              </div>
            );
          })}

          {/* Opaque Red Audio Players on Canvas — Activated ONLY via Double-Tap (No Drag/Pinch on Canvas) */}
          {audioAttachments.map((aud) => (
            <CanvasAudioPlayerCard
              key={aud.id}
              audio={aud}
              isReadingMode={isReadingMode}
              isSelected={!isReadingMode && selectedCanvasAudioId === aud.id}
              onSelect={() => {
                selectAudioCardSafely(aud.id);
              }}
            />
          ))}

          <div className="relative z-10 mx-auto max-w-3xl pointer-events-none">
            {/* Rich Text Editable Canvas */}
            <div
              ref={editorRef}
              contentEditable={!isReadingMode}
              suppressContentEditableWarning
              onBeforeInput={handleEditorBeforeInput}
              onKeyDown={handleEditorKeyDown}
              onInput={handleEditorInput}
              onCopy={(e) => handleEditorCopyOrCut(e, false)}
              onCut={(e) => handleEditorCopyOrCut(e, true)}
              onClick={(e) => {
                handleEditorClick(e);
              }}
              onDoubleClick={(e) => {
                if (isReadingMode) return;
                const rect = canvasContainerRef.current?.getBoundingClientRect();
                if (!rect) return;
                const clickX =
                  e.clientX - rect.left + (canvasContainerRef.current?.scrollLeft || 0);
                const clickY =
                  e.clientY - rect.top + (canvasContainerRef.current?.scrollTop || 0);

                // Check if user double-clicked over a background-layer Audio Card
                const hitBgAudio = audioAttachments.find((aud) => {
                  const ax = aud.x ?? 24;
                  const ay = aud.y ?? 140;
                  const aw = aud.width ?? 270;
                  const ah = aud.height ?? 56;
                  return (
                    aud.layer === 'background' &&
                    clickX >= ax &&
                    clickX <= ax + aw &&
                    clickY >= ay &&
                    clickY <= ay + ah
                  );
                });
                if (hitBgAudio) {
                  if (document.activeElement instanceof HTMLElement) {
                    document.activeElement.blur();
                  }
                  window.getSelection()?.removeAllRanges();
                  selectAudioCardSafely(hitBgAudio.id);
                  return;
                }

                // Check if user double-clicked over a background-layer Image
                const hitBgImg = canvasImages.find(
                  (img) =>
                    img.layer === 'background' &&
                    clickX >= img.x &&
                    clickX <= img.x + img.width &&
                    clickY >= img.y &&
                    clickY <= img.y + (img.height || img.width)
                );
                if (hitBgImg) {
                  setSelectedCanvasImgId(hitBgImg.id);
                  setSelectedCanvasAudioId(null);
                }
              }}
              data-placeholder="Start writing..."
              className="wiki-editor-content min-h-[65vh] pb-20 pointer-events-auto"
            />
          </div>
        </div>
      </div>

      {/* Rich Text Editing Toolbar — Visible ONLY in Edit Mode */}
      {!isReadingMode && (
        <RichTextToolbar
          editorRef={editorRef}
          onContentChange={() => {
            pushCanvasSnapshot();
          }}
          onUndoCanvas={handleCanvasUndo}
          onRedoCanvas={handleCanvasRedo}
          canvasBgDataUrl={canvasBgDataUrl}
          canvasBgOpacity={canvasBgOpacity}
          onChangeCanvasBg={(dataUrl, opacity) => {
            setCanvasBgDataUrl(dataUrl);
            setCanvasBgOpacity(opacity);
            pushCanvasSnapshot({
              canvasBgDataUrl: dataUrl,
              canvasBgOpacity: opacity,
            });
          }}
          onOpenBgImageStudio={(rawBgDataUrl) => setRawBgStudioImage(rawBgDataUrl)}
          onOpenMediaImageStudio={(rawDataUrl) => {
            setEditingCanvasImageId(null);
            setRawMediaStudioImage(rawDataUrl);
          }}
          onOpenMediaAudioStudio={(rawAudioDataUrl, fileName, ext) => {
            const validExts: AudioFormatOption[] = [
              'wav',
              'flac',
              'm4a',
              'aac',
              'mp3',
              'ogg',
              'webm',
            ];
            const fmt: AudioFormatOption = validExts.includes(ext as AudioFormatOption)
              ? (ext as AudioFormatOption)
              : 'wav';
            setRawMediaStudioAudio({
              dataUrl: rawAudioDataUrl,
              fileName,
              format: fmt,
            });
          }}
          onAddAudioAttachment={(aud) => {
            setAudioAttachments((prev) => {
              const next: CanvasAudioAttachment[] = [
                ...prev,
                {
                  ...aud,
                  x: 24 + (prev.length * 18) % 90,
                  y: 120 + (prev.length * 76) % 260,
                  width: 270,
                  height: 56,
                  rotation: 0,
                  playbackRate: 1,
                  layer: 'foreground',
                },
              ];
              pushCanvasSnapshot({ audioAttachments: next });
              return next;
            });
            selectAudioCardSafely(aud.id);
          }}
          customFonts={customFonts}
          onAddCustomFont={onAddCustomFont}
          micSettings={micSettings}
          vaultMode={vaultMode}
          diaryLockPin={diaryLockPin}
          onChangeDiaryLockPin={(pin) => {
            setDiaryLockPin(pin);
            pushCanvasSnapshot({ diaryLockPin: pin });
          }}
          onImportDocument={(importedTitle, importedHtml, editableImages) => {
            const nextHeading = heading.trim() ? heading : importedTitle;
            if (!heading.trim() && importedTitle) {
              setHeading(importedTitle);
            }
            let nextCanvasImages = canvasImages;
            if (editableImages && editableImages.length > 0) {
              const nowBase = Date.now();
              const addedItems: CanvasDraggableImage[] = editableImages.map(
                (dataUrl, idx) => ({
                  id: `wiki_img_${nowBase}_${idx}`,
                  dataUrl,
                  x: 24 + ((canvasImages.length + idx) * 24) % 120,
                  y: 64 + ((canvasImages.length + idx) * 140) % 420,
                  width: 240,
                  height: 180,
                  opacity: 1,
                  rotation: 0,
                  layer: 'foreground',
                })
              );
              nextCanvasImages = [...canvasImages, ...addedItems];
              setCanvasImages(nextCanvasImages);
              if (addedItems.length > 0) {
                setSelectedCanvasImgId(addedItems[0].id);
                setSelectedCanvasAudioId(null);
              }
            }
            if (editorRef.current) {
              const current = editorRef.current.innerHTML.trim();
              const combined = current ? `${current}<hr>${importedHtml}` : importedHtml;
              editorRef.current.innerHTML = combined;
              pushCanvasSnapshot({
                heading: nextHeading,
                contentHtml: combined,
                canvasImages: nextCanvasImages,
              });
            }
          }}
        />
      )}

      {/* Unlock / Disable Spoiler Text Passcode Modal */}
      {activeSpoilerSpan && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs"
          onClick={() => setActiveSpoilerSpan(null)}
        >
          <div
            className="w-full max-w-xs border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <div className="flex items-center gap-2">
                <Eye className="h-4 w-4 text-[#3366cc]" />
                <h3 className="font-wiki-serif text-base font-bold">
                  Spoiler Passcode
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setActiveSpoilerSpan(null)}
                className="flex h-7 w-7 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleVerifySpoilerView} className="p-4 space-y-3">
              {spoilerCooldownSec > 0 ? (
                <p className="text-xs font-semibold text-[#b32424]">
                  Locked for {spoilerCooldownSec}s due to continuous wrong attempts.
                </p>
              ) : (
                spoilerUnlockError && (
                  <p className="text-xs font-medium text-[#b32424]">
                    {spoilerUnlockError}
                  </p>
                )
              )}
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                  Enter Spoiler Passcode
                </label>
                <input
                  type="password"
                  value={spoilerUnlockInput}
                  disabled={spoilerCooldownSec > 0}
                  onChange={(e) => {
                    setSpoilerUnlockInput(e.target.value);
                    setSpoilerUnlockError(null);
                  }}
                  placeholder="Passcode..."
                  className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc] disabled:opacity-50"
                  autoFocus
                />
              </div>
              <div className="flex flex-wrap items-center justify-end gap-1.5 pt-1">
                <button
                  type="button"
                  onClick={() => setActiveSpoilerSpan(null)}
                  className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 text-xs font-medium text-[var(--wiki-text)]"
                >
                  Cancel
                </button>
                {!isReadingMode && (
                  <button
                    type="button"
                    disabled={spoilerCooldownSec > 0}
                    onClick={handleDisableSpoilerForEditing}
                    className="flex h-9 items-center gap-1 border border-[#b32424] bg-[#b32424] px-2.5 text-xs font-semibold text-white disabled:opacity-50"
                    title="Disable spoiler passcode to edit or delete this word"
                  >
                    <Unlock className="h-3.5 w-3.5" />
                    <span>Disable Spoiler</span>
                  </button>
                )}
                <button
                  type="submit"
                  disabled={spoilerCooldownSec > 0}
                  className="h-9 bg-[#3366cc] px-3 text-xs font-semibold text-white disabled:opacity-50"
                >
                  {spoilerCooldownSec > 0 ? `Wait ${spoilerCooldownSec}s` : 'View Text'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Existing Inline LaTeX Formula Modal */}
      {activeLatexSpan && !isReadingMode && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs"
          onClick={() => setActiveLatexSpan(null)}
        >
          <div
            className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
              <h3 className="font-wiki-serif text-base font-bold">
                Edit LaTeX Formula
              </h3>
              <button
                type="button"
                onClick={() => setActiveLatexSpan(null)}
                className="flex h-7 w-7 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!activeLatexSpan || !editorRef.current) return;
                const cleaned = editLatexInput.trim();
                if (!cleaned) {
                  activeLatexSpan.parentNode?.removeChild(activeLatexSpan);
                } else {
                  activeLatexSpan.setAttribute('data-latex-src', cleaned);
                  activeLatexSpan.innerHTML = renderLatexExpressionToHtml(cleaned);
                }
                pushCanvasSnapshot({ contentHtml: editorRef.current.innerHTML });
                setActiveLatexSpan(null);
              }}
              className="p-4 space-y-3"
            >
              <div>
                <label className="mb-1 block text-xs font-semibold text-[var(--wiki-text)]">
                  LaTeX Expression
                </label>
                <input
                  type="text"
                  value={editLatexInput}
                  onChange={(e) => setEditLatexInput(e.target.value)}
                  placeholder="e.g. \frac{a}{b}, \sum_{i=1}^{n} x_i"
                  className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 font-wiki-mono text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                  autoFocus
                />
              </div>

              {editLatexInput.trim().length > 0 && (
                <div className="flex items-center gap-2 border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-3 py-2 text-xs">
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-[var(--wiki-muted)]">
                    Preview:
                  </span>
                  <span
                    className="wiki-latex-formula text-sm text-[var(--wiki-text)]"
                    dangerouslySetInnerHTML={{
                      __html: renderLatexExpressionToHtml(editLatexInput),
                    }}
                  />
                </div>
              )}

              <div className="flex items-center justify-between gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    if (activeLatexSpan && editorRef.current) {
                      activeLatexSpan.parentNode?.removeChild(activeLatexSpan);
                      pushCanvasSnapshot({ contentHtml: editorRef.current.innerHTML });
                    }
                    setActiveLatexSpan(null);
                  }}
                  className="flex h-9 items-center gap-1 border border-[#b32424]/40 bg-[#b32424]/10 px-2.5 text-xs font-semibold text-[#b32424]"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  <span>Delete</span>
                </button>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setActiveLatexSpan(null)}
                    className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-medium text-[var(--wiki-text)]"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="h-9 bg-[#3366cc] px-3.5 text-xs font-semibold text-white"
                  >
                    Update Formula
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 1:1 Header PFP Studio Modal (Image Cropper + Emoji/Text & Background Color) */}
      {(showPfpStudioModal || rawSelectedImage) && (
        <ImageCropperModal
          imageSrc={rawSelectedImage}
          currentPfpDataUrl={pfpDataUrl}
          onCancel={() => {
            setRawSelectedImage(null);
            setShowPfpStudioModal(false);
          }}
          onCropComplete={(croppedUrl) => {
            setPfpDataUrl(croppedUrl);
            setRawSelectedImage(null);
            setShowPfpStudioModal(false);
            pushCanvasSnapshot({ pfpDataUrl: croppedUrl });
          }}
        />
      )}

      {/* Canvas Background Image Studio Modal (Same Compression, Resolution, Quality, Bit Depth, Color Space, EXIF, Crop, Transparency & Filters as Canvas Image) */}
      {rawBgStudioImage && (
        <MediaImageStudioModal
          imageSrc={rawBgStudioImage}
          onCancel={() => setRawBgStudioImage(null)}
          onConfirm={(processedDataUrl, opacity) => {
            const nextOpacity =
              typeof opacity === 'number' && opacity > 0 && opacity < 1
                ? opacity
                : canvasBgOpacity;
            setCanvasBgDataUrl(processedDataUrl);
            setCanvasBgOpacity(nextOpacity);
            pushCanvasSnapshot({
              canvasBgDataUrl: processedDataUrl,
              canvasBgOpacity: nextOpacity,
            });
            setRawBgStudioImage(null);
          }}
        />
      )}

      {/* Media Picker & Canvas Image Studio Modal (Format, Crop, Transparency, Adjust & 22+ Filters) */}
      {rawMediaStudioImage && (
        <MediaImageStudioModal
          imageSrc={rawMediaStudioImage}
          onCancel={() => {
            setRawMediaStudioImage(null);
            setEditingCanvasImageId(null);
            setEditingInlineImgId(null);
          }}
          onConfirm={(processedDataUrl, opacity, width, height) => {
            if (editingInlineImgId && editorRef.current) {
              const inlineEl = editorRef.current.querySelector(
                `#${CSS.escape(editingInlineImgId)}`
              ) as HTMLImageElement | null;
              if (inlineEl) {
                inlineEl.src = processedDataUrl;
                inlineEl.style.opacity = String(opacity ?? 1);
                if (width && width > 0) {
                  inlineEl.style.width = `${width}px`;
                }
              }
              setSelectedInlineImgId(editingInlineImgId);
              setEditingInlineImgId(null);
              setRawMediaStudioImage(null);
              setInlineImgTick((t) => t + 1);
              pushCanvasSnapshot({ contentHtml: editorRef.current.innerHTML });
              return;
            }

            if (editingCanvasImageId) {
              setCanvasImages((prev) => {
                const next = prev.map((item) =>
                  item.id === editingCanvasImageId
                    ? {
                        ...item,
                        dataUrl: processedDataUrl,
                        opacity,
                        width: width || item.width,
                        height: height || item.height,
                      }
                    : item
                );
                pushCanvasSnapshot({ canvasImages: next });
                return next;
              });
              setSelectedCanvasImgId(editingCanvasImageId);
              setEditingCanvasImageId(null);
              setRawMediaStudioImage(null);
              return;
            }

            const newId = `img_${Date.now()}`;
            setCanvasImages((prev) => {
              const next: CanvasDraggableImage[] = [
                ...prev,
                {
                  id: newId,
                  dataUrl: processedDataUrl,
                  x: 24 + (prev.length * 18) % 100,
                  y: 36 + (prev.length * 24) % 120,
                  width,
                  height,
                  opacity,
                  rotation: 0,
                  layer: 'foreground',
                },
              ];
              pushCanvasSnapshot({ canvasImages: next });
              return next;
            });
            setSelectedCanvasImgId(newId);
            setSelectedCanvasAudioId(null);
            setRawMediaStudioImage(null);
          }}
        />
      )}

      {/* Media Picker Audio Studio Modal (Bitrate, Audio Codec, Mono/Stereo Channels, Sampling Rate) */}
      {rawMediaStudioAudio && (
        <MediaAudioStudioModal
          audioDataUrl={rawMediaStudioAudio.dataUrl}
          audioFileName={rawMediaStudioAudio.fileName}
          initialFormat={rawMediaStudioAudio.format}
          onCancel={() => setRawMediaStudioAudio(null)}
          onConfirm={(processedDataUrl, finalFileName, format, durationSec) => {
            const newId = `aud_${Date.now()}`;
            setAudioAttachments((prev) => {
              const next: CanvasAudioAttachment[] = [
                ...prev,
                {
                  id: newId,
                  name: finalFileName,
                  format,
                  dataUrl: processedDataUrl,
                  durationSec,
                  createdAt: Date.now(),
                  x: 24 + (prev.length * 18) % 90,
                  y: 120 + (prev.length * 76) % 260,
                  width: 270,
                  height: 56,
                  rotation: 0,
                  playbackRate: 1,
                  layer: 'foreground',
                },
              ];
              pushCanvasSnapshot({ audioAttachments: next });
              return next;
            });
            selectAudioCardSafely(newId);
            setRawMediaStudioAudio(null);
          }}
        />
      )}

      {/* Reminder Date & Time Modal — Preserves creation & modification stamps when only reminder is changed */}
      {showReminderModal && (
        <ReminderModal
          currentReminder={reminderAt}
          heading={heading}
          onClose={() => setShowReminderModal(false)}
          onSaveReminder={(ts) => {
            setReminderAt(ts);
            const contentEdited = hasContentChangedFromInitial();
            const { log } = buildCurrentLogObject(ts, !initialLog || contentEdited);
            scheduleAndroidNativeReminder(
              log.id,
              `Likkho: ${log.heading}`,
              log.plainPreview || log.heading,
              ts
            );
            onSaveLog(log, false);
          }}
        />
      )}
    </div>
  );
};

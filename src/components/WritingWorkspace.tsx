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
  X,
  Gauge,
} from 'lucide-react';
import {
  CanvasAudioAttachment,
  CanvasDraggableImage,
  CustomFontItem,
  DiaryLog,
  MicRecordingSettings,
} from '../utils/cryptoVault';
import { scheduleAndroidNativeReminder } from '../utils/notificationSound';
import { ImageCropperModal } from './ImageCropperModal';
import { ReminderModal } from './ReminderModal';
import { RichTextToolbar } from './RichTextToolbar';
import { MediaImageStudioModal } from './MediaImageStudioModal';
import { CanvasAudioPlayerCard } from './CanvasAudioPlayerCard';

interface WritingWorkspaceProps {
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
    el.classList.remove('wiki-spoiler-unlocked');
    el.classList.add('wiki-spoiler-locked');
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

  // Header 1:1 PFP Cropper state
  const [rawSelectedImage, setRawSelectedImage] = useState<string | null>(null);

  // Media Picker Image Studio state
  const [rawMediaStudioImage, setRawMediaStudioImage] = useState<string | null>(null);

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

  // Free-dragging + 2-finger pinch resize & rotate state for Canvas Audio Players
  const [selectedCanvasAudioId, setSelectedCanvasAudioId] = useState<string | null>(null);
  const [activeAudioDragId, setActiveAudioDragId] = useState<string | null>(null);
  const [audioDragOffset, setAudioDragOffset] = useState<{ x: number; y: number }>({
    x: 0,
    y: 0,
  });
  const audioPointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());
  const audioPinchInitialRef = useRef<{
    id: string;
    dist: number;
    angle: number;
    startWidth: number;
    startRotation: number;
  } | null>(null);

  // Spoiler Unlock Modal state (when user taps a blurred spoiler span on the Canvas)
  const [activeSpoilerSpan, setActiveSpoilerSpan] = useState<HTMLElement | null>(null);
  const [spoilerUnlockInput, setSpoilerUnlockInput] = useState<string>('');
  const [spoilerUnlockError, setSpoilerUnlockError] = useState<string | null>(null);

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
    }
    const normalizedAudios = (initialLog?.audioAttachments || []).map((a, idx) => ({
      ...a,
      x: a.x ?? 24 + (idx * 20) % 80,
      y: a.y ?? 140 + idx * 88,
      width: a.width ?? 270,
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

  // Check whether the user actually modified any diary content compared to initialLog
  const hasContentChangedFromInitial = (): boolean => {
    if (!initialLog) return true;
    const initialSnap = historyStackRef.current[0];
    if (!initialSnap) return false;
    const currentHtml = editorRef.current
      ? normalizeSpoilersToLockedForSave(editorRef.current.innerHTML)
      : '';
    const initialNormalizedHtml = normalizeSpoilersToLockedForSave(initialSnap.contentHtml);

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
    if (textDebounceTimerRef.current) {
      window.clearTimeout(textDebounceTimerRef.current);
    }
    textDebounceTimerRef.current = window.setTimeout(() => {
      pushCanvasSnapshot();
      textDebounceTimerRef.current = null;
    }, 350);
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
      ? normalizeSpoilersToLockedForSave(editorRef.current.innerHTML)
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

    // Original Creation Date & Time Stamp — strictly locked to createdAt!
    const dateStamp = initialLog?.dateStamp || formatDateStamp(createdAtMs);
    const timeStamp = initialLog?.timeStamp || formatTimeStamp(createdAtMs);

    // Modification Date & Time Stamp — only updates when the user actually edits the entry!
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
      plainPreview, // Strictly empty '' if no text content!
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

  // Handle Back / Exit:
  // - If viewing an existing log without any edits, exit WITHOUT modifying timestamps!
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

  // Handle explicit Save button tap
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
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  // Handle clicks inside Editor:
  // 1. Check if user clicked a Passcode-Protected Spoiler span (`data-wiki-spoiler="true"`)
  // 2. Otherwise check if user clicked a hyperlink `<a>`
  const handleEditorClick = (e: React.MouseEvent<HTMLDivElement>) => {
    setSelectedCanvasImgId(null);
    setSelectedCanvasAudioId(null);
    const target = e.target as HTMLElement;

    const spoilerEl = target.closest('span[data-wiki-spoiler="true"]') as HTMLElement | null;
    if (spoilerEl) {
      e.preventDefault();
      e.stopPropagation();
      if (spoilerEl.classList.contains('wiki-spoiler-unlocked')) {
        spoilerEl.classList.remove('wiki-spoiler-unlocked');
        spoilerEl.classList.add('wiki-spoiler-locked');
      } else {
        setActiveSpoilerSpan(spoilerEl);
        setSpoilerUnlockInput('');
        setSpoilerUnlockError(null);
      }
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

  const handleVerifySpoilerUnlock = (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeSpoilerSpan) return;
    const encodedPin = activeSpoilerSpan.getAttribute('data-spoiler-pin') || '';
    let expectedPin = '';
    try {
      expectedPin = decodeURIComponent(escape(atob(encodedPin)));
    } catch {
      expectedPin = '';
    }

    if (spoilerUnlockInput.trim() === expectedPin) {
      activeSpoilerSpan.classList.remove('wiki-spoiler-locked');
      activeSpoilerSpan.classList.add('wiki-spoiler-unlocked');
      setActiveSpoilerSpan(null);
      setSpoilerUnlockInput('');
      setSpoilerUnlockError(null);
    } else {
      setSpoilerUnlockError('Incorrect spoiler passcode.');
    }
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
      const nextWidth = Math.min(420, Math.max(50, Math.round(pinchInitialRef.current.startWidth * scale)));
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

  // Pointer drag + 2-finger pinch resize & rotate handlers for Canvas Audio Player Cards (active in Edit Mode)
  const handleStartDragAudio = (
    e: React.PointerEvent<HTMLDivElement>,
    aud: CanvasAudioAttachment
  ) => {
    if (isReadingMode) return;
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);
    setSelectedCanvasAudioId(aud.id);
    setSelectedCanvasImgId(null);

    audioPointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (audioPointersRef.current.size === 2) {
      const pts = Array.from(audioPointersRef.current.values());
      const dist = Math.max(10, Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y));
      const angle = (Math.atan2(pts[1].y - pts[0].y, pts[1].x - pts[0].x) * 180) / Math.PI;
      audioPinchInitialRef.current = {
        id: aud.id,
        dist,
        angle,
        startWidth: aud.width ?? 270,
        startRotation: aud.rotation ?? 0,
      };
      setActiveAudioDragId(null);
    } else if (audioPointersRef.current.size === 1) {
      setActiveAudioDragId(aud.id);
      setAudioDragOffset({
        x: e.clientX - (aud.x ?? 24),
        y: e.clientY - (aud.y ?? 140),
      });
    }
  };

  const handleMoveDragAudio = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isReadingMode) return;
    if (audioPointersRef.current.has(e.pointerId)) {
      audioPointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    }

    if (audioPointersRef.current.size === 2 && audioPinchInitialRef.current) {
      e.stopPropagation();
      const pts = Array.from(audioPointersRef.current.values());
      const dist = Math.max(10, Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y));
      const angle = (Math.atan2(pts[1].y - pts[0].y, pts[1].x - pts[0].x) * 180) / Math.PI;

      const scale = dist / audioPinchInitialRef.current.dist;
      const nextWidth = Math.min(
        420,
        Math.max(190, Math.round(audioPinchInitialRef.current.startWidth * scale))
      );
      const deltaAngle = angle - audioPinchInitialRef.current.angle;
      const nextRotation = Math.round(audioPinchInitialRef.current.startRotation + deltaAngle);
      const targetId = audioPinchInitialRef.current.id;

      setAudioAttachments((prev) =>
        prev.map((item) =>
          item.id === targetId
            ? { ...item, width: nextWidth, rotation: nextRotation }
            : item
        )
      );
      return;
    }

    if (!activeAudioDragId || audioPointersRef.current.size !== 1) return;
    e.stopPropagation();
    const nextX = Math.max(0, e.clientX - audioDragOffset.x);
    const nextY = Math.max(0, e.clientY - audioDragOffset.y);
    setAudioAttachments((prev) =>
      prev.map((item) =>
        item.id === activeAudioDragId ? { ...item, x: nextX, y: nextY } : item
      )
    );
  };

  const handleEndDragAudio = (e: React.PointerEvent<HTMLDivElement>) => {
    if (isReadingMode) return;
    audioPointersRef.current.delete(e.pointerId);
    if (audioPointersRef.current.size < 2) {
      audioPinchInitialRef.current = null;
    }
    if (audioPointersRef.current.size === 0) {
      setActiveAudioDragId(null);
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
              if (window.LikkhoNative?.requestFilesAndMediaPermission) {
                window.LikkhoNative.requestFilesAndMediaPermission();
              }
              fileInputRef.current?.click();
            }}
            className={`group relative flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden border border-[var(--wiki-border)] bg-[var(--wiki-bg)] ${
              isReadingMode ? 'cursor-default' : 'hover:border-[#3366cc]'
            }`}
            title={isReadingMode ? 'Diary PFP' : 'Select & crop 1:1 image from storage'}
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

      {/* Selected Canvas Audio Player Toolbar: Size (-/+), Rotate (-15°/+15°), Speed (1x..2x), Layer (BG/FG), Delete */}
      {selectedCanvasAudio && (
        <div className="relative z-30 flex flex-wrap items-center justify-between gap-2 border-b border-[#b32424] bg-[var(--wiki-surface)] px-3 py-1.5 text-xs">
          <div className="flex items-center gap-1.5 overflow-x-auto">
            <button
              type="button"
              onClick={() =>
                updateAudioAttachmentsWithHistory((prev) =>
                  prev.map((a) =>
                    a.id === selectedCanvasAudio.id
                      ? { ...a, width: Math.max(190, (a.width ?? 270) - 20) }
                      : a
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Smaller Audio Player"
            >
              <ZoomOut className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>Size -</span>
            </button>
            <button
              type="button"
              onClick={() =>
                updateAudioAttachmentsWithHistory((prev) =>
                  prev.map((a) =>
                    a.id === selectedCanvasAudio.id
                      ? { ...a, width: Math.min(420, (a.width ?? 270) + 20) }
                      : a
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Larger Audio Player"
            >
              <ZoomIn className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>Size +</span>
            </button>

            <button
              type="button"
              onClick={() =>
                updateAudioAttachmentsWithHistory((prev) =>
                  prev.map((a) =>
                    a.id === selectedCanvasAudio.id
                      ? { ...a, rotation: ((a.rotation || 0) - 15) % 360 }
                      : a
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
                updateAudioAttachmentsWithHistory((prev) =>
                  prev.map((a) =>
                    a.id === selectedCanvasAudio.id
                      ? { ...a, rotation: ((a.rotation || 0) + 15) % 360 }
                      : a
                  )
                )
              }
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-semibold hover:border-[#3366cc]"
              title="Rotate Right 15°"
            >
              <RotateCw className="h-3.5 w-3.5 text-[#3366cc]" />
              <span>+15°</span>
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
              className="flex h-7 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 font-wiki-mono font-bold text-[var(--wiki-text)] hover:border-[#3366cc]"
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
              className={`flex h-7 items-center gap-1 border px-2.5 font-semibold transition-colors ${
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

          {/* Free-Draggable, Pinch-Resizable & Pinch-Rotatable Opaque Red Audio Players on Canvas */}
          {audioAttachments.map((aud) => (
            <CanvasAudioPlayerCard
              key={aud.id}
              audio={aud}
              isReadingMode={isReadingMode}
              isSelected={!isReadingMode && selectedCanvasAudioId === aud.id}
              onSelect={() => {
                setSelectedCanvasAudioId(aud.id);
                setSelectedCanvasImgId(null);
              }}
              onStartDrag={handleStartDragAudio}
              onMoveDrag={handleMoveDragAudio}
              onEndDrag={handleEndDragAudio}
            />
          ))}

          <div className="relative z-10 mx-auto max-w-3xl pointer-events-none">
            {/* Rich Text Editable Canvas */}
            <div
              ref={editorRef}
              contentEditable={!isReadingMode}
              suppressContentEditableWarning
              onInput={scheduleTextHistorySnapshot}
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
                  const ah = 64;
                  return (
                    aud.layer === 'background' &&
                    clickX >= ax &&
                    clickX <= ax + aw &&
                    clickY >= ay &&
                    clickY <= ay + ah
                  );
                });
                if (hitBgAudio) {
                  setSelectedCanvasAudioId(hitBgAudio.id);
                  setSelectedCanvasImgId(null);
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
          onOpenMediaImageStudio={(rawDataUrl) => setRawMediaStudioImage(rawDataUrl)}
          onAddAudioAttachment={(aud) => {
            setAudioAttachments((prev) => {
              const next: CanvasAudioAttachment[] = [
                ...prev,
                {
                  ...aud,
                  x: 24 + (prev.length * 18) % 90,
                  y: 120 + (prev.length * 76) % 260,
                  width: 270,
                  rotation: 0,
                  playbackRate: 1,
                  layer: 'foreground',
                },
              ];
              pushCanvasSnapshot({ audioAttachments: next });
              return next;
            });
            setSelectedCanvasAudioId(aud.id);
            setSelectedCanvasImgId(null);
          }}
          customFonts={customFonts}
          onAddCustomFont={onAddCustomFont}
          micSettings={micSettings}
          diaryLockPin={diaryLockPin}
          onChangeDiaryLockPin={(pin) => {
            setDiaryLockPin(pin);
            pushCanvasSnapshot({ diaryLockPin: pin });
          }}
          onImportDocument={(importedTitle, importedHtml) => {
            const nextHeading = heading.trim() ? heading : importedTitle;
            if (!heading.trim() && importedTitle) {
              setHeading(importedTitle);
            }
            if (editorRef.current) {
              const current = editorRef.current.innerHTML.trim();
              const combined = current ? `${current}<hr>${importedHtml}` : importedHtml;
              editorRef.current.innerHTML = combined;
              pushCanvasSnapshot({
                heading: nextHeading,
                contentHtml: combined,
              });
            }
          }}
        />
      )}

      {/* Unlock Spoiler Text Passcode Modal */}
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
                  Unlock Spoiler
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

            <form onSubmit={handleVerifySpoilerUnlock} className="p-4 space-y-3">
              {spoilerUnlockError && (
                <p className="text-xs font-medium text-[#b32424]">
                  {spoilerUnlockError}
                </p>
              )}
              <div>
                <label className="mb-1 block text-xs font-medium text-[var(--wiki-text)]">
                  Enter Spoiler Passcode
                </label>
                <input
                  type="password"
                  value={spoilerUnlockInput}
                  onChange={(e) => {
                    setSpoilerUnlockInput(e.target.value);
                    setSpoilerUnlockError(null);
                  }}
                  placeholder="Passcode..."
                  className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                  autoFocus
                />
              </div>
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setActiveSpoilerSpan(null)}
                  className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-medium text-[var(--wiki-text)]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="h-9 bg-[#3366cc] px-4 text-xs font-semibold text-white"
                >
                  View Text
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 1:1 Mandatory Image Cropper Modal before setting Header PFP */}
      {rawSelectedImage && (
        <ImageCropperModal
          imageSrc={rawSelectedImage}
          onCancel={() => setRawSelectedImage(null)}
          onCropComplete={(croppedUrl) => {
            setPfpDataUrl(croppedUrl);
            setRawSelectedImage(null);
            pushCanvasSnapshot({ pfpDataUrl: croppedUrl });
          }}
        />
      )}

      {/* Media Picker Image Studio Modal (Crop, Transparency, Adjust & 22+ Filters) */}
      {rawMediaStudioImage && (
        <MediaImageStudioModal
          imageSrc={rawMediaStudioImage}
          onCancel={() => setRawMediaStudioImage(null)}
          onConfirm={(processedDataUrl, opacity, width, height) => {
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

import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Bell,
  BellRing,
  Check,
  ImagePlus,
  Save,
  Trash2,
  X,
  Volume2,
  Move,
  RotateCw,
  RotateCcw,
  ZoomIn,
  ZoomOut,
  Layers,
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

function stripHtmlToSingleLine(html: string): string {
  const temp = document.createElement('div');
  temp.innerHTML = html;
  const text = temp.textContent || temp.innerText || '';
  return text.replace(/\s+/g, ' ').trim();
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

  const [heading, setHeading] = useState<string>(initialLog?.heading || '');
  const [pfpDataUrl, setPfpDataUrl] = useState<string | null>(
    initialLog?.pfpDataUrl || null
  );
  const [reminderAt, setReminderAt] = useState<number | null>(
    initialLog?.reminderAt || null
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
  const [audioAttachments, setAudioAttachments] = useState<CanvasAudioAttachment[]>(
    initialLog?.audioAttachments || []
  );

  // Header 1:1 PFP Cropper state
  const [rawSelectedImage, setRawSelectedImage] = useState<string | null>(null);

  // Media Picker Image Studio state (22+ filters, crop, adjustments, transparency)
  const [rawMediaStudioImage, setRawMediaStudioImage] = useState<string | null>(null);

  // Free-dragging + 2-finger pinch resize & rotate state for canvas images
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

  const [showReminderModal, setShowReminderModal] = useState<boolean>(false);
  const [savedIndicator, setSavedIndicator] = useState<boolean>(false);

  const editorRef = useRef<HTMLDivElement | null>(null);
  const canvasContainerRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Populate initial HTML into contentEditable once on mount
  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.innerHTML = initialLog?.contentHtml || '';
    }
  }, [initialLog]);

  // Build a DiaryLog object from current workspace state (including all media attachments)
  const buildCurrentLogObject = (
    overrideReminderAt?: number | null
  ): {
    log: DiaryLog;
    hasAnyEntry: boolean;
  } => {
    const rawHtml = editorRef.current ? editorRef.current.innerHTML : '';
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
    const dateObj = initialLog ? new Date(initialLog.createdAt) : new Date(now);

    const dateStamp = dateObj.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });

    const timeStamp = new Date(now).toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });

    const log: DiaryLog = {
      id: stableLogIdRef.current,
      heading: trimmedHeading || (plainPreview.slice(0, 40) || 'Untitled Entry'),
      contentHtml: rawHtml,
      plainPreview:
        plainPreview ||
        (audioAttachments.length > 0
          ? `Audio attachment (${audioAttachments[0].name})`
          : canvasImages.length > 0
          ? `Image attachment (${canvasImages.length})`
          : 'Reminder scheduled.'),
      pfpDataUrl,
      createdAt: initialLog?.createdAt || now,
      updatedAt: now,
      dateStamp,
      timeStamp,
      reminderAt: effectiveReminder,
      reminderFired:
        effectiveReminder && effectiveReminder > Date.now()
          ? false
          : initialLog?.reminderFired,
      pinned: initialLog?.pinned || false,
      canvasBgDataUrl,
      canvasBgOpacity,
      canvasImages,
      audioAttachments,
    };

    return { log, hasAnyEntry };
  };

  // Handle Back / Exit: Auto-save if user entered anything; otherwise discard if empty
  const handleExitWorkspace = () => {
    const { log, hasAnyEntry } = buildCurrentLogObject();
    if (hasAnyEntry) {
      scheduleAndroidNativeReminder(
        log.id,
        `Likkho: ${log.heading}`,
        log.plainPreview,
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
    const { log, hasAnyEntry } = buildCurrentLogObject();
    if (!hasAnyEntry) {
      return;
    }
    scheduleAndroidNativeReminder(
      log.id,
      `Likkho: ${log.heading}`,
      log.plainPreview,
      log.reminderAt
    );
    onSaveLog(log, false);
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

  // Open links in browser when tapped
  const handleEditorClick = (e: React.MouseEvent<HTMLDivElement>) => {
    setSelectedCanvasImgId(null);
    const target = e.target as HTMLElement;
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

  // Pointer drag + 2-finger pinch resize & rotate handlers for Canvas Images
  const handleStartDragImage = (
    e: React.PointerEvent<HTMLDivElement>,
    img: CanvasDraggableImage
  ) => {
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);
    setSelectedCanvasImgId(img.id);

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
    imgPointersRef.current.delete(e.pointerId);
    if (imgPointersRef.current.size < 2) {
      pinchInitialRef.current = null;
    }
    if (imgPointersRef.current.size === 0) {
      setActiveDragId(null);
    }
  };

  const selectedCanvasImage = canvasImages.find((c) => c.id === selectedCanvasImgId) || null;

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

      {/* Workspace Header */}
      <header className="flex min-h-[58px] shrink-0 items-center justify-between gap-2 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 py-2">
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
            onClick={() => {
              if (window.LikkhoNative?.requestFilesAndMediaPermission) {
                window.LikkhoNative.requestFilesAndMediaPermission();
              }
              fileInputRef.current?.click();
            }}
            className="group relative flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden border border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]"
            title="Select & crop 1:1 image from storage"
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

          <div className="flex-1 min-w-0">
            <input
              type="text"
              value={heading}
              onChange={(e) => setHeading(e.target.value)}
              placeholder="Diary Heading..."
              className="w-full border-b border-transparent bg-transparent font-wiki-serif text-lg font-bold text-[var(--wiki-text)] placeholder:text-[var(--wiki-muted)]/60 focus:border-[#3366cc] outline-none truncate"
            />
          </div>
        </div>

        {/* Reminder Button & Save Button */}
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
        </div>
      </header>

      {/* Active Reminder Sub-bar if scheduled */}
      {reminderAt && reminderAt > Date.now() && (
        <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[#3366cc]/10 px-4 py-1.5 text-[11px]">
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
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#3366cc] bg-[var(--wiki-surface)] px-3 py-1.5 text-xs z-30">
          <div className="flex items-center gap-1.5 overflow-x-auto">
            {/* Resize Buttons */}
            <button
              type="button"
              onClick={() =>
                setCanvasImages((prev) =>
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
                setCanvasImages((prev) =>
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

            {/* Rotate Buttons */}
            <button
              type="button"
              onClick={() =>
                setCanvasImages((prev) =>
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
                setCanvasImages((prev) =>
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

            {/* Background vs Foreground Toggle (Text covers image vs Image covers text) */}
            <button
              type="button"
              onClick={() =>
                setCanvasImages((prev) =>
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
                setCanvasImages((prev) =>
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

      {/* Wikipedia Article Canvas with Custom Background Image, Free-Draggable/Pinchable Images & Audio Attachments */}
      <div
        ref={canvasContainerRef}
        className="relative flex-1 overflow-y-auto px-4 py-5 sm:px-8 cursor-text"
        onClick={(e) => {
          setSelectedCanvasImgId(null);
          if (e.target === e.currentTarget && editorRef.current) {
            editorRef.current.focus();
          }
        }}
      >
        {canvasBgDataUrl && (
          <div
            className="pointer-events-none fixed inset-0 z-0 bg-cover bg-center bg-no-repeat"
            style={{
              backgroundImage: `url(${canvasBgDataUrl})`,
              opacity: canvasBgOpacity,
            }}
          />
        )}

        {/* Free-Draggable, Pinch-Resizable & Pinch-Rotatable Images Layer on Canvas */}
        {canvasImages.map((img) => {
          const isSelected = selectedCanvasImgId === img.id;
          const isBehindText = img.layer === 'background';
          // Text layer is z-10.
          // If Behind Text (background): zIndex = 5 (when unselected) so text (z-10) covers image; when selected, zIndex = 25 so user can still drag/pinch it easily.
          // If In Front of Text (foreground): zIndex = 20 so image covers text.
          const computedZIndex = isSelected ? 25 : isBehindText ? 5 : 20;

          return (
            <div
              key={img.id}
              onPointerDown={(e) => handleStartDragImage(e, img)}
              onPointerMove={handleMoveDragImage}
              onPointerUp={handleEndDragImage}
              onPointerCancel={handleEndDragImage}
              onClick={(e) => {
                e.stopPropagation();
                setSelectedCanvasImgId(img.id);
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
              className={`group touch-none select-none cursor-move ${
                isSelected ? 'ring-2 ring-[#3366cc]' : 'hover:ring-1 hover:ring-[var(--wiki-border)]'
              }`}
            >
              <img
                src={img.dataUrl}
                alt="Canvas media"
                referrerPolicy="no-referrer"
                draggable={false}
                className="block h-auto w-full pointer-events-none"
              />
              {isSelected && (
                <div className="absolute -top-8 right-0 flex items-center gap-1 bg-[#101418]/90 px-1.5 py-0.5 text-white shadow-md whitespace-nowrap">
                  <Move className="h-3 w-3 text-[#6699ff]" />
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setCanvasImages((prev) =>
                        prev.map((c) =>
                          c.id === img.id
                            ? { ...c, width: Math.max(50, c.width - 20) }
                            : c
                        )
                      );
                    }}
                    className="px-1 text-[11px] font-bold hover:text-[#6699ff]"
                    title="Shrink image"
                  >
                    -
                  </button>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setCanvasImages((prev) =>
                        prev.map((c) =>
                          c.id === img.id
                            ? { ...c, width: Math.min(420, c.width + 20) }
                            : c
                        )
                      );
                    }}
                    className="px-1 text-[11px] font-bold hover:text-[#6699ff]"
                    title="Enlarge image"
                  >
                    +
                  </button>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setCanvasImages((prev) =>
                        prev.map((c) =>
                          c.id === img.id
                            ? { ...c, rotation: ((c.rotation || 0) - 15) % 360 }
                            : c
                        )
                      );
                    }}
                    className="px-1 text-[11px] hover:text-[#6699ff]"
                    title="Rotate Left 15°"
                  >
                    <RotateCcw className="h-3 w-3" />
                  </button>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setCanvasImages((prev) =>
                        prev.map((c) =>
                          c.id === img.id
                            ? { ...c, rotation: ((c.rotation || 0) + 15) % 360 }
                            : c
                        )
                      );
                    }}
                    className="px-1 text-[11px] hover:text-[#6699ff]"
                    title="Rotate Right 15°"
                  >
                    <RotateCw className="h-3 w-3" />
                  </button>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setCanvasImages((prev) =>
                        prev.map((c) =>
                          c.id === img.id
                            ? {
                                ...c,
                                layer:
                                  c.layer === 'background'
                                    ? 'foreground'
                                    : 'background',
                              }
                            : c
                        )
                      );
                    }}
                    className="px-1 text-[10px] font-bold text-[#6699ff] hover:text-white"
                    title="Toggle BG / FG"
                  >
                    {isBehindText ? 'BG' : 'FG'}
                  </button>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setCanvasImages((prev) => prev.filter((c) => c.id !== img.id));
                      setSelectedCanvasImgId(null);
                    }}
                    className="ml-0.5 text-[#ff6b6b] hover:text-white"
                    title="Remove image"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
              )}
            </div>
          );
        })}

        <div className="relative z-10 mx-auto max-w-3xl pointer-events-none">
          {/* Rich Text Editable Canvas */}
          <div
            ref={editorRef}
            contentEditable
            suppressContentEditableWarning
            onClick={(e) => {
              // If user taps at a coordinate covered by a Background (Behind Text) image while not typing, allow selecting that background image via double tap or tap when empty
              handleEditorClick(e);
            }}
            onDoubleClick={(e) => {
              // Double-tap anywhere over a background image to select and move/resize it
              const rect = canvasContainerRef.current?.getBoundingClientRect();
              if (!rect) return;
              const clickX = e.clientX - rect.left + (canvasContainerRef.current?.scrollLeft || 0);
              const clickY = e.clientY - rect.top + (canvasContainerRef.current?.scrollTop || 0);
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
              }
            }}
            data-placeholder="Start writing..."
            className="wiki-editor-content min-h-[55vh] pb-8 pointer-events-auto"
          />

          {/* Attached Audio & Voice Recordings Section inside Canvas */}
          {audioAttachments.length > 0 && (
            <div className="mt-4 space-y-2 border-t border-[var(--wiki-hairline)] pt-4 pb-10 pointer-events-auto">
              {audioAttachments.map((aud) => (
                <div
                  key={aud.id}
                  className="flex flex-col gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-2.5"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5 min-w-0">
                      <Volume2 className="h-4 w-4 shrink-0 text-[#3366cc]" />
                      <span className="truncate text-xs font-semibold text-[var(--wiki-text)]">
                        {aud.name}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        setAudioAttachments((prev) =>
                          prev.filter((item) => item.id !== aud.id)
                        )
                      }
                      className="text-[var(--wiki-muted)] hover:text-[#b32424]"
                      title="Delete audio"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                  <audio
                    controls
                    src={aud.dataUrl}
                    className="h-9 w-full"
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Rich Text Editing Toolbar Docked Directly Above Soft Keyboard */}
      <RichTextToolbar
        editorRef={editorRef}
        onContentChange={() => {}}
        canvasBgDataUrl={canvasBgDataUrl}
        canvasBgOpacity={canvasBgOpacity}
        onChangeCanvasBg={(dataUrl, opacity) => {
          setCanvasBgDataUrl(dataUrl);
          setCanvasBgOpacity(opacity);
        }}
        onOpenMediaImageStudio={(rawDataUrl) => setRawMediaStudioImage(rawDataUrl)}
        onAddAudioAttachment={(aud) =>
          setAudioAttachments((prev) => [...prev, aud])
        }
        customFonts={customFonts}
        onAddCustomFont={onAddCustomFont}
        micSettings={micSettings}
      />

      {/* 1:1 Mandatory Image Cropper Modal before setting Header PFP */}
      {rawSelectedImage && (
        <ImageCropperModal
          imageSrc={rawSelectedImage}
          onCancel={() => setRawSelectedImage(null)}
          onCropComplete={(croppedUrl) => {
            setPfpDataUrl(croppedUrl);
            setRawSelectedImage(null);
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
            setCanvasImages((prev) => [
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
            ]);
            setSelectedCanvasImgId(newId);
            setRawMediaStudioImage(null);
          }}
        />
      )}

      {/* Reminder Date & Time Modal — Immediately persists log & schedules Android OS Alarm */}
      {showReminderModal && (
        <ReminderModal
          currentReminder={reminderAt}
          heading={heading}
          onClose={() => setShowReminderModal(false)}
          onSaveReminder={(ts) => {
            setReminderAt(ts);
            const { log } = buildCurrentLogObject(ts);
            scheduleAndroidNativeReminder(
              log.id,
              `Likkho: ${log.heading}`,
              log.plainPreview,
              ts
            );
            onSaveLog(log, false);
          }}
        />
      )}
    </div>
  );
};

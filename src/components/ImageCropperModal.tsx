import React, { useEffect, useRef, useState } from 'react';
import { Check, ImagePlus, RotateCcw, Smile, Trash2, Upload, X, ZoomIn, ZoomOut } from 'lucide-react';

interface ImageCropperModalProps {
  imageSrc?: string | null;
  currentPfpDataUrl?: string | null;
  onCancel: () => void;
  onCropComplete: (croppedDataUrl: string | null) => void;
}

const PFP_BG_COLORS: { label: string; value: string; textColor: string }[] = [
  { label: 'Wikipedia Blue', value: '#3366cc', textColor: '#ffffff' },
  { label: 'Obsidian Slate', value: '#202122', textColor: '#ffffff' },
  { label: 'Crimson Ink', value: '#b32424', textColor: '#ffffff' },
  { label: 'Emerald Pine', value: '#14866d', textColor: '#ffffff' },
  { label: 'Royal Violet', value: '#6b4ba1', textColor: '#ffffff' },
  { label: 'Warm Amber', value: '#ac6600', textColor: '#ffffff' },
  { label: 'Deep Indigo', value: '#3730a3', textColor: '#ffffff' },
  { label: 'Rose Berry', value: '#be185d', textColor: '#ffffff' },
  { label: 'Teal Ocean', value: '#0f766e', textColor: '#ffffff' },
  { label: 'Forest Moss', value: '#166534', textColor: '#ffffff' },
  { label: 'Terracotta', value: '#c2410c', textColor: '#ffffff' },
  { label: 'Parchment Cream', value: '#fef6e7', textColor: '#202122' },
  { label: 'Soft Lavender', value: '#ede9fe', textColor: '#3730a3' },
  { label: 'Mint Mist', value: '#d1fae5', textColor: '#065f46' },
  { label: 'Sky Pastel', value: '#dbeafe', textColor: '#1e40af' },
  { label: 'Blush Petal', value: '#fce7f3', textColor: '#9d174d' },
];

const QUICK_EMOJIS = [
  '📔', '✨', '🔒', '🌙', '☕', '🌿', '🪶', '🔥',
  '💡', '🎯', '🎧', '🎨', '🧠', '🚀', '🌸', '🌊',
  '🏔️', '🕯️', '📜', '💎', '🦋', '⚡', '🌈', '❤️',
];

export const ImageCropperModal: React.FC<ImageCropperModalProps> = ({
  imageSrc: initialImageSrc,
  currentPfpDataUrl,
  onCancel,
  onCropComplete,
}) => {
  const [activeTab, setActiveTab] = useState<'image' | 'emojiText'>(() =>
    initialImageSrc ? 'image' : 'emojiText'
  );

  // Image Cropper State
  const [localImageSrc, setLocalImageSrc] = useState<string | null>(
    initialImageSrc || null
  );
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const internalFileInputRef = useRef<HTMLInputElement | null>(null);
  const [imgElement, setImgElement] = useState<HTMLImageElement | null>(null);
  const [zoom, setZoom] = useState<number>(1);
  const [offset, setOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 });

  // Emoji / Text & Background State
  const [emojiOrText, setEmojiOrText] = useState<string>('📔');
  const [bgColor, setBgColor] = useState<string>('#3366cc');
  const [textColor, setTextColor] = useState<string>('#ffffff');
  const [fontScale, setFontScale] = useState<number>(1);
  const emojiPreviewCanvasRef = useRef<HTMLCanvasElement | null>(null);

  // Multi-touch pinch-to-zoom tracking
  const activePointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());
  const pinchStartDistRef = useRef<number | null>(null);
  const pinchStartZoomRef = useRef<number>(1);

  const CROP_SIZE = 240;

  useEffect(() => {
    if (initialImageSrc) {
      setLocalImageSrc(initialImageSrc);
      setActiveTab('image');
    }
  }, [initialImageSrc]);

  useEffect(() => {
    if (!localImageSrc) {
      setImgElement(null);
      return;
    }
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      setImgElement(img);
      setZoom(1);
      setOffset({ x: 0, y: 0 });
    };
    img.src = localImageSrc;
  }, [localImageSrc]);

  // Render Image Cropper Canvas
  useEffect(() => {
    if (activeTab !== 'image' || !imgElement || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, CROP_SIZE, CROP_SIZE);

    ctx.fillStyle = '#101418';
    ctx.fillRect(0, 0, CROP_SIZE, CROP_SIZE);

    const minDim = Math.min(imgElement.width, imgElement.height);
    const baseScale = CROP_SIZE / minDim;
    const finalScale = baseScale * zoom;

    const drawWidth = imgElement.width * finalScale;
    const drawHeight = imgElement.height * finalScale;

    const drawX = (CROP_SIZE - drawWidth) / 2 + offset.x;
    const drawY = (CROP_SIZE - drawHeight) / 2 + offset.y;

    ctx.drawImage(imgElement, drawX, drawY, drawWidth, drawHeight);

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.32)';
    ctx.lineWidth = 1;
    const third = CROP_SIZE / 3;
    ctx.beginPath();
    ctx.moveTo(third, 0);
    ctx.lineTo(third, CROP_SIZE);
    ctx.moveTo(third * 2, 0);
    ctx.lineTo(third * 2, CROP_SIZE);
    ctx.moveTo(0, third);
    ctx.lineTo(CROP_SIZE, third);
    ctx.moveTo(0, third * 2);
    ctx.lineTo(CROP_SIZE, third * 2);
    ctx.stroke();
  }, [activeTab, imgElement, zoom, offset]);

  // Helper to draw Emoji / Text onto any square canvas
  const renderEmojiTextToSquareCanvas = (
    canvas: HTMLCanvasElement,
    size: number,
    rawText: string,
    bgHex: string,
    fgHex: string,
    scale: number
  ) => {
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, size, size);

    // Fill Background
    ctx.fillStyle = bgHex;
    ctx.fillRect(0, 0, size, size);

    const displayStr = (rawText || '').trim().slice(0, 8) || '📔';
    const graphemeCount = Array.from(displayStr).length;

    let baseFontPx = size * 0.54;
    if (graphemeCount === 2) baseFontPx = size * 0.44;
    else if (graphemeCount === 3) baseFontPx = size * 0.34;
    else if (graphemeCount >= 4) baseFontPx = size * 0.26;

    const finalFontPx = Math.round(baseFontPx * scale);

    ctx.fillStyle = fgHex;
    ctx.font = `700 ${finalFontPx}px "Plus Jakarta Sans", "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(displayStr, size / 2, size / 2 + size * 0.02);
  };

  // Render Live Preview for Emoji/Text Tab
  useEffect(() => {
    if (activeTab !== 'emojiText' || !emojiPreviewCanvasRef.current) return;
    renderEmojiTextToSquareCanvas(
      emojiPreviewCanvasRef.current,
      160,
      emojiOrText,
      bgColor,
      textColor,
      fontScale
    );
  }, [activeTab, emojiOrText, bgColor, textColor, fontScale]);

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    activePointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (activePointersRef.current.size === 2) {
      const pts = Array.from(activePointersRef.current.values());
      const dist = Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y);
      pinchStartDistRef.current = Math.max(10, dist);
      pinchStartZoomRef.current = zoom;
      setIsDragging(false);
    } else if (activePointersRef.current.size === 1) {
      setIsDragging(true);
      setDragStart({ x: e.clientX - offset.x, y: e.clientY - offset.y });
    }
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (activePointersRef.current.has(e.pointerId)) {
      activePointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    }

    if (activePointersRef.current.size === 2 && pinchStartDistRef.current) {
      const pts = Array.from(activePointersRef.current.values());
      const dist = Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y);
      const scaleFactor = dist / pinchStartDistRef.current;
      const nextZoom = Math.min(
        4.0,
        Math.max(1.0, +(pinchStartZoomRef.current * scaleFactor).toFixed(2))
      );
      setZoom(nextZoom);
      return;
    }

    if (!isDragging || activePointersRef.current.size !== 1) return;
    setOffset({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y,
    });
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    activePointersRef.current.delete(e.pointerId);
    if (activePointersRef.current.size < 2) {
      pinchStartDistRef.current = null;
    }
    if (activePointersRef.current.size === 1) {
      const remaining = Array.from(activePointersRef.current.values())[0];
      setIsDragging(true);
      setDragStart({ x: remaining.x - offset.x, y: remaining.y - offset.y });
    } else if (activePointersRef.current.size === 0) {
      setIsDragging(false);
    }
  };

  const handleInternalFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setLocalImageSrc(reader.result);
        setActiveTab('image');
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleConfirmPfp = () => {
    const OUTPUT_RES = 400;
    const outCanvas = document.createElement('canvas');
    outCanvas.width = OUTPUT_RES;
    outCanvas.height = OUTPUT_RES;

    if (activeTab === 'emojiText') {
      renderEmojiTextToSquareCanvas(
        outCanvas,
        OUTPUT_RES,
        emojiOrText,
        bgColor,
        textColor,
        fontScale
      );
      const dataUrl = outCanvas.toDataURL('image/png');
      onCropComplete(dataUrl);
      return;
    }

    if (!imgElement) return;
    const outCtx = outCanvas.getContext('2d');
    if (!outCtx) return;

    const ratio = OUTPUT_RES / CROP_SIZE;
    const minDim = Math.min(imgElement.width, imgElement.height);
    const baseScale = OUTPUT_RES / minDim;
    const finalScale = baseScale * zoom;

    const drawWidth = imgElement.width * finalScale;
    const drawHeight = imgElement.height * finalScale;
    const drawX = (OUTPUT_RES - drawWidth) / 2 + offset.x * ratio;
    const drawY = (OUTPUT_RES - drawHeight) / 2 + offset.y * ratio;

    outCtx.fillStyle = '#ffffff';
    outCtx.fillRect(0, 0, OUTPUT_RES, OUTPUT_RES);
    outCtx.drawImage(imgElement, drawX, drawY, drawWidth, drawHeight);

    const dataUrl = outCanvas.toDataURL('image/jpeg', 0.88);
    onCropComplete(dataUrl);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4 backdrop-blur-xs">
      <input
        ref={internalFileInputRef}
        type="file"
        accept="image/*"
        onChange={handleInternalFileChange}
        className="hidden"
      />

      <div className="flex max-h-[92vh] w-full max-w-sm flex-col overflow-hidden border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl">
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
          <div>
            <h3 className="font-wiki-serif text-lg font-semibold leading-tight">
              Diary Profile Picture (1:1)
            </h3>
            <p className="text-xs text-[var(--wiki-muted)]">
              Choose an Image or Emoji / Text with custom background
            </p>
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
            aria-label="Cancel PFP studio"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Mode Switcher Tabs: Emoji / Text vs Image */}
        <div className="grid shrink-0 grid-cols-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] p-1.5 gap-1.5">
          <button
            type="button"
            onClick={() => setActiveTab('emojiText')}
            className={`flex h-9 items-center justify-center gap-1.5 text-xs font-semibold transition-colors ${
              activeTab === 'emojiText'
                ? 'bg-[#3366cc] text-white'
                : 'border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
            }`}
          >
            <Smile className="h-4 w-4" />
            <span>Emoji / Text & BG</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('image')}
            className={`flex h-9 items-center justify-center gap-1.5 text-xs font-semibold transition-colors ${
              activeTab === 'image'
                ? 'bg-[#3366cc] text-white'
                : 'border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
            }`}
          >
            <ImagePlus className="h-4 w-4" />
            <span>Photo / Image</span>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-4">
          {activeTab === 'emojiText' ? (
            <div className="space-y-4">
              {/* Live 1:1 Preview */}
              <div className="flex flex-col items-center">
                <div className="overflow-hidden border-2 border-[var(--wiki-border)] shadow-sm">
                  <canvas
                    ref={emojiPreviewCanvasRef}
                    width={160}
                    height={160}
                    className="block h-[132px] w-[132px]"
                  />
                </div>
              </div>

              {/* Emoji or Short Text Input */}
              <div>
                <div className="mb-1 flex items-center justify-between">
                  <label className="text-xs font-semibold text-[var(--wiki-text)]">
                    Emoji or Text (Initials / Word)
                  </label>
                  <span className="text-[11px] text-[var(--wiki-muted)]">
                    Up to 6 chars
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={emojiOrText}
                    maxLength={8}
                    onChange={(e) => setEmojiOrText(e.target.value)}
                    placeholder="Type emoji or text (e.g. 📔, LK, अ)..."
                    className="h-9 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-sm font-medium text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                  />
                  <input
                    type="color"
                    value={textColor}
                    onChange={(e) => setTextColor(e.target.value)}
                    title="Text Color"
                    className="h-9 w-10 shrink-0 cursor-pointer border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-0.5"
                  />
                </div>
              </div>

              {/* Quick Emoji Grid */}
              <div>
                <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-[var(--wiki-muted)]">
                  Quick Emojis
                </label>
                <div className="grid grid-cols-8 gap-1">
                  {QUICK_EMOJIS.map((em) => (
                    <button
                      key={em}
                      type="button"
                      onClick={() => setEmojiOrText(em)}
                      className={`flex h-8 items-center justify-center border text-base transition-colors ${
                        emojiOrText === em
                          ? 'border-[#3366cc] bg-[#3366cc]/15'
                          : 'border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] hover:border-[#3366cc]'
                      }`}
                    >
                      {em}
                    </button>
                  ))}
                </div>
              </div>

              {/* Size Scale Slider */}
              <div>
                <div className="mb-1 flex items-center justify-between text-xs">
                  <span className="font-semibold text-[var(--wiki-text)]">
                    Emoji / Text Size
                  </span>
                  <span className="font-wiki-mono text-[11px] text-[var(--wiki-muted)]">
                    {Math.round(fontScale * 100)}%
                  </span>
                </div>
                <input
                  type="range"
                  min={0.6}
                  max={1.45}
                  step={0.05}
                  value={fontScale}
                  onChange={(e) => setFontScale(parseFloat(e.target.value))}
                  className="h-1.5 w-full cursor-pointer accent-[#3366cc]"
                />
              </div>

              {/* Background Color Presets + Custom Color Picker */}
              <div>
                <div className="mb-1.5 flex items-center justify-between">
                  <label className="text-xs font-semibold text-[var(--wiki-text)]">
                    Background Color
                  </label>
                  <label className="flex cursor-pointer items-center gap-1.5 text-xs font-semibold text-[#3366cc]">
                    <span>Custom BG</span>
                    <input
                      type="color"
                      value={bgColor}
                      onChange={(e) => setBgColor(e.target.value)}
                      className="h-6 w-7 cursor-pointer border border-[var(--wiki-border)] bg-transparent p-0"
                    />
                  </label>
                </div>
                <div className="grid grid-cols-8 gap-1.5">
                  {PFP_BG_COLORS.map((preset) => (
                    <button
                      key={preset.value}
                      type="button"
                      onClick={() => {
                        setBgColor(preset.value);
                        setTextColor(preset.textColor);
                      }}
                      title={preset.label}
                      style={{ backgroundColor: preset.value }}
                      className={`h-7 w-full border transition-transform ${
                        bgColor.toLowerCase() === preset.value.toLowerCase()
                          ? 'scale-105 border-2 border-[#3366cc] ring-1 ring-white'
                          : 'border-[var(--wiki-border)]'
                      }`}
                    />
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center">
              {imgElement ? (
                <>
                  <div className="relative overflow-hidden border-2 border-[var(--wiki-border)] bg-black shadow-inner">
                    <canvas
                      ref={canvasRef}
                      width={CROP_SIZE}
                      height={CROP_SIZE}
                      onPointerDown={handlePointerDown}
                      onPointerMove={handlePointerMove}
                      onPointerUp={handlePointerUp}
                      onPointerCancel={handlePointerUp}
                      className="block cursor-move touch-none"
                    />
                  </div>

                  {/* Zoom Slider Controls */}
                  <div className="mt-3 flex w-full items-center gap-2.5 px-1">
                    <button
                      type="button"
                      onClick={() => setZoom((z) => Math.max(1, +(z - 0.15).toFixed(2)))}
                      className="flex h-8 w-8 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]"
                      aria-label="Zoom out"
                    >
                      <ZoomOut className="h-4 w-4" />
                    </button>
                    <input
                      type="range"
                      min={1}
                      max={4}
                      step={0.05}
                      value={zoom}
                      onChange={(e) => setZoom(parseFloat(e.target.value))}
                      className="h-1.5 flex-1 cursor-pointer accent-[#3366cc]"
                    />
                    <button
                      type="button"
                      onClick={() => setZoom((z) => Math.min(4, +(z + 0.15).toFixed(2)))}
                      className="flex h-8 w-8 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]"
                      aria-label="Zoom in"
                    >
                      <ZoomIn className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setZoom(1);
                        setOffset({ x: 0, y: 0 });
                      }}
                      className="flex h-8 w-8 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]"
                      title="Reset position"
                    >
                      <RotateCcw className="h-4 w-4" />
                    </button>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      if (window.LikkhoNative?.requestFilesAndMediaPermission) {
                        window.LikkhoNative.requestFilesAndMediaPermission();
                      }
                      internalFileInputRef.current?.click();
                    }}
                    className="mt-3 flex h-9 w-full items-center justify-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-xs font-semibold text-[var(--wiki-text)] hover:border-[#3366cc]"
                  >
                    <Upload className="h-3.5 w-3.5 text-[#3366cc]" />
                    <span>Choose Another Photo</span>
                  </button>
                </>
              ) : (
                <div className="flex w-full flex-col items-center justify-center border border-dashed border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-10 text-center">
                  <ImagePlus className="mb-2 h-8 w-8 text-[#3366cc]" />
                  <p className="text-xs font-semibold text-[var(--wiki-text)]">
                    Select a Photo from Device Storage
                  </p>
                  <p className="mt-1 text-[11px] text-[var(--wiki-muted)]">
                    Crop any photo to a 1:1 square diary profile picture
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      if (window.LikkhoNative?.requestFilesAndMediaPermission) {
                        window.LikkhoNative.requestFilesAndMediaPermission();
                      }
                      internalFileInputRef.current?.click();
                    }}
                    className="mt-4 flex h-9 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0]"
                  >
                    <Upload className="h-3.5 w-3.5" />
                    <span>Browse Image</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="flex shrink-0 items-center justify-between gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
          <div>
            {currentPfpDataUrl && (
              <button
                type="button"
                onClick={() => onCropComplete(null)}
                className="flex h-9 items-center gap-1 border border-[#b32424]/40 bg-[#b32424]/10 px-2.5 text-xs font-semibold text-[#b32424]"
                title="Remove Profile Picture"
              >
                <Trash2 className="h-3.5 w-3.5" />
                <span>Remove</span>
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onCancel}
              className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3.5 text-xs font-medium text-[var(--wiki-text)]"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={activeTab === 'image' && !imgElement}
              onClick={handleConfirmPfp}
              className="flex h-9 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] disabled:opacity-50"
            >
              <Check className="h-4 w-4" />
              <span>Save PFP</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useEffect, useRef, useState } from 'react';
import { Check, RotateCcw, ZoomIn, ZoomOut, X } from 'lucide-react';

interface ImageCropperModalProps {
  imageSrc: string;
  onCancel: () => void;
  onCropComplete: (croppedDataUrl: string) => void;
}

export const ImageCropperModal: React.FC<ImageCropperModalProps> = ({
  imageSrc,
  onCancel,
  onCropComplete,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [imgElement, setImgElement] = useState<HTMLImageElement | null>(null);
  const [zoom, setZoom] = useState<number>(1);
  const [offset, setOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 });

  // Multi-touch pinch-to-zoom tracking
  const activePointersRef = useRef<Map<number, { x: number; y: number }>>(new Map());
  const pinchStartDistRef = useRef<number | null>(null);
  const pinchStartZoomRef = useRef<number>(1);

  const CROP_SIZE = 280;

  useEffect(() => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      setImgElement(img);
      setZoom(1);
      setOffset({ x: 0, y: 0 });
    };
    img.src = imageSrc;
  }, [imageSrc]);

  useEffect(() => {
    if (!imgElement || !canvasRef.current) return;
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
  }, [imgElement, zoom, offset]);

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
      const nextZoom = Math.min(4.0, Math.max(1.0, +(pinchStartZoomRef.current * scaleFactor).toFixed(2)));
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

  const handleConfirmCrop = () => {
    if (!imgElement) return;
    const outCanvas = document.createElement('canvas');
    const OUTPUT_RES = 400;
    outCanvas.width = OUTPUT_RES;
    outCanvas.height = OUTPUT_RES;
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
      <div className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
          <div>
            <h3 className="font-wiki-serif text-lg font-semibold leading-tight">
              Crop Image (1:1 Square)
            </h3>
            <p className="text-xs text-[var(--wiki-muted)]">
              Pinch with two fingers to zoom or drag to reposition
            </p>
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="flex h-9 w-9 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
            aria-label="Cancel crop"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* 1:1 Interactive Canvas Viewport */}
        <div className="flex flex-col items-center p-4">
          <div className="relative overflow-hidden border-2 border-[var(--wiki-border)] bg-black shadow-inner">
            <canvas
              ref={canvasRef}
              width={CROP_SIZE}
              height={CROP_SIZE}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={handlePointerUp}
              onPointerCancel={handlePointerUp}
              className="cursor-move touch-none block"
            />
          </div>

          {/* Zoom Slider Controls */}
          <div className="mt-4 flex w-full items-center gap-3 px-2">
            <button
              type="button"
              onClick={() => setZoom((z) => Math.max(1, +(z - 0.15).toFixed(2)))}
              className="flex h-9 w-9 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]"
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
              className="flex h-9 w-9 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]"
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
              className="flex h-9 w-9 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]"
              title="Reset position"
            >
              <RotateCcw className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-end gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
          <button
            type="button"
            onClick={onCancel}
            className="h-10 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-4 text-xs font-medium text-[var(--wiki-text)]"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirmCrop}
            className="flex h-10 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0]"
          >
            <Check className="h-4 w-4" />
            Set 1:1 Image
          </button>
        </div>
      </div>
    </div>
  );
};

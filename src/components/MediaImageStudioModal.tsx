import React, { useEffect, useRef, useState } from 'react';
import {
  Check,
  X,
  RotateCcw,
  Crop,
  Sliders,
  Sparkles,
  Eye,
} from 'lucide-react';

export type ImageFilterPreset =
  | 'none'
  | 'grayscale'
  | 'sepia'
  | 'invert'
  | 'blur'
  | 'sharpen'
  | 'edge'
  | 'threshold'
  | 'highpass'
  | 'lowpass'
  | 'vignette'
  | 'warmth'
  | 'huerotate'
  | 'vintage'
  | 'noir'
  | 'hdr'
  | 'colorbalance'
  | 'posterize'
  | 'emboss'
  | 'pixelate'
  | 'fisheye'
  | 'denoise';

export const IMAGE_FILTER_LIST: { id: ImageFilterPreset; label: string }[] = [
  { id: 'none', label: 'Original' },
  { id: 'grayscale', label: 'Grayscale (Monochrome)' },
  { id: 'sepia', label: 'Sepia' },
  { id: 'invert', label: 'Invert (Negative)' },
  { id: 'blur', label: 'Blur (Gaussian/Motion)' },
  { id: 'sharpen', label: 'Sharpen' },
  { id: 'edge', label: 'Edge Detection (Sobel)' },
  { id: 'threshold', label: 'Thresholding (Binarization)' },
  { id: 'highpass', label: 'High-Pass Filter' },
  { id: 'lowpass', label: 'Low-Pass Filter' },
  { id: 'vignette', label: 'Vignette' },
  { id: 'warmth', label: 'Warmth / Coolness' },
  { id: 'huerotate', label: 'Hue Rotate' },
  { id: 'vintage', label: 'Vintage / Retro' },
  { id: 'noir', label: 'Noir' },
  { id: 'hdr', label: 'HDR (High Dynamic Range)' },
  { id: 'colorbalance', label: 'Color Balance' },
  { id: 'posterize', label: 'Posterize' },
  { id: 'emboss', label: 'Emboss' },
  { id: 'pixelate', label: 'Pixelate' },
  { id: 'fisheye', label: 'Distortion (Fish-eye/Warp)' },
  { id: 'denoise', label: 'Noise Reduction (Denoise)' },
];

interface MediaImageStudioModalProps {
  imageSrc: string;
  onCancel: () => void;
  onConfirm: (processedDataUrl: string, opacity: number, width: number, height: number) => void;
}

function clampByte(val: number): number {
  return val < 0 ? 0 : val > 255 ? 255 : Math.round(val);
}

function applyConvolution3x3(
  src: Uint8ClampedArray,
  w: number,
  h: number,
  kernel: number[],
  divisor: number = 1,
  bias: number = 0
): Uint8ClampedArray {
  const out = new Uint8ClampedArray(src.length);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let r = 0,
        g = 0,
        b = 0;
      for (let ky = -1; ky <= 1; ky++) {
        for (let kx = -1; kx <= 1; kx++) {
          const py = Math.min(h - 1, Math.max(0, y + ky));
          const px = Math.min(w - 1, Math.max(0, x + kx));
          const idx = (py * w + px) * 4;
          const weight = kernel[(ky + 1) * 3 + (kx + 1)];
          r += src[idx] * weight;
          g += src[idx + 1] * weight;
          b += src[idx + 2] * weight;
        }
      }
      const dstIdx = (y * w + x) * 4;
      out[dstIdx] = clampByte(r / divisor + bias);
      out[dstIdx + 1] = clampByte(g / divisor + bias);
      out[dstIdx + 2] = clampByte(b / divisor + bias);
      out[dstIdx + 3] = src[dstIdx + 3];
    }
  }
  return out;
}

export const MediaImageStudioModal: React.FC<MediaImageStudioModalProps> = ({
  imageSrc,
  onCancel,
  onConfirm,
}) => {
  const previewCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const [imgEl, setImgEl] = useState<HTMLImageElement | null>(null);
  const [activeTab, setActiveTab] = useState<'filters' | 'adjust' | 'crop'>('filters');

  // Filter preset
  const [selectedFilter, setSelectedFilter] = useState<ImageFilterPreset>('none');

  // Fine adjustments (Brightness, Contrast, Saturation, Temperature, Hue, Opacity, Red/Blue Balance)
  const [brightness, setBrightness] = useState<number>(0); // -100 to 100
  const [contrast, setContrast] = useState<number>(0); // -100 to 100
  const [saturation, setSaturation] = useState<number>(0); // -100 to 100
  const [temperature, setTemperature] = useState<number>(0); // -100 to 100
  const [hueRotate, setHueRotate] = useState<number>(0); // 0 to 360
  const [colorBalanceRG, setColorBalanceRG] = useState<number>(0); // -100 to 100
  const [opacity, setOpacity] = useState<number>(1); // 0.1 to 1.0

  // Crop & Zoom controls
  const [cropAspect, setCropAspect] = useState<'free' | '1:1' | '4:3' | '16:9'>('free');
  const [zoom, setZoom] = useState<number>(1);
  const [panX, setPanX] = useState<number>(0);
  const [panY, setPanY] = useState<number>(0);

  useEffect(() => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      setImgEl(img);
    };
    img.src = imageSrc;
  }, [imageSrc]);

  const resetAll = () => {
    setSelectedFilter('none');
    setBrightness(0);
    setContrast(0);
    setSaturation(0);
    setTemperature(0);
    setHueRotate(0);
    setColorBalanceRG(0);
    setOpacity(1);
    setCropAspect('free');
    setZoom(1);
    setPanX(0);
    setPanY(0);
  };

  useEffect(() => {
    if (!imgEl || !previewCanvasRef.current) return;
    const canvas = previewCanvasRef.current;

    // Determine output dimensions (max 380px for real-time responsiveness)
    const MAX_DIM = 360;
    let targetW = imgEl.width;
    let targetH = imgEl.height;

    if (cropAspect === '1:1') {
      targetW = MAX_DIM;
      targetH = MAX_DIM;
    } else if (cropAspect === '4:3') {
      targetW = MAX_DIM;
      targetH = Math.round((MAX_DIM * 3) / 4);
    } else if (cropAspect === '16:9') {
      targetW = MAX_DIM;
      targetH = Math.round((MAX_DIM * 9) / 16);
    } else {
      const scale = Math.min(MAX_DIM / imgEl.width, MAX_DIM / imgEl.height, 1);
      targetW = Math.max(80, Math.round(imgEl.width * scale));
      targetH = Math.max(80, Math.round(imgEl.height * scale));
    }

    canvas.width = targetW;
    canvas.height = targetH;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, targetW, targetH);

    // Draw zoomed/panned source image
    const coverScale = Math.max(targetW / imgEl.width, targetH / imgEl.height) * zoom;
    const drawW = imgEl.width * coverScale;
    const drawH = imgEl.height * coverScale;
    const drawX = (targetW - drawW) / 2 + panX;
    const drawY = (targetH - drawH) / 2 + panY;

    ctx.drawImage(imgEl, drawX, drawY, drawW, drawH);

    // Extract ImageData for pixel-level DSP & Filter Pipeline
    const imgData = ctx.getImageData(0, 0, targetW, targetH);
    let data = imgData.data;

    // 1. Apply spatial / kernel filters first if selected
    if (selectedFilter === 'blur' || selectedFilter === 'lowpass') {
      data = applyConvolution3x3(
        data,
        targetW,
        targetH,
        [1, 2, 1, 2, 4, 2, 1, 2, 1],
        16,
        0
      );
      if (selectedFilter === 'blur') {
        data = applyConvolution3x3(
          data,
          targetW,
          targetH,
          [1, 2, 1, 2, 4, 2, 1, 2, 1],
          16,
          0
        );
      }
    } else if (selectedFilter === 'sharpen') {
      data = applyConvolution3x3(
        data,
        targetW,
        targetH,
        [0, -1, 0, -1, 5, -1, 0, -1, 0],
        1,
        0
      );
    } else if (selectedFilter === 'highpass') {
      data = applyConvolution3x3(
        data,
        targetW,
        targetH,
        [-1, -1, -1, -1, 8, -1, -1, -1, -1],
        1,
        128
      );
    } else if (selectedFilter === 'emboss') {
      data = applyConvolution3x3(
        data,
        targetW,
        targetH,
        [-2, -1, 0, -1, 1, 1, 0, 1, 2],
        1,
        128
      );
    } else if (selectedFilter === 'edge') {
      // Sobel Edge Magnitude
      const gxKernel = [-1, 0, 1, -2, 0, 2, -1, 0, 1];
      const gyKernel = [-1, -2, -1, 0, 0, 0, 1, 2, 1];
      const edgeOut = new Uint8ClampedArray(data.length);
      for (let y = 0; y < targetH; y++) {
        for (let x = 0; x < targetW; x++) {
          let rx = 0,
            ry = 0;
          for (let ky = -1; ky <= 1; ky++) {
            for (let kx = -1; kx <= 1; kx++) {
              const py = Math.min(targetH - 1, Math.max(0, y + ky));
              const px = Math.min(targetW - 1, Math.max(0, x + kx));
              const idx = (py * targetW + px) * 4;
              const lum = 0.299 * data[idx] + 0.587 * data[idx + 1] + 0.114 * data[idx + 2];
              const kIdx = (ky + 1) * 3 + (kx + 1);
              rx += lum * gxKernel[kIdx];
              ry += lum * gyKernel[kIdx];
            }
          }
          const mag = clampByte(Math.sqrt(rx * rx + ry * ry));
          const dst = (y * targetW + x) * 4;
          edgeOut[dst] = mag;
          edgeOut[dst + 1] = mag;
          edgeOut[dst + 2] = mag;
          edgeOut[dst + 3] = data[dst + 3];
        }
      }
      data = edgeOut;
    } else if (selectedFilter === 'denoise') {
      // 3x3 Median-like smooth denoise filter
      data = applyConvolution3x3(
        data,
        targetW,
        targetH,
        [1, 1, 1, 1, 2, 1, 1, 1, 1],
        10,
        0
      );
    } else if (selectedFilter === 'pixelate') {
      const blockSize = 8;
      const pixOut = new Uint8ClampedArray(data);
      for (let y = 0; y < targetH; y += blockSize) {
        for (let x = 0; x < targetW; x += blockSize) {
          const baseIdx = (y * targetW + x) * 4;
          const r = data[baseIdx];
          const g = data[baseIdx + 1];
          const b = data[baseIdx + 2];
          for (let by = 0; by < blockSize && y + by < targetH; by++) {
            for (let bx = 0; bx < blockSize && x + bx < targetW; bx++) {
              const idx = ((y + by) * targetW + (x + bx)) * 4;
              pixOut[idx] = r;
              pixOut[idx + 1] = g;
              pixOut[idx + 2] = b;
            }
          }
        }
      }
      data = pixOut;
    } else if (selectedFilter === 'fisheye') {
      // Barrel / Fish-eye radial warp
      const warpOut = new Uint8ClampedArray(data.length);
      const cx = targetW / 2;
      const cy = targetH / 2;
      const maxR = Math.sqrt(cx * cx + cy * cy);
      for (let y = 0; y < targetH; y++) {
        for (let x = 0; x < targetW; x++) {
          const dx = (x - cx) / cx;
          const dy = (y - cy) / cy;
          const r = Math.sqrt(dx * dx + dy * dy);
          const theta = Math.atan2(dy, dx);
          const rn = Math.pow(r, 1.45);
          const srcX = Math.min(targetW - 1, Math.max(0, Math.round(cx + rn * cx * Math.cos(theta))));
          const srcY = Math.min(targetH - 1, Math.max(0, Math.round(cy + rn * cy * Math.sin(theta))));
          const dstIdx = (y * targetW + x) * 4;
          const srcIdx = (srcY * targetW + srcX) * 4;
          warpOut[dstIdx] = data[srcIdx];
          warpOut[dstIdx + 1] = data[srcIdx + 1];
          warpOut[dstIdx + 2] = data[srcIdx + 2];
          warpOut[dstIdx + 3] = data[srcIdx + 3];
          void maxR;
        }
      }
      data = warpOut;
    }

    // 2. Per-pixel color, preset & continuous adjustments
    const contrastFactor = (259 * (contrast + 255)) / (255 * (259 - contrast));
    const satFactor = 1 + saturation / 100;
    const effectiveHue =
      (hueRotate + (selectedFilter === 'huerotate' ? 140 : 0)) * (Math.PI / 180);
    const cosA = Math.cos(effectiveHue);
    const sinA = Math.sin(effectiveHue);

    const cx = targetW / 2;
    const cy = targetH / 2;
    const maxDist = Math.sqrt(cx * cx + cy * cy);

    for (let i = 0; i < data.length; i += 4) {
      let r = data[i];
      let g = data[i + 1];
      let b = data[i + 2];

      // Preset color transformations
      if (selectedFilter === 'grayscale') {
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        r = g = b = lum;
      } else if (selectedFilter === 'sepia') {
        const sr = r * 0.393 + g * 0.769 + b * 0.189;
        const sg = r * 0.349 + g * 0.686 + b * 0.168;
        const sb = r * 0.272 + g * 0.534 + b * 0.131;
        r = sr;
        g = sg;
        b = sb;
      } else if (selectedFilter === 'invert') {
        r = 255 - r;
        g = 255 - g;
        b = 255 - b;
      } else if (selectedFilter === 'threshold') {
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        const bin = lum >= 128 ? 255 : 0;
        r = g = b = bin;
      } else if (selectedFilter === 'warmth') {
        r += 32;
        g += 10;
        b -= 28;
      } else if (selectedFilter === 'vintage') {
        r = r * 0.9 + 35;
        g = g * 0.85 + 20;
        b = b * 0.7 + 10;
      } else if (selectedFilter === 'noir') {
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        const c = (lum - 128) * 1.65 + 115;
        r = g = b = c;
      } else if (selectedFilter === 'hdr') {
        // Non-linear shadow boost & highlight compression + vibrance
        const lum = (r + g + b) / 3 || 1;
        const hdrLum = 255 * Math.pow(lum / 255, 0.82);
        const ratio = hdrLum / lum;
        r = (r - lum) * 1.35 + lum * ratio;
        g = (g - lum) * 1.35 + lum * ratio;
        b = (b - lum) * 1.35 + lum * ratio;
      } else if (selectedFilter === 'colorbalance') {
        r += 25;
        g -= 10;
        b += 30;
      } else if (selectedFilter === 'posterize') {
        const levels = 4;
        const step = 255 / (levels - 1);
        r = Math.round(Math.round(r / step) * step);
        g = Math.round(Math.round(g / step) * step);
        b = Math.round(Math.round(b / step) * step);
      }

      // Vignette radial falloff
      if (selectedFilter === 'vignette' || selectedFilter === 'noir' || selectedFilter === 'vintage') {
        const pxIdx = i / 4;
        const py = Math.floor(pxIdx / targetW);
        const px = pxIdx % targetW;
        const dist = Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
        const vig = 1 - Math.pow(dist / maxDist, 1.8) * 0.65;
        r *= vig;
        g *= vig;
        b *= vig;
      }

      // Continuous Sliders: Brightness
      if (brightness !== 0) {
        r += brightness * 1.8;
        g += brightness * 1.8;
        b += brightness * 1.8;
      }

      // Contrast
      if (contrast !== 0) {
        r = contrastFactor * (r - 128) + 128;
        g = contrastFactor * (g - 128) + 128;
        b = contrastFactor * (b - 128) + 128;
      }

      // Saturation (Vibrance)
      if (saturation !== 0) {
        const gray = 0.2989 * r + 0.587 * g + 0.114 * b;
        r = gray + (r - gray) * satFactor;
        g = gray + (g - gray) * satFactor;
        b = gray + (b - gray) * satFactor;
      }

      // Warmth / Coolness Temperature slider
      if (temperature !== 0) {
        r += temperature * 0.6;
        b -= temperature * 0.6;
      }

      // Color Balance slider
      if (colorBalanceRG !== 0) {
        r += colorBalanceRG * 0.5;
        g -= colorBalanceRG * 0.25;
      }

      // Hue Rotate matrix
      if (effectiveHue !== 0) {
        const rx =
          r * (0.213 + cosA * 0.787 - sinA * 0.213) +
          g * (0.715 - cosA * 0.715 - sinA * 0.715) +
          b * (0.072 - cosA * 0.072 + sinA * 0.928);
        const gx =
          r * (0.213 - cosA * 0.213 + sinA * 0.143) +
          g * (0.715 + cosA * 0.285 + sinA * 0.14) +
          b * (0.072 - cosA * 0.072 - sinA * 0.283);
        const bx =
          r * (0.213 - cosA * 0.213 - sinA * 0.787) +
          g * (0.715 - cosA * 0.715 + sinA * 0.715) +
          b * (0.072 + cosA * 0.928 + sinA * 0.072);
        r = rx;
        g = gx;
        b = bx;
      }

      data[i] = clampByte(r);
      data[i + 1] = clampByte(g);
      data[i + 2] = clampByte(b);
      data[i + 3] = clampByte(data[i + 3] * opacity);
    }

    const outImgData = new ImageData(new Uint8ClampedArray(data), targetW, targetH);
    ctx.putImageData(outImgData, 0, 0);
  }, [
    imgEl,
    selectedFilter,
    brightness,
    contrast,
    saturation,
    temperature,
    hueRotate,
    colorBalanceRG,
    opacity,
    cropAspect,
    zoom,
    panX,
    panY,
  ]);

  const handlePlaceOnCanvas = () => {
    if (!previewCanvasRef.current) return;
    const dataUrl = previewCanvasRef.current.toDataURL('image/png');
    const w = Math.min(220, previewCanvasRef.current.width);
    const h = Math.round(
      (w * previewCanvasRef.current.height) / previewCanvasRef.current.width
    );
    onConfirm(dataUrl, opacity, w, h);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-3 backdrop-blur-xs">
      <div className="flex max-h-[92dvh] w-full max-w-md flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-3.5 py-2.5">
          <h3 className="font-wiki-serif text-base font-bold">
            Edit &amp; Place Image on Canvas
          </h3>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={resetAll}
              className="flex h-8 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 text-[11px] font-medium"
              title="Reset all filters and adjustments"
            >
              <RotateCcw className="h-3 w-3" />
              Reset
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)]"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Live Canvas Preview */}
        <div className="flex shrink-0 items-center justify-center bg-[#101418] p-3">
          <canvas
            ref={previewCanvasRef}
            className="max-h-[210px] max-w-full border border-[var(--wiki-border)] object-contain shadow-md"
          />
        </div>

        {/* Mode Tabs: Filters (22+) | Adjust & Opacity | Crop & Zoom */}
        <div className="grid shrink-0 grid-cols-3 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] text-xs font-semibold">
          <button
            type="button"
            onClick={() => setActiveTab('filters')}
            className={`flex items-center justify-center gap-1.5 py-2.5 border-b-2 transition-colors ${
              activeTab === 'filters'
                ? 'border-[#3366cc] text-[#3366cc] bg-[var(--wiki-bg)]'
                : 'border-transparent text-[var(--wiki-muted)]'
            }`}
          >
            <Sparkles className="h-3.5 w-3.5" />
            Filters
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('adjust')}
            className={`flex items-center justify-center gap-1.5 py-2.5 border-b-2 transition-colors ${
              activeTab === 'adjust'
                ? 'border-[#3366cc] text-[#3366cc] bg-[var(--wiki-bg)]'
                : 'border-transparent text-[var(--wiki-muted)]'
            }`}
          >
            <Sliders className="h-3.5 w-3.5" />
            Adjust &amp; Opacity
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('crop')}
            className={`flex items-center justify-center gap-1.5 py-2.5 border-b-2 transition-colors ${
              activeTab === 'crop'
                ? 'border-[#3366cc] text-[#3366cc] bg-[var(--wiki-bg)]'
                : 'border-transparent text-[var(--wiki-muted)]'
            }`}
          >
            <Crop className="h-3.5 w-3.5" />
            Crop
          </button>
        </div>

        {/* Tab Body */}
        <div className="flex-1 overflow-y-auto p-3.5">
          {activeTab === 'filters' && (
            <div className="grid grid-cols-2 gap-1.5">
              {IMAGE_FILTER_LIST.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  onClick={() => setSelectedFilter(f.id)}
                  className={`border px-2.5 py-2 text-left text-xs font-medium transition-colors truncate ${
                    selectedFilter === f.id
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          )}

          {activeTab === 'adjust' && (
            <div className="space-y-3 text-xs">
              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium flex items-center gap-1">
                    <Eye className="h-3.5 w-3.5 text-[#3366cc]" />
                    Transparency / Opacity
                  </span>
                  <span className="font-wiki-mono">{Math.round(opacity * 100)}%</span>
                </div>
                <input
                  type="range"
                  min={0.1}
                  max={1}
                  step={0.05}
                  value={opacity}
                  onChange={(e) => setOpacity(parseFloat(e.target.value))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Brightness</span>
                  <span className="font-wiki-mono">{brightness}</span>
                </div>
                <input
                  type="range"
                  min={-100}
                  max={100}
                  value={brightness}
                  onChange={(e) => setBrightness(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Contrast</span>
                  <span className="font-wiki-mono">{contrast}</span>
                </div>
                <input
                  type="range"
                  min={-100}
                  max={100}
                  value={contrast}
                  onChange={(e) => setContrast(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Saturation (Vibrance)</span>
                  <span className="font-wiki-mono">{saturation}</span>
                </div>
                <input
                  type="range"
                  min={-100}
                  max={100}
                  value={saturation}
                  onChange={(e) => setSaturation(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Warmth / Coolness (Temperature)</span>
                  <span className="font-wiki-mono">{temperature}</span>
                </div>
                <input
                  type="range"
                  min={-100}
                  max={100}
                  value={temperature}
                  onChange={(e) => setTemperature(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Hue Rotate</span>
                  <span className="font-wiki-mono">{hueRotate}°</span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={360}
                  value={hueRotate}
                  onChange={(e) => setHueRotate(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Color Balance (Red / Green)</span>
                  <span className="font-wiki-mono">{colorBalanceRG}</span>
                </div>
                <input
                  type="range"
                  min={-100}
                  max={100}
                  value={colorBalanceRG}
                  onChange={(e) => setColorBalanceRG(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>
            </div>
          )}

          {activeTab === 'crop' && (
            <div className="space-y-3 text-xs">
              <div>
                <span className="block font-medium mb-1.5">Crop Aspect Ratio</span>
                <div className="grid grid-cols-4 gap-2">
                  {(['free', '1:1', '4:3', '16:9'] as const).map((ratio) => (
                    <button
                      key={ratio}
                      type="button"
                      onClick={() => setCropAspect(ratio)}
                      className={`py-2 border text-center font-semibold uppercase ${
                        cropAspect === ratio
                          ? 'border-[#3366cc] bg-[#3366cc] text-white'
                          : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)]'
                      }`}
                    >
                      {ratio}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Zoom</span>
                  <span className="font-wiki-mono">{zoom.toFixed(2)}x</span>
                </div>
                <input
                  type="range"
                  min={1}
                  max={3}
                  step={0.05}
                  value={zoom}
                  onChange={(e) => setZoom(parseFloat(e.target.value))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Horizontal Offset (Pan X)</span>
                  <span className="font-wiki-mono">{panX}px</span>
                </div>
                <input
                  type="range"
                  min={-150}
                  max={150}
                  value={panX}
                  onChange={(e) => setPanX(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>

              <div>
                <div className="flex justify-between mb-1">
                  <span className="font-medium">Vertical Offset (Pan Y)</span>
                  <span className="font-wiki-mono">{panY}px</span>
                </div>
                <input
                  type="range"
                  min={-150}
                  max={150}
                  value={panY}
                  onChange={(e) => setPanY(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3366cc]"
                />
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex shrink-0 items-center justify-end gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-2.5">
          <button
            type="button"
            onClick={onCancel}
            className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3.5 text-xs font-medium"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handlePlaceOnCanvas}
            className="flex h-9 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white"
          >
            <Check className="h-4 w-4" />
            Add to Canvas
          </button>
        </div>
      </div>
    </div>
  );
};

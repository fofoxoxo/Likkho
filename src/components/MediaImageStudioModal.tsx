import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  X,
  RotateCcw,
  Crop,
  Sliders,
  Sparkles,
  Eye,
  Settings2,
  FileArchive,
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

export type ImageCompressionFormat = 'image/jpeg' | 'image/png' | 'image/webp';
export type ImageBitDepth = 24 | 16 | 8 | 1;
export type ImageColorSpaceOption = 'srgb' | 'display-p3' | 'grayscale';
export type ImageExifMode = 'strip' | 'keep';

interface MediaImageStudioModalProps {
  imageSrc: string;
  onCancel: () => void;
  onConfirm: (
    processedDataUrl: string,
    opacity: number,
    width: number,
    height: number
  ) => void;
}

export function estimateDataUrlByteSize(dataUrl: string | null | undefined): number {
  if (!dataUrl) return 0;
  const commaIdx = dataUrl.indexOf(',');
  if (commaIdx < 0) return dataUrl.length;
  const base64 = dataUrl.slice(commaIdx + 1);
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0;
  return Math.max(0, Math.floor((base64.length * 3) / 4) - padding);
}

export function formatByteSizeLabel(bytes: number): string {
  if (bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(kb >= 100 ? 0 : 1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(2)} MB`;
}

function detectOriginalMimeType(dataUrl: string): ImageCompressionFormat {
  if (dataUrl.startsWith('data:image/png')) return 'image/png';
  if (dataUrl.startsWith('data:image/webp')) return 'image/webp';
  return 'image/jpeg';
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
  const [activeTab, setActiveTab] = useState<'encoding' | 'filters' | 'adjust' | 'crop'>('encoding');

  // Compression is strictly OPTIONAL (OFF by default).
  // Users can check the file size and enable compression only if they want to reduce file size.
  const [isCompressionEnabled, setIsCompressionEnabled] = useState<boolean>(false);

  // Original file size & dimensions
  const originalSizeBytes = useMemo(() => estimateDataUrlByteSize(imageSrc), [imageSrc]);
  const [originalWidth, setOriginalWidth] = useState<number>(360);
  const [originalHeight, setOriginalHeight] = useState<number>(360);
  const [estimatedOutputBytes, setEstimatedOutputBytes] = useState<number>(originalSizeBytes);

  // Image Encoding & Format Controls (used when isCompressionEnabled is ON):
  // 1. Dimensions (Resolution)
  const [resolutionWidth, setResolutionWidth] = useState<number>(360);
  const [resolutionHeight, setResolutionHeight] = useState<number>(360);
  const [lockAspect, setLockAspect] = useState<boolean>(true);
  // 2. Compression Format
  const [compressionFormat, setCompressionFormat] =
    useState<ImageCompressionFormat>('image/webp');
  // 3. Quality Percentage
  const [qualityPercent, setQualityPercent] = useState<number>(80);
  // 4. Bit Depth
  const [bitDepth, setBitDepth] = useState<ImageBitDepth>(24);
  // 5. Color Space
  const [colorSpace, setColorSpace] = useState<ImageColorSpaceOption>('srgb');
  // 6. EXIF Metadata
  const [exifMode, setExifMode] = useState<ImageExifMode>('strip');

  // Filter preset
  const [selectedFilter, setSelectedFilter] = useState<ImageFilterPreset>('none');

  // Fine adjustments (Brightness, Contrast, Saturation, Temperature, Hue, Opacity, Red/Blue Balance)
  const [brightness, setBrightness] = useState<number>(0);
  const [contrast, setContrast] = useState<number>(0);
  const [saturation, setSaturation] = useState<number>(0);
  const [temperature, setTemperature] = useState<number>(0);
  const [hueRotate, setHueRotate] = useState<number>(0);
  const [colorBalanceRG, setColorBalanceRG] = useState<number>(0);
  const [opacity, setOpacity] = useState<number>(1);

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
      setOriginalWidth(img.width);
      setOriginalHeight(img.height);
      const scale = Math.min(720 / img.width, 720 / img.height, 1);
      setResolutionWidth(Math.max(64, Math.round(img.width * scale)));
      setResolutionHeight(Math.max(64, Math.round(img.height * scale)));
    };
    img.src = imageSrc;
  }, [imageSrc]);

  const hasVisualEdits =
    selectedFilter !== 'none' ||
    brightness !== 0 ||
    contrast !== 0 ||
    saturation !== 0 ||
    temperature !== 0 ||
    hueRotate !== 0 ||
    colorBalanceRG !== 0 ||
    opacity !== 1 ||
    cropAspect !== 'free' ||
    zoom !== 1 ||
    panX !== 0 ||
    panY !== 0;

  const resetAll = () => {
    setIsCompressionEnabled(false);
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
    setCompressionFormat('image/webp');
    setQualityPercent(80);
    setBitDepth(24);
    setColorSpace('srgb');
    setExifMode('strip');
    if (imgEl) {
      const scale = Math.min(720 / imgEl.width, 720 / imgEl.height, 1);
      setResolutionWidth(Math.max(64, Math.round(imgEl.width * scale)));
      setResolutionHeight(Math.max(64, Math.round(imgEl.height * scale)));
    }
  };

  const handleWidthInput = (newW: number) => {
    const clampedW = Math.min(2400, Math.max(40, newW || 40));
    setResolutionWidth(clampedW);
    if (lockAspect && imgEl && imgEl.width > 0) {
      setResolutionHeight(
        Math.max(40, Math.round((clampedW * imgEl.height) / imgEl.width))
      );
    }
  };

  const handleHeightInput = (newH: number) => {
    const clampedH = Math.min(2400, Math.max(40, newH || 40));
    setResolutionHeight(clampedH);
    if (lockAspect && imgEl && imgEl.height > 0) {
      setResolutionWidth(
        Math.max(40, Math.round((clampedH * imgEl.width) / imgEl.height))
      );
    }
  };

  useEffect(() => {
    if (!imgEl || !previewCanvasRef.current) return;
    const canvas = previewCanvasRef.current;

    const baseW = isCompressionEnabled
      ? Math.min(2000, Math.max(40, resolutionWidth))
      : Math.min(2000, Math.max(40, originalWidth));
    const baseH = isCompressionEnabled
      ? Math.min(2000, Math.max(40, resolutionHeight))
      : Math.min(2000, Math.max(40, originalHeight));

    let targetW = baseW;
    let targetH = baseH;

    if (cropAspect === '1:1') {
      targetH = targetW;
    } else if (cropAspect === '4:3') {
      targetH = Math.round((targetW * 3) / 4);
    } else if (cropAspect === '16:9') {
      targetH = Math.round((targetW * 9) / 16);
    }

    canvas.width = targetW;
    canvas.height = targetH;

    const effectiveColorSpace = isCompressionEnabled ? colorSpace : 'srgb';
    const effectiveBitDepth = isCompressionEnabled ? bitDepth : 24;

    const ctx = canvas.getContext('2d', {
      colorSpace: effectiveColorSpace === 'display-p3' ? 'display-p3' : 'srgb',
    });
    if (!ctx) return;

    ctx.clearRect(0, 0, targetW, targetH);

    // Draw zoomed/panned source image
    const coverScale = Math.max(targetW / imgEl.width, targetH / imgEl.height) * zoom;
    const drawW = imgEl.width * coverScale;
    const drawH = imgEl.height * coverScale;
    const drawX = (targetW - drawW) / 2 + panX;
    const drawY = (targetH - drawH) / 2 + panY;

    ctx.drawImage(imgEl, drawX, drawY, drawW, drawH);

    if (hasVisualEdits || isCompressionEnabled) {
      const imgData = ctx.getImageData(0, 0, targetW, targetH);
      let data = imgData.data;

      // 1. Spatial / kernel filters
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
        const warpOut = new Uint8ClampedArray(data.length);
        const cx = targetW / 2;
        const cy = targetH / 2;
        for (let y = 0; y < targetH; y++) {
          for (let x = 0; x < targetW; x++) {
            const dx = (x - cx) / cx;
            const dy = (y - cy) / cy;
            const r = Math.sqrt(dx * dx + dy * dy);
            const theta = Math.atan2(dy, dx);
            const rn = Math.pow(r, 1.45);
            const srcX = Math.min(
              targetW - 1,
              Math.max(0, Math.round(cx + rn * cx * Math.cos(theta)))
            );
            const srcY = Math.min(
              targetH - 1,
              Math.max(0, Math.round(cy + rn * cy * Math.sin(theta)))
            );
            const dstIdx = (y * targetW + x) * 4;
            const srcIdx = (srcY * targetW + srcX) * 4;
            warpOut[dstIdx] = data[srcIdx];
            warpOut[dstIdx + 1] = data[srcIdx + 1];
            warpOut[dstIdx + 2] = data[srcIdx + 2];
            warpOut[dstIdx + 3] = data[srcIdx + 3];
          }
        }
        data = warpOut;
      }

      // 2. Per-pixel color, preset, Bit Depth & Color Space quantization
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

        if (selectedFilter === 'grayscale' || effectiveColorSpace === 'grayscale') {
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

        if (effectiveColorSpace === 'display-p3') {
          const lum = 0.299 * r + 0.587 * g + 0.114 * b;
          r = lum + (r - lum) * 1.12;
          g = lum + (g - lum) * 1.12;
          b = lum + (b - lum) * 1.12;
        }

        if (
          selectedFilter === 'vignette' ||
          selectedFilter === 'noir' ||
          selectedFilter === 'vintage'
        ) {
          const pxIdx = i / 4;
          const py = Math.floor(pxIdx / targetW);
          const px = pxIdx % targetW;
          const dist = Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
          const vig = 1 - Math.pow(dist / maxDist, 1.8) * 0.65;
          r *= vig;
          g *= vig;
          b *= vig;
        }

        if (brightness !== 0) {
          r += brightness * 1.8;
          g += brightness * 1.8;
          b += brightness * 1.8;
        }

        if (contrast !== 0) {
          r = contrastFactor * (r - 128) + 128;
          g = contrastFactor * (g - 128) + 128;
          b = contrastFactor * (b - 128) + 128;
        }

        if (saturation !== 0 && effectiveColorSpace !== 'grayscale') {
          const gray = 0.2989 * r + 0.587 * g + 0.114 * b;
          r = gray + (r - gray) * satFactor;
          g = gray + (g - gray) * satFactor;
          b = gray + (b - gray) * satFactor;
        }

        if (temperature !== 0) {
          r += temperature * 0.6;
          b -= temperature * 0.6;
        }

        if (colorBalanceRG !== 0) {
          r += colorBalanceRG * 0.5;
          g -= colorBalanceRG * 0.25;
        }

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

        if (effectiveBitDepth === 16) {
          r = Math.round((clampByte(r) / 255) * 31) * (255 / 31);
          g = Math.round((clampByte(g) / 255) * 63) * (255 / 63);
          b = Math.round((clampByte(b) / 255) * 31) * (255 / 31);
        } else if (effectiveBitDepth === 8) {
          r = Math.round((clampByte(r) / 255) * 7) * (255 / 7);
          g = Math.round((clampByte(g) / 255) * 7) * (255 / 7);
          b = Math.round((clampByte(b) / 255) * 3) * (255 / 3);
        } else if (effectiveBitDepth === 1) {
          const lum = 0.299 * clampByte(r) + 0.587 * clampByte(g) + 0.114 * clampByte(b);
          r = g = b = lum >= 128 ? 255 : 0;
        }

        data[i] = clampByte(r);
        data[i + 1] = clampByte(g);
        data[i + 2] = clampByte(b);
        data[i + 3] = clampByte(data[i + 3] * opacity);
      }

      const outImgData = new ImageData(new Uint8ClampedArray(data), targetW, targetH);
      ctx.putImageData(outImgData, 0, 0);
    }

    // Update estimated output byte size so user can compare Original vs Compressed
    if (!isCompressionEnabled && !hasVisualEdits) {
      setEstimatedOutputBytes(originalSizeBytes);
    } else {
      const outFmt = isCompressionEnabled
        ? compressionFormat
        : detectOriginalMimeType(imageSrc);
      const outQuality = isCompressionEnabled
        ? Math.min(1, Math.max(0.05, qualityPercent / 100))
        : 0.96;
      const previewUrl = canvas.toDataURL(outFmt, outQuality);
      setEstimatedOutputBytes(estimateDataUrlByteSize(previewUrl));
    }
  }, [
    imgEl,
    imageSrc,
    isCompressionEnabled,
    hasVisualEdits,
    originalWidth,
    originalHeight,
    originalSizeBytes,
    resolutionWidth,
    resolutionHeight,
    compressionFormat,
    qualityPercent,
    bitDepth,
    colorSpace,
    exifMode,
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
    // If compression is NOT enabled and no visual filter/crop/adjustment was made,
    // use the original imageSrc directly without re-encoding or compressing!
    if (!isCompressionEnabled && !hasVisualEdits) {
      const displayW = Math.min(240, originalWidth || 240);
      const displayH =
        originalWidth > 0
          ? Math.round((displayW * originalHeight) / originalWidth)
          : 240;
      onConfirm(imageSrc, opacity, displayW, displayH);
      return;
    }

    if (!previewCanvasRef.current) return;
    const outFmt = isCompressionEnabled
      ? compressionFormat
      : detectOriginalMimeType(imageSrc);
    const qualityRatio = isCompressionEnabled
      ? Math.min(1, Math.max(0.05, qualityPercent / 100))
      : 0.96;
    const dataUrl = previewCanvasRef.current.toDataURL(outFmt, qualityRatio);
    const w = Math.min(240, previewCanvasRef.current.width);
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
            Image Studio &amp; Optional Compression
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

        {/* Live Canvas Preview + File Size Info Bar */}
        <div className="flex shrink-0 flex-col items-center justify-center bg-[#101418] p-2.5">
          <canvas
            ref={previewCanvasRef}
            className="max-h-[175px] max-w-full border border-[var(--wiki-border)] object-contain shadow-md"
          />
          <div className="mt-1.5 flex flex-wrap items-center justify-center gap-2 font-wiki-mono text-[10px] text-white/90">
            <span>
              Original: <strong>{formatByteSizeLabel(originalSizeBytes)}</strong> ({originalWidth}×
              {originalHeight}px)
            </span>
            {isCompressionEnabled ? (
              <>
                <span>→</span>
                <span className="text-[#99f6e4] font-bold">
                  Compressed: {formatByteSizeLabel(estimatedOutputBytes)} ({resolutionWidth}×
                  {resolutionHeight}px · {compressionFormat.replace('image/', '').toUpperCase()} Q:
                  {qualityPercent}%)
                </span>
              </>
            ) : (
              <span className="rounded-xs bg-white/15 px-1.5 py-0.5 text-[9px] font-semibold text-white">
                Compression: OFF (Original Quality)
              </span>
            )}
          </div>
        </div>

        {/* Mode Tabs: Optional Compress | Filters (22+) | Adjust & Opacity | Crop */}
        <div className="grid shrink-0 grid-cols-4 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] text-[11px] font-semibold">
          <button
            type="button"
            onClick={() => setActiveTab('encoding')}
            className={`flex items-center justify-center gap-1 py-2.5 border-b-2 transition-colors ${
              activeTab === 'encoding'
                ? 'border-[#3366cc] text-[#3366cc] bg-[var(--wiki-bg)]'
                : 'border-transparent text-[var(--wiki-muted)]'
            }`}
          >
            <Settings2 className="h-3.5 w-3.5" />
            Compress
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('filters')}
            className={`flex items-center justify-center gap-1 py-2.5 border-b-2 transition-colors ${
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
            className={`flex items-center justify-center gap-1 py-2.5 border-b-2 transition-colors ${
              activeTab === 'adjust'
                ? 'border-[#3366cc] text-[#3366cc] bg-[var(--wiki-bg)]'
                : 'border-transparent text-[var(--wiki-muted)]'
            }`}
          >
            <Sliders className="h-3.5 w-3.5" />
            Adjust
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('crop')}
            className={`flex items-center justify-center gap-1 py-2.5 border-b-2 transition-colors ${
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
          {activeTab === 'encoding' && (
            <div className="space-y-3.5 text-xs">
              {/* Optional Compression Toggle Card */}
              <div className="flex items-center justify-between gap-3 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-1.5 font-semibold text-[var(--wiki-text)]">
                    <FileArchive className="h-4 w-4 shrink-0 text-[#3366cc]" />
                    <span>Compress Image (Optional)</span>
                  </div>
                  <p className="mt-0.5 text-[11px] leading-snug text-[var(--wiki-muted)]">
                    File size is <strong>{formatByteSizeLabel(originalSizeBytes)}</strong>. Enable
                    compression only if you want to reduce the file size.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setIsCompressionEnabled((prev) => !prev)}
                  className={`shrink-0 border px-3 py-1.5 text-xs font-bold transition-colors ${
                    isCompressionEnabled
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                >
                  {isCompressionEnabled ? 'ON' : 'OFF'}
                </button>
              </div>

              {!isCompressionEnabled ? (
                <div className="border border-dashed border-[var(--wiki-border)] bg-[var(--wiki-surface)]/50 p-3 text-center text-[11px] text-[var(--wiki-muted)]">
                  Compression is currently <strong>OFF</strong>. The image will keep its original
                  file size ({formatByteSizeLabel(originalSizeBytes)}) and original resolution (
                  {originalWidth}×{originalHeight}px). Turn <strong>ON</strong> above if the file
                  size is large and you want to compress it.
                </div>
              ) : (
                <>
                  {/* 1. Dimensions (Resolution) */}
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="font-semibold">1. Dimensions (Resolution)</span>
                      <label className="flex items-center gap-1 text-[11px] text-[var(--wiki-muted)] cursor-pointer">
                        <input
                          type="checkbox"
                          checked={lockAspect}
                          onChange={(e) => setLockAspect(e.target.checked)}
                          className="accent-[#3366cc]"
                        />
                        Lock Aspect Ratio
                      </label>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <span className="block text-[10px] text-[var(--wiki-muted)] mb-0.5">
                          Width (px)
                        </span>
                        <input
                          type="number"
                          min={40}
                          max={2400}
                          value={resolutionWidth}
                          onChange={(e) => handleWidthInput(parseInt(e.target.value, 10))}
                          className="h-8 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2 font-wiki-mono text-xs outline-none focus:border-[#3366cc]"
                        />
                      </div>
                      <div>
                        <span className="block text-[10px] text-[var(--wiki-muted)] mb-0.5">
                          Height (px)
                        </span>
                        <input
                          type="number"
                          min={40}
                          max={2400}
                          value={resolutionHeight}
                          onChange={(e) => handleHeightInput(parseInt(e.target.value, 10))}
                          className="h-8 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2 font-wiki-mono text-xs outline-none focus:border-[#3366cc]"
                        />
                      </div>
                    </div>
                  </div>

                  {/* 2. Compression Format */}
                  <div>
                    <span className="block font-semibold mb-1.5">2. Compression Format</span>
                    <div className="grid grid-cols-3 gap-1.5">
                      {(
                        [
                          { id: 'image/webp', label: 'WebP' },
                          { id: 'image/jpeg', label: 'JPEG' },
                          { id: 'image/png', label: 'PNG (Lossless)' },
                        ] as const
                      ).map((fmt) => (
                        <button
                          key={fmt.id}
                          type="button"
                          onClick={() => setCompressionFormat(fmt.id)}
                          className={`border py-2 text-center font-semibold transition-colors ${
                            compressionFormat === fmt.id
                              ? 'border-[#3366cc] bg-[#3366cc] text-white'
                              : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                          }`}
                        >
                          {fmt.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 3. Quality Percentage */}
                  <div>
                    <div className="flex justify-between mb-1">
                      <span className="font-semibold">3. Quality Percentage</span>
                      <span className="font-wiki-mono font-bold text-[#3366cc]">
                        {qualityPercent}%
                      </span>
                    </div>
                    <input
                      type="range"
                      min={10}
                      max={100}
                      step={1}
                      value={qualityPercent}
                      onChange={(e) => setQualityPercent(parseInt(e.target.value, 10))}
                      className="w-full accent-[#3366cc]"
                    />
                  </div>

                  {/* 4. Bit Depth */}
                  <div>
                    <span className="block font-semibold mb-1.5">4. Bit Depth</span>
                    <div className="grid grid-cols-4 gap-1.5">
                      {(
                        [
                          { bits: 24, label: '24-bit RGB' },
                          { bits: 16, label: '16-bit Hi' },
                          { bits: 8, label: '8-bit 256c' },
                          { bits: 1, label: '1-bit Mono' },
                        ] as const
                      ).map((b) => (
                        <button
                          key={b.bits}
                          type="button"
                          onClick={() => setBitDepth(b.bits)}
                          className={`border py-1.5 text-center font-wiki-mono text-[11px] font-semibold transition-colors ${
                            bitDepth === b.bits
                              ? 'border-[#3366cc] bg-[#3366cc] text-white'
                              : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                          }`}
                        >
                          {b.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 5. Color Space */}
                  <div>
                    <span className="block font-semibold mb-1.5">5. Color Space</span>
                    <div className="grid grid-cols-3 gap-1.5">
                      {(
                        [
                          { id: 'srgb', label: 'sRGB' },
                          { id: 'display-p3', label: 'Display P3' },
                          { id: 'grayscale', label: 'Grayscale' },
                        ] as const
                      ).map((cs) => (
                        <button
                          key={cs.id}
                          type="button"
                          onClick={() => setColorSpace(cs.id)}
                          className={`border py-1.5 text-center font-semibold transition-colors ${
                            colorSpace === cs.id
                              ? 'border-[#3366cc] bg-[#3366cc] text-white'
                              : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                          }`}
                        >
                          {cs.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 6. EXIF Metadata */}
                  <div>
                    <span className="block font-semibold mb-1.5">6. EXIF Metadata</span>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setExifMode('strip')}
                        className={`border py-2 text-center font-semibold transition-colors ${
                          exifMode === 'strip'
                            ? 'border-[#3366cc] bg-[#3366cc] text-white'
                            : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                        }`}
                      >
                        Strip EXIF (Privacy)
                      </button>
                      <button
                        type="button"
                        onClick={() => setExifMode('keep')}
                        className={`border py-2 text-center font-semibold transition-colors ${
                          exifMode === 'keep'
                            ? 'border-[#3366cc] bg-[#3366cc] text-white'
                            : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                        }`}
                      >
                        Retain EXIF Metadata
                      </button>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}

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
            {isCompressionEnabled ? 'Compress & Apply' : 'Add Without Compression'}
          </button>
        </div>
      </div>
    </div>
  );
};

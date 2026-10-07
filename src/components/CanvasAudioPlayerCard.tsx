import React, { useEffect, useRef, useState } from 'react';
import { Play, Pause } from 'lucide-react';
import { CanvasAudioAttachment } from '../utils/cryptoVault';

interface CanvasAudioPlayerCardProps {
  audio: CanvasAudioAttachment;
  isReadingMode: boolean;
  isSelected: boolean;
  onSelect: () => void;
}

function formatAudioSeconds(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00';
  const total = Math.floor(sec);
  const mins = Math.floor(total / 60);
  const rem = total % 60;
  return `${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
}

export const CanvasAudioPlayerCard: React.FC<CanvasAudioPlayerCardProps> = ({
  audio,
  isReadingMode,
  isSelected,
  onSelect,
}) => {
  const audioElRef = useRef<HTMLAudioElement | null>(null);
  const lastTapTimeRef = useRef<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(audio.durationSec || 0);

  useEffect(() => {
    const el = audioElRef.current;
    if (!el) return;

    const onLoadedMetadata = () => {
      if (Number.isFinite(el.duration) && el.duration > 0) {
        setDuration(el.duration);
      }
    };
    const onTimeUpdate = () => {
      setCurrentTime(el.currentTime || 0);
    };
    const onEnded = () => {
      setIsPlaying(false);
      setCurrentTime(0);
    };
    const onPause = () => setIsPlaying(false);
    const onPlay = () => setIsPlaying(true);

    el.addEventListener('loadedmetadata', onLoadedMetadata);
    el.addEventListener('timeupdate', onTimeUpdate);
    el.addEventListener('ended', onEnded);
    el.addEventListener('pause', onPause);
    el.addEventListener('play', onPlay);

    return () => {
      el.removeEventListener('loadedmetadata', onLoadedMetadata);
      el.removeEventListener('timeupdate', onTimeUpdate);
      el.removeEventListener('ended', onEnded);
      el.removeEventListener('pause', onPause);
      el.removeEventListener('play', onPlay);
    };
  }, [audio.dataUrl]);

  // Sync playbackRate from toolbar state
  useEffect(() => {
    const el = audioElRef.current;
    if (el) {
      el.playbackRate = audio.playbackRate || 1;
    }
  }, [audio.playbackRate]);

  const togglePlayPause = (e: React.MouseEvent) => {
    e.stopPropagation();
    const el = audioElRef.current;
    if (!el) return;
    el.playbackRate = audio.playbackRate || 1;
    if (isPlaying) {
      el.pause();
    } else {
      el.play().catch(() => {});
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.stopPropagation();
    const el = audioElRef.current;
    const nextTime = parseFloat(e.target.value);
    setCurrentTime(nextTime);
    if (el && Number.isFinite(nextTime)) {
      el.currentTime = nextTime;
    }
  };

  const isBehindText = audio.layer === 'background';
  const computedZIndex = isSelected ? 26 : isBehindText ? 6 : 21;
  const xPos = audio.x ?? 24;
  const yPos = audio.y ?? 140;
  const cardWidth = audio.width ?? 270;
  const cardHeight = audio.height ?? 56;
  const rotationDeg = audio.rotation ?? 0;
  const effectiveDuration = duration > 0 ? duration : audio.durationSec || 1;

  // Strictly ONLY Double-Tap activates editing tools on the Audio Player!
  // Triggered on pointerUp so inserting the toolbar row above the canvas NEVER causes a synthetic click on the underlying editor that closes the toolbar!
  const handlePointerUpDoubleTap = (e: React.PointerEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement;
    if (target.closest('[data-audio-control="true"]')) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    if (isReadingMode) return;

    const now = Date.now();
    if (now - lastTapTimeRef.current < 380) {
      lastTapTimeRef.current = 0;
      if (document.activeElement instanceof HTMLElement) {
        document.activeElement.blur();
      }
      window.getSelection()?.removeAllRanges();
      onSelect();
    } else {
      lastTapTimeRef.current = now;
    }
  };

  return (
    <div
      data-canvas-audio-card="true"
      contentEditable={false}
      onPointerDown={(e) => {
        const target = e.target as HTMLElement;
        if (target.closest('[data-audio-control="true"]')) {
          return;
        }
        e.preventDefault();
        e.stopPropagation();
      }}
      onPointerUp={handlePointerUpDoubleTap}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
      }}
      onDoubleClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        if (!isReadingMode) {
          onSelect();
        }
      }}
      style={{
        position: 'absolute',
        left: `${xPos}px`,
        top: `${yPos}px`,
        width: `${cardWidth}px`,
        height: `${cardHeight}px`,
        transform: `rotate(${rotationDeg}deg)`,
        transformOrigin: 'center center',
        backgroundColor: '#b32424',
        opacity: 1,
        zIndex: computedZIndex,
      }}
      className={`touch-none select-none flex flex-col justify-center rounded-none border border-[#7a1616] bg-[#b32424] px-3 py-1.5 text-white shadow-md overflow-hidden ${
        isReadingMode
          ? 'pointer-events-auto cursor-default'
          : isSelected
          ? 'cursor-pointer ring-2 ring-[#3366cc] pointer-events-auto'
          : 'cursor-pointer pointer-events-auto'
      }`}
    >
      {/* Hidden native HTML5 Audio Engine */}
      <audio ref={audioElRef} src={audio.dataUrl} preload="metadata" className="hidden" />

      {/* Opaque Red Rectangular Body: Play/Pause Button BEFORE the Slider */}
      <div className="flex items-center gap-2.5 w-full">
        <button
          type="button"
          data-audio-control="true"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={togglePlayPause}
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-none border border-white/40 bg-[#8e1b1b] text-white hover:bg-[#751515] active:scale-95 transition-transform"
          title={isPlaying ? 'Pause' : 'Play'}
          aria-label={isPlaying ? 'Pause audio' : 'Play audio'}
        >
          {isPlaying ? (
            <Pause className="h-4 w-4 fill-current" />
          ) : (
            <Play className="h-4 w-4 fill-current ml-0.5" />
          )}
        </button>

        <div className="flex flex-1 flex-col gap-1 min-w-0">
          <input
            type="range"
            data-audio-control="true"
            onPointerDown={(e) => e.stopPropagation()}
            min={0}
            max={effectiveDuration}
            step={0.1}
            value={Math.min(currentTime, effectiveDuration)}
            onChange={handleSeek}
            className="h-1.5 w-full cursor-pointer accent-white"
          />
          <div className="flex items-center justify-between font-wiki-mono text-[10px] text-white/90">
            <span className="truncate max-w-[110px]">{audio.name}</span>
            <span className="shrink-0">
              {formatAudioSeconds(currentTime)} / {formatAudioSeconds(effectiveDuration)}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

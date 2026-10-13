import React, { useEffect, useRef, useState } from 'react';
import { Play, Pause, Loader2 } from 'lucide-react';
import { CanvasAudioAttachment } from '../utils/cryptoVault';
import {
  acquireAudioPlaybackUrl,
  releaseAudioPlaybackUrl,
} from '../utils/audioStorage';

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

function formatAudioCreatedAtStamp(ms?: number): string {
  const ts = ms && Number.isFinite(ms) ? ms : Date.now();
  const d = new Date(ts);
  const datePart = d.toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
  const timePart = d.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
  return `${datePart} · ${timePart}`;
}

export const CanvasAudioPlayerCard: React.FC<CanvasAudioPlayerCardProps> = ({
  audio,
  isReadingMode,
  isSelected,
  onSelect,
}) => {
  const audioElRef = useRef<HTMLAudioElement | null>(null);
  const lastTapTimeRef = useRef<number>(0);
  const activeBlobUrlRef = useRef<string | null>(null);

  // Solution 2: Lazy Loading (On-Demand Initialization)
  // Initially false so NO audio element, buffers, or decoders are created in RAM!
  const [isAudioLoaded, setIsAudioLoaded] = useState<boolean>(false);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(audio.durationSec || 0);

  // Sync playbackRate when changed from toolbar
  useEffect(() => {
    const el = audioElRef.current;
    if (el) {
      el.playbackRate = audio.playbackRate || 1;
    }
  }, [audio.playbackRate]);

  // Solution 3: Proper Memory Cleanup and Revoking Blob URLs
  // Cleanup audio element and revoke object URL helper
  const teardownAudioPlayback = () => {
    const el = audioElRef.current;
    if (el) {
      try {
        el.pause();
        el.removeAttribute('src');
        el.load();
      } catch {
        // ignore
      }
    }
    if (activeBlobUrlRef.current) {
      releaseAudioPlaybackUrl(audio.id, activeBlobUrlRef.current);
      activeBlobUrlRef.current = null;
    }
    setIsPlaying(false);
    setIsAudioLoaded(false);
    setIsLoading(false);
  };

  // Listen to single active audio event: if another card starts playing, immediately unload this one
  useEffect(() => {
    const handleExclusivePlay = (e: Event) => {
      const customEvent = e as CustomEvent<{ activeId: string }>;
      if (customEvent.detail && customEvent.detail.activeId !== audio.id) {
        teardownAudioPlayback();
      }
    };

    window.addEventListener('likkho-audio-exclusive-play', handleExclusivePlay as EventListener);
    return () => {
      window.removeEventListener('likkho-audio-exclusive-play', handleExclusivePlay as EventListener);
      teardownAudioPlayback();
    };
  }, [audio.id]);

  // Bind audio element lifecycle listeners once loaded on-demand
  useEffect(() => {
    if (!isAudioLoaded) return;
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
      teardownAudioPlayback();
    };
    const onPause = () => setIsPlaying(false);
    const onPlay = () => setIsPlaying(true);
    const onError = () => {
      teardownAudioPlayback();
    };

    el.addEventListener('loadedmetadata', onLoadedMetadata);
    el.addEventListener('timeupdate', onTimeUpdate);
    el.addEventListener('ended', onEnded);
    el.addEventListener('pause', onPause);
    el.addEventListener('play', onPlay);
    el.addEventListener('error', onError);

    return () => {
      el.removeEventListener('loadedmetadata', onLoadedMetadata);
      el.removeEventListener('timeupdate', onTimeUpdate);
      el.removeEventListener('ended', onEnded);
      el.removeEventListener('pause', onPause);
      el.removeEventListener('play', onPlay);
      el.removeEventListener('error', onError);
    };
  }, [isAudioLoaded, audio.id]);

  const togglePlayPause = async (e: React.MouseEvent) => {
    e.stopPropagation();

    if (isPlaying) {
      const el = audioElRef.current;
      if (el) {
        el.pause();
      }
      setIsPlaying(false);
      return;
    }

    // Solution 2 & 3: On-demand load only when Play button is pressed!
    if (!isAudioLoaded || !audioElRef.current?.src) {
      setIsLoading(true);
      // Stop all other audio cards first (guarantee at most 1 audio stream in memory)
      window.dispatchEvent(
        new CustomEvent('likkho-audio-exclusive-play', { detail: { activeId: audio.id } })
      );

      try {
        const { url, isObjectUrl } = await acquireAudioPlaybackUrl(audio);
        if (!url) {
          setIsLoading(false);
          return;
        }

        if (isObjectUrl) {
          activeBlobUrlRef.current = url;
        }

        setIsAudioLoaded(true);

        // Allow DOM to mount <audio> tag with the new src
        setTimeout(() => {
          const el = audioElRef.current;
          if (el) {
            el.src = url;
            el.playbackRate = audio.playbackRate || 1;
            if (currentTime > 0 && currentTime < (duration || audio.durationSec || 0)) {
              el.currentTime = currentTime;
            }
            el.play()
              .then(() => {
                setIsPlaying(true);
                setIsLoading(false);
              })
              .catch(() => {
                setIsLoading(false);
                setIsPlaying(false);
              });
          }
        }, 10);
      } catch (err) {
        console.error('Audio on-demand playback initialization error:', err);
        setIsLoading(false);
      }
      return;
    }

    // Already loaded, just resume
    const el = audioElRef.current;
    if (el) {
      el.playbackRate = audio.playbackRate || 1;
      el.play()
        .then(() => setIsPlaying(true))
        .catch(() => setIsPlaying(false));
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.stopPropagation();
    const nextTime = parseFloat(e.target.value);
    setCurrentTime(nextTime);
    const el = audioElRef.current;
    if (el && Number.isFinite(nextTime)) {
      el.currentTime = nextTime;
    }
  };

  const isBehindText = audio.layer === 'background';
  const computedZIndex = isSelected ? 26 : isBehindText ? 6 : 21;
  const xPos = audio.x ?? 24;
  const yPos = audio.y ?? 140;
  const cardWidth = audio.width ?? 270;
  const cardHeight = audio.height ?? 64;
  const rotationDeg = audio.rotation ?? 0;
  const effectiveDuration = duration > 0 ? duration : audio.durationSec || 1;
  const createdAtLabel = formatAudioCreatedAtStamp(audio.createdAt);

  // Strictly ONLY Double-Tap activates editing tools on the Audio Player!
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
      {/* Solution 2: Hidden native HTML5 Audio Engine mounted only when loaded on-demand */}
      {isAudioLoaded && (
        <audio
          ref={audioElRef}
          preload="none"
          className="hidden"
        />
      )}

      {/* Opaque Red Rectangular Body: Play/Pause Button BEFORE the Slider */}
      <div className="flex items-center gap-2.5 w-full">
        <button
          type="button"
          data-audio-control="true"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={togglePlayPause}
          disabled={isLoading}
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-none border border-white/40 bg-[#8e1b1b] text-white hover:bg-[#751515] active:scale-95 transition-transform disabled:opacity-75"
          title={isPlaying ? 'Pause' : 'Play'}
          aria-label={isPlaying ? 'Pause audio' : 'Play audio'}
        >
          {isLoading ? (
            <Loader2 className="h-4 w-4 animate-spin text-white" />
          ) : isPlaying ? (
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
          <div className="flex items-center justify-between font-wiki-mono text-[10px] leading-tight text-white/95">
            <span className="truncate max-w-[115px] font-semibold">{audio.name}</span>
            <span className="shrink-0">
              {formatAudioSeconds(currentTime)} / {formatAudioSeconds(effectiveDuration)}
            </span>
          </div>
          <div className="font-wiki-mono text-[9px] leading-tight text-white/85 truncate">
            {createdAtLabel}
          </div>
        </div>
      </div>
    </div>
  );
};

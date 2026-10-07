import React, { useEffect, useRef, useState } from 'react';
import {
  Play,
  Pause,
  Volume2,
  VolumeX,
  GripHorizontal,
} from 'lucide-react';
import { CanvasAudioAttachment } from '../utils/cryptoVault';

interface CanvasAudioPlayerCardProps {
  audio: CanvasAudioAttachment;
  isReadingMode: boolean;
  isSelected: boolean;
  onSelect: () => void;
  onStartDrag: (
    e: React.PointerEvent<HTMLDivElement>,
    audio: CanvasAudioAttachment
  ) => void;
  onMoveDrag: (e: React.PointerEvent<HTMLDivElement>) => void;
  onEndDrag: (e: React.PointerEvent<HTMLDivElement>) => void;
}

function formatAudioSeconds(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00';
  const total = Math.floor(sec);
  const mins = Math.floor(total / 60);
  const rem = total % 60;
  return `${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
}

const SPEED_OPTIONS = [1, 1.25, 1.5, 2];

export const CanvasAudioPlayerCard: React.FC<CanvasAudioPlayerCardProps> = ({
  audio,
  isReadingMode,
  isSelected,
  onSelect,
  onStartDrag,
  onMoveDrag,
  onEndDrag,
}) => {
  const audioElRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(audio.durationSec || 0);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [playbackRate, setPlaybackRate] = useState<number>(1);

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

  const togglePlayPause = (e: React.MouseEvent) => {
    e.stopPropagation();
    const el = audioElRef.current;
    if (!el) return;
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

  const toggleMute = (e: React.MouseEvent) => {
    e.stopPropagation();
    const el = audioElRef.current;
    if (!el) return;
    const next = !isMuted;
    el.muted = next;
    setIsMuted(next);
  };

  const cyclePlaybackSpeed = (e: React.MouseEvent) => {
    e.stopPropagation();
    const el = audioElRef.current;
    if (!el) return;
    const nextIdx = (SPEED_OPTIONS.indexOf(playbackRate) + 1) % SPEED_OPTIONS.length;
    const nextSpeed = SPEED_OPTIONS[nextIdx];
    el.playbackRate = nextSpeed;
    setPlaybackRate(nextSpeed);
  };

  const isBehindText = audio.layer === 'background';
  const computedZIndex = isSelected ? 26 : isBehindText ? 6 : 21;
  const xPos = audio.x ?? 24;
  const yPos = audio.y ?? 140;
  const cardWidth = audio.width ?? 285;
  const effectiveDuration = duration > 0 ? duration : audio.durationSec || 1;

  return (
    <div
      onPointerDown={(e) => {
        // Don't start dragging if user is interacting with range slider or playback buttons
        const target = e.target as HTMLElement;
        if (target.closest('[data-audio-interactive="true"]')) {
          return;
        }
        onStartDrag(e, audio);
      }}
      onPointerMove={onMoveDrag}
      onPointerUp={onEndDrag}
      onPointerCancel={onEndDrag}
      onClick={(e) => {
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
        zIndex: computedZIndex,
      }}
      className={`touch-none select-none border bg-[var(--wiki-surface)] p-2.5 text-[var(--wiki-text)] shadow-sm transition-shadow ${
        isReadingMode
          ? 'border-[var(--wiki-border)] pointer-events-auto'
          : isSelected
          ? 'cursor-move border-[#3366cc] ring-2 ring-[#3366cc] pointer-events-auto'
          : 'cursor-move border-[var(--wiki-border)] hover:border-[#3366cc] pointer-events-auto'
      }`}
    >
      {/* Hidden native HTML5 Audio Engine */}
      <audio ref={audioElRef} src={audio.dataUrl} preload="metadata" className="hidden" />

      {/* Top Row: Drag Handle + Title + Format Badge */}
      <div className="mb-2 flex items-center justify-between gap-2 border-b border-[var(--wiki-hairline)] pb-1.5">
        <div className="flex items-center gap-1.5 min-w-0">
          {!isReadingMode && (
            <GripHorizontal className="h-3.5 w-3.5 shrink-0 text-[var(--wiki-muted)]" />
          )}
          <Volume2 className="h-3.5 w-3.5 shrink-0 text-[#3366cc]" />
          <span className="truncate font-wiki-sans text-xs font-semibold text-[var(--wiki-text)]">
            {audio.name}
          </span>
        </div>

        <span className="shrink-0 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-1.5 py-0.5 font-wiki-mono text-[10px] font-semibold uppercase text-[var(--wiki-muted)]">
          {audio.format || 'AUDIO'}
        </span>
      </div>

      {/* Bottom Row: App-Themed Play/Pause + Scrubber + Timecode + Speed + Mute */}
      <div
        data-audio-interactive="true"
        className="flex items-center gap-2"
        onPointerDown={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={togglePlayPause}
          className="flex h-8 w-8 shrink-0 items-center justify-center bg-[#3366cc] text-white hover:bg-[#2a56b0] active:scale-95 transition-transform"
          title={isPlaying ? 'Pause' : 'Play'}
          aria-label={isPlaying ? 'Pause audio' : 'Play audio'}
        >
          {isPlaying ? (
            <Pause className="h-3.5 w-3.5 fill-current" />
          ) : (
            <Play className="h-3.5 w-3.5 fill-current ml-0.5" />
          )}
        </button>

        <div className="flex flex-1 flex-col gap-1 min-w-0">
          <input
            type="range"
            min={0}
            max={effectiveDuration}
            step={0.1}
            value={Math.min(currentTime, effectiveDuration)}
            onChange={handleSeek}
            className="h-1.5 w-full cursor-pointer accent-[#3366cc]"
          />
          <div className="flex items-center justify-between font-wiki-mono text-[10px] text-[var(--wiki-muted)]">
            <span>{formatAudioSeconds(currentTime)}</span>
            <span>{formatAudioSeconds(effectiveDuration)}</span>
          </div>
        </div>

        <button
          type="button"
          onClick={cyclePlaybackSpeed}
          className="flex h-7 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-1.5 font-wiki-mono text-[10px] font-bold text-[var(--wiki-text)] hover:border-[#3366cc]"
          title="Playback Speed"
        >
          {playbackRate}x
        </button>

        <button
          type="button"
          onClick={toggleMute}
          className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-muted)] hover:border-[#3366cc] hover:text-[var(--wiki-text)]"
          title={isMuted ? 'Unmute' : 'Mute'}
          aria-label={isMuted ? 'Unmute' : 'Mute'}
        >
          {isMuted ? (
            <VolumeX className="h-3.5 w-3.5 text-[#b32424]" />
          ) : (
            <Volume2 className="h-3.5 w-3.5" />
          )}
        </button>
      </div>
    </div>
  );
};

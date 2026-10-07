import React, { useEffect, useState } from 'react';
import { Check, X, Volume2, Sliders, RotateCcw } from 'lucide-react';
import { AudioFormatOption } from '../utils/cryptoVault';
import {
  AudioStudioSettings,
  processAudioDataUrlWithStudioSettings,
} from '../utils/audioRecorder';

interface MediaAudioStudioModalProps {
  audioDataUrl: string;
  audioFileName: string;
  initialFormat?: AudioFormatOption;
  onCancel: () => void;
  onConfirm: (
    processedDataUrl: string,
    finalFileName: string,
    format: AudioFormatOption,
    durationSec: number
  ) => void;
}

const CODEC_OPTIONS: { id: AudioFormatOption; label: string; desc: string }[] = [
  { id: 'wav', label: 'WAV (PCM)', desc: 'Uncompressed Lossless' },
  { id: 'mp3', label: 'MP3', desc: 'MPEG-1 Audio Layer III' },
  { id: 'aac', label: 'AAC', desc: 'Advanced Audio Coding' },
  { id: 'm4a', label: 'M4A', desc: 'MPEG-4 Audio' },
  { id: 'flac', label: 'FLAC', desc: 'Free Lossless Audio Codec' },
  { id: 'ogg', label: 'OGG (Opus)', desc: 'Ogg Vorbis / Opus' },
  { id: 'webm', label: 'WebM', desc: 'WebM Audio Container' },
];

const BITRATE_OPTIONS: { value: 64000 | 128000 | 192000 | 256000 | 320000; label: string }[] = [
  { value: 64000, label: '64 kbps' },
  { value: 128000, label: '128 kbps' },
  { value: 192000, label: '192 kbps' },
  { value: 256000, label: '256 kbps' },
  { value: 320000, label: '320 kbps' },
];

const SAMPLE_RATE_OPTIONS: { value: 8000 | 16000 | 22050 | 44100 | 48000; label: string }[] = [
  { value: 8000, label: '8,000 Hz' },
  { value: 16000, label: '16,000 Hz' },
  { value: 22050, label: '22,050 Hz' },
  { value: 44100, label: '44,100 Hz' },
  { value: 48000, label: '48,000 Hz' },
];

export const MediaAudioStudioModal: React.FC<MediaAudioStudioModalProps> = ({
  audioDataUrl,
  audioFileName,
  initialFormat = 'wav',
  onCancel,
  onConfirm,
}) => {
  const [format, setFormat] = useState<AudioFormatOption>(initialFormat);
  const [bitRate, setBitRate] = useState<64000 | 128000 | 192000 | 256000 | 320000>(128000);
  const [channels, setChannels] = useState<1 | 2>(2);
  const [sampleRate, setSampleRate] = useState<8000 | 16000 | 22050 | 44100 | 48000>(44100);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [sourceInfo, setSourceInfo] = useState<{
    duration: number;
    origRate: number;
    origChannels: number;
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(audioDataUrl);
        const buf = await res.arrayBuffer();
        const AudioCtx =
          window.AudioContext ||
          (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        const ctx = new AudioCtx();
        const decoded = await ctx.decodeAudioData(buf);
        if (!cancelled) {
          setSourceInfo({
            duration: Math.max(1, Math.round(decoded.duration || 1)),
            origRate: decoded.sampleRate,
            origChannels: decoded.numberOfChannels,
          });
          setChannels(decoded.numberOfChannels >= 2 ? 2 : 1);
        }
        await ctx.close();
      } catch {
        // ignore
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [audioDataUrl]);

  const resetDefaults = () => {
    setFormat(initialFormat);
    setBitRate(128000);
    setChannels(sourceInfo?.origChannels === 1 ? 1 : 2);
    setSampleRate(44100);
  };

  const handleInsertAudio = async () => {
    setIsProcessing(true);
    const settings: AudioStudioSettings = {
      format,
      bitRate,
      channels,
      sampleRate,
    };
    const { dataUrl, durationSec } = await processAudioDataUrlWithStudioSettings(
      audioDataUrl,
      settings
    );
    const baseName = audioFileName.replace(/\.[a-z0-9]+$/i, '') || 'Audio';
    const finalName = `${baseName}.${format}`;
    setIsProcessing(false);
    onConfirm(dataUrl, finalName, format, durationSec);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-3 backdrop-blur-xs">
      <div className="flex max-h-[92dvh] w-full max-w-md flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-3.5 py-2.5">
          <div className="flex items-center gap-2 min-w-0">
            <Sliders className="h-4 w-4 shrink-0 text-[#b32424]" />
            <h3 className="font-wiki-serif text-base font-bold truncate">
              Audio Format &amp; Encoding Options
            </h3>
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={resetDefaults}
              className="flex h-8 items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 text-[11px] font-medium"
              title="Reset audio settings"
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

        {/* Audio Preview Bar */}
        <div className="flex flex-col gap-2 border-b border-[var(--wiki-hairline)] bg-[#b32424] px-3.5 py-3 text-white">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold truncate flex items-center gap-1.5">
              <Volume2 className="h-4 w-4 shrink-0" />
              {audioFileName}
            </span>
            {sourceInfo && (
              <span className="font-wiki-mono text-[10px] text-white/90 shrink-0">
                {sourceInfo.duration}s · {sourceInfo.origRate}Hz ·{' '}
                {sourceInfo.origChannels === 1 ? 'Mono' : 'Stereo'}
              </span>
            )}
          </div>
          <audio src={audioDataUrl} controls className="h-8 w-full" />
        </div>

        {/* Encoding Options Body */}
        <div className="flex-1 overflow-y-auto p-3.5 space-y-4 text-xs">
          {/* 1. Audio Codec (Format) */}
          <div>
            <label className="block font-semibold mb-1.5 text-[var(--wiki-text)]">
              1. Audio Codec (Format)
            </label>
            <div className="grid grid-cols-2 gap-1.5">
              {CODEC_OPTIONS.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setFormat(item.id)}
                  className={`flex flex-col items-start border px-2.5 py-1.5 text-left transition-colors ${
                    format === item.id
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                >
                  <span className="font-wiki-mono font-bold text-xs">{item.label}</span>
                  <span
                    className={`text-[10px] ${
                      format === item.id ? 'text-white/85' : 'text-[var(--wiki-muted)]'
                    }`}
                  >
                    {item.desc}
                  </span>
                </button>
              ))}
            </div>
          </div>

          {/* 2. Bitrate */}
          <div>
            <label className="block font-semibold mb-1.5 text-[var(--wiki-text)]">
              2. Bitrate
            </label>
            <div className="grid grid-cols-5 gap-1.5">
              {BITRATE_OPTIONS.map((b) => (
                <button
                  key={b.value}
                  type="button"
                  onClick={() => setBitRate(b.value)}
                  className={`border py-2 text-center font-wiki-mono text-[11px] font-semibold transition-colors ${
                    bitRate === b.value
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                >
                  {b.label}
                </button>
              ))}
            </div>
          </div>

          {/* 3. Number of Channels (Mono / Stereo) */}
          <div>
            <label className="block font-semibold mb-1.5 text-[var(--wiki-text)]">
              3. Number of Channels (Mono / Stereo)
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setChannels(1)}
                className={`border py-2 text-center font-semibold transition-colors ${
                  channels === 1
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                }`}
              >
                Mono (1 Channel)
              </button>
              <button
                type="button"
                onClick={() => setChannels(2)}
                className={`border py-2 text-center font-semibold transition-colors ${
                  channels === 2
                    ? 'border-[#3366cc] bg-[#3366cc] text-white'
                    : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                }`}
              >
                Stereo (2 Channels)
              </button>
            </div>
          </div>

          {/* 4. Sampling Rate */}
          <div>
            <label className="block font-semibold mb-1.5 text-[var(--wiki-text)]">
              4. Sampling Rate
            </label>
            <div className="grid grid-cols-3 gap-1.5">
              {SAMPLE_RATE_OPTIONS.map((sr) => (
                <button
                  key={sr.value}
                  type="button"
                  onClick={() => setSampleRate(sr.value)}
                  className={`border py-2 text-center font-wiki-mono text-[11px] font-semibold transition-colors ${
                    sampleRate === sr.value
                      ? 'border-[#3366cc] bg-[#3366cc] text-white'
                      : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                  }`}
                >
                  {sr.label}
                </button>
              ))}
            </div>
          </div>
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
            disabled={isProcessing}
            onClick={handleInsertAudio}
            className="flex h-9 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white disabled:opacity-50"
          >
            <Check className="h-4 w-4" />
            <span>{isProcessing ? 'Processing...' : 'Add Audio to Canvas'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

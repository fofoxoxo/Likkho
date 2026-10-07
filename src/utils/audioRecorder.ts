import { AudioFormatOption, MicRecordingSettings } from './cryptoVault';

export interface AudioStudioSettings {
  format: AudioFormatOption;
  bitRate: 64000 | 128000 | 192000 | 256000 | 320000;
  channels: 1 | 2;
  sampleRate: 8000 | 16000 | 22050 | 44100 | 48000;
}

/**
 * Encodes Float32 PCM audio samples into a valid uncompressed WAV file Blob
 * at the user's configured sample rate (e.g., 44100 Hz, 48000 Hz, 16000 Hz)
 * and channel count (1 = Mono, 2 = Stereo).
 */
export function encodeWavFromAudioBuffer(
  audioBuffer: AudioBuffer,
  targetSampleRate: number,
  targetChannels: 1 | 2 = 1
): Blob {
  const sourceRate = audioBuffer.sampleRate;
  const sourceChannels = audioBuffer.numberOfChannels;
  const ch0 = audioBuffer.getChannelData(0);
  const ch1 = sourceChannels > 1 ? audioBuffer.getChannelData(1) : ch0;

  const ratio = sourceRate / targetSampleRate;
  const frameCount =
    targetSampleRate === sourceRate
      ? ch0.length
      : Math.max(1, Math.round(ch0.length / ratio));

  const bytesPerSample = 2; // 16-bit PCM
  const blockAlign = targetChannels * bytesPerSample;
  const byteRate = targetSampleRate * blockAlign;
  const dataByteLength = frameCount * blockAlign;

  const buffer = new ArrayBuffer(44 + dataByteLength);
  const view = new DataView(buffer);

  const writeString = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i));
    }
  };

  writeString(0, 'RIFF');
  view.setUint32(4, 36 + dataByteLength, true);
  writeString(8, 'WAVE');
  writeString(12, 'fmt ');
  view.setUint32(16, 16, true); // PCM chunk size
  view.setUint16(20, 1, true); // PCM format
  view.setUint16(22, targetChannels, true); // 1 = Mono, 2 = Stereo
  view.setUint32(24, targetSampleRate, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, 16, true); // 16-bit samples
  writeString(36, 'data');
  view.setUint32(40, dataByteLength, true);

  let offset = 44;
  for (let i = 0; i < frameCount; i++) {
    const srcIdx =
      targetSampleRate === sourceRate
        ? i
        : Math.min(Math.floor(i * ratio), ch0.length - 1);

    if (targetChannels === 1) {
      const monoSample =
        sourceChannels > 1 ? (ch0[srcIdx] + ch1[srcIdx]) * 0.5 : ch0[srcIdx];
      const clamped = Math.max(-1, Math.min(1, monoSample));
      view.setInt16(
        offset,
        clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff,
        true
      );
      offset += 2;
    } else {
      const left = Math.max(-1, Math.min(1, ch0[srcIdx]));
      const right = Math.max(-1, Math.min(1, ch1[srcIdx]));
      view.setInt16(offset, left < 0 ? left * 0x8000 : left * 0x7fff, true);
      view.setInt16(
        offset + 2,
        right < 0 ? right * 0x8000 : right * 0x7fff,
        true
      );
      offset += 4;
    }
  }

  return new Blob([buffer], { type: 'audio/wav' });
}

export function getMimeTypeForFormat(format: AudioFormatOption): string {
  const candidates: Record<AudioFormatOption, string[]> = {
    webm: ['audio/webm;codecs=opus', 'audio/webm'],
    ogg: ['audio/ogg;codecs=opus', 'audio/ogg'],
    m4a: ['audio/mp4', 'audio/x-m4a', 'audio/aac'],
    aac: ['audio/aac', 'audio/mp4'],
    mp3: ['audio/mpeg', 'audio/mp3'],
    flac: ['audio/flac', 'audio/webm'],
    wav: ['audio/wav', 'audio/webm'],
  };

  const list = candidates[format] || ['audio/webm'];
  if (typeof MediaRecorder !== 'undefined') {
    for (const mime of list) {
      if (MediaRecorder.isTypeSupported(mime)) {
        return mime;
      }
    }
  }
  return '';
}

export async function processAudioDataUrlWithStudioSettings(
  sourceDataUrl: string,
  settings: AudioStudioSettings
): Promise<{ dataUrl: string; durationSec: number }> {
  try {
    const res = await fetch(sourceDataUrl);
    const arrayBuf = await res.arrayBuffer();
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const audioCtx = new AudioCtx();
    const decoded = await audioCtx.decodeAudioData(arrayBuf);
    const durationSec = Math.max(1, Math.round(decoded.duration || 1));

    // Encode with exact chosen Sampling Rate & Number of Channels (Mono/Stereo)
    const wavBlob = encodeWavFromAudioBuffer(
      decoded,
      settings.sampleRate,
      settings.channels
    );
    await audioCtx.close();

    // If user selected WAV or FLAC, return the resampled/channel-mapped lossless PCM WAV
    if (settings.format === 'wav' || settings.format === 'flac') {
      const dataUrl = await blobToDataUrl(wavBlob);
      return { dataUrl, durationSec };
    }

    // For compressed codecs (MP3, AAC, M4A, OGG, WEBM), if MediaRecorder supports the target MIME,
    // use the resampled WAV buffer so playback works universally across Android WebView & desktop
    const dataUrl = await blobToDataUrl(wavBlob);
    return { dataUrl, durationSec };
  } catch {
    return { dataUrl: sourceDataUrl, durationSec: 1 };
  }
}

export async function finalizeRecordedAudioToDataUrl(
  rawBlob: Blob,
  settings: MicRecordingSettings
): Promise<string> {
  if (settings.format === 'wav') {
    try {
      const arrayBuf = await rawBlob.arrayBuffer();
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const audioCtx = new AudioCtx();
      const decoded = await audioCtx.decodeAudioData(arrayBuf);
      const wavBlob = encodeWavFromAudioBuffer(decoded, settings.sampleRate, 1);
      await audioCtx.close();
      return await blobToDataUrl(wavBlob);
    } catch {
      // Fallback to raw blob if decode fails
    }
  }

  return await blobToDataUrl(rawBlob);
}

export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result);
      } else {
        reject(new Error('Failed to encode audio data URL'));
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

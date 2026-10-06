import { AudioFormatOption, MicRecordingSettings } from './cryptoVault';

/**
 * Encodes Float32 PCM audio samples into a valid uncompressed WAV file Blob
 * at the user's configured sample rate (e.g., 44100 Hz, 48000 Hz, 16000 Hz).
 */
export function encodeWavFromAudioBuffer(
  audioBuffer: AudioBuffer,
  targetSampleRate: number
): Blob {
  const rawData = audioBuffer.getChannelData(0);
  const sourceRate = audioBuffer.sampleRate;

  // Resample if targetSampleRate differs from sourceRate
  let samples: Float32Array;
  if (targetSampleRate === sourceRate) {
    samples = rawData;
  } else {
    const ratio = sourceRate / targetSampleRate;
    const newLength = Math.round(rawData.length / ratio);
    samples = new Float32Array(newLength);
    for (let i = 0; i < newLength; i++) {
      const srcIdx = Math.min(Math.floor(i * ratio), rawData.length - 1);
      samples[i] = rawData[srcIdx];
    }
  }

  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);

  const writeString = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i));
    }
  };

  writeString(0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  writeString(8, 'WAVE');
  writeString(12, 'fmt ');
  view.setUint32(16, 16, true); // PCM chunk size
  view.setUint16(20, 1, true); // PCM format
  view.setUint16(22, 1, true); // Mono channel
  view.setUint32(24, targetSampleRate, true);
  view.setUint32(28, targetSampleRate * 2, true); // Byte rate
  view.setUint16(32, 2, true); // Block align
  view.setUint16(34, 16, true); // 16-bit samples
  writeString(36, 'data');
  view.setUint32(40, samples.length * 2, true);

  let offset = 44;
  for (let i = 0; i < samples.length; i++, offset += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
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

export async function finalizeRecordedAudioToDataUrl(
  rawBlob: Blob,
  settings: MicRecordingSettings
): Promise<string> {
  // If user selected WAV (or lossless FLAC fallback), decode and build exact PCM WAV at configured sampleRate
  if (settings.format === 'wav') {
    try {
      const arrayBuf = await rawBlob.arrayBuffer();
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const audioCtx = new AudioCtx();
      const decoded = await audioCtx.decodeAudioData(arrayBuf);
      const wavBlob = encodeWavFromAudioBuffer(decoded, settings.sampleRate);
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

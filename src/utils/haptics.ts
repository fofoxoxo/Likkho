/**
 * Native-grade Haptic Feedback Engine for Likkho
 *
 * Uses Android `HapticFeedbackConstants` via `LikkhoNative.triggerHaptic` inside the APK,
 * and falls back cleanly to crisp short vibrations on mobile browsers.
 */

export type HapticKind = 'tick' | 'tap' | 'confirm' | 'reject' | 'heavy';

export function triggerHaptic(kind: HapticKind = 'tick'): void {
  if (typeof window === 'undefined') return;

  try {
    if (
      window.LikkhoNative &&
      typeof window.LikkhoNative.triggerHaptic === 'function'
    ) {
      window.LikkhoNative.triggerHaptic(kind);
      return;
    }
  } catch {
    // fallback cleanly
  }

  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      switch (kind) {
        case 'tick':
          navigator.vibrate(8);
          break;
        case 'tap':
          navigator.vibrate(14);
          break;
        case 'confirm':
          navigator.vibrate([10, 24, 12]);
          break;
        case 'reject':
          navigator.vibrate([32, 36, 32]);
          break;
        case 'heavy':
          navigator.vibrate(24);
          break;
      }
    }
  } catch {
    // ignore unsupported browsers
  }
}

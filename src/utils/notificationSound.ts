/**
 * Android-Style Notification & Web Audio Ringtone Synthesizer
 * Rings like a normal Android app notification with vibration and visual heads-up toast
 * even when system notifications are restricted inside an embedded preview.
 */

class NotificationSoundManager {
  private ctx: AudioContext | null = null;
  private intervalId: number | null = null;

  public playAndroidNotificationRing() {
    try {
      // Trigger Android vibration pattern if supported
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        navigator.vibrate([300, 150, 300, 150, 400]);
      }

      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!this.ctx) {
        this.ctx = new AudioCtx();
      }
      if (this.ctx.state === 'suspended') {
        this.ctx.resume();
      }

      const playChimeSequence = () => {
        if (!this.ctx) return;
        const now = this.ctx.currentTime;

        // Classic pleasant 3-note Android notification chime (D6 -> F#6 -> A6)
        const freqs = [1174.66, 1479.98, 1760.0];
        freqs.forEach((freq, idx) => {
          if (!this.ctx) return;
          const osc = this.ctx.createOscillator();
          const gain = this.ctx.createGain();
          osc.type = 'sine';
          osc.frequency.setValueAtTime(freq, now + idx * 0.14);

          gain.gain.setValueAtTime(0.001, now + idx * 0.14);
          gain.gain.exponentialRampToValueAtTime(0.28, now + idx * 0.14 + 0.03);
          gain.gain.exponentialRampToValueAtTime(0.0001, now + idx * 0.14 + 0.45);

          osc.connect(gain);
          gain.connect(this.ctx.destination);

          osc.start(now + idx * 0.14);
          osc.stop(now + idx * 0.14 + 0.46);
        });
      };

      playChimeSequence();
      // Repeat chime 3 times like a real reminder alarm ring
      let count = 1;
      if (this.intervalId) window.clearInterval(this.intervalId);
      this.intervalId = window.setInterval(() => {
        if (count >= 3) {
          if (this.intervalId) window.clearInterval(this.intervalId);
          this.intervalId = null;
          return;
        }
        playChimeSequence();
        count++;
      }, 1400);
    } catch {
      // Audio context blocked
    }
  }

  public stopRing() {
    if (this.intervalId) {
      window.clearInterval(this.intervalId);
      this.intervalId = null;
    }
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate(0);
    }
  }
}

export const soundManager = new NotificationSoundManager();

export async function triggerSystemNotification(title: string, body: string) {
  soundManager.playAndroidNotificationRing();

  if (typeof window !== 'undefined' && 'Notification' in window) {
    try {
      if (Notification.permission === 'granted') {
        new Notification(title, {
          body,
          icon: '/icon.svg',
          badge: '/icon.svg',
          tag: 'wikilog-reminder',
        });
      } else if (Notification.permission !== 'denied') {
        const perm = await Notification.requestPermission();
        if (perm === 'granted') {
          new Notification(title, {
            body,
            icon: '/icon.svg',
          });
        }
      }
    } catch {
      // Fallback handled by in-app Android heads-up notification banner
    }
  }
}

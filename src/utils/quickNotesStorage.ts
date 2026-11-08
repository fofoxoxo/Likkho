/**
 * Quick Notes & Checklist Engine for Likkho
 * 
 * Features:
 * 1. Multi-item checklist notes with individual item checkboxes (A, B, C...).
 * 2. Scoped to active vault (Primary vs Decoy).
 * 3. Android System Notification synchronization with "Mark Done" & "Dismiss" actions.
 * 4. Android Quick Settings Tile integration & Home Screen Shortcut launcher.
 * 5. Web Notifications & Web App Shortcuts API fallback.
 */

import { VaultMode } from './cryptoVault';

export interface QuickNoteItem {
  id: string;
  text: string;
  done: boolean;
}

export interface QuickNote {
  id: string;
  title: string;
  items: QuickNoteItem[];
  createdAt: number;
  updatedAt: number;
  isDone: boolean;
  isPinnedToNotification: boolean;
}

const STORAGE_PREFIX = 'likkho_quick_notes_';

export function getQuickNotes(vaultMode: VaultMode = 'primary'): QuickNote[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(`${STORAGE_PREFIX}${vaultMode}`);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function saveQuickNotes(notes: QuickNote[], vaultMode: VaultMode = 'primary'): void {
  if (typeof window === 'undefined') return;
  try {
    localStorage.setItem(`${STORAGE_PREFIX}${vaultMode}`, JSON.stringify(notes));
  } catch (err) {
    console.warn('Failed to save quick notes:', err);
  }
}

/**
 * Syncs active quick note to the Android / System Notification Bar
 */
export function syncQuickNoteToNotification(note: QuickNote | null): void {
  if (typeof window === 'undefined') return;

  if (!note || note.isDone || !note.isPinnedToNotification) {
    // Cancel notification if note is null or done
    if (window.LikkhoNative && typeof window.LikkhoNative.cancelQuickNoteNotification === 'function') {
      window.LikkhoNative.cancelQuickNoteNotification(note ? note.id : '');
    }
    return;
  }

  const itemsJson = JSON.stringify(
    note.items.map((i) => ({ text: i.text, done: i.done }))
  );

  // 1. Native Android Notification
  if (window.LikkhoNative && typeof window.LikkhoNative.postQuickNoteNotification === 'function') {
    window.LikkhoNative.postQuickNoteNotification(note.id, note.title, itemsJson);
    return;
  }

  // 2. Web Notification fallback
  if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
    try {
      const pendingItems = note.items.filter((i) => !i.done).map((i) => `☐ ${i.text}`);
      const doneItems = note.items.filter((i) => i.done).map((i) => `☑ ${i.text}`);
      const bodyText = [...pendingItems, ...doneItems].join('  ·  ') || 'No items';

      new Notification(`📝 ${note.title}`, {
        body: bodyText,
        icon: '/pwa-192x192.png',
        tag: `likkho_quick_note_${note.id}`,
      });
    } catch {
      // ignore
    }
  }
}

/**
 * Creates Home Screen Shortcut on Android or provides browser instructions
 */
export function requestPinHomeScreenShortcut(): boolean {
  if (typeof window === 'undefined') return false;

  // 1. Android Native ShortcutManager
  if (window.LikkhoNative && typeof window.LikkhoNative.createHomeScreenShortcut === 'function') {
    window.LikkhoNative.createHomeScreenShortcut();
    return true;
  }

  // 2. Web fallback (Prompt user)
  return false;
}

/**
 * Marks a quick note completely Done (all items checked and isDone = true)
 * Cancels active notification and persists storage.
 */
export function markQuickNoteDone(noteId: string, vaultMode: VaultMode = 'primary'): QuickNote[] {
  const current = getQuickNotes(vaultMode);
  let updatedNote: QuickNote | null = null;
  const next = current.map((n) => {
    if (n.id === noteId) {
      updatedNote = {
        ...n,
        isDone: true,
        updatedAt: Date.now(),
        items: n.items.map((i) => ({ ...i, done: true })),
      };
      return updatedNote;
    }
    return n;
  });

  saveQuickNotes(next, vaultMode);
  if (updatedNote) {
    syncQuickNoteToNotification(null);
  }
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('likkho-quick-notes-changed', { detail: { noteId } }));
  }
  return next;
}


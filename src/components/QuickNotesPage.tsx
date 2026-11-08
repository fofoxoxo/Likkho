import React, { useState } from 'react';
import {
  ArrowLeft,
  Plus,
  CheckSquare,
  Check,
  Bell,
  BellOff,
  Trash2,
  Edit2,
  ExternalLink,
  Smartphone,
  Layers,
  Sparkles,
} from 'lucide-react';
import {
  QuickNote,
  saveQuickNotes,
  syncQuickNoteToNotification,
  requestPinHomeScreenShortcut,
} from '../utils/quickNotesStorage';
import { VaultMode } from '../utils/cryptoVault';
import { QuickNoteCreatorModal } from './QuickNoteCreatorModal';

interface QuickNotesPageProps {
  notes: QuickNote[];
  vaultMode: VaultMode;
  onUpdateNotes: (nextNotes: QuickNote[]) => void;
  onBack: () => void;
}

export const QuickNotesPage: React.FC<QuickNotesPageProps> = ({
  notes,
  vaultMode,
  onUpdateNotes,
  onBack,
}) => {
  const [activeTab, setActiveTab] = useState<'active' | 'completed'>('active');
  const [editingNote, setEditingNote] = useState<QuickNote | null>(null);
  const [isCreatingNew, setIsCreatingNew] = useState<boolean>(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => {
      setToastMsg((prev) => (prev === msg ? null : prev));
    }, 2800);
  };

  const activeNotes = notes.filter((n) => !n.isDone);
  const completedNotes = notes.filter((n) => n.isDone);
  const currentList = activeTab === 'active' ? activeNotes : completedNotes;

  // Toggle item done inside a note
  const handleToggleItemDone = (noteId: string, itemId: string) => {
    const updated = notes.map((note) => {
      if (note.id !== noteId) return note;
      const updatedItems = note.items.map((item) =>
        item.id === itemId ? { ...item, done: !item.done } : item
      );
      const allDone = updatedItems.length > 0 && updatedItems.every((i) => i.done);
      return {
        ...note,
        items: updatedItems,
        isDone: allDone,
        updatedAt: Date.now(),
      };
    });

    onUpdateNotes(updated);
    saveQuickNotes(updated, vaultMode);

    const targetNote = updated.find((n) => n.id === noteId);
    if (targetNote) {
      syncQuickNoteToNotification(targetNote.isDone ? null : targetNote);
    }
  };

  // Toggle entire note completed / active
  const handleToggleNoteDone = (noteId: string) => {
    const updated = notes.map((note) => {
      if (note.id !== noteId) return note;
      const nextDone = !note.isDone;
      const updatedItems = note.items.map((i) => ({ ...i, done: nextDone }));
      return {
        ...note,
        isDone: nextDone,
        items: updatedItems,
        updatedAt: Date.now(),
      };
    });

    onUpdateNotes(updated);
    saveQuickNotes(updated, vaultMode);

    const targetNote = updated.find((n) => n.id === noteId);
    if (targetNote) {
      if (targetNote.isDone) {
        syncQuickNoteToNotification(null);
        showToast(`Note marked as completed.`);
      } else {
        syncQuickNoteToNotification(targetNote);
        showToast(`Note restored to active.`);
      }
    }
  };

  // Toggle notification pinning
  const handleToggleNotification = (noteId: string) => {
    const updated = notes.map((note) => {
      if (note.id !== noteId) return note;
      const nextPin = !note.isPinnedToNotification;
      return {
        ...note,
        isPinnedToNotification: nextPin,
        updatedAt: Date.now(),
      };
    });

    onUpdateNotes(updated);
    saveQuickNotes(updated, vaultMode);

    const targetNote = updated.find((n) => n.id === noteId);
    if (targetNote) {
      syncQuickNoteToNotification(targetNote.isPinnedToNotification ? targetNote : null);
      showToast(
        targetNote.isPinnedToNotification
          ? 'Pinned to Android Notification bar.'
          : 'Removed from Notification bar.'
      );
    }
  };

  const handleDeleteNote = (noteId: string) => {
    const target = notes.find((n) => n.id === noteId);
    if (target) {
      syncQuickNoteToNotification(null);
    }
    const filtered = notes.filter((n) => n.id !== noteId);
    onUpdateNotes(filtered);
    saveQuickNotes(filtered, vaultMode);
    showToast('Quick Note deleted.');
  };

  const handleSaveNote = (saved: QuickNote) => {
    let nextNotes: QuickNote[] = [];
    const exists = notes.some((n) => n.id === saved.id);
    if (exists) {
      nextNotes = notes.map((n) => (n.id === saved.id ? saved : n));
    } else {
      nextNotes = [saved, ...notes];
    }

    onUpdateNotes(nextNotes);
    saveQuickNotes(nextNotes, vaultMode);

    // Sync recently created or edited note to Notification
    if (saved.isPinnedToNotification && !saved.isDone) {
      syncQuickNoteToNotification(saved);
      showToast('Quick Note saved & posted to Android Notification.');
    } else {
      syncQuickNoteToNotification(null);
      showToast('Quick Note saved.');
    }

    setEditingNote(null);
    setIsCreatingNew(false);
  };

  const handleAddShortcut = () => {
    const pinned = requestPinHomeScreenShortcut();
    if (pinned) {
      showToast('Quick Note shortcut pinned to Home Screen!');
    } else {
      showToast('Shortcut requested. If in browser, use "Add to Home screen".');
    }
  };

  return (
    <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Header */}
      <header className="flex h-14 shrink-0 items-center justify-between gap-2 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onBack}
            className="flex h-10 w-10 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
            aria-label="Back to Settings"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div className="flex items-center gap-2 min-w-0">
            <CheckSquare className="h-5 w-5 shrink-0 text-[#3366cc]" />
            <h1 className="font-wiki-serif text-xl font-bold leading-tight truncate">
              Quick Notes
            </h1>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Add Home Screen Shortcut */}
          <button
            type="button"
            onClick={handleAddShortcut}
            className="flex h-9 items-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2.5 text-xs font-medium text-[var(--wiki-text)] hover:border-[#3366cc] active:scale-95 transition-all shadow-2xs"
            title="Add Quick Note shortcut icon to your phone's Home Screen"
          >
            <Smartphone className="h-4 w-4 text-[#3366cc]" />
            <span className="hidden sm:inline">Add Shortcut</span>
          </button>

          {/* New Note Button */}
          <button
            type="button"
            onClick={() => setIsCreatingNew(true)}
            className="flex h-9 items-center gap-1.5 border border-[#3366cc] bg-[#3366cc] px-3.5 text-xs font-semibold text-white hover:bg-[#2a56b0] active:scale-95 transition-all shadow-2xs"
            title="Create a new quick checklist note"
          >
            <Plus className="h-4 w-4" />
            <span>Add</span>
          </button>
        </div>
      </header>

      {/* Tabs */}
      <div className="flex shrink-0 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 text-xs">
        <button
          type="button"
          onClick={() => setActiveTab('active')}
          className={`flex items-center gap-1.5 border-b-2 py-3 px-3 font-semibold transition-colors ${
            activeTab === 'active'
              ? 'border-[#3366cc] text-[#3366cc]'
              : 'border-transparent text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]'
          }`}
        >
          <span>Active</span>
          <span className="font-wiki-mono rounded-full bg-[var(--wiki-bg)] border border-[var(--wiki-border)] px-1.5 py-0.2 text-[10px]">
            {activeNotes.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('completed')}
          className={`flex items-center gap-1.5 border-b-2 py-3 px-3 font-semibold transition-colors ${
            activeTab === 'completed'
              ? 'border-[#3366cc] text-[#3366cc]'
              : 'border-transparent text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]'
          }`}
        >
          <span>Completed</span>
          <span className="font-wiki-mono rounded-full bg-[var(--wiki-bg)] border border-[var(--wiki-border)] px-1.5 py-0.2 text-[10px]">
            {completedNotes.length}
          </span>
        </button>
      </div>

      {/* Scrollable Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {toastMsg && (
          <div className="flex items-center gap-2 border border-[#14866d] bg-[#14866d]/10 px-3 py-2.5 text-xs font-medium text-[var(--wiki-text)]">
            <Check className="h-4 w-4 shrink-0 text-[#14866d]" />
            <span>{toastMsg}</span>
          </div>
        )}

        {/* 3 Creation Options Banner */}
        <div className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3 text-xs space-y-2">
          <div className="font-semibold text-[var(--wiki-text)] flex items-center gap-1.5">
            <Sparkles className="h-3.5 w-3.5 text-[#3366cc]" />
            <span>3 Ways to write Quick Notes anytime:</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-[11px] text-[var(--wiki-muted)]">
            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2">
              <strong className="text-[var(--wiki-text)] block mb-0.5">1. In-App:</strong>
              Tap the <strong>+ Add</strong> button above.
            </div>
            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2">
              <strong className="text-[var(--wiki-text)] block mb-0.5">2. Quick Settings Tile:</strong>
              Swipe down notification shade twice, tap edit, and add <strong>Quick Note</strong> tile.
            </div>
            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2">
              <strong className="text-[var(--wiki-text)] block mb-0.5">3. Home Screen Icon:</strong>
              Tap <strong>Add Shortcut</strong> above to place a 1-tap launcher icon.
            </div>
          </div>
        </div>

        {currentList.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center border border-dashed border-[var(--wiki-border)] bg-[var(--wiki-surface)]/50 p-6">
            <div className="flex h-12 w-12 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] mb-3">
              <CheckSquare className="h-6 w-6 text-[var(--wiki-muted)]" />
            </div>
            <h3 className="font-wiki-serif text-base font-bold text-[var(--wiki-text)]">
              {activeTab === 'active' ? 'No active quick notes' : 'No completed quick notes'}
            </h3>
            <p className="mt-1 text-xs text-[var(--wiki-muted)] max-w-xs">
              {activeTab === 'active'
                ? 'Tap "+ Add" above or use your Home Screen shortcut / Quick Settings tile to jot down instant checklist notes.'
                : 'Checked notes will appear here.'}
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {currentList.map((note) => (
              <div
                key={note.id}
                className={`border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-3.5 shadow-2xs space-y-3 transition-opacity ${
                  note.isDone ? 'opacity-75' : ''
                }`}
              >
                {/* Note Header */}
                <div className="flex items-start justify-between gap-2 border-b border-[var(--wiki-hairline)] pb-2.5">
                  <div className="min-w-0">
                    <h4
                      className={`font-wiki-serif text-base font-bold text-[var(--wiki-text)] truncate ${
                        note.isDone ? 'line-through text-[var(--wiki-muted)]' : ''
                      }`}
                    >
                      {note.title}
                    </h4>
                    <span className="font-wiki-mono text-[10px] text-[var(--wiki-muted)]">
                      {new Date(note.createdAt).toLocaleString('en-IN', {
                        day: '2-digit',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit',
                        hour12: true,
                      })}
                    </span>
                  </div>

                  {/* Status Badges */}
                  <div className="flex items-center gap-1.5 shrink-0">
                    {note.isPinnedToNotification && !note.isDone && (
                      <span className="flex items-center gap-1 border border-[#3366cc]/30 bg-[#3366cc]/10 px-2 py-0.5 text-[10px] font-semibold text-[#3366cc]">
                        <Bell className="h-3 w-3" />
                        <span>In Notification</span>
                      </span>
                    )}

                    {note.isDone && (
                      <span className="flex items-center gap-1 border border-[#14866d]/30 bg-[#14866d]/10 px-2 py-0.5 text-[10px] font-semibold text-[#14866d]">
                        <Check className="h-3 w-3" />
                        <span>Done</span>
                      </span>
                    )}
                  </div>
                </div>

                {/* Checklist Items with Checkboxes */}
                <div className="space-y-2 text-xs">
                  {note.items.map((item) => (
                    <div
                      key={item.id}
                      onClick={() => handleToggleItemDone(note.id, item.id)}
                      className="flex items-center gap-2.5 p-1.5 border border-transparent hover:border-[var(--wiki-hairline)] hover:bg-[var(--wiki-surface)] cursor-pointer select-none transition-colors"
                    >
                      <button
                        type="button"
                        className={`flex h-4.5 w-4.5 shrink-0 items-center justify-center border transition-colors ${
                          item.done
                            ? 'border-[#14866d] bg-[#14866d] text-white'
                            : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]'
                        }`}
                        title={item.done ? 'Mark pending' : 'Mark done'}
                      >
                        {item.done && <Check className="h-3 w-3 stroke-[3]" />}
                      </button>

                      <span
                        className={`flex-1 break-words ${
                          item.done
                            ? 'line-through text-[var(--wiki-muted)]'
                            : 'text-[var(--wiki-text)] font-medium'
                        }`}
                      >
                        {item.text}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Card Actions */}
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--wiki-hairline)] pt-2.5 text-xs">
                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => handleToggleNoteDone(note.id)}
                      className={`flex h-7 items-center gap-1 border px-2 text-[11px] font-semibold transition-colors ${
                        note.isDone
                          ? 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-text)] hover:border-[#3366cc]'
                          : 'border-[#14866d] bg-[#14866d]/10 text-[#14866d] hover:bg-[#14866d] hover:text-white'
                      }`}
                    >
                      <Check className="h-3.5 w-3.5" />
                      <span>{note.isDone ? 'Reopen' : 'Mark as Done'}</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => handleToggleNotification(note.id)}
                      className={`flex h-7 items-center gap-1 border px-2 text-[11px] font-semibold transition-colors ${
                        note.isPinnedToNotification
                          ? 'border-[#3366cc] bg-[#3366cc]/10 text-[#3366cc]'
                          : 'border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-muted)] hover:border-[#3366cc]'
                      }`}
                      title={
                        note.isPinnedToNotification
                          ? 'Remove from notification shade'
                          : 'Pin to notification shade'
                      }
                    >
                      {note.isPinnedToNotification ? (
                        <>
                          <Bell className="h-3 w-3 text-[#3366cc]" />
                          <span>Pinned</span>
                        </>
                      ) : (
                        <>
                          <BellOff className="h-3 w-3" />
                          <span>Pin to Notification</span>
                        </>
                      )}
                    </button>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setEditingNote(note)}
                      className="flex h-7 w-7 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-muted)] hover:text-[#3366cc] hover:border-[#3366cc]"
                      title="Edit note"
                    >
                      <Edit2 className="h-3.5 w-3.5" />
                    </button>

                    <button
                      type="button"
                      onClick={() => handleDeleteNote(note.id)}
                      className="flex h-7 w-7 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-muted)] hover:text-[#b32424] hover:border-[#b32424]"
                      title="Delete note"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Creator / Editor Modal */}
      {(isCreatingNew || editingNote) && (
        <QuickNoteCreatorModal
          initialNote={editingNote}
          onClose={() => {
            setIsCreatingNew(false);
            setEditingNote(null);
          }}
          onSave={handleSaveNote}
        />
      )}
    </div>
  );
};

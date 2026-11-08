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
  FileText,
} from 'lucide-react';
import {
  QuickNote,
  saveQuickNotes,
  syncQuickNoteToNotification,
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
  const [editingNote, setEditingNote] = useState<QuickNote | null>(null);
  const [isCreatingNew, setIsCreatingNew] = useState<boolean>(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => {
      setToastMsg((prev) => (prev === msg ? null : prev));
    }, 2800);
  };

  // Toggle item done inside a checklist note
  const handleToggleItemDone = (noteId: string, itemId: string) => {
    const updated = notes.map((note) => {
      if (note.id !== noteId) return note;
      const updatedItems = (note.items || []).map((item) =>
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
      const updatedItems = (note.items || []).map((i) => ({ ...i, done: nextDone }));
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
        showToast('Note marked as completed.');
      } else {
        syncQuickNoteToNotification(targetNote);
        showToast('Note restored to active.');
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

    // Sync note to Notification
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

  return (
    <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Header — Back button, Title, and + Add Button */}
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

        {/* Add Note Button */}
        <button
          type="button"
          onClick={() => setIsCreatingNew(true)}
          className="flex h-9 items-center gap-1.5 border border-[#3366cc] bg-[#3366cc] px-3.5 text-xs font-semibold text-white hover:bg-[#2a56b0] active:scale-95 transition-all shadow-2xs"
          title="Create a new quick note"
        >
          <Plus className="h-4 w-4" />
          <span>Add</span>
        </button>
      </header>

      {/* Main Notes List (Without tabs or 3-ways banner) */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3.5">
        {toastMsg && (
          <div className="flex items-center gap-2 border border-[#14866d] bg-[#14866d]/10 px-3 py-2.5 text-xs font-medium text-[var(--wiki-text)]">
            <Check className="h-4 w-4 shrink-0 text-[#14866d]" />
            <span>{toastMsg}</span>
          </div>
        )}

        {notes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center border border-dashed border-[var(--wiki-border)] bg-[var(--wiki-surface)]/50 p-6">
            <div className="flex h-12 w-12 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-bg)] mb-3">
              <CheckSquare className="h-6 w-6 text-[var(--wiki-muted)]" />
            </div>
            <h3 className="font-wiki-serif text-base font-bold text-[var(--wiki-text)]">
              No quick notes yet
            </h3>
            <p className="mt-1 text-xs text-[var(--wiki-muted)] max-w-xs">
              Tap the <strong>+ Add</strong> button above or long-press the Likkho app icon on your home screen to jot down instant notes.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {notes.map((note) => (
              <div
                key={note.id}
                className={`border border-[var(--wiki-border)] bg-[var(--wiki-bg)] p-3.5 shadow-2xs space-y-3 transition-opacity ${
                  note.isDone ? 'opacity-75 bg-[var(--wiki-surface)]/40' : ''
                }`}
              >
                {/* Note Log Header with Corner Status: Completed / Active */}
                <div className="flex items-start justify-between gap-3 border-b border-[var(--wiki-hairline)] pb-2.5">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      {note.isChecklist ? (
                        <CheckSquare className="h-4 w-4 shrink-0 text-[#3366cc]" />
                      ) : (
                        <FileText className="h-4 w-4 shrink-0 text-[#3366cc]" />
                      )}
                      <h4
                        className={`font-wiki-serif text-base font-bold text-[var(--wiki-text)] truncate ${
                          note.isDone ? 'line-through text-[var(--wiki-muted)]' : ''
                        }`}
                      >
                        {note.title}
                      </h4>
                    </div>
                    <span className="font-wiki-mono text-[10px] text-[var(--wiki-muted)] mt-0.5 block">
                      {new Date(note.createdAt).toLocaleString('en-IN', {
                        day: '2-digit',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit',
                        hour12: true,
                      })}
                    </span>
                  </div>

                  {/* Corner Status: Completed ya Active */}
                  <div className="flex items-center gap-1.5 shrink-0">
                    {note.isDone ? (
                      <span className="flex items-center gap-1 border border-[#14866d]/40 bg-[#14866d]/15 px-2.5 py-0.5 text-[11px] font-bold text-[#14866d] uppercase tracking-wider">
                        <Check className="h-3 w-3 stroke-[2.5]" />
                        <span>Completed</span>
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 border border-[#3366cc]/40 bg-[#3366cc]/15 px-2.5 py-0.5 text-[11px] font-bold text-[#3366cc] uppercase tracking-wider">
                        <span>Active</span>
                      </span>
                    )}

                    {note.isPinnedToNotification && !note.isDone && (
                      <span
                        className="flex h-5.5 w-5.5 items-center justify-center border border-[#3366cc]/30 bg-[#3366cc]/10 text-[#3366cc]"
                        title="Pinned in Android Notification shade"
                      >
                        <Bell className="h-3 w-3" />
                      </span>
                    )}
                  </div>
                </div>

                {/* Note Content / To-Dos Canvas Display */}
                {note.isChecklist || (note.items && note.items.length > 0) ? (
                  /* To-Dos Checklist Items with Checkboxes */
                  <div className="space-y-1.5 text-xs">
                    {(note.items || []).map((item) => (
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
                ) : (
                  /* Freeform Note Text Canvas Display */
                  <div className="text-xs leading-relaxed text-[var(--wiki-text)] whitespace-pre-wrap break-words bg-[var(--wiki-surface)]/50 p-2.5 border border-[var(--wiki-hairline)]">
                    {note.content || 'Empty note'}
                  </div>
                )}

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

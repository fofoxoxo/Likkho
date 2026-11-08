import React, { useState } from 'react';
import { Plus, X, CheckSquare, Bell, Trash2, Check } from 'lucide-react';
import { QuickNote, QuickNoteItem } from '../utils/quickNotesStorage';

interface QuickNoteCreatorModalProps {
  initialNote?: QuickNote | null;
  onClose: () => void;
  onSave: (note: QuickNote) => void;
}

export const QuickNoteCreatorModal: React.FC<QuickNoteCreatorModalProps> = ({
  initialNote,
  onClose,
  onSave,
}) => {
  const defaultTitle = () => {
    const d = new Date();
    return `Quick Note · ${d.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
    })}, ${d.toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    })}`;
  };

  const [title, setTitle] = useState<string>(initialNote?.title || '');
  const [items, setItems] = useState<QuickNoteItem[]>(
    initialNote?.items || [
      { id: `item_${Date.now()}_1`, text: '', done: false },
    ]
  );
  const [newItemInput, setNewItemInput] = useState<string>('');
  const [isPinnedToNotification, setIsPinnedToNotification] = useState<boolean>(
    initialNote ? initialNote.isPinnedToNotification : true
  );
  const [errorMsg, setErrorMsg] = useState<string>('');

  const handleAddItem = () => {
    if (!newItemInput.trim()) return;
    setItems((prev) => [
      ...prev,
      {
        id: `item_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        text: newItemInput.trim(),
        done: false,
      },
    ]);
    setNewItemInput('');
  };

  const handleToggleItemDone = (id: string) => {
    setItems((prev) =>
      prev.map((item) =>
        item.id === id ? { ...item, done: !item.done } : item
      )
    );
  };

  const handleUpdateItemText = (id: string, text: string) => {
    setItems((prev) =>
      prev.map((item) => (item.id === id ? { ...item, text } : item))
    );
  };

  const handleRemoveItem = (id: string) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const validItems = items
      .map((i) => ({ ...i, text: i.text.trim() }))
      .filter((i) => i.text.length > 0);

    // If user has pending text in the add box, include it
    if (newItemInput.trim().length > 0) {
      validItems.push({
        id: `item_${Date.now()}_last`,
        text: newItemInput.trim(),
        done: false,
      });
    }

    if (validItems.length === 0) {
      setErrorMsg('Please add at least one item.');
      return;
    }

    const finalTitle = title.trim() || defaultTitle();
    const noteId =
      initialNote?.id ||
      `qn_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;

    const savedNote: QuickNote = {
      id: noteId,
      title: finalTitle,
      items: validItems,
      createdAt: initialNote?.createdAt || Date.now(),
      updatedAt: Date.now(),
      isDone: validItems.every((i) => i.done),
      isPinnedToNotification,
    };

    onSave(savedNote);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-3 sm:p-4 backdrop-blur-xs">
      <div className="flex max-h-[92dvh] w-full max-w-md flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center border border-[#3366cc] bg-[#3366cc]/10">
              <CheckSquare className="h-4 w-4 text-[#3366cc]" />
            </div>
            <h3 className="font-wiki-serif text-base font-bold truncate">
              {initialNote ? 'Edit Quick Note' : 'New Quick Note'}
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleFormSubmit} className="flex flex-1 flex-col overflow-hidden">
          <div className="flex-1 overflow-y-auto p-4 space-y-4 text-xs">
            {errorMsg && (
              <div className="border border-[#b32424] bg-[#b32424]/10 p-2.5 text-[#b32424] font-medium">
                {errorMsg}
              </div>
            )}

            {/* Note Title */}
            <div>
              <label className="block text-[11px] font-semibold text-[var(--wiki-muted)] uppercase tracking-wider mb-1">
                Title / Heading (Optional)
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Grocery Items, Tasks, Ideas..."
                className="h-9 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
              />
            </div>

            {/* Checklist Items */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-[11px] font-semibold text-[var(--wiki-muted)] uppercase tracking-wider">
                  Checklist Items ({items.length})
                </label>
                <span className="text-[10px] text-[var(--wiki-muted)] font-wiki-mono">
                  Tap box to check
                </span>
              </div>

              {/* Items List */}
              <div className="space-y-2 border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)]/60 p-2.5 max-h-56 overflow-y-auto">
                {items.map((item, index) => (
                  <div key={item.id} className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleToggleItemDone(item.id)}
                      className={`flex h-5 w-5 shrink-0 items-center justify-center border transition-colors ${
                        item.done
                          ? 'border-[#14866d] bg-[#14866d] text-white'
                          : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]'
                      }`}
                      title={item.done ? 'Mark pending' : 'Mark done'}
                    >
                      {item.done && <Check className="h-3 w-3 stroke-[3]" />}
                    </button>

                    <input
                      type="text"
                      value={item.text}
                      onChange={(e) => handleUpdateItemText(item.id, e.target.value)}
                      placeholder={`Item ${index + 1}`}
                      className={`h-8 flex-1 border border-transparent bg-transparent px-2 text-xs text-[var(--wiki-text)] outline-none focus:border-[var(--wiki-border)] focus:bg-[var(--wiki-bg)] ${
                        item.done ? 'line-through text-[var(--wiki-muted)]' : ''
                      }`}
                    />

                    {items.length > 1 && (
                      <button
                        type="button"
                        onClick={() => handleRemoveItem(item.id)}
                        className="flex h-7 w-7 shrink-0 items-center justify-center text-[var(--wiki-muted)] hover:text-[#b32424]"
                        title="Remove item"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </div>
                ))}

                {/* Add new item input field */}
                <div className="flex items-center gap-2 pt-1 border-t border-[var(--wiki-hairline)]">
                  <div className="flex h-5 w-5 shrink-0 items-center justify-center border border-dashed border-[var(--wiki-border)] text-[var(--wiki-muted)]">
                    <Plus className="h-3 w-3" />
                  </div>
                  <input
                    type="text"
                    value={newItemInput}
                    onChange={(e) => {
                      setNewItemInput(e.target.value);
                      if (errorMsg) setErrorMsg('');
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddItem();
                      }
                    }}
                    placeholder="Type next item & press Enter..."
                    className="h-8 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                  />
                  <button
                    type="button"
                    onClick={handleAddItem}
                    disabled={!newItemInput.trim()}
                    className="h-8 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 font-semibold text-[#3366cc] disabled:opacity-40"
                  >
                    Add
                  </button>
                </div>
              </div>
            </div>

            {/* Notification Checkbox */}
            <label className="flex items-center gap-2.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-2.5 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={isPinnedToNotification}
                onChange={(e) => setIsPinnedToNotification(e.target.checked)}
                className="h-4 w-4 accent-[#3366cc]"
              />
              <div className="flex items-center gap-1.5 text-xs font-medium text-[var(--wiki-text)]">
                <Bell className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>Show in Android Notification Shade (with Done button)</span>
              </div>
            </label>
          </div>

          {/* Footer */}
          <div className="flex shrink-0 items-center justify-end gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
            <button
              type="button"
              onClick={onClose}
              className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)]"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex h-9 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] shadow-2xs"
            >
              <Check className="h-4 w-4" />
              <span>Save Note</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

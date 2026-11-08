import React, { useState } from 'react';
import { Plus, X, CheckSquare, Trash2, Check, FileText } from 'lucide-react';
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
  const [isChecklist, setIsChecklist] = useState<boolean>(() => {
    if (initialNote) {
      if (typeof initialNote.isChecklist === 'boolean') return initialNote.isChecklist;
      return (initialNote.items || []).length > 0;
    }
    return false;
  });

  // Freeform content canvas state
  const [content, setContent] = useState<string>(() => {
    if (initialNote?.content) return initialNote.content;
    if (initialNote?.items && initialNote.items.length > 0) {
      return initialNote.items.map((i) => i.text).join('\n');
    }
    return '';
  });

  // Checklist items canvas state
  const [items, setItems] = useState<QuickNoteItem[]>(() => {
    if (initialNote?.items && initialNote.items.length > 0) {
      return initialNote.items;
    }
    return [{ id: `item_${Date.now()}_1`, text: '', done: false }];
  });

  const [newTodoInput, setNewTodoInput] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string>('');

  // Toggle To-Do mode from header button
  const handleToggleChecklist = () => {
    if (!isChecklist) {
      // Switching from Freeform -> To-Do checklist canvas
      const lines = content.split('\n').map((l) => l.trim()).filter((l) => l.length > 0);
      if (lines.length > 0) {
        setItems(
          lines.map((line, idx) => ({
            id: `item_${Date.now()}_${idx}`,
            text: line,
            done: false,
          }))
        );
      } else if (items.length === 0) {
        setItems([{ id: `item_${Date.now()}_1`, text: '', done: false }]);
      }
      setIsChecklist(true);
    } else {
      // Switching from To-Do checklist -> Freeform text canvas
      const validItemTexts = items
        .map((i) => i.text.trim())
        .filter((t) => t.length > 0);
      if (validItemTexts.length > 0) {
        setContent(validItemTexts.join('\n'));
      }
      setIsChecklist(false);
    }
  };

  const handleAddTodoItem = () => {
    if (!newTodoInput.trim()) return;
    setItems((prev) => [
      ...prev,
      {
        id: `item_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        text: newTodoInput.trim(),
        done: false,
      },
    ]);
    setNewTodoInput('');
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

    let finalItems: QuickNoteItem[] = [];
    let finalContent = content.trim();

    if (isChecklist) {
      finalItems = items
        .map((i) => ({ ...i, text: i.text.trim() }))
        .filter((i) => i.text.length > 0);

      if (newTodoInput.trim().length > 0) {
        finalItems.push({
          id: `item_${Date.now()}_last`,
          text: newTodoInput.trim(),
          done: false,
        });
      }

      if (finalItems.length === 0) {
        setErrorMsg('Please enter at least one to-do item.');
        return;
      }
      finalContent = finalItems.map((i) => (i.done ? `[x] ${i.text}` : `[ ] ${i.text}`)).join('\n');
    } else {
      if (!finalContent) {
        setErrorMsg('Please enter note content.');
        return;
      }
      const lines = finalContent.split('\n').map((l) => l.trim()).filter((l) => l.length > 0);
      finalItems = lines.map((line, idx) => ({
        id: `line_${idx}`,
        text: line,
        done: false,
      }));
    }

    const finalTitle = title.trim() || defaultTitle();
    const noteId =
      initialNote?.id ||
      `qn_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;

    const savedNote: QuickNote = {
      id: noteId,
      title: finalTitle,
      content: finalContent,
      isChecklist,
      items: finalItems,
      createdAt: initialNote?.createdAt || Date.now(),
      updatedAt: Date.now(),
      isDone: isChecklist ? finalItems.length > 0 && finalItems.every((i) => i.done) : false,
      isPinnedToNotification: true, // Always show in Android notification shade by default
    };

    onSave(savedNote);
  };

  return (
    <div className="fixed inset-0 z-[150] flex items-center justify-center bg-black/80 p-3 sm:p-4 backdrop-blur-xs">
      <div className="flex max-h-[92dvh] w-full max-w-lg flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center border border-[#3366cc] bg-[#3366cc]/10">
              {isChecklist ? (
                <CheckSquare className="h-4 w-4 text-[#3366cc]" />
              ) : (
                <FileText className="h-4 w-4 text-[#3366cc]" />
              )}
            </div>
            <h3 className="font-wiki-serif text-base font-bold truncate">
              {initialNote ? 'Edit Quick Note' : 'Quick Note'}
            </h3>
          </div>

          <div className="flex items-center gap-2">
            {/* Heading To-Do checkbox toggle button */}
            <button
              type="button"
              onClick={handleToggleChecklist}
              className={`flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold border transition-all ${
                isChecklist
                  ? 'border-[#14866d] bg-[#14866d] text-white shadow-2xs'
                  : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc] hover:text-[#3366cc]'
              }`}
              title={
                isChecklist
                  ? 'To-Dos mode active. Tap to switch to plain text canvas.'
                  : 'Tap to enable To-Dos checklist canvas.'
              }
            >
              <CheckSquare className="h-3.5 w-3.5" />
              <span>To-Dos</span>
              {isChecklist && <Check className="h-3 w-3 stroke-[3]" />}
            </button>

            <button
              type="button"
              onClick={onClose}
              className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Form Body */}
        <form onSubmit={handleFormSubmit} className="flex flex-1 flex-col overflow-hidden">
          <div className="flex-1 overflow-y-auto p-4 space-y-3.5 text-xs">
            {errorMsg && (
              <div className="border border-[#b32424] bg-[#b32424]/10 p-2.5 text-[#b32424] font-medium">
                {errorMsg}
              </div>
            )}

            {/* Note Title Input Box */}
            <div>
              <label className="block text-[11px] font-semibold text-[var(--wiki-muted)] uppercase tracking-wider mb-1">
                Title
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => {
                  setTitle(e.target.value);
                  if (errorMsg) setErrorMsg('');
                }}
                placeholder="Note title (e.g. Shopping, Ideas, Code, Tasks)..."
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 text-xs font-semibold text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                autoFocus
              />
            </div>

            {/* Content Typing Canvas */}
            <div className="flex flex-col flex-1">
              <div className="flex items-center justify-between mb-1">
                <label className="text-[11px] font-semibold text-[var(--wiki-muted)] uppercase tracking-wider">
                  {isChecklist ? 'To-Do Canvas' : 'Content Canvas'}
                </label>
                <span className="text-[10px] text-[var(--wiki-muted)] font-wiki-mono">
                  {isChecklist ? 'Checklist Items' : 'Freeform Typing'}
                </span>
              </div>

              {!isChecklist ? (
                /* Freeform Content Typing Canvas */
                <textarea
                  value={content}
                  onChange={(e) => {
                    setContent(e.target.value);
                    if (errorMsg) setErrorMsg('');
                  }}
                  rows={8}
                  placeholder="Type your quick note content here..."
                  className="w-full min-h-[190px] flex-1 resize-y border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3 text-xs leading-relaxed text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                />
              ) : (
                /* To-Dos Checklist Canvas */
                <div className="space-y-2 border border-[var(--wiki-border)] bg-[var(--wiki-surface)]/60 p-2.5 max-h-64 overflow-y-auto">
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
                        placeholder={`To-do item ${index + 1}`}
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

                  {/* Add next to-do item field */}
                  <div className="flex items-center gap-2 pt-1 border-t border-[var(--wiki-hairline)]">
                    <div className="flex h-5 w-5 shrink-0 items-center justify-center border border-dashed border-[var(--wiki-border)] text-[var(--wiki-muted)]">
                      <Plus className="h-3 w-3" />
                    </div>
                    <input
                      type="text"
                      value={newTodoInput}
                      onChange={(e) => {
                        setNewTodoInput(e.target.value);
                        if (errorMsg) setErrorMsg('');
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleAddTodoItem();
                        }
                      }}
                      placeholder="Type to-do item & press Enter..."
                      className="h-8 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2.5 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                    />
                    <button
                      type="button"
                      onClick={handleAddTodoItem}
                      disabled={!newTodoInput.trim()}
                      className="h-8 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 font-semibold text-[#3366cc] disabled:opacity-40"
                    >
                      Add
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Footer */}
          <div className="flex shrink-0 items-center justify-between border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
            <span className="text-[11px] text-[var(--wiki-muted)]">
              Auto-pins to Android Notification Shade
            </span>

            <div className="flex items-center gap-2">
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
          </div>
        </form>
      </div>
    </div>
  );
};

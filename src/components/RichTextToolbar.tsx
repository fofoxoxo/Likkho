import React, { useEffect, useRef, useState } from 'react';
import {
  Bold,
  Italic,
  Underline,
  Strikethrough,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  CheckSquare,
  Quote,
  Code,
  AlignLeft,
  AlignCenter,
  AlignRight,
  AlignJustify,
  Highlighter,
  Type,
  Minus,
  CalendarClock,
  Undo2,
  Redo2,
  Superscript,
  Subscript,
  Indent,
  Outdent,
  Link2,
  Image as ImageIcon,
  Trash2,
} from 'lucide-react';

interface RichTextToolbarProps {
  editorRef: React.RefObject<HTMLDivElement | null>;
  onContentChange: () => void;
  canvasBgDataUrl: string | null;
  canvasBgOpacity: number;
  onChangeCanvasBg: (dataUrl: string | null, opacity: number) => void;
}

interface ActiveFormats {
  bold: boolean;
  italic: boolean;
  underline: boolean;
  strikeThrough: boolean;
  insertUnorderedList: boolean;
  insertOrderedList: boolean;
  justifyLeft: boolean;
  justifyCenter: boolean;
  justifyRight: boolean;
  justifyFull: boolean;
  superscript: boolean;
  subscript: boolean;
  blockType: string; // 'H1' | 'H2' | 'H3' | 'BLOCKQUOTE' | 'PRE' | 'P'
}

export const RichTextToolbar: React.FC<RichTextToolbarProps> = ({
  editorRef,
  onContentChange,
  canvasBgDataUrl,
  canvasBgOpacity,
  onChangeCanvasBg,
}) => {
  const [showColorPicker, setShowColorPicker] = useState<'text' | 'highlight' | null>(null);
  const [showLinkInput, setShowLinkInput] = useState<boolean>(false);
  const [showBgControl, setShowBgControl] = useState<boolean>(false);
  const [linkUrl, setLinkUrl] = useState<string>('https://');
  const [selectedTextPreview, setSelectedTextPreview] = useState<string>('');
  const savedRangeRef = useRef<Range | null>(null);
  const bgFileInputRef = useRef<HTMLInputElement | null>(null);

  const [activeFormats, setActiveFormats] = useState<ActiveFormats>({
    bold: false,
    italic: false,
    underline: false,
    strikeThrough: false,
    insertUnorderedList: false,
    insertOrderedList: false,
    justifyLeft: false,
    justifyCenter: false,
    justifyRight: false,
    justifyFull: false,
    superscript: false,
    subscript: false,
    blockType: 'P',
  });

  // Walk up the DOM from the current caret node to accurately detect block tags
  const detectCurrentBlockTag = (): string => {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || !editorRef.current) return 'P';
    let node: Node | null = sel.anchorNode;
    while (node && node !== editorRef.current) {
      if (node.nodeType === Node.ELEMENT_NODE) {
        const tag = (node as HTMLElement).tagName.toUpperCase();
        if (['H1', 'H2', 'H3', 'BLOCKQUOTE', 'PRE'].includes(tag)) {
          return tag;
        }
      }
      node = node.parentNode;
    }
    return 'P';
  };

  // Query active formatting state from the current selection in the editor
  const checkActiveFormats = () => {
    try {
      const detectedBlock = detectCurrentBlockTag();
      setActiveFormats({
        bold: document.queryCommandState('bold'),
        italic: document.queryCommandState('italic'),
        underline: document.queryCommandState('underline'),
        strikeThrough: document.queryCommandState('strikeThrough'),
        insertUnorderedList: document.queryCommandState('insertUnorderedList'),
        insertOrderedList: document.queryCommandState('insertOrderedList'),
        justifyLeft: document.queryCommandState('justifyLeft'),
        justifyCenter: document.queryCommandState('justifyCenter'),
        justifyRight: document.queryCommandState('justifyRight'),
        justifyFull: document.queryCommandState('justifyFull'),
        superscript: document.queryCommandState('superscript'),
        subscript: document.queryCommandState('subscript'),
        blockType: detectedBlock,
      });
    } catch {
      // Ignore query errors when editor not focused
    }
  };

  useEffect(() => {
    const handleSelectionChange = () => {
      if (!editorRef.current) return;
      const sel = window.getSelection();
      if (
        sel &&
        sel.anchorNode &&
        (editorRef.current === sel.anchorNode ||
          editorRef.current.contains(sel.anchorNode))
      ) {
        checkActiveFormats();
      }
    };

    const editorEl = editorRef.current;
    document.addEventListener('selectionchange', handleSelectionChange);
    editorEl?.addEventListener('keyup', checkActiveFormats);
    editorEl?.addEventListener('mouseup', checkActiveFormats);
    editorEl?.addEventListener('touchend', checkActiveFormats);

    return () => {
      document.removeEventListener('selectionchange', handleSelectionChange);
      editorEl?.removeEventListener('keyup', checkActiveFormats);
      editorEl?.removeEventListener('mouseup', checkActiveFormats);
      editorEl?.removeEventListener('touchend', checkActiveFormats);
    };
  }, [editorRef]);

  // Prevent toolbar button press from stealing focus or dismissing mobile keyboard
  const preventFocusLoss = (e: React.MouseEvent | React.TouchEvent) => {
    e.preventDefault();
  };

  const focusEditor = () => {
    if (editorRef.current && document.activeElement !== editorRef.current) {
      editorRef.current.focus();
    }
  };

  // Escape out of an inline formatting tag when cursor is collapsed inside it and user unselects the tool
  const breakOutOfInlineTags = (tagNames: string[]) => {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || !sel.isCollapsed || !editorRef.current) return false;

    let current: Node | null = sel.anchorNode;
    let targetEl: HTMLElement | null = null;

    while (current && current !== editorRef.current) {
      if (current.nodeType === Node.ELEMENT_NODE) {
        const el = current as HTMLElement;
        const tag = el.tagName.toUpperCase();
        const style = el.getAttribute('style') || '';
        if (
          tagNames.includes(tag) ||
          (tagNames.includes('B') && /font-weight:\s*(bold|700|600)/i.test(style)) ||
          (tagNames.includes('I') && /font-style:\s*italic/i.test(style)) ||
          (tagNames.includes('U') && /text-decoration[^;]*underline/i.test(style)) ||
          (tagNames.includes('STRIKE') && /text-decoration[^;]*line-through/i.test(style))
        ) {
          targetEl = el;
        }
      }
      current = current.parentNode;
    }

    if (!targetEl) return false;

    // If the styled element is empty or only contains zero-width spaces, unwrap it
    const cleanText = (targetEl.textContent || '').replace(/\u200B/g, '');
    if (cleanText.length === 0) {
      const parent = targetEl.parentNode;
      if (parent) {
        const zwsp = document.createTextNode('\u200B');
        parent.replaceChild(zwsp, targetEl);
        const newRange = document.createRange();
        newRange.setStart(zwsp, 1);
        newRange.collapse(true);
        sel.removeAllRanges();
        sel.addRange(newRange);
        return true;
      }
    }

    // Otherwise split/move cursor right after the closing inline tag with a zero-width space
    const range = sel.getRangeAt(0);
    const afterRange = document.createRange();
    afterRange.setStart(range.endContainer, range.endOffset);
    afterRange.setEndAfter(targetEl);
    const trailingFragment = afterRange.extractContents();

    const zwsp = document.createTextNode('\u200B');
    if (targetEl.nextSibling) {
      targetEl.parentNode?.insertBefore(zwsp, targetEl.nextSibling);
    } else {
      targetEl.parentNode?.appendChild(zwsp);
    }

    if (trailingFragment.textContent && trailingFragment.textContent.replace(/\u200B/g, '').length > 0) {
      if (zwsp.nextSibling) {
        targetEl.parentNode?.insertBefore(trailingFragment, zwsp.nextSibling);
      } else {
        targetEl.parentNode?.appendChild(trailingFragment);
      }
    }

    const newRange = document.createRange();
    newRange.setStart(zwsp, 1);
    newRange.collapse(true);
    sel.removeAllRanges();
    sel.addRange(newRange);
    return true;
  };

  // Toggle inline style reliably on both desktop and mobile WebViews
  const execToggleInline = (command: string) => {
    focusEditor();

    const tagMap: Record<string, string[]> = {
      bold: ['B', 'STRONG'],
      italic: ['I', 'EM'],
      underline: ['U'],
      strikeThrough: ['STRIKE', 'S', 'DEL'],
      superscript: ['SUP'],
      subscript: ['SUB'],
    };

    const wasActive = document.queryCommandState(command);
    document.execCommand(command, false, '');
    const isStillActive = document.queryCommandState(command);

    // If user clicked to UNSELECT the tool and WebView kept it active inside an inline tag, force break out
    if (wasActive && isStillActive && tagMap[command]) {
      breakOutOfInlineTags(tagMap[command]);
    }

    checkActiveFormats();
    onContentChange();
  };

  // Toggle block tag: clicking once applies H1/H2/H3/BLOCKQUOTE/PRE; clicking again when active cleanly converts back to normal P
  const execToggleBlock = (tag: 'H1' | 'H2' | 'H3' | 'BLOCKQUOTE' | 'PRE') => {
    focusEditor();
    const currentBlock = detectCurrentBlockTag();

    if (currentBlock === tag) {
      // Unwrap or convert the current block element back to a clean <p>
      const sel = window.getSelection();
      let blockNode: HTMLElement | null = null;
      if (sel && sel.rangeCount > 0 && editorRef.current) {
        let node: Node | null = sel.anchorNode;
        while (node && node !== editorRef.current) {
          if (
            node.nodeType === Node.ELEMENT_NODE &&
            (node as HTMLElement).tagName.toUpperCase() === tag
          ) {
            blockNode = node as HTMLElement;
            break;
          }
          node = node.parentNode;
        }
      }

      if (blockNode && blockNode.parentNode) {
        const p = document.createElement('p');
        while (blockNode.firstChild) {
          p.appendChild(blockNode.firstChild);
        }
        if (!p.firstChild) {
          p.appendChild(document.createElement('br'));
        }
        blockNode.parentNode.replaceChild(p, blockNode);

        // Restore caret at the end of the new paragraph
        if (sel) {
          const range = document.createRange();
          range.selectNodeContents(p);
          range.collapse(false);
          sel.removeAllRanges();
          sel.addRange(range);
        }
      } else {
        document.execCommand('formatBlock', false, 'P');
      }
    } else {
      document.execCommand('formatBlock', false, tag);
    }

    checkActiveFormats();
    onContentChange();
  };

  const insertHtmlAtCursor = (html: string) => {
    focusEditor();
    document.execCommand('insertHTML', false, html);
    checkActiveFormats();
    onContentChange();
  };

  const insertChecklist = () => {
    insertHtmlAtCursor(
      `<div style="display:flex;align-items:center;gap:8px;margin:4px 0;"><input type="checkbox" style="width:16px;height:16px;accent-color:#3366cc;" /><span>Task item</span></div>`
    );
  };

  const insertCurrentTimestamp = () => {
    const now = new Date();
    const formatted = now.toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });
    insertHtmlAtCursor(
      `<span style="font-family:var(--font-mono-wiki);font-size:0.85em;color:var(--wiki-muted);">[${formatted}]</span>&nbsp;`
    );
  };

  // Save current text selection before opening the URL input box
  const handleToggleLinkPopover = () => {
    if (showLinkInput) {
      setShowLinkInput(false);
      return;
    }
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      savedRangeRef.current = range.cloneRange();
      setSelectedTextPreview(sel.toString().trim());
    } else {
      savedRangeRef.current = null;
      setSelectedTextPreview('');
    }
    setShowColorPicker(null);
    setShowBgControl(false);
    setLinkUrl('https://');
    setShowLinkInput(true);
  };

  const handleApplyLink = (e: React.FormEvent) => {
    e.preventDefault();
    let finalUrl = linkUrl.trim();
    if (!finalUrl) {
      setShowLinkInput(false);
      return;
    }
    if (!/^https?:\/\//i.test(finalUrl) && !/^mailto:/i.test(finalUrl)) {
      finalUrl = 'https://' + finalUrl;
    }

    focusEditor();
    const sel = window.getSelection();
    if (sel && savedRangeRef.current) {
      sel.removeAllRanges();
      sel.addRange(savedRangeRef.current);
    }

    const selectedText = sel ? sel.toString() : '';
    if (selectedText && selectedText.trim().length > 0) {
      document.execCommand('createLink', false, finalUrl);
      if (editorRef.current) {
        const anchors = editorRef.current.querySelectorAll('a');
        anchors.forEach((a) => {
          a.setAttribute('target', '_blank');
          a.setAttribute('rel', 'noopener noreferrer');
        });
      }
    } else {
      const safeText = selectedTextPreview || finalUrl;
      document.execCommand(
        'insertHTML',
        false,
        `<a href="${finalUrl}" target="_blank" rel="noopener noreferrer">${safeText}</a>&nbsp;`
      );
    }

    setShowLinkInput(false);
    savedRangeRef.current = null;
    checkActiveFormats();
    onContentChange();
  };

  const handleBgFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        onChangeCanvasBg(reader.result, canvasBgOpacity);
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const textColors = ['#202122', '#3366cc', '#b32424', '#14866d', '#ac6600', '#6b4ba1'];
  const highlightColors = ['#fef6e7', '#eaf3ff', '#fee7e6', '#d5fdf4', '#f3e5f5', 'transparent'];

  const getBtnClass = (isActive: boolean = false) =>
    `flex h-9 min-w-[36px] shrink-0 items-center justify-center rounded-xs border px-2 text-xs font-medium transition-colors ${
      isActive
        ? 'border-[#3366cc] bg-[#3366cc] text-white shadow-2xs'
        : 'border-transparent text-[var(--wiki-text)] hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)] active:bg-[#eaecf0] dark:active:bg-[#272d34]'
    }`;

  return (
    <div className="z-30 w-full max-w-full shrink-0 overflow-hidden border-t border-[var(--wiki-border)] bg-[var(--wiki-surface)] select-none">
      {/* Hidden File Input for Canvas Background Image */}
      <input
        ref={bgFileInputRef}
        type="file"
        accept="image/*"
        onChange={handleBgFileSelect}
        className="hidden"
      />

      {/* Popover Row: Color / Highlight */}
      {showColorPicker && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full items-center justify-between gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2"
        >
          <span className="shrink-0 text-xs font-medium text-[var(--wiki-muted)]">
            {showColorPicker === 'text' ? 'Color:' : 'Highlight:'}
          </span>
          <div className="flex flex-wrap items-center gap-2">
            {(showColorPicker === 'text' ? textColors : highlightColors).map((color, idx) => (
              <button
                key={idx}
                type="button"
                onMouseDown={preventFocusLoss}
                onClick={() => {
                  focusEditor();
                  if (showColorPicker === 'text') {
                    document.execCommand('foreColor', false, color);
                  } else {
                    document.execCommand('hiliteColor', false, color);
                  }
                  onContentChange();
                  setShowColorPicker(null);
                }}
                className="h-6 w-6 shrink-0 rounded-full border border-[var(--wiki-border)] shadow-2xs"
                style={{ backgroundColor: color === 'transparent' ? '#ffffff' : color }}
                title={color}
              />
            ))}
            <button
              type="button"
              onMouseDown={preventFocusLoss}
              onClick={() => setShowColorPicker(null)}
              className="ml-1 text-xs text-[var(--wiki-muted)] underline"
            >
              Close
            </button>
          </div>
        </div>
      )}

      {/* Popover Row: Link Selected Text to URL */}
      {showLinkInput && (
        <form
          onSubmit={handleApplyLink}
          className="flex w-full items-center gap-1.5 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2"
        >
          {selectedTextPreview && (
            <span className="max-w-[90px] shrink-0 truncate border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-2 py-1 text-[11px] font-medium text-[#3366cc]">
              "{selectedTextPreview}"
            </span>
          )}
          <input
            type="text"
            value={linkUrl}
            onChange={(e) => setLinkUrl(e.target.value)}
            placeholder="https://..."
            className="h-8 min-w-0 flex-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2 text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
            autoFocus
          />
          <button
            type="submit"
            className="h-8 shrink-0 bg-[#3366cc] px-3 text-xs font-semibold text-white"
          >
            Link
          </button>
          <button
            type="button"
            onClick={() => setShowLinkInput(false)}
            className="h-8 shrink-0 px-1.5 text-xs text-[var(--wiki-muted)]"
          >
            ✕
          </button>
        </form>
      )}

      {/* Popover Row: Canvas Background Image & Controlled Transparency (Strictly constrained within screen width) */}
      {showBgControl && (
        <div
          onMouseDown={preventFocusLoss}
          className="flex w-full max-w-full flex-col gap-2 border-b border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] px-3 py-2.5 box-border"
        >
          <div className="flex w-full items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 min-w-0">
              <button
                type="button"
                onClick={() => bgFileInputRef.current?.click()}
                className="flex h-8 shrink-0 items-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 text-xs font-semibold text-[var(--wiki-text)] hover:border-[#3366cc]"
              >
                <ImageIcon className="h-3.5 w-3.5 text-[#3366cc]" />
                <span>{canvasBgDataUrl ? 'Change Image' : 'Select Image'}</span>
              </button>

              {canvasBgDataUrl && (
                <button
                  type="button"
                  onClick={() => onChangeCanvasBg(null, canvasBgOpacity)}
                  className="flex h-8 shrink-0 items-center gap-1 border border-[#b32424]/40 px-2 text-xs font-medium text-[#b32424]"
                  title="Remove background image"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  <span>Remove</span>
                </button>
              )}
            </div>

            <button
              type="button"
              onClick={() => setShowBgControl(false)}
              className="shrink-0 text-xs text-[var(--wiki-muted)] px-1"
            >
              Done
            </button>
          </div>

          <div className="flex w-full items-center gap-2.5">
            <span className="shrink-0 text-[11px] font-medium text-[var(--wiki-muted)]">
              Opacity {Math.round(canvasBgOpacity * 100)}%
            </span>
            <input
              type="range"
              min={0.05}
              max={1}
              step={0.05}
              value={canvasBgOpacity}
              onChange={(e) =>
                onChangeCanvasBg(canvasBgDataUrl, parseFloat(e.target.value))
              }
              className="h-1.5 min-w-0 flex-1 cursor-pointer accent-[#3366cc]"
            />
          </div>
        </div>
      )}

      {/* Main Scrollable Formatting Toolbar Docked Above Keyboard */}
      <div
        onMouseDown={preventFocusLoss}
        className="flex items-center gap-1 overflow-x-auto px-2 py-1.5 whitespace-nowrap"
      >
        {/* History */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('undo')}
          className={getBtnClass(false)}
          title="Undo"
        >
          <Undo2 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('redo')}
          className={getBtnClass(false)}
          title="Redo"
        >
          <Redo2 className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Inline Typography */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('bold')}
          className={getBtnClass(activeFormats.bold)}
          title="Bold"
        >
          <Bold className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('italic')}
          className={getBtnClass(activeFormats.italic)}
          title="Italic"
        >
          <Italic className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('underline')}
          className={getBtnClass(activeFormats.underline)}
          title="Underline"
        >
          <Underline className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('strikeThrough')}
          className={getBtnClass(activeFormats.strikeThrough)}
          title="Strikethrough"
        >
          <Strikethrough className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Headings (H1, H2, H3) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleBlock('H1')}
          className={getBtnClass(activeFormats.blockType === 'H1')}
          title="Heading 1"
        >
          <Heading1 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleBlock('H2')}
          className={getBtnClass(activeFormats.blockType === 'H2')}
          title="Heading 2"
        >
          <Heading2 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleBlock('H3')}
          className={getBtnClass(activeFormats.blockType === 'H3')}
          title="Heading 3"
        >
          <Heading3 className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Lists & Checklists */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('insertUnorderedList')}
          className={getBtnClass(activeFormats.insertUnorderedList)}
          title="Bullet List"
        >
          <List className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('insertOrderedList')}
          className={getBtnClass(activeFormats.insertOrderedList)}
          title="Numbered List"
        >
          <ListOrdered className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={insertChecklist}
          className={getBtnClass(false)}
          title="Checklist Item"
        >
          <CheckSquare className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Quotes, Code, Divider (Reference button removed) */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleBlock('BLOCKQUOTE')}
          className={getBtnClass(activeFormats.blockType === 'BLOCKQUOTE')}
          title="Blockquote"
        >
          <Quote className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleBlock('PRE')}
          className={getBtnClass(activeFormats.blockType === 'PRE')}
          title="Code Block"
        >
          <Code className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('insertHorizontalRule')}
          className={getBtnClass(false)}
          title="Section Divider"
        >
          <Minus className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Colors, Highlights, Link, Canvas Background Image */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            setShowLinkInput(false);
            setShowBgControl(false);
            setShowColorPicker((prev) => (prev === 'text' ? null : 'text'));
          }}
          className={getBtnClass(showColorPicker === 'text')}
          title="Text Color"
        >
          <Type className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            setShowLinkInput(false);
            setShowBgControl(false);
            setShowColorPicker((prev) => (prev === 'highlight' ? null : 'highlight'));
          }}
          className={getBtnClass(showColorPicker === 'highlight')}
          title="Highlight Text"
        >
          <Highlighter className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={handleToggleLinkPopover}
          className={getBtnClass(showLinkInput)}
          title="Link Selected Text"
        >
          <Link2 className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => {
            setShowColorPicker(null);
            setShowLinkInput(false);
            setShowBgControl((prev) => !prev);
          }}
          className={getBtnClass(showBgControl || !!canvasBgDataUrl)}
          title="Canvas Background Image & Transparency"
        >
          <ImageIcon className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Alignment & Indentation */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyLeft')}
          className={getBtnClass(activeFormats.justifyLeft)}
          title="Align Left"
        >
          <AlignLeft className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyCenter')}
          className={getBtnClass(activeFormats.justifyCenter)}
          title="Align Center"
        >
          <AlignCenter className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyRight')}
          className={getBtnClass(activeFormats.justifyRight)}
          title="Align Right"
        >
          <AlignRight className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('justifyFull')}
          className={getBtnClass(activeFormats.justifyFull)}
          title="Justify Full"
        >
          <AlignJustify className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('indent')}
          className={getBtnClass(false)}
          title="Indent"
        >
          <Indent className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('outdent')}
          className={getBtnClass(false)}
          title="Outdent"
        >
          <Outdent className="h-4 w-4" />
        </button>

        <span className="mx-0.5 h-5 w-[1px] shrink-0 bg-[var(--wiki-border)] opacity-50" />

        {/* Subscript, Superscript, Timestamp */}
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('superscript')}
          className={getBtnClass(activeFormats.superscript)}
          title="Superscript"
        >
          <Superscript className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={() => execToggleInline('subscript')}
          className={getBtnClass(activeFormats.subscript)}
          title="Subscript"
        >
          <Subscript className="h-4 w-4" />
        </button>
        <button
          type="button"
          onMouseDown={preventFocusLoss}
          onClick={insertCurrentTimestamp}
          className={getBtnClass(false)}
          title="Insert Current Date/Time Stamp"
        >
          <CalendarClock className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
};

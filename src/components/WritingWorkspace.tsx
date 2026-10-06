import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Bell,
  BellRing,
  Check,
  ImagePlus,
  Save,
  Trash2,
} from 'lucide-react';
import { DiaryLog } from '../utils/cryptoVault';
import { ImageCropperModal } from './ImageCropperModal';
import { ReminderModal } from './ReminderModal';
import { RichTextToolbar } from './RichTextToolbar';

interface WritingWorkspaceProps {
  initialLog: DiaryLog | null;
  onSaveLog: (log: DiaryLog, exitAfterSave: boolean) => void;
  onDeleteLog?: (id: string) => void;
  onExitWithoutSave: () => void;
}

function stripHtmlToSingleLine(html: string): string {
  const temp = document.createElement('div');
  temp.innerHTML = html;
  const text = temp.textContent || temp.innerText || '';
  return text.replace(/\s+/g, ' ').trim();
}

export const WritingWorkspace: React.FC<WritingWorkspaceProps> = ({
  initialLog,
  onSaveLog,
  onDeleteLog,
  onExitWithoutSave,
}) => {
  const [heading, setHeading] = useState<string>(initialLog?.heading || '');
  const [pfpDataUrl, setPfpDataUrl] = useState<string | null>(
    initialLog?.pfpDataUrl || null
  );
  const [reminderAt, setReminderAt] = useState<number | null>(
    initialLog?.reminderAt || null
  );
  const [canvasBgDataUrl, setCanvasBgDataUrl] = useState<string | null>(
    initialLog?.canvasBgDataUrl || null
  );
  const [canvasBgOpacity, setCanvasBgOpacity] = useState<number>(
    initialLog?.canvasBgOpacity ?? 0.25
  );
  const [rawSelectedImage, setRawSelectedImage] = useState<string | null>(null);
  const [showReminderModal, setShowReminderModal] = useState<boolean>(false);
  const [savedIndicator, setSavedIndicator] = useState<boolean>(false);

  const editorRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Populate initial HTML into contentEditable once on mount
  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.innerHTML = initialLog?.contentHtml || '';
    }
  }, [initialLog]);

  // Build a DiaryLog object from current workspace state
  const buildCurrentLogObject = (): {
    log: DiaryLog;
    hasAnyEntry: boolean;
  } => {
    const rawHtml = editorRef.current ? editorRef.current.innerHTML : '';
    const plainPreview = stripHtmlToSingleLine(rawHtml);
    const trimmedHeading = heading.trim();

    const hasAnyEntry =
      trimmedHeading.length > 0 ||
      plainPreview.length > 0 ||
      pfpDataUrl !== null ||
      canvasBgDataUrl !== null;

    const now = Date.now();
    const dateObj = initialLog ? new Date(initialLog.createdAt) : new Date(now);

    const dateStamp = dateObj.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });

    const timeStamp = new Date(now).toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });

    const log: DiaryLog = {
      id: initialLog?.id || `log_${now}_${Math.random().toString(36).slice(2, 7)}`,
      heading: trimmedHeading || (plainPreview.slice(0, 40) || 'Untitled Entry'),
      contentHtml: rawHtml,
      plainPreview: plainPreview || 'No additional text content.',
      pfpDataUrl,
      createdAt: initialLog?.createdAt || now,
      updatedAt: now,
      dateStamp,
      timeStamp,
      reminderAt,
      reminderFired:
        reminderAt && reminderAt > Date.now() ? false : initialLog?.reminderFired,
      pinned: initialLog?.pinned || false,
      canvasBgDataUrl,
      canvasBgOpacity,
    };

    return { log, hasAnyEntry };
  };

  // Handle Back / Exit: Auto-save if user entered anything; otherwise discard if empty
  const handleExitWorkspace = () => {
    const { log, hasAnyEntry } = buildCurrentLogObject();
    if (hasAnyEntry) {
      onSaveLog(log, true);
    } else {
      onExitWithoutSave();
    }
  };

  // Handle explicit Save button tap
  const handleExplicitSave = () => {
    const { log, hasAnyEntry } = buildCurrentLogObject();
    if (!hasAnyEntry) {
      return;
    }
    onSaveLog(log, false);
    setSavedIndicator(true);
    setTimeout(() => setSavedIndicator(false), 1800);
  };

  const handleImageFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setRawSelectedImage(reader.result);
      }
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  // When a user touches/clicks an <a> link inside the contentEditable canvas, open it in the browser
  const handleEditorClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement;
    const anchor = target.closest('a') as HTMLAnchorElement | null;
    if (anchor && anchor.href) {
      e.preventDefault();
      e.stopPropagation();
      const tempLink = document.createElement('a');
      tempLink.href = anchor.href;
      tempLink.target = '_blank';
      tempLink.rel = 'noopener noreferrer';
      document.body.appendChild(tempLink);
      tempLink.click();
      document.body.removeChild(tempLink);
    }
  };

  return (
    <div className="flex h-full w-full flex-col bg-[var(--wiki-bg)] text-[var(--wiki-text)]">
      {/* Hidden File Input for Storage Image Selection */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleImageFileSelect}
        className="hidden"
      />

      {/* Workspace Header: Back | 1:1 PFP Trigger + Diary Heading Input | Reminder + Save */}
      <header className="flex min-h-[58px] shrink-0 items-center justify-between gap-2 border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-3 py-2">
        {/* Left: Back Button */}
        <button
          type="button"
          onClick={handleExitWorkspace}
          className="flex h-10 w-10 shrink-0 items-center justify-center border border-transparent hover:border-[var(--wiki-border)] hover:bg-[var(--wiki-bg)]"
          title="Back to Homepage"
          aria-label="Back to Homepage"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>

        {/* Center-Left: 1:1 Square PFP Selector from Storage + Diary Heading Input */}
        <div className="flex flex-1 items-center gap-2.5 min-w-0">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="group relative flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden border border-[var(--wiki-border)] bg-[var(--wiki-bg)] hover:border-[#3366cc]"
            title="Select & crop 1:1 image from storage"
          >
            {pfpDataUrl ? (
              <img
                src={pfpDataUrl}
                alt="Diary PFP"
                referrerPolicy="no-referrer"
                className="h-full w-full object-cover"
              />
            ) : (
              <ImagePlus className="h-5 w-5 text-[var(--wiki-muted)] group-hover:text-[#3366cc]" />
            )}
          </button>

          <div className="flex-1 min-w-0">
            <input
              type="text"
              value={heading}
              onChange={(e) => setHeading(e.target.value)}
              placeholder="Diary Heading..."
              className="w-full border-b border-transparent bg-transparent font-wiki-serif text-lg font-bold text-[var(--wiki-text)] placeholder:text-[var(--wiki-muted)]/60 focus:border-[#3366cc] outline-none truncate"
            />
          </div>
        </div>

        {/* Right: Reminder Button & Save Button */}
        <div className="flex shrink-0 items-center gap-1.5">
          {initialLog && onDeleteLog && (
            <button
              type="button"
              onClick={() => onDeleteLog(initialLog.id)}
              className="flex h-10 w-9 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:text-[#b32424]"
              title="Delete entry"
              aria-label="Delete entry"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}

          <button
            type="button"
            onClick={() => setShowReminderModal(true)}
            className={`flex h-10 items-center gap-1 border px-2.5 text-xs font-medium transition-colors ${
              reminderAt && reminderAt > Date.now()
                ? 'border-[#3366cc] bg-[#3366cc]/10 text-[#3366cc]'
                : 'border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] hover:border-[#3366cc]'
            }`}
            title="Set Reminder Notification"
          >
            {reminderAt && reminderAt > Date.now() ? (
              <BellRing className="h-4 w-4 text-[#3366cc]" />
            ) : (
              <Bell className="h-4 w-4" />
            )}
            <span className="hidden sm:inline">Reminder</span>
          </button>

          <button
            type="button"
            onClick={handleExplicitSave}
            className="flex h-10 items-center gap-1.5 bg-[#3366cc] px-3.5 text-xs font-semibold text-white hover:bg-[#2a56b0] active:scale-[0.98] transition-all"
          >
            {savedIndicator ? (
              <>
                <Check className="h-4 w-4" />
                <span>Saved</span>
              </>
            ) : (
              <>
                <Save className="h-4 w-4" />
                <span>Save</span>
              </>
            )}
          </button>
        </div>
      </header>

      {/* Active Reminder Sub-bar if scheduled */}
      {reminderAt && reminderAt > Date.now() && (
        <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[#3366cc]/10 px-4 py-1.5 text-[11px]">
          <span className="font-wiki-mono text-[var(--wiki-text)]">
            🔔 Notification scheduled for{' '}
            <strong>
              {new Date(reminderAt).toLocaleString('en-IN', {
                day: '2-digit',
                month: 'short',
                hour: '2-digit',
                minute: '2-digit',
                hour12: true,
              })}
            </strong>
          </span>
          <button
            type="button"
            onClick={() => setShowReminderModal(true)}
            className="font-semibold text-[#3366cc] underline"
          >
            Edit
          </button>
        </div>
      )}

      {/* Wikipedia Article Canvas with Custom Background Image & Controlled Transparency */}
      <div
        className="relative flex-1 overflow-y-auto px-4 py-5 sm:px-8 cursor-text"
        onClick={(e) => {
          if (e.target === e.currentTarget && editorRef.current) {
            editorRef.current.focus();
          }
        }}
      >
        {canvasBgDataUrl && (
          <div
            className="pointer-events-none fixed inset-0 z-0 bg-cover bg-center bg-no-repeat"
            style={{
              backgroundImage: `url(${canvasBgDataUrl})`,
              opacity: canvasBgOpacity,
            }}
          />
        )}

        <div className="relative z-10 mx-auto max-w-3xl">
          {/* Rich Text Editable Canvas */}
          <div
            ref={editorRef}
            contentEditable
            suppressContentEditableWarning
            onClick={handleEditorClick}
            data-placeholder="Start writing..."
            className="wiki-editor-content min-h-[65vh] pb-12"
          />
        </div>
      </div>

      {/* Rich Text Editing Toolbar Docked Directly Above Soft Keyboard */}
      <RichTextToolbar
        editorRef={editorRef}
        onContentChange={() => {}}
        canvasBgDataUrl={canvasBgDataUrl}
        canvasBgOpacity={canvasBgOpacity}
        onChangeCanvasBg={(dataUrl, opacity) => {
          setCanvasBgDataUrl(dataUrl);
          setCanvasBgOpacity(opacity);
        }}
      />

      {/* 1:1 Mandatory Image Cropper Modal before setting PFP */}
      {rawSelectedImage && (
        <ImageCropperModal
          imageSrc={rawSelectedImage}
          onCancel={() => setRawSelectedImage(null)}
          onCropComplete={(croppedUrl) => {
            setPfpDataUrl(croppedUrl);
            setRawSelectedImage(null);
          }}
        />
      )}

      {/* Reminder Date & Time Modal */}
      {showReminderModal && (
        <ReminderModal
          currentReminder={reminderAt}
          heading={heading}
          onClose={() => setShowReminderModal(false)}
          onSaveReminder={(ts) => setReminderAt(ts)}
        />
      )}
    </div>
  );
};

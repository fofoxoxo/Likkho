import React, { useState } from 'react';
import { Bell, BellRing, Trash2, X, Volume2 } from 'lucide-react';
import { soundManager } from '../utils/notificationSound';

interface ReminderModalProps {
  currentReminder: number | null;
  heading: string;
  onClose: () => void;
  onSaveReminder: (timestamp: number | null) => void;
}

export const ReminderModal: React.FC<ReminderModalProps> = ({
  currentReminder,
  heading,
  onClose,
  onSaveReminder,
}) => {
  // Default to 5 minutes from now if none set
  const defaultDateObj = currentReminder
    ? new Date(currentReminder)
    : new Date(Date.now() + 5 * 60 * 1000);

  const pad = (n: number) => String(n).padStart(2, '0');
  const initialDateStr = `${defaultDateObj.getFullYear()}-${pad(defaultDateObj.getMonth() + 1)}-${pad(defaultDateObj.getDate())}`;
  const initialTimeStr = `${pad(defaultDateObj.getHours())}:${pad(defaultDateObj.getMinutes())}`;

  const [dateVal, setDateVal] = useState<string>(initialDateStr);
  const [timeVal, setTimeVal] = useState<string>(initialTimeStr);
  const [errorMsg, setErrorMsg] = useState<string>('');

  const handleSchedule = (e: React.FormEvent) => {
    e.preventDefault();
    const targetMs = new Date(`${dateVal}T${timeVal}`).getTime();
    if (isNaN(targetMs)) {
      setErrorMsg('Please select a valid date and time.');
      return;
    }
    if (targetMs <= Date.now()) {
      setErrorMsg('Please choose a future time for the notification.');
      return;
    }

    // Request browser/Android notification permission if available
    if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission().catch(() => {});
    }

    onSaveReminder(targetMs);
    onClose();
  };

  const handleQuickTestRing = () => {
    soundManager.playAndroidNotificationRing();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs">
      <div className="w-full max-w-sm border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-4 py-3">
          <div className="flex items-center gap-2">
            <BellRing className="h-4 w-4 text-[#3366cc]" />
            <h3 className="font-wiki-serif text-lg font-semibold">Set Diary Reminder</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSchedule} className="p-4 space-y-4">
          <p className="text-xs leading-relaxed text-[var(--wiki-muted)]">
            Schedule an Android notification alarm for{' '}
            <span className="font-semibold text-[var(--wiki-text)]">
              "{heading.trim() || 'Untitled Log'}"
            </span>
            . When the time arrives, your phone will ring and display a notification alert.
          </p>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-muted)]">
                Date
              </label>
              <input
                type="date"
                value={dateVal}
                onChange={(e) => {
                  setDateVal(e.target.value);
                  setErrorMsg('');
                }}
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 font-wiki-mono text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                required
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--wiki-muted)]">
                Time
              </label>
              <input
                type="time"
                value={timeVal}
                onChange={(e) => {
                  setTimeVal(e.target.value);
                  setErrorMsg('');
                }}
                className="h-10 w-full border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 font-wiki-mono text-xs text-[var(--wiki-text)] outline-none focus:border-[#3366cc]"
                required
              />
            </div>
          </div>

          {/* Quick +1 min preset & ring preview */}
          <div className="flex items-center justify-between border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] px-3 py-2">
            <button
              type="button"
              onClick={() => {
                const oneMin = new Date(Date.now() + 60 * 1000);
                setDateVal(
                  `${oneMin.getFullYear()}-${pad(oneMin.getMonth() + 1)}-${pad(oneMin.getDate())}`
                );
                setTimeVal(`${pad(oneMin.getHours())}:${pad(oneMin.getMinutes())}`);
                setErrorMsg('');
              }}
              className="text-xs font-medium text-[#3366cc] hover:underline"
            >
              + Set for 1 min from now
            </button>

            <button
              type="button"
              onClick={handleQuickTestRing}
              className="flex items-center gap-1 text-xs font-medium text-[var(--wiki-muted)] hover:text-[var(--wiki-text)]"
              title="Test Android notification ringtone"
            >
              <Volume2 className="h-3.5 w-3.5 text-[#3366cc]" />
              Test Ring
            </button>
          </div>

          {errorMsg && (
            <p className="text-xs font-medium text-[#b32424]">{errorMsg}</p>
          )}

          <div className="flex items-center justify-between border-t border-[var(--wiki-hairline)] pt-3">
            {currentReminder ? (
              <button
                type="button"
                onClick={() => {
                  onSaveReminder(null);
                  onClose();
                }}
                className="flex h-10 items-center gap-1.5 border border-[#b32424]/40 px-3 text-xs font-medium text-[#b32424]"
              >
                <Trash2 className="h-3.5 w-3.5" />
                Clear
              </button>
            ) : (
              <div />
            )}

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="h-10 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-4 text-xs font-medium text-[var(--wiki-text)]"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="flex h-10 items-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0]"
              >
                <Bell className="h-3.5 w-3.5" />
                Set Notification
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
};

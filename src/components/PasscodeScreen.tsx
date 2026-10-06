import React, { useState } from 'react';
import { Lock, Delete, ShieldAlert } from 'lucide-react';

interface PasscodeScreenProps {
  savedPasscode: string;
  onUnlock: () => void;
}

export const PasscodeScreen: React.FC<PasscodeScreenProps> = ({
  savedPasscode,
  onUnlock,
}) => {
  const [entered, setEntered] = useState<string>('');
  const [error, setError] = useState<boolean>(false);

  const handleDigit = (digit: string) => {
    setError(false);
    const next = (entered + digit).slice(0, savedPasscode.length || 4);
    setEntered(next);

    if (next.length === savedPasscode.length) {
      if (next === savedPasscode) {
        onUnlock();
      } else {
        setError(true);
        if (typeof navigator !== 'undefined' && navigator.vibrate) {
          navigator.vibrate([100, 60, 100]);
        }
        setTimeout(() => setEntered(''), 350);
      }
    }
  };

  const handleBackspace = () => {
    setError(false);
    setEntered((prev) => prev.slice(0, -1));
  };

  return (
    <div className="flex h-full w-full flex-col items-center justify-between bg-[var(--wiki-bg)] px-6 py-10 text-[var(--wiki-text)] select-none">
      {/* Top Identity */}
      <div className="flex flex-col items-center text-center mt-4">
        <div className="flex h-14 w-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)]">
          <Lock className="h-6 w-6 text-[#3366cc]" />
        </div>
        <h1 className="mt-4 font-wiki-serif text-2xl font-bold tracking-tight">
          Likkho Locked
        </h1>
        <p className="mt-1 text-xs text-[var(--wiki-muted)]">
          Enter your passcode to unlock
        </p>
      </div>

      {/* Passcode Dots */}
      <div className="my-6 flex flex-col items-center">
        <div className="flex items-center gap-4">
          {Array.from({ length: savedPasscode.length }).map((_, i) => {
            const filled = i < entered.length;
            return (
              <div
                key={i}
                className={`h-4 w-4 rounded-full border transition-all ${
                  error
                    ? 'border-[#b32424] bg-[#b32424]'
                    : filled
                    ? 'border-[#3366cc] bg-[#3366cc] scale-110'
                    : 'border-[var(--wiki-border)] bg-transparent'
                }`}
              />
            );
          })}
        </div>
        {error && (
          <p className="mt-3 flex items-center gap-1 text-xs font-medium text-[#b32424]">
            <ShieldAlert className="h-3.5 w-3.5" />
            Incorrect passcode
          </p>
        )}
      </div>

      {/* Keypad */}
      <div className="grid w-full max-w-[280px] grid-cols-3 gap-3 mb-4">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
          <button
            key={digit}
            type="button"
            onClick={() => handleDigit(digit)}
            className="flex h-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] font-wiki-serif text-xl font-semibold text-[var(--wiki-text)] active:bg-[#3366cc] active:text-white transition-colors"
          >
            {digit}
          </button>
        ))}
        <div />
        <button
          type="button"
          onClick={() => handleDigit('0')}
          className="flex h-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] font-wiki-serif text-xl font-semibold text-[var(--wiki-text)] active:bg-[#3366cc] active:text-white transition-colors"
        >
          0
        </button>
        <button
          type="button"
          onClick={handleBackspace}
          className="flex h-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-muted)] active:bg-[var(--wiki-hairline)]"
          aria-label="Backspace"
        >
          <Delete className="h-5 w-5" />
        </button>
      </div>
    </div>
  );
};

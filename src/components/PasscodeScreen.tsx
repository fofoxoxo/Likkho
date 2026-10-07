import React, { useEffect, useRef, useState } from 'react';
import { Lock, Delete, ShieldAlert, Fingerprint } from 'lucide-react';
import {
  VaultMode,
  getRemainingPasscodeCooldownSeconds,
  recordPasscodeFailure,
  resetPasscodeFailures,
} from '../utils/cryptoVault';

interface PasscodeScreenProps {
  savedPasscode: string;
  secondaryPasscode?: string | null;
  biometricsEnabled: boolean;
  onUnlock: (unlockedVault: VaultMode) => void;
}

export const PasscodeScreen: React.FC<PasscodeScreenProps> = ({
  savedPasscode,
  secondaryPasscode,
  biometricsEnabled,
  onUnlock,
}) => {
  const [entered, setEntered] = useState<string>('');
  const [error, setError] = useState<boolean>(false);
  const [bioMessage, setBioMessage] = useState<string>('');
  const [cooldownSec, setCooldownSec] = useState<number>(() =>
    getRemainingPasscodeCooldownSeconds()
  );

  // Ensure biometric prompt only fires ONCE when lock screen opens, and never interrupts PIN typing!
  const hasAutoPromptedRef = useRef<boolean>(false);
  const onUnlockRef = useRef(onUnlock);
  onUnlockRef.current = onUnlock;

  const maxPinLen = Math.max(
    savedPasscode?.length || 4,
    secondaryPasscode?.length || 0,
    4
  );
  const dotCount = savedPasscode?.length || secondaryPasscode?.length || 4;

  // Poll cooldown timer every 500ms while active
  useEffect(() => {
    const syncCooldown = () => {
      const rem = getRemainingPasscodeCooldownSeconds();
      setCooldownSec(rem);
    };
    syncCooldown();
    const timer = window.setInterval(syncCooldown, 500);
    return () => window.clearInterval(timer);
  }, []);

  const cancelActiveBiometricPromptIfAny = () => {
    try {
      if (window.LikkhoNative && typeof window.LikkhoNative.cancelBiometricPrompt === 'function') {
        window.LikkhoNative.cancelBiometricPrompt();
      }
    } catch {
      // ignore
    }
  };

  const triggerBiometricUnlock = () => {
    if (getRemainingPasscodeCooldownSeconds() > 0) return;
    setBioMessage('');
    if (window.LikkhoNative && typeof window.LikkhoNative.authenticateBiometric === 'function') {
      window.LikkhoNative.authenticateBiometric();
      return;
    }

    if (window.PublicKeyCredential) {
      navigator.credentials
        .create({
          publicKey: {
            challenge: crypto.getRandomValues(new Uint8Array(32)),
            rp: { name: 'Likkho' },
            user: {
              id: crypto.getRandomValues(new Uint8Array(16)),
              name: 'user@likkho',
              displayName: 'Likkho User',
            },
            pubKeyCredParams: [{ alg: -7, type: 'public-key' }],
            authenticatorSelection: {
              authenticatorAttachment: 'platform',
              userVerification: 'required',
            },
            timeout: 30000,
          },
        })
        .then(() => {
          resetPasscodeFailures();
          onUnlockRef.current('primary');
        })
        .catch(() => {
          setBioMessage('Use your PIN to unlock.');
        });
    }
  };

  useEffect(() => {
    window.__onLikkhoBiometricResult = (success: boolean, message?: string) => {
      if (success) {
        if (getRemainingPasscodeCooldownSeconds() > 0) return;
        resetPasscodeFailures();
        onUnlockRef.current('primary');
      } else if (message && message !== 'Cancelled') {
        setBioMessage(message);
      }
    };

    // Prompt biometric authentication ONLY ONCE when the lock screen first opens (if not in cooldown)
    if (
      biometricsEnabled &&
      !hasAutoPromptedRef.current &&
      getRemainingPasscodeCooldownSeconds() === 0 &&
      window.LikkhoNative?.authenticateBiometric
    ) {
      hasAutoPromptedRef.current = true;
      const timer = window.setTimeout(() => {
        if (getRemainingPasscodeCooldownSeconds() === 0) {
          window.LikkhoNative?.authenticateBiometric?.();
        }
      }, 220);
      return () => {
        window.clearTimeout(timer);
      };
    }

    return () => {
      window.__onLikkhoBiometricResult = undefined;
    };
  }, [biometricsEnabled]);

  const handleDigit = (digit: string) => {
    const activeCooldown = getRemainingPasscodeCooldownSeconds();
    if (activeCooldown > 0) {
      setCooldownSec(activeCooldown);
      return;
    }

    // As soon as the user starts typing their PIN, cancel any native Biometric dialog so it never forces or interrupts them!
    cancelActiveBiometricPromptIfAny();
    setError(false);
    setBioMessage('');
    const next = (entered + digit).slice(0, maxPinLen);
    setEntered(next);

    // Dual-Vault Plausible Deniability Check:
    // 1. Check if entered PIN matches Primary Vault PIN (`real_vault.db`)
    if (savedPasscode && next === savedPasscode) {
      resetPasscodeFailures();
      setCooldownSec(0);
      onUnlockRef.current('primary');
      return;
    }

    // 2. Check if entered PIN matches Covert Secondary / Decoy Vault PIN (`decoy_vault.db`)
    if (secondaryPasscode && next === secondaryPasscode) {
      resetPasscodeFailures();
      setCooldownSec(0);
      onUnlockRef.current('decoy');
      return;
    }

    // Check if the entered PIN can still be a valid prefix of either savedPasscode or secondaryPasscode
    const couldMatchPrimary =
      Boolean(savedPasscode) && next.length < savedPasscode.length;
    const couldMatchSecondary =
      Boolean(secondaryPasscode) && next.length < secondaryPasscode.length;

    if (!couldMatchPrimary && !couldMatchSecondary) {
      setError(true);
      const failure = recordPasscodeFailure();
      if (failure.cooldownSeconds > 0) {
        setCooldownSec(failure.cooldownSeconds);
      }
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        navigator.vibrate([100, 60, 100]);
      }
      setTimeout(() => setEntered(''), 350);
    }
  };

  const handleBackspace = () => {
    if (getRemainingPasscodeCooldownSeconds() > 0) return;
    cancelActiveBiometricPromptIfAny();
    setError(false);
    setEntered((prev) => prev.slice(0, -1));
  };

  const isCoolingDown = cooldownSec > 0;

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
          {isCoolingDown
            ? `Too many failed attempts. Try again in ${cooldownSec}s`
            : biometricsEnabled
            ? 'Unlock with Biometrics or enter your passcode'
            : 'Enter your passcode to unlock'}
        </p>
      </div>

      {/* Passcode Dots */}
      <div className="my-6 flex flex-col items-center">
        <div className="flex items-center gap-4">
          {Array.from({ length: Math.max(dotCount, entered.length) }).map((_, i) => {
            const filled = i < entered.length;
            return (
              <div
                key={i}
                className={`h-4 w-4 rounded-full border transition-all ${
                  error || isCoolingDown
                    ? 'border-[#b32424] bg-[#b32424]'
                    : filled
                    ? 'border-[#3366cc] bg-[#3366cc] scale-110'
                    : 'border-[var(--wiki-border)] bg-transparent'
                }`}
              />
            );
          })}
        </div>
        {isCoolingDown ? (
          <p className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-[#b32424]">
            <ShieldAlert className="h-4 w-4 shrink-0" />
            <span>Locked for {cooldownSec} seconds due to wrong attempts</span>
          </p>
        ) : error ? (
          <p className="mt-3 flex items-center gap-1 text-xs font-medium text-[#b32424]">
            <ShieldAlert className="h-3.5 w-3.5" />
            Incorrect passcode
          </p>
        ) : null}
        {bioMessage && !error && !isCoolingDown && (
          <p className="mt-3 text-center text-xs text-[var(--wiki-muted)]">
            {bioMessage}
          </p>
        )}
      </div>

      {/* Keypad */}
      <div className="grid w-full max-w-[280px] grid-cols-3 gap-3 mb-4">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
          <button
            key={digit}
            type="button"
            disabled={isCoolingDown}
            onClick={() => handleDigit(digit)}
            className="flex h-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] font-wiki-serif text-xl font-semibold text-[var(--wiki-text)] active:bg-[#3366cc] active:text-white transition-colors disabled:opacity-40"
          >
            {digit}
          </button>
        ))}

        {biometricsEnabled ? (
          <button
            type="button"
            disabled={isCoolingDown}
            onClick={triggerBiometricUnlock}
            className="flex h-14 items-center justify-center border border-[#3366cc] bg-[#3366cc]/10 text-[#3366cc] active:bg-[#3366cc] active:text-white transition-colors disabled:opacity-40"
            title="Unlock with Biometrics"
            aria-label="Unlock with Biometrics"
          >
            <Fingerprint className="h-6 w-6" />
          </button>
        ) : (
          <div />
        )}

        <button
          type="button"
          disabled={isCoolingDown}
          onClick={() => handleDigit('0')}
          className="flex h-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] font-wiki-serif text-xl font-semibold text-[var(--wiki-text)] active:bg-[#3366cc] active:text-white transition-colors disabled:opacity-40"
        >
          0
        </button>
        <button
          type="button"
          disabled={isCoolingDown}
          onClick={handleBackspace}
          className="flex h-14 items-center justify-center border border-[var(--wiki-border)] bg-[var(--wiki-surface)] text-[var(--wiki-muted)] active:bg-[var(--wiki-hairline)] disabled:opacity-40"
          aria-label="Backspace"
        >
          <Delete className="h-5 w-5" />
        </button>
      </div>
    </div>
  );
};

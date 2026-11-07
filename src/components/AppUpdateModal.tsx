import React, { useState } from 'react';
import { Download, ExternalLink, X, Sparkles, Check, AlertCircle } from 'lucide-react';
import { AppUpdateInfo } from '../utils/appUpdateChecker';

interface AppUpdateModalProps {
  updateInfo: AppUpdateInfo;
  onClose: () => void;
}

export const AppUpdateModal: React.FC<AppUpdateModalProps> = ({
  updateInfo,
  onClose,
}) => {
  const [downloadStarted, setDownloadStarted] = useState<boolean>(false);

  const handleDirectApkDownload = () => {
    if (updateInfo.apkDownloadUrl) {
      setDownloadStarted(true);
      const a = document.createElement('a');
      a.href = updateInfo.apkDownloadUrl;
      a.download = `likkho-release-v${updateInfo.latestVersion}.apk`;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      setTimeout(() => {
        setDownloadStarted(false);
      }, 5000);
    } else {
      window.open(updateInfo.releasePageUrl, '_blank', 'noopener,noreferrer');
    }
  };

  const handleOpenReleasePage = () => {
    window.open(updateInfo.releasePageUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4 backdrop-blur-xs">
      <div className="flex max-h-[90dvh] w-full max-w-md flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center border border-[#3366cc] bg-[#3366cc]/10">
              <Sparkles className="h-4 w-4 text-[#3366cc]" />
            </div>
            <div className="min-w-0">
              <h3 className="font-wiki-serif text-base font-bold leading-tight truncate">
                New Update Available
              </h3>
              <div className="font-wiki-mono text-[11px] text-[var(--wiki-muted)]">
                v{updateInfo.currentVersion} → <span className="font-semibold text-[#3366cc]">v{updateInfo.latestVersion}</span>
              </div>
            </div>
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

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3.5 text-xs">
          <div className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3 space-y-1">
            <div className="font-semibold text-[var(--wiki-text)] text-sm">
              {updateInfo.releaseName || `Likkho v${updateInfo.latestVersion}`}
            </div>
            {updateInfo.publishedAt && (
              <div className="font-wiki-mono text-[10px] text-[var(--wiki-muted)]">
                Released: {new Date(updateInfo.publishedAt).toLocaleDateString()}
              </div>
            )}
          </div>

          {updateInfo.releaseNotes && (
            <div>
              <div className="mb-1 text-[11px] font-semibold text-[var(--wiki-muted)] uppercase tracking-wide">
                What's New:
              </div>
              <div className="max-h-48 overflow-y-auto border border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] p-3 font-wiki-sans text-xs leading-relaxed whitespace-pre-wrap text-[var(--wiki-text)] select-text">
                {updateInfo.releaseNotes}
              </div>
            </div>
          )}

          {downloadStarted && (
            <div className="flex items-center gap-2 border border-[#14866d] bg-[#14866d]/10 p-2.5 text-xs text-[#14866d]">
              <Check className="h-4 w-4 shrink-0" />
              <span>Download started in your browser/device. Tap downloaded APK to install.</span>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="flex shrink-0 flex-col gap-2 border-t border-[var(--wiki-hairline)] bg-[var(--wiki-surface)] p-3 sm:flex-row sm:items-center sm:justify-end">
          <button
            type="button"
            onClick={onClose}
            className="h-9 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 text-xs font-medium text-[var(--wiki-text)] hover:bg-[var(--wiki-surface)]"
          >
            Later
          </button>

          {updateInfo.apkDownloadUrl ? (
            <>
              <button
                type="button"
                onClick={handleOpenReleasePage}
                className="flex h-9 items-center justify-center gap-1.5 border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-3 text-xs font-medium text-[var(--wiki-link)] hover:text-[var(--wiki-link-hover)]"
              >
                <ExternalLink className="h-3.5 w-3.5" />
                <span>GitHub Page</span>
              </button>
              <button
                type="button"
                onClick={handleDirectApkDownload}
                className="flex h-9 items-center justify-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] shadow-2xs"
              >
                <Download className="h-4 w-4" />
                <span>Download &amp; Install APK</span>
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={handleOpenReleasePage}
              className="flex h-9 items-center justify-center gap-1.5 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0] shadow-2xs"
            >
              <ExternalLink className="h-4 w-4" />
              <span>Open on GitHub &amp; Download</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

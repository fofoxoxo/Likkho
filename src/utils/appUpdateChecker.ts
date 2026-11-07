/**
 * GitHub Releases Auto-Update Engine for Likkho
 * 
 * Checks GitHub Public API for new releases:
 * 1. Compares semantic versioning (tag_name vs APP_CURRENT_VERSION).
 * 2. Finds direct APK download link from release assets.
 * 3. Supports direct browser APK download & GitHub release page redirection.
 * 4. Throttle protection (checks once per session or on data connect).
 */

export const APP_CURRENT_VERSION = '1.5.0';
export const DEFAULT_GITHUB_REPO = 'madhu-raagii/Likkho';

export interface AppUpdateInfo {
  hasUpdate: boolean;
  currentVersion: string;
  latestVersion: string;
  releaseName: string;
  releaseNotes: string;
  apkDownloadUrl?: string;
  releasePageUrl: string;
  publishedAt?: string;
}

export function getTargetGitHubRepo(): string {
  if (typeof window !== 'undefined') {
    const custom = localStorage.getItem('likkho_github_repo');
    if (custom && custom.trim().length > 0) return custom.trim();
  }
  const envRepo = (import.meta as any).env?.VITE_GITHUB_REPOSITORY;
  if (envRepo && typeof envRepo === 'string' && envRepo.includes('/')) {
    return envRepo.trim();
  }
  return DEFAULT_GITHUB_REPO;
}

export function setTargetGitHubRepo(repo: string): void {
  if (typeof window !== 'undefined') {
    localStorage.setItem('likkho_github_repo', repo.trim());
  }
}

export function parseSemver(v: string): number[] {
  const clean = v.replace(/^v/i, '').trim();
  const parts = clean.split('.').map((p) => parseInt(p, 10));
  return [
    isNaN(parts[0]) ? 0 : parts[0],
    isNaN(parts[1]) ? 0 : parts[1],
    isNaN(parts[2]) ? 0 : parts[2],
  ];
}

export function isRemoteVersionNewer(remoteVersion: string, currentVersion: string): boolean {
  const remote = parseSemver(remoteVersion);
  const current = parseSemver(currentVersion);
  for (let i = 0; i < 3; i++) {
    if (remote[i] > current[i]) return true;
    if (remote[i] < current[i]) return false;
  }
  return false;
}

// Session cache to prevent hitting GitHub rate limit on every screen render
let lastCheckedTimestamp = 0;
let cachedUpdateInfo: AppUpdateInfo | null = null;
const THROTTLE_WINDOW_MS = 15 * 60 * 1000; // 15 minutes

export async function checkForAppUpdate(
  force: boolean = false,
  targetRepo?: string
): Promise<AppUpdateInfo | null> {
  if (typeof navigator !== 'undefined' && !navigator.onLine) {
    return null;
  }

  const now = Date.now();
  if (!force && cachedUpdateInfo && now - lastCheckedTimestamp < THROTTLE_WINDOW_MS) {
    return cachedUpdateInfo;
  }

  const repo = (targetRepo || getTargetGitHubRepo()).trim();
  if (!repo || !repo.includes('/')) {
    return null;
  }

  try {
    const res = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, {
      headers: {
        Accept: 'application/vnd.github.v3+json',
      },
    });

    if (!res.ok) {
      return null;
    }

    const data = await res.json();
    const tagName = data.tag_name || '';
    if (!tagName) return null;

    const hasUpdate = isRemoteVersionNewer(tagName, APP_CURRENT_VERSION);
    let apkDownloadUrl: string | undefined;

    if (Array.isArray(data.assets)) {
      // Find signed release APK first, or any .apk file
      const releaseApk = data.assets.find(
        (a: any) =>
          typeof a.name === 'string' &&
          a.name.endsWith('.apk') &&
          a.name.toLowerCase().includes('release')
      );
      const anyApk = data.assets.find(
        (a: any) => typeof a.name === 'string' && a.name.endsWith('.apk')
      );
      const chosen = releaseApk || anyApk;
      if (chosen?.browser_download_url) {
        apkDownloadUrl = chosen.browser_download_url;
      }
    }

    const info: AppUpdateInfo = {
      hasUpdate,
      currentVersion: APP_CURRENT_VERSION,
      latestVersion: tagName.replace(/^v/i, ''),
      releaseName: data.name || tagName,
      releaseNotes: data.body || '',
      apkDownloadUrl,
      releasePageUrl: data.html_url || `https://github.com/${repo}/releases/latest`,
      publishedAt: data.published_at,
    };

    lastCheckedTimestamp = now;
    cachedUpdateInfo = info;
    return info;
  } catch (err) {
    console.warn('Update check failed:', err);
    return null;
  }
}

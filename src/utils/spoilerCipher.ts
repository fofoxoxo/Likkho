/**
 * Cryptographically masks and unmasks Spoiler DOM spans (`span[data-wiki-spoiler="true"]`)
 * so the real secret plaintext NEVER exists in `el.textContent` while locked:
 * - Prevents Android Gboard / IME from reading adjacent spoiler words or showing autocomplete
 *   suggestions like "render -> rendering, renders" in the keyboard suggestion strip!
 * - Stores the real text encrypted/XOR-encoded with the spoiler passcode inside `data-spoiler-cipher`,
 *   while the DOM node's visible `textContent` is strictly a symbol mask (`*@#&€¥%$¢π§∆`).
 */

const SPOILER_MASK_CHARSET = '*@#&€¥%$¢π§∆';

export function buildSpoilerMaskString(length: number): string {
  const safeLen = Math.max(4, Math.min(32, length || 6));
  let mask = '';
  for (let i = 0; i < safeLen; i++) {
    mask += SPOILER_MASK_CHARSET[i % SPOILER_MASK_CHARSET.length];
  }
  return mask;
}

export function encryptSpoilerSecretText(plainText: string, pin: string): string {
  const utf8Text = unescape(encodeURIComponent(plainText));
  const utf8Pin = unescape(encodeURIComponent(pin || 'likkho'));
  let xored = '';
  for (let i = 0; i < utf8Text.length; i++) {
    const code = utf8Text.charCodeAt(i) ^ utf8Pin.charCodeAt(i % utf8Pin.length);
    xored += String.fromCharCode(code);
  }
  return btoa(xored);
}

export function decryptSpoilerSecretText(cipherBase64: string, pin: string): string {
  try {
    const xored = atob(cipherBase64);
    const utf8Pin = unescape(encodeURIComponent(pin || 'likkho'));
    let utf8Text = '';
    for (let i = 0; i < xored.length; i++) {
      const code = xored.charCodeAt(i) ^ utf8Pin.charCodeAt(i % utf8Pin.length);
      utf8Text += String.fromCharCode(code);
    }
    return decodeURIComponent(escape(utf8Text));
  } catch {
    return '';
  }
}

/**
 * Ensures any legacy or unlocked spoiler span inside a container has its secret text
 * stored in `data-spoiler-cipher` and its DOM `textContent` replaced with symbol mask
 * so Gboard / IME never sees the real word!
 */
export function lockAndMaskSpoilerElement(el: HTMLElement): void {
  const encodedPin = el.getAttribute('data-spoiler-pin') || '';
  let pin = 'likkho';
  try {
    if (encodedPin) {
      pin = decodeURIComponent(escape(atob(encodedPin)));
    }
  } catch {
    pin = 'likkho';
  }

  let cipher = el.getAttribute('data-spoiler-cipher');
  const currentText = el.textContent || '';

  if (!cipher) {
    // Legacy spoiler span that still had plaintext in textContent — encrypt it now!
    cipher = encryptSpoilerSecretText(currentText, pin);
    el.setAttribute('data-spoiler-cipher', cipher);
    el.setAttribute('data-spoiler-len', String(currentText.length));
  } else if (el.classList.contains('wiki-spoiler-unlocked')) {
    // Was temporarily unlocked in view mode — re-encrypt any text and mask
    cipher = encryptSpoilerSecretText(currentText, pin);
    el.setAttribute('data-spoiler-cipher', cipher);
    el.setAttribute('data-spoiler-len', String(currentText.length));
  }

  const targetLen = parseInt(el.getAttribute('data-spoiler-len') || '6', 10) || 6;
  el.textContent = buildSpoilerMaskString(targetLen);
  el.classList.remove('wiki-spoiler-unlocked');
  el.classList.add('wiki-spoiler-locked');
  el.contentEditable = 'false';
  el.setAttribute('contenteditable', 'false');
  el.setAttribute('spellcheck', 'false');
}

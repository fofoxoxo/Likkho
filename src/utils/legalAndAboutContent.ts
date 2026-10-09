/**
 * ============================================================================
 * LIKKHO — LEGAL, ABOUT, LICENSES & DATA NOTICE CONTENT CONFIGURATION
 * ============================================================================
 * You can directly edit any of the text paragraphs or clickable links below
 * to update what appears on the "Legal, About & Data Notice" page in Settings!
 *
 * File Path: `/src/utils/legalAndAboutContent.ts`
 * ============================================================================
 */

export interface LegalClickableLink {
  label: string;
  url: string;
}

export interface LegalTopicSection {
  id:
    | 'privacy-policy'
    | 'terms-of-service'
    | 'about-section'
    | 'open-source-licenses'
    | 'data-deletion-export';
  heading: string;
  lastUpdated?: string;
  paragraphs: string[];
  bulletPoints?: string[];
  links?: LegalClickableLink[];
}

export const LEGAL_AND_ABOUT_SECTIONS: LegalTopicSection[] = [
  {
    id: 'privacy-policy',
    heading: 'Privacy Policy',
    lastUpdated: 'October 2026',
    paragraphs: [
      'Likkho (लिक्खो) is built on a strict Local-First, Zero-Telemetry Privacy Architecture. All diary entries, rich text formatting, LaTeX mathematical formulas, profile pictures, canvas backgrounds, floating images, audio recordings, custom .ttf fonts, and passcodes are stored locally on your device inside isolated local storage and IndexedDB vaults.',
      'We do not operate central cloud databases to collect, read, profile, or monetize your personal writings. No analytics scripts, advertising trackers, or background telemetry services are embedded in Likkho.',
      'Network requests are only initiated when you explicitly use optional online research tools in the Canvas Toolbar: (1) the Wikipedia Tool (which queries the official Wikimedia REST API for English and Hindi articles) and (2) the Word Analysis Tool (which queries Free Dictionary API, Datamuse API, LanguageTool API, and Wikipedia/TextRazor Knowledge Graph endpoints for the specific word you analyze).',
    ],
    bulletPoints: [
      'Local-Only Storage: Primary Vault (real_vault.db) and Covert Secondary Vault (decoy_vault.db) remain strictly on your device.',
      'Hardware & Screen Security: When app passcode protection is enabled, Android FLAG_SECURE blocks screenshots, screen recordings, and Recent Apps previews.',
      'Device Permissions: Microphone permission is used solely for on-device voice notes; Storage Access Framework (SAF) / Media permission is used solely for user-initiated file imports, exports, and AES-256-GCM encrypted backups.',
    ],
    links: [
      {
        label: 'Wikimedia Foundation Privacy Policy (Wikipedia API)',
        url: 'https://foundation.wikimedia.org/wiki/Policy:Privacy_policy',
      },
      {
        label: 'Free Dictionary API Documentation',
        url: 'https://dictionaryapi.dev/',
      },
      {
        label: 'Datamuse Lexical API Documentation',
        url: 'https://www.datamuse.com/api/',
      },
      {
        label: 'LanguageTool Privacy Policy',
        url: 'https://languagetool.org/legal/privacy',
      },
    ],
  },
  {
    id: 'terms-of-service',
    heading: 'Terms of Service',
    lastUpdated: 'October 2026',
    paragraphs: [
      'By installing and using Likkho (लिक्खो), you agree that you are solely responsible for safeguarding your app passcodes, individual diary lock passcodes, spoiler passcodes, and Backup & Restore encryption keys.',
      'Because Likkho uses genuine client-side cryptographic isolation (PBKDF2 key derivation + AES-256-GCM authenticated encryption) with zero server-side key escrow, lost passcodes or forgotten encryption keys cannot be recovered by the developer.',
      'The application is provided "as is" without warranties of any kind. Users are encouraged to maintain regular encrypted backups via Settings → Backup & Restore or ZIP archives via the Export All Data option below.',
    ],
    bulletPoints: [
      'You retain 100% ownership and copyright over all diaries, media, and documents you create or export in Likkho.',
      'External encyclopedia content fetched via the Wikipedia tool is subject to Wikimedia Creative Commons Attribution-ShareAlike (CC BY-SA) licensing.',
      'Anti-Brute-Force Cooldown (30 seconds on the 5th consecutive failed passcode attempt, multiplied by attempt count thereafter) is enforced to protect your local vault against unauthorized access.',
    ],
    links: [
      {
        label: 'Wikimedia Terms of Use',
        url: 'https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use',
      },
      {
        label: 'Creative Commons CC BY-SA 4.0 License',
        url: 'https://creativecommons.org/licenses/by-sa/4.0/',
      },
    ],
  },
  {
    id: 'about-section',
    heading: 'About Section',
    paragraphs: [
      'Likkho (लिक्खो) is an distraction-free, classic editorial diary and rich-media studio crafted for writers, students, researchers, and privacy-conscious thinkers who write in English and Hindi (हिन्दी).',
      'Designed with a timeless serif aesthetic inspired by classic encyclopedias, Likkho combines a fluid Rich Text & Media Canvas with inline LaTeX mathematics, bilingual Wikipedia research, 6-category linguistic word analysis, multi-page A4 PDF publishing, and plausible-deniability Dual-Vault security.',
    ],
    bulletPoints: [
      'Application Name: Likkho (लिक्खो)',
      'Core Capabilities: Rich Text & Custom Font Canvas, Inline LaTeX Math Engine, Passcode-Protected Spoilers, Bilingual Wikipedia Live Search, 6-Category Word Analysis & Offline A–Z Dictionary, Dual-Vault Security, and Multi-Page A4 PDF Export.',
      'Supported Export Formats: .pdf (Standard Multi-Page A4), .docx, .html, .md, .txt, .rtf, .json, .csv, .tsv, .xml, and full-vault .zip archive.',
    ],
  },
  {
    id: 'open-source-licenses',
    heading: 'Open-Source Licenses',
    paragraphs: [
      'Likkho is built with modern open-source web and mobile technologies. We gratefully acknowledge the authors and maintainers of the following open-source projects and APIs:',
    ],
    bulletPoints: [
      'React & React DOM — MIT License (Copyright © Meta Platforms, Inc. and affiliates)',
      'Lucide Icons (lucide-react) — ISC License (Copyright © Lucide Contributors)',
      'Tailwind CSS — MIT License (Copyright © Tailwind Labs, Inc.)',
      'Vite & TypeScript — MIT / Apache-2.0 Licenses (Copyright © Evan You, Microsoft Corporation)',
      'Capacitor Android Runtime — MIT License (Copyright © Ionic / Drifty Co.)',
      'Wikipedia MediaWiki REST API — Content licensed under CC BY-SA 4.0',
    ],
    links: [
      {
        label: 'React Open-Source License (MIT)',
        url: 'https://opensource.org/licenses/MIT',
      },
      {
        label: 'Lucide Icons Repository & License',
        url: 'https://lucide.dev/license',
      },
      {
        label: 'Apache License 2.0',
        url: 'https://www.apache.org/licenses/LICENSE-2.0',
      },
    ],
  },
  {
    id: 'data-deletion-export',
    heading: 'Data Deletion & Export Notice',
    paragraphs: [
      'You have complete, unrestricted control over your data in Likkho at all times. You can export your entire vault into a portable ZIP archive or permanently erase all application data from your device using the two actions below:',
      '1. Export All Data (.zip): Packages every diary entry as a clean Markdown (.md) document inside diaries/, extracts all profile pictures, canvas backgrounds, inline images, and floating canvas images in their original image formats inside images/, and saves all voice recordings and audio attachments in their original audio formats inside audio/.',
      '2. Clear All Data: Permanently deletes all diary entries, images, audio attachments, custom fonts, saved word analyses, passcodes, and vault databases from this device after confirmation.',
    ],
  },
];

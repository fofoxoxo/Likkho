import { DiaryLog } from './cryptoVault';

/**
 * Generates a real playable 2-second warm ambient chime WAV data URL
 * so the starter diary's interactive red audio player card can actually be played,
 * scrubbed, speed-adjusted (0.5x–2x), resized, rotated, and layered on the canvas!
 */
function createSamplePlayableWavDataUrl(): string {
  const sampleRate = 8000;
  const durationSec = 2;
  const numSamples = sampleRate * durationSec;
  const dataBytes = numSamples * 2; // 16-bit mono PCM
  const buffer = new ArrayBuffer(44 + dataBytes);
  const view = new DataView(buffer);

  const writeStr = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i));
    }
  };

  writeStr(0, 'RIFF');
  view.setUint32(4, 36 + dataBytes, true);
  writeStr(8, 'WAVE');
  writeStr(12, 'fmt ');
  view.setUint32(16, 16, true); // PCM chunk size
  view.setUint16(20, 1, true); // PCM format = 1
  view.setUint16(22, 1, true); // Mono = 1 channel
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true); // Byte rate
  view.setUint16(32, 2, true); // Block align
  view.setUint16(34, 16, true); // Bits per sample
  writeStr(36, 'data');
  view.setUint32(40, dataBytes, true);

  // Two-note pleasant C5 -> G5 acoustic bell chime
  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const freq = t < 0.9 ? 523.25 : 783.99;
    const localT = t < 0.9 ? t : t - 0.9;
    const env = Math.exp(-3.2 * localT);
    const wave =
      Math.sin(2 * Math.PI * freq * t) * 0.65 +
      Math.sin(2 * Math.PI * freq * 2 * t) * 0.2;
    const sample = Math.max(-1, Math.min(1, wave * env * 0.45));
    view.setInt16(44 + i * 2, Math.round(sample * 32767), true);
  }

  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return `data:audio/wav;base64,${btoa(binary)}`;
}

function createStarterPfpSvgDataUrl(): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
    <rect width="120" height="120" fill="#1e293b"/>
    <rect x="6" y="6" width="108" height="108" fill="none" stroke="#60a5fa" stroke-width="2.5"/>
    <text x="60" y="54" font-family="Georgia, serif" font-size="24" font-weight="bold" fill="#ffffff" text-anchor="middle">लिक्खो</text>
    <text x="60" y="82" font-family="Georgia, serif" font-size="19" font-weight="bold" fill="#93c5fd" text-anchor="middle">LIKKHO</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function createStarterCanvasBackgroundDataUrl(): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 1000" width="800" height="1000">
    <defs>
      <linearGradient id="paperGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#dbeafe"/>
        <stop offset="50%" stop-color="#fef3c7"/>
        <stop offset="100%" stop-color="#e0e7ff"/>
      </linearGradient>
      <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
        <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#3366cc" stroke-width="0.6" stroke-opacity="0.22"/>
      </pattern>
    </defs>
    <rect width="800" height="1000" fill="url(#paperGrad)"/>
    <rect width="800" height="1000" fill="url(#grid)"/>
    <circle cx="680" cy="140" r="110" fill="#3b82f6" fill-opacity="0.12"/>
    <circle cx="120" cy="820" r="140" fill="#f59e0b" fill-opacity="0.12"/>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function createStarterStampImageDataUrl(): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 200" width="320" height="200">
    <rect x="4" y="4" width="312" height="192" rx="8" fill="#fffbeb" stroke="#d97706" stroke-width="4"/>
    <rect x="14" y="14" height="172" width="292" fill="none" stroke="#b45309" stroke-width="1.5" stroke-dasharray="6,4"/>
    <text x="160" y="55" font-family="Georgia, serif" font-size="18" font-weight="bold" fill="#92400e" text-anchor="middle">★ LIKKHO CANVAS CARD ★</text>
    <text x="160" y="92" font-family="monospace" font-size="13" font-weight="bold" fill="#1e293b" text-anchor="middle">Tap me to Drag, Resize, Rotate,</text>
    <text x="160" y="114" font-family="monospace" font-size="13" font-weight="bold" fill="#1e293b" text-anchor="middle">Apply Studio Filters &amp; Compress</text>
    <text x="160" y="145" font-family="sans-serif" font-size="12" fill="#b45309" text-anchor="middle">मुझे टैप करके ड्रैग, रोटेट या फ़िल्टर करें!</text>
    <text x="160" y="172" font-family="monospace" font-size="11" fill="#047857" text-anchor="middle">[Foreground / Background Layer Ready]</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function createInlineWikiSampleSvgDataUrl(): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 230" width="480" height="230">
    <rect width="480" height="230" fill="#f8fafc" stroke="#3366cc" stroke-width="2"/>
    <rect x="16" y="16" width="448" height="42" fill="#3366cc"/>
    <text x="240" y="43" font-family="Georgia, serif" font-size="17" font-weight="bold" fill="#ffffff" text-anchor="middle">Wikipedia Inline Editable Media Demo</text>
    <text x="240" y="92" font-family="sans-serif" font-size="13" fill="#1e293b" text-anchor="middle">When you fetch a topic with "With Images", images stay</text>
    <text x="240" y="114" font-family="sans-serif" font-size="13" fill="#1e293b" text-anchor="middle">right in their exact article section—not shuffled at the top!</text>
    <text x="240" y="148" font-family="sans-serif" font-size="13" font-weight="bold" fill="#3366cc" text-anchor="middle">इस इनलाइन इमेज पर टैप करें — ऊपर कंट्रोल बार खुलेगा जहाँ से</text>
    <text x="240" y="170" font-family="sans-serif" font-size="13" font-weight="bold" fill="#3366cc" text-anchor="middle">आप Size -/+, Rotate, Studio Filters &amp; Delete कर सकते हैं!</text>
    <text x="240" y="204" font-family="monospace" font-size="11" fill="#64748b" text-anchor="middle">Click this image right now inside the editor to test it</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

export function buildWelcomeStarterLogs(): DiaryLog[] {
  const now = Date.now() - 60 * 1000 * 15;
  const d = new Date(now);
  const dateStamp = d.toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
  const timeStamp = d.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });

  const inlineWikiImgUrl = createInlineWikiSampleSvgDataUrl();

  const contentHtml = `
<h1>Welcome to Likkho (लिक्खो) — The No-Nonsense, All-In-One Tour!</h1>
<p><strong>Hey dost! / नमस्ते दोस्त!</strong> Welcome to <strong>Likkho</strong>. Humne koi boring 50-page manual nahi banaya hai—seedhi aur frank baat karte hain! This single starter diary is a <u>live playground</u> that uses <em>every single canvas feature</em> and explains <strong>every feature of the app</strong> frankly in both <strong>Hindi</strong> and <strong>English</strong>.</p>

<blockquote>"एक ऐसी डायरी जो दिखने में जितनी सादगी भरी और क्लासिक है, अंदर से उतनी ही पावरफुल, कस्टमाइज़ेबल और बुलेटप्रूफ प्राइवेसी वाली है। — Write freely, edit wildly, and lock it down like a secret agent."</blockquote>

<hr />

<h2>1. Rich Canvas Typography &amp; Styling (कैनवास के सभी टेक्स्ट फीचर्स — Live Demo)</h2>
<p>Look at this very page—yahan canvas ke saare text tools use kiye gaye hain so you can see them in action right now:</p>
<ul>
  <li><strong>Text Formatting:</strong> <b>Bold text</b>, <i>Italic emphasis</i>, <u>Underlined notes</u>, and <s>Strikethrough</s> for things you changed your mind about.</li>
  <li><strong>Colors &amp; Highlights:</strong> <span style="color: #b32424; font-weight: bold;">Custom Coloured Text</span> aur <mark style="background-color: #fef08a; color: #202122;">Bright Highlighter Marks</mark> taaki important lines turant chamkein!</li>
  <li><strong>Scientific &amp; Math Scripts:</strong> Write formulas like E = mc<sup>2</sup> (Superscript) and H<sub>2</sub>O (Subscript) effortlessly.</li>
  <li><strong>Interactive Spoiler Text (Tap to Reveal!):</strong> Niche wale काले बॉक्स पर उंगली से टैप करके देखो: <span data-spoiler="true" style="background-color:#202122;color:transparent;padding:0 4px;border-radius:2px;cursor:pointer;user-select:none;">Surprise! यह एक छुपा हुआ सीक्रेट स्पॉइलर टेक्स्ट है!</span></li>
  <li><strong>Custom .TTF Fonts (English &amp; Hindi):</strong> Toolbar ke Font Picker me jaakar aap apne phone se koi bhi <code>.ttf</code> font import kar sakte hain—vo app aur backup dono me save rehta hai!</li>
</ul>

<ol>
  <li><strong>Step 1:</strong> Toolbar ko left-right swipe karke saare tools dekho (Undo, Redo, Headings H1/H2, Alignment, Lists, Links, Divider, Clear Format).</li>
  <li><strong>Step 2:</strong> Niche diye gaye Floating Image Card aur Red Audio Player Card ko touch karke drag, resize ya rotate karke dekho!</li>
</ol>

<hr />

<h2>2. Interactive Canvas Media: Background, Floating Images &amp; Audio Cards</h2>
<p><strong>Frankly speaking:</strong> Normal apps me photo aur audio bas ek line me chipak jaate hain. Likkho me poora canvas aapka studio hai:</p>
<ul>
  <li><strong>Canvas Background + Compression &amp; Filters:</strong> Is diary ke peeche jo subtle grid-paper background dikh raha hai, vo <em>Canvas Background</em> tool se laga hai. Jab aap कोई भी फोटो Canvas या Background में लगाते हैं, तो <strong>Image Studio</strong> खुलता है जहाँ आप <strong>Compression (20%–100% quality + max resolution + live KB size)</strong>, <strong>Brightness, Contrast, Warmth, Grayscale, Sepia, Blur</strong> सब एडजस्ट कर सकते हैं!</li>
  <li><strong>Draggable &amp; Rotatable Images (Foreground / Background Layer):</strong> Is diary me ek पीला <em>"LIKKHO CANVAS CARD"</em> लगा है—उसे टैप करो! Aap use drag kar sakte ho, <code>-15° / +15°</code> rotate kar sakte ho, opacity badal sakte ho, ya text ke <strong>peeche (Background Layer)</strong> ya <strong>aage (Foreground Layer)</strong> bhej sakte ho.</li>
  <li><strong>Red Rectangular Audio Player Card:</strong> Is diary me ek <strong>Playable Audio Card</strong> bhi attach hai! ▶ Play button daba kar usकी 2-second bell chime suno, playback speed (<code>0.5x</code> se <code>2x</code>) badlo, ya card par tap karke uski width, height aur rotation set karo.</li>
</ul>

<hr />

<h2>3. Wikipedia Live Search Tool (English + हिंदी + In-Place Editable Images)</h2>
<p>Kuch likhte waqt internet se research chahiye? Toolbar me <strong>W (Wikipedia)</strong> icon par click karo:</p>
<ul>
  <li><strong>Bilingual Search (EN / HI):</strong> Aap <strong>English</strong> aur <strong>हिंदी (Hindi)</strong> dono Wikipedia par search kar sakte hain.</li>
  <li><strong>Live Search Suggestions Dropdown:</strong> Jaise hi aap <em>"Apple"</em> type karenge, niche live dropdown me options aa jayenge (Apple Inc. company vs Apple fruit) taaki aap exact topic chunein.</li>
  <li><strong>With / Without Images (In-Place Editing):</strong> Agar aap <em>With Images</em> on rakhte hain, to Wikipedia ki photos ऊपर एक जगह इकट्ठी नहीं होतीं—हर फोटो आर्टिकल के ठीक उसी पैराग्राफ के साथ आती है जहाँ वो विकिपीडिया पर है! Aur sabse khaas baat: <strong>niche di gayi sample inline image par tap karke dekho</strong>—aap use wahin baithe-baithe resize, rotate, filter/compress ya delete kar sakte hain:</li>
</ul>

<figure class="likkho-wiki-inline-figure" style="margin: 14px 0; display: block;">
  <img src="${inlineWikiImgUrl}" alt="Wikipedia Inline Editable Demo" data-wiki-inline="true" style="max-width: 100%; width: 340px; height: auto; display: block; border: 1px solid #a2a9b1; cursor: pointer;" />
  <figcaption style="font-size: 12px; color: #54595d; font-style: italic; margin-top: 4px;">↑ Tap this inline image inside the workspace to test in-place Size, Rotate &amp; Image Studio Filter controls!</figcaption>
</figure>

<hr />

<h2>4. Deep 6-Category Word Analysis &amp; Offline Dictionary</h2>
<p>Kisi shabd ki gehrai jaanni ho? Canvas par koi word select karke (ya seedhe search karke) toolbar me <strong>Word Analysis (Book icon)</strong> par tap karo:</p>
<ul>
  <li><strong>6 Categories &amp; 22+ Linguistic Metrics:</strong> Yeh tool <em>Free Dictionary API, Datamuse API, LanguageTool API, aur TextRazor / Wikipedia Knowledge Graph</em> se data laakar aapko <strong>Phonetics (IPA, Syllables, Perfect &amp; Slant Rhymes, Meter), Lexical (Definitions, Synonyms, Antonyms, Hypernyms, Hyponyms, Collocations), Morphology &amp; Etymology, Pragmatics &amp; Sentiment Polarity, Knowledge Graph (NER &amp; Disambiguation), aur Style/Zipf/Readability</strong> ek clean pop-up me dikhata hai.</li>
  <li><strong>Auto-Save for Offline Viewing:</strong> Jo bhi word aap ek baar search karte hain, vo apne aap save ho jata hai! Baad me bina internet ke <strong>Settings → Saved Word Analysis</strong> button par click karke aap saare words <strong>Alphabetical Order (A–Z)</strong> me dekh sakte hain aur upar diye <strong>Search button</strong> se turant khoj sakte hain.</li>
</ul>

<hr />

<h2>5. Stealth Dual-Vault Architecture, Anti-Screenshot &amp; Brute-Force Cooldown</h2>
<p>Ab baat karte hain Likkho ke sabse badass security system ki—बिल्कुल साफ और फ्रैंक शब्दों में:</p>
<ul>
  <li><strong>Primary Passcode &amp; Individual Diary Lock:</strong> Settings me jaakar aap app par 4–6 digit Passcode (aur Primary me Biometric Unlock) laga sakte hain. Iske alawa har diary ke andar toolbar me <strong>Lock icon</strong> se kisi single diary par alag PIN bhi laga sakte hain!</li>
  <li><strong>Anti-Screenshot &amp; Recent Apps Blur/Black:</strong> Jab app me passcode ON hota hai, to app ke andar kisi bhi page ka <strong>Screenshot ya Screen Recording bilkul block</strong> ho jata hai, aur phone ke Recent Apps switcher me Likkho ka preview <strong>black/blur</strong> ho jata hai. Agar passcode hata denge, to screenshot wapas allow ho jayega.</li>
  <li><strong>Covert Secondary / Decoy Vault (Plausible Deniability):</strong> Maan lijiye koi aapke peeche pad jaye ki <em>"Apni diary ka PIN batao!"</em> — iske liye Likkho me ek <strong>Secret Second Space (Decoy Vault)</strong> hai.
    <br /><strong>कैसे चालू करें?</strong> Sabse pehle Primary me ek passcode set karein. Ab Primary ke <strong>Settings</strong> page par <strong>"Set Passcode" button ko lagataar 10 seconds tak daba kar rakhein (10-sec long press)</strong>! Ek bilkul waisa hi Set Passcode pop-up khulega—wahan ek alag PIN daal dein. Bas! Ab lock screen par Primary PIN daalenge to asli दुनिया (<code>real_vault.db</code>) khulegi, aur Secondary PIN daalenge to ek 100% alag, fresh Secondary Vault (<code>decoy_vault.db</code>) khulegi! Secondary vault me Biometrics ka option nahi dikhta, aur agar aap Primary ya Secondary me se kisi ka bhi passcode remove karenge to Secondary vault automatically deactivate ho jayega.</li>
  <li><strong>Anti-Brute-Force Cooldown:</strong> Agar koi lagataar <strong>5 baar galat PIN</strong> daalta hai, to <strong>30 seconds ka cooldown timer</strong> lag jata hai. Uske baad har agle galat attempt par 30 seconds attempt number se multiply hote jaate hain (6th गलत प्रयास = 180s, 7th = 210s...).</li>
</ul>

<hr />

<h2>6. Multi-Page A4 PDF Export (Zero Line-Cutting!), 10 Formats, Reminders &amp; SAF Backup</h2>
<ul>
  <li><strong>Smart Multi-Page A4 PDF Export:</strong> Jab aap kisi diary ko <code>.pdf</code> me export karte hain, to poora lamba canvas ek single ajeeb page nahi banta—balki <strong>Standard A4 Pages</strong> me break hota hai jisme har page par aapka <strong>edited Canvas Background</strong> dikhta hai, aur koi bhi text line do pages ke beech me kat-ti (split) nahi hai!</li>
  <li><strong>10 Export Formats:</strong> Export any diary as <code>.pdf</code>, <code>.docx</code>, <code>.html</code>, <code>.md</code>, <code>.txt</code>, <code>.rtf</code>, <code>.json</code>, <code>.csv</code>, <code>.tsv</code>, or <code>.xml</code>.</li>
  <li><strong>Audio Recording Pop-up in Settings:</strong> Settings me <strong>Audio Recording Settings</strong> button par click karke pop-up me apna pasandida format (<code>.wav, .flac, .m4a, .aac, .mp3, .ogg, .webm</code>), Sample Rate (8kHz–48kHz) aur Bitrate (64kbps–320kbps) set karein.</li>
  <li><strong>Date &amp; Time Alarm Reminders:</strong> Workspace ke toolbar me <strong>Bell icon</strong> se kisi bhi diary par reminder lagayein—waqt aane par notification aur alarm chime bajegi.</li>
  <li><strong>AES-256-GCM Encrypted Backup &amp; Restore:</strong> Settings → <strong>Backup &amp; Restore</strong> me jaakar apne phone ke kisi bhi folder me poora vault (texts, images, audio, custom fonts) strong encryption ke saath backup aur restore karein.</li>
</ul>

<p><strong>तो देर किस बात की?</strong> Is starter diary ke elements ko chhed kar dekho, ya homepage ke <strong>+ (Plus)</strong> button par tap karke apni pehli asli kahani likhna shuru karo. <em>Happy Writing on Likkho!</em></p>
`.trim();

  return [
    {
      id: 'log_starter_welcome_guide_v1',
      heading: 'Welcome to Likkho (लिक्खो) — Complete Interactive Feature Tour!',
      contentHtml,
      plainPreview:
        'Hey dost! Welcome to Likkho. This starter diary uses every canvas feature (rich text, spoilers, background, draggable image card, playable audio card, inline Wikipedia image) and frankly explains all features in Hindi & English.',
      pfpDataUrl: createStarterPfpSvgDataUrl(),
      createdAt: now,
      updatedAt: now,
      dateStamp,
      timeStamp,
      updatedDateStamp: dateStamp,
      updatedTimeStamp: timeStamp,
      reminderAt: null,
      pinned: true,
      canvasBgDataUrl: createStarterCanvasBackgroundDataUrl(),
      canvasBgOpacity: 0.28,
      canvasImages: [
        {
          id: 'starter_canvas_img_1',
          dataUrl: createStarterStampImageDataUrl(),
          x: 410,
          y: 415,
          width: 245,
          height: 153,
          opacity: 0.96,
          rotation: -4,
          layer: 'foreground',
        },
      ],
      audioAttachments: [
        {
          id: 'starter_canvas_audio_1',
          name: 'Likkho Welcome Chime.wav',
          format: 'wav',
          dataUrl: createSamplePlayableWavDataUrl(),
          durationSec: 2,
          createdAt: now,
          x: 405,
          y: 590,
          width: 275,
          height: 64,
          rotation: 0,
          playbackRate: 1,
          layer: 'foreground',
        },
      ],
    },
  ];
}

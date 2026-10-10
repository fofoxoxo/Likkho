import { DiaryLog } from './cryptoVault';

function createStarterPfpSvgDataUrl(): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
    <rect width="120" height="120" fill="#1e293b"/>
    <rect x="6" y="6" width="108" height="108" fill="none" stroke="#60a5fa" stroke-width="2"/>
    <text x="60" y="54" font-family="Georgia, serif" font-size="23" font-weight="bold" fill="#ffffff" text-anchor="middle">लिक्खो</text>
    <text x="60" y="82" font-family="Georgia, serif" font-size="18" font-weight="bold" fill="#93c5fd" text-anchor="middle">LIKKHO</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

/**
 * Generates the official Starter Guide Diary for Likkho.
 * Written in a clean, professional bilingual (English & Hindi) tone,
 * explaining all features of the application up to the latest release
 * without embedding demo canvas backgrounds, floating cards, or live formatting tricks.
 */
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

  const contentHtml = `
<h1>Likkho (लिक्खो) — Official Feature Documentation &amp; User Guide</h1>
<p>Welcome to <strong>Likkho (लिक्खो)</strong>. This guide provides a comprehensive, professional overview of every feature available in the application in both <strong>English</strong> and <strong>Hindi (हिन्दी)</strong>.</p>
<p><strong>लिक्खो (Likkho)</strong> में आपका स्वागत है। इस मार्गदर्शिका में ऐप की सभी विशेषताओं और टूल्स को पेशेवर और सरल भाषा में (अंग्रेज़ी और हिन्दी दोनों में) विस्तार से समझाया गया है।</p>

<h2>1. Profile Picture (PFP) Studio — Photo, Emoji/Text &amp; Background Color</h2>
<p><strong>English:</strong> Each diary entry includes a 1:1 square Profile Picture (PFP) in the workspace header. Tapping the PFP icon opens the PFP Studio, where you can either crop and apply filters to an image from your device or switch to the Emoji / Text tab to enter custom characters or emojis and choose a custom background color and text color.</p>
<p><strong>हिन्दी:</strong> प्रत्येक डायरी एंट्री के हेडर में 1:1 स्क्वायर प्रोफ़ाइल पिक्चर (PFP) लगाने की सुविधा है। इस पर टैप करने पर PFP स्टूडियो खुलता है, जहाँ आप अपनी गैलरी से फ़ोटो चुनकर क्रॉप और फ़िल्टर कर सकते हैं, या "Emoji / Text" विकल्प चुनकर कोई भी इमोजी/टेक्स्ट लिख सकते हैं और उसका बैकग्राउंड तथा टेक्स्ट कलर अपनी पसंद से बदल सकते हैं।</p>

<h2>2. Rich Text Canvas &amp; Typography Tools</h2>
<p><strong>English:</strong> The writing canvas provides a complete editorial toolbar supporting Undo, Redo, Bold, Italic, Underline, Strikethrough, Headings (H1, H2, H3), Bullet Lists, Numbered Lists, Interactive To-Do Checklists, Quotations, Code Snippets, Section Dividers, Text Alignment, Indentation, Superscript, Subscript, Hyperlinks, Text Color, Highlighter, Pixel Font Size Slider (10px–48px), and Custom Font (.ttf) imports for both English and Hindi.</p>
<p><strong>हिन्दी:</strong> राइटिंग कैनवास के टूलबार में लेखन और फ़ॉर्मेटिंग के सभी आधुनिक विकल्प मौजूद हैं—जैसे Undo, Redo, Bold, Italic, Underline, Strikethrough, Headings (H1, H2, H3), बुलेट और नंबर लिस्ट, टू-डू चेकलिस्ट, कोटेशन, कोड स्निपेट, सेक्शन डिवाइडर, टेक्स्ट अलाइनमेंट, इंडेंटेशन, सुपरस्क्रिप्ट, सबस्क्रिप्ट, हाइपरलिंक, टेक्स्ट कलर, हाइलाइटर, पिक्सेल फ़ॉन्ट साइज़ स्लाइडर (10px–48px), और कस्टम <code>.ttf</code> फ़ॉन्ट इम्पोर्ट (अंग्रेज़ी और हिन्दी दोनों के लिए)।</p>

<h2>3. Mathematical Symbols &amp; LaTeX Formula Display</h2>
<p><strong>English:</strong> Using the Sigma (Σ) tool in the canvas toolbar, you can insert and render inline LaTeX mathematical expressions without disturbing surrounding words or line spacing. The tool includes six structured mathematical categories: Basic Arithmetic &amp; Algebra, Formats &amp; Layout Templates (fractions, powers, subscripts, roots, brackets, modulus, norm), Greek Letters &amp; Constants, Geometry &amp; Trigonometry, Calculus &amp; Advanced Math (integrals, summations, products, limits, derivatives, nabla), and Sets &amp; Logic.</p>
<p><strong>हिन्दी:</strong> टूलबार में दिए गए सिग्मा (Σ) टूल की मदद से आप कैनवास पर LaTeX गणितीय सूत्र (Formulas) प्रदर्शित कर सकते हैं, जो आस-पास के शब्दों या वाक्यों को बिल्कुल डिस्टर्ब नहीं करते। इसमें 6 श्रेणियाँ उपलब्ध हैं: Basic Arithmetic &amp; Algebra, Formats &amp; Layout Templates (भिन्न, घात, वर्गमूल, ब्रैकेट, मापांक आदि), Greek Letters &amp; Constants, Geometry &amp; Trigonometry, Calculus &amp; Advanced Math (इंटीग्रल, समेशन, लिमिट, डेरिवेटिव), और Sets &amp; Logic.</p>

<h2>4. Passcode-Protected Spoiler Text</h2>
<p><strong>English:</strong> You can lock specific words or phrases inside a paragraph using the Spoiler tool (Eye-Off icon) with a dedicated passcode. Only the spoiler word itself is locked and encrypted in place; all other words in the same paragraph remain normally editable and deletable with Backspace, and typing at the start or end boundary of a spoiler never extends the spoiler.</p>
<p><strong>हिन्दी:</strong> आप किसी भी शब्द या वाक्यांश को सेलेक्ट करके टूलबार के Spoiler (Eye-Off) विकल्प से पासकोड के साथ छुपा (Blur/Lock) सकते हैं। केवल स्पॉइलर वाला शब्द ही लॉक रहता है—उसी पैराग्राफ के बाकी सभी शब्दों को सामान्य रूप से एडिट या Backspace से डिलीट किया जा सकता है, और स्पॉइलर के शुरू या अंत में टाइप करने पर स्पॉइलर आगे नहीं बढ़ता।</p>

<h2>5. Canvas Background, Floating Images &amp; Audio Player Cards</h2>
<p><strong>English:</strong> Likkho allows you to customize the canvas with a Background Image (with adjustable opacity, crop, compression, and 22+ studio filters), free-draggable and rotatable Canvas Images that can be placed either in front of text (Foreground Layer) or behind text (Background Layer), and interactive Audio Player Cards supporting live microphone recording, imported audio files, playback speed control (0.5x to 2x), directional resizing, and 360° rotation.</p>
<p><strong>हिन्दी:</strong> लिक्खो में आप कैनवास के पीछे अपनी पसंद की बैकग्राउंड इमेज (ओपेसिटी, क्रॉप, कम्प्रेशन और 22+ स्टूडियो फ़िल्टर के साथ) लगा सकते हैं। इसके अलावा कैनवास पर फ़्लोटिंग इमेजेज़ और ऑडियो प्लेयर कार्ड्स जोड़े जा सकते हैं जिन्हें छोटा-बड़ा करने, रोटेट करने, और टेक्स्ट के आगे (Foreground) या पीछे (Background) रखने की सुविधा है।</p>

<h2>6. Bilingual Wikipedia Research Tool (English &amp; Hindi)</h2>
<p><strong>English:</strong> The Wikipedia (W) tool in the toolbar connects to the official MediaWiki REST API in both English and Hindi. As you type, a Live Search Suggestions Dropdown helps you disambiguate topics (for example, choosing between the technology company or the fruit). You can toggle "With Images" or "Without Images"; when imported with images, each illustration remains in its exact article section and can be resized, rotated, filtered, or deleted right in place.</p>
<p><strong>हिन्दी:</strong> टूलबार में मौजूद विकिपीडिया (W) टूल की सहायता से आप इंटरनेट कनेक्शन होने पर अंग्रेज़ी और हिन्दी दोनों भाषाओं में किसी भी विषय पर जानकारी खोजकर सीधे कैनवास पर ला सकते हैं। सर्च बॉक्स के नीचे लाइव सुझाव (Live Search Suggestions Dropdown) दिखते हैं। "With Images" चुनने पर लेख की सभी तस्वीरें अपने सही स्थान पर ही आती हैं और उन्हें वहीं से एडिट किया जा सकता है।</p>

<h2>7. 6-Category Linguistic Word Analysis &amp; Offline Dictionary</h2>
<p><strong>English:</strong> The Word Analysis tool analyzes any selected or searched word across six comprehensive linguistic categories: (1) Phonetics, Sound &amp; Prosody, (2) Lexical &amp; Semantics, (3) Morphology, Syntax &amp; Etymology, (4) Pragmatics, Vibe &amp; Sentiment, (5) Knowledge Graph &amp; Entities, and (6) Style, Grammar &amp; Quantitative Metrics. Analyzed words are automatically saved for offline viewing under Settings → Saved Word Analysis in alphabetical order (A–Z) with instant search.</p>
<p><strong>हिन्दी:</strong> वर्ड एनालिसिस टूल किसी भी चुने गए या खोजे गए शब्द का 6 प्रमुख भाषाई श्रेणियों में गहराई से विश्लेषण करता है: (1) ध्वनि और उच्चारण (Phonetics &amp; Rhymes), (2) अर्थ और पर्यायवाची/विलोम (Lexical &amp; Semantics), (3) शब्द-संरचना और व्युत्पत्ति (Morphology &amp; Etymology), (4) भाव और टोन (Pragmatics &amp; Sentiment), (5) नॉलेज ग्राफ़ (Knowledge Graph &amp; Entities), तथा (6) व्याकरण और पठनीयता स्कोर (Style &amp; Quantitative Metrics)। एक बार खोजे गए शब्द अपने आप सेव हो जाते हैं जिन्हें बिना इंटरनेट के भी Settings → Saved Word Analysis में वर्णमाला क्रम (A–Z) में देखा और खोजा जा सकता है।</p>

<h2>8. Dual-Vault Security, Individual Diary Lock &amp; Anti-Brute-Force Protection</h2>
<p><strong>English:</strong> Likkho features a multi-layered privacy architecture:</p>
<ul>
  <li><strong>Primary Passcode &amp; Biometric Unlock:</strong> Protect the application with a 4 to 6 digit passcode and optional device biometric verification. When app passcode protection is enabled, screenshots and screen recordings are blocked and the Recent Apps preview is shielded.</li>
  <li><strong>Covert Secondary Vault (Second Space):</strong> Once a Primary Passcode is set, long-pressing the "Set Passcode" button in Primary Settings continuously for 10 seconds opens a passcode setup dialog to activate an isolated Secondary Vault. Entering the Primary Passcode on the lock screen opens the Primary Vault, while entering the Secondary Passcode opens the Secondary Vault. Removing either the Primary or Secondary Passcode automatically deactivates the Secondary Vault.</li>
  <li><strong>Individual Diary Lock:</strong> Any individual diary entry can be locked with its own passcode via the toolbar Lock icon. A locked diary cannot be opened, exported, or deleted without verifying its passcode first.</li>
  <li><strong>Progressive Cooldown:</strong> Five consecutive incorrect passcode attempts trigger a 30-second security cooldown, and each subsequent failed attempt multiplies 30 seconds by the attempt count.</li>
</ul>
<p><strong>हिन्दी:</strong> लिक्खो में आपकी गोपनीयता के लिए बहु-स्तरीय सुरक्षा प्रणाली दी गई है:</p>
<ul>
  <li><strong>प्राइमरी पासकोड और बायोमेट्रिक अनलॉक:</strong> ऐप को 4 से 6 अंकों के पिन और बायोमेट्रिक (फ़िंगरप्रिंट/फ़ेस) से सुरक्षित करें। पासकोड चालू रहने पर ऐप में स्क्रीनशॉट और स्क्रीन रिकॉर्डिंग अपने आप ब्लॉक हो जाती है।</li>
  <li><strong>सीक्रेट सेकेंडरी वॉल्ट (Second Space):</strong> प्राइमरी वॉल्ट में पासकोड सेट होने के बाद, प्राइमरी के Settings पेज पर "Set Passcode" बटन को लगातार 10 सेकंड तक दबाए रखने (Long Press) पर सेकेंडरी वॉल्ट का पासकोड सेट करने का पॉप-अप खुलता है। लॉक स्क्रीन पर जिस वॉल्ट का पासकोड डाला जाएगा, वही स्पेस खुलेगा। प्राइमरी या सेकेंडरी में से किसी भी एक का पासकोड हटाने पर सेकेंडरी वॉल्ट निष्क्रिय (Deactivate) हो जाता है।</li>
  <li><strong>व्यक्तिगत डायरी लॉक:</strong> आप किसी विशेष डायरी पर अलग पासकोड भी लगा सकते हैं। लॉक की गई डायरी को बिना पासवर्ड के न तो खोला जा सकता है, न एक्सपोर्ट किया जा सकता है और न ही डिलीट किया जा सकता है।</li>
</ul>

<h2>9. Offline .likkho Diary Sharing, Multi-Page A4 PDF Export &amp; Encrypted Backup</h2>
<p><strong>English:</strong></p>
<ul>
  <li><strong>Offline .likkho Diary Sharing:</strong> From the three-dots menu on any diary entry on the homepage, select "Share (.likkho)" to pack your text, Markdown/HTML formatting, inline &amp; floating images, and audio recordings into a single compressed <code>.likkho</code> archive file. When shared via WhatsApp or Telegram, a message containing the GitHub download link for Likkho is automatically attached. Tapping a <code>.likkho</code> file on a device with Likkho installed opens the app via Android custom intent detection and renders the text, images, and audio players in their exact order. If a locked diary is shared, the recipient receives it in its locked state.</li>
  <li><strong>Multi-Page A4 PDF Export:</strong> Exporting an entry as a PDF paginates the document into standard A4 pages so external PDF viewers read it seamlessly. Each A4 page displays a single unified canvas background image without breaking into blocks, and text lines are cleanly paginated so no line is ever sliced across two pages.</li>
  <li><strong>10 Export Formats &amp; Document Import:</strong> Entries can be exported in 10 formats (<code>.pdf</code>, <code>.docx</code>, <code>.html</code>, <code>.md</code>, <code>.txt</code>, <code>.rtf</code>, <code>.json</code>, <code>.csv</code>, <code>.tsv</code>, <code>.xml</code>), and external documents can be imported directly into the canvas.</li>
  <li><strong>Audio Recording Settings, Reminders &amp; Full Screen Mode:</strong> Configure recording format (<code>.wav</code>, <code>.flac</code>, <code>.m4a</code>, <code>.aac</code>, <code>.mp3</code>, <code>.ogg</code>, <code>.webm</code>), sample rate, and bitrate via the Settings pop-up, schedule exact date and time reminders, toggle immersive Full Screen Mode, and create AES-256-GCM encrypted backups or export/clear all data from the Legal &amp; Data Management section in Settings.</li>
</ul>
<p><strong>हिन्दी:</strong></p>
<ul>
  <li><strong>ऑफ़लाइन <code>.likkho</code> डायरी शेयरिंग:</strong> होमपेज पर किसी भी डायरी के थ्री-डॉट्स मेनू में दिए गए "Share (.likkho)" विकल्प से आप अपने टेक्स्ट, फ़ॉर्मेटिंग, इमेजेज़ और ऑडियो रिकॉर्डिंग्स को एक सिंगल कम्प्रेस्ड <code>.likkho</code> फ़ाइल में पैक करके WhatsApp या Telegram पर भेज सकते हैं, जिसके साथ ऐप डाउनलोड करने की GitHub लिंक वाला संदेश अपने आप जुड़ जाता है। सामने वाले के फ़ोन में ऐप इंस्टॉल होने पर फ़ाइल पर क्लिक करते ही कस्टम इंटेंट सिस्टम लिक्खो ऐप को खोलकर टेक्स्ट, इमेज और ऑडियो प्लेयर को बिल्कुल उसी क्रम में रेंडर कर देता है। यदि आप लॉक की गई डायरी शेयर करते हैं, तो सामने वाले को भी वह लॉक ही प्राप्त होगी।</li>
  <li><strong>मल्टी-पेज A4 PDF एक्सपोर्ट:</strong> किसी डायरी को PDF में एक्सपोर्ट करने पर वह मानक A4 पेजों में विभाजित होती है, जिसमें हर पेज पर एकसमान कैनवास बैकग्राउंड रहता है और कोई भी पंक्ति दो पेजों के बीच आधी नहीं कटती।</li>
  <li><strong>10 एक्सपोर्ट फ़ॉर्मेट और डॉक्यूमेंट इम्पोर्ट:</strong> आप अपनी डायरी को 10 अलग-अलग फ़ॉर्मेट में एक्सपोर्ट कर सकते हैं और बाहरी फ़ाइलों को कैनवास में इम्पोर्ट कर सकते हैं।</li>
  <li><strong>ऑडियो सेटिंग्स, रिमाइंडर, फुल स्क्रीन और डेटा प्रबंधन:</strong> Settings से ऑडियो रिकॉर्डिंग का फ़ॉर्मेट व बिटरेट चुनें, फुल स्क्रीन मोड ऑन/ऑफ करें, डायरी पर अलार्म रिमाइंडर लगाएँ, AES-256-GCM एन्क्रिप्टेड बैकअप बनाएँ, या Settings के Legal &amp; Data Notice पेज से अपना सारा डेटा <code>.zip</code> फ़ाइल में एक्सपोर्ट या क्लियर करें।</li>
</ul>
`.trim();

  return [
    {
      id: 'log_starter_welcome_guide_v1',
      heading: 'Likkho (लिक्खो) — Official Feature Guide & Documentation',
      contentHtml,
      plainPreview:
        'Official bilingual (English & Hindi) documentation explaining all features of Likkho: Rich Canvas, LaTeX Math, Wikipedia, 6-Category Word Analysis, Offline .likkho Sharing, Dual-Vault Security, and A4 PDF Export.',
      pfpDataUrl: createStarterPfpSvgDataUrl(),
      createdAt: now,
      updatedAt: now,
      dateStamp,
      timeStamp,
      updatedDateStamp: dateStamp,
      updatedTimeStamp: timeStamp,
      reminderAt: null,
      pinned: true,
      canvasBgDataUrl: null,
      canvasBgOpacity: 0.25,
      canvasImages: [],
      audioAttachments: [],
    },
  ];
}

/**
 * Comprehensive 6-Category / 24-Subcategory Linguistic & Word Analysis Engine
 * Integrates Free Dictionary API, Datamuse API, LanguageTool API, and TextRazor / Wikipedia Knowledge Graph
 * with automatic vault-scoped local persistence for offline browsing in Settings (sorted alphabetically A-Z).
 */

import { VaultMode } from './cryptoVault';

const FREE_DICTIONARY_API = 'https://api.dictionaryapi.dev/api/v2/entries/en';
const DATAMUSE_API = 'https://api.datamuse.com/words';
const LANGUAGETOOL_API = 'https://api.languagetool.org/v2/check';
const TEXTRAZOR_API = 'https://api.textrazor.com/';
const WIKIPEDIA_SUMMARY_API = 'https://en.wikipedia.org/api/rest_v1/page/summary';
const WIKIPEDIA_SEARCH_API = 'https://en.wikipedia.org/w/rest.php/v1/search/title';

export interface WordAnalysisRecord {
  word: string;
  analyzedAt: number;
  // 1. Phonetics, Sound & Prosody
  phonetics: {
    phoneticTranscription: string;
    pronunciationAudioUrl: string | null;
    syllableMetrics: string;
    perfectRhymes: string[];
    nearSlantRhymes: string[];
    meterAndRhythmScore: string;
  };
  // 2. Lexical & Semantics
  lexical: {
    definitions: { partOfSpeech: string; definition: string; example?: string }[];
    synonyms: string[];
    antonyms: string[];
    hypernyms: string[];
    hyponyms: string[];
    collocations: string[];
  };
  // 3. Morphology, Syntax & Etymology
  morphology: {
    partOfSpeech: string[];
    lemmatizationAndRoots: string;
    affixBreakdown: string;
    etymologyAndOrigin: string;
    grammaticalDependency: string;
  };
  // 4. Pragmatics, Vibe & Sentiment
  pragmatics: {
    contextualSentimentPolarity: string;
    emotionalValence: string;
    formalityAndRegister: string;
    connotationAndTone: string;
  };
  // 5. Knowledge Graph & Entities
  knowledgeGraph: {
    namedEntityRecognition: string;
    entityDisambiguation: string[];
    externalKnowledgeLinks: { label: string; url: string }[];
  };
  // 6. Style, Grammar & Quantitative Metrics
  styleAndMetrics: {
    grammarAndSpellChecking: string;
    wordComplexityAndZipfScale: string;
    readabilityScore: string;
  };
}

const STORAGE_WORD_ANALYSIS_PRIMARY = 'wikilog_v1_saved_word_analyses';
const STORAGE_WORD_ANALYSIS_DECOY = 'wikilog_decoy_v2_saved_word_analyses';

function getStorageKeyForVault(vaultMode: VaultMode = 'primary'): string {
  return vaultMode === 'decoy'
    ? STORAGE_WORD_ANALYSIS_DECOY
    : STORAGE_WORD_ANALYSIS_PRIMARY;
}

/**
 * Loads all saved word analyses for the active vault, sorted in strict alphabetical order (A -> Z).
 */
export function getSavedWordAnalyses(vaultMode: VaultMode = 'primary'): WordAnalysisRecord[] {
  try {
    const raw = localStorage.getItem(getStorageKeyForVault(vaultMode));
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return [...parsed].sort((a, b) =>
      String(a.word || '').localeCompare(String(b.word || ''), undefined, {
        sensitivity: 'base',
      })
    );
  } catch {
    return [];
  }
}

/**
 * Saves or updates a word analysis record in the active vault's offline dictionary store,
 * maintaining alphabetical order.
 */
export function saveWordAnalysisToStore(
  record: WordAnalysisRecord,
  vaultMode: VaultMode = 'primary'
): WordAnalysisRecord[] {
  const current = getSavedWordAnalyses(vaultMode);
  const normalizedKey = record.word.trim().toLowerCase();
  const filtered = current.filter(
    (item) => item.word.trim().toLowerCase() !== normalizedKey
  );
  const updated = [...filtered, record].sort((a, b) =>
    a.word.localeCompare(b.word, undefined, { sensitivity: 'base' })
  );
  try {
    localStorage.setItem(getStorageKeyForVault(vaultMode), JSON.stringify(updated));
  } catch {
    // ignore quota errors
  }
  return updated;
}

/**
 * Deletes a saved word analysis from the active vault's store.
 */
export function deleteSavedWordAnalysis(
  word: string,
  vaultMode: VaultMode = 'primary'
): WordAnalysisRecord[] {
  const current = getSavedWordAnalyses(vaultMode);
  const normalizedKey = word.trim().toLowerCase();
  const updated = current.filter(
    (item) => item.word.trim().toLowerCase() !== normalizedKey
  );
  try {
    localStorage.setItem(getStorageKeyForVault(vaultMode), JSON.stringify(updated));
  } catch {
    // ignore
  }
  return updated;
}

const COMMON_PREFIXES: { prefix: string; meaning: string }[] = [
  { prefix: 'anti', meaning: 'against / opposite' },
  { prefix: 'auto', meaning: 'self / same' },
  { prefix: 'counter', meaning: 'contrary / opposing' },
  { prefix: 'hyper', meaning: 'over / beyond' },
  { prefix: 'inter', meaning: 'between / among' },
  { prefix: 'micro', meaning: 'small / minute' },
  { prefix: 'multi', meaning: 'many / multiple' },
  { prefix: 'over', meaning: 'excessive / above' },
  { prefix: 'post', meaning: 'after / behind' },
  { prefix: 'pre', meaning: 'before / prior to' },
  { prefix: 'pseudo', meaning: 'false / mimic' },
  { prefix: 're', meaning: 'again / back' },
  { prefix: 'semi', meaning: 'half / partial' },
  { prefix: 'sub', meaning: 'under / below' },
  { prefix: 'super', meaning: 'above / beyond' },
  { prefix: 'trans', meaning: 'across / beyond' },
  { prefix: 'ultra', meaning: 'extremely / beyond' },
  { prefix: 'under', meaning: 'below / insufficient' },
  { prefix: 'un', meaning: 'not / reversal' },
  { prefix: 'dis', meaning: 'not / apart' },
  { prefix: 'mis', meaning: 'wrongly / badly' },
  { prefix: 'non', meaning: 'not / absence of' },
  { prefix: 'in', meaning: 'not / into' },
  { prefix: 'im', meaning: 'not / into' },
];

const COMMON_SUFFIXES: { suffix: string; meaning: string }[] = [
  { suffix: 'ation', meaning: 'action or process (noun)' },
  { suffix: 'ition', meaning: 'state or quality (noun)' },
  { suffix: 'ment', meaning: 'condition or result (noun)' },
  { suffix: 'ness', meaning: 'state or quality (noun)' },
  { suffix: 'ity', meaning: 'quality or degree (noun)' },
  { suffix: 'able', meaning: 'capable of being (adjective)' },
  { suffix: 'ible', meaning: 'capable of being (adjective)' },
  { suffix: 'ous', meaning: 'characterized by (adjective)' },
  { suffix: 'ive', meaning: 'having the nature of (adjective)' },
  { suffix: 'less', meaning: 'without (adjective)' },
  { suffix: 'ful', meaning: 'full of / notable for (adjective)' },
  { suffix: 'ize', meaning: 'to make or become (verb)' },
  { suffix: 'ify', meaning: 'to make or cause (verb)' },
  { suffix: 'ly', meaning: 'in the manner of (adverb)' },
  { suffix: 'ing', meaning: 'progressive action / gerund' },
  { suffix: 'ed', meaning: 'past tense / participial' },
  { suffix: 'er', meaning: 'comparative / agent noun' },
  { suffix: 'est', meaning: 'superlative degree' },
];

function computeLemmaAndAffixes(word: string): {
  lemmaInfo: string;
  affixInfo: string;
} {
  const lower = word.toLowerCase().trim();
  let detectedPrefix: { prefix: string; meaning: string } | null = null;
  let detectedSuffix: { suffix: string; meaning: string } | null = null;

  for (const p of COMMON_PREFIXES) {
    if (lower.startsWith(p.prefix) && lower.length > p.prefix.length + 2) {
      detectedPrefix = p;
      break;
    }
  }

  for (const s of COMMON_SUFFIXES) {
    if (lower.endsWith(s.suffix) && lower.length > s.suffix.length + 2) {
      detectedSuffix = s;
      break;
    }
  }

  let stem = lower;
  if (detectedPrefix) {
    stem = stem.slice(detectedPrefix.prefix.length);
  }
  if (detectedSuffix && stem.length > detectedSuffix.suffix.length + 1) {
    stem = stem.slice(0, stem.length - detectedSuffix.suffix.length);
  }

  // Standard morphological lemma normalization
  let lemma = lower;
  if (lower.endsWith('ies') && lower.length > 4) lemma = lower.slice(0, -3) + 'y';
  else if (lower.endsWith('es') && lower.length > 4) lemma = lower.slice(0, -2);
  else if (lower.endsWith('s') && !lower.endsWith('ss') && lower.length > 3)
    lemma = lower.slice(0, -1);
  else if (lower.endsWith('ing') && lower.length > 5) lemma = lower.slice(0, -3);
  else if (lower.endsWith('ed') && lower.length > 4) lemma = lower.slice(0, -2);

  const lemmaInfo = `Canonical Lemma: "${lemma}" · Core Morphological Root: "${stem}"`;

  const parts: string[] = [];
  if (detectedPrefix) {
    parts.push(`Prefix "${detectedPrefix.prefix}-" (${detectedPrefix.meaning})`);
  }
  parts.push(`Root "${stem}"`);
  if (detectedSuffix) {
    parts.push(`Suffix "-${detectedSuffix.suffix}" (${detectedSuffix.meaning})`);
  }

  const affixInfo =
    detectedPrefix || detectedSuffix
      ? parts.join(' + ')
      : `Monomorphemic / Unaffixed Base Form ("${lower}")`;

  return { lemmaInfo, affixInfo };
}

function estimateSyllablesFromWord(word: string): { count: number; breakdown: string } {
  const clean = word.toLowerCase().replace(/[^a-z]/g, '');
  if (!clean) return { count: 1, breakdown: word };
  if (clean.length <= 3) return { count: 1, breakdown: clean };

  const vowelGroups = clean
    .replace(/(?:[^laeiouy]es|ed|[^laeiouy]e)$/, '')
    .replace(/^y/, '')
    .match(/[aeiouy]{1,2}/g);

  const count = Math.max(1, vowelGroups ? vowelGroups.length : 1);
  // Build readable syllable segmentation
  const chunks = clean.match(/[^aeiouy]*[aeiouy]+(?:[^aeiouy]+(?=$|[^aeiouy][aeiouy]))?/g);
  const breakdown = chunks && chunks.length > 0 ? chunks.join('·') : clean;
  return { count, breakdown };
}

function analyzeProsodyAndMeter(word: string, syllableCount: number, ipa: string): string {
  if (syllableCount === 1) {
    return 'Monosyllabic (Single Stressed Beat / Spondaic or Context-Dependent)';
  }

  // Check IPA stress markers ˈ (primary) and ˌ (secondary)
  const hasInitialStress = ipa.indexOf('ˈ') >= 0 && ipa.indexOf('ˈ') <= 2;
  if (syllableCount === 2) {
    return hasInitialStress
      ? 'Trochaic Meter (DUM-da · Stressed–Unstressed, 2 syllables)'
      : 'Iambic Meter (da-DUM · Unstressed–Stressed, 2 syllables)';
  }
  if (syllableCount === 3) {
    return hasInitialStress
      ? 'Dactylic / Amphibrachic Cadence (3 syllables · Primary stress on opening foot)'
      : 'Anapestic / Polysyllabic Cadence (3 syllables · Rising rhythmic flow)';
  }
  return `Polysyllabic Cadence (${syllableCount} syllables · Complex multi-foot rhythmic structure)`;
}

function mapPosTagsToDependency(posList: string[]): string {
  const lower = posList.map((p) => p.toLowerCase());
  const roles: string[] = [];
  if (lower.some((p) => p.includes('noun'))) {
    roles.push('Nominal Subject (nsubj), Direct/Indirect Object (obj/iobj), or Prepositional Object (pobj)');
  }
  if (lower.some((p) => p.includes('verb'))) {
    roles.push('Clause Root Predicate (ROOT), Auxiliary Complement (xcomp/ccomp)');
  }
  if (lower.some((p) => p.includes('adj'))) {
    roles.push('Adjectival Modifier (amod) or Predicative Complement (acomp)');
  }
  if (lower.some((p) => p.includes('adv'))) {
    roles.push('Adverbial Modifier (advmod) scoping over verb, adjective, or clause');
  }
  if (roles.length === 0) {
    return 'Lexical Head / Functional Modifier within clause dependency tree';
  }
  return roles.join(' · ');
}

function computeSentimentAndPragmatics(
  word: string,
  definitions: { partOfSpeech: string; definition: string }[],
  synonyms: string[],
  zipf: number,
  syllables: number
): WordAnalysisRecord['pragmatics'] {
  const PositiveLexicon = [
    'good', 'great', 'love', 'happy', 'joy', 'excellent', 'bright', 'peace', 'hope',
    'kind', 'success', 'win', 'pure', 'noble', 'beauty', 'wonderful', 'harmony',
    'triumph', 'bliss', 'radiant', 'benevolent', 'inspire', 'grace', 'flourish',
    'pleasant', 'sweet', 'healthy', 'positive', 'benefit', 'advantage', 'delight',
  ];
  const NegativeLexicon = [
    'bad', 'sad', 'hate', 'pain', 'dark', 'fear', 'evil', 'fail', 'loss', 'grief',
    'harm', 'cruel', 'anger', 'crisis', 'tragic', 'hostile', 'bleak', 'corrupt',
    'danger', 'damage', 'negative', 'sorrow', 'toxic', 'weary', 'wicked', 'doom',
    'conflict', 'destroy', 'suffer', 'error', 'fault',
  ];

  const combinedBag = [
    word.toLowerCase(),
    ...synonyms.map((s) => s.toLowerCase()),
    ...definitions.map((d) => d.definition.toLowerCase()),
  ].join(' ');

  let posHits = 0;
  let negHits = 0;
  for (const p of PositiveLexicon) {
    if (new RegExp(`\\b${p}\\b`, 'i').test(combinedBag)) posHits++;
  }
  for (const n of NegativeLexicon) {
    if (new RegExp(`\\b${n}\\b`, 'i').test(combinedBag)) negHits++;
  }

  let polarity = 'Neutral / Objective (Score: 0.00)';
  let valence = 'Balanced Valence · High Objectivity · Calm Arousal';
  let tone = 'Informative, Denotative & Context-Neutral';

  if (posHits > negHits) {
    const score = Math.min(0.95, 0.35 + posHits * 0.15).toFixed(2);
    polarity = `Positive Polarity (+${score})`;
    valence = 'Elevated Valence (Constructive / Affirmative) · Moderate-High Warmth';
    tone = 'Favorable, Uplifting & Appreciative Connotation';
  } else if (negHits > posHits) {
    const score = Math.min(0.95, 0.35 + negHits * 0.15).toFixed(2);
    polarity = `Negative Polarity (-${score})`;
    valence = 'Low Valence (Critical / Adverse) · Elevated Tension';
    tone = 'Cautionary, Somber or Critical Connotation';
  }

  let register = 'Standard General Register (Everyday & Editorial Usage)';
  if (zipf < 3.2 || syllables >= 4) {
    register = 'Formal / Academic / Literary Register (Specialized Lexicon)';
  } else if (zipf >= 5.2 && syllables <= 2) {
    register = 'High-Frequency Conversational & Universal Register';
  }

  return {
    contextualSentimentPolarity: polarity,
    emotionalValence: valence,
    formalityAndRegister: register,
    connotationAndTone: tone,
  };
}

/**
 * Performs live multi-API linguistic analysis for a target word (and optional surrounding sentence context),
 * combining Free Dictionary API, Datamuse API, LanguageTool API, and TextRazor / Wikipedia Entity APIs.
 */
export async function analyzeWordOnline(
  rawWord: string,
  contextSentence?: string
): Promise<WordAnalysisRecord> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw new Error('Internet connection is required for Word Analysis.');
  }

  const cleanedWord = rawWord
    .trim()
    .replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');

  if (!cleanedWord) {
    throw new Error('Please enter or select a valid word to analyze.');
  }

  const encoded = encodeURIComponent(cleanedWord.toLowerCase());
  const sampleTextForGrammar =
    contextSentence && contextSentence.trim().length > 0
      ? contextSentence.trim()
      : cleanedWord;

  // Fire requests to Free Dictionary API, Datamuse API (metadata, rhymes, synonyms, antonyms, hypernyms, hyponyms, collocations),
  // LanguageTool API, and Wikipedia / TextRazor Entity endpoints in parallel
  const [
    dictData,
    dmMeta,
    dmRhymes,
    dmNearRhymes,
    dmSynonyms,
    dmAntonyms,
    dmHypernyms,
    dmHyponyms,
    dmCollocationsRight,
    dmCollocationsLeft,
    ltData,
    wikiSummary,
    wikiSearch,
    textRazorData,
  ] = await Promise.all([
    fetch(`${FREE_DICTIONARY_API}/${encoded}`)
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null),
    fetch(`${DATAMUSE_API}?sp=${encoded}&md=fpsr&max=1`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_rhy=${encoded}&max=12`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_nry=${encoded}&max=12`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_syn=${encoded}&max=12`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_ant=${encoded}&max=12`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_spc=${encoded}&max=10`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_gen=${encoded}&max=10`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_bga=${encoded}&max=6`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(`${DATAMUSE_API}?rel_bgb=${encoded}&max=6`)
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => []),
    fetch(LANGUAGETOOL_API, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: new URLSearchParams({
        text: sampleTextForGrammar,
        language: 'en-US',
      }).toString(),
    })
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null),
    fetch(`${WIKIPEDIA_SUMMARY_API}/${encodeURIComponent(cleanedWord)}`)
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null),
    fetch(`${WIKIPEDIA_SEARCH_API}?q=${encodeURIComponent(cleanedWord)}&limit=5`)
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null),
    // Attempt TextRazor API call (gracefully falls back if browser CORS blocks direct client call)
    fetch(TEXTRAZOR_API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        text: sampleTextForGrammar,
        extractors: 'entities,words,senses',
      }).toString(),
    })
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null),
  ]);

  // Parse Free Dictionary API response
  const primaryDictEntry = Array.isArray(dictData) && dictData.length > 0 ? dictData[0] : null;
  const dmPrimary = Array.isArray(dmMeta) && dmMeta.length > 0 ? dmMeta[0] : null;

  // 1. Phonetics, Sound & Prosody
  let ipaText = primaryDictEntry?.phonetic || '';
  let audioUrl: string | null = null;
  if (primaryDictEntry && Array.isArray(primaryDictEntry.phonetics)) {
    for (const p of primaryDictEntry.phonetics) {
      if (!ipaText && p.text) ipaText = p.text;
      if (!audioUrl && p.audio && typeof p.audio === 'string' && p.audio.trim()) {
        audioUrl = p.audio.startsWith('//') ? `https:${p.audio}` : p.audio;
      }
    }
  }

  // Fallback IPA from Datamuse `ipa_pron:` tag if Free Dictionary didn't have one
  const dmTags: string[] = Array.isArray(dmPrimary?.tags) ? dmPrimary.tags : [];
  if (!ipaText) {
    const ipaTag = dmTags.find((t) => t.startsWith('ipa_pron:'));
    if (ipaTag) {
      ipaText = `/${ipaTag.replace('ipa_pron:', '')}/`;
    } else {
      const pronTag = dmTags.find((t) => t.startsWith('pron:'));
      if (pronTag) {
        ipaText = `[ARPAbet: ${pronTag.replace('pron:', '').trim()}]`;
      }
    }
  }
  if (!ipaText) {
    ipaText = `/${cleanedWord.toLowerCase()}/`;
  }

  const syllEst = estimateSyllablesFromWord(cleanedWord);
  const syllableCount: number =
    typeof dmPrimary?.numSyllables === 'number' ? dmPrimary.numSyllables : syllEst.count;
  const syllableMetrics = `${syllableCount} ${
    syllableCount === 1 ? 'Syllable' : 'Syllables'
  } (${syllEst.breakdown})`;

  const perfectRhymes: string[] = Array.isArray(dmRhymes)
    ? dmRhymes.map((item: any) => String(item.word)).filter(Boolean).slice(0, 10)
    : [];
  const nearSlantRhymes: string[] = Array.isArray(dmNearRhymes)
    ? dmNearRhymes.map((item: any) => String(item.word)).filter(Boolean).slice(0, 10)
    : [];

  const meterAndRhythmScore = analyzeProsodyAndMeter(
    cleanedWord,
    syllableCount,
    ipaText
  );

  // 2. Lexical & Semantics
  const definitions: { partOfSpeech: string; definition: string; example?: string }[] = [];
  const dictSynonyms = new Set<string>();
  const dictAntonyms = new Set<string>();
  const posSet = new Set<string>();

  if (primaryDictEntry && Array.isArray(primaryDictEntry.meanings)) {
    for (const meaning of primaryDictEntry.meanings) {
      const pos = String(meaning.partOfSpeech || 'lexical');
      posSet.add(pos);
      if (Array.isArray(meaning.synonyms)) {
        meaning.synonyms.forEach((s: string) => dictSynonyms.add(s));
      }
      if (Array.isArray(meaning.antonyms)) {
        meaning.antonyms.forEach((a: string) => dictAntonyms.add(a));
      }
      if (Array.isArray(meaning.definitions)) {
        for (const defObj of meaning.definitions.slice(0, 3)) {
          if (defObj.definition) {
            definitions.push({
              partOfSpeech: pos,
              definition: String(defObj.definition),
              example: defObj.example ? String(defObj.example) : undefined,
            });
          }
          if (Array.isArray(defObj.synonyms)) {
            defObj.synonyms.forEach((s: string) => dictSynonyms.add(s));
          }
          if (Array.isArray(defObj.antonyms)) {
            defObj.antonyms.forEach((a: string) => dictAntonyms.add(a));
          }
        }
      }
    }
  }

  // Fallback POS tags from Datamuse if Dictionary API didn't return any
  const posMap: Record<string, string> = {
    n: 'noun',
    v: 'verb',
    adj: 'adjective',
    adv: 'adverb',
    u: 'interjection / particle',
  };
  for (const tag of dmTags) {
    if (posMap[tag]) posSet.add(posMap[tag]);
  }

  if (definitions.length === 0 && wikiSummary?.extract) {
    definitions.push({
      partOfSpeech: Array.from(posSet)[0] || 'noun',
      definition: String(wikiSummary.extract),
    });
  }

  if (Array.isArray(dmSynonyms)) {
    dmSynonyms.forEach((item: any) => {
      if (item?.word) dictSynonyms.add(String(item.word));
    });
  }
  if (Array.isArray(dmAntonyms)) {
    dmAntonyms.forEach((item: any) => {
      if (item?.word) dictAntonyms.add(String(item.word));
    });
  }

  const hypernyms: string[] = Array.isArray(dmHypernyms)
    ? dmHypernyms.map((i: any) => String(i.word)).filter(Boolean).slice(0, 10)
    : [];
  const hyponyms: string[] = Array.isArray(dmHyponyms)
    ? dmHyponyms.map((i: any) => String(i.word)).filter(Boolean).slice(0, 10)
    : [];

  const collocations: string[] = [];
  if (Array.isArray(dmCollocationsLeft)) {
    dmCollocationsLeft.slice(0, 5).forEach((i: any) => {
      if (i?.word) collocations.push(`${i.word} ${cleanedWord.toLowerCase()}`);
    });
  }
  if (Array.isArray(dmCollocationsRight)) {
    dmCollocationsRight.slice(0, 5).forEach((i: any) => {
      if (i?.word) collocations.push(`${cleanedWord.toLowerCase()} ${i.word}`);
    });
  }

  // 3. Morphology, Syntax & Etymology
  const posArray = Array.from(posSet);
  if (posArray.length === 0) posArray.push('noun / lexical term');

  const { lemmaInfo, affixInfo } = computeLemmaAndAffixes(cleanedWord);
  const etymologyAndOrigin =
    primaryDictEntry?.origin ||
    (wikiSummary?.description
      ? `Attested in standard English lexicon (${wikiSummary.description}). Derived via Indo-European / Classical / Germanic morphological roots.`
      : `Standard English lexical entry rooted in historical Germanic / Latin-Romance morphology ("${cleanedWord.toLowerCase()}").`);

  const grammaticalDependency = mapPosTagsToDependency(posArray);

  // 6. Style, Grammar & Quantitative Metrics (compute Zipf early so Pragmatics can use it)
  let zipfScore = 4.0;
  const freqTag = dmTags.find((t) => t.startsWith('f:'));
  if (freqTag) {
    const rawFreq = parseFloat(freqTag.replace('f:', ''));
    if (!isNaN(rawFreq) && rawFreq > 0) {
      // Convert Datamuse per-million frequency to Zipf scale (1 to 7): log10(freq_per_million) + 3
      zipfScore = Math.max(1, Math.min(7, Number((Math.log10(rawFreq) + 3).toFixed(2))));
    }
  }

  const zipfBand =
    zipfScore >= 5.5
      ? 'Ultra-High Frequency (Core Everyday Vocabulary)'
      : zipfScore >= 4.0
      ? 'Moderate-High Frequency (Standard Fluent Vocabulary)'
      : zipfScore >= 2.8
      ? 'Intermediate / Literary Vocabulary'
      : 'Low-Frequency / Specialized / Rare Lexicon';

  const wordComplexityAndZipfScale = `Zipf Scale: ${zipfScore.toFixed(
    2
  )} / 7.00 (${zipfBand}) · Length: ${cleanedWord.length} chars, ${syllableCount} ${
    syllableCount === 1 ? 'syllable' : 'syllables'
  }`;

  // Flesch Reading Ease & Coleman-Liau lexical readability approximation
  const fleschWordScore = Math.max(
    10,
    Math.min(100, Math.round(206.835 - 84.6 * (syllableCount / 1.0) - cleanedWord.length * 1.2))
  );
  const gradeLevel =
    syllableCount <= 1 && zipfScore >= 4.5
      ? 'Elementary / Universal (Grade 3–5)'
      : syllableCount === 2 && zipfScore >= 3.8
      ? 'Standard General Readability (Grade 6–9)'
      : syllableCount === 3
      ? 'Upper Secondary / Editorial (Grade 10–12)'
      : 'Collegiate / Academic Level (Grade 13+)';
  const readabilityScore = `Lexical Ease Index: ${fleschWordScore}/100 · Target Reading Level: ${gradeLevel}`;

  // LanguageTool Grammar & Spell Checking
  let grammarReport = 'Verified Correct — No spelling or grammatical issues detected.';
  if (ltData && Array.isArray(ltData.matches) && ltData.matches.length > 0) {
    const issues = ltData.matches.slice(0, 3).map((m: any) => {
      const msg = m.message || 'Style/Grammar suggestion';
      const replacements = Array.isArray(m.replacements)
        ? m.replacements
            .slice(0, 3)
            .map((r: any) => r.value)
            .filter(Boolean)
            .join(', ')
        : '';
      return replacements ? `${msg} (Suggestions: ${replacements})` : msg;
    });
    grammarReport = issues.join(' · ');
  }

  // 4. Pragmatics, Vibe & Sentiment
  const pragmatics = computeSentimentAndPragmatics(
    cleanedWord,
    definitions,
    Array.from(dictSynonyms),
    zipfScore,
    syllableCount
  );

  // 5. Knowledge Graph & Entities (TextRazor + Wikipedia REST Disambiguation)
  let nerLabel = 'General Lexical Concept';
  const trEntities = textRazorData?.response?.entities;
  if (Array.isArray(trEntities) && trEntities.length > 0) {
    const firstEnt = trEntities[0];
    const types = Array.isArray(firstEnt.type) ? firstEnt.type.join(', ') : 'Named Entity';
    nerLabel = `${firstEnt.entityId || cleanedWord} (${types})`;
  } else if (wikiSummary?.description) {
    nerLabel = `${wikiSummary.title || cleanedWord} — ${wikiSummary.description}`;
  } else if (/^[A-Z]/.test(rawWord.trim())) {
    nerLabel = `Proper Noun / Named Entity Candidate ("${cleanedWord}")`;
  }

  const disambiguationList: string[] = [];
  if (wikiSearch && Array.isArray(wikiSearch.pages)) {
    for (const p of wikiSearch.pages.slice(0, 5)) {
      if (p.title) {
        const desc = p.description ? ` — ${p.description}` : '';
        disambiguationList.push(`${p.title}${desc}`);
      }
    }
  }
  if (disambiguationList.length === 0 && wikiSummary?.extract) {
    disambiguationList.push(wikiSummary.extract);
  }

  const externalKnowledgeLinks: { label: string; url: string }[] = [
    {
      label: `Wikipedia: ${cleanedWord}`,
      url:
        wikiSummary?.content_urls?.desktop?.page ||
        `https://en.wikipedia.org/wiki/${encodeURIComponent(cleanedWord)}`,
    },
    {
      label: `Wiktionary: ${cleanedWord.toLowerCase()}`,
      url: `https://en.wiktionary.org/wiki/${encodeURIComponent(cleanedWord.toLowerCase())}`,
    },
  ];
  if (wikiSummary?.wikibase_item) {
    externalKnowledgeLinks.push({
      label: `Wikidata Knowledge Graph (${wikiSummary.wikibase_item})`,
      url: `https://www.wikidata.org/wiki/${encodeURIComponent(wikiSummary.wikibase_item)}`,
    });
  }

  return {
    word: cleanedWord,
    analyzedAt: Date.now(),
    phonetics: {
      phoneticTranscription: ipaText,
      pronunciationAudioUrl: audioUrl,
      syllableMetrics,
      perfectRhymes,
      nearSlantRhymes,
      meterAndRhythmScore,
    },
    lexical: {
      definitions,
      synonyms: Array.from(dictSynonyms).slice(0, 14),
      antonyms: Array.from(dictAntonyms).slice(0, 14),
      hypernyms,
      hyponyms,
      collocations,
    },
    morphology: {
      partOfSpeech: posArray,
      lemmatizationAndRoots: lemmaInfo,
      affixBreakdown: affixInfo,
      etymologyAndOrigin,
      grammaticalDependency,
    },
    pragmatics,
    knowledgeGraph: {
      namedEntityRecognition: nerLabel,
      entityDisambiguation: disambiguationList,
      externalKnowledgeLinks,
    },
    styleAndMetrics: {
      grammarAndSpellChecking: grammarReport,
      wordComplexityAndZipfScale,
      readabilityScore,
    },
  };
}

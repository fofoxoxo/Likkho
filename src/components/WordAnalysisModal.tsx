import React from 'react';
import { X, Volume2, BookOpenCheck, ExternalLink } from 'lucide-react';
import { WordAnalysisRecord } from '../utils/wordAnalysisEngine';

interface WordAnalysisModalProps {
  record: WordAnalysisRecord;
  onClose: () => void;
}

export const WordAnalysisModal: React.FC<WordAnalysisModalProps> = ({
  record,
  onClose,
}) => {
  const handlePlayPronunciation = () => {
    const audioUrl = record.phonetics.pronunciationAudioUrl;
    if (audioUrl) {
      const audio = new Audio(audioUrl);
      audio.play().catch(() => {
        speakFallback();
      });
      return;
    }
    speakFallback();
  };

  const speakFallback = () => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      try {
        window.speechSynthesis.cancel();
        const utter = new SpeechSynthesisUtterance(record.word);
        utter.lang = 'en-US';
        window.speechSynthesis.speak(utter);
      } catch {
        // ignore
      }
    }
  };

  const renderBadgeList = (items: string[], emptyLabel: string = 'None detected') => {
    if (!items || items.length === 0) {
      return <span className="text-xs italic text-[var(--wiki-muted)]">{emptyLabel}</span>;
    }
    return (
      <div className="flex flex-wrap gap-1.5">
        {items.map((item, idx) => (
          <span
            key={idx}
            className="border border-[var(--wiki-border)] bg-[var(--wiki-bg)] px-2 py-0.5 font-wiki-mono text-[11px] text-[var(--wiki-text)]"
          >
            {item}
          </span>
        ))}
      </div>
    );
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-3 sm:p-5 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="flex max-h-[88vh] w-full max-w-2xl flex-col border border-[var(--wiki-border)] bg-[var(--wiki-bg)] text-[var(--wiki-text)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <BookOpenCheck className="h-5 w-5 shrink-0 text-[#3366cc]" />
            <div className="min-w-0">
              <h2 className="font-wiki-serif text-xl font-bold leading-tight truncate">
                {record.word}
              </h2>
              <p className="font-wiki-mono text-[11px] text-[var(--wiki-muted)]">
                Word Analysis · Saved for Offline View
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handlePlayPronunciation}
              className="flex h-8 items-center gap-1.5 border border-[#3366cc] bg-[#3366cc]/10 px-2.5 text-xs font-semibold text-[#3366cc] hover:bg-[#3366cc] hover:text-white transition-colors"
              title="Play Pronunciation Audio"
            >
              <Volume2 className="h-3.5 w-3.5" />
              <span>Pronounce</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              className="flex h-8 w-8 items-center justify-center border border-transparent text-[var(--wiki-muted)] hover:border-[var(--wiki-border)] hover:text-[var(--wiki-text)]"
              aria-label="Close Word Analysis"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Scrollable 6 Categories & All Subcategories */}
        <div className="flex-1 overflow-y-auto p-4 space-y-5 text-xs">
          {/* 1. Phonetics, Sound & Prosody */}
          <section className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3.5 space-y-2.5">
            <h3 className="border-b border-[var(--wiki-hairline)] pb-1.5 font-wiki-serif text-base font-bold text-[#3366cc]">
              1. Phonetics, Sound &amp; Prosody
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Phonetic Transcription
                </div>
                <div className="font-wiki-mono text-sm font-bold text-[var(--wiki-text)]">
                  {record.phonetics.phoneticTranscription}
                </div>
              </div>

              <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Pronunciation Audio
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handlePlayPronunciation}
                    className="flex items-center gap-1.5 bg-[#3366cc] px-2.5 py-1 text-xs font-semibold text-white"
                  >
                    <Volume2 className="h-3.5 w-3.5" />
                    <span>
                      {record.phonetics.pronunciationAudioUrl
                        ? 'Play Audio Stream'
                        : 'Synthesize Speech'}
                    </span>
                  </button>
                </div>
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Syllable Metrics
              </div>
              <div className="font-wiki-mono text-[var(--wiki-text)]">
                {record.phonetics.syllableMetrics}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Perfect Rhymes
              </div>
              {renderBadgeList(record.phonetics.perfectRhymes, 'No exact rhymes found')}
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Near / Slant Rhymes
              </div>
              {renderBadgeList(record.phonetics.nearSlantRhymes, 'No slant rhymes found')}
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Meter &amp; Rhythm Score
              </div>
              <div className="text-[var(--wiki-text)]">
                {record.phonetics.meterAndRhythmScore}
              </div>
            </div>
          </section>

          {/* 2. Lexical & Semantics */}
          <section className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3.5 space-y-2.5">
            <h3 className="border-b border-[var(--wiki-hairline)] pb-1.5 font-wiki-serif text-base font-bold text-[#3366cc]">
              2. Lexical &amp; Semantics
            </h3>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5 space-y-2">
              <div className="font-semibold text-[var(--wiki-muted)]">Definitions</div>
              {record.lexical.definitions.length === 0 ? (
                <p className="italic text-[var(--wiki-muted)]">
                  No dictionary definitions returned.
                </p>
              ) : (
                <ol className="list-decimal pl-4 space-y-1.5">
                  {record.lexical.definitions.map((d, idx) => (
                    <li key={idx} className="leading-relaxed">
                      <span className="mr-1.5 font-wiki-mono text-[10px] font-bold uppercase text-[#3366cc]">
                        [{d.partOfSpeech}]
                      </span>
                      <span>{d.definition}</span>
                      {d.example && (
                        <div className="mt-0.5 italic text-[var(--wiki-muted)]">
                          “{d.example}”
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5 space-y-2">
              <div>
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">Synonyms</div>
                {renderBadgeList(record.lexical.synonyms, 'No synonyms found')}
              </div>
              <div className="border-t border-[var(--wiki-hairline)] pt-2">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">Antonyms</div>
                {renderBadgeList(record.lexical.antonyms, 'No antonyms found')}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5 space-y-2">
              <div>
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Hypernyms (Broader Terms)
                </div>
                {renderBadgeList(record.lexical.hypernyms, 'No hypernyms found')}
              </div>
              <div className="border-t border-[var(--wiki-hairline)] pt-2">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Hyponyms (More Specific Terms)
                </div>
                {renderBadgeList(record.lexical.hyponyms, 'No hyponyms found')}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Collocations
              </div>
              {renderBadgeList(
                record.lexical.collocations,
                'No frequent collocations found'
              )}
            </div>
          </section>

          {/* 3. Morphology, Syntax & Etymology */}
          <section className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3.5 space-y-2.5">
            <h3 className="border-b border-[var(--wiki-hairline)] pb-1.5 font-wiki-serif text-base font-bold text-[#3366cc]">
              3. Morphology, Syntax &amp; Etymology
            </h3>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Part of Speech (POS)
              </div>
              {renderBadgeList(record.morphology.partOfSpeech)}
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Lemmatization &amp; Roots
              </div>
              <div className="text-[var(--wiki-text)]">
                {record.morphology.lemmatizationAndRoots}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Affix Breakdown
              </div>
              <div className="text-[var(--wiki-text)]">
                {record.morphology.affixBreakdown}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Etymology &amp; Origin
              </div>
              <div className="leading-relaxed text-[var(--wiki-text)]">
                {record.morphology.etymologyAndOrigin}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Grammatical Dependency
              </div>
              <div className="text-[var(--wiki-text)]">
                {record.morphology.grammaticalDependency}
              </div>
            </div>
          </section>

          {/* 4. Pragmatics, Vibe & Sentiment */}
          <section className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3.5 space-y-2.5">
            <h3 className="border-b border-[var(--wiki-hairline)] pb-1.5 font-wiki-serif text-base font-bold text-[#3366cc]">
              4. Pragmatics, Vibe &amp; Sentiment
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Contextual Sentiment Polarity
                </div>
                <div className="font-semibold text-[var(--wiki-text)]">
                  {record.pragmatics.contextualSentimentPolarity}
                </div>
              </div>

              <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Emotional Valence
                </div>
                <div className="text-[var(--wiki-text)]">
                  {record.pragmatics.emotionalValence}
                </div>
              </div>

              <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Formality &amp; Register
                </div>
                <div className="text-[var(--wiki-text)]">
                  {record.pragmatics.formalityAndRegister}
                </div>
              </div>

              <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
                <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                  Connotation &amp; Tone
                </div>
                <div className="text-[var(--wiki-text)]">
                  {record.pragmatics.connotationAndTone}
                </div>
              </div>
            </div>
          </section>

          {/* 5. Knowledge Graph & Entities */}
          <section className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3.5 space-y-2.5">
            <h3 className="border-b border-[var(--wiki-hairline)] pb-1.5 font-wiki-serif text-base font-bold text-[#3366cc]">
              5. Knowledge Graph &amp; Entities
            </h3>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Named Entity Recognition (NER)
              </div>
              <div className="text-[var(--wiki-text)]">
                {record.knowledgeGraph.namedEntityRecognition}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Entity Disambiguation
              </div>
              {record.knowledgeGraph.entityDisambiguation.length === 0 ? (
                <span className="italic text-[var(--wiki-muted)]">
                  No additional disambiguation senses
                </span>
              ) : (
                <ul className="list-disc pl-4 space-y-1">
                  {record.knowledgeGraph.entityDisambiguation.map((ent, idx) => (
                    <li key={idx} className="leading-relaxed">
                      {ent}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                External Knowledge Links
              </div>
              <div className="flex flex-wrap gap-2">
                {record.knowledgeGraph.externalKnowledgeLinks.map((lnk, idx) => (
                  <a
                    key={idx}
                    href={lnk.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 border border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-2.5 py-1 text-xs font-medium text-[#3366cc] hover:underline"
                  >
                    <span>{lnk.label}</span>
                    <ExternalLink className="h-3 w-3" />
                  </a>
                ))}
              </div>
            </div>
          </section>

          {/* 6. Style, Grammar & Quantitative Metrics */}
          <section className="border border-[var(--wiki-border)] bg-[var(--wiki-surface)] p-3.5 space-y-2.5">
            <h3 className="border-b border-[var(--wiki-hairline)] pb-1.5 font-wiki-serif text-base font-bold text-[#3366cc]">
              6. Style, Grammar &amp; Quantitative Metrics
            </h3>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Grammar &amp; Spell Checking
              </div>
              <div className="text-[var(--wiki-text)]">
                {record.styleAndMetrics.grammarAndSpellChecking}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Word Complexity &amp; Zipf Scale
              </div>
              <div className="font-wiki-mono text-[var(--wiki-text)]">
                {record.styleAndMetrics.wordComplexityAndZipfScale}
              </div>
            </div>

            <div className="border border-[var(--wiki-hairline)] bg-[var(--wiki-bg)] p-2.5">
              <div className="font-semibold text-[var(--wiki-muted)] mb-1">
                Readability Score
              </div>
              <div className="font-wiki-mono text-[var(--wiki-text)]">
                {record.styleAndMetrics.readabilityScore}
              </div>
            </div>
          </section>
        </div>

        {/* Footer */}
        <div className="flex shrink-0 items-center justify-end border-t border-[var(--wiki-border)] bg-[var(--wiki-surface)] px-4 py-2.5">
          <button
            type="button"
            onClick={onClose}
            className="h-9 bg-[#3366cc] px-4 text-xs font-semibold text-white hover:bg-[#2a56b0]"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

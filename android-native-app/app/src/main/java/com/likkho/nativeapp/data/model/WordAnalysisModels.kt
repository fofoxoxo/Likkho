package com.likkho.nativeapp.data.model

data class WordAnalysisRecord(
    val word: String,
    val analyzedAt: Long = System.currentTimeMillis(),
    val phonetics: PhoneticsData = PhoneticsData(),
    val lexical: LexicalData = LexicalData(),
    val morphology: MorphologyData = MorphologyData(),
    val pragmatics: PragmaticsData = PragmaticsData(),
    val knowledgeGraph: KnowledgeGraphData = KnowledgeGraphData(),
    val styleAndMetrics: StyleAndMetricsData = StyleAndMetricsData()
)

data class PhoneticsData(
    val phoneticTranscription: String = "",
    val pronunciationAudioUrl: String? = null,
    val syllableMetrics: String = "",
    val perfectRhymes: List<String> = emptyList(),
    val nearSlantRhymes: List<String> = emptyList(),
    val meterAndRhythmScore: String = ""
)

data class LexicalDefinition(
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null
)

data class LexicalData(
    val definitions: List<LexicalDefinition> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val hypernyms: List<String> = emptyList(),
    val hyponyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList()
)

data class MorphologyData(
    val partOfSpeech: List<String> = emptyList(),
    val lemmatizationAndRoots: String = "",
    val affixBreakdown: String = "",
    val etymologyAndOrigin: String = "",
    val grammaticalDependency: String = ""
)

data class PragmaticsData(
    val contextualSentimentPolarity: String = "",
    val emotionalValence: String = "",
    val formalityAndRegister: String = "",
    val connotationAndTone: String = ""
)

data class ExternalLinkItem(
    val label: String,
    val url: String
)

data class KnowledgeGraphData(
    val namedEntityRecognition: String = "",
    val entityDisambiguation: List<String> = emptyList(),
    val externalKnowledgeLinks: List<ExternalLinkItem> = emptyList()
)

data class StyleAndMetricsData(
    val grammarAndSpellChecking: String = "",
    val wordComplexityAndZipfScale: String = "",
    val readabilityScore: String = ""
)

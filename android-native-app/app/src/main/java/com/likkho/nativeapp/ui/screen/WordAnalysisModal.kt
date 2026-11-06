package com.likkho.nativeapp.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.likkho.nativeapp.data.model.WordAnalysisRecord
import com.likkho.nativeapp.ui.theme.*

@Composable
fun WordAnalysisModal(
    record: WordAnalysisRecord,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.85f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = WikipediaBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = record.word,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Linguistic Analysis · Saved Offline",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Phonetics, Sound & Prosody
                item {
                    CategorySection(title = "1. Phonetics, Sound & Prosody") {
                        MetricRow(label = "Phonetic Transcription", value = record.phonetics.phoneticTranscription.ifEmpty { "N/A" }, isMono = true)
                        MetricRow(label = "Syllable Metrics", value = record.phonetics.syllableMetrics.ifEmpty { "N/A" })
                        BadgeListRow(label = "Perfect Rhymes", items = record.phonetics.perfectRhymes)
                        BadgeListRow(label = "Near / Slant Rhymes", items = record.phonetics.nearSlantRhymes)
                        MetricRow(label = "Meter & Rhythm", value = record.phonetics.meterAndRhythmScore.ifEmpty { "Balanced" })
                    }
                }

                // 2. Lexical & Semantics
                item {
                    CategorySection(title = "2. Lexical & Semantics") {
                        if (record.lexical.definitions.isNotEmpty()) {
                            Text(
                                text = "Definitions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            record.lexical.definitions.forEachIndexed { idx, def ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row {
                                        Text(
                                            text = "[${def.partOfSpeech.uppercase()}] ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = WikipediaBlue
                                        )
                                        Text(
                                            text = "${idx + 1}. ${def.definition}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (!def.example.isNullOrEmpty()) {
                                        Text(
                                            text = "“${def.example}”",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        BadgeListRow(label = "Synonyms", items = record.lexical.synonyms)
                        BadgeListRow(label = "Antonyms", items = record.lexical.antonyms)
                        BadgeListRow(label = "Collocations", items = record.lexical.collocations)
                    }
                }

                // 3. Morphology, Syntax & Etymology
                item {
                    CategorySection(title = "3. Morphology & Etymology") {
                        MetricRow(label = "Etymology & Origin", value = record.morphology.etymologyAndOrigin.ifEmpty { "Indo-European / Proto-Germanic" })
                        MetricRow(label = "Root / Lemma", value = record.morphology.lemmatizationAndRoots.ifEmpty { record.word })
                        MetricRow(label = "Affix Breakdown", value = record.morphology.affixBreakdown.ifEmpty { "Base root without affixes" })
                    }
                }

                // 4. Pragmatics, Vibe & Sentiment
                item {
                    CategorySection(title = "4. Pragmatics, Vibe & Sentiment") {
                        MetricRow(label = "Sentiment Polarity", value = record.pragmatics.contextualSentimentPolarity.ifEmpty { "Neutral" })
                        MetricRow(label = "Emotional Valence", value = record.pragmatics.emotionalValence.ifEmpty { "Moderate" })
                        MetricRow(label = "Formality & Register", value = record.pragmatics.formalityAndRegister.ifEmpty { "Standard Written English" })
                    }
                }

                // 5. Knowledge Graph & Wikipedia Links
                item {
                    CategorySection(title = "5. Knowledge Graph & Wikipedia Links") {
                        if (record.knowledgeGraph.externalKnowledgeLinks.isNotEmpty()) {
                            record.knowledgeGraph.externalKnowledgeLinks.forEach { link ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                context.startActivity(intent)
                                            } catch (ignored: Exception) {}
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = link.label,
                                        fontSize = 12.sp,
                                        color = WikipediaBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = WikipediaBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        } else {
                            Text(
                                text = "Wikipedia summary linked to encyclopedia index",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 6. Style & Readability Metrics
                item {
                    CategorySection(title = "6. Style & Readability Metrics") {
                        MetricRow(label = "Complexity Scale", value = record.styleAndMetrics.wordComplexityAndZipfScale.ifEmpty { "Common Vocabulary" })
                        MetricRow(label = "Readability Level", value = record.styleAndMetrics.readabilityScore.ifEmpty { "General Audience" })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = WikipediaBlue)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CategorySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = WikipediaBlue
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeListRow(label: String, items: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (items.isEmpty()) {
            Text(
                text = "None detected",
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

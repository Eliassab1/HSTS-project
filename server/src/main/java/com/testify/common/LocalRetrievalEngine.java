package com.testify.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The offline fallback: no network, no API key, no dependency.
 *
 * It does not generate anything — it <i>retrieves</i>. Given a question it
 * finds the paragraph of the teacher's material that best overlaps with it
 * and hands that back, labelled with its source. That is a keyword match, not
 * an answer, and the client says so on screen rather than passing it off as
 * one.
 *
 * It exists so a missing key or a flaky LAN degrades the system instead of
 * ending it. Deliberately worth testing before a demo: it is the path a bad
 * network will put you on.
 */
public class LocalRetrievalEngine implements BotEngine {

    /**
     * Minimum overlap score before a paragraph counts as relevant at all.
     * Below it the engine says the material does not cover the question,
     * which is a better answer than a confidently irrelevant paragraph.
     */
    private static final double RELEVANCE_THRESHOLD = 0.5;

    /**
     * Words carrying no topical signal. Without this list every question
     * matches whichever paragraph is longest, because long paragraphs contain
     * more instances of "the".
     */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "about", "an", "and", "are", "as", "at", "be", "but", "by", "can", "did", "do",
            "does", "for", "from", "had", "has", "have", "how", "i", "if", "in", "is", "it",
            "its", "me", "my", "of", "on", "or", "so", "than", "that", "the", "their", "them",
            "then", "there", "these", "they", "this", "to", "was", "we", "were", "what",
            "when", "where", "which", "who", "why", "will", "with", "would", "you", "your"));

    @Override
    public String name() {
        return ENGINE_LOCAL;
    }

    /** Always true — this engine needs nothing to run. */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String answerQuestion(String question, List<BotSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "Your teacher has not added any material for this course yet, "
                    + "so I have nothing to answer from.";
        }

        List<String> terms = tokenise(question);
        if (terms.isEmpty()) {
            return "I could not make out a question there. Try phrasing it in a full sentence.";
        }

        // Rarer terms are worth more: a word appearing in every paragraph
        // says nothing about which paragraph is the right one.
        Map<String, Integer> paragraphFrequency = new HashMap<>();
        List<Passage> passages = new ArrayList<>();
        for (BotSource source : sources) {
            for (String paragraph : splitParagraphs(source.getContent())) {
                Passage passage = new Passage(source.getTitle(), paragraph, tokenise(paragraph));
                passages.add(passage);
                for (String distinct : new HashSet<>(passage.terms)) {
                    paragraphFrequency.merge(distinct, 1, Integer::sum);
                }
            }
        }
        if (passages.isEmpty()) {
            return "Your teacher's material for this course is empty, "
                    + "so I have nothing to answer from.";
        }

        Passage best = null;
        double bestScore = 0.0;
        for (Passage passage : passages) {
            double score = score(terms, passage, passages.size(), paragraphFrequency);
            if (score > bestScore) {
                bestScore = score;
                best = passage;
            }
        }

        if (best == null || bestScore < RELEVANCE_THRESHOLD) {
            StringBuilder titles = new StringBuilder();
            for (BotSource source : sources) {
                if (titles.length() > 0) titles.append(", ");
                titles.append(source.getTitle());
            }
            return "The course material does not seem to cover that. "
                    + "What your teacher has provided is: " + titles + ".";
        }

        return "From \"" + best.sourceTitle + "\":\n\n" + best.text;
    }

    /**
     * Not supported: this engine can only quote material that already exists,
     * and inventing exam questions is not retrieval.
     *
     * The failure is loud on purpose. The generation screen asks the server
     * whether generation is available and disables itself when it is not,
     * rather than letting a teacher click a button that quietly produces
     * nothing.
     */
    @Override
    public List<Question> generateQuestions(String topic, String difficulty, int count,
                                            int courseId, List<BotSource> sources) {
        throw new UnsupportedOperationException(
                "Question generation needs the Claude engine, which is not configured. "
                        + "Set ANTHROPIC_API_KEY on the server and restart it. "
                        + "The learning bot still works — it answers from course material offline.");
    }

    /**
     * Weighted overlap between the question's terms and one paragraph's.
     *
     * A term's weight is a plain inverse-document-frequency: log(N / df).
     * The total is normalised by the number of question terms so a long
     * question cannot out-score a short one purely by length.
     */
    private double score(List<String> questionTerms, Passage passage,
                         int totalPassages, Map<String, Integer> paragraphFrequency) {
        Set<String> passageTerms = new HashSet<>(passage.terms);
        double total = 0.0;
        for (String term : questionTerms) {
            if (!passageTerms.contains(term)) continue;
            int df = paragraphFrequency.getOrDefault(term, 1);
            total += Math.log(1.0 + ((double) totalPassages / df));
        }
        return total / questionTerms.size();
    }

    /** Lowercases, strips punctuation, drops stopwords and one-letter noise. */
    private List<String> tokenise(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null) return terms;
        for (String raw : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) continue;
            terms.add(raw);
        }
        return terms;
    }

    /**
     * Splits source content on blank lines, falling back to the whole text
     * when a teacher pasted one unbroken block.
     */
    private List<String> splitParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        if (content == null || content.isBlank()) return paragraphs;
        for (String chunk : content.split("\\r?\\n\\s*\\r?\\n")) {
            String trimmed = chunk.trim();
            if (!trimmed.isEmpty()) paragraphs.add(trimmed);
        }
        if (paragraphs.isEmpty()) paragraphs.add(content.trim());
        return paragraphs;
    }

    /** One candidate paragraph, with its source title and its terms. */
    private static final class Passage {
        final String sourceTitle;
        final String text;
        final List<String> terms;

        Passage(String sourceTitle, String text, List<String> terms) {
            this.sourceTitle = sourceTitle == null ? "course material" : sourceTitle;
            this.text = text;
            this.terms = terms;
        }
    }
}

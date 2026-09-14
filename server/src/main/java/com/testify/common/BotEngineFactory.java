package com.testify.common;

import java.util.List;

/**
 * Picks the engine, and degrades to the fallback when Claude fails.
 *
 * Two separate decisions live here, and keeping them separate is the whole
 * design:
 * <ul>
 *   <li><b>Configuration</b> — with no API key, {@link ClaudeBotEngine} is
 *       never even tried, and the system runs offline from the start.</li>
 *   <li><b>Failure</b> — with a key present but a call that throws (no
 *       internet, a rate limit, a timeout), that <i>one call</i> falls back
 *       to {@link LocalRetrievalEngine}. The next call tries Claude again,
 *       because a dropped Wi-Fi connection is not a permanent verdict.</li>
 * </ul>
 *
 * {@link Outcome} carries the engine name alongside the answer so the caller
 * records in {@code bot_questions.engine_used} what actually answered, rather
 * than what was supposed to.
 *
 * The engines are stateless, so one instance of each is shared; the JDK's
 * {@code HttpClient} inside {@link ClaudeBotEngine} is itself thread-safe,
 * which matters because every connected client is its own thread.
 */
public final class BotEngineFactory {

    private static final ClaudeBotEngine CLAUDE = new ClaudeBotEngine();
    private static final LocalRetrievalEngine LOCAL = new LocalRetrievalEngine();

    private BotEngineFactory() {
    }

    /**
     * The engine that would be used for the next call.
     *
     * @return the Claude engine when a key is configured, otherwise the
     *         local fallback
     */
    public static BotEngine getEngine() {
        return CLAUDE.isAvailable() ? CLAUDE : LOCAL;
    }

    /**
     * Whether generation is possible at all right now.
     *
     * The teacher's generation screen asks this so it can disable itself with
     * an explanation, instead of offering a button that fails on click.
     *
     * @return true when the Claude engine is configured
     */
    public static boolean isGenerationAvailable() {
        return CLAUDE.isAvailable();
    }

    /**
     * The model the Claude engine is configured to call, for status display.
     *
     * @return the model ID
     */
    public static String getConfiguredModel() {
        return CLAUDE.getModel();
    }

    /**
     * Answers a question, falling back for this one call if Claude throws.
     *
     * @param question the student's question
     * @param sources the teacher's material
     * @return the answer and the name of the engine that produced it
     */
    public static Outcome answer(String question, List<BotSource> sources) {
        if (CLAUDE.isAvailable()) {
            try {
                return new Outcome(CLAUDE.answerQuestion(question, sources),
                        BotEngine.ENGINE_CLAUDE);
            } catch (Exception e) {
                // Not fatal: the material is still here, so answer from it.
                System.err.println("Claude engine failed, falling back to local retrieval: "
                        + e.getMessage());
            }
        }
        return new Outcome(LOCAL.answerQuestion(question, sources), BotEngine.ENGINE_LOCAL);
    }

    /**
     * Generates draft questions.
     *
     * There is no fallback here on purpose: {@link LocalRetrievalEngine}
     * cannot generate, and quietly producing nothing would be worse than
     * saying so. The exception message is written for a teacher to read.
     *
     * @param topic subject area
     * @param difficulty EASY, MEDIUM or HARD
     * @param count how many to draft
     * @param courseId course the drafts belong to
     * @param sources the teacher's material, used as context
     * @return the drafts, unsaved
     * @throws Exception when generation is unavailable or fails
     */
    public static List<Question> generate(String topic, String difficulty, int count,
                                          int courseId, List<BotSource> sources)
            throws Exception {
        if (!CLAUDE.isAvailable()) {
            throw new UnsupportedOperationException(
                    "Question generation is unavailable: the server has no ANTHROPIC_API_KEY set. "
                            + "The learning bot still answers from course material.");
        }
        return CLAUDE.generateQuestions(topic, difficulty, count, courseId, sources);
    }

    /** An answer plus the name of the engine that actually produced it. */
    public static final class Outcome {
        private final String answer;
        private final String engineUsed;

        Outcome(String answer, String engineUsed) {
            this.answer = answer;
            this.engineUsed = engineUsed;
        }

        public String getAnswer() {
            return answer;
        }

        public String getEngineUsed() {
            return engineUsed;
        }
    }
}

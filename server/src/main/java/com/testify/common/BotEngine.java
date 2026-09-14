package com.testify.common;

import java.util.List;

/**
 * What answers a student's question and drafts a teacher's questions.
 *
 * <b>Server-side only.</b> Unlike the rest of {@code com.testify.common},
 * engines are not mirrored into the client tree and are not
 * {@code Serializable}: only the server holds the API key, only the server
 * reaches the internet, and an engine instance must never travel over the
 * wire. The clients on the LAN ask the server; the server asks Claude.
 *
 * Two implementations, chosen per call by {@link BotEngineFactory}:
 * {@link ClaudeBotEngine} when a key is configured and the call succeeds, and
 * {@link LocalRetrievalEngine} otherwise. The point of the interface is that
 * a missing key or a dead network degrades the system instead of ending it —
 * the student still gets an answer out of the teacher's own material, and the
 * logged {@code engine_used} says honestly which one produced it.
 */
public interface BotEngine {

    /** Value recorded in {@code bot_questions.engine_used} for Claude. */
    String ENGINE_CLAUDE = "CLAUDE";

    /** Value recorded in {@code bot_questions.engine_used} for the fallback. */
    String ENGINE_LOCAL = "LOCAL";

    /**
     * Which engine this is: {@link #ENGINE_CLAUDE} or {@link #ENGINE_LOCAL}.
     *
     * @return the engine's recorded name
     */
    String name();

    /**
     * Whether this engine can be used at all right now. False for
     * {@link ClaudeBotEngine} when no API key is configured; always true for
     * the local fallback, which needs nothing.
     *
     * @return true when the engine is usable
     */
    boolean isAvailable();

    /**
     * Answers a student's question from the teacher's material and nothing
     * else.
     *
     * The sources are the only permitted ground: an engine that cannot answer
     * from them must say so rather than fall back on general knowledge. That
     * constraint is what makes spec 13's "information sources" a functional
     * requirement rather than a decorative one.
     *
     * @param question what the student asked
     * @param sources the teacher's material for that course
     * @return the answer text
     * @throws Exception if the engine fails; the caller falls back
     */
    String answerQuestion(String question, List<BotSource> sources) throws Exception;

    /**
     * Drafts multiple-choice questions for a teacher to review.
     *
     * The returned questions are <b>drafts</b>: unsaved, unattributed, and
     * carrying no ID. Nothing reaches the question bank until a teacher
     * approves it on screen.
     *
     * @param topic subject area to write about
     * @param difficulty EASY, MEDIUM or HARD
     * @param count how many to draft
     * @param courseId course the drafts belong to
     * @param sources the teacher's material, used as context when present
     * @return the drafts
     * @throws UnsupportedOperationException if this engine cannot generate
     * @throws Exception if the generation fails
     */
    List<Question> generateQuestions(String topic, String difficulty, int count,
                                     int courseId, List<BotSource> sources) throws Exception;
}

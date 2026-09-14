package com.testify.common;

import java.io.Serializable;

/**
 * One logged exchange with a course bot — the row shape behind both history
 * views (spec 14.2 and 14.3).
 *
 * <b>{@link #studentId} is 0 in the teacher's view, and that is the point.</b>
 * Spec 14.3 gives the teacher the general question history <i>without user
 * identification</i>, so {@code BotQuestionDAO.getAnonymousHistoryForBot}
 * never SELECTs the column at all rather than fetching it and hiding it in the
 * UI — a hidden column still travels over the wire and still sits in the
 * client's memory. Only {@code getHistoryForStudent}, which a student calls
 * for their own history, fills it in.
 */
public class BotQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int botId;

    /** 0 in the anonymised teacher view — see the class comment. */
    private int studentId;

    private String questionText;
    private String answerText;

    /**
     * Which engine actually produced the answer, {@code "CLAUDE"} or
     * {@code "LOCAL"}. Recorded per exchange rather than per bot because a
     * single failed Claude call falls back for that call alone, so the history
     * shows honestly what answered each question.
     */
    private String engineUsed;

    private String askedAt;

    public BotQuestion() {
    }

    public BotQuestion(int id, int botId, int studentId, String questionText,
                       String answerText, String engineUsed, String askedAt) {
        this.id = id;
        this.botId = botId;
        this.studentId = studentId;
        this.questionText = questionText;
        this.answerText = answerText;
        this.engineUsed = engineUsed;
        this.askedAt = askedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBotId() {
        return botId;
    }

    public void setBotId(int botId) {
        this.botId = botId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public String getEngineUsed() {
        return engineUsed;
    }

    public void setEngineUsed(String engineUsed) {
        this.engineUsed = engineUsed;
    }

    public String getAskedAt() {
        return askedAt;
    }

    public void setAskedAt(String askedAt) {
        this.askedAt = askedAt;
    }

    /** True when this answer came from the offline retrieval fallback. */
    public boolean isFromLocalEngine() {
        return "LOCAL".equalsIgnoreCase(engineUsed);
    }
}

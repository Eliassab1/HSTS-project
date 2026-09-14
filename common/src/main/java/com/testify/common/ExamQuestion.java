package com.testify.common;

import java.io.Serializable;

/**
 * Represents a question selected for a specific exam.
 *
 * The class connects a Question object with the number of points
 * assigned to that question in the exam.
 */
public class ExamQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Question included in the exam.
     */
    private Question question;

    /**
     * Number of points assigned to the question.
     */
    private int points;

    /**
     * Empty constructor.
     */
    public ExamQuestion() {
    }

    /**
     * Creates an exam question.
     *
     * @param question question included in the exam
     * @param points number of points assigned to the question
     */
    public ExamQuestion(
            Question question,
            int points
    ) {
        this.question = question;
        this.points = points;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(
            Question question
    ) {
        this.question = question;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(
            int points
    ) {
        this.points = points;
    }

    /**
     * Returns the identifier of the contained question.
     *
     * @return question identifier, or zero when no question exists
     */
    public int getQuestionId() {

        if (question == null) {
            return 0;
        }

        return question.getId();
    }

    /**
     * Returns the text of the contained question.
     *
     * @return question text
     */
    public String getQuestionText() {

        if (question == null) {
            return "";
        }

        return question.getQuestionText();
    }

    @Override
    public String toString() {
        return "ExamQuestion{" +
                "question=" + question +
                ", points=" + points +
                '}';
    }
}
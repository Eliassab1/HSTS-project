package com.testify.common;

import java.io.Serializable;

/**
 * Represents one answer selected by a student
 * while completing an exam.
 */
public class StudentAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the question.
     */
    private int questionId;

    /**
     * Answer selected by the student.
     *
     * Expected values:
     * A, B, C or D.
     */
    private String selectedAnswer;

    /**
     * Empty constructor.
     */
    public StudentAnswer() {
    }

    /**
     * Creates a student answer.
     *
     * @param questionId identifier of the question
     * @param selectedAnswer answer selected by the student
     */
    public StudentAnswer(
            int questionId,
            String selectedAnswer
    ) {
        this.questionId = questionId;
        this.selectedAnswer = selectedAnswer;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(
            int questionId
    ) {
        this.questionId = questionId;
    }

    public String getSelectedAnswer() {
        return selectedAnswer;
    }

    public void setSelectedAnswer(
            String selectedAnswer
    ) {
        this.selectedAnswer = selectedAnswer;
    }

    @Override
    public String toString() {
        return "StudentAnswer{" +
                "questionId=" + questionId +
                ", selectedAnswer='" + selectedAnswer + '\'' +
                '}';
    }
}
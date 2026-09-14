package com.testify.common;

import java.io.Serializable;

/**
 * One question of a checked exam form, as the student sees it during review:
 * the question itself, what the student chose, what the right answer was,
 * what the question was worth and whether the points were awarded.
 *
 * Only ever sent for a submission whose grade has already been approved —
 * see {@link SubmissionReview}.
 */
public class ReviewedQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The question that was asked, including its four options.
     */
    private Question question;

    /**
     * Option the student selected (A-D), or null if the question was
     * left unanswered.
     */
    private String studentAnswer;

    /**
     * Option that was correct (A-D).
     */
    private String correctAnswer;

    /**
     * Points this question was worth in this exam.
     */
    private double pointsWorth;

    /**
     * True when the student's answer matched the correct option.
     */
    private boolean correct;

    /**
     * Empty constructor.
     */
    public ReviewedQuestion() {
    }

    /**
     * Creates one reviewed question.
     *
     * @param question question that was asked
     * @param studentAnswer option the student selected, may be null
     * @param correctAnswer option that was correct
     * @param pointsWorth points the question was worth
     * @param correct whether the points were awarded
     */
    public ReviewedQuestion(
            Question question,
            String studentAnswer,
            String correctAnswer,
            double pointsWorth,
            boolean correct
    ) {
        this.question = question;
        this.studentAnswer = studentAnswer;
        this.correctAnswer = correctAnswer;
        this.pointsWorth = pointsWorth;
        this.correct = correct;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(
            Question question
    ) {
        this.question = question;
    }

    public String getStudentAnswer() {
        return studentAnswer;
    }

    public void setStudentAnswer(
            String studentAnswer
    ) {
        this.studentAnswer = studentAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(
            String correctAnswer
    ) {
        this.correctAnswer = correctAnswer;
    }

    public double getPointsWorth() {
        return pointsWorth;
    }

    public void setPointsWorth(
            double pointsWorth
    ) {
        this.pointsWorth = pointsWorth;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(
            boolean correct
    ) {
        this.correct = correct;
    }

    /**
     * Points actually earned on this question.
     *
     * @return the full points when correct, otherwise zero
     */
    public double getPointsEarned() {
        return correct ? pointsWorth : 0.0;
    }

    @Override
    public String toString() {
        return "ReviewedQuestion{" +
                "questionId=" + (question != null ? question.getId() : 0) +
                ", studentAnswer='" + studentAnswer + '\'' +
                ", correctAnswer='" + correctAnswer + '\'' +
                ", correct=" + correct +
                '}';
    }
}
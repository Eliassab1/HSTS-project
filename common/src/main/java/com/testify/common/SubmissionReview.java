package com.testify.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A student's checked exam form: the approved grade plus every question with
 * the student's answer against the correct one.
 *
 * The server only ever builds this for a submission that belongs to the
 * requesting student AND whose grade has already been approved (status
 * {@code GRADED}) — an unapproved submission would otherwise leak the
 * correct answers of an exam still in progress for other students.
 */
public class SubmissionReview implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the submission being reviewed.
     */
    private int submissionId;

    /**
     * Title of the exam that was sat.
     */
    private String examTitle;

    /**
     * Approved grade.
     */
    private double score;

    /**
     * Maximum possible grade.
     */
    private double maxScore;

    /**
     * Submission status — always GRADED for a review the server returns.
     */
    private String status;

    /**
     * The checked questions, in exam order.
     */
    private List<ReviewedQuestion> questions;

    /**
     * Empty constructor.
     */
    public SubmissionReview() {
        this.questions = new ArrayList<>();
    }

    /**
     * Creates a checked exam form.
     *
     * @param submissionId identifier of the submission
     * @param examTitle title of the exam
     * @param score approved grade
     * @param maxScore maximum possible grade
     * @param status submission status
     * @param questions checked questions
     */
    public SubmissionReview(
            int submissionId,
            String examTitle,
            double score,
            double maxScore,
            String status,
            List<ReviewedQuestion> questions
    ) {
        this.submissionId = submissionId;
        this.examTitle = examTitle;
        this.score = score;
        this.maxScore = maxScore;
        this.status = status;
        this.questions = questions != null ? questions : new ArrayList<>();
    }

    public int getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(
            int submissionId
    ) {
        this.submissionId = submissionId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public void setExamTitle(
            String examTitle
    ) {
        this.examTitle = examTitle;
    }

    public double getScore() {
        return score;
    }

    public void setScore(
            double score
    ) {
        this.score = score;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(
            double maxScore
    ) {
        this.maxScore = maxScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status
    ) {
        this.status = status;
    }

    public List<ReviewedQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(
            List<ReviewedQuestion> questions
    ) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }

    /**
     * Counts the questions the student answered correctly.
     *
     * @return number of correct answers
     */
    public int countCorrect() {

        int correct = 0;

        for (ReviewedQuestion q : questions) {
            if (q.isCorrect()) {
                correct++;
            }
        }

        return correct;
    }

    @Override
    public String toString() {
        return "SubmissionReview{" +
                "submissionId=" + submissionId +
                ", examTitle='" + examTitle + '\'' +
                ", score=" + score +
                ", questions=" + questions.size() +
                '}';
    }
}
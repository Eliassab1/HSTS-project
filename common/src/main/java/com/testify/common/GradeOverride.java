package com.testify.common;

import java.io.Serializable;

/**
 * Request payload a teacher sends to change a computer-calculated grade.
 *
 * The original computed score is never discarded — the server keeps it in
 * {@code test_submissions.original_score} and writes {@code newScore} to
 * {@code final_score}, together with the justification.
 */
public class GradeOverride implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the submission whose grade is being changed.
     */
    private int submissionId;

    /**
     * New grade the teacher is assigning, 0-100.
     */
    private double newScore;

    /**
     * Reason the teacher is changing the grade. Required — the server
     * rejects a blank justification.
     */
    private String justification;

    /**
     * Empty constructor.
     */
    public GradeOverride() {
    }

    /**
     * Creates a grade override request.
     *
     * @param submissionId identifier of the submission
     * @param newScore new grade, 0-100
     * @param justification reason for the change
     */
    public GradeOverride(
            int submissionId,
            double newScore,
            String justification
    ) {
        this.submissionId = submissionId;
        this.newScore = newScore;
        this.justification = justification;
    }

    public int getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(
            int submissionId
    ) {
        this.submissionId = submissionId;
    }

    public double getNewScore() {
        return newScore;
    }

    public void setNewScore(
            double newScore
    ) {
        this.newScore = newScore;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(
            String justification
    ) {
        this.justification = justification;
    }

    @Override
    public String toString() {
        return "GradeOverride{" +
                "submissionId=" + submissionId +
                ", newScore=" + newScore +
                '}';
    }
}
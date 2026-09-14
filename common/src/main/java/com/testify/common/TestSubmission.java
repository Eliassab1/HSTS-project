package com.testify.common;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Represents a student's submission for a test.
 *
 * This class is shared between client and server for serialization over OCSF.
 */
public class TestSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int testId;
    private int studentId;
    private String status;
    private Double finalScore;
    private Timestamp startedAt;
    private Timestamp submittedAt;

    public TestSubmission(int id, int testId, int studentId, String status, Double finalScore, Timestamp startedAt, Timestamp submittedAt) {
        this.id = id;
        this.testId = testId;
        this.studentId = studentId;
        this.status = status;
        this.finalScore = finalScore;
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
    }

    public int getId() { return id; }

    public int getTestId() { return testId; }

    public int getStudentId() { return studentId; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public Double getFinalScore() { return finalScore; }

    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }

    public Timestamp getStartedAt() { return startedAt; }

    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }

    public Timestamp getSubmittedAt() { return submittedAt; }

    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }
}


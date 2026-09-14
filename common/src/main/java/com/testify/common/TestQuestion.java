package com.testify.common;

import java.io.Serializable;

/**
 * Represents the relationship between a test and a question.
 *
 * This class is shared between client and server for serialization over OCSF.
 */
public class TestQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private int testId;
    private int questionId;
    private double pointsWorth;

    public TestQuestion(int testId, int questionId, double pointsWorth) {
        this.testId = testId;
        this.questionId = questionId;
        this.pointsWorth = pointsWorth;
    }

    public int getTestId() {
        return testId;
    }

    public void setTestId(int testId) {
        this.testId = testId;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public double getPointsWorth() {
        return pointsWorth;
    }

    public void setPointsWorth(double pointsWorth) {
        this.pointsWorth = pointsWorth;
    }
}


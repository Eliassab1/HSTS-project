package com.testify.common;

import java.io.Serializable;

/**
 * One point on the administrator's score-trend report: the average final
 * score of every exam graded on a given day.
 */
public class ScoreTrendPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Label for this point on the time axis (submission date, e.g. "2026-07-28").
     */
    private String periodLabel;

    /**
     * Average final score of all graded submissions on this date.
     */
    private double averageScore;

    /**
     * Number of graded submissions this average is based on.
     */
    private int submissionCount;

    /**
     * Empty constructor.
     */
    public ScoreTrendPoint() {
    }

    /**
     * Creates a score-trend point.
     *
     * @param periodLabel date label
     * @param averageScore average score for the period
     * @param submissionCount number of submissions averaged
     */
    public ScoreTrendPoint(
            String periodLabel,
            double averageScore,
            int submissionCount
    ) {
        this.periodLabel = periodLabel;
        this.averageScore = averageScore;
        this.submissionCount = submissionCount;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(int submissionCount) {
        this.submissionCount = submissionCount;
    }

    @Override
    public String toString() {
        return "ScoreTrendPoint{" +
                "periodLabel='" + periodLabel + '\'' +
                ", averageScore=" + averageScore +
                ", submissionCount=" + submissionCount +
                '}';
    }
}

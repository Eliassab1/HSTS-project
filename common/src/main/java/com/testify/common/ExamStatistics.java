package com.testify.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Summary statistics for one exam, used by the principal's comparison
 * reports (spec 12): comparing the exams of one teacher, the exams of one
 * course, or the exams one student has sat.
 *
 * Every figure is computed over that exam's {@code GRADED} submissions
 * only — an unapproved grade is not part of any report.
 */
public class ExamStatistics implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * What this row describes — the exam's title.
     */
    private String label;

    /**
     * Mean score.
     */
    private double average;

    /**
     * Middle score. For an even number of submissions this is the mean of
     * the two middle values.
     */
    private double median;

    /**
     * Number of graded submissions behind these figures.
     */
    private int submissionCount;

    /**
     * Lowest score achieved.
     */
    private double minScore;

    /**
     * Highest score achieved.
     */
    private double maxScore;

    /**
     * Empty constructor.
     */
    public ExamStatistics() {
    }

    /**
     * Creates one statistics row.
     *
     * @param label exam title
     * @param average mean score
     * @param median middle score
     * @param submissionCount graded submissions counted
     * @param minScore lowest score
     * @param maxScore highest score
     */
    public ExamStatistics(
            String label,
            double average,
            double median,
            int submissionCount,
            double minScore,
            double maxScore
    ) {
        this.label = label;
        this.average = average;
        this.median = median;
        this.submissionCount = submissionCount;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(
            String label
    ) {
        this.label = label;
    }

    public double getAverage() {
        return average;
    }

    public void setAverage(
            double average
    ) {
        this.average = average;
    }

    public double getMedian() {
        return median;
    }

    public void setMedian(
            double median
    ) {
        this.median = median;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(
            int submissionCount
    ) {
        this.submissionCount = submissionCount;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(
            double minScore
    ) {
        this.minScore = minScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(
            double maxScore
    ) {
        this.maxScore = maxScore;
    }

    /**
     * Spread between the lowest and highest score.
     *
     * @return maxScore - minScore
     */
    public double getRange() {
        return maxScore - minScore;
    }

    /**
     * Reduces a set of scores to one statistics row.
     *
     * Like {@code GradeDistributionBucket.bucketize}, this sits on the model
     * so the server's comparison reports and the client's per-exam summary
     * agree — in particular on the even case, where the median is the mean of
     * the two middle values.
     *
     * The scores are sorted here rather than assumed sorted, so a caller
     * cannot get a wrong median by passing them in the order they arrived.
     *
     * @param label what the row describes
     * @param scores scores in any order; an empty or null list gives a zeroed row
     * @return the statistics row
     */
    public static ExamStatistics summarise(
            String label,
            List<Double> scores
    ) {
        if (scores == null || scores.isEmpty()) {
            return new ExamStatistics(label, 0.0, 0.0, 0, 0.0, 0.0);
        }

        List<Double> sorted = new ArrayList<>(scores);
        Collections.sort(sorted);

        double sum = 0.0;
        for (double score : sorted) {
            sum += score;
        }

        int size = sorted.size();
        double median;
        if (size % 2 == 1) {
            median = sorted.get(size / 2);
        } else {
            // Even count: the mean of the two middle values.
            median = (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }

        return new ExamStatistics(
                label,
                sum / size,
                median,
                size,
                sorted.get(0),
                sorted.get(size - 1)
        );
    }

    @Override
    public String toString() {
        return "ExamStatistics{" +
                "label='" + label + '\'' +
                ", average=" + average +
                ", median=" + median +
                ", submissionCount=" + submissionCount +
                '}';
    }
}

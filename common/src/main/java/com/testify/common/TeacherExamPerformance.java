package com.testify.common;

import java.io.Serializable;

/**
 * One point on the teacher dashboard's performance chart: the average
 * final score students achieved on one of the teacher's graded exams.
 */
public class TeacherExamPerformance implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Label for this point on the exam axis (the exam's title).
     */
    private String examLabel;

    /**
     * Date the exam was created (yyyy-MM-dd), used for the chart's x-axis.
     */
    private String examDate;

    /**
     * Average final score of all graded submissions for this exam.
     */
    private double averageScore;

    /**
     * Number of graded submissions this average is based on.
     */
    private int submissionCount;

    /**
     * Empty constructor.
     */
    public TeacherExamPerformance() {
    }

    /**
     * Creates a teacher exam performance point.
     *
     * @param examLabel exam title label
     * @param examDate date the exam was created
     * @param averageScore average score for the exam
     * @param submissionCount number of submissions averaged
     */
    public TeacherExamPerformance(
            String examLabel,
            String examDate,
            double averageScore,
            int submissionCount
    ) {
        this.examLabel = examLabel;
        this.examDate = examDate;
        this.averageScore = averageScore;
        this.submissionCount = submissionCount;
    }

    public String getExamLabel() {
        return examLabel;
    }

    public void setExamLabel(String examLabel) {
        this.examLabel = examLabel;
    }

    public String getExamDate() {
        return examDate;
    }

    public void setExamDate(String examDate) {
        this.examDate = examDate;
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
        return "TeacherExamPerformance{" +
                "examLabel='" + examLabel + '\'' +
                ", examDate='" + examDate + '\'' +
                ", averageScore=" + averageScore +
                ", submissionCount=" + submissionCount +
                '}';
    }
}

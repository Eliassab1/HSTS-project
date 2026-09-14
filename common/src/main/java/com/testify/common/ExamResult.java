package com.testify.common;

import java.io.Serializable;

/**
 * Represents the result of an exam completed
 * by a student.
 */
public class ExamResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier of the result.
     */
    private int resultId;

    /**
     * Identifier of the exam.
     */
    private int examId;

    /**
     * Name of the exam.
     */
    private String examTitle;

    /**
     * Identifier of the student.
     */
    private int studentId;

    /**
     * Final exam grade.
     */
    private double grade;

    /**
     * Maximum possible grade.
     */
    private double maximumGrade;

    /**
     * Date on which the exam was submitted.
     */
    private String submissionDate;

    /**
     * Result status.
     *
     * Examples:
     * PASSED
     * FAILED
     * WAITING_FOR_REVIEW
     */
    private String status;

    /**
     * Display name of the student.
     *
     * Only populated by the principal-facing {@code GET_ALL_RESULTS} query,
     * which spans every student and would otherwise show bare numeric IDs.
     * Null on a student's own results, where the name is already known.
     */
    private String studentName;

    /**
     * Empty constructor.
     */
    public ExamResult() {
    }

    /**
     * Creates an exam result.
     *
     * @param resultId result identifier
     * @param examId exam identifier
     * @param examTitle exam title
     * @param studentId student identifier
     * @param grade final grade
     * @param maximumGrade maximum grade
     * @param submissionDate submission date
     * @param status result status
     */
    public ExamResult(
            int resultId,
            int examId,
            String examTitle,
            int studentId,
            double grade,
            double maximumGrade,
            String submissionDate,
            String status
    ) {
        this.resultId = resultId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.studentId = studentId;
        this.grade = grade;
        this.maximumGrade = maximumGrade;
        this.submissionDate = submissionDate;
        this.status = status;
    }

    public int getResultId() {
        return resultId;
    }

    public void setResultId(
            int resultId
    ) {
        this.resultId = resultId;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(
            int examId
    ) {
        this.examId = examId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public void setExamTitle(
            String examTitle
    ) {
        this.examTitle = examTitle;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(
            int studentId
    ) {
        this.studentId = studentId;
    }

    public double getGrade() {
        return grade;
    }

    public void setGrade(
            double grade
    ) {
        this.grade = grade;
    }

    public double getMaximumGrade() {
        return maximumGrade;
    }

    public void setMaximumGrade(
            double maximumGrade
    ) {
        this.maximumGrade = maximumGrade;
    }

    public String getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(
            String submissionDate
    ) {
        this.submissionDate = submissionDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status
    ) {
        this.status = status;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(
            String studentName
    ) {
        this.studentName = studentName;
    }

    /**
     * Calculates the grade as a percentage.
     *
     * @return grade percentage
     */
    public double calculatePercentage() {

        if (maximumGrade <= 0) {
            return 0;
        }

        return (grade / maximumGrade) * 100.0;
    }

    @Override
    public String toString() {
        return "ExamResult{" +
                "resultId=" + resultId +
                ", examId=" + examId +
                ", examTitle='" + examTitle + '\'' +
                ", studentId=" + studentId +
                ", grade=" + grade +
                ", maximumGrade=" + maximumGrade +
                ", status='" + status + '\'' +
                '}';
    }
}
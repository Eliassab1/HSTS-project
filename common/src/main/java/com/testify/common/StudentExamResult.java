package com.testify.common;

import java.io.Serializable;

/**
 * One student's result on one exam, as the teacher's exam-results screen
 * lists it (spec 10).
 *
 * Distinct from {@link ExamResult}, which answers "how did this student do
 * across their exams" and therefore names the exam; this answers "how did the
 * class do on this exam" and therefore names the student.
 *
 * Only ever built from {@code GRADED} submissions — a grade the teacher has
 * not released is not part of any class picture.
 */
public class StudentExamResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the submission behind this row.
     */
    private int submissionId;

    /**
     * Identifier of the student who sat the exam.
     */
    private int studentId;

    /**
     * The student's display name, falling back to their username.
     */
    private String studentName;

    /**
     * The student's שכבה (9-12). An {@code Integer}, not an {@code int}, so a
     * row with no grade level on record stays distinguishable from grade 0.
     */
    private Integer gradeLevel;

    /**
     * Final score, 0-100.
     */
    private double score;

    /**
     * PASSED or FAILED, on the same 60-point cutoff used everywhere else.
     */
    private String status;

    /**
     * When it was submitted, already formatted for display.
     */
    private String submittedAt;

    /**
     * Empty constructor.
     */
    public StudentExamResult() {
    }

    /**
     * Creates one result row.
     *
     * @param submissionId identifier of the submission
     * @param studentId identifier of the student
     * @param studentName the student's display name
     * @param gradeLevel the student's grade level, or null if unrecorded
     * @param score final score
     * @param status PASSED or FAILED
     * @param submittedAt submission timestamp, formatted
     */
    public StudentExamResult(
            int submissionId,
            int studentId,
            String studentName,
            Integer gradeLevel,
            double score,
            String status,
            String submittedAt
    ) {
        this.submissionId = submissionId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.gradeLevel = gradeLevel;
        this.score = score;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public int getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(
            int submissionId
    ) {
        this.submissionId = submissionId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(
            int studentId
    ) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(
            String studentName
    ) {
        this.studentName = studentName;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(
            Integer gradeLevel
    ) {
        this.gradeLevel = gradeLevel;
    }

    public double getScore() {
        return score;
    }

    public void setScore(
            double score
    ) {
        this.score = score;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status
    ) {
        this.status = status;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(
            String submittedAt
    ) {
        this.submittedAt = submittedAt;
    }

    /**
     * Whether this result is a pass.
     *
     * @return true when the status is PASSED
     */
    public boolean isPassed() {
        return "PASSED".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return "StudentExamResult{" +
                "studentName='" + studentName + '\'' +
                ", gradeLevel=" + gradeLevel +
                ", score=" + score +
                '}';
    }
}

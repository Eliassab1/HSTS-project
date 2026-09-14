package com.testify.common;

import java.io.Serializable;

/**
 * One row of the teacher's Grade Approval table: a submission that has been
 * graded by the computer but not yet released to the student.
 *
 * A submission reaches this state when
 * {@code test_submissionsDAO.calculateAndFinalizeScore} sets its status to
 * {@code AWAITING_APPROVAL}; it leaves it when the teacher approves the
 * grade or overrides it, both of which set the status to {@code GRADED}.
 */
public class PendingGrade implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the submission.
     */
    private int submissionId;

    /**
     * Identifier of the exam that was sat.
     */
    private int examId;

    /**
     * Title of the exam.
     */
    private String examTitle;

    /**
     * Identifier of the student who sat the exam.
     */
    private int studentId;

    /**
     * Display name of the student.
     */
    private String studentName;

    /**
     * Current grade — the computed score before approval, or the overridden
     * score afterwards.
     */
    private double score;

    /**
     * Submission status.
     *
     * Examples:
     * AWAITING_APPROVAL
     * GRADED
     */
    private String status;

    /**
     * Empty constructor.
     */
    public PendingGrade() {
    }

    /**
     * Creates one grade-approval row.
     *
     * @param submissionId identifier of the submission
     * @param examId identifier of the exam
     * @param examTitle title of the exam
     * @param studentId identifier of the student
     * @param studentName display name of the student
     * @param score current grade
     * @param status submission status
     */
    public PendingGrade(
            int submissionId,
            int examId,
            String examTitle,
            int studentId,
            String studentName,
            double score,
            String status
    ) {
        this.submissionId = submissionId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.studentId = studentId;
        this.studentName = studentName;
        this.score = score;
        this.status = status;
    }

    public int getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(
            int submissionId
    ) {
        this.submissionId = submissionId;
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

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(
            String studentName
    ) {
        this.studentName = studentName;
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

    @Override
    public String toString() {
        return "PendingGrade{" +
                "submissionId=" + submissionId +
                ", examTitle='" + examTitle + '\'' +
                ", studentName='" + studentName + '\'' +
                ", score=" + score +
                ", status='" + status + '\'' +
                '}';
    }
}
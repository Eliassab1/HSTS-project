package com.testify.common;

import java.io.Serializable;

/**
 * Payload for {@code GET_EXAM_RESULTS}: which exam's results to fetch, and
 * optionally which שכבה to narrow them to (spec 10).
 *
 * The exam identifier is not a permission — the server checks that the
 * requesting session owns the exam (or is the principal) before it returns
 * anything.
 */
public class ExamResultsQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Exam whose results are wanted.
     */
    private int examId;

    /**
     * Grade level to filter to (9-12), or null for every grade level.
     */
    private Integer gradeLevel;

    /**
     * Empty constructor.
     */
    public ExamResultsQuery() {
    }

    /**
     * Creates a results query.
     *
     * @param examId exam whose results are wanted
     * @param gradeLevel grade level to filter to, or null for all
     */
    public ExamResultsQuery(
            int examId,
            Integer gradeLevel
    ) {
        this.examId = examId;
        this.gradeLevel = gradeLevel;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(
            int examId
    ) {
        this.examId = examId;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(
            Integer gradeLevel
    ) {
        this.gradeLevel = gradeLevel;
    }

    @Override
    public String toString() {
        return "ExamResultsQuery{" +
                "examId=" + examId +
                ", gradeLevel=" + gradeLevel +
                '}';
    }
}

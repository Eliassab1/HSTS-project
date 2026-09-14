package com.testify.common;

import java.io.Serializable;

/**
 * Payload for {@code EXTEND_EXAM_DURATION}: how much longer a teacher wants
 * an in-progress exam to run (spec 7).
 *
 * The exam identifier is not a permission — the server checks that the
 * requesting session owns the exam before extending anything.
 */
public class ExamExtension implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Exam to extend.
     */
    private int examId;

    /**
     * Minutes to add. The server accepts 1-120.
     */
    private int extraMinutes;

    /**
     * Empty constructor.
     */
    public ExamExtension() {
    }

    /**
     * Creates an extension request.
     *
     * @param examId exam to extend
     * @param extraMinutes minutes to add
     */
    public ExamExtension(
            int examId,
            int extraMinutes
    ) {
        this.examId = examId;
        this.extraMinutes = extraMinutes;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(
            int examId
    ) {
        this.examId = examId;
    }

    public int getExtraMinutes() {
        return extraMinutes;
    }

    public void setExtraMinutes(
            int extraMinutes
    ) {
        this.extraMinutes = extraMinutes;
    }

    @Override
    public String toString() {
        return "ExamExtension{" +
                "examId=" + examId +
                ", extraMinutes=" + extraMinutes +
                '}';
    }
}

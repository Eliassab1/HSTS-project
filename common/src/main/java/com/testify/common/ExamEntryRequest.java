package com.testify.common;

import java.io.Serializable;

/**
 * Payload for {@code START_EXAM_BY_CODE}: the 4-digit code and national ID
 * (ת"ז) a student enters at the exam entry gate, plus their own ID so the
 * server can verify the national ID against the logged-in account.
 */
public class ExamEntryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 4-digit code the teacher set when scheduling the exam.
     */
    private String examCode;

    /**
     * National ID (ת"ז) entered by the student.
     */
    private String nationalId;

    /**
     * Identifier of the logged-in student making the request.
     */
    private int studentId;

    /**
     * Empty constructor.
     */
    public ExamEntryRequest() {
    }

    /**
     * Creates an exam entry request.
     *
     * @param examCode 4-digit exam code
     * @param nationalId student's national ID
     * @param studentId identifier of the logged-in student
     */
    public ExamEntryRequest(String examCode, String nationalId, int studentId) {
        this.examCode = examCode;
        this.nationalId = nationalId;
        this.studentId = studentId;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    @Override
    public String toString() {
        return "ExamEntryRequest{" +
                "examCode='" + examCode + '\'' +
                ", studentId=" + studentId +
                '}';
    }
}

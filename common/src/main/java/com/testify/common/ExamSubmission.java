package com.testify.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an exam completed and submitted
 * by a student.
 */
public class ExamSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the exam.
     */
    private int examId;

    /**
     * Identifier of the student.
     */
    private int studentId;

    /**
     * Student answers.
     */
    private List<StudentAnswer> answers;

    /**
     * Time used by the student in minutes.
     */
    private int usedMinutes;

    /**
     * Empty constructor.
     */
    public ExamSubmission() {
        this.answers = new ArrayList<>();
    }

    /**
     * Creates an exam submission.
     *
     * @param examId identifier of the exam
     * @param studentId identifier of the student
     * @param answers student answers
     * @param usedMinutes time used in minutes
     */
    public ExamSubmission(
            int examId,
            int studentId,
            List<StudentAnswer> answers,
            int usedMinutes
    ) {
        this.examId = examId;
        this.studentId = studentId;
        this.usedMinutes = usedMinutes;

        if (answers == null) {
            this.answers = new ArrayList<>();
        } else {
            this.answers = new ArrayList<>(answers);
        }
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(
            int examId
    ) {
        this.examId = examId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(
            int studentId
    ) {
        this.studentId = studentId;
    }

    public List<StudentAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(
            List<StudentAnswer> answers
    ) {

        if (answers == null) {
            this.answers = new ArrayList<>();
        } else {
            this.answers = new ArrayList<>(answers);
        }
    }

    public int getUsedMinutes() {
        return usedMinutes;
    }

    public void setUsedMinutes(
            int usedMinutes
    ) {
        this.usedMinutes = usedMinutes;
    }

    /**
     * Adds one answer to the submission.
     *
     * @param answer student answer
     */
    public void addAnswer(
            StudentAnswer answer
    ) {

        if (answer != null) {
            answers.add(answer);
        }
    }

    /**
     * Returns the answer selected for a question.
     *
     * @param questionId question identifier
     * @return selected answer, or null if unanswered
     */
    public String findAnswerForQuestion(
            int questionId
    ) {

        for (StudentAnswer answer : answers) {

            if (answer.getQuestionId() == questionId) {
                return answer.getSelectedAnswer();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "ExamSubmission{" +
                "examId=" + examId +
                ", studentId=" + studentId +
                ", answersCount=" + answers.size() +
                ", usedMinutes=" + usedMinutes +
                '}';
    }
}
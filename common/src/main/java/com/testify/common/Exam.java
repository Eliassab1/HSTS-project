package com.testify.common;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an exam in the HSTS system.
 *
 * An exam contains general exam information and a list
 * of selected questions with their assigned points.
 */
public class Exam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier of the exam.
     */
    private int examId;

    /**
     * Exam title.
     */
    private String title;

    /**
     * Course associated with the exam.
     */
    private String course;

    /**
     * Instructions displayed to the student.
     */
    private String instructions;

    /**
     * Exam duration in minutes.
     */
    private int durationMinutes;

    /**
     * Identifier of the teacher who created the exam.
     */
    private int teacherId;

    /**
     * Questions selected for the exam.
     */
    private List<ExamQuestion> examQuestions;

    /**
     * Whether the exam is currently active (visible to students).
     */
    private boolean isActive;

    /**
     * Number of questions — populated by the server on list queries.
     */
    private int questionCount;

    /**
     * Approval status set by the principal: PENDING, APPROVED, or REJECTED.
     */
    private String approvalStatus;

    /**
     * Reason the principal gave when rejecting this exam. Null unless
     * {@code approvalStatus} is REJECTED.
     */
    private String rejectionReason;

    /**
     * Start of the window during which students may take this exam. Null
     * means no window restriction.
     */
    private Timestamp openAt;

    /**
     * End of the window during which students may take this exam. Null
     * means no window restriction.
     */
    private Timestamp closeAt;

    /**
     * 4-digit code students enter to start this exam. Null until scheduled.
     */
    private String examCode;

    /**
     * Display name of the teacher who owns this exam.
     *
     * Only populated by the principal-facing {@code GET_ALL_EXAMS} query,
     * which spans every teacher and would otherwise show bare numeric IDs.
     * Null everywhere else.
     */
    private String teacherName;

    /**
     * Empty constructor.
     */
    public Exam() {
        this.examQuestions = new ArrayList<>();
    }

    /**
     * Creates a new exam.
     *
     * @param examId unique exam identifier
     * @param title exam title
     * @param course course name
     * @param instructions instructions for students
     * @param durationMinutes exam duration in minutes
     * @param teacherId identifier of the teacher
     * @param examQuestions selected exam questions
     */
    public Exam(
            int examId,
            String title,
            String course,
            String instructions,
            int durationMinutes,
            int teacherId,
            List<ExamQuestion> examQuestions
    ) {
        this.examId = examId;
        this.title = title;
        this.course = course;
        this.instructions = instructions;
        this.durationMinutes = durationMinutes;
        this.teacherId = teacherId;

        if (examQuestions == null) {
            this.examQuestions = new ArrayList<>();
        } else {
            this.examQuestions = new ArrayList<>(examQuestions);
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

    public String getTitle() {
        return title;
    }

    public void setTitle(
            String title
    ) {
        this.title = title;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(
            String course
    ) {
        this.course = course;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(
            String instructions
    ) {
        this.instructions = instructions;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(
            int durationMinutes
    ) {
        this.durationMinutes = durationMinutes;
    }

    public int getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(
            int teacherId
    ) {
        this.teacherId = teacherId;
    }

    public List<ExamQuestion> getExamQuestions() {
        return examQuestions;
    }

    public void setExamQuestions(
            List<ExamQuestion> examQuestions
    ) {

        if (examQuestions == null) {
            this.examQuestions = new ArrayList<>();
        } else {
            this.examQuestions = new ArrayList<>(examQuestions);
        }
    }

    /**
     * Adds a question to the exam.
     *
     * @param examQuestion question and assigned points
     */
    public void addExamQuestion(
            ExamQuestion examQuestion
    ) {

        if (examQuestion != null) {
            examQuestions.add(examQuestion);
        }
    }

    /**
     * Removes a question from the exam.
     *
     * @param questionId identifier of the question to remove
     */
    public void removeExamQuestion(
            int questionId
    ) {

        examQuestions.removeIf(
                examQuestion ->
                        examQuestion.getQuestionId() == questionId
        );
    }

    /**
     * Calculates the total number of exam points.
     *
     * @return total exam points
     */
    public int calculateTotalPoints() {

        int totalPoints = 0;

        for (ExamQuestion examQuestion : examQuestions) {
            totalPoints += examQuestion.getPoints();
        }

        return totalPoints;
    }

    /**
     * Returns the number of selected questions.
     *
     * @return number of exam questions
     */
    public int getQuestionCount() {
        return examQuestions.size();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }



    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Timestamp getOpenAt() {
        return openAt;
    }

    public void setOpenAt(Timestamp openAt) {
        this.openAt = openAt;
    }

    public Timestamp getCloseAt() {
        return closeAt;
    }

    public void setCloseAt(Timestamp closeAt) {
        this.closeAt = closeAt;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    @Override
    public String toString() {
        return "Exam{" +
                "examId=" + examId +
                ", title='" + title + '\'' +
                ", course='" + course + '\'' +
                ", durationMinutes=" + durationMinutes +
                ", teacherId=" + teacherId +
                ", questionCount=" + getQuestionCount() +
                ", totalPoints=" + calculateTotalPoints() +
                '}';
    }
}
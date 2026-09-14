package com.testify.common;

import java.io.Serializable;

public class Question implements Serializable {

    // Required by OCSF to safely send this object over the network
    private static final long serialVersionUID = 1L;

    private int id;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;
    private String visualAidUrl;
    private int courseId;

    // Identifier of the teacher who authored this question. 0 means
    // "unattributed" (e.g. questions created before this field existed).
    private int teacherId;

    // 'EASY', 'MEDIUM' or 'HARD'; null when the question predates the field
    // or was written without one. Set by the generator, and by hand in the
    // question bank.
    private String difficultyLevel;

    // Free-text subject area within the course ("quadratic equations"), null
    // when unclassified. Together with difficultyLevel this is what makes
    // automatic exam generation (req 3.1) selectable rather than random.
    private String topic;

    // Empty constructor (Best practice for database mapping and serialization)
    public Question() {
    }

    // Full constructor
    public Question(int id, String questionText, String optionA, String optionB, String optionC, String optionD, String correctAnswer, String visualAidUrl, int courseId) {
        this.id = id;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctAnswer = correctAnswer;
        this.visualAidUrl = visualAidUrl;
        this.courseId = courseId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getVisualAidUrl() {
        return visualAidUrl;
    }

    public void setVisualAidUrl(String visualAidUrl) {
        this.visualAidUrl = visualAidUrl;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public int getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(int teacherId) {
        this.teacherId = teacherId;
    }

    public String getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}

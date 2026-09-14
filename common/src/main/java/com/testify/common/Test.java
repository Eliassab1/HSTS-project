package com.testify.common;

import java.io.Serializable;

/**
 * Represents a test in the TESTIFY system.
 *
 * This class is shared between client and server for serialization over OCSF.
 */
public class Test implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String testCode;
    private int teacherId;
    private int courseId;
    private int durationMinutes;
    private String studentInstructions;
    private String teacherNotes;
    private boolean isActive;

    public Test(int id, String testCode, int teacherId, int courseId, int durationMinutes, String studentInstructions, String teacherNotes, boolean isActive) {
        this.id = id;
        this.testCode = testCode;
        this.teacherId = teacherId;
        this.courseId = courseId;
        this.durationMinutes = durationMinutes;
        this.studentInstructions = studentInstructions;
        this.teacherNotes = teacherNotes;
        this.isActive = isActive;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTestCode() {
        return testCode;
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public int getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(int teacherId) {
        this.teacherId = teacherId;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getStudentInstructions() {
        return studentInstructions;
    }

    public void setStudentInstructions(String studentInstructions) {
        this.studentInstructions = studentInstructions;
    }

    public String getTeacherNotes() {
        return teacherNotes;
    }

    public void setTeacherNotes(String teacherNotes) {
        this.teacherNotes = teacherNotes;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}


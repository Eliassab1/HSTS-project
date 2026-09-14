package com.testify.common;

import java.io.Serializable;

/**
 * Represents a course in the TESTIFY system.
 *
 * This class is shared between client and server for serialization over OCSF.
 */
public class Course implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String courseCode;
    private String courseName;
    private String subjectCode;
    private String subjectName;

    public Course(int id, String courseCode, String courseName, String subjectCode, String subjectName) {
        this.id = id;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }
}


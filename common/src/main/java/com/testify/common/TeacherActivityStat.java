package com.testify.common;

import java.io.Serializable;

/**
 * One row of the administrator's teacher activity report: how many tests
 * and questions a teacher has authored, from teacher_statistics.
 */
public class TeacherActivityStat implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Full name of the teacher.
     */
    private String teacherName;

    /**
     * Number of tests the teacher has created.
     */
    private int testsCount;

    /**
     * Number of questions the teacher has authored.
     */
    private int questionsCount;

    /**
     * Empty constructor.
     */
    public TeacherActivityStat() {
    }

    /**
     * Creates a teacher activity stat row.
     *
     * @param teacherName teacher's full name
     * @param testsCount number of tests created
     * @param questionsCount number of questions authored
     */
    public TeacherActivityStat(
            String teacherName,
            int testsCount,
            int questionsCount
    ) {
        this.teacherName = teacherName;
        this.testsCount = testsCount;
        this.questionsCount = questionsCount;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public int getTestsCount() {
        return testsCount;
    }

    public void setTestsCount(int testsCount) {
        this.testsCount = testsCount;
    }

    public int getQuestionsCount() {
        return questionsCount;
    }

    public void setQuestionsCount(int questionsCount) {
        this.questionsCount = questionsCount;
    }

    @Override
    public String toString() {
        return "TeacherActivityStat{" +
                "teacherName='" + teacherName + '\'' +
                ", testsCount=" + testsCount +
                ", questionsCount=" + questionsCount +
                '}';
    }
}

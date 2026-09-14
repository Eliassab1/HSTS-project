package com.testify.common;

import java.io.Serializable;

/**
 * One row of the administrator's exam pass/fail report: how many graded
 * submissions passed (score &gt;= 60) vs. failed, for a single course.
 */
public class PassFailStat implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Label identifying this row (course name).
     */
    private String label;

    /**
     * Number of graded submissions with a passing score.
     */
    private int passCount;

    /**
     * Number of graded submissions with a failing score.
     */
    private int failCount;

    /**
     * Empty constructor.
     */
    public PassFailStat() {
    }

    /**
     * Creates a pass/fail stat row.
     *
     * @param label course name
     * @param passCount number of passing submissions
     * @param failCount number of failing submissions
     */
    public PassFailStat(
            String label,
            int passCount,
            int failCount
    ) {
        this.label = label;
        this.passCount = passCount;
        this.failCount = failCount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getPassCount() {
        return passCount;
    }

    public void setPassCount(int passCount) {
        this.passCount = passCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    /**
     * Total number of graded submissions in this row.
     *
     * @return passCount + failCount
     */
    public int getTotalCount() {
        return passCount + failCount;
    }

    /**
     * Pass rate as a percentage of graded submissions.
     *
     * @return 0-100, or 0 if there are no graded submissions
     */
    public double getPassRate() {
        int total = getTotalCount();
        return total == 0 ? 0.0 : (passCount * 100.0) / total;
    }

    @Override
    public String toString() {
        return "PassFailStat{" +
                "label='" + label + '\'' +
                ", passCount=" + passCount +
                ", failCount=" + failCount +
                '}';
    }
}

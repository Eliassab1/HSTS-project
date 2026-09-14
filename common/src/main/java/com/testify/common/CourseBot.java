package com.testify.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A course's learning bot (spec 13) — one per course, enforced by the UNIQUE
 * index on {@code course_bots.course_id}.
 *
 * Two different shapes travel under this class, and which one you get depends
 * on the action:
 * <ul>
 *   <li>{@code GET_STUDENT_BOTS} returns headers — no {@link #sources}, since
 *       a student must never receive the material the answers are graded
 *       against;</li>
 *   <li>{@code GET_COURSE_BOT} returns the full record with sources, and is
 *       answered only for a teacher of that course.</li>
 * </ul>
 * {@link #sourceCount} is filled either way so the student's course picker can
 * show which bots actually have material behind them.
 */
public class CourseBot implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int courseId;

    /** Resolved course name, so a listing needs no second lookup. */
    private String courseName;

    private String botName;

    /**
     * Whether students may use this bot at all (spec 14 — availability is the
     * teacher's switch, separate from the exam lockout, which is the server's).
     */
    private boolean available;

    private int sourceCount;

    /** Never populated for a student — see the class comment. */
    private List<BotSource> sources = new ArrayList<>();

    public CourseBot() {
    }

    public CourseBot(int id, int courseId, String botName, boolean available) {
        this.id = id;
        this.courseId = courseId;
        this.botName = botName;
        this.available = available;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(int sourceCount) {
        this.sourceCount = sourceCount;
    }

    public List<BotSource> getSources() {
        return sources;
    }

    public void setSources(List<BotSource> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    @Override
    public String toString() {
        String name = botName == null || botName.isBlank() ? "Course bot" : botName;
        return courseName == null || courseName.isBlank()
                ? name
                : name + " (" + courseName + ")";
    }
}

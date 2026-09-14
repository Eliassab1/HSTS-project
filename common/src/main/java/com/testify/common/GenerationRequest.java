package com.testify.common;

import java.io.Serializable;

/**
 * What a teacher asks the engine to generate — the payload for both
 * {@code GENERATE_QUESTIONS} and {@code GENERATE_EXAM}.
 *
 * {@link #durationMinutes} is only read by {@code GENERATE_EXAM}; the question
 * generator ignores it. One payload class rather than two because the two
 * requests differ by exactly that field, and the teacher's screen offers both
 * modes side by side.
 *
 * Nothing here is a permission: the server checks that the session is a
 * teacher of {@link #courseId} before any engine call is made.
 */
public class GenerationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Difficulty values accepted by the server and stored on a question. */
    public static final String DIFFICULTY_EASY = "EASY";
    public static final String DIFFICULTY_MEDIUM = "MEDIUM";
    public static final String DIFFICULTY_HARD = "HARD";

    /** Upper bound on {@link #count}, mirrored in the server's validation. */
    public static final int MAX_COUNT = 10;

    private int courseId;
    private String topic;
    private String difficulty;
    private int count;

    /** Exam length in minutes; used by GENERATE_EXAM only. */
    private int durationMinutes;

    public GenerationRequest() {
    }

    public GenerationRequest(int courseId, String topic, String difficulty, int count) {
        this.courseId = courseId;
        this.topic = topic;
        this.difficulty = difficulty;
        this.count = count;
    }

    public GenerationRequest(int courseId, String topic, String difficulty, int count,
                             int durationMinutes) {
        this(courseId, topic, difficulty, count);
        this.durationMinutes = durationMinutes;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
